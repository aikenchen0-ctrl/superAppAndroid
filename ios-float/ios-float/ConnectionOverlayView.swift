import UIKit

enum MessageConnectionLayoutMetrics {
    static let bubbleConnectionSideInset: CGFloat = 11
    static let bubbleOppositeSideInset: CGFloat = 11
    static let trackGap: CGFloat = 5
    static let maximumBubbleWidthRatio: CGFloat = 0.92
    static let maximumBubbleWidthIncrease: CGFloat = 0
    static let groupMemberAvatarSide: CGFloat = 36
    static let groupMemberAvatarToBubbleGap: CGFloat = 4
    static let groupTrunkToMemberAvatarGap: CGFloat = 4

    static var groupMemberOccupiedWidth: CGFloat {
        groupMemberAvatarSide + groupMemberAvatarToBubbleGap
    }

    static var bubbleHorizontalMargins: CGFloat {
        bubbleConnectionSideInset + bubbleOppositeSideInset
    }

    static func resolvedConnectionSideInset(
        in containerWidth: CGFloat,
        occupiedWidth: CGFloat = 0
    ) -> CGFloat {
        bubbleConnectionSideInset
    }

    static func maximumBubbleWidth(
        in containerWidth: CGFloat,
        occupiedWidth: CGFloat = 0,
        reservedWidth: CGFloat = 0,
        minimumWidth: CGFloat = 1
    ) -> CGFloat {
        let connectionSideInset = resolvedConnectionSideInset(
            in: containerWidth,
            occupiedWidth: occupiedWidth
        )
        let geometryLimit = containerWidth
            - connectionSideInset
            - bubbleOppositeSideInset
            - occupiedWidth
            - reservedWidth
        let expandedLimit = containerWidth * maximumBubbleWidthRatio
            + maximumBubbleWidthIncrease
            - reservedWidth
        return max(minimumWidth, min(geometryLimit, expandedLimit))
    }
}

enum ConnectionDirection: Equatable {
    case leftToMessage
    case rightAccountToMessage
    case rightToolToMessage
}

struct ConnectionAnchor {
    let from: CGPoint
    let to: CGPoint
    let color: UIColor
    let direction: ConnectionDirection
    let groupID: UUID?
    let usesRoundedCorners: Bool
    let preferredVerticalX: CGFloat?

    init(
        from: CGPoint,
        to: CGPoint,
        color: UIColor,
        direction: ConnectionDirection,
        groupID: UUID? = nil,
        usesRoundedCorners: Bool = true,
        preferredVerticalX: CGFloat? = nil
    ) {
        self.from = from
        self.to = to
        self.color = color
        self.direction = direction
        self.groupID = groupID
        self.usesRoundedCorners = usesRoundedCorners
        self.preferredVerticalX = preferredVerticalX
    }
}

struct ConnectionOverlayColorInput: Equatable {
    let colorSpaceName: String
    let components: [CGFloat]
    let fallbackDescription: String?

    init(color: UIColor, traitCollection: UITraitCollection) {
        let resolvedColor = color.resolvedColor(with: traitCollection).cgColor
        let extendedSRGB = CGColorSpace(name: CGColorSpace.extendedSRGB)
        let normalizedColor = extendedSRGB.flatMap {
            resolvedColor.converted(to: $0, intent: .defaultIntent, options: nil)
        }
        let comparableColor = normalizedColor ?? resolvedColor
        if let name = comparableColor.colorSpace?.name {
            colorSpaceName = name as String
        } else {
            colorSpaceName = "model:\(comparableColor.colorSpace?.model.rawValue ?? -1)"
        }
        components = comparableColor.components ?? []
        fallbackDescription = normalizedColor == nil ? String(describing: resolvedColor) : nil
    }
}

struct ConnectionOverlayAnchorInput: Equatable {
    let from: CGPoint
    let to: CGPoint
    let color: ConnectionOverlayColorInput
    let direction: ConnectionDirection
    let groupID: UUID?
    let usesRoundedCorners: Bool
    let preferredVerticalX: CGFloat?

    init(anchor: ConnectionAnchor, traitCollection: UITraitCollection) {
        from = anchor.from
        to = anchor.to
        color = ConnectionOverlayColorInput(color: anchor.color, traitCollection: traitCollection)
        direction = anchor.direction
        groupID = anchor.groupID
        usesRoundedCorners = anchor.usesRoundedCorners
        preferredVerticalX = anchor.preferredVerticalX
    }
}

struct ConnectionOverlayRenderInput: Equatable {
    let anchors: [ConnectionOverlayAnchorInput]
    let bounds: CGRect
    let displayScale: CGFloat

    init(
        anchors: [ConnectionAnchor],
        bounds: CGRect,
        displayScale: CGFloat,
        traitCollection: UITraitCollection
    ) {
        self.anchors = anchors.map {
            ConnectionOverlayAnchorInput(anchor: $0, traitCollection: traitCollection)
        }
        self.bounds = bounds
        self.displayScale = displayScale
    }
}

final class ConnectionOverlayView: UIView {
    private struct StraightSegment {
        let start: CGPoint
        let end: CGPoint
    }

    private struct QuadSegment {
        let start: CGPoint
        let control: CGPoint
        let end: CGPoint
    }

    private struct RoutedConnection {
        var straightSegments: [StraightSegment] = []
        var quadSegments: [QuadSegment] = []
    }

    private struct SideTracks {
        let left: CGFloat?
        let right: CGFloat?
    }

    private struct SegmentBucketKey: Hashable {
        enum Axis {
            case horizontal
            case vertical
        }

        let axis: Axis
        let fixedCoordinate: Int
    }

    private struct SegmentInterval {
        var lower: CGFloat
        var upper: CGFloat
    }

    var anchors: [ConnectionAnchor] = [] {
        didSet { renderConnections() }
    }
    let usesAnimatedDashFlow = false
    private let connectionLayer = CAShapeLayer()
    private var groupedConnectionLayers: [ConnectionGroupKey: CAShapeLayer] = [:]
    private let coordinateMergeScale: CGFloat = 2
    private let messageTrackGap = MessageConnectionLayoutMetrics.trackGap
    private var lastRenderInput: ConnectionOverlayRenderInput?
#if DEBUG
    private(set) var debugPathRebuildCount = 0
#endif

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        clipsToBounds = true
        layer.masksToBounds = true
        configureConnectionLayer()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        clipsToBounds = true
        layer.masksToBounds = true
        configureConnectionLayer()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        renderConnections()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        renderConnections()
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard previousTraitCollection?.hasDifferentColorAppearance(comparedTo: traitCollection) ?? true else {
            return
        }
        renderConnections()
    }

    private func renderConnections() {
        let scale = renderingScale
        guard bounds.hasFiniteGeometry, scale.isFinite, scale > 0 else {
            clearRenderedConnections()
            return
        }
        let renderableAnchors = anchors.filter(\.hasFiniteGeometry)
        let renderInput = ConnectionOverlayRenderInput(
            anchors: renderableAnchors,
            bounds: bounds,
            displayScale: scale,
            traitCollection: traitCollection
        )
        guard renderInput != lastRenderInput else { return }
        lastRenderInput = renderInput
#if DEBUG
        debugPathRebuildCount &+= 1
        let signpostID = PerformanceSignpost.begin("ConnectionOverlayPathRebuild")
        defer { PerformanceSignpost.end("ConnectionOverlayPathRebuild", id: signpostID) }
#endif
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let groupedAnchors = Dictionary(grouping: renderableAnchors) { anchor in
            anchor.groupID.map(ConnectionGroupKey.group) ?? .shared
        }
        let usesGroupedColors = renderableAnchors.contains { $0.groupID != nil }
        let combinedPath = UIBezierPath()
        let activeGroupKeys = Set(groupedAnchors.keys)
        let staleGroupKeys = groupedConnectionLayers.keys.filter { !activeGroupKeys.contains($0) }
        for key in staleGroupKeys {
            groupedConnectionLayers.removeValue(forKey: key)?.removeFromSuperlayer()
        }
        for (key, groupAnchors) in groupedAnchors {
            let tracks = sideTracks(for: groupAnchors)
            let routes = groupAnchors.map { anchor in
                makeRoute(
                    for: anchor,
                    preferredVerticalX: anchor.preferredVerticalX
                        ?? (anchor.direction == .leftToMessage ? tracks.left : tracks.right)
                )
            }
            let groupPath = makeMergedPath(from: routes)
            combinedPath.append(groupPath)
            if usesGroupedColors, let color = groupAnchors.first?.color {
                let groupLayer: CAShapeLayer
                if let existing = groupedConnectionLayers[key] {
                    groupLayer = existing
                    groupLayer.strokeColor = color.withAlphaComponent(0.88).cgColor
                } else {
                    groupLayer = makeConnectionLayer(strokeColor: color.withAlphaComponent(0.88))
                    layer.addSublayer(groupLayer)
                    groupedConnectionLayers[key] = groupLayer
                }
                groupLayer.frame = bounds
                groupLayer.path = groupPath.cgPath
            }
        }
        if !usesGroupedColors {
            groupedConnectionLayers.values.forEach { $0.removeFromSuperlayer() }
            groupedConnectionLayers.removeAll(keepingCapacity: true)
        }
        connectionLayer.frame = bounds
        connectionLayer.path = usesGroupedColors ? nil : combinedPath.cgPath
        CATransaction.commit()
    }

    private func clearRenderedConnections() {
        lastRenderInput = nil
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        connectionLayer.path = nil
        groupedConnectionLayers.values.forEach { $0.removeFromSuperlayer() }
        groupedConnectionLayers.removeAll(keepingCapacity: true)
        CATransaction.commit()
    }

    private enum ConnectionGroupKey: Hashable {
        case shared
        case group(UUID)
    }

    private func configureConnectionLayer() {
        guard connectionLayer.superlayer == nil else { return }
        connectionLayer.fillColor = UIColor.clear.cgColor
        connectionLayer.strokeColor = UIColor.white.withAlphaComponent(0.9).cgColor
        connectionLayer.lineWidth = 1.5
        connectionLayer.lineCap = .butt
        connectionLayer.lineJoin = .miter
        connectionLayer.lineDashPattern = nil
        connectionLayer.shadowOpacity = 0
        layer.addSublayer(connectionLayer)
    }

    private func makeConnectionLayer(strokeColor: UIColor) -> CAShapeLayer {
        let shapeLayer = CAShapeLayer()
        shapeLayer.fillColor = UIColor.clear.cgColor
        shapeLayer.strokeColor = strokeColor.cgColor
        shapeLayer.lineWidth = 1.5
        shapeLayer.lineCap = .butt
        shapeLayer.lineJoin = .miter
        return shapeLayer
    }

    private func makeRoute(for anchor: ConnectionAnchor, preferredVerticalX: CGFloat?) -> RoutedConnection {
        var route = RoutedConnection()
        let isLeftSide = anchor.direction == .leftToMessage
        let verticalX = pixelAligned(preferredVerticalX ?? messageSideVerticalX(for: anchor, isLeftSide: isLeftSide))
        let horizontalDistanceToMessage = abs(anchor.to.x - verticalX)
        let radius = anchor.usesRoundedCorners
            ? min(CGFloat(5), horizontalDistanceToMessage, abs(anchor.to.y - anchor.from.y) * 0.24)
            : 0
        let verticalDirection: CGFloat = anchor.to.y >= anchor.from.y ? 1 : -1
        let horizontalDirection: CGFloat
        if horizontalDistanceToMessage > 0.5 {
            horizontalDirection = anchor.to.x > verticalX ? 1 : -1
        } else {
            horizontalDirection = isLeftSide ? 1 : -1
        }
        let secondCornerStart = CGPoint(x: verticalX, y: anchor.to.y - verticalDirection * radius)
        let secondCornerEnd = CGPoint(x: verticalX + horizontalDirection * radius, y: anchor.to.y)

        appendLine(from: anchor.from, to: CGPoint(x: verticalX, y: anchor.from.y), into: &route)
        if radius > 1 {
            appendLine(from: CGPoint(x: verticalX, y: anchor.from.y), to: secondCornerStart, into: &route)
            route.quadSegments.append(
                QuadSegment(
                    start: secondCornerStart,
                    control: CGPoint(x: verticalX, y: anchor.to.y),
                    end: secondCornerEnd
                )
            )
            appendLine(from: secondCornerEnd, to: anchor.to, into: &route)
        } else {
            appendLine(from: CGPoint(x: verticalX, y: anchor.from.y), to: CGPoint(x: verticalX, y: anchor.to.y), into: &route)
            appendLine(from: CGPoint(x: verticalX, y: anchor.to.y), to: anchor.to, into: &route)
        }
        return route
    }

    private func sideTracks(for anchors: [ConnectionAnchor]) -> SideTracks {
        let leftAnchors = anchors.filter { $0.direction == .leftToMessage }
        let rightAnchors = anchors.filter { $0.direction != .leftToMessage }

        let leftTrack = outsideMessageTrack(
            messageEdge: leftAnchors.map(\.to.x).min(),
            senderEdge: leftAnchors.map(\.from.x).max(),
            side: .left
        )
        let rightTrack = outsideMessageTrack(
            messageEdge: rightAnchors.map(\.to.x).max(),
            senderEdge: rightAnchors.map(\.from.x).min(),
            side: .right
        )

        return SideTracks(left: leftTrack, right: rightTrack)
    }

    private enum TrackSide {
        case left
        case right
    }

    private func outsideMessageTrack(messageEdge: CGFloat?, senderEdge: CGFloat?, side: TrackSide) -> CGFloat? {
        guard let messageEdge, let senderEdge else { return messageEdge }

        switch side {
        case .left:
            let desired = messageEdge - messageTrackGap
            let minAllowed = senderEdge + 6
            let maxAllowed = messageEdge - 4
            guard maxAllowed >= minAllowed else {
                return (senderEdge + messageEdge) / 2
            }
            return min(max(desired, minAllowed), maxAllowed)
        case .right:
            let desired = messageEdge + messageTrackGap
            let minAllowed = messageEdge + 4
            let maxAllowed = senderEdge - 6
            guard maxAllowed >= minAllowed else {
                return (senderEdge + messageEdge) / 2
            }
            return min(max(desired, minAllowed), maxAllowed)
        }
    }

    private func messageSideVerticalX(for anchor: ConnectionAnchor, isLeftSide: Bool) -> CGFloat {
        let messageSideInset: CGFloat = 0
        let minimumGap: CGFloat = 24
        let gap = abs(anchor.to.x - anchor.from.x)

        guard gap > minimumGap else {
            let midpoint = (anchor.from.x + anchor.to.x) / 2
            return isLeftSide
                ? max(anchor.from.x + 6, midpoint)
                : min(anchor.from.x - 6, midpoint)
        }

        return isLeftSide
            ? anchor.to.x - messageSideInset
            : anchor.to.x + messageSideInset
    }

    private func appendLine(from start: CGPoint, to end: CGPoint, into route: inout RoutedConnection) {
        guard abs(start.x - end.x) > 0.1 || abs(start.y - end.y) > 0.1 else { return }
        route.straightSegments.append(
            StraightSegment(
                start: CGPoint(x: pixelAligned(start.x), y: pixelAligned(start.y)),
                end: CGPoint(x: pixelAligned(end.x), y: pixelAligned(end.y))
            )
        )
    }

    private func makeMergedPath(from routes: [RoutedConnection]) -> UIBezierPath {
        let path = UIBezierPath()
        var buckets: [SegmentBucketKey: [SegmentInterval]] = [:]

        for route in routes {
            for segment in route.straightSegments {
                add(segment, to: &buckets)
            }
        }

        for (key, intervals) in buckets {
            let fixed = CGFloat(key.fixedCoordinate) / coordinateMergeScale
            for interval in merge(intervals) {
                switch key.axis {
                case .horizontal:
                    path.move(to: CGPoint(x: interval.lower, y: fixed))
                    path.addLine(to: CGPoint(x: interval.upper, y: fixed))
                case .vertical:
                    path.move(to: CGPoint(x: fixed, y: interval.lower))
                    path.addLine(to: CGPoint(x: fixed, y: interval.upper))
                }
            }
        }

        for route in routes {
            for segment in route.quadSegments {
                path.move(to: segment.start)
                path.addQuadCurve(to: segment.end, controlPoint: segment.control)
            }
        }

        return path
    }

    private func add(_ segment: StraightSegment, to buckets: inout [SegmentBucketKey: [SegmentInterval]]) {
        if abs(segment.start.y - segment.end.y) <= 0.5 {
            let y = quantizedCoordinate(segment.start.y)
            buckets[SegmentBucketKey(axis: .horizontal, fixedCoordinate: y), default: []].append(
                SegmentInterval(
                    lower: min(segment.start.x, segment.end.x),
                    upper: max(segment.start.x, segment.end.x)
                )
            )
            return
        }

        if abs(segment.start.x - segment.end.x) <= 0.5 {
            let x = quantizedCoordinate(segment.start.x)
            buckets[SegmentBucketKey(axis: .vertical, fixedCoordinate: x), default: []].append(
                SegmentInterval(
                    lower: min(segment.start.y, segment.end.y),
                    upper: max(segment.start.y, segment.end.y)
                )
            )
            return
        }

        let key = SegmentBucketKey(axis: .horizontal, fixedCoordinate: quantizedCoordinate(segment.start.y))
        buckets[key, default: []].append(
            SegmentInterval(
                lower: min(segment.start.x, segment.end.x),
                upper: max(segment.start.x, segment.end.x)
            )
        )
    }

    private func merge(_ intervals: [SegmentInterval]) -> [SegmentInterval] {
        let sorted = intervals.sorted {
            if abs($0.lower - $1.lower) > 0.1 {
                return $0.lower < $1.lower
            }
            return $0.upper < $1.upper
        }
        guard var current = sorted.first else { return [] }
        var merged: [SegmentInterval] = []

        for interval in sorted.dropFirst() {
            if interval.lower <= current.upper + 0.75 {
                current.upper = max(current.upper, interval.upper)
            } else {
                merged.append(current)
                current = interval
            }
        }
        merged.append(current)
        return merged
    }

    private func pixelAligned(_ value: CGFloat) -> CGFloat {
        let scale = renderingScale
        guard value.isFinite, scale.isFinite, scale > 0 else { return 0 }
        return (value * scale).rounded() / scale
    }

    private var renderingScale: CGFloat {
        window?.screen.scale ?? UIScreen.main.scale
    }

    private func quantizedCoordinate(_ value: CGFloat) -> Int {
        guard value.isFinite else { return 0 }
        return Int((value * coordinateMergeScale).rounded())
    }

}

private extension ConnectionAnchor {
    var hasFiniteGeometry: Bool {
        from.hasFiniteCoordinates
            && to.hasFiniteCoordinates
            && (preferredVerticalX?.isFinite ?? true)
    }
}

private extension CGPoint {
    var hasFiniteCoordinates: Bool {
        x.isFinite && y.isFinite
    }
}

private extension CGRect {
    var hasFiniteGeometry: Bool {
        origin.hasFiniteCoordinates && width.isFinite && height.isFinite
    }
}
