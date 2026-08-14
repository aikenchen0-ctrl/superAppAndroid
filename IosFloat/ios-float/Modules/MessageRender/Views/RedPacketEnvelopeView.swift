import AVFoundation
import UIKit

final class RedPacketEnvelopeView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        isOpaque = false
        backgroundColor = .clear
    }

    override func draw(_ rect: CGRect) {
        guard rect.width > 0, rect.height > 0,
              let context = UIGraphicsGetCurrentContext()
        else { return }

        if let image = UIImage(named: "RedPacketStackIcon") {
            image.draw(in: AVMakeRect(aspectRatio: image.size, insideRect: rect))
            return
        }

        let cardRect = rect.insetBy(dx: 0.5, dy: 0.5)
        let cardPath = UIBezierPath(roundedRect: cardRect, cornerRadius: 7)
        context.saveGState()
        cardPath.addClip()

        let colors = [
            UIColor(red: 1.0, green: 0.28, blue: 0.12, alpha: 1).cgColor,
            UIColor(red: 1.0, green: 0.11, blue: 0.24, alpha: 1).cgColor
        ] as CFArray
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
            context.drawLinearGradient(
                gradient,
                start: CGPoint(x: cardRect.midX, y: cardRect.minY),
                end: CGPoint(x: cardRect.midX, y: cardRect.maxY),
                options: []
            )
        }

        UIColor(red: 0.88, green: 0.05, blue: 0.15, alpha: 0.24).setFill()
        let flapPath = UIBezierPath()
        flapPath.move(to: CGPoint(x: cardRect.minX, y: cardRect.height * 0.34))
        flapPath.addQuadCurve(
            to: CGPoint(x: cardRect.maxX, y: cardRect.height * 0.34),
            controlPoint: CGPoint(x: cardRect.midX, y: cardRect.height * 0.20)
        )
        flapPath.addLine(to: CGPoint(x: cardRect.maxX, y: cardRect.height * 0.56))
        flapPath.addQuadCurve(
            to: CGPoint(x: cardRect.minX, y: cardRect.height * 0.56),
            controlPoint: CGPoint(x: cardRect.midX, y: cardRect.height * 0.43)
        )
        flapPath.close()
        flapPath.fill()

        context.restoreGState()

        UIColor(red: 1.0, green: 0.58, blue: 0.02, alpha: 0.92).setStroke()
        cardPath.lineWidth = 1.5
        cardPath.stroke()

        let coinDiameter = min(CGFloat(36), max(28, cardRect.height * 0.26))
        let coinRect = CGRect(
            x: cardRect.midX - coinDiameter / 2,
            y: cardRect.minY + 18,
            width: coinDiameter,
            height: coinDiameter
        )
        UIColor(red: 1.0, green: 0.78, blue: 0.06, alpha: 1).setFill()
        UIBezierPath(ovalIn: coinRect).fill()
        UIColor(red: 1.0, green: 0.95, blue: 0.31, alpha: 0.72).setFill()
        UIBezierPath(ovalIn: coinRect.insetBy(dx: 5, dy: 5)).fill()

        let symbol = "¥" as NSString
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        symbol.draw(
            in: coinRect.insetBy(dx: 4, dy: 4),
            withAttributes: [
                .font: UIFont.systemFont(ofSize: coinDiameter * 0.54, weight: .black),
                .foregroundColor: UIColor(red: 0.86, green: 0.38, blue: 0.01, alpha: 0.88),
                .paragraphStyle: paragraph
            ]
        )
    }
}
