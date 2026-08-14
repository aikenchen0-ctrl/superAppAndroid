import AVFoundation
import ImageIO
import os.signpost
import UIKit

private let sidebarPlaceholderAvatarCache: NSCache<NSString, UIImage> = {
    let cache = NSCache<NSString, UIImage>()
    cache.countLimit = 800
    return cache
}()

final class AvatarPipeline {
    static let shared = AvatarPipeline()
    static let decodedByteBudget = 24 * 1_024 * 1_024
    static let defaultTargetSize = CGSize(width: 44, height: 44)

    private let imagePipeline: MediaThumbnailPipeline
    private let lock = NSLock()
    private var failedUntilByURL: [NSURL: Date] = [:]
    private let failureRetryInterval: TimeInterval = 18

    init(decoder: MediaThumbnailPipeline.DecodeHandler? = nil) {
        imagePipeline = MediaThumbnailPipeline(
            maxConcurrentOperationCount: 3,
            decodedByteBudget: Self.decodedByteBudget,
            cacheCountLimit: 500,
            signpostName: "AvatarImageLoad",
            decoder: decoder
        )
    }

    func cachedImage(
        for url: URL,
        targetSize: CGSize = AvatarPipeline.defaultTargetSize,
        scale: CGFloat = UIScreen.main.scale
    ) -> UIImage? {
        imagePipeline.cachedImage(
            for: url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: targetSize,
            scale: scale
        )
    }

    @discardableResult
    func load(
        _ url: URL,
        targetSize: CGSize = AvatarPipeline.defaultTargetSize,
        scale: CGFloat = UIScreen.main.scale,
        priority: Float = URLSessionTask.defaultPriority,
        completion: @escaping (UIImage?) -> Void
    ) -> MediaThumbnailRequest {
        let key = url as NSURL
        if priority < URLSessionTask.highPriority, isTemporarilyFailed(key) {
            let request = MediaThumbnailRequest(cancellation: {})
            DispatchQueue.main.async {
                request.deliver(nil, completion: completion)
            }
            return request
        }

        return imagePipeline.load(
            url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: targetSize,
            scale: scale,
            priority: priority
        ) { [weak self] image in
            if image == nil {
                self?.markTemporaryFailure(key)
            } else {
                self?.clearTemporaryFailure(key)
            }
            completion(image)
        }
    }

    @discardableResult
    func prefetch(
        _ url: URL,
        targetSize: CGSize = AvatarPipeline.defaultTargetSize,
        scale: CGFloat = UIScreen.main.scale
    ) -> MediaThumbnailRequest {
        load(
            url,
            targetSize: targetSize,
            scale: scale,
            priority: URLSessionTask.lowPriority
        ) { _ in }
    }

    private func isTemporarilyFailed(_ key: NSURL) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard let until = failedUntilByURL[key] else { return false }
        if until > Date() {
            return true
        }
        failedUntilByURL.removeValue(forKey: key)
        return false
    }

    private func markTemporaryFailure(_ key: NSURL) {
        lock.lock()
        failedUntilByURL[key] = Date().addingTimeInterval(failureRetryInterval)
        lock.unlock()
    }

    private func clearTemporaryFailure(_ key: NSURL) {
        lock.lock()
        failedUntilByURL.removeValue(forKey: key)
        lock.unlock()
    }
}

final class MediaThumbnailRequest {
    private let lock = NSLock()
    private var cancellation: (() -> Void)?
    private var isCancelled = false
    private var didBeginDelivery = false

    fileprivate init(cancellation: @escaping () -> Void) {
        self.cancellation = cancellation
    }

    func cancel() {
        lock.lock()
        guard !isCancelled, !didBeginDelivery else {
            lock.unlock()
            return
        }
        isCancelled = true
        let action = cancellation
        cancellation = nil
        lock.unlock()
        action?()
    }

    fileprivate func deliver(_ image: UIImage?, completion: (UIImage?) -> Void) {
        lock.lock()
        guard !isCancelled, !didBeginDelivery else {
            lock.unlock()
            return
        }
        didBeginDelivery = true
        cancellation = nil
        lock.unlock()
        completion(image)
    }

    deinit {
        cancel()
    }
}

final class MediaThumbnailPipeline {
    enum ContentKind: Hashable {
        case image(trimsTransparentCanvas: Bool)
        case video
    }

    struct CacheKey: Hashable {
        let url: URL
        let kind: ContentKind
        let maximumPixelDimension: Int

        var cacheKey: NSString {
            "\(url.absoluteString)|\(kind)|\(maximumPixelDimension)" as NSString
        }
    }

    static let shared = MediaThumbnailPipeline()
    static let decodedByteBudget = 64 * 1_024 * 1_024

    typealias DecodeHandler = (URL, ContentKind, Int) -> UIImage?

    private struct Subscriber {
        let request: MediaThumbnailRequest
        let completion: (UIImage?) -> Void
    }

    private struct InFlightRequest {
        let generation: UUID
        var operation: Operation?
        var dataTask: URLSessionDataTask?
        var subscribers: [UUID: Subscriber]
        let signpostID: OSSignpostID
    }

    private let cache = NSCache<NSString, UIImage>()
    private let operationQueue: OperationQueue
    private let session: URLSession
    private let signpostName: StaticString
    private let decodeHandler: DecodeHandler?
    private let lock = NSLock()
    private var inFlight: [CacheKey: InFlightRequest] = [:]

    init(
        maxConcurrentOperationCount: Int = 3,
        decodedByteBudget: Int = MediaThumbnailPipeline.decodedByteBudget,
        cacheCountLimit: Int = 256,
        signpostName: StaticString = "MediaThumbnailLoad",
        decoder: DecodeHandler? = nil
    ) {
        cache.totalCostLimit = decodedByteBudget
        cache.countLimit = cacheCountLimit
        operationQueue = OperationQueue()
        operationQueue.name = "local.ios-float.media-thumbnail"
        operationQueue.qualityOfService = .userInitiated
        operationQueue.maxConcurrentOperationCount = max(1, maxConcurrentOperationCount)
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 24
        configuration.timeoutIntervalForResource = 36
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.httpMaximumConnectionsPerHost = max(1, maxConcurrentOperationCount)
        session = URLSession(configuration: configuration)
        self.signpostName = signpostName
        decodeHandler = decoder
    }

    func cachedImage(
        for url: URL,
        kind: ContentKind,
        targetSize: CGSize,
        scale: CGFloat
    ) -> UIImage? {
        cache.object(forKey: makeKey(url: url, kind: kind, targetSize: targetSize, scale: scale).cacheKey)
    }

    @discardableResult
    func load(
        _ url: URL,
        kind: ContentKind,
        targetSize: CGSize,
        scale: CGFloat,
        priority: Float = URLSessionTask.defaultPriority,
        completion: @escaping (UIImage?) -> Void
    ) -> MediaThumbnailRequest {
        let key = makeKey(url: url, kind: kind, targetSize: targetSize, scale: scale)
        if let cached = cache.object(forKey: key.cacheKey) {
            let request = MediaThumbnailRequest(cancellation: {})
            DispatchQueue.main.async {
                request.deliver(cached, completion: completion)
            }
            return request
        }

        let subscriberID = UUID()
        let request = MediaThumbnailRequest { [weak self] in
            self?.cancelSubscriber(id: subscriberID, for: key)
        }
        let subscriber = Subscriber(request: request, completion: completion)
        lock.lock()
        if var current = inFlight[key] {
            current.subscribers[subscriberID] = subscriber
            current.dataTask?.priority = max(current.dataTask?.priority ?? 0, priority)
            if let operation = current.operation {
                let requestedPriority = Self.queuePriority(for: priority)
                if requestedPriority.rawValue > operation.queuePriority.rawValue {
                    operation.queuePriority = requestedPriority
                }
            }
            inFlight[key] = current
            lock.unlock()
            return request
        }

        let generation = UUID()
        let signpostID = PerformanceSignpost.begin(signpostName)
        if decodeHandler == nil, Self.shouldDownloadImage(url: url, kind: kind) {
            let dataTask = makeImageDataTask(url: url, key: key, generation: generation)
            dataTask.priority = priority
            inFlight[key] = InFlightRequest(
                generation: generation,
                operation: nil,
                dataTask: dataTask,
                subscribers: [subscriberID: subscriber],
                signpostID: signpostID
            )
            lock.unlock()
            dataTask.resume()
        } else {
            let operation = makeDecodeOperation(url: url, kind: kind, key: key, generation: generation)
            operation.queuePriority = Self.queuePriority(for: priority)
            inFlight[key] = InFlightRequest(
                generation: generation,
                operation: operation,
                dataTask: nil,
                subscribers: [subscriberID: subscriber],
                signpostID: signpostID
            )
            lock.unlock()
            operationQueue.addOperation(operation)
        }
        return request
    }

    private func makeKey(
        url: URL,
        kind: ContentKind,
        targetSize: CGSize,
        scale: CGFloat
    ) -> CacheKey {
        let finiteScale = scale.isFinite && scale > 0 ? scale : 1
        let requested = max(targetSize.width, targetSize.height) * finiteScale
        let maximumPixelDimension = min(2_048, max(64, Int(ceil(requested.isFinite ? requested : 512))))
        return CacheKey(url: url, kind: kind, maximumPixelDimension: maximumPixelDimension)
    }

    private func cancelSubscriber(id subscriberID: UUID, for key: CacheKey) {
        lock.lock()
        guard var current = inFlight[key] else {
            lock.unlock()
            return
        }
        current.subscribers.removeValue(forKey: subscriberID)
        if current.subscribers.isEmpty {
            inFlight.removeValue(forKey: key)
            current.dataTask?.cancel()
            current.operation?.cancel()
            let signpostID = current.signpostID
            lock.unlock()
            PerformanceSignpost.end(signpostName, id: signpostID)
            return
        } else {
            inFlight[key] = current
        }
        lock.unlock()
    }

    private func complete(key: CacheKey, generation: UUID, image: UIImage?) {
        lock.lock()
        guard let current = inFlight[key], current.generation == generation else {
            lock.unlock()
            return
        }
        inFlight.removeValue(forKey: key)
        let subscribers = Array(current.subscribers.values)
        let signpostID = current.signpostID
        lock.unlock()
        PerformanceSignpost.end(signpostName, id: signpostID)

        if let image, let cgImage = image.cgImage {
            let cost = min(Int.max / 4, cgImage.bytesPerRow * cgImage.height)
            cache.setObject(image, forKey: key.cacheKey, cost: cost)
        }
        DispatchQueue.main.async {
            subscribers.forEach { subscriber in
                subscriber.request.deliver(image, completion: subscriber.completion)
            }
        }
    }

    private static func shouldDownloadImage(url: URL, kind: ContentKind) -> Bool {
        guard case .image = kind else { return false }
        let scheme = url.scheme?.lowercased()
        return scheme == "https" || scheme == "http"
    }

    private static func queuePriority(for priority: Float) -> Operation.QueuePriority {
        if priority >= URLSessionTask.highPriority { return .high }
        if priority <= URLSessionTask.lowPriority { return .low }
        return .normal
    }

    private func makeDecodeOperation(
        url: URL,
        kind: ContentKind,
        key: CacheKey,
        generation: UUID
    ) -> BlockOperation {
        var operation: BlockOperation!
        operation = BlockOperation { [weak self] in
            guard let self, !operation.isCancelled else { return }
            let image: UIImage?
            if let decodeHandler = self.decodeHandler {
                image = decodeHandler(url, kind, key.maximumPixelDimension)
            } else {
                image = self.decode(url: url, kind: kind, maximumPixelDimension: key.maximumPixelDimension)
            }
            guard !operation.isCancelled else { return }
            self.complete(key: key, generation: generation, image: image)
        }
        return operation
    }

    private func makeImageDataTask(
        url: URL,
        key: CacheKey,
        generation: UUID
    ) -> URLSessionDataTask {
        var request = URLRequest(url: url)
        request.timeoutInterval = 24
        request.cachePolicy = .returnCacheDataElseLoad
        request.setValue("image/avif,image/webp,image/apng,image/*,*/*;q=0.8", forHTTPHeaderField: "Accept")
        return session.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard error == nil,
                  (statusCode == 0 || 200..<300 ~= statusCode),
                  let data
            else {
                self.complete(key: key, generation: generation, image: nil)
                return
            }
            var operation: BlockOperation!
            operation = BlockOperation { [weak self] in
                guard let self, !operation.isCancelled else { return }
                let image = self.decodeImageData(
                    data,
                    kind: key.kind,
                    maximumPixelDimension: key.maximumPixelDimension
                )
                guard !operation.isCancelled else { return }
                self.complete(key: key, generation: generation, image: image)
            }
            guard self.attach(operation: operation, to: key, generation: generation) else { return }
            self.operationQueue.addOperation(operation)
        }
    }

    private func attach(operation: Operation, to key: CacheKey, generation: UUID) -> Bool {
        lock.lock()
        guard var current = inFlight[key], current.generation == generation else {
            lock.unlock()
            operation.cancel()
            return false
        }
        current.operation = operation
        current.dataTask = nil
        inFlight[key] = current
        lock.unlock()
        return true
    }

    private func decode(url: URL, kind: ContentKind, maximumPixelDimension: Int) -> UIImage? {
        switch kind {
        case .image(let trimsTransparentCanvas):
            guard let source = imageSource(for: url) else { return nil }
            return decodeImageSource(
                source,
                trimsTransparentCanvas: trimsTransparentCanvas,
                maximumPixelDimension: maximumPixelDimension
            )
        case .video:
            let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(
                width: maximumPixelDimension,
                height: maximumPixelDimension
            )
            guard let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) else {
                return nil
            }
            return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
        }
    }

    private func decodeImageData(
        _ data: Data,
        kind: ContentKind,
        maximumPixelDimension: Int
    ) -> UIImage? {
        guard case .image(let trimsTransparentCanvas) = kind,
              let source = CGImageSourceCreateWithData(data as CFData, nil)
        else { return nil }
        return decodeImageSource(
            source,
            trimsTransparentCanvas: trimsTransparentCanvas,
            maximumPixelDimension: maximumPixelDimension
        )
    }

    private func decodeImageSource(
        _ source: CGImageSource,
        trimsTransparentCanvas: Bool,
        maximumPixelDimension: Int
    ) -> UIImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maximumPixelDimension,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        let image = UIImage(cgImage: cgImage, scale: 1, orientation: .up)
        return trimsTransparentCanvas ? image.trimmedTransparentCanvasIfNeeded() : image
    }

    private func imageSource(for url: URL) -> CGImageSource? {
        if url.isFileURL {
            return CGImageSourceCreateWithURL(url as CFURL, nil)
        }
        guard let data = try? Data(contentsOf: url, options: .mappedIfSafe) else { return nil }
        return CGImageSourceCreateWithData(data as CFData, nil)
    }
}

extension UIImage {
    static func sidebarPlaceholderAvatar(
        title: String,
        color: UIColor,
        size: CGSize = CGSize(width: 28, height: 28),
        cornerRadius: CGFloat? = nil
    ) -> UIImage {
        let initial = title.trimmingCharacters(in: .whitespacesAndNewlines).first.map { String($0).uppercased() } ?? "?"
        let radiusKey = cornerRadius.map { String(format: "%.2f", $0) } ?? "default"
        let key = "\(initial)|\(Int(size.width.rounded()))x\(Int(size.height.rounded()))|\(radiusKey)|\(color.description)" as NSString
        if let cached = sidebarPlaceholderAvatarCache.object(forKey: key) {
            return cached
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let rect = CGRect(origin: .zero, size: size)
            let side = min(size.width, size.height)
            let radius = max(0, min(side * 0.28, cornerRadius ?? side * 0.28))
            let path = UIBezierPath(roundedRect: rect, cornerRadius: radius)
            color.withAlphaComponent(0.94).setFill()
            path.fill()

            let shineRect = CGRect(x: 0, y: 0, width: size.width, height: size.height * 0.46)
            UIColor.white.withAlphaComponent(0.10).setFill()
            UIBezierPath(
                roundedRect: shineRect,
                byRoundingCorners: [.topLeft, .topRight],
                cornerRadii: CGSize(width: radius, height: radius)
            ).fill()

            let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            let initial = trimmedTitle.isEmpty ? "?" : String(trimmedTitle.prefix(1)).uppercased()
            let fontSize = max(10, side * (side < 24 ? 0.52 : 0.44))
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .center
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
                .foregroundColor: UIColor.white,
                .paragraphStyle: paragraph
            ]
            let attributedInitial = NSAttributedString(string: initial, attributes: attributes)
            let textSize = attributedInitial.size()
            let textRect = CGRect(
                x: (size.width - textSize.width) / 2,
                y: (size.height - textSize.height) / 2 - side * 0.02,
                width: textSize.width,
                height: textSize.height
            )
            attributedInitial.draw(in: textRect)
        }
        sidebarPlaceholderAvatarCache.setObject(image, forKey: key)
        return image
    }

    static func sidebarCompositeGroupAvatar(
        titles: [String],
        color: UIColor,
        size: CGSize = CGSize(width: 44, height: 44)
    ) -> UIImage {
        let titles = Array(titles.filter { !$0.isEmpty }.prefix(4))
        guard titles.count > 1 else {
            return sidebarPlaceholderAvatar(title: titles.first ?? "群", color: color, size: size)
        }

        let key = "group|\(titles.joined(separator: "|"))|\(Int(size.width.rounded()))x\(Int(size.height.rounded()))|\(color.description)" as NSString
        if let cached = sidebarPlaceholderAvatarCache.object(forKey: key) {
            return cached
        }

        let palette: [UIColor] = [
            UIColor(red: 0.20, green: 0.56, blue: 0.78, alpha: 1),
            UIColor(red: 0.22, green: 0.68, blue: 0.48, alpha: 1),
            UIColor(red: 0.88, green: 0.52, blue: 0.22, alpha: 1),
            UIColor(red: 0.70, green: 0.40, blue: 0.72, alpha: 1)
        ]
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            let rect = CGRect(origin: .zero, size: size)
            color.withAlphaComponent(0.30).setFill()
            context.fill(rect)

            let gap = max(1, min(size.width, size.height) * 0.035)
            let inset = gap
            let tileSide = (min(size.width, size.height) - inset * 2 - gap) / 2
            let topY = inset
            let bottomY = inset + tileSide + gap
            let leftX = (size.width - tileSide * 2 - gap) / 2
            let rightX = leftX + tileSide + gap
            let centeredX = (size.width - tileSide) / 2
            let centeredY = (size.height - tileSide) / 2
            let frames: [CGRect]
            switch titles.count {
            case 2:
                frames = [
                    CGRect(x: leftX, y: centeredY, width: tileSide, height: tileSide),
                    CGRect(x: rightX, y: centeredY, width: tileSide, height: tileSide)
                ]
            case 3:
                frames = [
                    CGRect(x: centeredX, y: topY, width: tileSide, height: tileSide),
                    CGRect(x: leftX, y: bottomY, width: tileSide, height: tileSide),
                    CGRect(x: rightX, y: bottomY, width: tileSide, height: tileSide)
                ]
            default:
                frames = [
                    CGRect(x: leftX, y: topY, width: tileSide, height: tileSide),
                    CGRect(x: rightX, y: topY, width: tileSide, height: tileSide),
                    CGRect(x: leftX, y: bottomY, width: tileSide, height: tileSide),
                    CGRect(x: rightX, y: bottomY, width: tileSide, height: tileSide)
                ]
            }

            for (index, frame) in frames.enumerated() {
                let tile = sidebarPlaceholderAvatar(
                    title: titles[index],
                    color: palette[index % palette.count],
                    size: frame.size,
                    cornerRadius: tileSide * 0.18
                )
                tile.draw(in: frame)
            }
        }
        sidebarPlaceholderAvatarCache.setObject(image, forKey: key)
        return image
    }
}

class PaddingLabel: UILabel {
    var textInsets = UIEdgeInsets(top: 2, left: 6, bottom: 2, right: 6) {
        didSet { invalidateIntrinsicContentSize() }
    }

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: textInsets))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(
            width: size.width + textInsets.left + textInsets.right,
            height: size.height + textInsets.top + textInsets.bottom
        )
    }
}

private final class OutlinedNameLabel: PaddingLabel {
    var outlineColor = UIColor.white.withAlphaComponent(0.36)
    var outlineWidth: CGFloat = 0.55

    override func drawText(in rect: CGRect) {
        guard let text, !text.isEmpty else {
            super.drawText(in: rect)
            return
        }
        let originalColor = textColor

        textColor = outlineColor
        let offsets = [
            CGPoint(x: -outlineWidth, y: 0),
            CGPoint(x: outlineWidth, y: 0),
            CGPoint(x: 0, y: -outlineWidth),
            CGPoint(x: 0, y: outlineWidth)
        ]
        for offset in offsets {
            super.drawText(in: rect.offsetBy(dx: offset.x, dy: offset.y))
        }

        textColor = originalColor
        super.drawText(in: rect)
    }
}

private struct LayeredTextShadow: Equatable {
    let offset: CGSize
    let blurRadius: CGFloat
    let color: UIColor

    static func == (lhs: LayeredTextShadow, rhs: LayeredTextShadow) -> Bool {
        lhs.offset == rhs.offset
            && lhs.blurRadius == rhs.blurRadius
            && lhs.color.isEqual(rhs.color)
    }
}

private final class LayeredShadowLabel: UILabel {
    private static let shadowOutset: CGFloat = 11

    var textShadows: [LayeredTextShadow] = [] {
        didSet {
            guard oldValue != textShadows else { return }
            setNeedsDisplay()
        }
    }

    var textContentRect: CGRect {
        bounds.insetBy(dx: Self.shadowOutset, dy: Self.shadowOutset)
    }

    override var alignmentRectInsets: UIEdgeInsets {
        UIEdgeInsets(
            top: Self.shadowOutset,
            left: Self.shadowOutset,
            bottom: Self.shadowOutset,
            right: Self.shadowOutset
        )
    }

    override func drawText(in rect: CGRect) {
        let textRect = rect.insetBy(dx: Self.shadowOutset, dy: Self.shadowOutset)
        guard let context = UIGraphicsGetCurrentContext(),
              let renderedText = resolvedAttributedText(),
              renderedText.length > 0
        else {
            super.drawText(in: textRect)
            return
        }

        let textStorage = NSTextStorage(attributedString: renderedText)
        let layoutManager = NSLayoutManager()
        layoutManager.usesFontLeading = true
        let textContainer = NSTextContainer(size: textRect.size)
        textContainer.lineFragmentPadding = 0
        textContainer.maximumNumberOfLines = numberOfLines
        textContainer.lineBreakMode = lineBreakMode
        layoutManager.addTextContainer(textContainer)
        textStorage.addLayoutManager(layoutManager)

        let glyphRange = layoutManager.glyphRange(for: textContainer)
        let usedRect = layoutManager.usedRect(for: textContainer)
        let drawingOrigin = CGPoint(
            x: textRect.minX,
            y: textRect.minY + max(0, (textRect.height - usedRect.height) / 2) - usedRect.minY
        )

        // CSS paints the first declared shadow on top, so composite in reverse order.
        for textShadow in textShadows.reversed() {
            context.saveGState()
            context.setShadow(
                offset: textShadow.offset,
                blur: textShadow.blurRadius,
                color: textShadow.color.cgColor
            )
            layoutManager.drawGlyphs(forGlyphRange: glyphRange, at: drawingOrigin)
            context.restoreGState()
        }
        layoutManager.drawGlyphs(forGlyphRange: glyphRange, at: drawingOrigin)
    }

    func compactTextWidth(constrainedTo maximumWidth: CGFloat, scale: CGFloat) -> CGFloat {
        guard maximumWidth > 0,
              let renderedText = resolvedAttributedText(),
              renderedText.length > 0
        else { return 0 }

        var candidateWidth = maximumWidth
        let pixel = 1 / max(scale, 1)

        // Reflow at the available width, then tighten to the widest real line.
        // A few passes are enough for the new width to settle after wrapping.
        for _ in 0..<4 {
            let textStorage = NSTextStorage(attributedString: renderedText)
            let layoutManager = NSLayoutManager()
            layoutManager.usesFontLeading = true
            let textContainer = NSTextContainer(
                size: CGSize(width: candidateWidth, height: .greatestFiniteMagnitude)
            )
            textContainer.lineFragmentPadding = 0
            textContainer.maximumNumberOfLines = numberOfLines
            textContainer.lineBreakMode = lineBreakMode
            layoutManager.addTextContainer(textContainer)
            textStorage.addLayoutManager(layoutManager)
            layoutManager.ensureLayout(for: textContainer)

            var widestLine: CGFloat = 0
            let glyphRange = layoutManager.glyphRange(for: textContainer)
            layoutManager.enumerateLineFragments(forGlyphRange: glyphRange) { _, usedRect, _, _, _ in
                widestLine = max(widestLine, usedRect.maxX)
            }

            let tightenedWidth = min(
                maximumWidth,
                max(pixel, ceil(widestLine / pixel) * pixel)
            )
            if abs(tightenedWidth - candidateWidth) < pixel {
                return tightenedWidth
            }
            candidateWidth = tightenedWidth
        }
        return candidateWidth
    }

    private func resolvedAttributedText() -> NSAttributedString? {
        if let attributedText, attributedText.length > 0 {
            return attributedText
        }
        guard let text, !text.isEmpty else { return nil }
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.alignment = textAlignment
        paragraphStyle.lineBreakMode = lineBreakMode
        return NSAttributedString(
            string: text,
            attributes: [
                .font: font as Any,
                .foregroundColor: textColor as Any,
                .paragraphStyle: paragraphStyle
            ]
        )
    }
}

struct InlineCardInteraction {
    let kind: BubbleCardKind
    let title: String
    let subtitle: String
    let url: String
}

struct RichMediaInteraction {
    enum Kind {
        case image
        case file
    }

    enum Action {
        case preview
        case recognizeImage
        case findObject
        case openLink
    }

    let kind: Kind
    let action: Action
    let title: String
    let url: String
    let access: BubbleAccessScope
    let preview: String
}

private struct RichTextLinkHitTarget {
    let label: UILabel
    let range: NSRange
    let url: String
}

private final class DashedBubbleView: UIView {
    override class var layerClass: AnyClass {
        CAShapeLayer.self
    }

    private var shapeLayer: CAShapeLayer {
        layer as! CAShapeLayer
    }

    private let borderLayer = CAShapeLayer()
    private var fillColor: UIColor = .clear
    private var borderColor: UIColor = UIColor.white.withAlphaComponent(0.44)
    private var dashedBorderColor: UIColor = UIColor.white.withAlphaComponent(0.88)
    private var usesDashedBorder = false
    private var hidesBorder = false
    private var topBorderGap: CGRect?
    private let dimensionalGlossLayer = CAGradientLayer()
    private var usesDimensionalAppearance = false
    private var dimensionalAccent = UIColor.clear

    override var backgroundColor: UIColor? {
        get { fillColor }
        set {
            fillColor = newValue ?? .clear
            super.backgroundColor = .clear
            updateShape()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateShape()
    }

    func setBorderStyle(isDashed: Bool) {
        usesDashedBorder = isDashed
        if !isDashed {
            setDashedBorderAnimation(active: false)
        }
        updateShape()
    }

    func setBorderHidden(_ hidden: Bool) {
        hidesBorder = hidden
        updateShape()
    }

    func setDashedBorderAnimation(active: Bool) {
        let key = "dashedBorderPhaseFlow"
        if active {
            borderLayer.removeAnimation(forKey: "dashedBorderClockwiseRotation")
            borderLayer.transform = CATransform3DIdentity
            guard borderLayer.animation(forKey: key) == nil else { return }
            let animation = CABasicAnimation(keyPath: "lineDashPhase")
            animation.fromValue = 0
            animation.toValue = -18
            animation.duration = 0.85
            animation.repeatCount = .infinity
            animation.timingFunction = CAMediaTimingFunction(name: .linear)
            borderLayer.add(animation, forKey: key)
        } else {
            borderLayer.removeAnimation(forKey: key)
            borderLayer.removeAnimation(forKey: "dashedBorderClockwiseRotation")
            borderLayer.transform = CATransform3DIdentity
            borderLayer.lineDashPhase = 0
        }
    }

    func setTopBorderGap(_ gap: CGRect?) {
        topBorderGap = gap
        updateShape()
    }

    func setDimensionalAppearance(enabled: Bool, accent: UIColor) {
        usesDimensionalAppearance = enabled
        dimensionalAccent = accent
        updateShape()
    }

    private func setup() {
        super.backgroundColor = .clear
        shapeLayer.fillColor = fillColor.cgColor
        shapeLayer.strokeColor = UIColor.clear.cgColor
        borderLayer.fillColor = UIColor.clear.cgColor
        borderLayer.lineJoin = .round
        borderLayer.lineCap = .round
        dimensionalGlossLayer.startPoint = CGPoint(x: 0.08, y: 0)
        dimensionalGlossLayer.endPoint = CGPoint(x: 0.92, y: 1)
        dimensionalGlossLayer.masksToBounds = true
        dimensionalGlossLayer.isHidden = true
        layer.addSublayer(borderLayer)
        layer.insertSublayer(dimensionalGlossLayer, below: borderLayer)
    }

    private func updateShape() {
        let lineWidth: CGFloat = usesDashedBorder ? 1.6 : 1
        let inset = max(0.5, lineWidth / 2)
        guard bounds.origin.x.isFinite,
              bounds.origin.y.isFinite,
              bounds.width.isFinite,
              bounds.height.isFinite,
              bounds.width > inset * 2,
              bounds.height > inset * 2,
              layer.cornerRadius.isFinite
        else {
            clearShapeLayers()
            return
        }
        let radius = max(0, min(layer.cornerRadius - inset, min(bounds.width, bounds.height) / 2))
        let pathBounds = bounds.insetBy(dx: inset, dy: inset)
        let fillPath = UIBezierPath(roundedRect: pathBounds, cornerRadius: radius)

        shapeLayer.fillColor = fillColor.cgColor
        shapeLayer.strokeColor = UIColor.clear.cgColor
        shapeLayer.path = fillPath.cgPath
        borderLayer.frame = bounds
        updateDimensionalAppearance(pathBounds: pathBounds, radius: radius, fillPath: fillPath)
        guard !hidesBorder else {
            borderLayer.path = nil
            borderLayer.strokeColor = UIColor.clear.cgColor
            return
        }
        borderLayer.strokeColor = (usesDashedBorder ? dashedBorderColor : borderColor).cgColor
        borderLayer.lineWidth = lineWidth
        borderLayer.lineDashPattern = usesDashedBorder ? [5, 4] : nil
        borderLayer.path = borderPath(in: pathBounds, radius: radius).cgPath
    }

    private func clearShapeLayers() {
        shapeLayer.path = nil
        shapeLayer.shadowPath = nil
        shapeLayer.shadowOpacity = 0
        borderLayer.path = nil
        borderLayer.strokeColor = UIColor.clear.cgColor
        dimensionalGlossLayer.isHidden = true
    }

    private func updateDimensionalAppearance(pathBounds: CGRect, radius: CGFloat, fillPath: UIBezierPath) {
        let canRender = usesDimensionalAppearance && !hidesBorder && fillColor.cgColor.alpha > 0.01 && !pathBounds.isEmpty
        dimensionalGlossLayer.isHidden = !canRender
        if canRender {
            dimensionalGlossLayer.frame = pathBounds
            dimensionalGlossLayer.cornerRadius = radius
            dimensionalGlossLayer.colors = [
                UIColor.white.withAlphaComponent(0.16).cgColor,
                UIColor.white.withAlphaComponent(0.05).cgColor,
                UIColor.clear.cgColor
            ]
            shapeLayer.shadowColor = UIColor.black.withAlphaComponent(0.18).cgColor
            shapeLayer.shadowOpacity = 1
            shapeLayer.shadowOffset = CGSize(width: 0, height: 1.2)
            shapeLayer.shadowRadius = 2
            shapeLayer.shadowPath = fillPath.cgPath
        } else {
            shapeLayer.shadowColor = UIColor.clear.cgColor
            shapeLayer.shadowOpacity = 0
            shapeLayer.shadowOffset = .zero
            shapeLayer.shadowRadius = 0
            shapeLayer.shadowPath = nil
        }
    }

    private func borderPath(in rect: CGRect, radius: CGFloat) -> UIBezierPath {
        guard usesDashedBorder,
              let topBorderGap,
              topBorderGap.origin.x.isFinite,
              topBorderGap.origin.y.isFinite,
              topBorderGap.width.isFinite,
              topBorderGap.height.isFinite
        else {
            return UIBezierPath(roundedRect: rect, cornerRadius: radius)
        }

        let minTopX = rect.minX + radius
        let maxTopX = rect.maxX - radius
        let gapStart = max(minTopX, min(topBorderGap.minX, maxTopX))
        let gapEnd = max(minTopX, min(topBorderGap.maxX, maxTopX))
        guard gapEnd > gapStart else {
            return UIBezierPath(roundedRect: rect, cornerRadius: radius)
        }

        let path = UIBezierPath()
        if gapStart > minTopX {
            path.move(to: CGPoint(x: minTopX, y: rect.minY))
            path.addLine(to: CGPoint(x: gapStart, y: rect.minY))
        }
        path.move(to: CGPoint(x: gapEnd, y: rect.minY))
        path.addLine(to: CGPoint(x: maxTopX, y: rect.minY))
        path.addArc(
            withCenter: CGPoint(x: rect.maxX - radius, y: rect.minY + radius),
            radius: radius,
            startAngle: -.pi / 2,
            endAngle: 0,
            clockwise: true
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - radius))
        path.addArc(
            withCenter: CGPoint(x: rect.maxX - radius, y: rect.maxY - radius),
            radius: radius,
            startAngle: 0,
            endAngle: .pi / 2,
            clockwise: true
        )
        path.addLine(to: CGPoint(x: rect.minX + radius, y: rect.maxY))
        path.addArc(
            withCenter: CGPoint(x: rect.minX + radius, y: rect.maxY - radius),
            radius: radius,
            startAngle: .pi / 2,
            endAngle: .pi,
            clockwise: true
        )
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + radius))
        path.addArc(
            withCenter: CGPoint(x: rect.minX + radius, y: rect.minY + radius),
            radius: radius,
            startAngle: .pi,
            endAngle: .pi * 1.5,
            clockwise: true
        )
        return path
    }
}

private final class BubbleDepthSurfaceView: UIView {
    private let gradientLayer = CAGradientLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
    }

    func configure(color: UIColor, cornerRadius: CGFloat) {
        layer.cornerRadius = cornerRadius
        layer.cornerCurve = .continuous
        gradientLayer.cornerRadius = cornerRadius
        gradientLayer.colors = [
            color.withAlphaComponent(0.30).cgColor,
            color.withAlphaComponent(0.16).cgColor
        ]
        layer.shadowColor = UIColor.black.withAlphaComponent(0.10).cgColor
        layer.shadowOpacity = 1
        layer.shadowRadius = 2
        layer.shadowOffset = CGSize(width: 0, height: 1)
    }

    private func setup() {
        isUserInteractionEnabled = false
        backgroundColor = .clear
        layer.masksToBounds = true
        gradientLayer.startPoint = CGPoint(x: 0.18, y: 0)
        gradientLayer.endPoint = CGPoint(x: 0.82, y: 1)
        layer.addSublayer(gradientLayer)
    }
}

final class SidebarCell: UICollectionViewCell {
    static let reuseIdentifier = "SidebarCell"

    private let tileView = UIView()
    private let avatarView = SidebarAvatarView()
    private let titleLabel = UILabel()
    private let unreadDot = UIView()
    private let accountConnectionDot = UIView()
    private var tileCenterYConstraint: NSLayoutConstraint!
    private var tileTopOffsetConstraint: NSLayoutConstraint!
    private var accountConnectionDotCenterXConstraint: NSLayoutConstraint!
    private var normalContentAlpha: CGFloat = 1

    func connectionAnchorPoint(in view: UIView) -> CGPoint {
        let frame = view.convert(tileView.bounds, from: tileView)
        return CGPoint(x: frame.maxX, y: frame.midY)
    }

    func alignConnectionCenterY(_ centerY: CGFloat) {
        guard centerY.isFinite else { return }
        tileCenterYConstraint.isActive = false
        tileTopOffsetConstraint.isActive = true
        let resolvedTopOffset = centerY - UnreadTimelineLayoutMetrics.avatarSideLength / 2
        applyUnreadTimelineTranslation(resolvedTopOffset)
    }

    func updateUnreadTimelineTopOffset(_ topOffset: CGFloat) {
        guard topOffset.isFinite else { return }
        tileCenterYConstraint.isActive = false
        tileTopOffsetConstraint.isActive = true
        applyUnreadTimelineTranslation(topOffset)
    }

    private func applyUnreadTimelineTranslation(_ topOffset: CGFloat) {
        guard abs(tileView.transform.ty - topOffset) > 0.1 else { return }
        tileView.transform = CGAffineTransform(translationX: 0, y: topOffset)
    }

    func leadingConnectionAnchorPoint(in view: UIView) -> CGPoint {
        if !accountConnectionDot.isHidden {
            let frame = view.convert(accountConnectionDot.bounds, from: accountConnectionDot)
            return CGPoint(x: frame.midX, y: frame.midY)
        }
        let frame = view.convert(tileView.bounds, from: tileView)
        return CGPoint(x: frame.minX, y: frame.midY)
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override var isHighlighted: Bool {
        didSet {
            contentView.alpha = isHighlighted ? normalContentAlpha * 0.65 : normalContentAlpha
            contentView.transform = isHighlighted ? CGAffineTransform(scaleX: 0.96, y: 0.96) : .identity
        }
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        avatarView.prepareForReuse()
        stopLoadingAnimation()
        normalContentAlpha = 1
        contentView.transform = .identity
        contentView.alpha = 1
        tileView.transform = .identity
    }

    func configure(
        with item: SidebarItem,
        showsIcon: Bool = true,
        showsTitle: Bool = true,
        accountBorderColor: UIColor? = nil,
        accountBorderWidth: CGFloat? = nil,
        accountConnectionDotColor: UIColor? = nil,
        alignsToTop: Bool = false,
        topOffset: CGFloat = 0,
        isAvailable: Bool = true
    ) {
        stopLoadingAnimation()
        normalContentAlpha = isAvailable ? 1 : 0.26
        contentView.alpha = isHighlighted ? normalContentAlpha * 0.65 : normalContentAlpha
        tileCenterYConstraint.isActive = !alignsToTop
        tileTopOffsetConstraint.isActive = alignsToTop
        tileTopOffsetConstraint.constant = 0
        tileView.transform = alignsToTop
            ? CGAffineTransform(translationX: 0, y: topOffset)
            : .identity
        let usesAvatarTile = avatarView.configure(with: item)
        avatarView.isHidden = !showsIcon
        tileView.backgroundColor = item.tintColor.withAlphaComponent(item.isActive ? 0.95 : 0.78)
        titleLabel.text = item.title
        titleLabel.isHidden = usesAvatarTile || !showsTitle
        titleLabel.font = showsIcon ? .systemFont(ofSize: 8, weight: .semibold) : .systemFont(ofSize: 12, weight: .bold)
        titleLabel.numberOfLines = showsIcon ? 1 : 2
        unreadDot.isHidden = item.kind != .account || item.isOnline != true
        if let accountBorderWidth {
            tileView.layer.borderWidth = accountBorderWidth
            tileView.layer.borderColor = accountBorderColor?.cgColor ?? UIColor.clear.cgColor
        } else {
            tileView.layer.borderWidth = item.isActive ? 2 : 1
            tileView.layer.borderColor = item.isActive
                ? UIColor.white.withAlphaComponent(0.92).cgColor
                : UIColor.white.withAlphaComponent(0.58).cgColor
        }
        contentView.layer.shadowColor = accountBorderColor?.cgColor
        contentView.layer.shadowOpacity = accountBorderColor == nil ? 0 : (accountBorderWidth ?? 1) >= 3 ? 0.38 : 0.22
        contentView.layer.shadowRadius = (accountBorderWidth ?? 1) >= 3 ? 3 : 1.5
        contentView.layer.shadowOffset = .zero
        accountConnectionDot.isHidden = accountConnectionDotColor == nil
        accountConnectionDot.backgroundColor = accountConnectionDotColor
        accountConnectionDot.layer.shadowColor = accountConnectionDotColor?.cgColor
        accountConnectionDot.layer.shadowOpacity = accountConnectionDotColor == nil ? 0 : 0.30
        accountConnectionDotCenterXConstraint.constant = accountConnectionDotColor == nil
            ? 0
            : (accountBorderWidth ?? 0) / 2
    }

    func accountConnectionDotColor() -> UIColor? {
        guard !accountConnectionDot.isHidden else { return nil }
        return accountConnectionDot.backgroundColor
    }

    func configureLoadingAccount(index: Int) {
        avatarView.configureLoading()
        normalContentAlpha = 1
        contentView.alpha = 1
        avatarView.isHidden = false
        titleLabel.text = index == 0 ? "拉取中" : ""
        titleLabel.isHidden = false
        titleLabel.font = .systemFont(ofSize: 8, weight: .semibold)
        titleLabel.numberOfLines = 1
        unreadDot.isHidden = true
        accountConnectionDot.isHidden = true
        tileView.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        tileView.layer.borderWidth = 1
        tileView.layer.borderColor = UIColor.white.withAlphaComponent(0.26).cgColor
        contentView.layer.shadowOpacity = 0
        startLoadingAnimation(delay: Double(index) * 0.12)
    }

    private func startLoadingAnimation(delay: Double) {
        stopLoadingAnimation()
        let pulse = CABasicAnimation(keyPath: "opacity")
        pulse.fromValue = 0.42
        pulse.toValue = 1
        pulse.duration = 0.82
        pulse.autoreverses = true
        pulse.repeatCount = .infinity
        pulse.beginTime = CACurrentMediaTime() + delay
        pulse.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        contentView.layer.add(pulse, forKey: "accountLoadingPulse")
    }

    private func stopLoadingAnimation() {
        contentView.layer.removeAnimation(forKey: "accountLoadingPulse")
    }

    private func setup() {
        clipsToBounds = false
        contentView.backgroundColor = .clear
        contentView.clipsToBounds = false
        tileView.layer.cornerRadius = 14
        tileView.layer.cornerCurve = .continuous
        tileView.layer.borderWidth = 1
        tileView.layer.borderColor = UIColor.white.withAlphaComponent(0.58).cgColor
        tileView.clipsToBounds = true
        tileView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(tileView)

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        tileView.addSubview(avatarView)

        titleLabel.font = .systemFont(ofSize: 8, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 1
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        tileView.addSubview(titleLabel)

        unreadDot.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        unreadDot.layer.cornerRadius = 4.5
        unreadDot.layer.borderWidth = 1.5
        unreadDot.layer.borderColor = UIColor.white.cgColor
        unreadDot.layer.shadowColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1).cgColor
        unreadDot.layer.shadowOpacity = 0.30
        unreadDot.layer.shadowRadius = 1.5
        unreadDot.layer.shadowOffset = .zero
        unreadDot.translatesAutoresizingMaskIntoConstraints = false
        tileView.addSubview(unreadDot)

        accountConnectionDot.isHidden = true
        accountConnectionDot.isUserInteractionEnabled = false
        accountConnectionDot.layer.cornerRadius = 4.5
        accountConnectionDot.layer.borderWidth = 1.5
        accountConnectionDot.layer.borderColor = UIColor.white.withAlphaComponent(0.9).cgColor
        accountConnectionDot.layer.shadowRadius = 1.5
        accountConnectionDot.layer.shadowOffset = .zero
        accountConnectionDot.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(accountConnectionDot)

        tileCenterYConstraint = tileView.centerYAnchor.constraint(equalTo: contentView.centerYAnchor)
        tileTopOffsetConstraint = tileView.topAnchor.constraint(equalTo: contentView.topAnchor)
        accountConnectionDotCenterXConstraint = accountConnectionDot.centerXAnchor.constraint(
            equalTo: tileView.leadingAnchor
        )
        NSLayoutConstraint.activate([
            tileView.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            tileCenterYConstraint,
            tileView.widthAnchor.constraint(equalToConstant: UnreadTimelineLayoutMetrics.avatarSideLength),
            tileView.heightAnchor.constraint(equalToConstant: UnreadTimelineLayoutMetrics.avatarSideLength),

            avatarView.topAnchor.constraint(equalTo: tileView.topAnchor),
            avatarView.leadingAnchor.constraint(equalTo: tileView.leadingAnchor),
            avatarView.trailingAnchor.constraint(equalTo: tileView.trailingAnchor),
            avatarView.bottomAnchor.constraint(equalTo: tileView.bottomAnchor),

            titleLabel.leadingAnchor.constraint(equalTo: tileView.leadingAnchor, constant: 3),
            titleLabel.trailingAnchor.constraint(equalTo: tileView.trailingAnchor, constant: -3),
            titleLabel.bottomAnchor.constraint(equalTo: tileView.bottomAnchor, constant: -6),

            unreadDot.widthAnchor.constraint(equalToConstant: 9),
            unreadDot.heightAnchor.constraint(equalToConstant: 9),
            unreadDot.topAnchor.constraint(equalTo: tileView.topAnchor, constant: 4),
            unreadDot.trailingAnchor.constraint(equalTo: tileView.trailingAnchor, constant: -4),

            accountConnectionDotCenterXConstraint,
            accountConnectionDot.centerYAnchor.constraint(equalTo: tileView.centerYAnchor),
            accountConnectionDot.widthAnchor.constraint(equalToConstant: 9),
            accountConnectionDot.heightAnchor.constraint(equalToConstant: 9)
        ])
    }
}

final class SelectedLeftSidebarFloatingView: UIView {
    private let content = UIView()
    private let avatarView = SidebarAvatarView()
    private let titleLabel = UILabel()
    private let unreadDot = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(with item: SidebarItem) {
        let usesAvatarTile = avatarView.configure(with: item)
        content.backgroundColor = item.tintColor.withAlphaComponent(0.96)
        titleLabel.text = item.title
        titleLabel.isHidden = usesAvatarTile
        unreadDot.isHidden = true
    }

    private func setup() {
        backgroundColor = .clear
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.20
        layer.shadowRadius = 8
        layer.shadowOffset = CGSize(width: 0, height: 3)

        content.layer.cornerRadius = 14
        content.layer.cornerCurve = .continuous
        content.layer.borderWidth = 2
        content.layer.borderColor = UIColor.white.withAlphaComponent(0.94).cgColor
        content.clipsToBounds = true
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(avatarView)

        titleLabel.font = .systemFont(ofSize: 8, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 1
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(titleLabel)

        unreadDot.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        unreadDot.layer.cornerRadius = 5
        unreadDot.layer.borderWidth = 1
        unreadDot.layer.borderColor = UIColor.white.cgColor
        unreadDot.translatesAutoresizingMaskIntoConstraints = false
        content.addSubview(unreadDot)

        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: topAnchor),
            content.leadingAnchor.constraint(equalTo: leadingAnchor),
            content.trailingAnchor.constraint(equalTo: trailingAnchor),
            content.bottomAnchor.constraint(equalTo: bottomAnchor)
        ] + [
            avatarView.topAnchor.constraint(equalTo: content.topAnchor),
            avatarView.leadingAnchor.constraint(equalTo: content.leadingAnchor),
            avatarView.trailingAnchor.constraint(equalTo: content.trailingAnchor),
            avatarView.bottomAnchor.constraint(equalTo: content.bottomAnchor),

            titleLabel.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 3),
            titleLabel.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -3),
            titleLabel.bottomAnchor.constraint(equalTo: content.bottomAnchor, constant: -6),

            unreadDot.widthAnchor.constraint(equalToConstant: 10),
            unreadDot.heightAnchor.constraint(equalToConstant: 10),
            unreadDot.topAnchor.constraint(equalTo: content.topAnchor, constant: 4),
            unreadDot.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -4)
        ])
    }
}

final class UnansweredRecipientAvatarStackView: UIView {
    static let avatarSide: CGFloat = 37
    static let maximumVisibleAvatarCount = 3
    static let visibleOverlapStep: CGFloat = avatarSide / 2
    private var avatarViews: [SidebarAvatarView] = []

    var preferredWidth: CGFloat {
        let visibleCount = avatarViews.lazy.filter { !$0.isHidden }.count
        guard visibleCount > 0 else { return Self.avatarSide }
        return Self.avatarSide + Self.visibleOverlapStep * CGFloat(visibleCount - 1)
    }

    func configure(with items: [SidebarItem]) {
        let visibleItems = Array(items.prefix(Self.maximumVisibleAvatarCount))
        while avatarViews.count < visibleItems.count {
            let avatarView = SidebarAvatarView()
            avatarView.isUserInteractionEnabled = false
            avatarView.layer.borderWidth = 0.5
            avatarView.layer.borderColor = UIColor.white.withAlphaComponent(0.48).cgColor
            avatarView.layer.masksToBounds = true
            addSubview(avatarView)
            avatarViews.append(avatarView)
        }

        for (index, avatarView) in avatarViews.enumerated() {
            guard let item = visibleItems[safe: index] else {
                avatarView.prepareForReuse()
                avatarView.isHidden = true
                continue
            }
            _ = avatarView.configure(with: item, cornerRadius: 0)
            avatarView.isHidden = false
        }
        isHidden = visibleItems.isEmpty
        setNeedsLayout()
    }

    func prepareForReuse() {
        avatarViews.forEach {
            $0.prepareForReuse()
            $0.isHidden = true
        }
        isHidden = true
    }

    func rightmostAvatarFrame(in view: UIView) -> CGRect? {
        guard let avatarView = avatarViews.last(where: { !$0.isHidden }) else { return nil }
        return avatarView.convert(avatarView.bounds, to: view)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let visibleViews = avatarViews.filter { !$0.isHidden }
        guard !visibleViews.isEmpty else { return }

        let scale = window?.screen.scale ?? UIScreen.main.scale
        let align: (CGFloat) -> CGFloat = { ($0 * scale).rounded() / scale }
        let rightX = align(bounds.maxX - Self.avatarSide)
        let bottomY = align(bounds.maxY - Self.avatarSide)

        if visibleViews.count == 1 {
            visibleViews[0].frame = CGRect(
                x: rightX,
                y: bottomY,
                width: Self.avatarSide,
                height: Self.avatarSide
            )
            visibleViews[0].alpha = 1
            visibleViews[0].layer.zPosition = 0
            return
        }

        for (index, avatarView) in visibleViews.enumerated() {
            let distanceFromRight = visibleViews.count - 1 - index
            avatarView.frame = CGRect(
                x: align(rightX - CGFloat(distanceFromRight) * Self.visibleOverlapStep),
                y: bottomY,
                width: Self.avatarSide,
                height: Self.avatarSide
            )
            avatarView.alpha = min(1, 0.68 + CGFloat(index) * 0.13)
            avatarView.layer.zPosition = CGFloat(index)
        }
    }
}

private final class ChatMessageAvatarView: UIView {
    private let imageView = UIImageView()
    private let fallbackLabel = UILabel()
    private var avatarLoadTask: MediaThumbnailRequest?
    private var representedAvatarURL: URL?
    private var avatarLoadGeneration = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func prepareForReuse() {
        avatarLoadTask?.cancel()
        avatarLoadTask = nil
        avatarLoadGeneration &+= 1
        representedAvatarURL = nil
        imageView.image = nil
        imageView.isHidden = true
        fallbackLabel.isHidden = false
    }

    func configure(initials: String, color: UIColor, avatarURL: URL?) {
        avatarLoadTask?.cancel()
        avatarLoadTask = nil
        avatarLoadGeneration &+= 1
        representedAvatarURL = avatarURL

        backgroundColor = color.withAlphaComponent(0.92)
        fallbackLabel.text = initials
        fallbackLabel.backgroundColor = .clear
        fallbackLabel.isHidden = false
        imageView.image = nil
        imageView.isHidden = true

        guard let avatarURL else { return }
        let targetSize = CGSize(width: 40, height: 40)
        let scale = window?.screen.scale ?? UIScreen.main.scale
        if let cached = AvatarPipeline.shared.cachedImage(
            for: avatarURL,
            targetSize: targetSize,
            scale: scale
        ) {
            imageView.image = cached
            imageView.isHidden = false
            fallbackLabel.isHidden = true
            return
        }

        let generation = avatarLoadGeneration
        avatarLoadTask = AvatarPipeline.shared.load(
            avatarURL,
            targetSize: targetSize,
            scale: scale,
            priority: URLSessionTask.highPriority
        ) { [weak self] image in
            guard let self,
                  self.avatarLoadGeneration == generation,
                  self.representedAvatarURL == avatarURL,
                  let image
            else { return }
            self.imageView.image = image
            self.imageView.isHidden = false
            self.fallbackLabel.isHidden = true
        }
    }

    private func setup() {
        backgroundColor = .clear
        clipsToBounds = true

        fallbackLabel.translatesAutoresizingMaskIntoConstraints = false
        fallbackLabel.textAlignment = .center
        fallbackLabel.textColor = .white
        fallbackLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        fallbackLabel.isUserInteractionEnabled = false
        addSubview(fallbackLabel)

        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.isHidden = true
        imageView.isUserInteractionEnabled = false
        addSubview(imageView)

        NSLayoutConstraint.activate([
            fallbackLabel.leadingAnchor.constraint(equalTo: leadingAnchor),
            fallbackLabel.trailingAnchor.constraint(equalTo: trailingAnchor),
            fallbackLabel.topAnchor.constraint(equalTo: topAnchor),
            fallbackLabel.bottomAnchor.constraint(equalTo: bottomAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }
}

final class ChatMessageBubbleView: UIView {
    var onConnectionGeometryDidChange: (() -> Void)?

    private enum BubbleMode {
        case text
        case oversizedText
        case media
        case channelVideo
        case voice
        case document
        case location
        case profile
        case link
        case money
        case call
        case quote
        case stacked
        case notice
        case system
    }

    private let rowStack = UIStackView()
    private let avatarView = ChatMessageAvatarView()
    private let columnStack = UIStackView()
    private let senderLabel = LayeredShadowLabel()
    private let depthSurfaceView = BubbleDepthSurfaceView()
    private let bubbleView = DashedBubbleView()
    private let unansweredRecipientDot = UIView()
    private let unansweredRecipientWatermark = UnansweredRecipientAvatarStackView()
    private let unansweredRecipientWatermarkMask = CALayer()
    private var unansweredRecipientWatermarkMaskSize = CGSize.zero
    private var unansweredRecipientWatermarkWidthConstraint: NSLayoutConstraint!
    private let inlineNameLabel = LayeredShadowLabel()
    private let contentStack = UIStackView()
    private let titleRow = UIStackView()
    private let iconView = UIImageView()
    private let titleLabel = UILabel()
    private let bodyLabel = LayeredShadowLabel()
    private let richStack = UIStackView()
    private let detailLabel = UILabel()
    private let previewView = UIView()
    private let previewIconView = UIImageView()
    private let previewTitleLabel = UILabel()
    private let previewDetailLabel = UILabel()
    private let paymentContentView = PaymentCardContentView()
    private let documentCardView = DocumentCardView()
    private let mediaImageView = UIImageView()
    private let mediaPlayIconView = UIImageView(image: UIImage(systemName: "play.circle.fill"))
    private let mediaPrivacyOverlayView = MediaPrivacyOverlayView()
    private let channelsOverlayView = ChannelsMediaOverlayView()
    private let wechatCardContainer = UIView()
    private let wechatCardSourceIconView = UIImageView()
    private let wechatCardSourceLabel = UILabel()
    private let wechatCardBadgeLabel = PaddingLabel()
    private let wechatCardTitleLabel = UILabel()
    private let wechatCardSubtitleLabel = UILabel()
    private let wechatCardThumbView = UIView()
    private let wechatCardThumbIconView = UIImageView()
    private let wechatCardDividerView = UIView()
    private let wechatCardFooterIconView = UIImageView()
    private let wechatCardFooterLabel = UILabel()
    private let wechatCardChevronView = UIImageView(image: UIImage(systemName: "chevron.right"))
    private var wechatCardThumbWidthConstraint: NSLayoutConstraint?
    private var wechatCardThumbHeightConstraint: NSLayoutConstraint?
    private let miniProgramShareCardView = MiniProgramShareCardView()
    private let linkShareCardView = LinkShareCardView()
    private let contactCardView = ContactCardView()
    private let metaLabel = LayeredShadowLabel()
    private let deliveryStatusLabel = UILabel()

    private var leadingConstraint: NSLayoutConstraint?
    private var trailingConstraint: NSLayoutConstraint?
    private var centerXConstraint: NSLayoutConstraint?
    private var bubbleTopConstraint: NSLayoutConstraint?
    private var contentLeadingConstraint: NSLayoutConstraint?
    private var contentTrailingConstraint: NSLayoutConstraint?
    private var contentBottomConstraint: NSLayoutConstraint?
    private var bubbleMaxWidthConstraint: NSLayoutConstraint?
    private var bubbleRequestedMinimumWidthConstraint: NSLayoutConstraint?
    private var textBubbleWidthConstraint: NSLayoutConstraint?
    private var bubbleMinimumHeightConstraint: NSLayoutConstraint?
    private var metaLeadingConstraint: NSLayoutConstraint?
    private var metaTrailingConstraint: NSLayoutConstraint?
    private var metaRightLeadingConstraint: NSLayoutConstraint?
    private var metaNameTrailingConstraint: NSLayoutConstraint?
    private var inlineNameLeadingConstraint: NSLayoutConstraint?
    private var inlineNameTrailingConstraint: NSLayoutConstraint?
    private var inlineNameCenterYConstraint: NSLayoutConstraint?
    private var inlineNameMediaTopConstraint: NSLayoutConstraint?
    private var inlineNameMediaTrailingConstraint: NSLayoutConstraint?
    private var inlineNameMaxWidthConstraint: NSLayoutConstraint?
    private var inlineNameHeightConstraint: NSLayoutConstraint?
    private var rowTopConstraint: NSLayoutConstraint?
    private var rowBottomConstraint: NSLayoutConstraint?
    private var previewHeightConstraint: NSLayoutConstraint?
    private var previewWidthConstraint: NSLayoutConstraint?
    private var previewTitleLeadingConstraint: NSLayoutConstraint?
    private var previewIconLeadingConstraint: NSLayoutConstraint?
    private var previewIconCenterYConstraint: NSLayoutConstraint?
    private var depthSurfaceLeadingConstraint: NSLayoutConstraint?
    private var depthSurfaceTopConstraint: NSLayoutConstraint?
    private var depthSurfaceWidthConstraint: NSLayoutConstraint?
    private var depthSurfaceHeightConstraint: NSLayoutConstraint?
    private weak var depthSurfaceAnchorView: UIView?
    private var rendersAIMessage = false
    private var isVoicePlaying = false
    private var screenshotMediaBlurEnabled = false
    private var mediaPrivacyBlurEnabled = false
    private var mediaThumbnailRequests: [MediaThumbnailRequest] = []
    private var activeRemoteMediaImageURL: URL?
    private var configuredMessageID: UUID?
    private var inlineCardHitTargets: [(view: UIView, interaction: InlineCardInteraction)] = []
    private var richMediaHitTargets: [(view: UIView, interaction: RichMediaInteraction)] = []
    private var richTextLinkHitTargets: [RichTextLinkHitTarget] = []
    private var usesPreviewConnectionFrame = false
    private var configuredPaymentType: ChatMessageType?
    private var centersGroupMemberAvatarWithBubble = false
    private var configuredBubbleMode: BubbleMode?
    private var configuredUsesUnansweredPresentation = false
    private var configuredIsOutgoing = false
    private var reservesConnectionLane = false
    private var lastTextBubbleLayoutWidth: CGFloat = 0
    private var requestedMinimumBubbleWidth: CGFloat = 0
    private static let wechatMiniProgramShareStyleMarker = "样式：微信小程序转发卡片"
    private static let messageBubbleCornerRadius: CGFloat = 6
    private static let webMessageTextShadows = [
        LayeredTextShadow(offset: CGSize(width: 0, height: 1), blurRadius: 3, color: UIColor.black.withAlphaComponent(0.60)),
        LayeredTextShadow(offset: .zero, blurRadius: 6, color: UIColor.black.withAlphaComponent(0.40)),
        LayeredTextShadow(offset: .zero, blurRadius: 10, color: UIColor.black.withAlphaComponent(0.25)),
        LayeredTextShadow(offset: CGSize(width: 0, height: 1), blurRadius: 1, color: UIColor.black.withAlphaComponent(0.50))
    ]

    private static func messageTextShadows(isAI: Bool) -> [LayeredTextShadow] {
        guard isAI else { return webMessageTextShadows }
        return [
            LayeredTextShadow(
                offset: CGSize(width: 0, height: 1),
                blurRadius: 3,
                color: UIColor(red: 1.0, green: 0.88, blue: 0.42, alpha: 0.26)
            )
        ]
    }
    private static let defaultMediaPreviewSize = CGSize(width: 190, height: 150)
    private static let mediaPreviewMinimumSize = CGSize(width: 118, height: 72)
    private static let mediaPreviewMaximumSize = CGSize(width: 238, height: 260)
    private static let unansweredRecipientMaskCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 16
        return cache
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateConnectionLaneInsetIfNeeded()
        updateTextBubbleWidthIfNeeded()
        unansweredRecipientWatermark.layoutIfNeeded()
        updateUnansweredRecipientWatermarkMask()
        updateAdaptivePaymentWidthIfNeeded()
        updateInlineNameBorderGap()
        onConnectionGeometryDidChange?()
    }

    func configure(
        with message: ChatMessage,
        isVoicePlaying: Bool = false,
        isAIStreaming: Bool = false,
        screenshotMediaBlurEnabled: Bool = false,
        mediaPrivacyBlurEnabled: Bool = false,
        uses3DBubbleAppearance: Bool = false,
        deliveryStatusText: String? = nil,
        showsGroupAvatar: Bool = true,
        showsGroupName: Bool = true,
        usesUnansweredPresentation: Bool = false,
        unansweredRecipientColor: UIColor? = nil,
        unansweredRecipientItem: SidebarItem? = nil,
        unansweredRecipientItems: [SidebarItem] = []
    ) {
        reset()
        configuredMessageID = message.id
        // Unanswered mode is intentionally denser than a normal chat. Restore
        // the standard values first because cells are reused between modes.
        rowStack.spacing = MessageConnectionLayoutMetrics.groupMemberAvatarToBubbleGap
        columnStack.spacing = 4
        contentStack.spacing = 7
        metaLabel.textAlignment = .left
        rendersAIMessage = message.isAI
        self.isVoicePlaying = isVoicePlaying
        self.screenshotMediaBlurEnabled = screenshotMediaBlurEnabled
        self.mediaPrivacyBlurEnabled = mediaPrivacyBlurEnabled
        mediaPrivacyOverlayView.setMessage(mediaPrivacyBlurEnabled ? "申请后可查看" : "截图保护")
        let template = message.type.template
        let mode = Self.mode(for: message.type)
        configuredBubbleMode = mode
        configuredUsesUnansweredPresentation = usesUnansweredPresentation
        let usesCaptionedAttachment = Self.isCaptionedAttachment(message)
        let palette = Self.palette(for: message, mode: mode)
        let showsInlineName = shouldShowInlineName(for: message, mode: mode, showsGroupName: showsGroupName)
        let overlaysInlineNameOnContent = (mode == .media && !usesCaptionedAttachment) || mode == .money
        bubbleMaxWidthConstraint?.constant = MessageConnectionLayoutMetrics.maximumBubbleWidthIncrease
            - (usesUnansweredPresentation ? 11 : 0)
        bubbleMinimumHeightConstraint?.isActive = mode == .text
        centersGroupMemberAvatarWithBubble = message.isGroupConversation
            && !message.isOutgoing
            && showsGroupAvatar
            && !usesUnansweredPresentation
        rowStack.alignment = centersGroupMemberAvatarWithBubble ? .center : .top
        avatarView.layer.borderWidth = centersGroupMemberAvatarWithBubble ? 1 : 0
        avatarView.layer.borderColor = UIColor.white.withAlphaComponent(0.82).cgColor

        unansweredRecipientDot.isHidden = unansweredRecipientColor == nil
        unansweredRecipientDot.backgroundColor = unansweredRecipientColor
        unansweredRecipientDot.layer.shadowColor = unansweredRecipientColor?.cgColor
        unansweredRecipientDot.layer.shadowOpacity = unansweredRecipientColor == nil ? 0 : 0.72
        setUnansweredRecipientItems(
            unansweredRecipientItems.isEmpty ? unansweredRecipientItem.map { [$0] } ?? [] : unansweredRecipientItems
        )

        avatarView.configure(
            initials: message.sender.initials,
            color: message.sender.tintColor,
            avatarURL: message.sender.avatarURL
        )
        senderLabel.text = message.sender.displayName
        inlineNameLabel.text = message.sender.displayName
        applyInlineNamePlacement(for: message, mode: mode, overlaysInlineNameOnContent: overlaysInlineNameOnContent)
        applyInlineNameStyle(for: message)
        metaLabel.text = message.displayTimestamp
        iconView.image = UIImage(systemName: template.symbolName)
        previewIconView.image = UIImage(systemName: template.symbolName)
        iconView.tintColor = palette.accent
        previewIconView.tintColor = .white

        applyDirection(message)
        applyConversation(message, mode: mode, showsInlineName: showsInlineName, showsGroupAvatar: showsGroupAvatar, showsGroupName: showsGroupName)
        reservesConnectionLane = avatarView.isHidden && !Self.isNotificationMessage(message)
        updateConnectionLaneInsetIfNeeded()
        applyPalette(palette, mode: mode, isAI: message.isAI, usesCaptionedAttachment: usesCaptionedAttachment)
        bubbleView.setDashedBorderAnimation(active: message.isAI && isAIStreaming)
        applyContent(message, template: template, mode: mode, usesCaptionedAttachment: usesCaptionedAttachment)
        if usesUnansweredPresentation {
            detailLabel.font = .systemFont(ofSize: 11, weight: .regular)
            metaLabel.font = .systemFont(ofSize: 9, weight: .medium)
            metaLabel.textAlignment = .left
            rowStack.spacing = 4
            columnStack.spacing = 2
            contentStack.spacing = 3
        }
        if message.isAI {
            applyAITextColor()
        }
        applyDeliveryStatusText(deliveryStatusText)
        applyDimensionalAppearance(
            enabled: uses3DBubbleAppearance,
            message: message,
            mode: mode,
            usesCaptionedAttachment: usesCaptionedAttachment
        )

        inlineNameLabel.isHidden = message.sender.displayName.isEmpty || !showsInlineName
        let displaysInlineName = !inlineNameLabel.isHidden
        applyRowSpacing(showsInlineName: displaysInlineName, overlaysMediaName: overlaysInlineNameOnContent, mode: mode)
        applyContentInsets(mode: mode, showsInlineName: displaysInlineName, usesCaptionedAttachment: usesCaptionedAttachment)
        applyMetadataAlignment(
            alignsToOutgoingGroupName: message.isGroupConversation
                && message.isOutgoing
                && !usesUnansweredPresentation
        )
        updateTextBubbleWidthIfNeeded(force: true)
        if usesUnansweredPresentation {
            // Apply this after applyRowSpacing so group names cannot restore
            // an outer top inset above the actual message bubble.
            rowTopConstraint?.constant = 0
            rowBottomConstraint?.constant = 0
        }
    }

    private func applyDeliveryStatusText(_ deliveryStatusText: String?) {
        let resolvedText = deliveryStatusText?.trimmingCharacters(in: .whitespacesAndNewlines)
        deliveryStatusLabel.text = resolvedText
        deliveryStatusLabel.isHidden = resolvedText?.isEmpty ?? true
        guard let resolvedText, !resolvedText.isEmpty else {
            deliveryStatusLabel.textColor = Self.mutedColor
            return
        }
        switch resolvedText {
        case let text where text.contains("失败"):
            deliveryStatusLabel.textColor = UIColor.systemRed.withAlphaComponent(0.96)
        case "发送中", "等待确认":
            deliveryStatusLabel.textColor = UIColor.systemOrange.withAlphaComponent(0.94)
        default:
            deliveryStatusLabel.textColor = Self.mutedColor
        }
    }

    private func applyMetadataAlignment(alignsToOutgoingGroupName: Bool) {
        metaLeadingConstraint?.isActive = !alignsToOutgoingGroupName
        metaTrailingConstraint?.isActive = !alignsToOutgoingGroupName
        metaRightLeadingConstraint?.isActive = alignsToOutgoingGroupName
        metaNameTrailingConstraint?.isActive = alignsToOutgoingGroupName
        metaLabel.textAlignment = alignsToOutgoingGroupName ? .right : .left
    }

    func connectionBubbleFrame(in view: UIView) -> CGRect {
        if usesPreviewConnectionFrame, !previewView.isHidden {
            return previewView.convert(previewView.bounds, to: view)
        }
        return bubbleView.convert(bubbleView.bounds, to: view)
    }

    func groupMemberAvatarFrame(in view: UIView) -> CGRect? {
        guard centersGroupMemberAvatarWithBubble, !avatarView.isHidden else { return nil }
        let frame = avatarView.convert(avatarView.bounds, to: view)
        guard frame.origin.x.isFinite,
              frame.origin.y.isFinite,
              frame.width > 1,
              frame.height > 1
        else { return nil }
        return frame
    }

    func setUnansweredRecipientItem(_ item: SidebarItem?) {
        setUnansweredRecipientItems(item.map { [$0] } ?? [])
    }

    func setMinimumBubbleWidth(_ width: CGFloat) {
        requestedMinimumBubbleWidth = max(0, width)
        bubbleRequestedMinimumWidthConstraint?.constant = requestedMinimumBubbleWidth
        bubbleRequestedMinimumWidthConstraint?.isActive = requestedMinimumBubbleWidth > 0
        updateTextBubbleWidthIfNeeded(force: true)
        setNeedsLayout()
    }

    func setUnansweredRecipientItems(_ items: [SidebarItem]) {
        if items.isEmpty {
            unansweredRecipientWatermark.prepareForReuse()
        } else {
            unansweredRecipientWatermark.configure(with: items)
            unansweredRecipientWatermark.isHidden = false
            unansweredRecipientWatermarkWidthConstraint.constant = unansweredRecipientWatermark.preferredWidth
            unansweredRecipientWatermark.setNeedsLayout()
        }
        setNeedsLayout()
    }

    private func updateUnansweredRecipientWatermarkMask() {
        let size = unansweredRecipientWatermark.bounds.size
        guard size.width > 0, size.height > 0 else { return }
        unansweredRecipientWatermarkMask.frame = unansweredRecipientWatermark.bounds
        guard abs(size.width - unansweredRecipientWatermarkMaskSize.width) > 0.5
                || abs(size.height - unansweredRecipientWatermarkMaskSize.height) > 0.5
        else { return }

        unansweredRecipientWatermarkMaskSize = size
        let format = UIGraphicsImageRendererFormat.default()
        format.opaque = false
        format.scale = window?.screen.scale ?? UIScreen.main.scale
        let key = String(
            format: "%.1fx%.1f@%.1f",
            size.width,
            size.height,
            format.scale
        ) as NSString
        if let cachedImage = Self.unansweredRecipientMaskCache.object(forKey: key) {
            unansweredRecipientWatermarkMask.contentsScale = format.scale
            unansweredRecipientWatermarkMask.contents = cachedImage.cgImage
            return
        }
        let image = UIGraphicsImageRenderer(size: size, format: format).image { rendererContext in
            let context = rendererContext.cgContext
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let colors = [UIColor.clear.cgColor, UIColor.white.cgColor] as CFArray
            guard let gradient = CGGradient(
                colorsSpace: colorSpace,
                colors: colors,
                locations: [0, 1]
            ) else { return }

            context.drawLinearGradient(
                gradient,
                start: CGPoint(x: size.width / 2, y: 0),
                end: CGPoint(x: size.width / 2, y: size.height / 2),
                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
            )
            context.setBlendMode(.destinationIn)
            context.drawLinearGradient(
                gradient,
                start: CGPoint(x: 0, y: size.height / 2),
                end: CGPoint(x: size.width / 2, y: size.height / 2),
                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
            )
        }
        Self.unansweredRecipientMaskCache.setObject(image, forKey: key)
        unansweredRecipientWatermarkMask.contentsScale = format.scale
        unansweredRecipientWatermarkMask.contents = image.cgImage
    }

    func recipientWatermarkBubbleFrame(in view: UIView) -> CGRect {
        return bubbleView.convert(bubbleView.bounds, to: view)
    }

    func recipientWatermarkFrame(in view: UIView) -> CGRect? {
        guard !unansweredRecipientWatermark.isHidden else { return nil }
        return unansweredRecipientWatermark.convert(unansweredRecipientWatermark.bounds, to: view)
    }

    func recipientWatermarkRightmostAvatarFrame(in view: UIView) -> CGRect? {
        guard !unansweredRecipientWatermark.isHidden else { return nil }
        return unansweredRecipientWatermark.rightmostAvatarFrame(in: view)
    }

    func recipientColorDotFrame(in view: UIView) -> CGRect? {
        guard !unansweredRecipientDot.isHidden else { return nil }
        return unansweredRecipientDot.convert(unansweredRecipientDot.bounds, to: view)
    }

    func recipientColorDotColor() -> UIColor? {
        unansweredRecipientDot.backgroundColor
    }

    func setOutgoingGroupAccountPresentation(_ enabled: Bool) {
        applyMetadataAlignment(alignsToOutgoingGroupName: enabled)
    }

    func metadataFrame(in view: UIView) -> CGRect? {
        guard !metaLabel.isHidden else { return nil }
        return metaLabel.convert(metaLabel.bounds, to: view)
    }

    func inlineNameFrame(in view: UIView) -> CGRect? {
        guard !inlineNameLabel.isHidden else { return nil }
        return inlineNameLabel.convert(inlineNameLabel.bounds, to: view)
    }

    private static func isCaptionedAttachment(_ message: ChatMessage) -> Bool {
        switch message.type {
        case .image, .capturedPhoto, .video, .file:
            return message.detail.contains("附件说明消息")
        default:
            return false
        }
    }

    static func estimatedHeight(
        for message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool = true,
        showsGroupName: Bool = true,
        usesUnansweredPresentation: Bool = false,
        deliveryStatusText: String? = nil
    ) -> CGFloat {
        let signpostID = PerformanceSignpost.begin("BubbleEstimatedHeight")
        defer { PerformanceSignpost.end("BubbleEstimatedHeight", id: signpostID) }
        return heuristicHeight(
            for: message,
            width: width,
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation,
            deliveryStatusText: deliveryStatusText
        )
    }

    private static func heuristicHeight(
        for message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool = true,
        showsGroupName: Bool = true,
        usesUnansweredPresentation: Bool = false,
        deliveryStatusText: String? = nil
    ) -> CGFloat {
        let mode = Self.mode(for: message.type)
        let usesCaptionedAttachment = Self.isCaptionedAttachment(message)
        let usesCompactContentHeight = usesUnansweredPresentation
            || (!message.isGroupConversation
                && (mode == .voice || mode == .money || !message.richElements.isEmpty))
        let showsAvatar = message.isGroupConversation ? showsGroupAvatar : showsAvatarStatic(for: message)
        let avatarAndSpacing: CGFloat = showsAvatar
            ? MessageConnectionLayoutMetrics.groupMemberOccupiedWidth
            : 0
        let unansweredWidthReserve: CGFloat = usesUnansweredPresentation ? 11 : 0
        let maxBubbleWidth = MessageConnectionLayoutMetrics.maximumBubbleWidth(
            in: width,
            occupiedWidth: avatarAndSpacing,
            reservedWidth: unansweredWidthReserve,
            minimumWidth: 126
        )
        let textWidth = max(96, maxBubbleWidth - 14)
        let cellVerticalChrome: CGFloat
        if usesUnansweredPresentation {
            cellVerticalChrome = 0
        } else if usesCompactContentHeight {
            cellVerticalChrome = 6
        } else {
            cellVerticalChrome = Self.isNotificationMessage(message) ? 4 : 8
        }
        let senderHeight: CGFloat = showsExternalSender(for: message, mode: mode, showsGroupName: showsGroupName) ? 16 : 0
        let showsInlineName = shouldShowInlineNameStatic(for: message, mode: mode, showsGroupName: showsGroupName)
        let overlaysInlineNameOnMedia = (mode == .media && !usesCaptionedAttachment) || mode == .money
        let inlineNameCellPadding: CGFloat
        if mode == .money && showsInlineName {
            inlineNameCellPadding = 16
        } else {
            inlineNameCellPadding = showsInlineName && !overlaysInlineNameOnMedia ? 12 : 0
        }
        let richContentWidth = max(1, maxBubbleWidth - 24)
        let singleInlineCardHeight: CGFloat? = {
            guard message.richElements.count == 1,
                  case .inlineCard(_, let title, let subtitle, _) = message.richElements[0]
            else { return nil }
            return estimatedInlineCardHeight(
                title: title,
                subtitle: subtitle,
                width: richContentWidth
            )
        }()
        let richHeight = singleInlineCardHeight
            ?? richElementsHeight(message.richElements, width: richContentWidth)
        let bodyHeight = message.richElements.isEmpty
            ? measuredHeight(
                message.body,
                font: bodyFont(for: mode),
                width: textWidth,
                maxLines: mode == .text ? 0 : 2,
                lineHeight: mode == .text ? 16.8 : nil
            )
            : richHeight
        let baseCardHeight = cardHeight(for: message, mode: mode)
        let mediaPreviewHeight = defaultMediaPreviewSize.height

        let stackSpacing: CGFloat = mode == .text ? 3 : 7
        let metaHeight = ceil(UIFont.systemFont(ofSize: 10, weight: .medium).lineHeight)
        let normalTopInset: CGFloat = mode == .text
            ? 7
            : (showsInlineName && !overlaysInlineNameOnMedia ? 20 : 11)
        let normalBottomInset: CGFloat = mode == .text ? 7 : 10
        let titleRowHeight: CGFloat = 20
        let safetyPadding: CGFloat = mode == .text || usesCompactContentHeight ? 2 : 10
        let normalInsets = normalTopInset + normalBottomInset

        func verticalStackHeight(_ parts: [CGFloat], top: CGFloat = normalTopInset, bottom: CGFloat = normalBottomInset) -> CGFloat {
            let visibleParts = parts.filter { $0 > 0 }
            guard !visibleParts.isEmpty else { return top + bottom }
            return top
                + visibleParts.reduce(0, +)
                + stackSpacing * CGFloat(max(visibleParts.count - 1, 0))
                + bottom
        }

        let contentHeight: CGFloat
        if let singleInlineCardHeight {
            contentHeight = max(
                34,
                verticalStackHeight(
                    [singleInlineCardHeight],
                    top: normalTopInset,
                    bottom: normalBottomInset
                )
            )
        } else if !message.richElements.isEmpty {
            let richMinimum: CGFloat = usesCompactContentHeight
                ? 0
                : (message.richElements.count == 1 ? 34 : 44)
            contentHeight = max(richMinimum, verticalStackHeight([richHeight], top: normalTopInset, bottom: normalBottomInset))
        } else {
            switch mode {
        case .system:
            contentHeight = max(34, verticalStackHeight([bodyHeight], top: 7, bottom: 7))
        case .text:
            contentHeight = max(37, verticalStackHeight([bodyHeight]))
        case .oversizedText:
            contentHeight = max(62, verticalStackHeight([bodyHeight, metaHeight]))
        case .media:
            if usesCaptionedAttachment {
                contentHeight = max(160, verticalStackHeight([bodyHeight, mediaPreviewHeight, metaHeight]))
            } else {
                contentHeight = mediaPreviewHeight
            }
        case .channelVideo:
            contentHeight = 276 + (showsInlineName ? 20 : 0)
        case .document:
            if usesCaptionedAttachment {
                contentHeight = max(170, verticalStackHeight([bodyHeight, 104, metaHeight]))
            } else {
                contentHeight = max(144, verticalStackHeight([104, metaHeight]))
            }
        case .voice, .call:
            let previewHeight: CGFloat = mode == .voice ? 58 : 56
            contentHeight = max(78, verticalStackHeight([previewHeight], top: normalTopInset, bottom: 9))
        case .money:
            let paymentHeight: CGFloat = message.type == .redPacket ? 96 : 92
            contentHeight = verticalStackHeight([paymentHeight], top: 0, bottom: 0)
        case .quote:
            contentHeight = max(112, verticalStackHeight([titleRowHeight, bodyHeight, 50, metaHeight], top: normalTopInset, bottom: 8))
        case .location:
            contentHeight = max(baseCardHeight, verticalStackHeight([titleRowHeight, bodyHeight, baseCardHeight - 68, metaHeight]))
        case .profile, .link:
            contentHeight = max(baseCardHeight, verticalStackHeight([baseCardHeight == 0 ? 112 : max(112, baseCardHeight - normalInsets - metaHeight - stackSpacing), metaHeight]))
        case .stacked, .notice:
            contentHeight = max(baseCardHeight, verticalStackHeight([titleRowHeight, bodyHeight, max(58, baseCardHeight - 88), metaHeight]))
            }
        }

        let senderSpacing: CGFloat = senderHeight > 0 ? 4 : 0
        let minimumHeight: CGFloat
        if usesCompactContentHeight {
            minimumHeight = 0
        } else if mode == .text {
            minimumHeight = min(message.type.template.minimumReadableHeight, 34)
        } else {
            minimumHeight = message.type.template.minimumReadableHeight
        }
        let resolvedSafetyPadding: CGFloat = singleInlineCardHeight == nil ? safetyPadding : 1
        let deliveryStatusHeight: CGFloat = {
            guard let text = deliveryStatusText?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !text.isEmpty
            else { return 0 }
            let labelHeight = measuredHeight(
                text,
                font: .systemFont(ofSize: 10, weight: .medium),
                width: maxBubbleWidth,
                maxLines: 0
            )
            return 3 + max(ceil(labelHeight), 12)
        }()
        return ceil(max(
            minimumHeight,
            cellVerticalChrome
                + inlineNameCellPadding
                + senderHeight
                + senderSpacing
                + contentHeight
                + deliveryStatusHeight
                + resolvedSafetyPadding
        ))
    }

    private static func mediaPreviewSize(for image: UIImage?) -> CGSize {
        guard let image, image.size.width > 0, image.size.height > 0 else {
            return defaultMediaPreviewSize
        }
        let aspect = max(0.20, min(5.0, image.size.width / image.size.height))
        var width = min(mediaPreviewMaximumSize.width, max(mediaPreviewMinimumSize.width, defaultMediaPreviewSize.width))
        var height = width / aspect
        if height > mediaPreviewMaximumSize.height {
            height = mediaPreviewMaximumSize.height
            width = height * aspect
        }
        let minimumHeight: CGFloat = aspect > 2.3 ? 54 : mediaPreviewMinimumSize.height
        if height < minimumHeight {
            width = min(mediaPreviewMaximumSize.width, max(width, minimumHeight * aspect))
            height = width / aspect
        }
        width = min(mediaPreviewMaximumSize.width, max(72, width))
        height = min(mediaPreviewMaximumSize.height, max(44, height))
        return CGSize(width: round(width), height: round(height))
    }

    private static func richImagePreviewHeight(url: String, aspect: BubbleImageAspect) -> CGFloat {
        estimatedRichImagePreviewHeight(aspect: aspect)
    }

    private static func estimatedRichImagePreviewHeight(aspect: BubbleImageAspect) -> CGFloat {
        aspect == .vertical ? 168 : 96
    }

    private func setup() {
        backgroundColor = .clear
        depthSurfaceView.isHidden = true
        depthSurfaceView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(depthSurfaceView)

        rowStack.axis = .horizontal
        rowStack.alignment = .top
        rowStack.spacing = MessageConnectionLayoutMetrics.groupMemberAvatarToBubbleGap
        rowStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(rowStack)

        avatarView.layer.cornerRadius = 10
        avatarView.layer.cornerCurve = .continuous
        avatarView.clipsToBounds = true
        avatarView.widthAnchor.constraint(
            equalToConstant: MessageConnectionLayoutMetrics.groupMemberAvatarSide
        ).isActive = true
        avatarView.heightAnchor.constraint(
            equalToConstant: MessageConnectionLayoutMetrics.groupMemberAvatarSide
        ).isActive = true

        columnStack.axis = .vertical
        columnStack.alignment = .leading
        columnStack.spacing = 4

        senderLabel.font = .systemFont(ofSize: 12, weight: .regular)
        senderLabel.textColor = .white

        bubbleView.layer.cornerRadius = Self.messageBubbleCornerRadius
        bubbleView.layer.cornerCurve = .continuous
        bubbleView.translatesAutoresizingMaskIntoConstraints = false
        bubbleView.addSubview(contentStack)
        bubbleView.addSubview(inlineNameLabel)
        metaLabel.translatesAutoresizingMaskIntoConstraints = false
        bubbleView.addSubview(metaLabel)
        unansweredRecipientWatermark.isUserInteractionEnabled = false
        unansweredRecipientWatermark.alpha = 1
        unansweredRecipientWatermark.layer.cornerRadius = 6
        unansweredRecipientWatermark.layer.maskedCorners = [.layerMaxXMaxYCorner]
        unansweredRecipientWatermark.clipsToBounds = true
        unansweredRecipientWatermark.translatesAutoresizingMaskIntoConstraints = false
        unansweredRecipientWatermark.layer.mask = unansweredRecipientWatermarkMask
        bubbleView.insertSubview(unansweredRecipientWatermark, belowSubview: contentStack)

        inlineNameLabel.font = .systemFont(ofSize: 12, weight: .regular)
        inlineNameLabel.textColor = .white
        inlineNameLabel.shadowColor = nil
        inlineNameLabel.shadowOffset = .zero
        inlineNameLabel.backgroundColor = .clear
        inlineNameLabel.layer.cornerRadius = 0
        inlineNameLabel.layer.borderWidth = 0
        inlineNameLabel.layer.borderColor = UIColor.clear.cgColor
        inlineNameLabel.clipsToBounds = false
        inlineNameLabel.translatesAutoresizingMaskIntoConstraints = false

        contentStack.axis = .vertical
        contentStack.spacing = 7
        contentStack.alignment = .fill
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        titleRow.axis = .horizontal
        titleRow.spacing = 7
        titleRow.alignment = .center
        iconView.contentMode = .scaleAspectFit
        iconView.widthAnchor.constraint(equalToConstant: 18).isActive = true
        iconView.heightAnchor.constraint(equalToConstant: 18).isActive = true

        [titleLabel, bodyLabel, detailLabel, previewTitleLabel, previewDetailLabel, metaLabel].forEach {
            $0.lineBreakMode = .byWordWrapping
            $0.setContentCompressionResistancePriority(.required, for: .vertical)
        }
        applyBubbleTextShadow()
        titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        titleLabel.numberOfLines = 2
        bodyLabel.font = Self.bodyFont(for: .text)
        bodyLabel.numberOfLines = 0
        richStack.axis = .vertical
        richStack.spacing = 7
        richStack.alignment = .fill
        richStack.isHidden = true
        detailLabel.font = .systemFont(ofSize: 12)
        detailLabel.numberOfLines = 3
        metaLabel.font = .systemFont(ofSize: 10, weight: .medium)
        metaLabel.textAlignment = .right
        deliveryStatusLabel.font = .systemFont(ofSize: 10, weight: .medium)
        deliveryStatusLabel.numberOfLines = 0
        deliveryStatusLabel.lineBreakMode = .byWordWrapping
        deliveryStatusLabel.isHidden = true
        deliveryStatusLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        deliveryStatusLabel.setContentCompressionResistancePriority(.required, for: .horizontal)

        previewView.layer.cornerRadius = 12
        previewView.layer.cornerCurve = .continuous
        previewView.clipsToBounds = true
        previewView.translatesAutoresizingMaskIntoConstraints = false
        mediaImageView.contentMode = .scaleAspectFill
        mediaImageView.clipsToBounds = true
        mediaImageView.isHidden = true
        mediaImageView.translatesAutoresizingMaskIntoConstraints = false
        mediaPrivacyOverlayView.reset()
        channelsOverlayView.reset()
        mediaPlayIconView.tintColor = UIColor.white.withAlphaComponent(0.92)
        mediaPlayIconView.contentMode = .scaleAspectFit
        mediaPlayIconView.isHidden = true
        mediaPlayIconView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardContainer.isHidden = true
        wechatCardContainer.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        wechatCardContainer.layer.cornerRadius = 12
        wechatCardContainer.layer.cornerCurve = .continuous
        wechatCardContainer.clipsToBounds = true
        wechatCardContainer.translatesAutoresizingMaskIntoConstraints = false
        wechatCardSourceIconView.contentMode = .scaleAspectFit
        wechatCardSourceIconView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardSourceLabel.font = .systemFont(ofSize: 11, weight: .medium)
        wechatCardSourceLabel.textColor = UIColor(red: 0.36, green: 0.42, blue: 0.45, alpha: 1)
        wechatCardSourceLabel.numberOfLines = 1
        wechatCardSourceLabel.translatesAutoresizingMaskIntoConstraints = false
        wechatCardBadgeLabel.font = .systemFont(ofSize: 9.5, weight: .bold)
        wechatCardBadgeLabel.textColor = .white
        wechatCardBadgeLabel.textInsets = UIEdgeInsets(top: 2, left: 6, bottom: 2, right: 6)
        wechatCardBadgeLabel.layer.cornerRadius = 7
        wechatCardBadgeLabel.layer.cornerCurve = .continuous
        wechatCardBadgeLabel.clipsToBounds = true
        wechatCardBadgeLabel.translatesAutoresizingMaskIntoConstraints = false
        wechatCardTitleLabel.font = .systemFont(ofSize: 14.5, weight: .semibold)
        wechatCardTitleLabel.textColor = Self.textColor
        wechatCardTitleLabel.numberOfLines = 2
        wechatCardTitleLabel.translatesAutoresizingMaskIntoConstraints = false
        wechatCardSubtitleLabel.font = .systemFont(ofSize: 11.5, weight: .regular)
        wechatCardSubtitleLabel.textColor = Self.detailColor
        wechatCardSubtitleLabel.numberOfLines = 2
        wechatCardSubtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        wechatCardThumbView.layer.cornerRadius = 9
        wechatCardThumbView.layer.cornerCurve = .continuous
        wechatCardThumbView.clipsToBounds = true
        wechatCardThumbView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardThumbIconView.contentMode = .scaleAspectFit
        wechatCardThumbIconView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardDividerView.backgroundColor = UIColor(red: 0.89, green: 0.91, blue: 0.92, alpha: 1)
        wechatCardDividerView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardFooterIconView.contentMode = .scaleAspectFit
        wechatCardFooterIconView.translatesAutoresizingMaskIntoConstraints = false
        wechatCardFooterLabel.font = .systemFont(ofSize: 10.5, weight: .medium)
        wechatCardFooterLabel.textColor = UIColor(red: 0.48, green: 0.54, blue: 0.58, alpha: 1)
        wechatCardFooterLabel.numberOfLines = 1
        wechatCardFooterLabel.translatesAutoresizingMaskIntoConstraints = false
        wechatCardChevronView.tintColor = UIColor(red: 0.62, green: 0.67, blue: 0.70, alpha: 1)
        wechatCardChevronView.contentMode = .scaleAspectFit
        wechatCardChevronView.translatesAutoresizingMaskIntoConstraints = false
        previewIconView.contentMode = .scaleAspectFit
        previewIconView.translatesAutoresizingMaskIntoConstraints = false
        previewTitleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        previewTitleLabel.numberOfLines = 2
        previewDetailLabel.font = .systemFont(ofSize: 11)
        previewDetailLabel.numberOfLines = 3
        previewTitleLabel.translatesAutoresizingMaskIntoConstraints = false
        previewDetailLabel.translatesAutoresizingMaskIntoConstraints = false
        paymentContentView.isHidden = true
        previewView.addSubview(paymentContentView)
        previewView.addSubview(mediaImageView)
        previewView.addSubview(mediaPrivacyOverlayView)
        previewView.addSubview(channelsOverlayView)
        previewView.addSubview(documentCardView)
        previewView.addSubview(previewIconView)
        previewView.addSubview(previewTitleLabel)
        previewView.addSubview(previewDetailLabel)
        previewView.addSubview(mediaPlayIconView)
        wechatCardContainer.addSubview(wechatCardSourceIconView)
        wechatCardContainer.addSubview(wechatCardSourceLabel)
        wechatCardContainer.addSubview(wechatCardBadgeLabel)
        wechatCardContainer.addSubview(wechatCardTitleLabel)
        wechatCardContainer.addSubview(wechatCardSubtitleLabel)
        wechatCardContainer.addSubview(wechatCardThumbView)
        wechatCardThumbView.addSubview(wechatCardThumbIconView)
        wechatCardContainer.addSubview(wechatCardDividerView)
        wechatCardContainer.addSubview(wechatCardFooterIconView)
        wechatCardContainer.addSubview(wechatCardFooterLabel)
        wechatCardContainer.addSubview(wechatCardChevronView)
        titleRow.addArrangedSubview(iconView)
        titleRow.addArrangedSubview(titleLabel)
        contentStack.addArrangedSubview(titleRow)
        contentStack.addArrangedSubview(bodyLabel)
        contentStack.addArrangedSubview(richStack)
        contentStack.addArrangedSubview(contactCardView)
        contentStack.addArrangedSubview(wechatCardContainer)
        contentStack.addArrangedSubview(miniProgramShareCardView)
        contentStack.addArrangedSubview(linkShareCardView)
        contentStack.addArrangedSubview(previewView)
        contentStack.addArrangedSubview(detailLabel)

        columnStack.addArrangedSubview(senderLabel)
        columnStack.addArrangedSubview(bubbleView)
        columnStack.addArrangedSubview(deliveryStatusLabel)
        columnStack.setCustomSpacing(3, after: bubbleView)
        rowStack.addArrangedSubview(avatarView)
        rowStack.addArrangedSubview(columnStack)
        unansweredRecipientDot.isUserInteractionEnabled = false
        unansweredRecipientDot.layer.cornerRadius = 4.5
        unansweredRecipientDot.layer.borderWidth = 1.5
        unansweredRecipientDot.layer.borderColor = UIColor.white.withAlphaComponent(0.9).cgColor
        unansweredRecipientDot.layer.shadowRadius = 3
        unansweredRecipientDot.layer.shadowOffset = .zero
        unansweredRecipientDot.translatesAutoresizingMaskIntoConstraints = false
        addSubview(unansweredRecipientDot)

        bubbleTopConstraint = contentStack.topAnchor.constraint(equalTo: bubbleView.topAnchor, constant: 11)
        contentLeadingConstraint = contentStack.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor, constant: 12)
        contentTrailingConstraint = contentStack.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor, constant: -12)
        contentBottomConstraint = contentStack.bottomAnchor.constraint(equalTo: bubbleView.bottomAnchor, constant: -10)
        inlineNameHeightConstraint = inlineNameLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 18)
        rowTopConstraint = rowStack.topAnchor.constraint(equalTo: topAnchor, constant: 3)
        rowBottomConstraint = rowStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -3)
        previewHeightConstraint = previewView.heightAnchor.constraint(equalToConstant: 76)
        previewWidthConstraint = previewView.widthAnchor.constraint(equalToConstant: 180)
        previewIconLeadingConstraint = previewIconView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor, constant: 10)
        previewIconCenterYConstraint = previewIconView.centerYAnchor.constraint(equalTo: previewView.centerYAnchor)
        wechatCardThumbWidthConstraint = wechatCardThumbView.widthAnchor.constraint(equalToConstant: 58)
        wechatCardThumbHeightConstraint = wechatCardThumbView.heightAnchor.constraint(equalToConstant: 58)

        previewTitleLeadingConstraint = previewTitleLabel.leadingAnchor.constraint(equalTo: previewIconView.trailingAnchor, constant: 10)
        depthSurfaceLeadingConstraint = depthSurfaceView.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor)
        depthSurfaceTopConstraint = depthSurfaceView.topAnchor.constraint(equalTo: bubbleView.topAnchor)
        depthSurfaceWidthConstraint = depthSurfaceView.widthAnchor.constraint(equalTo: bubbleView.widthAnchor)
        depthSurfaceHeightConstraint = depthSurfaceView.heightAnchor.constraint(equalTo: bubbleView.heightAnchor)

        unansweredRecipientWatermarkWidthConstraint = unansweredRecipientWatermark.widthAnchor.constraint(
            equalToConstant: UnansweredRecipientAvatarStackView.avatarSide
        )
        metaLeadingConstraint = metaLabel.leadingAnchor.constraint(
            equalTo: bubbleView.leadingAnchor,
            constant: 12
        )
        metaTrailingConstraint = metaLabel.trailingAnchor.constraint(
            lessThanOrEqualTo: bubbleView.trailingAnchor,
            constant: -12
        )
        metaRightLeadingConstraint = metaLabel.leadingAnchor.constraint(
            greaterThanOrEqualTo: bubbleView.leadingAnchor,
            constant: 8
        )
        metaNameTrailingConstraint = metaLabel.trailingAnchor.constraint(
            equalTo: inlineNameLabel.trailingAnchor
        )
        bubbleMaxWidthConstraint = bubbleView.widthAnchor.constraint(
            lessThanOrEqualTo: widthAnchor,
            multiplier: MessageConnectionLayoutMetrics.maximumBubbleWidthRatio,
            constant: MessageConnectionLayoutMetrics.maximumBubbleWidthIncrease
        )
        bubbleRequestedMinimumWidthConstraint = bubbleView.widthAnchor.constraint(
            greaterThanOrEqualToConstant: 0
        )
        bubbleRequestedMinimumWidthConstraint?.priority = UILayoutPriority(998)
        bubbleMinimumHeightConstraint = bubbleView.heightAnchor.constraint(greaterThanOrEqualToConstant: 37)
        NSLayoutConstraint.activate([
            rowTopConstraint!,
            rowBottomConstraint!,
            bubbleMaxWidthConstraint!,
            unansweredRecipientDot.centerXAnchor.constraint(equalTo: bubbleView.trailingAnchor),
            unansweredRecipientDot.centerYAnchor.constraint(equalTo: bubbleView.centerYAnchor),
            unansweredRecipientDot.widthAnchor.constraint(equalToConstant: 9),
            unansweredRecipientDot.heightAnchor.constraint(equalToConstant: 9),

            depthSurfaceLeadingConstraint!,
            depthSurfaceTopConstraint!,
            depthSurfaceWidthConstraint!,
            depthSurfaceHeightConstraint!,

            bubbleTopConstraint!,
            metaLeadingConstraint!,
            metaTrailingConstraint!,
            metaLabel.centerYAnchor.constraint(equalTo: bubbleView.bottomAnchor),
            unansweredRecipientWatermarkWidthConstraint,
            unansweredRecipientWatermark.heightAnchor.constraint(
                equalToConstant: UnansweredRecipientAvatarStackView.avatarSide
            ),
            unansweredRecipientWatermark.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor),
            unansweredRecipientWatermark.bottomAnchor.constraint(equalTo: bubbleView.bottomAnchor),
            contentLeadingConstraint!,
            contentTrailingConstraint!,
            contentBottomConstraint!,

            inlineNameHeightConstraint!,

            previewHeightConstraint!,
            paymentContentView.topAnchor.constraint(equalTo: previewView.topAnchor),
            paymentContentView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor),
            paymentContentView.trailingAnchor.constraint(equalTo: previewView.trailingAnchor),
            paymentContentView.bottomAnchor.constraint(equalTo: previewView.bottomAnchor),
            mediaImageView.topAnchor.constraint(equalTo: previewView.topAnchor),
            mediaImageView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor),
            mediaImageView.trailingAnchor.constraint(equalTo: previewView.trailingAnchor),
            mediaImageView.bottomAnchor.constraint(equalTo: previewView.bottomAnchor),
            mediaPrivacyOverlayView.topAnchor.constraint(equalTo: previewView.topAnchor),
            mediaPrivacyOverlayView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor),
            mediaPrivacyOverlayView.trailingAnchor.constraint(equalTo: previewView.trailingAnchor),
            mediaPrivacyOverlayView.bottomAnchor.constraint(equalTo: previewView.bottomAnchor),
            channelsOverlayView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor),
            channelsOverlayView.trailingAnchor.constraint(equalTo: previewView.trailingAnchor),
            channelsOverlayView.bottomAnchor.constraint(equalTo: previewView.bottomAnchor),
            channelsOverlayView.heightAnchor.constraint(greaterThanOrEqualToConstant: 78),
            mediaPlayIconView.centerXAnchor.constraint(equalTo: previewView.centerXAnchor),
            mediaPlayIconView.centerYAnchor.constraint(equalTo: previewView.centerYAnchor),
            mediaPlayIconView.widthAnchor.constraint(equalToConstant: 42),
            mediaPlayIconView.heightAnchor.constraint(equalToConstant: 42),
            wechatCardContainer.heightAnchor.constraint(greaterThanOrEqualToConstant: 112),
            wechatCardSourceIconView.leadingAnchor.constraint(equalTo: wechatCardContainer.leadingAnchor, constant: 12),
            wechatCardSourceIconView.topAnchor.constraint(equalTo: wechatCardContainer.topAnchor, constant: 10),
            wechatCardSourceIconView.widthAnchor.constraint(equalToConstant: 16),
            wechatCardSourceIconView.heightAnchor.constraint(equalToConstant: 16),
            wechatCardSourceLabel.leadingAnchor.constraint(equalTo: wechatCardSourceIconView.trailingAnchor, constant: 5),
            wechatCardSourceLabel.centerYAnchor.constraint(equalTo: wechatCardSourceIconView.centerYAnchor),
            wechatCardSourceLabel.trailingAnchor.constraint(lessThanOrEqualTo: wechatCardBadgeLabel.leadingAnchor, constant: -8),
            wechatCardBadgeLabel.trailingAnchor.constraint(equalTo: wechatCardContainer.trailingAnchor, constant: -12),
            wechatCardBadgeLabel.centerYAnchor.constraint(equalTo: wechatCardSourceIconView.centerYAnchor),
            wechatCardThumbView.trailingAnchor.constraint(equalTo: wechatCardContainer.trailingAnchor, constant: -12),
            wechatCardThumbView.topAnchor.constraint(equalTo: wechatCardSourceIconView.bottomAnchor, constant: 12),
            wechatCardThumbWidthConstraint!,
            wechatCardThumbHeightConstraint!,
            wechatCardThumbIconView.centerXAnchor.constraint(equalTo: wechatCardThumbView.centerXAnchor),
            wechatCardThumbIconView.centerYAnchor.constraint(equalTo: wechatCardThumbView.centerYAnchor),
            wechatCardThumbIconView.widthAnchor.constraint(equalTo: wechatCardThumbView.widthAnchor, multiplier: 0.44),
            wechatCardThumbIconView.heightAnchor.constraint(equalTo: wechatCardThumbView.heightAnchor, multiplier: 0.44),
            wechatCardTitleLabel.leadingAnchor.constraint(equalTo: wechatCardContainer.leadingAnchor, constant: 12),
            wechatCardTitleLabel.trailingAnchor.constraint(equalTo: wechatCardThumbView.leadingAnchor, constant: -10),
            wechatCardTitleLabel.topAnchor.constraint(equalTo: wechatCardThumbView.topAnchor, constant: 1),
            wechatCardSubtitleLabel.leadingAnchor.constraint(equalTo: wechatCardTitleLabel.leadingAnchor),
            wechatCardSubtitleLabel.trailingAnchor.constraint(equalTo: wechatCardTitleLabel.trailingAnchor),
            wechatCardSubtitleLabel.topAnchor.constraint(equalTo: wechatCardTitleLabel.bottomAnchor, constant: 5),
            wechatCardDividerView.leadingAnchor.constraint(equalTo: wechatCardContainer.leadingAnchor, constant: 12),
            wechatCardDividerView.trailingAnchor.constraint(equalTo: wechatCardContainer.trailingAnchor, constant: -12),
            wechatCardDividerView.topAnchor.constraint(greaterThanOrEqualTo: wechatCardThumbView.bottomAnchor, constant: 10),
            wechatCardDividerView.topAnchor.constraint(greaterThanOrEqualTo: wechatCardSubtitleLabel.bottomAnchor, constant: 10),
            wechatCardDividerView.heightAnchor.constraint(equalToConstant: 1),
            wechatCardFooterIconView.leadingAnchor.constraint(equalTo: wechatCardContainer.leadingAnchor, constant: 12),
            wechatCardFooterIconView.topAnchor.constraint(equalTo: wechatCardDividerView.bottomAnchor, constant: 8),
            wechatCardFooterIconView.widthAnchor.constraint(equalToConstant: 13),
            wechatCardFooterIconView.heightAnchor.constraint(equalToConstant: 13),
            wechatCardFooterLabel.leadingAnchor.constraint(equalTo: wechatCardFooterIconView.trailingAnchor, constant: 5),
            wechatCardFooterLabel.centerYAnchor.constraint(equalTo: wechatCardFooterIconView.centerYAnchor),
            wechatCardFooterLabel.trailingAnchor.constraint(lessThanOrEqualTo: wechatCardChevronView.leadingAnchor, constant: -8),
            wechatCardChevronView.trailingAnchor.constraint(equalTo: wechatCardContainer.trailingAnchor, constant: -12),
            wechatCardChevronView.centerYAnchor.constraint(equalTo: wechatCardFooterIconView.centerYAnchor),
            wechatCardChevronView.widthAnchor.constraint(equalToConstant: 10),
            wechatCardChevronView.heightAnchor.constraint(equalToConstant: 14),
            wechatCardFooterIconView.bottomAnchor.constraint(equalTo: wechatCardContainer.bottomAnchor, constant: -10),
            previewIconLeadingConstraint!,
            previewIconCenterYConstraint!,
            previewIconView.widthAnchor.constraint(equalToConstant: 28),
            previewIconView.heightAnchor.constraint(equalToConstant: 28),
            previewTitleLeadingConstraint!,
            previewTitleLabel.trailingAnchor.constraint(equalTo: previewView.trailingAnchor, constant: -10),
            previewTitleLabel.topAnchor.constraint(equalTo: previewView.topAnchor, constant: 9),
            previewDetailLabel.leadingAnchor.constraint(equalTo: previewTitleLabel.leadingAnchor),
            previewDetailLabel.trailingAnchor.constraint(equalTo: previewTitleLabel.trailingAnchor),
            previewDetailLabel.topAnchor.constraint(equalTo: previewTitleLabel.bottomAnchor, constant: 3),
            previewDetailLabel.bottomAnchor.constraint(lessThanOrEqualTo: previewView.bottomAnchor, constant: -9),

            documentCardView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor, constant: 10),
            documentCardView.centerYAnchor.constraint(equalTo: previewView.centerYAnchor),
            documentCardView.widthAnchor.constraint(equalToConstant: 52),
            documentCardView.heightAnchor.constraint(equalToConstant: 68)
        ])
    }

    func reset() {
        mediaThumbnailRequests.forEach { $0.cancel() }
        mediaThumbnailRequests.removeAll(keepingCapacity: true)
        activeRemoteMediaImageURL = nil
        configuredMessageID = nil
        avatarView.prepareForReuse()
        centersGroupMemberAvatarWithBubble = false
        rowStack.alignment = .top
        avatarView.layer.borderWidth = 0
        avatarView.transform = .identity
        leadingConstraint?.isActive = false
        trailingConstraint?.isActive = false
        centerXConstraint?.isActive = false
        [senderLabel, avatarView, inlineNameLabel, titleRow, bodyLabel, detailLabel, contactCardView, wechatCardContainer, miniProgramShareCardView, linkShareCardView, previewView, metaLabel].forEach {
            $0.isHidden = false
        }
        deliveryStatusLabel.text = nil
        deliveryStatusLabel.isHidden = true
        deliveryStatusLabel.textColor = Self.mutedColor
        titleLabel.textColor = Self.textColor
        bodyLabel.textColor = .white
        bodyLabel.alpha = 1
        detailLabel.textColor = Self.detailColor
        previewTitleLabel.textColor = Self.textColor
        previewDetailLabel.textColor = Self.detailColor
        metaLabel.textColor = .white
        applyBubbleTextShadow()
        applyBubbleTextShadow(to: wechatCardSourceLabel, wechatCardTitleLabel, wechatCardSubtitleLabel, wechatCardFooterLabel)
        let shadowColor = UIColor.white.withAlphaComponent(rendersAIMessage ? 0.40 : 0.22)
        let shadowOffset = CGSize(width: 0, height: rendersAIMessage ? 0.4 : 0.5)
        miniProgramShareCardView.applyTextShadow(color: shadowColor, offset: shadowOffset)
        linkShareCardView.applyTextShadow(color: shadowColor, offset: shadowOffset)
        contactCardView.applyTextShadow(isAI: rendersAIMessage)
        bodyLabel.attributedText = nil
        titleLabel.attributedText = nil
        detailLabel.attributedText = nil
        previewTitleLabel.attributedText = nil
        previewDetailLabel.attributedText = nil
        mediaImageView.image = nil
        inlineNameLabel.attributedText = nil
        bodyLabel.backgroundColor = .clear
        bodyLabel.layer.cornerRadius = 0
        bodyLabel.clipsToBounds = false
        bodyLabel.textAlignment = .natural
        bodyLabel.numberOfLines = 0
        bodyLabel.font = Self.bodyFont(for: .text)
        richStack.isHidden = true
        richStack.arrangedSubviews.forEach {
            richStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        inlineCardHitTargets.removeAll()
        richMediaHitTargets.removeAll()
        richTextLinkHitTargets.removeAll()
        titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        titleLabel.numberOfLines = 2
        detailLabel.font = .systemFont(ofSize: 12)
        detailLabel.numberOfLines = 3
        previewTitleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        previewTitleLabel.numberOfLines = 2
        previewDetailLabel.font = .systemFont(ofSize: 11)
        previewDetailLabel.numberOfLines = 3
        paymentContentView.reset()
        contentStack.alignment = .fill
        previewWidthConstraint?.isActive = false
        previewWidthConstraint?.constant = 180
        mediaImageView.image = nil
        mediaImageView.backgroundColor = .clear
        mediaImageView.isHidden = true
        mediaPrivacyOverlayView.reset()
        channelsOverlayView.reset()
        mediaPlayIconView.image = UIImage(systemName: "play.circle.fill")
        mediaPlayIconView.isHidden = true
        previewIconView.isHidden = false
        previewTitleLabel.isHidden = false
        previewDetailLabel.isHidden = false
        previewView.layer.cornerRadius = 12
        previewIconView.backgroundColor = .clear
        previewIconView.layer.cornerRadius = 0
        previewIconView.clipsToBounds = false
        previewIconView.contentMode = .scaleAspectFit
        previewIconView.transform = .identity
        previewIconLeadingConstraint?.constant = 10
        previewIconCenterYConstraint?.constant = 0
        wechatCardContainer.isHidden = true
        wechatCardContainer.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        wechatCardContainer.layer.borderWidth = 0
        wechatCardContainer.layer.borderColor = UIColor.clear.cgColor
        wechatCardSourceIconView.image = nil
        wechatCardSourceLabel.text = nil
        wechatCardBadgeLabel.text = nil
        wechatCardBadgeLabel.backgroundColor = .clear
        wechatCardTitleLabel.text = nil
        wechatCardSubtitleLabel.text = nil
        wechatCardThumbView.backgroundColor = .clear
        wechatCardThumbIconView.image = nil
        wechatCardFooterIconView.image = nil
        wechatCardFooterLabel.text = nil
        wechatCardThumbWidthConstraint?.constant = 58
        wechatCardThumbHeightConstraint?.constant = 58
        miniProgramShareCardView.reset()
        linkShareCardView.reset()
        setVoiceWaveAnimation(active: false)
        contactCardView.reset()
        documentCardView.reset()
        previewTitleLeadingConstraint?.constant = 10
        metaLabel.font = .systemFont(ofSize: 10, weight: .medium)
        previewHeightConstraint?.constant = 76
        rowTopConstraint?.constant = 3
        rowBottomConstraint?.constant = -3
        bubbleView.layer.cornerRadius = Self.messageBubbleCornerRadius
        bubbleMaxWidthConstraint?.constant = MessageConnectionLayoutMetrics.maximumBubbleWidthIncrease
        bubbleMinimumHeightConstraint?.isActive = false
        bubbleView.clipsToBounds = false
        bubbleView.setBorderHidden(false)
        bubbleView.setBorderStyle(isDashed: false)
        bubbleView.setDashedBorderAnimation(active: false)
        resetDimensionalAppearance()
        inlineNameLabel.backgroundColor = .clear
        inlineNameLabel.shadowColor = nil
        inlineNameLabel.shadowOffset = .zero
        inlineNameLabel.textAlignment = .left
        deactivateInlineNamePlacementConstraints()
        moveInlineNameLabel(to: bubbleView)
        rendersAIMessage = false
        screenshotMediaBlurEnabled = false
        mediaPrivacyBlurEnabled = false
        usesPreviewConnectionFrame = false
        configuredPaymentType = nil
        configuredBubbleMode = nil
        configuredUsesUnansweredPresentation = false
        configuredIsOutgoing = false
        reservesConnectionLane = false
        lastTextBubbleLayoutWidth = 0
        requestedMinimumBubbleWidth = 0
        bubbleRequestedMinimumWidthConstraint?.isActive = false
        textBubbleWidthConstraint?.isActive = false
    }

    private func updateTextBubbleWidthIfNeeded(force: Bool = false) {
        guard configuredBubbleMode == .text,
              !bodyLabel.isHidden,
              bounds.width > 1
        else {
            textBubbleWidthConstraint?.isActive = false
            lastTextBubbleLayoutWidth = 0
            return
        }

        let layoutWidth = bounds.width
        guard force || abs(layoutWidth - lastTextBubbleLayoutWidth) > 0.5 else { return }
        lastTextBubbleLayoutWidth = layoutWidth

        let avatarAndSpacing: CGFloat = avatarView.isHidden
            ? 0
            : MessageConnectionLayoutMetrics.groupMemberOccupiedWidth
        let unansweredReserve: CGFloat = configuredUsesUnansweredPresentation ? 11 : 0
        let maximumBubbleWidth = MessageConnectionLayoutMetrics.maximumBubbleWidth(
            in: layoutWidth,
            occupiedWidth: avatarAndSpacing,
            reservedWidth: unansweredReserve,
            minimumWidth: 14
        )
        let horizontalContentInset: CGFloat = 14
        let maximumTextWidth = max(1, maximumBubbleWidth - horizontalContentInset)
        let scale = window?.screen.scale ?? UIScreen.main.scale
        let fittedTextWidth = bodyLabel.compactTextWidth(
            constrainedTo: maximumTextWidth,
            scale: scale
        )
        let fittedBubbleWidth = min(
            maximumBubbleWidth,
            max(
                requestedMinimumBubbleWidth,
                horizontalContentInset,
                fittedTextWidth + horizontalContentInset
            )
        )

        if textBubbleWidthConstraint == nil {
            textBubbleWidthConstraint = bubbleView.widthAnchor.constraint(equalToConstant: fittedBubbleWidth)
            textBubbleWidthConstraint?.priority = UILayoutPriority(999)
        }
        textBubbleWidthConstraint?.constant = fittedBubbleWidth
        textBubbleWidthConstraint?.isActive = true
    }

    private func applyInlineNamePlacement(
        for message: ChatMessage,
        mode: BubbleMode,
        overlaysInlineNameOnContent: Bool
    ) {
        deactivateInlineNamePlacementConstraints()
        if overlaysInlineNameOnContent {
            let parent = bubbleView
            moveInlineNameLabel(to: parent)
            inlineNameLabel.textAlignment = .right
            let verticalOffset: CGFloat = 0
            inlineNameMediaTopConstraint = inlineNameLabel.centerYAnchor.constraint(equalTo: previewView.topAnchor, constant: verticalOffset)
            inlineNameMediaTrailingConstraint = inlineNameLabel.trailingAnchor.constraint(equalTo: previewView.trailingAnchor, constant: -12)
            inlineNameMaxWidthConstraint = inlineNameLabel.widthAnchor.constraint(lessThanOrEqualTo: previewView.widthAnchor, multiplier: 0.68)
            inlineNameMediaTopConstraint?.isActive = true
            inlineNameMediaTrailingConstraint?.isActive = true
            inlineNameMaxWidthConstraint?.isActive = true
            parent.bringSubviewToFront(inlineNameLabel)
            return
        }
        moveInlineNameLabel(to: bubbleView)
        inlineNameCenterYConstraint = inlineNameLabel.centerYAnchor.constraint(equalTo: bubbleView.topAnchor)
        inlineNameMaxWidthConstraint = inlineNameLabel.widthAnchor.constraint(lessThanOrEqualTo: bubbleView.widthAnchor, multiplier: 0.58)
        inlineNameCenterYConstraint?.isActive = true
        inlineNameMaxWidthConstraint?.isActive = true
        if message.isOutgoing {
            inlineNameLabel.textAlignment = .right
            inlineNameTrailingConstraint = inlineNameLabel.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor, constant: -8)
            inlineNameTrailingConstraint?.isActive = true
        } else {
            inlineNameLabel.textAlignment = .left
            inlineNameLeadingConstraint = inlineNameLabel.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor, constant: 8)
            inlineNameLeadingConstraint?.isActive = true
        }
    }

    private func deactivateInlineNamePlacementConstraints() {
        [
            inlineNameLeadingConstraint,
            inlineNameTrailingConstraint,
            inlineNameCenterYConstraint,
            inlineNameMediaTopConstraint,
            inlineNameMediaTrailingConstraint,
            inlineNameMaxWidthConstraint
        ].forEach { $0?.isActive = false }
        inlineNameLeadingConstraint = nil
        inlineNameTrailingConstraint = nil
        inlineNameCenterYConstraint = nil
        inlineNameMediaTopConstraint = nil
        inlineNameMediaTrailingConstraint = nil
        inlineNameMaxWidthConstraint = nil
    }

    private func moveInlineNameLabel(to parent: UIView) {
        guard inlineNameLabel.superview !== parent else { return }
        inlineNameLabel.removeFromSuperview()
        parent.addSubview(inlineNameLabel)
        inlineNameLabel.translatesAutoresizingMaskIntoConstraints = false
    }

    private func applyInlineNameStyle(for message: ChatMessage) {
        inlineNameLabel.textColor = message.isAI ? Self.aiTextColor : .white
    }

    private func applyContentInsets(mode: BubbleMode, showsInlineName: Bool, usesCaptionedAttachment: Bool) {
        if mode == .text {
            bubbleTopConstraint?.constant = 7
            contentLeadingConstraint?.constant = 7
            contentTrailingConstraint?.constant = -7
            contentBottomConstraint?.constant = -7
            return
        }
        if mode == .channelVideo {
            bubbleTopConstraint?.constant = showsInlineName ? 20 : 0
            contentLeadingConstraint?.constant = 0
            contentTrailingConstraint?.constant = 0
            contentBottomConstraint?.constant = 0
            return
        }
        if mode == .money {
            bubbleTopConstraint?.constant = 0
            contentLeadingConstraint?.constant = 0
            contentTrailingConstraint?.constant = 0
            contentBottomConstraint?.constant = 0
            return
        }
        if mode == .media && !usesCaptionedAttachment {
            bubbleTopConstraint?.constant = showsInlineName ? 10 : 0
            contentLeadingConstraint?.constant = 0
            contentTrailingConstraint?.constant = 0
            contentBottomConstraint?.constant = 0
            return
        }
        bubbleTopConstraint?.constant = showsInlineName ? 20 : 11
        contentLeadingConstraint?.constant = 12
        contentTrailingConstraint?.constant = -12
        contentBottomConstraint?.constant = -10
    }

    private func applyRowSpacing(showsInlineName: Bool, overlaysMediaName: Bool, mode: BubbleMode) {
        if mode == .text {
            rowTopConstraint?.constant = showsInlineName ? 8 : 1
            rowBottomConstraint?.constant = -1
            contentStack.spacing = 3
            return
        }
        contentStack.spacing = 7
        if mode == .money && showsInlineName {
            rowTopConstraint?.constant = 16
            rowBottomConstraint?.constant = -3
            return
        }
        if showsInlineName && !overlaysMediaName {
            rowTopConstraint?.constant = 12
            rowBottomConstraint?.constant = -6
        } else {
            rowTopConstraint?.constant = 3
            rowBottomConstraint?.constant = -3
        }
    }

    private func applyAITextColor() {
        titleLabel.textColor = Self.aiTextColor
        bodyLabel.textColor = Self.aiTextColor
        detailLabel.textColor = Self.aiTextColor.withAlphaComponent(0.88)
        previewTitleLabel.textColor = Self.aiTextColor
        previewDetailLabel.textColor = Self.aiTextColor.withAlphaComponent(0.84)
        metaLabel.textColor = Self.aiTextColor.withAlphaComponent(0.64)
        contactCardView.applyAITextColor()
        applyBubbleTextShadow(isAI: true)
    }

    private func applyBubbleTextShadow(isAI: Bool = false) {
        let shadowColor = isAI
            ? UIColor(red: 1.0, green: 0.88, blue: 0.42, alpha: 0.26)
            : UIColor.black.withAlphaComponent(0.60)
        let shadowOffset = CGSize(width: 0, height: 1)
        [titleLabel, detailLabel, previewTitleLabel, previewDetailLabel].forEach {
            $0.shadowColor = shadowColor
            $0.shadowOffset = shadowOffset
        }
        let layeredShadows = Self.messageTextShadows(isAI: isAI)
        [senderLabel, inlineNameLabel, bodyLabel, metaLabel].forEach {
            $0.shadowColor = nil
            $0.shadowOffset = .zero
            $0.textShadows = layeredShadows
        }
    }

    private func applyBubbleTextShadow(to labels: UILabel...) {
        let shadowColor = rendersAIMessage
            ? UIColor(red: 1.0, green: 0.88, blue: 0.42, alpha: 0.26)
            : UIColor.white.withAlphaComponent(0.34)
        labels.forEach {
            $0.shadowColor = shadowColor
            $0.shadowOffset = CGSize(width: 0, height: 0.7)
        }
    }

    private func applyDirection(_ message: ChatMessage) {
        configuredIsOutgoing = message.isOutgoing
        if Self.isNotificationMessage(message) {
            rowStack.semanticContentAttribute = .forceLeftToRight
            columnStack.alignment = .center
            centerXConstraint = rowStack.centerXAnchor.constraint(equalTo: centerXAnchor)
            leadingConstraint = rowStack.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 18)
            trailingConstraint = rowStack.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -18)
            centerXConstraint?.isActive = true
            leadingConstraint?.isActive = true
            trailingConstraint?.isActive = true
            return
        }

        rowStack.semanticContentAttribute = message.isOutgoing ? .forceRightToLeft : .forceLeftToRight
        columnStack.alignment = message.isOutgoing ? .trailing : .leading
        if message.isOutgoing {
            trailingConstraint = rowStack.trailingAnchor.constraint(
                equalTo: trailingAnchor,
                constant: -MessageConnectionLayoutMetrics.bubbleConnectionSideInset
            )
            leadingConstraint = rowStack.leadingAnchor.constraint(
                greaterThanOrEqualTo: leadingAnchor,
                constant: MessageConnectionLayoutMetrics.bubbleOppositeSideInset
            )
        } else {
            leadingConstraint = rowStack.leadingAnchor.constraint(
                equalTo: leadingAnchor,
                constant: MessageConnectionLayoutMetrics.bubbleConnectionSideInset
            )
            trailingConstraint = rowStack.trailingAnchor.constraint(
                lessThanOrEqualTo: trailingAnchor,
                constant: -MessageConnectionLayoutMetrics.bubbleOppositeSideInset
            )
        }
        leadingConstraint?.isActive = true
        trailingConstraint?.isActive = true
    }

    private func updateConnectionLaneInsetIfNeeded() {
        guard reservesConnectionLane, bounds.width > 1 else { return }
        let inset = MessageConnectionLayoutMetrics.resolvedConnectionSideInset(in: bounds.width)
        if configuredIsOutgoing {
            trailingConstraint?.constant = -inset
        } else {
            leadingConstraint?.constant = inset
        }
    }

    private func applyConversation(
        _ message: ChatMessage,
        mode: BubbleMode,
        showsInlineName: Bool,
        showsGroupAvatar: Bool,
        showsGroupName: Bool
    ) {
        if Self.isNotificationMessage(message) {
            avatarView.isHidden = true
            senderLabel.isHidden = true
            inlineNameLabel.isHidden = true
            return
        }
        if message.isOutgoing {
            avatarView.isHidden = true
            senderLabel.isHidden = true
            inlineNameLabel.isHidden = message.isGroupConversation ? !showsGroupName : !showsInlineName
            return
        }
        if message.isAI || message.isGroupConversation {
            avatarView.isHidden = message.isGroupConversation ? !showsGroupAvatar : !Self.showsAvatarStatic(for: message)
            senderLabel.isHidden = message.isGroupConversation ? true : showsInlineName
            inlineNameLabel.isHidden = message.isGroupConversation ? !showsGroupName : !showsInlineName
            return
        }
        avatarView.isHidden = true
        senderLabel.isHidden = true
    }

    private func applyPalette(_ palette: BubblePalette, mode: BubbleMode, isAI: Bool, usesCaptionedAttachment: Bool) {
        bubbleView.backgroundColor = palette.background
        bubbleView.setBorderStyle(isDashed: isAI)
        inlineNameLabel.backgroundColor = .clear
        previewView.backgroundColor = palette.previewBackground
        previewView.layer.borderWidth = 1
        previewView.layer.borderColor = UIColor.white.withAlphaComponent(0.28).cgColor
        iconView.tintColor = palette.accent
        if mode == .money && !isAI {
            bubbleView.backgroundColor = .clear
            bubbleView.setBorderStyle(isDashed: false)
            bubbleView.setBorderHidden(true)
            previewView.layer.borderWidth = 0
            return
        }
        if mode == .media && !isAI && !usesCaptionedAttachment {
            bubbleView.backgroundColor = .clear
            bubbleView.layer.borderWidth = 0
            bubbleView.setBorderStyle(isDashed: false)
            bubbleView.setBorderHidden(true)
            previewView.backgroundColor = .clear
            previewView.layer.borderWidth = 0
        }
    }

    private func applyDimensionalAppearance(
        enabled: Bool,
        message: ChatMessage,
        mode: BubbleMode,
        usesCaptionedAttachment: Bool
    ) {
        let supportsDepth = enabled && mode != .system && mode != .notice
        let usesPreviewSurface = (mode == .money && !message.isAI)
            || (mode == .media && !message.isAI && !usesCaptionedAttachment)
            || mode == .channelVideo
        let usesBubbleDepth = supportsDepth && !usesPreviewSurface
        let accent: UIColor
        if message.isAI {
            accent = UIColor(red: 0.72, green: 0.46, blue: 0.08, alpha: 1)
        } else if message.isOutgoing {
            accent = UIColor(red: 0.05, green: 0.42, blue: 0.31, alpha: 1)
        } else {
            accent = UIColor(red: 0.07, green: 0.28, blue: 0.37, alpha: 1)
        }

        bubbleView.setDimensionalAppearance(
            enabled: usesBubbleDepth,
            accent: accent
        )
        guard usesBubbleDepth else {
            depthSurfaceView.isHidden = true
            return
        }

        let horizontalOffset: CGFloat = message.isOutgoing ? -1.2 : 1.2
        attachDepthSurface(to: bubbleView, horizontalOffset: horizontalOffset, verticalOffset: 1.4)
        depthSurfaceView.configure(
            color: accent,
            cornerRadius: bubbleView.layer.cornerRadius
        )
        depthSurfaceView.isHidden = false
    }

    private func resetDimensionalAppearance() {
        depthSurfaceView.isHidden = true
        bubbleView.setDimensionalAppearance(enabled: false, accent: .clear)
    }

    private func attachDepthSurface(to anchor: UIView, horizontalOffset: CGFloat, verticalOffset: CGFloat) {
        if depthSurfaceAnchorView !== anchor {
            [
                depthSurfaceLeadingConstraint,
                depthSurfaceTopConstraint,
                depthSurfaceWidthConstraint,
                depthSurfaceHeightConstraint
            ].forEach { $0?.isActive = false }

            depthSurfaceLeadingConstraint = depthSurfaceView.leadingAnchor.constraint(equalTo: anchor.leadingAnchor)
            depthSurfaceTopConstraint = depthSurfaceView.topAnchor.constraint(equalTo: anchor.topAnchor)
            depthSurfaceWidthConstraint = depthSurfaceView.widthAnchor.constraint(equalTo: anchor.widthAnchor)
            depthSurfaceHeightConstraint = depthSurfaceView.heightAnchor.constraint(equalTo: anchor.heightAnchor)
            [
                depthSurfaceLeadingConstraint,
                depthSurfaceTopConstraint,
                depthSurfaceWidthConstraint,
                depthSurfaceHeightConstraint
            ].forEach { $0?.isActive = true }
            depthSurfaceAnchorView = anchor
        }
        depthSurfaceLeadingConstraint?.constant = horizontalOffset
        depthSurfaceTopConstraint?.constant = verticalOffset
    }

    private func updateInlineNameBorderGap() {
        guard rendersAIMessage, inlineNameLabel.superview === bubbleView, !inlineNameLabel.isHidden else {
            bubbleView.setTopBorderGap(nil)
            return
        }
        let gap = inlineNameLabel.convert(inlineNameLabel.bounds, to: bubbleView)
            .insetBy(dx: -3, dy: 0)
        bubbleView.setTopBorderGap(gap)
    }

    private func applyContent(_ message: ChatMessage, template: MessageRenderTemplate, mode: BubbleMode, usesCaptionedAttachment: Bool) {
        titleLabel.text = template.primaryText
        bodyLabel.text = message.body
        detailLabel.text = message.detail
        previewTitleLabel.text = message.body
        previewDetailLabel.text = message.detail.isEmpty ? template.secondaryText : message.detail

        switch mode {
        case .system:
            rowTopConstraint?.constant = 1
            rowBottomConstraint?.constant = -1
            titleRow.isHidden = true
            previewView.isHidden = true
            detailLabel.isHidden = true
            metaLabel.isHidden = true
            bodyLabel.font = .systemFont(ofSize: 13, weight: .medium)
            bodyLabel.textColor = .white
            bodyLabel.textAlignment = .center
            bodyLabel.numberOfLines = 0
        case .text:
            titleRow.isHidden = true
            previewView.isHidden = true
            detailLabel.isHidden = true
            bodyLabel.font = Self.bodyFont(for: .text)
            bodyLabel.attributedText = Self.linkifiedText(
                message.body,
                font: Self.bodyFont(for: .text),
                baseColor: rendersAIMessage ? Self.aiTextColor : .white
            )
        case .oversizedText:
            titleRow.isHidden = true
            previewView.isHidden = true
            detailLabel.isHidden = true
            bodyLabel.font = .systemFont(ofSize: 30, weight: .semibold)
            bodyLabel.textAlignment = .center
        case .media:
            titleRow.isHidden = true
            detailLabel.isHidden = true
            if usesCaptionedAttachment {
                bodyLabel.isHidden = false
                bodyLabel.font = Self.bodyFont(for: .text)
                bodyLabel.attributedText = Self.linkifiedText(
                    message.body,
                    font: Self.bodyFont(for: .text),
                    baseColor: rendersAIMessage ? Self.aiTextColor : .white
                )
                metaLabel.isHidden = false
                metaLabel.text = message.displayTimestamp
                previewHeightConstraint?.constant = 150
            } else {
                bodyLabel.isHidden = true
                metaLabel.isHidden = true
                previewHeightConstraint?.constant = 150
            }
            configureMediaPreview(for: message, template: template, usesCaptionedAttachment: usesCaptionedAttachment)
        case .channelVideo:
            configureChannelsVideoPreview(for: message, template: template)
        case .voice:
            titleRow.isHidden = true
            bodyLabel.isHidden = true
            detailLabel.isHidden = true
            metaLabel.isHidden = true
            previewHeightConstraint?.constant = 58
            bodyLabel.font = Self.bodyFont(for: .voice)
            previewIconView.image = UIImage(systemName: isVoicePlaying ? "waveform.circle.fill" : template.symbolName)
            previewTitleLabel.text = message.body.isEmpty ? "语音消息" : message.body
            previewDetailLabel.text = isVoicePlaying ? "再次点击停止" : "点击播放"
            previewTitleLabel.numberOfLines = 1
            previewDetailLabel.numberOfLines = 1
            setVoiceWaveAnimation(active: isVoicePlaying)
        case .document:
            titleRow.isHidden = true
            bodyLabel.isHidden = !usesCaptionedAttachment
            detailLabel.isHidden = true
            previewHeightConstraint?.constant = 104
            if usesCaptionedAttachment {
                bodyLabel.font = Self.bodyFont(for: .text)
                bodyLabel.attributedText = Self.linkifiedText(
                    message.body,
                    font: Self.bodyFont(for: .text),
                    baseColor: rendersAIMessage ? Self.aiTextColor : .white
                )
                metaLabel.isHidden = false
                metaLabel.text = message.displayTimestamp
            }
            configureDocumentPreview(for: message, template: template)
        case .location, .profile, .link, .stacked, .notice:
            detailLabel.isHidden = true
            bodyLabel.font = Self.bodyFont(for: mode)
            bodyLabel.numberOfLines = mode == .notice ? 0 : 2
            previewHeightConstraint?.constant = Self.cardHeight(for: message, mode: mode) - 68
            if mode == .profile, let contactCard = message.contactCard {
                configureContactCardPreview(contactCard)
            } else if message.type == .favorite {
                configureFavoritePreview(for: message, template: template)
            } else if message.type == .relay {
                configureRelayPreview(for: message, template: template)
            } else if Self.usesWechatMiniProgramShareStyle(message) {
                configureWechatMiniProgramSharePreview(for: message, template: template)
            } else if message.type == .webLink {
                configureWebLinkSharePreview(for: message, template: template)
            } else if mode == .link || mode == .profile {
                configureWechatStyleCardPreview(for: message, template: template)
            }
        case .money:
            configurePaymentCard(for: message)
        case .call:
            titleRow.isHidden = true
            bodyLabel.isHidden = true
            detailLabel.isHidden = true
            metaLabel.isHidden = true
            bodyLabel.font = Self.bodyFont(for: .call)
            previewHeightConstraint?.constant = 56
            previewTitleLabel.text = message.body.isEmpty ? template.primaryText : message.body
            previewDetailLabel.text = message.detail.isEmpty ? template.secondaryText : message.detail
            previewTitleLabel.numberOfLines = 1
            previewDetailLabel.numberOfLines = 1
        case .quote:
            bodyLabel.numberOfLines = 3
            detailLabel.isHidden = true
            previewHeightConstraint?.constant = 50
            previewTitleLabel.text = "引用原消息"
            previewDetailLabel.text = message.detail.isEmpty ? "下午 2 点怎么样？" : message.detail
        }

        if !message.richElements.isEmpty {
            titleRow.isHidden = true
            bodyLabel.isHidden = true
            detailLabel.isHidden = true
            contactCardView.isHidden = true
            wechatCardContainer.isHidden = true
            miniProgramShareCardView.isHidden = true
            linkShareCardView.isHidden = true
            previewView.isHidden = true
            richStack.isHidden = false
            renderRichElements(message.richElements)
        }

        if usesCaptionedAttachment {
            titleRow.isHidden = true
            bodyLabel.isHidden = false
            richStack.isHidden = true
            previewView.isHidden = false
            detailLabel.isHidden = true
            metaLabel.isHidden = false
            metaLabel.text = message.displayTimestamp
        }
    }

    private func setVoiceWaveAnimation(active: Bool) {
        previewIconView.layer.removeAnimation(forKey: "voiceWaveScale")
        previewIconView.layer.removeAnimation(forKey: "voiceWaveOpacity")
        previewIconView.transform = .identity
        previewIconView.alpha = 1
        guard active else { return }

        let scale = CABasicAnimation(keyPath: "transform.scale")
        scale.fromValue = 0.88
        scale.toValue = 1.18
        scale.duration = 0.42
        scale.autoreverses = true
        scale.repeatCount = .infinity
        scale.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        previewIconView.layer.add(scale, forKey: "voiceWaveScale")

        let opacity = CABasicAnimation(keyPath: "opacity")
        opacity.fromValue = 0.58
        opacity.toValue = 1
        opacity.duration = 0.42
        opacity.autoreverses = true
        opacity.repeatCount = .infinity
        opacity.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        previewIconView.layer.add(opacity, forKey: "voiceWaveOpacity")
    }

    func detectedLink(at point: CGPoint) -> String? {
        if let richLink = detectedRichTextLink(at: point) {
            return richLink
        }

        guard !bodyLabel.isHidden,
              let attributedText = bodyLabel.attributedText,
              attributedText.length > 0
        else { return nil }

        let ranges = Self.detectedURLRanges(in: attributedText.string)
        guard !ranges.isEmpty else { return nil }

        let pointInLabel = convert(point, to: bodyLabel)
        let textContentRect = bodyLabel.textContentRect
        guard textContentRect.insetBy(dx: -6, dy: -6).contains(pointInLabel) else { return nil }

        let textStorage = NSTextStorage(attributedString: attributedText)
        let layoutManager = NSLayoutManager()
        let textContainer = NSTextContainer(size: textContentRect.size)
        textContainer.lineFragmentPadding = 0
        textContainer.maximumNumberOfLines = bodyLabel.numberOfLines
        textContainer.lineBreakMode = bodyLabel.lineBreakMode
        layoutManager.addTextContainer(textContainer)
        textStorage.addLayoutManager(layoutManager)

        let glyphRange = layoutManager.glyphRange(for: textContainer)
        let usedRect = layoutManager.usedRect(for: textContainer)
        let textOffset = CGPoint(
            x: textContentRect.minX + (textContentRect.width - usedRect.width) * 0.5 - usedRect.minX,
            y: textContentRect.minY + (textContentRect.height - usedRect.height) * 0.5 - usedRect.minY
        )
        let location = CGPoint(x: pointInLabel.x - textOffset.x, y: pointInLabel.y - textOffset.y)
        let glyphIndex = layoutManager.glyphIndex(for: location, in: textContainer)
        guard NSLocationInRange(glyphIndex, glyphRange) else { return nil }

        let characterIndex = layoutManager.characterIndex(for: location, in: textContainer, fractionOfDistanceBetweenInsertionPoints: nil)
        guard let range = ranges.first(where: { NSLocationInRange(characterIndex, $0) }),
              let swiftRange = Range(range, in: attributedText.string)
        else { return nil }

        return String(attributedText.string[swiftRange])
    }

    func setUnansweredRecipientColor(_ color: UIColor?) {
        unansweredRecipientDot.isHidden = color == nil
        unansweredRecipientDot.backgroundColor = color
        unansweredRecipientDot.layer.shadowColor = color?.cgColor
        unansweredRecipientDot.layer.shadowOpacity = color == nil ? 0 : 0.72
    }

    private func detectedRichTextLink(at point: CGPoint) -> String? {
        for target in richTextLinkHitTargets.reversed() {
            guard let attributedText = target.label.attributedText,
                  attributedText.length > 0
            else { continue }

            let pointInLabel = convert(point, to: target.label)
            let textContentRect = (target.label as? LayeredShadowLabel)?.textContentRect ?? target.label.bounds
            guard textContentRect.insetBy(dx: -6, dy: -6).contains(pointInLabel),
                  characterIndex(at: pointInLabel, in: target.label, attributedText: attributedText).map({
                      NSLocationInRange($0, target.range)
                  }) == true
            else { continue }

            return target.url
        }
        return nil
    }

    private func characterIndex(at point: CGPoint, in label: UILabel, attributedText: NSAttributedString) -> Int? {
        let textContentRect = (label as? LayeredShadowLabel)?.textContentRect ?? label.bounds
        let textStorage = NSTextStorage(attributedString: attributedText)
        let layoutManager = NSLayoutManager()
        let textContainer = NSTextContainer(size: textContentRect.size)
        textContainer.lineFragmentPadding = 0
        textContainer.maximumNumberOfLines = label.numberOfLines
        textContainer.lineBreakMode = label.lineBreakMode
        layoutManager.addTextContainer(textContainer)
        textStorage.addLayoutManager(layoutManager)

        let glyphRange = layoutManager.glyphRange(for: textContainer)
        let usedRect = layoutManager.usedRect(for: textContainer)
        let textOffset = CGPoint(
            x: textContentRect.minX + (textContentRect.width - usedRect.width) * 0.5 - usedRect.minX,
            y: textContentRect.minY + (textContentRect.height - usedRect.height) * 0.5 - usedRect.minY
        )
        let location = CGPoint(x: point.x - textOffset.x, y: point.y - textOffset.y)
        let glyphIndex = layoutManager.glyphIndex(for: location, in: textContainer)
        guard NSLocationInRange(glyphIndex, glyphRange) else { return nil }
        return layoutManager.characterIndex(for: location, in: textContainer, fractionOfDistanceBetweenInsertionPoints: nil)
    }

    func detectedInlineCard(at point: CGPoint) -> InlineCardInteraction? {
        for target in inlineCardHitTargets.reversed() {
            let pointInCard = convert(point, to: target.view)
            if target.view.bounds.insetBy(dx: -6, dy: -6).contains(pointInCard) {
                return target.interaction
            }
        }
        return nil
    }

    func detectedRichMedia(at point: CGPoint) -> RichMediaInteraction? {
        for target in richMediaHitTargets.reversed() {
            let pointInTarget = convert(point, to: target.view)
            if target.view.bounds.insetBy(dx: -6, dy: -6).contains(pointInTarget) {
                return target.interaction
            }
        }
        return nil
    }

    private func configureMediaPreview(for message: ChatMessage, template: MessageRenderTemplate, usesCaptionedAttachment: Bool) {
        let signpostID = PerformanceSignpost.begin("MediaPreviewConfigure")
        defer { PerformanceSignpost.end("MediaPreviewConfigure", id: signpostID) }
        previewHeightConstraint?.constant = Self.defaultMediaPreviewSize.height
        previewTitleLabel.text = template.secondaryText
        previewDetailLabel.text = mediaAttachmentTitle(for: message)
        if usesCaptionedAttachment {
            contentStack.alignment = .fill
            previewWidthConstraint?.isActive = false
        } else {
            applyAdaptiveMediaPreviewSize(image: nil, isOutgoing: message.isOutgoing, fillsAvailableWidth: false)
        }

        switch message.type {
        case .stickerGif:
            mediaImageView.contentMode = .scaleAspectFit
            if let url = message.attachmentURL {
                if url.isFileURL {
                    requestMediaThumbnail(
                        for: message,
                        url: url,
                        kind: .image(trimsTransparentCanvas: false)
                    ) { bubble, image in
                        bubble.applyAdaptiveMediaPreviewSize(
                            image: image,
                            isOutgoing: message.isOutgoing,
                            fillsAvailableWidth: usesCaptionedAttachment
                        )
                        bubble.showInlineMediaImage(image, showsPlayIcon: false)
                    }
                } else if Self.isRemoteImageURL(url) {
                    loadRemoteInlineMediaImage(url, isOutgoing: message.isOutgoing, fillsAvailableWidth: usesCaptionedAttachment, showsPlayIcon: false)
                }
            } else {
                let image = makeStickerPreviewImage(for: message)
                applyAdaptiveMediaPreviewSize(image: image, isOutgoing: message.isOutgoing, fillsAvailableWidth: usesCaptionedAttachment)
                showInlineMediaImage(image, showsPlayIcon: false)
            }
        case .image, .capturedPhoto:
            guard let url = message.attachmentURL else { return }
            mediaImageView.contentMode = .scaleAspectFit
            if url.isFileURL {
                let extensionName = url.pathExtension.lowercased()
                let trimsTransparentCanvas = message.type != .capturedPhoto
                    && ["png", "webp"].contains(extensionName)
                requestMediaThumbnail(
                    for: message,
                    url: url,
                    kind: .image(trimsTransparentCanvas: trimsTransparentCanvas)
                ) { bubble, image in
                    bubble.applyAdaptiveMediaPreviewSize(
                        image: image,
                        isOutgoing: message.isOutgoing,
                        fillsAvailableWidth: usesCaptionedAttachment
                    )
                    bubble.showInlineMediaImage(image, showsPlayIcon: false)
                }
            } else if Self.isRemoteImageURL(url) {
                loadRemoteInlineMediaImage(url, isOutgoing: message.isOutgoing, fillsAvailableWidth: usesCaptionedAttachment, showsPlayIcon: false)
            }
        case .video, .channelsVideo:
            guard let url = message.attachmentURL else { return }
            previewView.backgroundColor = UIColor.black.withAlphaComponent(0.92)
            mediaImageView.backgroundColor = .clear
            mediaImageView.contentMode = .scaleAspectFit
            applyAdaptiveMediaPreviewSize(image: nil, isOutgoing: message.isOutgoing, fillsAvailableWidth: false)
            mediaImageView.isHidden = false
            mediaPlayIconView.isHidden = false
            setMediaPrivacyBlurVisible(false)
            requestMediaThumbnail(for: message, url: url, kind: .video) { bubble, thumbnail in
                bubble.applyAdaptiveMediaPreviewSize(
                    image: thumbnail,
                    isOutgoing: message.isOutgoing,
                    fillsAvailableWidth: false
                )
                bubble.showInlineMediaImage(thumbnail, showsPlayIcon: true)
            }
        default:
            break
        }
    }

    private func applyAdaptiveMediaPreviewSize(image: UIImage?, isOutgoing: Bool, fillsAvailableWidth: Bool) {
        contentStack.alignment = isOutgoing ? .trailing : .leading
        let size = Self.mediaPreviewSize(for: image)
        previewHeightConstraint?.constant = size.height
        previewWidthConstraint?.constant = size.width
        previewWidthConstraint?.isActive = !fillsAvailableWidth
    }

    private func requestMediaThumbnail(
        for message: ChatMessage,
        url: URL,
        kind: MediaThumbnailPipeline.ContentKind,
        targetSize: CGSize = CGSize(width: 320, height: 320),
        completion: @escaping (ChatMessageBubbleView, UIImage) -> Void
    ) {
        requestMediaThumbnail(
            expectedMessageID: message.id,
            url: url,
            kind: kind,
            targetSize: targetSize,
            completion: completion
        )
    }

    private func requestMediaThumbnail(
        expectedMessageID: UUID,
        url: URL,
        kind: MediaThumbnailPipeline.ContentKind,
        targetSize: CGSize,
        completion: @escaping (ChatMessageBubbleView, UIImage) -> Void
    ) {
        let scale = window?.screen.scale ?? UIScreen.main.scale
        if let cached = MediaThumbnailPipeline.shared.cachedImage(
            for: url,
            kind: kind,
            targetSize: targetSize,
            scale: scale
        ) {
            completion(self, cached)
            return
        }

        let request = MediaThumbnailPipeline.shared.load(
            url,
            kind: kind,
            targetSize: targetSize,
            scale: scale
        ) { [weak self] image in
            guard let self,
                  self.configuredMessageID == expectedMessageID,
                  let image
            else { return }
            completion(self, image)
        }
        mediaThumbnailRequests.append(request)
    }

    private func loadRemoteInlineMediaImage(
        _ url: URL,
        isOutgoing: Bool,
        fillsAvailableWidth: Bool,
        showsPlayIcon: Bool
    ) {
        activeRemoteMediaImageURL = url
        mediaImageView.backgroundColor = UIColor.black.withAlphaComponent(0.10)
        mediaImageView.isHidden = false
        mediaPlayIconView.isHidden = !showsPlayIcon
        setMediaPrivacyBlurVisible(false)
        previewTitleLabel.text = "图片加载中"
        previewDetailLabel.text = url.lastPathComponent.isEmpty ? url.host : url.lastPathComponent

        guard let expectedMessageID = configuredMessageID else { return }
        requestMediaThumbnail(
            expectedMessageID: expectedMessageID,
            url: url,
            kind: .image(trimsTransparentCanvas: true),
            targetSize: CGSize(width: 320, height: 320)
        ) { bubble, image in
            guard bubble.activeRemoteMediaImageURL == url else { return }
            bubble.applyAdaptiveMediaPreviewSize(
                image: image,
                isOutgoing: isOutgoing,
                fillsAvailableWidth: fillsAvailableWidth
            )
            bubble.showInlineMediaImage(image, showsPlayIcon: showsPlayIcon)
        }
    }

    private static func isRemoteImageURL(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http" else {
            return false
        }
        let ext = url.pathExtension.lowercased()
        return ext.isEmpty || ["jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp"].contains(ext)
    }

    private func configureChannelsVideoPreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = true
        detailLabel.isHidden = true
        previewView.isHidden = false
        bodyLabel.isHidden = false
        metaLabel.isHidden = false
        previewIconView.isHidden = true
        previewTitleLabel.isHidden = true
        previewDetailLabel.isHidden = true
        previewHeightConstraint?.constant = 276

        bubbleView.backgroundColor = .clear
        bubbleView.layer.borderWidth = 0
        bubbleView.setBorderStyle(isDashed: false)
        bubbleView.setBorderHidden(true)
        previewView.backgroundColor = UIColor.black
        previewView.layer.borderWidth = 0
        previewView.layer.cornerRadius = 7
        previewView.clipsToBounds = true

        showInlineMediaImage(makeChannelsVideoPoster(for: message), showsPlayIcon: true)
        if let url = message.attachmentURL {
            requestMediaThumbnail(
                for: message,
                url: url,
                kind: .video,
                targetSize: CGSize(width: 480, height: 320)
            ) { bubble, thumbnail in
                bubble.mediaImageView.image = thumbnail
            }
        }
        bodyLabel.isHidden = false
        metaLabel.isHidden = true
        mediaPlayIconView.image = UIImage(systemName: "play.fill")
        mediaPlayIconView.tintColor = .white.withAlphaComponent(0.94)

        let title = message.body
            .replacingOccurrences(of: "视频号：", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        bodyLabel.isHidden = true

        let source = channelsVideoSourceName(for: message)
        channelsOverlayView.configure(
            title: title.isEmpty ? "视频号内容" : title,
            source: "\(source) · 视频号 · 点击打开"
        )
        previewView.bringSubviewToFront(channelsOverlayView)
        previewView.bringSubviewToFront(mediaPlayIconView)
    }

    private func makeChannelsVideoPoster(for message: ChatMessage) -> UIImage {
        let size = CGSize(width: 320, height: 420)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.16, green: 0.09, blue: 0.05, alpha: 1).setFill()
            context.fill(rect)

            let gradientColors = [
                UIColor(red: 0.52, green: 0.24, blue: 0.08, alpha: 1).cgColor,
                UIColor(red: 0.08, green: 0.05, blue: 0.04, alpha: 1).cgColor
            ] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: gradientColors, locations: [0, 1]) {
                context.cgContext.drawLinearGradient(
                    gradient,
                    start: CGPoint(x: size.width * 0.2, y: 0),
                    end: CGPoint(x: size.width * 0.9, y: size.height),
                    options: []
                )
            }

            UIColor.white.withAlphaComponent(0.15).setFill()
            for index in 0..<6 {
                let y = CGFloat(index) * 74 + 18
                UIBezierPath(roundedRect: CGRect(x: -40, y: y, width: 190, height: 54), cornerRadius: 10).fill()
                UIBezierPath(roundedRect: CGRect(x: 170, y: y + 24, width: 190, height: 54), cornerRadius: 10).fill()
            }

            let title = message.body.isEmpty ? "视频号" : message.body
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .center
            (title as NSString).draw(
                in: CGRect(x: 26, y: 28, width: size.width - 52, height: 68),
                withAttributes: [
                    .font: UIFont.systemFont(ofSize: 25, weight: .black),
                    .foregroundColor: UIColor(red: 1.0, green: 0.16, blue: 0.10, alpha: 1),
                    .paragraphStyle: paragraph
                ]
            )

            let caption = "视频号 · 微信内容"
            (caption as NSString).draw(
                in: CGRect(x: 28, y: size.height - 72, width: size.width - 56, height: 28),
                withAttributes: [
                    .font: UIFont.systemFont(ofSize: 18, weight: .bold),
                    .foregroundColor: UIColor.white,
                    .paragraphStyle: paragraph
                ]
            )
        }
    }

    private func channelsVideoSourceName(for message: ChatMessage) -> String {
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        if let source = lines.first(where: { $0.hasPrefix("作者：") || $0.hasPrefix("视频号：") }) {
            return source
                .replacingOccurrences(of: "作者：", with: "")
                .replacingOccurrences(of: "视频号：", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return "篮球邮差Melo"
    }

    private func makeStickerPreviewImage(for message: ChatMessage) -> UIImage {
        let size = CGSize(width: 260, height: 180)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.96, green: 0.91, blue: 0.98, alpha: 1).setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: 18).fill()

            UIColor(red: 1.0, green: 0.74, blue: 0.20, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 58, y: 28, width: 112, height: 112)).fill()
            UIColor(red: 0.20, green: 0.16, blue: 0.12, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 92, y: 66, width: 11, height: 16)).fill()
            UIBezierPath(ovalIn: CGRect(x: 126, y: 66, width: 11, height: 16)).fill()
            let smile = UIBezierPath()
            smile.move(to: CGPoint(x: 92, y: 100))
            smile.addQuadCurve(to: CGPoint(x: 138, y: 100), controlPoint: CGPoint(x: 115, y: 122))
            smile.lineWidth = 5
            smile.lineCapStyle = .round
            smile.stroke()

            UIColor.white.withAlphaComponent(0.88).setFill()
            UIBezierPath(roundedRect: CGRect(x: 154, y: 34, width: 48, height: 26), cornerRadius: 13).fill()
            UIBezierPath(roundedRect: CGRect(x: 172, y: 70, width: 58, height: 26), cornerRadius: 13).fill()
            UIBezierPath(roundedRect: CGRect(x: 142, y: 106, width: 76, height: 26), cornerRadius: 13).fill()

            let badgeRect = CGRect(x: 22, y: 132, width: 78, height: 28)
            UIColor(red: 0.50, green: 0.22, blue: 0.72, alpha: 1).setFill()
            UIBezierPath(roundedRect: badgeRect, cornerRadius: 14).fill()
            let badgeAttributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            ("GIF" as NSString).draw(
                in: badgeRect.insetBy(dx: 22, dy: 6),
                withAttributes: badgeAttributes
            )

            let title = message.body.isEmpty ? "动态贴纸" : message.body
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .semibold),
                .foregroundColor: UIColor(red: 0.18, green: 0.12, blue: 0.22, alpha: 0.78)
            ]
            (title as NSString).draw(
                in: CGRect(x: 108, y: 135, width: 128, height: 22),
                withAttributes: attributes
            )
        }
    }

    private func configureDocumentPreview(for message: ChatMessage, template: MessageRenderTemplate) {
        previewView.backgroundColor = UIColor.white.withAlphaComponent(0.24)
        previewView.layer.borderColor = UIColor.white.withAlphaComponent(0.38).cgColor
        previewIconView.isHidden = true
        documentCardView.configure(format: fileExtensionLabel(for: message))
        previewTitleLeadingConstraint?.constant = 34
        previewTitleLabel.font = .systemFont(ofSize: 13.5, weight: .semibold)
        previewTitleLabel.numberOfLines = 2
        previewTitleLabel.text = mediaAttachmentTitle(for: message)
        previewDetailLabel.font = .systemFont(ofSize: 11.2, weight: .regular)
        previewDetailLabel.numberOfLines = 3
        let detail = cleanAttachmentDetail(message.detail).isEmpty ? template.secondaryText : cleanAttachmentDetail(message.detail)
        previewDetailLabel.text = "\(detail)\n点击预览 · 已生成可访问链接"
        metaLabel.text = "\(message.displayTimestamp)  文档"
    }

    private func mediaAttachmentTitle(for message: ChatMessage) -> String {
        if message.type == .file,
           let attachmentName = message.attachmentURL?.lastPathComponent,
           !attachmentName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return attachmentName
        }
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != "附件说明消息" && $0 != "文件已添加" }
        return lines.first ?? message.body
    }

    private func cleanAttachmentDetail(_ detail: String) -> String {
        detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != "附件说明消息" && $0 != "文件已添加" }
            .dropFirst()
            .joined(separator: "\n")
    }

    private func configurePaymentCard(for message: ChatMessage) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        metaLabel.isHidden = true
        previewView.isHidden = false
        usesPreviewConnectionFrame = true
        configuredPaymentType = message.type
        previewHeightConstraint?.constant = message.type == .redPacket ? 96 : 92
        previewWidthConstraint?.constant = adaptivePaymentWidth(for: message.type)
        previewWidthConstraint?.isActive = true
        let paymentCompleted = isCompletedPayment(message)
        previewView.backgroundColor = paymentCompleted
            ? UIColor(red: 0.78, green: 0.48, blue: 0.22, alpha: 0.72)
            : UIColor(red: 1.0, green: 0.56, blue: 0.02, alpha: 1)
        previewView.layer.borderWidth = 0
        previewView.layer.cornerRadius = 6
        previewView.layer.cornerCurve = .continuous
        previewView.clipsToBounds = true

        previewIconView.isHidden = false
        previewIconView.layer.cornerRadius = 22
        previewIconView.layer.cornerCurve = .continuous
        previewIconView.clipsToBounds = true
        previewIconView.backgroundColor = .clear
        previewIconView.contentMode = .scaleAspectFit
        previewIconView.transform = CGAffineTransform(scaleX: 1.62, y: 1.62)
        previewTitleLabel.isHidden = true
        previewDetailLabel.isHidden = true
        paymentContentView.isHidden = false

        previewTitleLabel.textColor = .white
        previewTitleLabel.shadowColor = UIColor.black.withAlphaComponent(0.08)
        previewTitleLabel.shadowOffset = CGSize(width: 0, height: 0.5)
        previewDetailLabel.textColor = UIColor.white.withAlphaComponent(0.88)
        previewDetailLabel.shadowColor = UIColor.black.withAlphaComponent(0.06)
        previewDetailLabel.shadowOffset = CGSize(width: 0, height: 0.5)
        previewDetailLabel.font = .systemFont(ofSize: 16, weight: .medium)
        previewTitleLabel.numberOfLines = message.type == .redPacket ? 2 : 1
        previewDetailLabel.numberOfLines = 1

        switch message.type {
        case .redPacket:
            previewIconView.isHidden = true
            paymentContentView.configure(
                type: message.type,
                title: paymentCompleted ? "已领完" : redPacketDisplayTitle(from: message),
                subtitle: nil,
                footer: "红包",
                completed: paymentCompleted
            )
        case .transfer:
            previewIconView.isHidden = true
            paymentContentView.configure(
                type: message.type,
                title: paymentAmountText(from: message),
                subtitle: paymentCompleted ? "已收款" : "请收款",
                footer: "转账",
                completed: paymentCompleted
            )
        case .splitBill:
            previewIconView.isHidden = true
            paymentContentView.configure(
                type: message.type,
                title: paymentAmountText(from: message),
                subtitle: paymentCompleted ? "已收齐" : "请参与收款",
                footer: "群收款",
                completed: paymentCompleted
            )
        default:
            break
        }
        previewView.layer.borderWidth = paymentCompleted ? 1 : 0
        previewView.layer.borderColor = UIColor.white.withAlphaComponent(paymentCompleted ? 0.16 : 0).cgColor
        if inlineNameLabel.superview === previewView {
            previewView.bringSubviewToFront(inlineNameLabel)
        }
    }

    private func isCompletedPayment(_ message: ChatMessage) -> Bool {
        switch message.type {
        case .redPacket:
            if message.detail.contains("状态：已领完") || message.detail.contains("已领完") {
                return true
            }
            if let progress = redPacketProgressFromDetail(message.detail) {
                return progress.received >= progress.total
            }
            return message.detail.contains("状态：已领取")
        case .transfer:
            return message.detail.contains("已收款") || message.detail.contains("已领取")
        case .splitBill:
            if let progress = splitBillProgressFromDetail(message.detail) {
                return progress.paid >= progress.total
            }
            return message.detail.contains("已收齐")
        default:
            return false
        }
    }

    private func redPacketProgressFromDetail(_ detail: String) -> (received: Int, total: Int)? {
        guard let line = detail.components(separatedBy: .newlines).first(where: { $0.hasPrefix("已领取：") }) else {
            return nil
        }
        let parts = line
            .replacingOccurrences(of: "已领取：", with: "")
            .split(separator: "/")
            .compactMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        guard parts.count == 2 else { return nil }
        return (parts[0], max(parts[1], 1))
    }

    private func splitBillProgressFromDetail(_ detail: String) -> (paid: Int, total: Int)? {
        guard let line = detail.components(separatedBy: .newlines).first(where: { $0.hasPrefix("已收 ") }) else {
            return nil
        }
        let parts = line
            .replacingOccurrences(of: "已收 ", with: "")
            .replacingOccurrences(of: " 人", with: "")
            .split(separator: "/")
            .compactMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        guard parts.count == 2 else { return nil }
        return (parts[0], max(parts[1], 1))
    }

    private func redPacketDisplayTitle(from message: ChatMessage) -> String {
        let trimmed = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "恭喜发财，大吉大利" }
        let cleaned = trimmed
            .replacingOccurrences(of: "微信红包", with: "红包")
            .replacingOccurrences(of: "红包：", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? "恭喜发财，大吉大利" : cleaned
    }

    private func paymentAmountText(from message: ChatMessage) -> String {
        let combined = [message.body, message.detail].joined(separator: "\n")
        if let range = combined.range(of: #"¥\s*\d+(?:\.\d{1,2})?"#, options: .regularExpression) {
            return combined[range].replacingOccurrences(of: " ", with: "")
        }
        if let range = combined.range(of: #"\d+(?:\.\d{1,2})?\s*元"#, options: .regularExpression) {
            return String(combined[range]).replacingOccurrences(of: " ", with: "")
        }
        return "¥50.00"
    }

    private func updateAdaptivePaymentWidthIfNeeded() {
        guard let type = configuredPaymentType else { return }
        let width = adaptivePaymentWidth(for: type)
        guard abs((previewWidthConstraint?.constant ?? 0) - width) > 0.5 else { return }
        previewWidthConstraint?.constant = width
    }

    private func adaptivePaymentWidth(for type: ChatMessageType) -> CGFloat {
        let preferred: CGFloat
        switch type {
        case .redPacket:
            preferred = 228
        case .transfer, .splitBill:
            preferred = 218
        default:
            preferred = 220
        }

        let availableWidth: CGFloat
        if bounds.width > 1 {
            availableWidth = bounds.width
        } else {
            availableWidth = UIScreen.main.bounds.width - 148
        }
        let maxBubbleWidth = max(168, floor(availableWidth * 0.80))
        return min(preferred, maxBubbleWidth)
    }

    private func configureFavoritePreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        previewWidthConstraint?.constant = adaptiveFavoritePreviewWidth()
        previewWidthConstraint?.isActive = true
        previewView.backgroundColor = UIColor.white.withAlphaComponent(0.24)
        previewView.layer.borderColor = UIColor.white.withAlphaComponent(0.36).cgColor
        previewHeightConstraint?.constant = 108
        previewTitleLeadingConstraint?.constant = 10
        previewIconView.isHidden = false
        previewIconView.backgroundColor = UIColor(red: 0.96, green: 0.78, blue: 0.28, alpha: 1)
        previewIconView.layer.cornerRadius = 8
        previewIconView.clipsToBounds = true
        previewIconView.tintColor = .white
        previewIconView.contentMode = .scaleAspectFill
        previewIconView.image = UIImage(systemName: "star.fill")
        previewTitleLabel.font = .systemFont(ofSize: 13.5, weight: .semibold)
        previewTitleLabel.numberOfLines = 2
        previewTitleLabel.text = message.body.isEmpty ? "收藏内容" : message.body
        previewDetailLabel.font = .systemFont(ofSize: 11.2)
        previewDetailLabel.numberOfLines = 3
        previewDetailLabel.attributedText = favoritePreviewDetail(for: message, fallback: template.secondaryText)
        metaLabel.text = "\(message.displayTimestamp)  收藏分享"

        if let url = message.attachmentURL {
            requestMediaThumbnail(
                for: message,
                url: url,
                kind: .image(trimsTransparentCanvas: url.pathExtension.lowercased() != "gif"),
                targetSize: CGSize(width: 84, height: 84)
            ) { bubble, image in
                bubble.previewIconView.image = image
            }
        }
    }

    private func adaptiveFavoritePreviewWidth() -> CGFloat {
        let preferred: CGFloat = 236
        let availableWidth: CGFloat
        if bounds.width > 1 {
            availableWidth = bounds.width
        } else {
            availableWidth = UIScreen.main.bounds.width - 148
        }
        let maxBubbleWidth = max(188, floor(availableWidth * 0.80))
        return min(preferred, maxBubbleWidth)
    }

    private func configureWechatStyleCardPreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        previewView.isHidden = false
        previewView.isHidden = true
        wechatCardContainer.isHidden = false
        let tint = cardPreviewTint(for: message.type)
        let title = message.body.isEmpty ? template.primaryText : message.body
        let detailLines = cardPreviewLines(for: message, fallback: template.secondaryText)
        let subtitle = detailLines.prefix(message.type == .miniProgram ? 2 : 3).joined(separator: "\n")

        wechatCardContainer.backgroundColor = UIColor.white.withAlphaComponent(message.isOutgoing ? 0.96 : 0.92)
        wechatCardContainer.layer.borderWidth = 1
        wechatCardContainer.layer.borderColor = UIColor.white.withAlphaComponent(0.48).cgColor
        wechatCardSourceIconView.image = UIImage(systemName: cardPreviewSourceSymbolName(for: message.type))
        wechatCardSourceIconView.tintColor = tint
        wechatCardSourceLabel.text = cardPreviewSourceName(for: message, fallback: cardPreviewFooter(for: message.type))
        wechatCardBadgeLabel.text = cardPreviewBadgeText(for: message.type)
        wechatCardBadgeLabel.backgroundColor = tint.withAlphaComponent(0.86)
        wechatCardTitleLabel.text = title
        wechatCardSubtitleLabel.text = subtitle
        wechatCardThumbView.backgroundColor = cardPreviewThumbBackground(for: message.type, outgoing: message.isOutgoing)
        wechatCardThumbIconView.image = UIImage(systemName: cardPreviewSymbolName(for: message.type))
        wechatCardThumbIconView.tintColor = tint
        wechatCardFooterIconView.image = UIImage(systemName: cardPreviewFooterSymbolName(for: message.type))
        wechatCardFooterIconView.tintColor = tint.withAlphaComponent(0.88)
        wechatCardFooterLabel.text = cardPreviewFooterDetail(for: message)
        wechatCardThumbWidthConstraint?.constant = message.type == .miniProgram ? 72 : 58
        wechatCardThumbHeightConstraint?.constant = message.type == .miniProgram ? 72 : 58
        previewHeightConstraint?.constant = 76
        metaLabel.text = "\(message.displayTimestamp)  \(cardPreviewFooter(for: message.type))"
    }

    private func configureWechatMiniProgramSharePreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        previewView.isHidden = true
        wechatCardContainer.isHidden = true
        miniProgramShareCardView.isHidden = false

        let lines = cardPreviewLines(for: message, fallback: template.secondaryText)
            .filter { !$0.contains(Self.wechatMiniProgramShareStyleMarker) }
        let source = miniProgramShareSource(from: lines)
        let page = miniProgramSharePage(from: lines)
        miniProgramShareCardView.configure(
            title: message.body.isEmpty ? "小程序页面" : message.body,
            source: source,
            page: page,
            heroSymbolName: message.detail.localizedCaseInsensitiveContains("order") || message.detail.contains("点餐") ? "takeoutbag.and.cup.and.straw.fill" : "app.badge.fill",
            isOutgoing: message.isOutgoing
        )
        metaLabel.text = "\(message.displayTimestamp)  小程序"
    }

    private func configureWebLinkSharePreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        previewView.isHidden = true
        wechatCardContainer.isHidden = true
        miniProgramShareCardView.isHidden = true
        linkShareCardView.isHidden = false

        let parsed = webLinkShareContent(for: message, fallback: template.secondaryText)
        linkShareCardView.configure(title: parsed.title, summary: parsed.summary, isOutgoing: message.isOutgoing)
        metaLabel.text = "\(message.displayTimestamp)  链接"
    }

    private func webLinkShareContent(for message: ChatMessage, fallback: String) -> (title: String, summary: String, urlText: String) {
        let combined = [message.body, message.detail]
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let urlText = Self.firstDetectedURLText(in: combined) ?? ""
        let host = Self.hostText(from: urlText)
        let titleSource = message.body
            .replacingOccurrences(of: urlText, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let title = titleSource.isEmpty ? (host.isEmpty ? "网页链接" : host) : titleSource
        let detailLines = cardPreviewLines(for: message, fallback: fallback)
            .filter { !$0.localizedCaseInsensitiveContains("网页链接") }
            .filter { !$0.contains(urlText) }
        let summary = detailLines.first ?? (host.isEmpty ? fallback : "\(host) - 点击申请访问")
        return (title, summary, urlText)
    }

    private static func firstDetectedURLText(in text: String) -> String? {
        let pattern = #"(?i)\b((?:https?://|www\.)[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: fullRange),
              let range = Range(match.range(at: 1), in: text)
        else { return nil }

        var end = range.upperBound
        while end > range.lowerBound,
              "。，、；;：:！？!?)）]】」\"'".contains(text[text.index(before: end)]) {
            end = text.index(before: end)
        }
        return String(text[range.lowerBound..<end])
    }

    private static func hostText(from urlText: String) -> String {
        guard !urlText.isEmpty else { return "" }
        let normalized = urlText.lowercased().hasPrefix("http") ? urlText : "https://\(urlText)"
        return URL(string: normalized)?.host ?? urlText
    }

    private func miniProgramShareSource(from lines: [String]) -> String {
        if let sourceLine = lines.first(where: {
            $0.hasPrefix("来源：")
                || $0.hasPrefix("来源名称：")
                || $0.hasPrefix("sourceName:")
                || $0.hasPrefix("sourceName：")
        }) {
            return sourceLine
                .replacingOccurrences(of: "来源：", with: "")
                .replacingOccurrences(of: "来源名称：", with: "")
                .replacingOccurrences(of: "sourceName:", with: "")
                .replacingOccurrences(of: "sourceName：", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let shortcutLine = lines.first(where: { $0.contains("#小程序://") }),
           let titlePart = shortcutLine.components(separatedBy: "#小程序://").last?.split(separator: "/", maxSplits: 1).first {
            let source = String(titlePart).split(separator: "丨", maxSplits: 1).first.map(String.init) ?? String(titlePart)
            let cleaned = source.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleaned.isEmpty { return cleaned }
        }
        if let line = lines.first(where: { $0.contains("小程序") }) {
            return line
        }
        return "微信小程序"
    }

    private func miniProgramSharePage(from lines: [String]) -> String {
        if let shortcutLine = lines.first(where: { $0.contains("#小程序://") }),
           let titlePart = shortcutLine.components(separatedBy: "#小程序://").last?.split(separator: "/", maxSplits: 1).first {
            let pieces = String(titlePart).split(separator: "丨", maxSplits: 1).map(String.init)
            if pieces.count > 1 {
                return pieces[1].trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        if let pageLine = lines.first(where: { $0.hasPrefix("页面：") || $0.hasPrefix("pagePath：") || $0.hasPrefix("pagePath:") }) {
            return pageLine
                .replacingOccurrences(of: "页面：", with: "")
                .replacingOccurrences(of: "pagePath：", with: "")
                .replacingOccurrences(of: "pagePath:", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let urlLine = lines.first(where: { $0.hasPrefix("http://") || $0.hasPrefix("https://") }) {
            return urlLine
        }
        return "点击后跳转微信打开"
    }

    private func cardPreviewDetail(for message: ChatMessage, fallback: String) -> NSAttributedString {
        let detailLines = cardPreviewLines(for: message, fallback: fallback)
        let summary = detailLines.first ?? fallback
        let source = cardPreviewSourceText(for: message)
        let extra = detailLines.dropFirst().prefix(2).joined(separator: " · ")
        let text = extra.isEmpty ? "\(summary)\n\(source)" : "\(summary)\n\(extra)\n\(source)"
        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: UIFont.systemFont(ofSize: 11.4, weight: .regular),
                .foregroundColor: rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.84) : Self.detailColor
            ]
        )
        if let sourceRange = text.range(of: source) {
            attributed.addAttributes(
                [
                    .font: UIFont.systemFont(ofSize: 10.5, weight: .medium),
                    .foregroundColor: (rendersAIMessage ? Self.aiTextColor : Self.mutedColor).withAlphaComponent(0.76)
                ],
                range: NSRange(sourceRange, in: text)
            )
        }
        return attributed
    }

    private func cardPreviewLines(for message: ChatMessage, fallback: String) -> [String] {
        let cleaned = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return cleaned.isEmpty ? [fallback] : cleaned
    }

    private func cardPreviewSourceText(for message: ChatMessage) -> String {
        switch message.type {
        case .miniProgram:
            let appName = message.detail.localizedCaseInsensitiveContains("douyin") || message.detail.contains("抖音") ? "抖音小程序" : "微信小程序"
            return "\(appName) · 点击跳转对应 App"
        case .article:
            return "公众号文章 · 点击查看图文详情"
        case .channelsVideo:
            return "视频号视频 · 点击播放"
        case .channelsLive:
            return "视频号直播 · 点击进入直播间"
        case .webLink:
            return "网页链接 · 点击打开浏览器"
        case .groupInvite:
            return "群邀请卡片 · 点击接受加入"
        case .contactCard:
            return "名片 · 点击查看详细资料"
        default:
            return "卡片消息 · 点击查看"
        }
    }

    private func cardPreviewFooter(for type: ChatMessageType) -> String {
        switch type {
        case .miniProgram: return "小程序"
        case .article: return "公众号"
        case .channelsVideo, .channelsLive: return "视频号"
        case .contactCard: return "名片"
        case .groupInvite: return "群邀请"
        case .webLink: return "链接"
        default: return type.title
        }
    }

    private func cardPreviewBadgeText(for type: ChatMessageType) -> String {
        switch type {
        case .miniProgram: return "小程序"
        case .article: return "图文"
        case .channelsVideo: return "视频"
        case .channelsLive: return "直播"
        case .groupInvite: return "邀请"
        case .contactCard: return "名片"
        case .webLink: return "网页"
        case .music: return "音乐"
        default: return "卡片"
        }
    }

    private func cardPreviewSourceName(for message: ChatMessage, fallback: String) -> String {
        let lines = cardPreviewLines(for: message, fallback: fallback)
        let explicitSource = lines.first { line in
            line.contains("sourceName")
                || line.contains("来源")
                || line.contains("公众号")
                || line.contains("视频号")
                || line.contains("小程序")
        }
        if let explicitSource {
            return explicitSource
                .replacingOccurrences(of: "sourceName:", with: "")
                .replacingOccurrences(of: "来源：", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        switch message.type {
        case .miniProgram:
            return lines.joined(separator: " ").localizedCaseInsensitiveContains("douyin") || lines.joined(separator: " ").contains("抖音")
                ? "抖音"
                : "微信"
        case .article:
            return "公众号"
        case .channelsVideo, .channelsLive:
            return "视频号"
        case .groupInvite:
            return "群聊邀请"
        case .contactCard:
            return "名片"
        case .webLink:
            return "网页链接"
        default:
            return fallback
        }
    }

    private func cardPreviewFooterDetail(for message: ChatMessage) -> String {
        switch message.type {
        case .miniProgram:
            return "小程序页面 · 点击跳转对应 App"
        case .article:
            return "公众号文章 · 查看图文"
        case .channelsVideo:
            return "视频号 · 点击播放"
        case .channelsLive:
            return "视频号直播 · 进入直播间"
        case .groupInvite:
            return "群邀请 · 点击接受加入"
        case .contactCard:
            return "名片 · 查看详细资料"
        case .webLink:
            return "网页链接 · 打开浏览器"
        case .music:
            return "音乐分享 · 点击播放"
        default:
            return cardPreviewSourceText(for: message)
        }
    }

    private func cardPreviewSymbolName(for type: ChatMessageType) -> String {
        switch type {
        case .miniProgram: return "app.fill"
        case .article: return "newspaper.fill"
        case .channelsVideo: return "play.rectangle.fill"
        case .channelsLive: return "dot.radiowaves.left.and.right"
        case .contactCard: return "person.crop.rectangle.fill"
        case .groupInvite: return "person.2.fill"
        case .webLink: return "link"
        default: return type.symbolName
        }
    }

    private func cardPreviewSourceSymbolName(for type: ChatMessageType) -> String {
        switch type {
        case .miniProgram: return "app.badge.fill"
        case .article: return "checkmark.seal.fill"
        case .channelsVideo, .channelsLive: return "play.rectangle.fill"
        case .groupInvite: return "person.2.circle.fill"
        case .contactCard: return "person.crop.circle.fill"
        case .webLink: return "safari.fill"
        case .music: return "music.note"
        default: return type.symbolName
        }
    }

    private func cardPreviewFooterSymbolName(for type: ChatMessageType) -> String {
        switch type {
        case .miniProgram: return "app.fill"
        case .article: return "newspaper.fill"
        case .channelsVideo, .channelsLive: return "play.tv.fill"
        case .groupInvite: return "person.2.fill"
        case .contactCard: return "person.text.rectangle.fill"
        case .webLink: return "link"
        case .music: return "music.quarternote.3"
        default: return type.symbolName
        }
    }

    private func cardPreviewThumbBackground(for type: ChatMessageType, outgoing: Bool) -> UIColor {
        switch type {
        case .miniProgram:
            return UIColor(red: 0.90, green: 0.98, blue: 0.94, alpha: 1)
        case .article:
            return UIColor(red: 1.00, green: 0.94, blue: 0.87, alpha: 1)
        case .channelsVideo, .channelsLive:
            return UIColor(red: 1.00, green: 0.90, blue: 0.92, alpha: 1)
        case .groupInvite, .contactCard:
            return UIColor(red: 0.88, green: 0.94, blue: 1.00, alpha: 1)
        case .webLink:
            return UIColor(red: 0.88, green: 0.94, blue: 1.00, alpha: 1)
        case .music:
            return UIColor(red: 0.95, green: 0.90, blue: 1.00, alpha: 1)
        default:
            return UIColor.white.withAlphaComponent(outgoing ? 0.62 : 0.54)
        }
    }

    private func cardPreviewTint(for type: ChatMessageType) -> UIColor {
        switch type {
        case .miniProgram:
            return UIColor(red: 0.12, green: 0.62, blue: 0.42, alpha: 1)
        case .article:
            return UIColor(red: 0.95, green: 0.54, blue: 0.18, alpha: 1)
        case .channelsVideo, .channelsLive:
            return UIColor(red: 0.88, green: 0.24, blue: 0.34, alpha: 1)
        case .contactCard, .groupInvite:
            return UIColor(red: 0.18, green: 0.48, blue: 0.76, alpha: 1)
        case .webLink:
            return UIColor(red: 0.14, green: 0.46, blue: 0.86, alpha: 1)
        default:
            return type.accentColor
        }
    }

    private func configureRelayPreview(for message: ChatMessage, template: MessageRenderTemplate) {
        titleRow.isHidden = false
        bodyLabel.isHidden = false
        bodyLabel.font = .systemFont(ofSize: 14.5, weight: .semibold)
        bodyLabel.numberOfLines = 2
        bodyLabel.text = message.body.isEmpty ? "接龙" : message.body
        previewView.isHidden = false
        previewView.backgroundColor = UIColor.white.withAlphaComponent(0.26)
        previewView.layer.borderColor = UIColor.white.withAlphaComponent(0.36).cgColor
        previewHeightConstraint?.constant = 78
        previewTitleLeadingConstraint?.constant = 10
        previewIconView.isHidden = false
        previewIconView.image = UIImage(systemName: template.symbolName)
        previewIconView.tintColor = UIColor(red: 0.05, green: 0.28, blue: 0.34, alpha: 0.82)
        previewIconView.backgroundColor = UIColor.white.withAlphaComponent(0.24)
        previewIconView.layer.cornerRadius = 7
        previewIconView.layer.cornerCurve = .continuous
        previewIconView.clipsToBounds = true
        previewTitleLabel.isHidden = false
        previewTitleLabel.font = .systemFont(ofSize: 13.5, weight: .semibold)
        previewTitleLabel.numberOfLines = 1
        previewTitleLabel.text = "接龙详情"
        previewDetailLabel.isHidden = false
        previewDetailLabel.font = .systemFont(ofSize: 11.5, weight: .regular)
        previewDetailLabel.numberOfLines = 3
        previewDetailLabel.attributedText = relayPreviewDetail(for: message)
        metaLabel.text = message.displayTimestamp
    }

    private func relayPreviewDetail(for message: ChatMessage) -> NSAttributedString {
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let noteLines = lines.filter { $0.hasPrefix("备注：") }
        let entryLines = lines.filter {
            !$0.hasPrefix("备注：")
                && !$0.hasPrefix("发起人ID：")
                && $0.range(of: #"^\d+[\.\、]\s*"#, options: .regularExpression) != nil
        }
        let visibleEntries = entryLines.prefix(3)
        var previewLines: [String] = []
        previewLines.append(contentsOf: noteLines.prefix(1))
        previewLines.append(contentsOf: visibleEntries)
        if entryLines.count > visibleEntries.count {
            previewLines.append("还有 \(entryLines.count - visibleEntries.count) 条接龙")
        }
        if previewLines.isEmpty {
            previewLines.append("暂无接龙，点击后填写自己的接龙信息")
        }

        let text = previewLines.joined(separator: "\n")
        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: UIFont.systemFont(ofSize: 11.5, weight: .regular),
                .foregroundColor: rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.84) : Self.detailColor
            ]
        )
        for note in noteLines.prefix(1) {
            let range = (text as NSString).range(of: note)
            guard range.location != NSNotFound else { continue }
            attributed.addAttributes(
                [
                    .font: UIFont.systemFont(ofSize: 11.5, weight: .semibold),
                    .foregroundColor: rendersAIMessage ? Self.aiTextColor : UIColor(red: 0.05, green: 0.28, blue: 0.34, alpha: 0.92)
                ],
                range: range
            )
        }
        return attributed
    }

    private func favoritePreviewDetail(for message: ChatMessage, fallback: String) -> NSAttributedString {
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let kind = lines.first ?? fallback
        let summary = lines.dropFirst().joined(separator: "\n")
        let text = summary.isEmpty ? kind : "\(kind)\n\(summary)"
        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: UIFont.systemFont(ofSize: 11.2),
                .foregroundColor: rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.84) : Self.detailColor
            ]
        )
        if !kind.isEmpty {
            attributed.addAttributes(
                [
                    .font: UIFont.systemFont(ofSize: 11.4, weight: .semibold),
                    .foregroundColor: UIColor(red: 0.57, green: 0.34, blue: 0.06, alpha: 1)
                ],
                range: NSRange(location: 0, length: min(kind.utf16.count, attributed.length))
            )
        }
        return attributed
    }

    private func configureContactCardPreview(_ profile: ContactCardProfile) {
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        previewView.isHidden = true
        contactCardView.configure(profile: profile)
        metaLabel.text = "\(metaLabel.text ?? "")  名片"
    }

    private func fileExtensionLabel(for message: ChatMessage) -> String {
        let filename = message.attachmentURL?.lastPathComponent ?? message.body
        let lowercased = filename.lowercased()
        if lowercased.hasSuffix(".pdf") { return "PDF" }
        if lowercased.hasSuffix(".md") { return "MD" }
        if lowercased.hasSuffix(".txt") { return "TXT" }
        if lowercased.hasSuffix(".csv") { return "CSV" }
        if lowercased.hasSuffix(".doc") || lowercased.hasSuffix(".docx") { return "DOC" }
        if lowercased.hasSuffix(".xls") || lowercased.hasSuffix(".xlsx") { return "XLS" }
        if lowercased.hasSuffix(".ppt") || lowercased.hasSuffix(".pptx") { return "PPT" }
        if lowercased.hasSuffix(".numbers") { return "NUM" }
        if lowercased.hasSuffix(".pages") { return "PAGE" }
        if lowercased.hasSuffix(".key") { return "KEY" }
        if lowercased.hasSuffix(".zip") || lowercased.hasSuffix(".rar") || lowercased.hasSuffix(".7z") { return "ZIP" }
        return "FILE"
    }

    private func showInlineMediaImage(_ image: UIImage, showsPlayIcon: Bool) {
        mediaImageView.image = image
        mediaImageView.isHidden = false
        mediaPlayIconView.isHidden = !showsPlayIcon
        setMediaPrivacyBlurVisible(false)
        titleRow.isHidden = true
        bodyLabel.isHidden = true
        detailLabel.isHidden = true
        metaLabel.isHidden = true
        previewIconView.isHidden = true
        previewTitleLabel.isHidden = true
        previewDetailLabel.isHidden = true
        if inlineNameLabel.superview === bubbleView {
            bubbleView.bringSubviewToFront(inlineNameLabel)
        }
    }

    private func setMediaPrivacyBlurVisible(_ visible: Bool) {
        mediaPrivacyOverlayView.setVisible(visible)
        if visible {
            previewView.bringSubviewToFront(mediaPrivacyOverlayView)
            previewView.bringSubviewToFront(mediaPlayIconView)
            if inlineNameLabel.superview === bubbleView {
                bubbleView.bringSubviewToFront(inlineNameLabel)
            }
        }
    }

    private func shouldBlurInlineMedia() -> Bool {
        false
    }

    private static func linkifiedText(_ text: String, font: UIFont, baseColor: UIColor) -> NSAttributedString {
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.minimumLineHeight = 16.8
        paragraphStyle.maximumLineHeight = 16.8
        let opaqueBaseColor = baseColor.withAlphaComponent(1)
        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: opaqueBaseColor,
                .paragraphStyle: paragraphStyle
            ]
        )
        let tokenAttributes: [NSAttributedString.Key: Any] = [
            .foregroundColor: UIColor.systemBlue,
            .font: UIFont.systemFont(ofSize: font.pointSize, weight: .semibold)
        ]
        for matchRange in detectedMentionRanges(in: text) + detectedFileTokenRanges(in: text) {
            attributed.addAttributes(tokenAttributes, range: matchRange)
        }
        for matchRange in detectedURLRanges(in: text) {
            attributed.addAttributes(
                [
                    .foregroundColor: UIColor.systemBlue,
                    .underlineStyle: NSUnderlineStyle.single.rawValue
                ],
                range: matchRange
            )
        }
        return attributed
    }

    private static func detectedMentionRanges(in text: String) -> [NSRange] {
        detectedRanges(in: text, pattern: #"(?<!\S)@[\p{Han}A-Za-z0-9_·-]{1,24}"#)
    }

    private static func detectedFileTokenRanges(in text: String) -> [NSRange] {
        detectedRanges(
            in: text,
            pattern: #"(?<!\S)[@#][^\s@#]{1,48}\.(?:txt|md|doc|docx|pdf|xls|xlsx|ppt|pptx|zip|rar|png|jpg|jpeg)"#
        )
    }

    private static func detectedURLRanges(in text: String) -> [NSRange] {
        let pattern = #"(?i)\b((?:https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]*|www\.[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+))"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.matches(in: text, range: fullRange).compactMap { match in
            guard let range = Range(match.range(at: 1), in: text) else { return nil }
            var end = range.upperBound
            while text[range.lowerBound..<end].lowercased() != "http://",
                  text[range.lowerBound..<end].lowercased() != "https://",
                  end > range.lowerBound,
                  Self.trailingURLPunctuation.contains(text[text.index(before: end)]) {
                end = text.index(before: end)
            }
            guard end > range.lowerBound else { return nil }
            return NSRange(range.lowerBound..<end, in: text)
        }
    }

    private static func detectedRanges(in text: String, pattern: String) -> [NSRange] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.matches(in: text, range: fullRange).map(\.range)
    }

    private static let trailingURLPunctuation: Set<Character> = [
        ".", ",", "!", "?", ":", ";", ")", "]", "}", "。", "，", "！", "？", "：", "；"
    ]

    private func renderRichElements(_ elements: [BubbleRichElement]) {
        var inline = NSMutableAttributedString()
        var inlineLinks: [(range: NSRange, url: String)] = []

        func flushInline() {
            guard inline.length > 0 else { return }
            let label = richLabel()
            label.attributedText = inline
            for link in inlineLinks {
                richTextLinkHitTargets.append(RichTextLinkHitTarget(label: label, range: link.range, url: link.url))
            }
            richStack.addArrangedSubview(label)
            inline = NSMutableAttributedString()
            inlineLinks.removeAll()
        }

        func appendLinkText(_ text: String, url: String, attributes: [NSAttributedString.Key: Any]) {
            let start = inline.length
            inline.append(NSAttributedString(string: text, attributes: attributes))
            inlineLinks.append((range: NSRange(location: start, length: (text as NSString).length), url: url))
        }

        var previousWasImage = false
        for element in elements {
            switch element {
            case .text(let text):
                previousWasImage = false
                inline.append(NSAttributedString(string: text, attributes: richTextAttributes()))
            case .blueLink(let label, let url, let bracketed):
                previousWasImage = false
                let text = bracketed ? "[\(label)]" : label
                appendLinkText(text, url: url, attributes: linkAttributes())
                if bracketed {
                    inline.append(NSAttributedString(string: " ", attributes: richTextAttributes()))
                    appendLinkText(url, url: url, attributes: smallLinkAttributes())
                }
            case .taggedFile(let label, let url, let prefix):
                previousWasImage = false
                appendLinkText("\(prefix)\(label)", url: url, attributes: linkAttributes())
                inline.append(NSAttributedString(string: " ", attributes: richTextAttributes()))
                appendLinkText(url, url: url, attributes: smallLinkAttributes())
            case .mention(let name):
                previousWasImage = false
                inline.append(NSAttributedString(string: "@\(name)", attributes: linkAttributes()))
            case .aiToken(let text):
                previousWasImage = false
                inline.append(NSAttributedString(string: "✦\(text)", attributes: aiTokenAttributes()))
            case .image(let name, let url, let aspect, let access):
                flushInline()
                if previousWasImage {
                    richStack.addArrangedSubview(makeImageConnector())
                }
                richStack.addArrangedSubview(makeImagePreview(name: name, url: url, aspect: aspect, access: access))
                previousWasImage = true
            case .inlineCard(let kind, let title, let subtitle, let url):
                flushInline()
                previousWasImage = false
                let card = makeInlineCard(kind: kind, title: title, subtitle: subtitle, url: url)
                inlineCardHitTargets.append((
                    view: card,
                    interaction: InlineCardInteraction(kind: kind, title: title, subtitle: subtitle, url: url)
                ))
                richStack.addArrangedSubview(card)
            case .location(let title, let address, let url):
                flushInline()
                previousWasImage = false
                richStack.addArrangedSubview(makeLocationRow(title: title, address: address, url: url))
            case .quote(let author, let text):
                flushInline()
                previousWasImage = false
                richStack.addArrangedSubview(makeQuoteView(author: author, text: text))
            case .file(let name, let format, let url, let preview, let access):
                flushInline()
                previousWasImage = false
                richStack.addArrangedSubview(makeFilePreview(name: name, format: format, url: url, preview: preview, access: access))
            }
        }
        flushInline()
    }

    private func richLabel() -> UILabel {
        let label = LayeredShadowLabel()
        label.numberOfLines = 0
        label.lineBreakMode = .byWordWrapping
        label.textShadows = Self.messageTextShadows(isAI: rendersAIMessage)
        label.setContentCompressionResistancePriority(.required, for: .vertical)
        return label
    }

    private func makeImagePreview(name: String, url: String, aspect: BubbleImageAspect, access: BubbleAccessScope) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(red: 0.10, green: 0.15, blue: 0.17, alpha: 0.72)
        container.layer.cornerRadius = 12
        container.layer.cornerCurve = .continuous
        container.clipsToBounds = true
        container.translatesAutoresizingMaskIntoConstraints = false

        let previewLayer = UIImageView(image: inlineImage(for: url, name: name, aspect: aspect))
        previewLayer.contentMode = isLocalImageURL(url) ? .scaleAspectFit : .scaleAspectFill
        previewLayer.alpha = isLocalImageURL(url) ? 1 : 0.78
        previewLayer.backgroundColor = UIColor.black.withAlphaComponent(0.18)
        previewLayer.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(previewLayer)
        richMediaHitTargets.append((
            view: container,
            interaction: RichMediaInteraction(
                kind: .image,
                action: .preview,
                title: name,
                url: url,
                access: .public,
                preview: "图片可直接查看"
            )
        ))

        let height = Self.richImagePreviewHeight(url: url, aspect: aspect)
        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(equalToConstant: height),
            previewLayer.topAnchor.constraint(equalTo: container.topAnchor),
            previewLayer.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            previewLayer.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            previewLayer.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        if let imageURL = URL(string: url), let expectedMessageID = configuredMessageID {
            requestMediaThumbnail(
                expectedMessageID: expectedMessageID,
                url: imageURL,
                kind: .image(trimsTransparentCanvas: true),
                targetSize: CGSize(width: 360, height: height)
            ) { _, image in
                previewLayer.image = image
                previewLayer.alpha = 1
            }
        }
        return container
    }

    private func makeImageConnector() -> UIView {
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        let line = UIView()
        line.backgroundColor = UIColor.white.withAlphaComponent(0.24)
        line.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(line)
        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(equalToConstant: 8),
            line.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 10),
            line.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -10),
            line.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            line.heightAnchor.constraint(equalToConstant: 1)
        ])
        return container
    }

    private func inlineImage(for urlText: String, name: String, aspect: BubbleImageAspect) -> UIImage {
        return generatedBlurredInlineImage(name: name, aspect: aspect)
    }

    private func isLocalImageURL(_ urlText: String) -> Bool {
        URL(string: urlText)?.isFileURL == true
    }

    private func generatedBlurredInlineImage(name: String, aspect: BubbleImageAspect) -> UIImage {
        let size = CGSize(width: aspect == .vertical ? 160 : 260, height: aspect == .vertical ? 260 : 120)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.18, green: 0.35, blue: 0.42, alpha: 1).setFill()
            context.fill(rect)
            UIColor(red: 0.70, green: 0.88, blue: 0.78, alpha: 0.50).setFill()
            UIBezierPath(ovalIn: CGRect(x: -30, y: 10, width: size.width * 0.72, height: size.height * 0.42)).fill()
            UIColor(red: 0.94, green: 0.62, blue: 0.32, alpha: 0.42).setFill()
            UIBezierPath(ovalIn: CGRect(x: size.width * 0.42, y: size.height * 0.38, width: size.width * 0.72, height: size.height * 0.46)).fill()
            UIColor.white.withAlphaComponent(0.24).setFill()
            for index in 0..<5 {
                let y = CGFloat(index) * size.height / 5 + 8
                UIBezierPath(
                    roundedRect: CGRect(x: 16, y: y, width: size.width - 32, height: 9),
                    cornerRadius: 4.5
                ).fill()
            }
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .bold),
                .foregroundColor: UIColor.white.withAlphaComponent(0.32)
            ]
            (name as NSString).draw(
                in: CGRect(x: 16, y: size.height * 0.45, width: size.width - 32, height: 24),
                withAttributes: attributes
            )
        }
    }

    private func makeInlineCard(kind: BubbleCardKind, title: String, subtitle: String, url: String) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.white.withAlphaComponent(rendersAIMessage ? 0.10 : 0.24)
        container.layer.cornerRadius = 12
        container.layer.cornerCurve = .continuous
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.white.withAlphaComponent(0.28).cgColor
        container.translatesAutoresizingMaskIntoConstraints = false

        let topArea = UIView()
        topArea.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(topArea)

        let divider = UIView()
        divider.backgroundColor = UIColor.white.withAlphaComponent(0.22)
        divider.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(divider)

        let footerIcon = UIImageView(image: UIImage(systemName: footerSymbolName(for: kind)))
        footerIcon.tintColor = cardTintColor(for: kind).withAlphaComponent(0.88)
        footerIcon.contentMode = .scaleAspectFit
        footerIcon.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(footerIcon)

        let footerLabel = UILabel()
        footerLabel.text = footerText(for: kind)
        footerLabel.font = .systemFont(ofSize: 10.5, weight: .medium)
        footerLabel.textColor = (rendersAIMessage ? Self.aiTextColor : Self.mutedColor).withAlphaComponent(0.78)
        footerLabel.numberOfLines = 1
        footerLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(footerLabel)

        let iconBox = UIView()
        iconBox.backgroundColor = cardTintColor(for: kind).withAlphaComponent(0.16)
        iconBox.layer.cornerRadius = 9
        iconBox.layer.cornerCurve = .continuous
        iconBox.translatesAutoresizingMaskIntoConstraints = false
        topArea.addSubview(iconBox)

        let icon = UIImageView(image: UIImage(systemName: cardSymbolName(for: kind)))
        icon.tintColor = cardTintColor(for: kind)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconBox.addSubview(icon)

        let typeBadge = PaddingLabel()
        typeBadge.text = kind.rawValue
        typeBadge.font = .systemFont(ofSize: 9.5, weight: .bold)
        typeBadge.textColor = UIColor.white.withAlphaComponent(0.92)
        typeBadge.backgroundColor = cardTintColor(for: kind).withAlphaComponent(0.72)
        typeBadge.layer.cornerRadius = 7
        typeBadge.layer.cornerCurve = .continuous
        typeBadge.clipsToBounds = true
        typeBadge.translatesAutoresizingMaskIntoConstraints = false
        topArea.addSubview(typeBadge)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.textColor = rendersAIMessage ? Self.aiTextColor : Self.textColor
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        topArea.addSubview(titleLabel)

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 11.5, weight: .regular)
        subtitleLabel.textColor = (rendersAIMessage ? Self.aiTextColor : Self.detailColor).withAlphaComponent(0.82)
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        topArea.addSubview(subtitleLabel)
        applyBubbleTextShadow(to: titleLabel, subtitleLabel, footerLabel)

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = UIColor.white.withAlphaComponent(0.54)
        chevron.contentMode = .scaleAspectFit
        chevron.translatesAutoresizingMaskIntoConstraints = false
        topArea.addSubview(chevron)

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: 96),
            topArea.topAnchor.constraint(equalTo: container.topAnchor),
            topArea.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            topArea.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            topArea.heightAnchor.constraint(greaterThanOrEqualToConstant: 68),
            divider.topAnchor.constraint(equalTo: topArea.bottomAnchor),
            divider.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 12),
            divider.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -12),
            divider.heightAnchor.constraint(equalToConstant: 1),
            footerIcon.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 12),
            footerIcon.topAnchor.constraint(equalTo: divider.bottomAnchor, constant: 8),
            footerIcon.widthAnchor.constraint(equalToConstant: 12),
            footerIcon.heightAnchor.constraint(equalToConstant: 12),
            footerLabel.leadingAnchor.constraint(equalTo: footerIcon.trailingAnchor, constant: 5),
            footerLabel.centerYAnchor.constraint(equalTo: footerIcon.centerYAnchor),
            footerLabel.trailingAnchor.constraint(lessThanOrEqualTo: container.trailingAnchor, constant: -12),
            footerLabel.bottomAnchor.constraint(lessThanOrEqualTo: container.bottomAnchor, constant: -8),
            iconBox.leadingAnchor.constraint(equalTo: topArea.leadingAnchor, constant: 12),
            iconBox.centerYAnchor.constraint(equalTo: topArea.centerYAnchor),
            iconBox.widthAnchor.constraint(equalToConstant: 44),
            iconBox.heightAnchor.constraint(equalToConstant: 44),
            icon.centerXAnchor.constraint(equalTo: iconBox.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconBox.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 22),
            icon.heightAnchor.constraint(equalToConstant: 22),
            typeBadge.leadingAnchor.constraint(equalTo: iconBox.trailingAnchor, constant: 10),
            typeBadge.topAnchor.constraint(equalTo: topArea.topAnchor, constant: 9),
            typeBadge.heightAnchor.constraint(greaterThanOrEqualToConstant: 18),
            chevron.trailingAnchor.constraint(equalTo: topArea.trailingAnchor, constant: -10),
            chevron.centerYAnchor.constraint(equalTo: topArea.centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 12),
            chevron.heightAnchor.constraint(equalToConstant: 18),
            titleLabel.leadingAnchor.constraint(equalTo: typeBadge.leadingAnchor),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),
            titleLabel.topAnchor.constraint(equalTo: typeBadge.bottomAnchor, constant: 5),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 2),
            subtitleLabel.bottomAnchor.constraint(lessThanOrEqualTo: topArea.bottomAnchor, constant: -9)
        ])

        return container
    }

    private func cardSymbolName(for kind: BubbleCardKind) -> String {
        switch kind {
        case .enterprise:
            return "building.2.crop.circle"
        case .personal:
            return "person.crop.square"
        case .officialAccount:
            return "megaphone.fill"
        case .miniProgramProfile, .miniProgramLink:
            return "app.fill"
        case .channelsProfile:
            return "play.rectangle.fill"
        }
    }

    private func footerSymbolName(for kind: BubbleCardKind) -> String {
        switch kind {
        case .enterprise:
            return "briefcase.fill"
        case .personal:
            return "person.fill"
        case .officialAccount:
            return "checkmark.seal.fill"
        case .miniProgramProfile, .miniProgramLink:
            return "app.fill"
        case .channelsProfile:
            return "play.rectangle.fill"
        }
    }

    private func footerText(for kind: BubbleCardKind) -> String {
        switch kind {
        case .enterprise:
            return "企业微信名片 · 点击查看"
        case .personal:
            return "个人名片 · 点击查看资料"
        case .officialAccount:
            return "公众号名片 · 点击查看主页"
        case .miniProgramProfile:
            return "小程序名片 · 跳转对应 App"
        case .miniProgramLink:
            return "小程序链接 · 跳转对应 App"
        case .channelsProfile:
            return "视频号名片 · 点击进入主页"
        }
    }

    private func cardTintColor(for kind: BubbleCardKind) -> UIColor {
        switch kind {
        case .enterprise:
            return UIColor(red: 0.16, green: 0.48, blue: 0.88, alpha: 1)
        case .personal:
            return UIColor(red: 0.14, green: 0.62, blue: 0.42, alpha: 1)
        case .officialAccount:
            return UIColor(red: 0.96, green: 0.58, blue: 0.18, alpha: 1)
        case .miniProgramProfile, .miniProgramLink:
            return UIColor(red: 0.39, green: 0.34, blue: 0.88, alpha: 1)
        case .channelsProfile:
            return UIColor(red: 0.90, green: 0.26, blue: 0.34, alpha: 1)
        }
    }

    private func makeLocationRow(title: String, address: String, url: String) -> UIView {
        iconTextRow(symbolName: "location.fill", title: title, subtitle: "\(address) \(url)")
    }

    private func makeQuoteView(author: String, text: String) -> UIView {
        iconTextRow(symbolName: "quote.bubble", title: "引用 \(author)", subtitle: text, minHeight: 52)
    }

    private func makeFilePreview(name: String, format: BubbleFileFormat, url: String, preview: String, access: BubbleAccessScope) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.white.withAlphaComponent(0.20)
        container.layer.cornerRadius = 12
        container.layer.cornerCurve = .continuous
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.white.withAlphaComponent(0.28).cgColor
        container.translatesAutoresizingMaskIntoConstraints = false
        richMediaHitTargets.append((
            view: container,
            interaction: RichMediaInteraction(
                kind: .file,
                action: .preview,
                title: name,
                url: url,
                access: access,
                preview: preview
            )
        ))

        let page = UIView()
        page.backgroundColor = UIColor.white.withAlphaComponent(0.90)
        page.layer.cornerRadius = 6
        page.layer.cornerCurve = .continuous
        page.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(page)

        let formatLabel = UILabel()
        formatLabel.text = format.rawValue
        formatLabel.font = .systemFont(ofSize: 9, weight: .bold)
        formatLabel.textColor = UIColor(red: 0.05, green: 0.36, blue: 0.48, alpha: 1)
        formatLabel.textAlignment = .center
        formatLabel.translatesAutoresizingMaskIntoConstraints = false
        page.addSubview(formatLabel)

        let titleLabel = UILabel()
        titleLabel.text = name
        titleLabel.font = .systemFont(ofSize: 12.5, weight: .semibold)
        titleLabel.textColor = rendersAIMessage ? Self.aiTextColor : Self.textColor
        titleLabel.numberOfLines = 1
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(titleLabel)

        let previewLabel = UILabel()
        previewLabel.text = preview
        previewLabel.font = .systemFont(ofSize: 10.5)
        previewLabel.textColor = rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.84) : Self.detailColor
        previewLabel.numberOfLines = 2
        previewLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(previewLabel)

        let linkLabel = UILabel()
        linkLabel.text = "\(url) · \(access.rawValue)"
        linkLabel.font = .systemFont(ofSize: 9.5, weight: .medium)
        linkLabel.textColor = rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.82) : UIColor.systemBlue.withAlphaComponent(0.82)
        linkLabel.numberOfLines = 1
        linkLabel.lineBreakMode = .byTruncatingMiddle
        linkLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(linkLabel)
        applyBubbleTextShadow(to: titleLabel, previewLabel, linkLabel)
        richMediaHitTargets.append((
            view: linkLabel,
            interaction: RichMediaInteraction(
                kind: .file,
                action: .openLink,
                title: name,
                url: url,
                access: access,
                preview: preview
            )
        ))

        let actionStack = UIStackView()
        actionStack.axis = .horizontal
        actionStack.spacing = 6
        actionStack.alignment = .center
        actionStack.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(actionStack)
        [
            ("预览", RichMediaInteraction.Action.preview),
            ("查看文件", .preview),
            ("链接", .openLink)
        ].forEach { text, action in
            let label = PaddingLabel()
            label.text = text
            label.font = .systemFont(ofSize: 9.5, weight: .semibold)
            label.textColor = UIColor(red: 0.03, green: 0.27, blue: 0.34, alpha: 1)
            label.backgroundColor = UIColor.white.withAlphaComponent(0.78)
            label.layer.cornerRadius = 8
            label.layer.cornerCurve = .continuous
            label.clipsToBounds = true
            actionStack.addArrangedSubview(label)
            richMediaHitTargets.append((
                view: label,
                interaction: RichMediaInteraction(
                    kind: .file,
                    action: action,
                    title: name,
                    url: url,
                    access: access,
                    preview: preview
                )
            ))
        }

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: 138),
            page.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 10),
            page.topAnchor.constraint(equalTo: container.topAnchor, constant: 18),
            page.widthAnchor.constraint(equalToConstant: 42),
            page.heightAnchor.constraint(equalToConstant: 56),
            formatLabel.centerXAnchor.constraint(equalTo: page.centerXAnchor),
            formatLabel.centerYAnchor.constraint(equalTo: page.centerYAnchor),
            formatLabel.leadingAnchor.constraint(equalTo: page.leadingAnchor, constant: 4),
            formatLabel.trailingAnchor.constraint(equalTo: page.trailingAnchor, constant: -4),
            titleLabel.leadingAnchor.constraint(equalTo: page.trailingAnchor, constant: 10),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -10),
            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 11),
            previewLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            previewLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            previewLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),
            linkLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            linkLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            linkLabel.topAnchor.constraint(equalTo: previewLabel.bottomAnchor, constant: 5),
            actionStack.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            actionStack.trailingAnchor.constraint(lessThanOrEqualTo: titleLabel.trailingAnchor),
            actionStack.topAnchor.constraint(equalTo: linkLabel.bottomAnchor, constant: 7),
            actionStack.bottomAnchor.constraint(lessThanOrEqualTo: container.bottomAnchor, constant: -10)
        ])
        return container
    }

    private func iconTextRow(symbolName: String, title: String, subtitle: String, minHeight: CGFloat = 62) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        container.layer.cornerRadius = 10
        container.layer.cornerCurve = .continuous
        container.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: symbolName))
        icon.tintColor = UIColor(red: 0.05, green: 0.36, blue: 0.48, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        titleLabel.textColor = rendersAIMessage ? Self.aiTextColor : Self.textColor
        titleLabel.numberOfLines = 1
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(titleLabel)

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 10.5)
        subtitleLabel.textColor = rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.84) : Self.detailColor
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(subtitleLabel)
        applyBubbleTextShadow(to: titleLabel, subtitleLabel)

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: minHeight),
            icon.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 9),
            icon.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 24),
            icon.heightAnchor.constraint(equalToConstant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 9),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -9),
            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 2),
            subtitleLabel.bottomAnchor.constraint(lessThanOrEqualTo: container.bottomAnchor, constant: -8)
        ])
        return container
    }

    private func shouldShowInlineName(for message: ChatMessage, mode: BubbleMode) -> Bool {
        Self.shouldShowInlineNameStatic(for: message, mode: mode)
    }

    private func shouldShowInlineName(for message: ChatMessage, mode: BubbleMode, showsGroupName: Bool) -> Bool {
        Self.shouldShowInlineNameStatic(for: message, mode: mode, showsGroupName: showsGroupName)
    }

    private static func shouldShowInlineNameStatic(
        for message: ChatMessage,
        mode: BubbleMode,
        showsGroupName: Bool = true
    ) -> Bool {
        guard !isNotificationMessage(message) else { return false }
        if message.isOutgoing {
            return message.presentation == .avatarAndName || message.presentation == .nameOnly
        }
        if message.isGroupConversation {
            return showsGroupName
        }
        return message.isAI
    }

    private static func showsExternalSender(for message: ChatMessage, mode: BubbleMode, showsGroupName: Bool = true) -> Bool {
        guard !isNotificationMessage(message), !message.isOutgoing else { return false }
        guard message.isGroupConversation || message.isAI else { return false }
        return !shouldShowInlineNameStatic(for: message, mode: mode, showsGroupName: showsGroupName)
    }

    private static func showsAvatarStatic(for message: ChatMessage) -> Bool {
        if isNotificationMessage(message) {
            return false
        }
        if message.isAI && !message.isGroupConversation && message.sender.displayName == "Codex" {
            return false
        }
        return message.type != .system
            && !message.isOutgoing
            && (message.isGroupConversation || message.isAI)
    }

    private static func mode(for type: ChatMessageType) -> BubbleMode {
        switch type {
        case .text:
            return .text
        case .emoji:
            return .oversizedText
        case .stickerGif:
            return .media
        case .image, .capturedPhoto, .video:
            return .media
        case .channelsVideo:
            return .channelVideo
        case .voice:
            return .voice
        case .file:
            return .document
        case .location, .liveLocation:
            return .location
        case .contactCard, .groupInvite:
            return .profile
        case .webLink, .article, .miniProgram, .channelsLive, .music, .favorite:
            return .link
        case .mergedForward:
            return .stacked
        case .redPacket, .transfer, .splitBill, .coupon:
            return .money
        case .voiceCall, .videoCall:
            return .call
        case .quotedReply:
            return .quote
        case .relay:
            return .stacked
        case .groupNotice:
            return .notice
        case .system:
            return .system
        }
    }

    private static func isNotificationMessage(_ message: ChatMessage) -> Bool {
        message.type == .system || message.type == .groupNotice
    }

    private static func palette(for message: ChatMessage, mode: BubbleMode) -> BubblePalette {
        let accent = message.type.accentColor
        switch mode {
        case .system:
            return BubblePalette(
                background: UIColor.black.withAlphaComponent(0.18),
                previewBackground: UIColor.clear,
                accent: .white
            )
        case .money:
            return BubblePalette(
                background: UIColor.systemOrange.withAlphaComponent(0.24),
                previewBackground: UIColor.systemOrange.withAlphaComponent(0.16),
                accent: UIColor.systemOrange
            )
        case .location:
            return BubblePalette(
                background: UIColor.systemGreen.withAlphaComponent(0.14),
                previewBackground: UIColor.systemGreen.withAlphaComponent(0.18),
                accent: UIColor.systemGreen
            )
        case .voice, .call:
            return BubblePalette(
                background: UIColor.systemBlue.withAlphaComponent(0.16),
                previewBackground: UIColor.systemBlue.withAlphaComponent(0.18),
                accent: UIColor.systemBlue
            )
        case .media:
            return BubblePalette(
                background: UIColor.white.withAlphaComponent(0.18),
                previewBackground: accent.withAlphaComponent(0.28),
                accent: accent
            )
        case .channelVideo:
            return BubblePalette(
                background: UIColor.clear,
                previewBackground: UIColor.black,
                accent: UIColor(red: 1.00, green: 0.48, blue: 0.10, alpha: 1)
            )
        default:
            return BubblePalette(
                background: UIColor.white.withAlphaComponent(message.isOutgoing ? 0.46 : 0.22),
                previewBackground: accent.withAlphaComponent(message.isOutgoing ? 0.24 : 0.16),
                accent: accent
            )
        }
    }

    private static func bodyFont(for mode: BubbleMode) -> UIFont {
        switch mode {
        case .oversizedText:
            return .systemFont(ofSize: 30, weight: .semibold)
        case .money:
            return .systemFont(ofSize: 18, weight: .semibold)
        case .voice, .call:
            return .monospacedDigitSystemFont(ofSize: 14.5, weight: .medium)
        case .system:
            return .systemFont(ofSize: 13, weight: .medium)
        case .text:
            return .systemFont(ofSize: 14)
        default:
            return .systemFont(ofSize: 14)
        }
    }

    private static func cardHeight(for message: ChatMessage, mode: BubbleMode) -> CGFloat {
        if usesWechatMiniProgramShareStyle(message) {
            return 258
        }
        if message.type == .webLink {
            return 132
        }
        if message.type == .relay {
            return 132
        }
        if message.type == .groupNotice {
            return 138
        }
        switch mode {
        case .media:
            return 184
        case .channelVideo:
            return 286
        case .money where message.type == .redPacket:
            return 96
        case .document, .location, .profile, .link:
            return 176
        case .stacked, .notice:
            return 166
        default:
            return 0
        }
    }

    private static func usesWechatMiniProgramShareStyle(_ message: ChatMessage) -> Bool {
        message.type == .miniProgram && message.detail.contains(wechatMiniProgramShareStyleMarker)
    }

    private static func measuredHeight(
        _ text: String,
        font: UIFont,
        width: CGFloat,
        maxLines: Int,
        lineHeight: CGFloat? = nil
    ) -> CGFloat {
        guard !text.isEmpty else { return 0 }
        var attributes: [NSAttributedString.Key: Any] = [.font: font]
        if let lineHeight {
            let paragraphStyle = NSMutableParagraphStyle()
            paragraphStyle.minimumLineHeight = lineHeight
            paragraphStyle.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraphStyle
        }
        let height = (text as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: attributes,
            context: nil
        ).height.rounded(.up)
        guard maxLines > 0 else { return height }
        return min(height, (lineHeight ?? font.lineHeight) * CGFloat(maxLines))
    }

    private static func richElementsHeight(_ elements: [BubbleRichElement], width: CGFloat) -> CGFloat {
        guard !elements.isEmpty else { return 0 }
        var blockHeights: [CGFloat] = []
        var inlineText = ""
        var previousWasImage = false

        func flushInline() {
            guard !inlineText.isEmpty else { return }
            blockHeights.append(
                measuredHeight(
                    inlineText,
                    font: bodyFont(for: .text),
                    width: width,
                    maxLines: 0,
                    lineHeight: 16.8
                )
            )
            inlineText = ""
        }

        for element in elements {
            switch element {
            case .text(let text):
                previousWasImage = false
                inlineText += text
            case .blueLink(let label, let url, let bracketed):
                previousWasImage = false
                inlineText += bracketed ? "[\(label)] \(url)" : label
            case .taggedFile(let label, let url, let prefix):
                previousWasImage = false
                inlineText += "\(prefix)\(label) \(url)"
            case .mention(let name):
                previousWasImage = false
                inlineText += "@\(name)"
            case .aiToken(let text):
                previousWasImage = false
                inlineText += "✦\(text)"
            case .image(_, _, let aspect, _):
                flushInline()
                if previousWasImage {
                    blockHeights.append(8)
                }
                blockHeights.append(estimatedRichImagePreviewHeight(aspect: aspect))
                previousWasImage = true
            case .inlineCard:
                flushInline()
                previousWasImage = false
                blockHeights.append(96)
            case .location:
                flushInline()
                previousWasImage = false
                blockHeights.append(62)
            case .quote:
                flushInline()
                previousWasImage = false
                blockHeights.append(52)
            case .file:
                flushInline()
                previousWasImage = false
                blockHeights.append(138)
            }
        }
        flushInline()
        return blockHeights.reduce(0, +) + CGFloat(max(0, blockHeights.count - 1)) * 7
    }

    private static func estimatedInlineCardHeight(
        title: String,
        subtitle: String,
        width: CGFloat
    ) -> CGFloat {
        let labelWidth = max(1, width - 96)
        let titleHeight = measuredHeight(
            title,
            font: .systemFont(ofSize: 14, weight: .semibold),
            width: labelWidth,
            maxLines: 2
        )
        let subtitleHeight = measuredHeight(
            subtitle,
            font: .systemFont(ofSize: 11.5, weight: .regular),
            width: labelWidth,
            maxLines: 2
        )
        let topAreaHeight = max(
            68,
            9 + 18 + 5 + titleHeight + 2 + subtitleHeight + 9
        )
        return max(96, topAreaHeight + 1 + 8 + 12 + 8)
    }

    private func richTextAttributes() -> [NSAttributedString.Key: Any] {
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.minimumLineHeight = 16.8
        paragraphStyle.maximumLineHeight = 16.8
        return [
            .font: Self.bodyFont(for: .text),
            .foregroundColor: rendersAIMessage ? Self.aiTextColor : UIColor.white,
            .paragraphStyle: paragraphStyle
        ]
    }

    private func linkAttributes() -> [NSAttributedString.Key: Any] {
        [
            .font: UIFont.systemFont(ofSize: 14, weight: .semibold),
            .foregroundColor: rendersAIMessage ? Self.aiTextColor : UIColor.systemBlue,
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]
    }

    private func smallLinkAttributes() -> [NSAttributedString.Key: Any] {
        [
            .font: UIFont.systemFont(ofSize: 10.5, weight: .medium),
            .foregroundColor: rendersAIMessage ? Self.aiTextColor.withAlphaComponent(0.78) : UIColor.systemBlue.withAlphaComponent(0.78),
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]
    }

    private func aiTokenAttributes() -> [NSAttributedString.Key: Any] {
        [
            .font: UIFont.systemFont(ofSize: 14.5, weight: .bold),
            .foregroundColor: UIColor(red: 0.86, green: 0.58, blue: 0.12, alpha: 1)
        ]
    }

    private static let textColor = UIColor(red: 0.03, green: 0.27, blue: 0.34, alpha: 1)
    private static let detailColor = UIColor(red: 0.12, green: 0.31, blue: 0.36, alpha: 0.78)
    private static let mutedColor = UIColor(red: 0.14, green: 0.30, blue: 0.35, alpha: 0.58)
    private static let aiTextColor = UIColor(red: 0.95, green: 0.68, blue: 0.18, alpha: 1)
}

private struct BubblePalette {
    let background: UIColor
    let previewBackground: UIColor
    let accent: UIColor
}

final class ChatMessageCell: UICollectionViewCell {
    static let reuseIdentifier = "ChatMessageCell"
    static let reuseIdentifiers = MessageRendererGroup.allCases.map {
        "\(reuseIdentifier).\($0.rawValue)"
    }

    static func reuseIdentifier(for messageType: ChatMessageType) -> String {
        "\(reuseIdentifier).\(MessageRendererRegistry.default.group(for: messageType).rawValue)"
    }

    var onConnectionGeometryDidChange: (() -> Void)?
    var onUnansweredFoldTap: (() -> Void)?

    private let bubbleView = ChatMessageBubbleView()
    private let selectionBadge = UILabel()
    private let recipientWatermarkView = SidebarAvatarView()
    private let recipientColorDot = UIView()
    private let unansweredFoldButton = UIButton(type: .system)
    private let recipientWatermarkMask = CAGradientLayer()
    private var mountedBubbleView: ChatMessageBubbleView!
    private var activeRenderer: MessageRendering?
    private var activeRendererReuseIdentifier: MessageRendererRegistry.ReuseIdentifier?
    private var bubbleConstraints: [NSLayoutConstraint] = []
    private var bubbleLeadingConstraint: NSLayoutConstraint?
    private var bubbleTopConstraint: NSLayoutConstraint?
    private var bubbleBottomConstraint: NSLayoutConstraint?
    private var isInSelectionMode = false
    private var configuredCellMessageID: UUID?
    private var bubbleConfigurationKey: BubbleConfigurationKey?
    private var cachedConnectionBubbleFrame: CGRect?
    private var cachedOuterBubbleFrame: CGRect?
    private var cachedGroupMemberAvatarFrame: CGRect?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    override var isHighlighted: Bool {
        didSet {
            guard !isInSelectionMode else {
                mountedBubbleView.transform = .identity
                mountedBubbleView.alpha = 1
                return
            }
            mountedBubbleView.transform = isHighlighted ? CGAffineTransform(scaleX: 0.985, y: 0.985) : .identity
            mountedBubbleView.alpha = isHighlighted ? 0.84 : 1
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        // Componentized renderers are mounted during configure. Settle their
        // geometry before the connection overlay asks this cell for an anchor.
        contentView.layoutIfNeeded()
        mountedBubbleView.layoutIfNeeded()
        refreshConnectionGeometry(for: mountedBubbleView)
        layoutUnansweredFoldButton()
        guard !recipientColorDot.isHidden || !recipientWatermarkView.isHidden else { return }
        // Recipient watermarks belong to the outer message bubble. The
        // connection frame may intentionally target an inner preview/card.
        guard let measuredFrame = cachedOuterBubbleFrame else { return }
        guard measuredFrame.width > 1, measuredFrame.height > 1 else { return }
        let bubbleFrame = measuredFrame.intersection(contentView.bounds.insetBy(dx: -1, dy: -1))
        guard !bubbleFrame.isNull, bubbleFrame.width > 1, bubbleFrame.height > 1 else { return }
        let scale = window?.screen.scale ?? UIScreen.main.scale
        let align: (CGFloat) -> CGFloat = { ($0 * scale).rounded() / scale }
        let watermarkSize = min(CGFloat(28), max(20, bubbleFrame.height - 8))
        if !recipientWatermarkView.isHidden {
            recipientWatermarkView.frame = CGRect(
                x: align(bubbleFrame.maxX - watermarkSize - 6),
                y: align(bubbleFrame.maxY - watermarkSize - 5),
                width: watermarkSize,
                height: watermarkSize
            )
            recipientWatermarkView.layer.cornerRadius = watermarkSize / 2
            recipientWatermarkMask.frame = recipientWatermarkView.bounds
        }
        recipientColorDot.frame = CGRect(
            x: align(contentView.bounds.maxX - 40),
            y: align(contentView.bounds.midY - 4.5),
            width: 9,
            height: 9
        )
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        onConnectionGeometryDidChange = nil
        onUnansweredFoldTap = nil
        isInSelectionMode = false
        configuredCellMessageID = nil
        bubbleConfigurationKey = nil
        if let activeRenderer {
            activeRenderer.resetForReuse()
        } else {
            bubbleView.reset()
        }
        contentView.transform = .identity
        contentView.alpha = 1
        mountedBubbleView.transform = .identity
        mountedBubbleView.alpha = 1
        bubbleLeadingConstraint?.constant = 0
        bubbleTopConstraint?.constant = 0
        bubbleBottomConstraint?.constant = 0
        selectionBadge.isHidden = true
        selectionBadge.text = nil
        recipientWatermarkView.prepareForReuse()
        recipientWatermarkView.isHidden = true
        recipientColorDot.isHidden = true
        unansweredFoldButton.isHidden = true
        var clearedFoldConfiguration = unansweredFoldButton.configuration
        clearedFoldConfiguration?.title = nil
        unansweredFoldButton.configuration = clearedFoldConfiguration
        cachedConnectionBubbleFrame = nil
        cachedOuterBubbleFrame = nil
        cachedGroupMemberAvatarFrame = nil
        setNeedsLayout()
    }

    func configure(
        with message: ChatMessage,
        isVoicePlaying: Bool = false,
        isAIStreaming: Bool = false,
        screenshotMediaBlurEnabled: Bool = false,
        mediaPrivacyBlurEnabled: Bool = false,
        uses3DBubbleAppearance: Bool = false,
        deliveryStatusText: String? = nil,
        isSelecting: Bool = false,
        isSelected: Bool = false,
        showsGroupAvatar: Bool = true,
        showsGroupName: Bool = true,
        groupTopSpacing: CGFloat = 0,
        groupBottomPadding: CGFloat = 0,
        isUnansweredSelected: Bool = false,
        unansweredRecipientItem: SidebarItem? = nil,
        unansweredRecipientItems: [SidebarItem] = [],
        usesUnansweredPresentation: Bool = false,
        outgoingGroupAccountDotColor: UIColor? = nil,
        usesOutgoingGroupAccountPresentation: Bool = false,
        unansweredFoldTitle: String? = nil
    ) {
        let signpostID = PerformanceSignpost.begin("ChatMessageCellConfigure")
        defer { PerformanceSignpost.end("ChatMessageCellConfigure", id: signpostID) }
        configuredCellMessageID = message.id
        let resolvedRecipientItems = unansweredRecipientItems.isEmpty
            ? unansweredRecipientItem.map { [$0] } ?? []
            : unansweredRecipientItems
        let primaryRecipientItem = resolvedRecipientItems.first
        isInSelectionMode = isSelecting
        let renderState = MessageRenderState(
            isVoicePlaying: isVoicePlaying,
            isAIStreaming: isAIStreaming,
            screenshotMediaBlurEnabled: screenshotMediaBlurEnabled,
            mediaPrivacyBlurEnabled: mediaPrivacyBlurEnabled,
            uses3DAppearance: uses3DBubbleAppearance,
            usesUnansweredPresentation: usesUnansweredPresentation,
            deliveryStatusText: deliveryStatusText,
            selection: MessageSelectionState(isSelecting: isSelecting, isSelected: isSelected),
            groupDisplay: MessageGroupDisplayState(showsAvatar: showsGroupAvatar, showsName: showsGroupName)
        )
        let registry = MessageRendererRegistry.default
        let rendererReuseIdentifier = registry.reuseIdentifier(for: message.type)
        let nextBubbleConfigurationKey = BubbleConfigurationKey(
            messageHash: message.layoutContentHash,
            rendererReuseIdentifier: rendererReuseIdentifier,
            isVoicePlaying: isVoicePlaying,
            isAIStreaming: isAIStreaming,
            screenshotMediaBlurEnabled: screenshotMediaBlurEnabled,
            mediaPrivacyBlurEnabled: mediaPrivacyBlurEnabled,
            uses3DBubbleAppearance: uses3DBubbleAppearance,
            deliveryStatusText: deliveryStatusText,
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation,
            recipientDescriptors: resolvedRecipientItems.map {
                [
                    $0.id.uuidString,
                    $0.title,
                    $0.avatarURL?.absoluteString ?? "",
                    $0.compositeAvatarTitles.joined(separator: ",")
                ].joined(separator: "|")
            }
        )
        let canReuseRenderedBubble = bubbleConfigurationKey == nextBubbleConfigurationKey
        if registry.version(for: message.type) == .componentized {
            let renderer: MessageRendering?
            if let activeRenderer,
               activeRendererReuseIdentifier == rendererReuseIdentifier {
                renderer = activeRenderer
            } else {
                activeRenderer?.resetForReuse()
                let newRenderer = registry.renderer(for: message.type)
                if let rendererBubbleView = newRenderer.view as? ChatMessageBubbleView {
                    activeRenderer = newRenderer
                    activeRendererReuseIdentifier = rendererReuseIdentifier
                    mountBubbleView(rendererBubbleView)
                    renderer = newRenderer
                } else {
                    assertionFailure("Componentized renderer must provide ChatMessageBubbleView during compatibility migration")
                    activeRenderer = nil
                    activeRendererReuseIdentifier = nil
                    mountBubbleView(bubbleView)
                    renderer = nil
                }
            }
            if let renderer, !canReuseRenderedBubble {
                renderer.render(model: MessageRenderModel(source: message), state: renderState, style: .legacy)
            } else if renderer == nil, !canReuseRenderedBubble {
                bubbleView.configure(
                    with: message,
                    isVoicePlaying: isVoicePlaying,
                    isAIStreaming: isAIStreaming,
                    screenshotMediaBlurEnabled: screenshotMediaBlurEnabled,
                    mediaPrivacyBlurEnabled: mediaPrivacyBlurEnabled,
                    uses3DBubbleAppearance: uses3DBubbleAppearance,
                    deliveryStatusText: deliveryStatusText,
                    showsGroupAvatar: showsGroupAvatar,
                    showsGroupName: showsGroupName,
                    usesUnansweredPresentation: usesUnansweredPresentation,
                    unansweredRecipientColor: primaryRecipientItem?.tintColor,
                    unansweredRecipientItem: primaryRecipientItem,
                    unansweredRecipientItems: resolvedRecipientItems
                )
            }
        } else {
            activeRenderer?.resetForReuse()
            activeRenderer = nil
            activeRendererReuseIdentifier = nil
            mountBubbleView(bubbleView)
            if !canReuseRenderedBubble {
                bubbleView.configure(
                    with: message,
                    isVoicePlaying: isVoicePlaying,
                    isAIStreaming: isAIStreaming,
                    screenshotMediaBlurEnabled: screenshotMediaBlurEnabled,
                    mediaPrivacyBlurEnabled: mediaPrivacyBlurEnabled,
                    uses3DBubbleAppearance: uses3DBubbleAppearance,
                    deliveryStatusText: deliveryStatusText,
                    showsGroupAvatar: showsGroupAvatar,
                    showsGroupName: showsGroupName,
                    usesUnansweredPresentation: usesUnansweredPresentation,
                    unansweredRecipientColor: primaryRecipientItem?.tintColor,
                    unansweredRecipientItem: primaryRecipientItem,
                    unansweredRecipientItems: resolvedRecipientItems
                )
            }
        }
        bubbleConfigurationKey = nextBubbleConfigurationKey
        mountedBubbleView.setUnansweredRecipientItems(resolvedRecipientItems)
        mountedBubbleView.setUnansweredRecipientColor(
            outgoingGroupAccountDotColor ?? primaryRecipientItem?.tintColor
        )
        mountedBubbleView.setOutgoingGroupAccountPresentation(
            usesOutgoingGroupAccountPresentation
        )
        mountedBubbleView.setMinimumBubbleWidth(
            usesUnansweredPresentation ? 96 : 0
        )
        bubbleLeadingConstraint?.constant = isSelecting ? 30 : 0
        bubbleTopConstraint?.constant = groupTopSpacing
        bubbleBottomConstraint?.constant = -max(0, groupBottomPadding)
        selectionBadge.isHidden = !isSelecting || message.type == .system
        selectionBadge.text = isSelected ? "✓" : ""
        selectionBadge.backgroundColor = isSelected ? UIColor.systemGreen : UIColor.white.withAlphaComponent(0.96)
        selectionBadge.layer.borderColor = (isSelected ? UIColor.systemGreen : UIColor.systemGray3).cgColor
        contentView.layer.borderWidth = 0
        contentView.layer.borderColor = UIColor.clear.cgColor
        contentView.backgroundColor = isUnansweredSelected
            ? UIColor.white.withAlphaComponent(0.055)
            : .clear
        let showsUnansweredFoldButton = unansweredFoldTitle != nil
        unansweredFoldButton.isHidden = !showsUnansweredFoldButton
        unansweredFoldButton.alpha = 1
        unansweredFoldButton.isUserInteractionEnabled = showsUnansweredFoldButton
        var foldConfiguration = unansweredFoldButton.configuration
        foldConfiguration?.title = unansweredFoldTitle
        foldConfiguration?.image = UIImage(systemName: "chevron.down")
        foldConfiguration?.showsActivityIndicator = false
        unansweredFoldButton.configuration = foldConfiguration
        unansweredFoldButton.accessibilityLabel = unansweredFoldTitle
        // The watermark is rendered inside ChatMessageBubbleView. Keep the
        // legacy cell-level overlay disabled so reuse/touch layout cannot move
        // a second avatar to the cell's top edge.
        recipientWatermarkView.isHidden = true
        recipientColorDot.isHidden = true
        cachedConnectionBubbleFrame = nil
        cachedOuterBubbleFrame = nil
        cachedGroupMemberAvatarFrame = nil
        setNeedsLayout()
    }

    private struct BubbleConfigurationKey: Equatable {
        let messageHash: Int
        let rendererReuseIdentifier: ObjectIdentifier
        let isVoicePlaying: Bool
        let isAIStreaming: Bool
        let screenshotMediaBlurEnabled: Bool
        let mediaPrivacyBlurEnabled: Bool
        let uses3DBubbleAppearance: Bool
        let deliveryStatusText: String?
        let showsGroupAvatar: Bool
        let showsGroupName: Bool
        let usesUnansweredPresentation: Bool
        let recipientDescriptors: [String]
    }

    func detectedLink(at point: CGPoint) -> String? {
        mountedBubbleView.detectedLink(at: convert(point, to: mountedBubbleView))
    }

    func detectedInlineCard(at point: CGPoint) -> InlineCardInteraction? {
        mountedBubbleView.detectedInlineCard(at: convert(point, to: mountedBubbleView))
    }

    func detectedRichMedia(at point: CGPoint) -> RichMediaInteraction? {
        mountedBubbleView.detectedRichMedia(at: convert(point, to: mountedBubbleView))
    }

    func cachedConnectionFrame(
        in view: UIView,
        for messageID: UUID,
        usesOuterBubble: Bool
    ) -> CGRect? {
        guard configuredCellMessageID == messageID else { return nil }
        let frame = usesOuterBubble ? cachedOuterBubbleFrame : cachedConnectionBubbleFrame
        guard let frame,
              frame.origin.x.isFinite,
              frame.origin.y.isFinite,
              frame.width.isFinite,
              frame.height.isFinite,
              frame.width > 1,
              frame.height > 1
        else { return nil }
        return contentView.convert(frame, to: view)
    }

    func cachedGroupMemberAvatarConnectionPoint(
        in view: UIView,
        for messageID: UUID
    ) -> CGPoint? {
        guard configuredCellMessageID == messageID,
              let frame = cachedGroupMemberAvatarFrame,
              frame.origin.x.isFinite,
              frame.origin.y.isFinite,
              frame.width > 1,
              frame.height > 1
        else { return nil }
        return contentView.convert(
            CGPoint(x: frame.minX, y: frame.midY),
            to: view
        )
    }

    private static func framesMatch(_ lhs: CGRect?, _ rhs: CGRect, tolerance: CGFloat = 0.25) -> Bool {
        guard let lhs else { return false }
        return abs(lhs.minX - rhs.minX) <= tolerance
            && abs(lhs.minY - rhs.minY) <= tolerance
            && abs(lhs.width - rhs.width) <= tolerance
            && abs(lhs.height - rhs.height) <= tolerance
    }

    private static func optionalFramesMatch(
        _ lhs: CGRect?,
        _ rhs: CGRect?,
        tolerance: CGFloat = 0.25
    ) -> Bool {
        switch (lhs, rhs) {
        case (nil, nil):
            return true
        case let (lhs?, rhs?):
            return framesMatch(lhs, rhs, tolerance: tolerance)
        default:
            return false
        }
    }

    private func setup() {
        contentView.backgroundColor = .clear
        clipsToBounds = false
        contentView.clipsToBounds = false
        bubbleView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(bubbleView)
        mountedBubbleView = bubbleView
        observeConnectionGeometry(of: bubbleView)
        selectionBadge.textAlignment = .center
        selectionBadge.textColor = .white
        selectionBadge.font = .systemFont(ofSize: 13, weight: .bold)
        selectionBadge.layer.cornerRadius = 12
        selectionBadge.layer.borderWidth = 1
        selectionBadge.layer.masksToBounds = true
        selectionBadge.isHidden = true
        selectionBadge.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(selectionBadge)
        recipientWatermarkView.isUserInteractionEnabled = false
        recipientWatermarkView.layer.cornerRadius = 18.5
        recipientWatermarkView.layer.masksToBounds = true
        recipientWatermarkMask.startPoint = CGPoint(x: 0, y: 0)
        recipientWatermarkMask.endPoint = CGPoint(x: 1, y: 1)
        recipientWatermarkMask.locations = [0, 0.42, 1]
        recipientWatermarkMask.colors = [
            UIColor.clear.cgColor,
            UIColor.white.withAlphaComponent(0.42).cgColor,
            UIColor.white.cgColor
        ]
        recipientWatermarkView.layer.mask = recipientWatermarkMask
        contentView.addSubview(recipientWatermarkView)
        recipientColorDot.isUserInteractionEnabled = false
        recipientColorDot.layer.cornerRadius = 4.5
        recipientColorDot.layer.borderWidth = 1.5
        recipientColorDot.layer.borderColor = UIColor.white.withAlphaComponent(0.85).cgColor
        recipientColorDot.layer.shadowRadius = 3
        recipientColorDot.layer.shadowOffset = .zero
        contentView.addSubview(recipientColorDot)
        var foldConfiguration = UIButton.Configuration.plain()
        foldConfiguration.image = UIImage(systemName: "chevron.down")
        foldConfiguration.imagePlacement = .trailing
        foldConfiguration.imagePadding = 5
        foldConfiguration.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(
            pointSize: 9,
            weight: .semibold
        )
        foldConfiguration.baseForegroundColor = UIColor.white.withAlphaComponent(0.9)
        foldConfiguration.contentInsets = NSDirectionalEdgeInsets(
            top: 2,
            leading: 10,
            bottom: 2,
            trailing: 10
        )
        foldConfiguration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var resolved = attributes
            resolved.font = .systemFont(ofSize: 11, weight: .semibold)
            return resolved
        }
        unansweredFoldButton.configuration = foldConfiguration
        unansweredFoldButton.titleLabel?.numberOfLines = 1
        unansweredFoldButton.titleLabel?.lineBreakMode = .byTruncatingTail
        unansweredFoldButton.titleLabel?.adjustsFontSizeToFitWidth = true
        unansweredFoldButton.titleLabel?.minimumScaleFactor = 0.85
        unansweredFoldButton.titleLabel?.setContentCompressionResistancePriority(.required, for: .horizontal)
        unansweredFoldButton.backgroundColor = UIColor.white.withAlphaComponent(0.09)
        unansweredFoldButton.layer.cornerRadius = 12
        unansweredFoldButton.layer.borderWidth = 0.5
        unansweredFoldButton.layer.borderColor = UIColor.white.withAlphaComponent(0.14).cgColor
        unansweredFoldButton.layer.shadowOpacity = 0
        unansweredFoldButton.isHidden = true
        unansweredFoldButton.addTarget(
            self,
            action: #selector(handleUnansweredFoldTap),
            for: .touchUpInside
        )
        contentView.addSubview(unansweredFoldButton)
        activateBubbleConstraints(for: bubbleView)
        NSLayoutConstraint.activate([
            selectionBadge.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 5),
            selectionBadge.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            selectionBadge.widthAnchor.constraint(equalToConstant: 24),
            selectionBadge.heightAnchor.constraint(equalToConstant: 24)
        ])
    }

    private func layoutUnansweredFoldButton() {
        guard !unansweredFoldButton.isHidden,
              let bubbleFrame = cachedOuterBubbleFrame ?? cachedConnectionBubbleFrame,
              bubbleFrame.width > 1,
              bubbleFrame.height > 1
        else { return }
        let height: CGFloat = 24
        let fittingWidth = unansweredFoldButton.sizeThatFits(
            CGSize(width: contentView.bounds.width, height: height)
        ).width
        let width = min(max(116, ceil(fittingWidth)), contentView.bounds.width - 16)
        let centerX = min(
            max(bubbleFrame.midX, width / 2 + 8),
            contentView.bounds.width - width / 2 - 8
        )
        unansweredFoldButton.frame = CGRect(
            x: centerX - width / 2,
            y: bubbleFrame.maxY + 4,
            width: width,
            height: height
        ).integral
        contentView.bringSubviewToFront(unansweredFoldButton)
    }

    @objc private func handleUnansweredFoldTap() {
        guard unansweredFoldButton.isUserInteractionEnabled else { return }
        unansweredFoldButton.isUserInteractionEnabled = false
        var configuration = unansweredFoldButton.configuration
        configuration?.title = "加载中"
        configuration?.image = nil
        configuration?.showsActivityIndicator = true
        unansweredFoldButton.configuration = configuration
        let action = onUnansweredFoldTap
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) { [weak self] in
            self?.hideUnansweredFoldControl()
            action?()
        }
    }

    func hideUnansweredFoldControl() {
        unansweredFoldButton.isHidden = true
        unansweredFoldButton.alpha = 0
        unansweredFoldButton.isUserInteractionEnabled = false
    }

    func prepareForUnansweredFoldExpansionTransition() {
        hideUnansweredFoldControl()
        bubbleBottomConstraint?.constant = 0
        setNeedsLayout()
    }

    private func mountBubbleView(_ nextBubbleView: ChatMessageBubbleView) {
        guard mountedBubbleView !== nextBubbleView else { return }
        cachedConnectionBubbleFrame = nil
        cachedOuterBubbleFrame = nil
        cachedGroupMemberAvatarFrame = nil
        NSLayoutConstraint.deactivate(bubbleConstraints)
        mountedBubbleView.onConnectionGeometryDidChange = nil
        mountedBubbleView.removeFromSuperview()
        mountedBubbleView = nextBubbleView
        observeConnectionGeometry(of: nextBubbleView)
        nextBubbleView.translatesAutoresizingMaskIntoConstraints = false
        contentView.insertSubview(nextBubbleView, belowSubview: selectionBadge)
        activateBubbleConstraints(for: nextBubbleView)
    }

    private func activateBubbleConstraints(for target: ChatMessageBubbleView) {
        let leading = target.leadingAnchor.constraint(equalTo: contentView.leadingAnchor)
        let top = target.topAnchor.constraint(equalTo: contentView.topAnchor)
        let bottom = target.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        bubbleLeadingConstraint = leading
        bubbleTopConstraint = top
        bubbleBottomConstraint = bottom
        bubbleConstraints = [
            top,
            leading,
            target.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            bottom
        ]
        NSLayoutConstraint.activate(bubbleConstraints)
    }

    private func observeConnectionGeometry(of bubbleView: ChatMessageBubbleView) {
        bubbleView.onConnectionGeometryDidChange = { [weak self, weak bubbleView] in
            guard let self, let bubbleView, self.mountedBubbleView === bubbleView else { return }
            self.refreshConnectionGeometry(for: bubbleView)
        }
    }

    private func refreshConnectionGeometry(for bubbleView: ChatMessageBubbleView) {
        guard mountedBubbleView === bubbleView else { return }
        let connectionFrame = bubbleView.connectionBubbleFrame(in: contentView)
        let outerFrame = bubbleView.recipientWatermarkBubbleFrame(in: contentView)
        let groupMemberAvatarFrame = bubbleView.groupMemberAvatarFrame(in: contentView)
        let geometryChanged = !Self.framesMatch(cachedConnectionBubbleFrame, connectionFrame)
            || !Self.framesMatch(cachedOuterBubbleFrame, outerFrame)
            || !Self.optionalFramesMatch(cachedGroupMemberAvatarFrame, groupMemberAvatarFrame)
        cachedConnectionBubbleFrame = connectionFrame
        cachedOuterBubbleFrame = outerFrame
        cachedGroupMemberAvatarFrame = groupMemberAvatarFrame
        if geometryChanged {
            onConnectionGeometryDidChange?()
        }
    }
}

final class InputBarView: UIView {
    let textField = UITextField()
    let voiceButton = UIButton(type: .system)
    let giftButton = UIButton(type: .system)
    let moreButton = UIButton(type: .system)
    let minimizeButton = UIButton(type: .system)

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor = UIColor(white: 0.94, alpha: 0.96)
        layer.borderWidth = 0

        let stack = UIStackView(arrangedSubviews: [giftButton, voiceButton, textField, minimizeButton, moreButton])
        stack.axis = .horizontal
        stack.spacing = 6
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)

        [voiceButton, giftButton, moreButton, minimizeButton].forEach { button in
            button.tintColor = UIColor(red: 0.12, green: 0.14, blue: 0.15, alpha: 1)
            button.backgroundColor = .clear
            button.layer.cornerRadius = 0
            button.layer.borderWidth = 0
            button.widthAnchor.constraint(equalToConstant: 32).isActive = true
            button.heightAnchor.constraint(equalToConstant: 44).isActive = true
            button.imageView?.contentMode = .scaleAspectFit
        }
        voiceButton.setImage(UIImage(systemName: "mic"), for: .normal)
        giftButton.setImage(UIImage(systemName: "face.smiling"), for: .normal)
        moreButton.setImage(.chatRobotIcon(size: CGSize(width: 25, height: 25)), for: .normal)
        moreButton.tintColor = nil
        minimizeButton.setImage(UIImage(systemName: "gift"), for: .normal)
        giftButton.accessibilityLabel = "\u{8868}\u{60c5}\u{5305}"
        voiceButton.accessibilityLabel = "\u{8bed}\u{97f3}"
        minimizeButton.accessibilityLabel = "\u{793c}\u{7269}"
        moreButton.accessibilityLabel = "AI"

        textField.placeholder = "\u{8f93}\u{5165}\u{6d88}\u{606f}..."
        textField.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.13, alpha: 1)
        textField.font = .systemFont(ofSize: 16)
        textField.returnKeyType = .send
        textField.enablesReturnKeyAutomatically = true
        textField.backgroundColor = .white
        textField.layer.cornerRadius = 12
        textField.layer.cornerCurve = .continuous
        textField.layer.borderWidth = 1
        textField.layer.borderColor = UIColor.black.withAlphaComponent(0.06).cgColor
        textField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 1))
        textField.leftViewMode = .always
        textField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        textField.setValue(UIColor(red: 0.54, green: 0.57, blue: 0.60, alpha: 1), forKeyPath: "placeholderLabel.textColor")

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6)
        ])
    }
}
