import UIKit

final class SidebarAvatarView: UIView {
    private let imageView = UIImageView()
    private var compactImageConstraints: [NSLayoutConstraint] = []
    private var fullImageConstraints: [NSLayoutConstraint] = []
    private var avatarLoadTask: MediaThumbnailRequest?
    private var representedAvatarURL: URL?
    private var avatarLoadGeneration = 0
    private var visibleRetryCount = 0

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
        visibleRetryCount = 0
        representedAvatarURL = nil
        imageView.image = nil
        imageView.tintColor = nil
        imageView.backgroundColor = .clear
    }

    @discardableResult
    func configure(with item: SidebarItem, cornerRadius: CGFloat? = nil) -> Bool {
        avatarLoadTask?.cancel()
        avatarLoadTask = nil
        avatarLoadGeneration &+= 1
        visibleRetryCount = 0
        representedAvatarURL = item.avatarURL

        let usesAvatarTile = usesFullAvatarTile(for: item)
        applyImageLayout(full: usesAvatarTile, cornerRadius: cornerRadius)

        let keepsSystemIcon = item.kind == .tool || item.title == "Claude AI" || item.title == "Codex"
        if keepsSystemIcon {
            imageView.image = UIImage(systemName: item.symbolName)
            imageView.tintColor = .white
            imageView.contentMode = .scaleAspectFit
            imageView.backgroundColor = .clear
        } else if !item.compositeAvatarTitles.isEmpty {
            imageView.image = UIImage.sidebarCompositeGroupAvatar(
                titles: item.compositeAvatarTitles,
                color: item.tintColor,
                size: placeholderAvatarSize()
            )
            imageView.tintColor = nil
            imageView.contentMode = .scaleAspectFill
            imageView.backgroundColor = .clear
        } else {
            imageView.image = UIImage.sidebarPlaceholderAvatar(
                title: item.title,
                color: item.tintColor,
                size: placeholderAvatarSize(),
                cornerRadius: cornerRadius
            )
            imageView.tintColor = nil
            imageView.contentMode = .scaleAspectFill
            imageView.backgroundColor = .clear
        }

        loadAvatarIfNeeded(for: item)
        return usesAvatarTile
    }

    func configureLoading() {
        avatarLoadTask?.cancel()
        avatarLoadTask = nil
        avatarLoadGeneration &+= 1
        visibleRetryCount = 0
        representedAvatarURL = nil
        applyImageLayout(full: false)
        imageView.image = UIImage(systemName: "person.crop.circle")
        imageView.tintColor = UIColor.white.withAlphaComponent(0.70)
        imageView.contentMode = .scaleAspectFit
        imageView.backgroundColor = .clear
    }

    private func loadAvatarIfNeeded(for item: SidebarItem) {
        guard item.kind != .tool,
              let avatarURL = item.avatarURL
        else { return }

        let targetSize = AvatarPipeline.defaultTargetSize
        let scale = window?.screen.scale ?? UIScreen.main.scale
        if let cached = AvatarPipeline.shared.cachedImage(
            for: avatarURL,
            targetSize: targetSize,
            scale: scale
        ) {
            imageView.image = cached
            imageView.tintColor = nil
            imageView.contentMode = .scaleAspectFill
            imageView.backgroundColor = item.tintColor.withAlphaComponent(0.28)
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
                  self.representedAvatarURL == avatarURL
            else { return }
            guard let image else {
                self.scheduleVisibleRetry(for: item, generation: generation)
                return
            }
            self.imageView.image = image
            self.imageView.tintColor = nil
            self.imageView.contentMode = .scaleAspectFill
            self.imageView.backgroundColor = item.tintColor.withAlphaComponent(0.28)
        }
    }

    private func scheduleVisibleRetry(for item: SidebarItem, generation: Int) {
        guard visibleRetryCount < 2 else { return }
        visibleRetryCount += 1
        let delay = 0.8 * Double(visibleRetryCount)
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self,
                  self.avatarLoadGeneration == generation,
                  self.representedAvatarURL == item.avatarURL,
                  self.window != nil
            else { return }
            self.loadAvatarIfNeeded(for: item)
        }
    }

    private func usesFullAvatarTile(for item: SidebarItem) -> Bool {
        guard item.kind == .session || item.kind == .account else { return false }
        return item.title != "Claude AI" && item.title != "Codex"
    }

    private func placeholderAvatarSize() -> CGSize {
        let width = max(bounds.width, 44)
        let height = max(bounds.height, 44)
        return CGSize(width: width, height: height)
    }

    private func applyImageLayout(full: Bool, cornerRadius: CGFloat? = nil) {
        NSLayoutConstraint.deactivate(full ? compactImageConstraints : fullImageConstraints)
        NSLayoutConstraint.activate(full ? fullImageConstraints : compactImageConstraints)
        imageView.layer.cornerRadius = cornerRadius ?? (full ? 14 : 12)
    }

    private func setup() {
        backgroundColor = .clear
        isUserInteractionEnabled = false

        imageView.contentMode = .scaleAspectFit
        imageView.layer.cornerRadius = 12
        imageView.layer.cornerCurve = .continuous
        imageView.clipsToBounds = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(imageView)

        compactImageConstraints = [
            imageView.centerXAnchor.constraint(equalTo: centerXAnchor),
            imageView.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            imageView.widthAnchor.constraint(equalToConstant: 24),
            imageView.heightAnchor.constraint(equalToConstant: 24)
        ]
        fullImageConstraints = [
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ]
        NSLayoutConstraint.activate(compactImageConstraints)
    }
}
