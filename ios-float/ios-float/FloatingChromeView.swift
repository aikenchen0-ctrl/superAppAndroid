import UIKit

final class FloatingChromeView: UIView {
    let visualEffectView = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialLight))
    let closeButton = UIButton(type: .system)
    let resizeHandle = UIView()
    private let resizeGlyph = UIImageView(image: UIImage(systemName: "arrow.up.left.and.arrow.down.right"))

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor = .clear
        layer.cornerRadius = 22
        layer.cornerCurve = .continuous
        layer.borderWidth = 1
        layer.borderColor = UIColor.white.withAlphaComponent(0.55).cgColor
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 18
        layer.shadowOffset = CGSize(width: 0, height: 12)
        clipsToBounds = false

        visualEffectView.layer.cornerRadius = 22
        visualEffectView.layer.cornerCurve = .continuous
        visualEffectView.clipsToBounds = true
        visualEffectView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(visualEffectView)

        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.22)
        closeButton.layer.cornerRadius = 18
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        resizeHandle.backgroundColor = UIColor.white.withAlphaComponent(0.24)
        resizeHandle.layer.cornerRadius = 16
        resizeHandle.layer.cornerCurve = .continuous
        resizeHandle.layer.borderWidth = 1
        resizeHandle.layer.borderColor = UIColor.white.withAlphaComponent(0.36).cgColor
        resizeHandle.translatesAutoresizingMaskIntoConstraints = false
        addSubview(resizeHandle)

        resizeGlyph.tintColor = .white
        resizeGlyph.contentMode = .scaleAspectFit
        resizeGlyph.translatesAutoresizingMaskIntoConstraints = false
        resizeHandle.addSubview(resizeGlyph)

        NSLayoutConstraint.activate([
            visualEffectView.topAnchor.constraint(equalTo: topAnchor),
            visualEffectView.leadingAnchor.constraint(equalTo: leadingAnchor),
            visualEffectView.trailingAnchor.constraint(equalTo: trailingAnchor),
            visualEffectView.bottomAnchor.constraint(equalTo: bottomAnchor),

            resizeHandle.widthAnchor.constraint(equalToConstant: 38),
            resizeHandle.heightAnchor.constraint(equalToConstant: 38),
            resizeHandle.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -10),
            resizeHandle.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -10),

            resizeGlyph.centerXAnchor.constraint(equalTo: resizeHandle.centerXAnchor),
            resizeGlyph.centerYAnchor.constraint(equalTo: resizeHandle.centerYAnchor),
            resizeGlyph.widthAnchor.constraint(equalToConstant: 18),
            resizeGlyph.heightAnchor.constraint(equalToConstant: 18)
        ])
    }
}
