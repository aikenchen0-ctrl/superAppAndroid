import UIKit
@preconcurrency import AVFoundation
import AVKit
import CoreImage
import Photos
import Vision
import WebKit

final class InlineFilePreviewViewController: UIViewController {
    private static let inlineExtensions: Set<String> = [
        "txt", "md", "markdown", "csv", "json", "xml", "yaml", "yml", "log", "html", "htm"
    ]

    private let url: URL
    private let textView = UITextView()
    private var webView: WKWebView?

    static func canPreview(_ url: URL) -> Bool {
        inlineExtensions.contains(url.pathExtension.lowercased())
    }

    init(url: URL) {
        self.url = url
        super.init(nibName: nil, bundle: nil)
        title = url.lastPathComponent.isEmpty ? "文件预览" : url.lastPathComponent
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        if isHTML {
            configureWebPreview()
        } else {
            configureTextPreview()
        }
    }

    private var isHTML: Bool {
        ["html", "htm"].contains(url.pathExtension.lowercased())
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    private func configureWebPreview() {
        let webView = WKWebView(frame: .zero)
        webView.backgroundColor = .systemBackground
        webView.isOpaque = false
        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)
        self.webView = webView
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        if url.isFileURL {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        } else {
            webView.load(URLRequest(url: url))
        }
    }

    private func configureTextPreview() {
        textView.isEditable = false
        textView.alwaysBounceVertical = true
        textView.backgroundColor = .systemBackground
        textView.textColor = .label
        textView.font = .monospacedSystemFont(ofSize: 13.5, weight: .regular)
        textView.textContainerInset = UIEdgeInsets(top: 16, left: 14, bottom: 24, right: 14)
        textView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(textView)
        NSLayoutConstraint.activate([
            textView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            textView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            textView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            textView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        loadText()
    }

    private func loadText() {
        textView.text = "正在加载..."
        Task { [weak self] in
            guard let self else { return }
            do {
                let data: Data
                if self.url.isFileURL {
                    let didAccess = self.url.startAccessingSecurityScopedResource()
                    defer {
                        if didAccess {
                            self.url.stopAccessingSecurityScopedResource()
                        }
                    }
                    data = try Data(contentsOf: self.url, options: [.mappedIfSafe])
                } else {
                    let (remoteData, _) = try await URLSession.shared.data(from: self.url)
                    data = remoteData
                }
                let text = self.previewText(from: data)
                await MainActor.run {
                    self.textView.text = text
                }
            } catch {
                await MainActor.run {
                    self.textView.text = "文件无法预览：\(error.localizedDescription)"
                }
            }
        }
    }

    private func previewText(from data: Data) -> String {
        if data.count > 5 * 1024 * 1024 {
            return "文件超过 5MB，已停止内置文本预览。可使用系统文件预览或发送后在微信端查看。"
        }
        if let text = String(data: data, encoding: .utf8) {
            return formattedText(text)
        }
        if let text = String(data: data, encoding: .unicode) {
            return formattedText(text)
        }
        if let text = String(data: data, encoding: .isoLatin1) {
            return formattedText(text)
        }
        return "当前文件不是可识别文本编码，无法内置预览。"
    }

    private func formattedText(_ text: String) -> String {
        guard url.pathExtension.lowercased() == "json",
              let data = text.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let formatted = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]),
              let pretty = String(data: formatted, encoding: .utf8)
        else {
            return text
        }
        return pretty
    }
}

struct ChatMediaPreviewItem {
    let id: UUID
    let title: String
    let url: URL?
    let image: UIImage?
    let isVideo: Bool
    let accessScope: BubbleAccessScope
    let accessState: MediaAccessState
    let sourceURLText: String

    var accessKey: String {
        sourceURLText
    }

    var kindTitle: String {
        isVideo ? "视频" : "图片"
    }
}

struct MediaDragPayload {
    let title: String
    let url: URL?
    let image: UIImage?
    let isVideo: Bool

    var kindTitle: String {
        isVideo ? "视频" : "图片"
    }
}

private enum BackgroundRemovalError: LocalizedError {
    case invalidImage
    case noForeground
    case invalidForegroundExtent
    case renderFailed

    var errorDescription: String? {
        switch self {
        case .invalidImage:
            return "图片无法读取。"
        case .noForeground:
            return "没有识别到可扣出的前景主体。"
        case .invalidForegroundExtent:
            return "识别结果尺寸异常，已保留原图。"
        case .renderFailed:
            return "透明背景图片生成失败。"
        }
    }
}

@available(iOS 17.0, *)
enum BackgroundRemovalProcessor {
    static func removeBackground(from image: UIImage) throws -> UIImage {
        guard let cgImage = image.normalizedForVision().cgImage else {
            throw BackgroundRemovalError.invalidImage
        }

        let request = VNGenerateForegroundInstanceMaskRequest()
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try handler.perform([request])

        guard let observation = request.results?.first,
              !observation.allInstances.isEmpty
        else {
            throw BackgroundRemovalError.noForeground
        }

        let outputBuffer = try observation.generateMaskedImage(
            ofInstances: observation.allInstances,
            from: handler,
            croppedToInstancesExtent: true
        )
        let ciImage = CIImage(cvPixelBuffer: outputBuffer)
        let context = CIContext(options: nil)
        let rect = CGRect(
            x: 0,
            y: 0,
            width: CVPixelBufferGetWidth(outputBuffer),
            height: CVPixelBufferGetHeight(outputBuffer)
        )
        guard let outputCGImage = context.createCGImage(ciImage, from: rect) else {
            throw BackgroundRemovalError.renderFailed
        }
        let output = UIImage(cgImage: outputCGImage, scale: 1, orientation: .up)
        let aspectRatio = output.size.width / max(output.size.height, 1)
        guard aspectRatio.isFinite, aspectRatio >= 0.15, aspectRatio <= 6.5 else {
            throw BackgroundRemovalError.invalidForegroundExtent
        }
        guard output.hasMeaningfulTransparency() else {
            throw BackgroundRemovalError.noForeground
        }
        return output
    }
}

extension UIImage {
    func normalizedForVision() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }

    func hasMeaningfulTransparency() -> Bool {
        guard let analysis = sampledAlphaAnalysis(maxDimension: 64) else { return false }
        return analysis.transparentPixelCount >= max(4, analysis.pixelCount / 200)
    }

    // Protect old transparent PNGs produced before the foreground crop fix.
    // This only trims large transparent margins and leaves normal photos unchanged.
    func trimmedTransparentCanvasIfNeeded() -> UIImage {
        guard let cgImage,
              cgImage.alphaInfo != .none,
              cgImage.alphaInfo != .noneSkipFirst,
              cgImage.alphaInfo != .noneSkipLast,
              let analysis = sampledAlphaAnalysis(maxDimension: 128),
              analysis.visibleBounds.width > 0,
              analysis.visibleBounds.height > 0
        else { return self }

        let sampleArea = CGFloat(analysis.sampleWidth * analysis.sampleHeight)
        let visibleArea = analysis.visibleBounds.width * analysis.visibleBounds.height
        guard visibleArea / sampleArea < 0.78 else { return self }

        let padding = max(2, Int(CGFloat(max(analysis.sampleWidth, analysis.sampleHeight)) * 0.025))
        let paddedBounds = analysis.visibleBounds
            .insetBy(dx: -CGFloat(padding), dy: -CGFloat(padding))
            .intersection(CGRect(x: 0, y: 0, width: analysis.sampleWidth, height: analysis.sampleHeight))
        guard !paddedBounds.isEmpty else { return self }

        let scaleX = CGFloat(cgImage.width) / CGFloat(analysis.sampleWidth)
        let scaleY = CGFloat(cgImage.height) / CGFloat(analysis.sampleHeight)
        let cropRect = CGRect(
            x: floor(paddedBounds.minX * scaleX),
            y: floor(paddedBounds.minY * scaleY),
            width: ceil(paddedBounds.width * scaleX),
            height: ceil(paddedBounds.height * scaleY)
        ).intersection(CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height)).integral
        guard cropRect.width >= 2,
              cropRect.height >= 2,
              let cropped = cgImage.cropping(to: cropRect)
        else { return self }
        return UIImage(cgImage: cropped, scale: scale, orientation: .up)
    }

    private func sampledAlphaAnalysis(maxDimension: Int) -> (
        sampleWidth: Int,
        sampleHeight: Int,
        pixelCount: Int,
        transparentPixelCount: Int,
        visibleBounds: CGRect
    )? {
        guard let cgImage, cgImage.width > 0, cgImage.height > 0 else { return nil }
        let sampleWidth = min(maxDimension, cgImage.width)
        let sampleHeight = min(maxDimension, cgImage.height)
        var pixels = [UInt8](repeating: 0, count: sampleWidth * sampleHeight * 4)
        return pixels.withUnsafeMutableBytes { buffer in
            guard let baseAddress = buffer.baseAddress,
                  let context = CGContext(
                    data: baseAddress,
                    width: sampleWidth,
                    height: sampleHeight,
                    bitsPerComponent: 8,
                    bytesPerRow: sampleWidth * 4,
                    space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
                  )
            else { return nil }
            context.interpolationQuality = .low
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: sampleWidth, height: sampleHeight))

            let bytes = buffer.bindMemory(to: UInt8.self)
            var minX = sampleWidth
            var minY = sampleHeight
            var maxX = -1
            var maxY = -1
            var transparentPixelCount = 0
            for y in 0..<sampleHeight {
                for x in 0..<sampleWidth {
                    let alpha = bytes[(y * sampleWidth + x) * 4 + 3]
                    if alpha < 250 {
                        transparentPixelCount += 1
                    }
                    if alpha > 8 {
                        minX = min(minX, x)
                        minY = min(minY, y)
                        maxX = max(maxX, x)
                        maxY = max(maxY, y)
                    }
                }
            }
            guard maxX >= minX, maxY >= minY else { return nil }
            return (
                sampleWidth: sampleWidth,
                sampleHeight: sampleHeight,
                pixelCount: sampleWidth * sampleHeight,
                transparentPixelCount: transparentPixelCount,
                visibleBounds: CGRect(
                    x: minX,
                    y: minY,
                    width: maxX - minX + 1,
                    height: maxY - minY + 1
                )
            )
        }
    }
}

final class PagedMediaPreviewController: UIViewController {
    private let items: [ChatMediaPreviewItem]
    private var currentIndex: Int
    private let pageController = UIPageViewController(transitionStyle: .scroll, navigationOrientation: .horizontal)
    private let closeButton = UIButton(type: .system)
    private let counterLabel = UILabel()
    var onRequestAccess: ((ChatMediaPreviewItem) -> Void)?
    var onDragMedia: ((MediaDragPayload, UIGestureRecognizer.State, CGPoint) -> Void)?
    var onBackgroundRemoved: ((UIImage, String) -> Void)?

    init(items: [ChatMediaPreviewItem], startIndex: Int) {
        self.items = items
        self.currentIndex = min(max(0, startIndex), max(0, items.count - 1))
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
        modalTransitionStyle = .crossDissolve
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configurePageController()
        configureChrome()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        view.bringSubviewToFront(closeButton)
        view.bringSubviewToFront(counterLabel)
    }

    private func configurePageController() {
        addChild(pageController)
        pageController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(pageController.view)
        pageController.didMove(toParent: self)
        pageController.dataSource = self
        pageController.delegate = self

        NSLayoutConstraint.activate([
            pageController.view.topAnchor.constraint(equalTo: view.topAnchor),
            pageController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pageController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pageController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        if let initial = controller(at: currentIndex) {
            pageController.setViewControllers([initial], direction: .forward, animated: false)
        }
    }

    private func configureChrome() {
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.42)
        closeButton.layer.cornerRadius = 22
        closeButton.layer.cornerCurve = .continuous
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeButton)

        counterLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        counterLabel.textColor = .white
        counterLabel.textAlignment = .center
        counterLabel.backgroundColor = UIColor.black.withAlphaComponent(0.36)
        counterLabel.layer.cornerRadius = 12
        counterLabel.layer.cornerCurve = .continuous
        counterLabel.clipsToBounds = true
        counterLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(counterLabel)
        updateCounter()

        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 14),
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            closeButton.widthAnchor.constraint(equalToConstant: 44),
            closeButton.heightAnchor.constraint(equalToConstant: 44),
            counterLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            counterLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            counterLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 58),
            counterLabel.heightAnchor.constraint(equalToConstant: 24)
        ])
    }

    private func controller(at index: Int) -> FullscreenMediaPreviewController? {
        guard items.indices.contains(index) else { return nil }
        let controller = FullscreenMediaPreviewController.item(items[index]) { [weak self] in
            self?.dismiss(animated: true)
        }
        controller.onRequestAccess = { [weak self] item in
            self?.onRequestAccess?(item)
        }
        controller.onDragMedia = { [weak self] payload, state, screenPoint in
            self?.onDragMedia?(payload, state, screenPoint)
        }
        controller.onBackgroundRemoved = { [weak self] image, originalTitle in
            self?.onBackgroundRemoved?(image, originalTitle)
        }
        controller.view.tag = index
        return controller
    }

    private func updateCounter() {
        counterLabel.text = "\(currentIndex + 1) / \(items.count)"
    }

    @objc private func close() {
        if let navigationController, navigationController.viewControllers.first !== self {
            navigationController.popViewController(animated: true)
            return
        }
        dismiss(animated: true)
    }
}

extension PagedMediaPreviewController: UIPageViewControllerDataSource, UIPageViewControllerDelegate {
    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerBefore viewController: UIViewController
    ) -> UIViewController? {
        controller(at: viewController.view.tag - 1)
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerAfter viewController: UIViewController
    ) -> UIViewController? {
        controller(at: viewController.view.tag + 1)
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        didFinishAnimating finished: Bool,
        previousViewControllers: [UIViewController],
        transitionCompleted completed: Bool
    ) {
        guard completed,
              let visible = pageViewController.viewControllers?.first
        else { return }
        currentIndex = visible.view.tag
        updateCounter()
    }
}

final class FullscreenMediaPreviewController: UIViewController {
    enum PreviewAction {
        case forward
        case save
        case grid
        case more

        var iconName: String {
            switch self {
            case .forward: return "arrowshape.turn.up.right.fill"
            case .save: return "square.and.arrow.down.fill"
            case .grid: return "square.grid.2x2.fill"
            case .more: return "ellipsis"
            }
        }

        var noticeText: String {
            switch self {
            case .forward: return "转发"
            case .save: return "保存"
            case .grid: return "更多媒体"
            case .more: return "更多操作"
            }
        }
    }

    private let image: UIImage?
    private let videoURL: URL?
    private let titleText: String
    private let thumbnailImage: UIImage?
    private let previewItem: ChatMediaPreviewItem?
    private var onClose: (() -> Void)?
    var onRequestAccess: ((ChatMediaPreviewItem) -> Void)?
    var onDragMedia: ((MediaDragPayload, UIGestureRecognizer.State, CGPoint) -> Void)?
    var onBackgroundRemoved: ((UIImage, String) -> Void)?
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var mediaCloseButton: UIButton?
    private var videoCloseButton: UIButton?
    private let videoPlayPauseButton = UIButton(type: .system)
    private let videoProgressSlider = UISlider()
    private let videoTimeLabel = UILabel()
    private let videoRateButton = UIButton(type: .system)
    private var videoTimeObserver: Any?
    private var isDraggingVideoProgress = false
    private var currentVideoRate: Float = 1.0
    private var longPressRestoreRate: Float?
    private var longPressWasPlaying = false
    private var speedHintLabel: UILabel?
    private var backgroundRemovalOverlay: UIView?

    static func image(_ image: UIImage, title: String) -> FullscreenMediaPreviewController {
        FullscreenMediaPreviewController(image: image, videoURL: nil, titleText: title, previewItem: nil)
    }

    static func video(_ url: URL) -> FullscreenMediaPreviewController {
        FullscreenMediaPreviewController(image: nil, videoURL: url, titleText: url.lastPathComponent, previewItem: nil)
    }

    static func item(_ item: ChatMediaPreviewItem, onClose: (() -> Void)? = nil) -> FullscreenMediaPreviewController {
        let controller = FullscreenMediaPreviewController(
            image: item.isVideo ? nil : item.image,
            videoURL: item.isVideo ? item.url : nil,
            titleText: item.title,
            previewItem: item
        )
        controller.onClose = onClose
        return controller
    }

    private init(image: UIImage?, videoURL: URL?, titleText: String, previewItem: ChatMediaPreviewItem?) {
        self.image = image
        self.videoURL = videoURL
        self.titleText = titleText
        self.previewItem = previewItem
        self.thumbnailImage = image ?? videoURL.flatMap { Self.videoThumbnail(url: $0) }
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
        modalTransitionStyle = .crossDissolve
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        if shouldProtectPreview {
            if let thumbnailImage {
                configureImage(thumbnailImage)
            } else {
                configureProtectedPlaceholder()
            }
            configureProtectedPreviewOverlay()
        } else if let image {
            configureImage(image)
        } else if let videoURL {
            configureVideo(videoURL)
        }
        if previewItem?.isVideo != true && previewItem?.kindTitle != "图片" {
            configureAccessOverlay()
        }
        configureSmartImageActions()
        configureBottomActions()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerLayer?.frame = view.bounds
        if let videoCloseButton {
            view.bringSubviewToFront(videoCloseButton)
        }
        if let mediaCloseButton {
            view.bringSubviewToFront(mediaCloseButton)
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        player?.playImmediately(atRate: currentVideoRate)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        player?.pause()
        removeVideoTimeObserver()
    }

    private var shouldProtectPreview: Bool {
        false
    }

    private func configureImage(_ image: UIImage) {
        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(imageView)

        let tap = UITapGestureRecognizer(target: self, action: #selector(close))
        imageView.addGestureRecognizer(tap)

        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: view.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        configureMediaCloseButton()
    }

    private func configureMediaCloseButton() {
        guard mediaCloseButton == nil else { return }
        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.42)
        closeButton.layer.cornerRadius = 22
        closeButton.layer.cornerCurve = .continuous
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeButton)
        mediaCloseButton = closeButton

        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 14),
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            closeButton.widthAnchor.constraint(equalToConstant: 44),
            closeButton.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    private func configureProtectedPlaceholder() {
        let placeholder = UIView()
        placeholder.backgroundColor = UIColor(white: 0.10, alpha: 1)
        placeholder.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(placeholder)

        let iconView = UIImageView(image: UIImage(systemName: previewItem?.isVideo == true ? "play.rectangle.fill" : "photo.fill"))
        iconView.tintColor = UIColor.white.withAlphaComponent(0.45)
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        placeholder.addSubview(iconView)

        NSLayoutConstraint.activate([
            placeholder.topAnchor.constraint(equalTo: view.topAnchor),
            placeholder.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            placeholder.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            placeholder.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            iconView.centerXAnchor.constraint(equalTo: placeholder.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: placeholder.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 76),
            iconView.heightAnchor.constraint(equalToConstant: 76)
        ])
    }

    private func configureProtectedPreviewOverlay() {
        let blurView = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        blurView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(blurView)

        let dimView = UIView()
        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.24)
        dimView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(dimView)

        let messageLabel = UILabel()
        messageLabel.text = "非公开内容\n申请审核通过后可查看清晰\(previewItem?.kindTitle ?? "媒体")"
        messageLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        messageLabel.textColor = .white
        messageLabel.textAlignment = .center
        messageLabel.numberOfLines = 0
        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(messageLabel)

        NSLayoutConstraint.activate([
            blurView.topAnchor.constraint(equalTo: view.topAnchor),
            blurView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            blurView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            blurView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            messageLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            messageLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            messageLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 28),
            messageLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -28)
        ])
    }

    private func configureVideo(_ url: URL) {
        let player = AVPlayer(url: url)
        self.player = player
        let playerLayer = AVPlayerLayer(player: player)
        playerLayer.videoGravity = .resizeAspect
        playerLayer.frame = view.bounds
        view.layer.addSublayer(playerLayer)
        self.playerLayer = playerLayer

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.42)
        closeButton.layer.cornerRadius = 22
        closeButton.layer.cornerCurve = .continuous
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeButton)
        videoCloseButton = closeButton

        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 14),
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            closeButton.widthAnchor.constraint(equalToConstant: 44),
            closeButton.heightAnchor.constraint(equalToConstant: 44)
        ])
        configureVideoControls()
        addVideoTimeObserver()
        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleVideoSpeedLongPress(_:)))
        longPress.minimumPressDuration = 0.28
        longPress.cancelsTouchesInView = false
        view.addGestureRecognizer(longPress)
    }

    private func configureVideoControls() {
        let panel = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        panel.layer.cornerRadius = 18
        panel.layer.cornerCurve = .continuous
        panel.clipsToBounds = true
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        videoPlayPauseButton.setImage(UIImage(systemName: "pause.fill"), for: .normal)
        videoPlayPauseButton.tintColor = .white
        videoPlayPauseButton.addTarget(self, action: #selector(toggleVideoPlayback), for: .touchUpInside)
        videoPlayPauseButton.translatesAutoresizingMaskIntoConstraints = false

        let rewindButton = makeVideoControlButton(symbolName: "gobackward.10", action: #selector(rewindVideo))
        let forwardButton = makeVideoControlButton(symbolName: "goforward.10", action: #selector(forwardVideo))

        videoRateButton.setTitle(Self.rateTitle(currentVideoRate), for: .normal)
        videoRateButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        videoRateButton.tintColor = .white
        videoRateButton.backgroundColor = UIColor.white.withAlphaComponent(0.18)
        videoRateButton.layer.cornerRadius = 14
        videoRateButton.addTarget(self, action: #selector(cycleVideoRate), for: .touchUpInside)
        videoRateButton.translatesAutoresizingMaskIntoConstraints = false

        videoProgressSlider.minimumValue = 0
        videoProgressSlider.maximumValue = 1
        videoProgressSlider.minimumTrackTintColor = .white
        videoProgressSlider.maximumTrackTintColor = UIColor.white.withAlphaComponent(0.32)
        videoProgressSlider.thumbTintColor = .white
        videoProgressSlider.addTarget(self, action: #selector(videoProgressTouchDown), for: .touchDown)
        videoProgressSlider.addTarget(self, action: #selector(videoProgressChanged), for: .valueChanged)
        videoProgressSlider.addTarget(self, action: #selector(videoProgressTouchUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        videoProgressSlider.translatesAutoresizingMaskIntoConstraints = false

        videoTimeLabel.text = "00:00 / 00:00"
        videoTimeLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .medium)
        videoTimeLabel.textColor = UIColor.white.withAlphaComponent(0.88)
        videoTimeLabel.textAlignment = .center
        videoTimeLabel.translatesAutoresizingMaskIntoConstraints = false

        [videoPlayPauseButton, rewindButton, forwardButton, videoRateButton, videoProgressSlider, videoTimeLabel].forEach {
            panel.contentView.addSubview($0)
        }

        NSLayoutConstraint.activate([
            panel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            panel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            panel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -100),
            panel.heightAnchor.constraint(equalToConstant: 104),

            videoPlayPauseButton.leadingAnchor.constraint(equalTo: panel.contentView.leadingAnchor, constant: 14),
            videoPlayPauseButton.topAnchor.constraint(equalTo: panel.contentView.topAnchor, constant: 12),
            videoPlayPauseButton.widthAnchor.constraint(equalToConstant: 38),
            videoPlayPauseButton.heightAnchor.constraint(equalToConstant: 34),

            rewindButton.leadingAnchor.constraint(equalTo: videoPlayPauseButton.trailingAnchor, constant: 8),
            rewindButton.centerYAnchor.constraint(equalTo: videoPlayPauseButton.centerYAnchor),
            rewindButton.widthAnchor.constraint(equalToConstant: 38),
            rewindButton.heightAnchor.constraint(equalToConstant: 34),

            forwardButton.leadingAnchor.constraint(equalTo: rewindButton.trailingAnchor, constant: 8),
            forwardButton.centerYAnchor.constraint(equalTo: videoPlayPauseButton.centerYAnchor),
            forwardButton.widthAnchor.constraint(equalToConstant: 38),
            forwardButton.heightAnchor.constraint(equalToConstant: 34),

            videoRateButton.trailingAnchor.constraint(equalTo: panel.contentView.trailingAnchor, constant: -14),
            videoRateButton.centerYAnchor.constraint(equalTo: videoPlayPauseButton.centerYAnchor),
            videoRateButton.widthAnchor.constraint(equalToConstant: 56),
            videoRateButton.heightAnchor.constraint(equalToConstant: 28),

            videoTimeLabel.leadingAnchor.constraint(equalTo: forwardButton.trailingAnchor, constant: 8),
            videoTimeLabel.trailingAnchor.constraint(equalTo: videoRateButton.leadingAnchor, constant: -8),
            videoTimeLabel.centerYAnchor.constraint(equalTo: videoPlayPauseButton.centerYAnchor),

            videoProgressSlider.leadingAnchor.constraint(equalTo: panel.contentView.leadingAnchor, constant: 16),
            videoProgressSlider.trailingAnchor.constraint(equalTo: panel.contentView.trailingAnchor, constant: -16),
            videoProgressSlider.bottomAnchor.constraint(equalTo: panel.contentView.bottomAnchor, constant: -14),
            videoProgressSlider.heightAnchor.constraint(equalToConstant: 30)
        ])
    }

    private func makeVideoControlButton(symbolName: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: symbolName), for: .normal)
        button.tintColor = .white
        button.addTarget(self, action: action, for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }

    private func configureAccessOverlay() {
        guard let previewItem, previewItem.accessState != .viewable else { return }
        let scopeText = previewItem.accessScope.rawValue
        let stateText = previewItem.accessState.rawValue

        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 8
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        stack.addArrangedSubview(makeAccessBadge(text: scopeText, color: UIColor(red: 0.14, green: 0.42, blue: 0.82, alpha: 1)))
        stack.addArrangedSubview(makeAccessBadge(text: stateText, color: accessStateColor))

        if previewItem.accessState == .requestable || previewItem.accessState == .requested {
            let requestButton = UIButton(type: .system)
            requestButton.setTitle(previewItem.accessState == .requested ? "已申请" : "申请查看", for: .normal)
            requestButton.titleLabel?.font = .systemFont(ofSize: 12, weight: .semibold)
            requestButton.tintColor = .white
            requestButton.backgroundColor = UIColor(red: 0.90, green: 0.45, blue: 0.18, alpha: 0.92)
            requestButton.layer.cornerRadius = 13
            requestButton.layer.cornerCurve = .continuous
            requestButton.applyContentInsets(top: 5, leading: 10, bottom: 5, trailing: 10)
            requestButton.addTarget(self, action: #selector(requestAccessTapped), for: .touchUpInside)
            stack.addArrangedSubview(requestButton)
        }

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 18),
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 58),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16)
        ])
    }

    private var accessStateColor: UIColor {
        switch previewItem?.accessState ?? .viewable {
        case .viewable, .approved:
            return UIColor(red: 0.18, green: 0.58, blue: 0.36, alpha: 1)
        case .requestable:
            return UIColor(red: 0.92, green: 0.48, blue: 0.18, alpha: 1)
        case .requested:
            return UIColor(red: 0.52, green: 0.47, blue: 0.70, alpha: 1)
        }
    }

    private func makeAccessBadge(text: String, color: UIColor) -> UIView {
        let label = InsetLabel()
        label.text = text
        label.textInsets = UIEdgeInsets(top: 0, left: 10, bottom: 0, right: 10)
        label.font = .systemFont(ofSize: 12, weight: .semibold)
        label.textColor = .white
        label.backgroundColor = color.withAlphaComponent(0.92)
        label.layer.cornerRadius = 13
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        label.heightAnchor.constraint(equalToConstant: 26).isActive = true
        label.widthAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        return label
    }

    private func configureSmartImageActions() {
        guard videoURL == nil else { return }
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 8
        stack.alignment = .leading
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        let recognize = makeSmartActionButton(title: "识图", symbolName: "sparkle.magnifyingglass", tag: 11)
        let find = makeSmartActionButton(title: "识物", symbolName: "viewfinder", tag: 12)
        stack.addArrangedSubview(recognize)
        stack.addArrangedSubview(find)
        if image != nil {
            let removeBackground = makeSmartActionButton(title: "扣背景", symbolName: "person.crop.rectangle", tag: 13)
            stack.addArrangedSubview(removeBackground)
        }

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 18),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -112)
        ])
    }

    private func makeSmartActionButton(title: String, symbolName: String, tag: Int) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.title = title
        configuration.image = UIImage(systemName: symbolName)
        configuration.imagePadding = 5
        configuration.baseForegroundColor = .white
        configuration.baseBackgroundColor = UIColor(white: 0.18, alpha: 0.92)
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 7, leading: 10, bottom: 7, trailing: 10)
        let button = UIButton(configuration: configuration)
        button.titleLabel?.font = .systemFont(ofSize: 12, weight: .semibold)
        button.layer.cornerRadius = 16
        button.layer.cornerCurve = .continuous
        button.tag = tag
        button.addTarget(self, action: #selector(smartImageActionTapped(_:)), for: .touchUpInside)
        let dragGesture = UILongPressGestureRecognizer(target: self, action: #selector(handleMediaDragGesture(_:)))
        dragGesture.minimumPressDuration = 0.35
        button.addGestureRecognizer(dragGesture)
        return button
    }

    private func configureBottomActions() {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 18
        stack.alignment = .center
        stack.distribution = .equalSpacing
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        [PreviewAction.forward, .save, .grid, .more].forEach { action in
            let button = UIButton(type: .system)
            button.setImage(UIImage(systemName: action.iconName), for: .normal)
            button.tintColor = .white
            button.backgroundColor = UIColor(white: 0.22, alpha: 0.92)
            button.layer.cornerRadius = 22
            button.layer.cornerCurve = .continuous
            button.tag = tag(for: action)
            button.addTarget(self, action: #selector(actionTapped(_:)), for: .touchUpInside)
            if action == .forward {
                let dragGesture = UILongPressGestureRecognizer(target: self, action: #selector(handleMediaDragGesture(_:)))
                dragGesture.minimumPressDuration = 0.35
                button.addGestureRecognizer(dragGesture)
            }
            button.translatesAutoresizingMaskIntoConstraints = false
            button.widthAnchor.constraint(equalToConstant: 44).isActive = true
            button.heightAnchor.constraint(equalToConstant: 44).isActive = true
            stack.addArrangedSubview(button)
        }

        NSLayoutConstraint.activate([
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -18),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -44)
        ])
    }

    private func tag(for action: PreviewAction) -> Int {
        switch action {
        case .forward: return 1
        case .save: return 2
        case .grid: return 3
        case .more: return 4
        }
    }

    private func action(for tag: Int) -> PreviewAction {
        switch tag {
        case 1: return .forward
        case 2: return .save
        case 3: return .grid
        default: return .more
        }
    }

    @objc private func requestAccessTapped() {
        guard let previewItem else { return }
        onRequestAccess?(previewItem)
        showAlert(title: "申请已提交", message: "请在右侧工具「申请审核」中模拟发布者同意。")
    }

    @objc private func smartImageActionTapped(_ sender: UIButton) {
        let source = previewItem?.sourceURLText ?? titleText
        if sender.tag == 11 {
            showAlert(
                title: "识图结果",
                message: "已识别：活动海报、门店场景、二维码/水印链接。\nAI 可访问链接：\(source)"
            )
        } else if sender.tag == 12 {
            showAlert(
                title: "识物结果",
                message: "已定位：招牌、入口、物料箱和画面主体。\n目标图片：\(titleText)"
            )
        } else {
            removeImageBackground()
        }
    }

    @objc private func handleMediaDragGesture(_ gesture: UILongPressGestureRecognizer) {
        guard let payload = mediaDragPayload else { return }
        let pointInWindow = gesture.view?.convert(gesture.location(in: gesture.view), to: nil) ?? gesture.location(in: view)
        onDragMedia?(payload, gesture.state, pointInWindow)
    }

    private var mediaDragPayload: MediaDragPayload? {
        if let previewItem {
            return MediaDragPayload(
                title: previewItem.title,
                url: previewItem.url,
                image: previewItem.image ?? thumbnailImage,
                isVideo: previewItem.isVideo
            )
        }
        if image != nil || videoURL != nil {
            return MediaDragPayload(
                title: titleText,
                url: videoURL,
                image: image ?? thumbnailImage,
                isVideo: videoURL != nil
            )
        }
        return nil
    }

    @objc private func actionTapped(_ sender: UIButton) {
        switch action(for: sender.tag) {
        case .forward:
            presentShareSheet(sourceView: sender)
        case .save:
            saveToPhotoLibrary()
        case .grid:
            presentMediaGrid()
        case .more:
            presentMoreActions(sourceView: sender)
        }
    }

    @objc private func toggleVideoPlayback() {
        guard let player else { return }
        if player.timeControlStatus == .playing {
            player.pause()
            videoPlayPauseButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
        } else {
            player.playImmediately(atRate: currentVideoRate)
            videoPlayPauseButton.setImage(UIImage(systemName: "pause.fill"), for: .normal)
        }
    }

    @objc private func handleVideoSpeedLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard let player else { return }
        switch gesture.state {
        case .began:
            longPressRestoreRate = currentVideoRate
            longPressWasPlaying = player.rate > 0 || player.timeControlStatus == .playing
            player.playImmediately(atRate: 2.0)
            videoPlayPauseButton.setImage(UIImage(systemName: "pause.fill"), for: .normal)
            showSpeedHint()
        case .ended, .cancelled, .failed:
            let restoreRate = longPressRestoreRate ?? currentVideoRate
            longPressRestoreRate = nil
            if longPressWasPlaying {
                player.rate = restoreRate
            } else {
                player.pause()
                videoPlayPauseButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
            }
            longPressWasPlaying = false
            hideSpeedHint()
        default:
            break
        }
    }

    private func showSpeedHint() {
        if speedHintLabel == nil {
            let label = UILabel()
            label.text = "2x 快进中"
            label.textAlignment = .center
            label.font = .systemFont(ofSize: 16, weight: .bold)
            label.textColor = .white
            label.backgroundColor = UIColor.black.withAlphaComponent(0.48)
            label.layer.cornerRadius = 18
            label.layer.cornerCurve = .continuous
            label.clipsToBounds = true
            label.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(label)
            speedHintLabel = label
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
                label.widthAnchor.constraint(greaterThanOrEqualToConstant: 112),
                label.heightAnchor.constraint(equalToConstant: 36)
            ])
        }
        view.bringSubviewToFront(speedHintLabel!)
        speedHintLabel?.alpha = 1
    }

    private func hideSpeedHint() {
        UIView.animate(withDuration: 0.16) {
            self.speedHintLabel?.alpha = 0
        }
    }

    @objc private func rewindVideo() {
        seekVideo(by: -10)
    }

    @objc private func forwardVideo() {
        seekVideo(by: 10)
    }

    @objc private func cycleVideoRate() {
        let rates: [Float] = [0.5, 1.0, 1.25, 1.5, 2.0]
        let currentIndex = rates.firstIndex(of: currentVideoRate) ?? 1
        currentVideoRate = rates[(currentIndex + 1) % rates.count]
        videoRateButton.setTitle(Self.rateTitle(currentVideoRate), for: .normal)
        if player?.timeControlStatus == .playing {
            player?.rate = currentVideoRate
        }
    }

    private static func rateTitle(_ rate: Float) -> String {
        if rate == 1.0 { return "1.0x" }
        if floor(rate) == rate {
            return String(format: "%.0fx", rate)
        }
        return String(format: "%.2gx", rate)
    }

    @objc private func videoProgressTouchDown() {
        isDraggingVideoProgress = true
    }

    @objc private func videoProgressChanged() {
        updateVideoTimeLabel(progress: Double(videoProgressSlider.value))
    }

    @objc private func videoProgressTouchUp() {
        guard let duration = player?.currentItem?.duration.seconds,
              duration.isFinite,
              duration > 0 else {
            isDraggingVideoProgress = false
            return
        }
        let seconds = duration * Double(videoProgressSlider.value)
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600)) { [weak self] _ in
            self?.isDraggingVideoProgress = false
            if self?.player?.timeControlStatus == .playing {
                self?.player?.rate = self?.currentVideoRate ?? 1.0
            }
        }
    }

    private func seekVideo(by seconds: Double) {
        guard let player,
              let duration = player.currentItem?.duration.seconds,
              duration.isFinite else { return }
        let current = player.currentTime().seconds
        let target = min(max(current + seconds, 0), duration)
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600)) { [weak self] _ in
            if self?.player?.timeControlStatus == .playing {
                self?.player?.rate = self?.currentVideoRate ?? 1.0
            }
        }
    }

    private func addVideoTimeObserver() {
        removeVideoTimeObserver()
        let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
        videoTimeObserver = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self,
                  let duration = self.player?.currentItem?.duration.seconds,
                  duration.isFinite,
                  duration > 0 else { return }
            let current = time.seconds
            if !self.isDraggingVideoProgress {
                self.videoProgressSlider.value = Float(min(max(current / duration, 0), 1))
            }
            self.updateVideoTimeLabel(current: current, duration: duration)
            self.videoPlayPauseButton.setImage(
                UIImage(systemName: self.player?.timeControlStatus == .playing ? "pause.fill" : "play.fill"),
                for: .normal
            )
        }
    }

    private func removeVideoTimeObserver() {
        if let videoTimeObserver {
            player?.removeTimeObserver(videoTimeObserver)
            self.videoTimeObserver = nil
        }
    }

    private func updateVideoTimeLabel(progress: Double) {
        guard let duration = player?.currentItem?.duration.seconds,
              duration.isFinite,
              duration > 0 else { return }
        updateVideoTimeLabel(current: duration * progress, duration: duration)
    }

    private func updateVideoTimeLabel(current: Double, duration: Double) {
        videoTimeLabel.text = "\(formatVideoTime(current)) / \(formatVideoTime(duration))"
    }

    private func formatVideoTime(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "00:00" }
        let total = max(0, Int(seconds.rounded()))
        return String(format: "%02d:%02d", total / 60, total % 60)
    }

    @objc private func close() {
        if let onClose {
            onClose()
        } else {
            dismiss(animated: true)
        }
    }

    private func presentShareSheet(sourceView: UIView) {
        let items: [Any]
        if let image {
            items = [image]
        } else if let videoURL {
            items = [videoURL]
        } else {
            items = [titleText]
        }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = sourceView
        controller.popoverPresentationController?.sourceRect = sourceView.bounds
        present(controller, animated: true)
    }

    private func saveToPhotoLibrary() {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { [weak self] status in
            DispatchQueue.main.async {
                guard let self else { return }
                guard status == .authorized || status == .limited else {
                    self.showAlert(title: "无法保存", message: "请在系统设置中允许写入相册。")
                    return
                }
                PHPhotoLibrary.shared().performChanges {
                    if let image = self.image {
                        PHAssetChangeRequest.creationRequestForAsset(from: image)
                    } else if let videoURL = self.videoURL {
                        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
                    }
                } completionHandler: { success, error in
                    DispatchQueue.main.async {
                        self.showAlert(
                            title: success ? "已保存" : "保存失败",
                            message: success ? "已保存到系统相册。" : (error?.localizedDescription ?? "无法保存当前媒体。")
                        )
                    }
                }
            }
        }
    }

    private func presentMediaGrid() {
        let controller = MediaPreviewGridController(
            titleText: titleText,
            thumbnail: thumbnailImage,
            isVideo: videoURL != nil
        )
        present(controller, animated: true)
    }

    private func presentMoreActions(sourceView: UIView) {
        let alert = UIAlertController(title: "更多操作", message: titleText, preferredStyle: .actionSheet)
        if image != nil {
            alert.addAction(UIAlertAction(title: "扣除背景", style: .default) { [weak self] _ in
                self?.removeImageBackground()
            })
        }
        alert.addAction(UIAlertAction(title: "转发", style: .default) { [weak self, weak sourceView] _ in
            guard let self, let sourceView else { return }
            self.presentShareSheet(sourceView: sourceView)
        })
        alert.addAction(UIAlertAction(title: "保存到相册", style: .default) { [weak self] _ in
            self?.saveToPhotoLibrary()
        })
        alert.addAction(UIAlertAction(title: "查看媒体列表", style: .default) { [weak self] _ in
            self?.presentMediaGrid()
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.popoverPresentationController?.sourceView = sourceView
        alert.popoverPresentationController?.sourceRect = sourceView.bounds
        present(alert, animated: true)
    }

    private func removeImageBackground() {
        guard let image else {
            showAlert(title: "无法扣背景", message: "当前不是图片内容。")
            return
        }
        guard #available(iOS 17.0, *) else {
            showAlert(title: "系统版本不支持", message: "本地扣背景需要 iOS 17 或更高版本。后续可接入 CoreML 模型兼容 iOS 16。")
            return
        }

        showBackgroundRemovalLoading()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let result = Result { try BackgroundRemovalProcessor.removeBackground(from: image) }
            DispatchQueue.main.async {
                guard let self else { return }
                self.hideBackgroundRemovalLoading()
                switch result {
                case .success(let output):
                    self.onBackgroundRemoved?(output, self.titleText)
                case .failure(let error):
                    self.showAlert(title: "扣背景失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func showBackgroundRemovalLoading() {
        guard backgroundRemovalOverlay == nil else { return }
        let overlay = UIView()
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.48)
        overlay.translatesAutoresizingMaskIntoConstraints = false

        let card = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        card.layer.cornerRadius = 18
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        card.translatesAutoresizingMaskIntoConstraints = false

        let spinner = UIActivityIndicatorView(style: .large)
        spinner.color = .white
        spinner.startAnimating()
        spinner.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = "正在扣除背景..."
        label.font = .systemFont(ofSize: 15, weight: .semibold)
        label.textColor = .white
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(overlay)
        overlay.addSubview(card)
        card.contentView.addSubview(spinner)
        card.contentView.addSubview(label)
        backgroundRemovalOverlay = overlay

        NSLayoutConstraint.activate([
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            card.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            card.centerYAnchor.constraint(equalTo: overlay.centerYAnchor),
            card.widthAnchor.constraint(equalToConstant: 178),
            card.heightAnchor.constraint(equalToConstant: 122),

            spinner.centerXAnchor.constraint(equalTo: card.contentView.centerXAnchor),
            spinner.topAnchor.constraint(equalTo: card.contentView.topAnchor, constant: 22),

            label.leadingAnchor.constraint(equalTo: card.contentView.leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(equalTo: card.contentView.trailingAnchor, constant: -16),
            label.topAnchor.constraint(equalTo: spinner.bottomAnchor, constant: 14)
        ])
    }

    private func hideBackgroundRemovalLoading() {
        backgroundRemovalOverlay?.removeFromSuperview()
        backgroundRemovalOverlay = nil
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }

    private static func videoThumbnail(url: URL) -> UIImage? {
        let asset = AVURLAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        guard let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

private final class MediaPreviewGridController: UIViewController {
    private let titleText: String
    private let thumbnail: UIImage?
    private let isVideo: Bool

    init(titleText: String, thumbnail: UIImage?, isVideo: Bool) {
        self.titleText = titleText
        self.thumbnail = thumbnail
        self.isVideo = isVideo
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .pageSheet
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(white: 0.06, alpha: 1)

        let titleLabel = UILabel()
        titleLabel.text = "媒体"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 18, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let closeButton = UIButton(type: .system)
        closeButton.setTitle("关闭", for: .normal)
        closeButton.tintColor = .white
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        let imageView = UIImageView(image: thumbnail)
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.layer.cornerRadius = 8
        imageView.layer.cornerCurve = .continuous
        imageView.backgroundColor = UIColor.white.withAlphaComponent(0.08)
        imageView.translatesAutoresizingMaskIntoConstraints = false

        let playView = UIImageView(image: UIImage(systemName: "play.circle.fill"))
        playView.tintColor = .white
        playView.contentMode = .scaleAspectFit
        playView.isHidden = !isVideo
        playView.translatesAutoresizingMaskIntoConstraints = false

        let nameLabel = UILabel()
        nameLabel.text = titleText.isEmpty ? (isVideo ? "视频" : "图片") : titleText
        nameLabel.textColor = UIColor.white.withAlphaComponent(0.84)
        nameLabel.font = .systemFont(ofSize: 13, weight: .medium)
        nameLabel.numberOfLines = 2
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(titleLabel)
        view.addSubview(closeButton)
        view.addSubview(imageView)
        view.addSubview(playView)
        view.addSubview(nameLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 18),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            closeButton.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

            imageView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 22),
            imageView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            imageView.widthAnchor.constraint(equalToConstant: 96),
            imageView.heightAnchor.constraint(equalToConstant: 96),
            playView.centerXAnchor.constraint(equalTo: imageView.centerXAnchor),
            playView.centerYAnchor.constraint(equalTo: imageView.centerYAnchor),
            playView.widthAnchor.constraint(equalToConstant: 30),
            playView.heightAnchor.constraint(equalToConstant: 30),

            nameLabel.leadingAnchor.constraint(equalTo: imageView.leadingAnchor),
            nameLabel.trailingAnchor.constraint(equalTo: imageView.trailingAnchor),
            nameLabel.topAnchor.constraint(equalTo: imageView.bottomAnchor, constant: 8)
        ])
    }

    @objc private func close() {
        dismiss(animated: true)
    }
}
