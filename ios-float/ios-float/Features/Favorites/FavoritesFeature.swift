import UIKit
@preconcurrency import AVFoundation
import AVKit

final class MusicPlayerViewController: UIViewController {
    private let titleText: String
    private let artistText: String
    private let audioURL: URL?
    private let playButton = UIButton(type: .system)
    private let progressView = UIProgressView(progressViewStyle: .default)
    private let timeLabel = UILabel()
    private let elapsedLabel = UILabel()
    private let durationLabel = UILabel()
    private let backgroundGradientLayer = CAGradientLayer()
    private let coverGradientLayer = CAGradientLayer()
    private let coverView = UIView()
    private var audioPlayer: AVAudioPlayer?
    private var timer: Timer?
    private var progress: Float = 0
    private var isPlaying = false

    init(titleText: String, artistText: String, audioURL: URL?) {
        self.titleText = titleText
        self.artistText = artistText
        self.audioURL = audioURL
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "音乐"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        preparePlayer()
        configure()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        backgroundGradientLayer.frame = view.bounds
        coverGradientLayer.frame = coverView.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        timer?.invalidate()
        audioPlayer?.stop()
    }

    private func preparePlayer() {
        guard let audioURL else { return }
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: audioURL)
            audioPlayer?.prepareToPlay()
        } catch {
            audioPlayer = nil
        }
    }

    private func configure() {
        backgroundGradientLayer.colors = [
            UIColor(red: 0.10, green: 0.15, blue: 0.17, alpha: 1).cgColor,
            UIColor(red: 0.05, green: 0.07, blue: 0.08, alpha: 1).cgColor,
            UIColor(red: 0.18, green: 0.16, blue: 0.10, alpha: 1).cgColor
        ]
        backgroundGradientLayer.startPoint = CGPoint(x: 0.2, y: 0)
        backgroundGradientLayer.endPoint = CGPoint(x: 0.9, y: 1)
        view.layer.insertSublayer(backgroundGradientLayer, at: 0)
        view.backgroundColor = UIColor(red: 0.06, green: 0.08, blue: 0.09, alpha: 1)

        let panel = UIView()
        panel.backgroundColor = UIColor.white.withAlphaComponent(0.10)
        panel.layer.cornerRadius = 28
        panel.layer.cornerCurve = .continuous
        panel.layer.borderWidth = 1
        panel.layer.borderColor = UIColor.white.withAlphaComponent(0.14).cgColor
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        panel.addSubview(stack)

        coverView.layer.cornerRadius = 26
        coverView.layer.cornerCurve = .continuous
        coverView.layer.borderWidth = 1
        coverView.layer.borderColor = UIColor.white.withAlphaComponent(0.20).cgColor
        coverView.clipsToBounds = true
        coverView.translatesAutoresizingMaskIntoConstraints = false
        coverGradientLayer.colors = [
            UIColor(red: 0.96, green: 0.76, blue: 0.32, alpha: 1).cgColor,
            UIColor(red: 0.22, green: 0.38, blue: 0.40, alpha: 1).cgColor,
            UIColor(red: 0.08, green: 0.12, blue: 0.14, alpha: 1).cgColor
        ]
        coverGradientLayer.startPoint = CGPoint(x: 0, y: 0)
        coverGradientLayer.endPoint = CGPoint(x: 1, y: 1)
        coverView.layer.insertSublayer(coverGradientLayer, at: 0)
        NSLayoutConstraint.activate([
            coverView.widthAnchor.constraint(equalToConstant: 238),
            coverView.heightAnchor.constraint(equalToConstant: 238)
        ])

        let disc = UIView()
        disc.backgroundColor = UIColor.black.withAlphaComponent(0.28)
        disc.layer.cornerRadius = 82
        disc.layer.borderWidth = 1
        disc.layer.borderColor = UIColor.white.withAlphaComponent(0.18).cgColor
        disc.translatesAutoresizingMaskIntoConstraints = false
        coverView.addSubview(disc)

        let centerDot = UIView()
        centerDot.backgroundColor = UIColor.white.withAlphaComponent(0.86)
        centerDot.layer.cornerRadius = 16
        centerDot.translatesAutoresizingMaskIntoConstraints = false
        disc.addSubview(centerDot)

        let icon = UIImageView(image: UIImage(systemName: "music.quarternote.3"))
        icon.tintColor = UIColor.white.withAlphaComponent(0.94)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        coverView.addSubview(icon)

        let badge = UILabel()
        badge.text = "只发音乐"
        badge.textColor = UIColor.white.withAlphaComponent(0.78)
        badge.font = .systemFont(ofSize: 12, weight: .semibold)
        badge.textAlignment = .center
        badge.translatesAutoresizingMaskIntoConstraints = false
        coverView.addSubview(badge)
        NSLayoutConstraint.activate([
            disc.centerXAnchor.constraint(equalTo: coverView.centerXAnchor),
            disc.centerYAnchor.constraint(equalTo: coverView.centerYAnchor),
            disc.widthAnchor.constraint(equalToConstant: 164),
            disc.heightAnchor.constraint(equalToConstant: 164),
            centerDot.centerXAnchor.constraint(equalTo: disc.centerXAnchor),
            centerDot.centerYAnchor.constraint(equalTo: disc.centerYAnchor),
            centerDot.widthAnchor.constraint(equalToConstant: 32),
            centerDot.heightAnchor.constraint(equalToConstant: 32),
            icon.centerXAnchor.constraint(equalTo: coverView.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: coverView.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 62),
            icon.heightAnchor.constraint(equalToConstant: 62),
            badge.leadingAnchor.constraint(equalTo: coverView.leadingAnchor, constant: 18),
            badge.trailingAnchor.constraint(equalTo: coverView.trailingAnchor, constant: -18),
            badge.bottomAnchor.constraint(equalTo: coverView.bottomAnchor, constant: -18)
        ])

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 24, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 2

        let artistLabel = UILabel()
        artistLabel.text = artistText
        artistLabel.font = .systemFont(ofSize: 14, weight: .medium)
        artistLabel.textColor = UIColor.white.withAlphaComponent(0.70)
        artistLabel.textAlignment = .center
        artistLabel.numberOfLines = 2

        progressView.progress = 0
        progressView.progressTintColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        progressView.trackTintColor = UIColor.white.withAlphaComponent(0.18)
        progressView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            progressView.widthAnchor.constraint(equalToConstant: 282),
            progressView.heightAnchor.constraint(equalToConstant: 4)
        ])

        updateTimeLabel()
        timeLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .medium)
        timeLabel.textColor = UIColor.white.withAlphaComponent(0.62)
        timeLabel.isHidden = true

        elapsedLabel.font = .monospacedDigitSystemFont(ofSize: 11, weight: .medium)
        elapsedLabel.textColor = UIColor.white.withAlphaComponent(0.54)
        durationLabel.font = .monospacedDigitSystemFont(ofSize: 11, weight: .medium)
        durationLabel.textColor = UIColor.white.withAlphaComponent(0.54)
        let timeRow = UIStackView(arrangedSubviews: [elapsedLabel, UIView(), durationLabel])
        timeRow.axis = .horizontal
        timeRow.alignment = .center
        timeRow.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            timeRow.widthAnchor.constraint(equalToConstant: 282)
        ])

        let controlsRow = UIStackView()
        controlsRow.axis = .horizontal
        controlsRow.alignment = .center
        controlsRow.spacing = 26

        playButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
        playButton.tintColor = .black
        playButton.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        playButton.layer.cornerRadius = 34
        playButton.layer.shadowColor = UIColor.black.withAlphaComponent(0.28).cgColor
        playButton.layer.shadowOpacity = 1
        playButton.layer.shadowRadius = 10
        playButton.layer.shadowOffset = CGSize(width: 0, height: 5)
        playButton.translatesAutoresizingMaskIntoConstraints = false
        playButton.addTarget(self, action: #selector(togglePlay), for: .touchUpInside)
        NSLayoutConstraint.activate([
            playButton.widthAnchor.constraint(equalToConstant: 68),
            playButton.heightAnchor.constraint(equalToConstant: 68)
        ])

        let previousButton = makeMusicControlButton(symbolName: "backward.fill")
        let nextButton = makeMusicControlButton(symbolName: "forward.fill")
        let favoriteButton = makeMusicControlButton(symbolName: "heart")
        [previousButton, playButton, nextButton].forEach { controlsRow.addArrangedSubview($0) }

        let sourceRow = UIStackView(arrangedSubviews: [
            makeMusicPill(symbolName: "waveform", text: audioURL == nil ? "模拟播放" : "本地音频"),
            makeMusicPill(symbolName: "square.and.arrow.up", text: "分享"),
            favoriteButton
        ])
        sourceRow.axis = .horizontal
        sourceRow.alignment = .center
        sourceRow.spacing = 10

        [coverView, titleLabel, artistLabel, progressView, timeRow, controlsRow, sourceRow].forEach { stack.addArrangedSubview($0) }
        NSLayoutConstraint.activate([
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 22),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -22),
            panel.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),

            stack.topAnchor.constraint(equalTo: panel.topAnchor, constant: 28),
            stack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -18),
            stack.bottomAnchor.constraint(equalTo: panel.bottomAnchor, constant: -24)
        ])
    }

    private func makeMusicControlButton(symbolName: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: symbolName), for: .normal)
        button.tintColor = UIColor.white.withAlphaComponent(0.86)
        button.backgroundColor = UIColor.white.withAlphaComponent(0.10)
        button.layer.cornerRadius = 22
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 44),
            button.heightAnchor.constraint(equalToConstant: 44)
        ])
        return button
    }

    private func makeMusicPill(symbolName: String, text: String) -> UIView {
        let container = UIStackView()
        container.axis = .horizontal
        container.alignment = .center
        container.spacing = 5
        container.backgroundColor = UIColor.white.withAlphaComponent(0.10)
        container.layer.cornerRadius = 14
        container.layer.cornerCurve = .continuous
        container.isLayoutMarginsRelativeArrangement = true
        container.layoutMargins = UIEdgeInsets(top: 6, left: 9, bottom: 6, right: 10)
        let icon = UIImageView(image: UIImage(systemName: symbolName))
        icon.tintColor = UIColor.white.withAlphaComponent(0.72)
        icon.contentMode = .scaleAspectFit
        icon.widthAnchor.constraint(equalToConstant: 13).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 13).isActive = true
        let label = UILabel()
        label.text = text
        label.textColor = UIColor.white.withAlphaComponent(0.72)
        label.font = .systemFont(ofSize: 11, weight: .medium)
        container.addArrangedSubview(icon)
        container.addArrangedSubview(label)
        return container
    }

    @objc private func togglePlay() {
        guard let audioPlayer else {
            simulatePlayback()
            return
        }

        if audioPlayer.isPlaying {
            audioPlayer.pause()
            isPlaying = false
            playButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
            timer?.invalidate()
        } else {
            if audioPlayer.currentTime >= audioPlayer.duration {
                audioPlayer.currentTime = 0
            }
            audioPlayer.play()
            isPlaying = true
            playButton.setImage(UIImage(systemName: "pause.fill"), for: .normal)
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
                self?.tick()
            }
        }
    }

    private func tick() {
        if let audioPlayer {
            let duration = max(audioPlayer.duration, 0.1)
            progress = Float(audioPlayer.currentTime / duration)
            progressView.progress = progress
            updateTimeLabel()
            if !audioPlayer.isPlaying || audioPlayer.currentTime >= duration {
                isPlaying = false
                playButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
                timer?.invalidate()
            }
            return
        }

        progress = min(1, progress + 0.012)
        progressView.progress = progress
        let current = Int(progress * 200)
        timeLabel.text = String(format: "%02d:%02d / 03:20", current / 60, current % 60)
        elapsedLabel.text = String(format: "%02d:%02d", current / 60, current % 60)
        durationLabel.text = "03:20"
        if progress >= 1 {
            togglePlay()
        }
    }

    private func simulatePlayback() {
        isPlaying.toggle()
        playButton.setImage(UIImage(systemName: isPlaying ? "pause.fill" : "play.fill"), for: .normal)
        if isPlaying {
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: true) { [weak self] _ in
                self?.tick()
            }
        } else {
            timer?.invalidate()
        }
    }

    private func updateTimeLabel() {
        if let audioPlayer {
            let elapsed = formatTime(audioPlayer.currentTime)
            let duration = formatTime(audioPlayer.duration)
            timeLabel.text = "\(elapsed) / \(duration)"
            elapsedLabel.text = elapsed
            durationLabel.text = duration
        } else {
            timeLabel.text = "00:00 / 03:20"
            let current = Int(progress * 200)
            elapsedLabel.text = String(format: "%02d:%02d", current / 60, current % 60)
            durationLabel.text = "03:20"
        }
    }

    private func formatTime(_ time: TimeInterval) -> String {
        let seconds = max(0, Int(time.rounded()))
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class FavoriteDetailViewController: UIViewController {
    var onOpenLink: ((String) -> Void)?

    private let titleText: String
    private let detailText: String
    private let detailTextView = UITextView()

    init(titleText: String, detailText: String) {
        self.titleText = titleText
        self.detailText = detailText
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "收藏详情"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configure()
    }

    private func configure() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.numberOfLines = 0
        titleLabel.textColor = .label
        stack.addArrangedSubview(titleLabel.wrapped(insets: UIEdgeInsets(top: 18, left: 18, bottom: 18, right: 18)))

        detailTextView.attributedText = formattedDetailText()
        detailTextView.font = .systemFont(ofSize: 15)
        detailTextView.textColor = .secondaryLabel
        detailTextView.backgroundColor = .clear
        detailTextView.isEditable = false
        detailTextView.isScrollEnabled = false
        detailTextView.dataDetectorTypes = [.link]
        detailTextView.delegate = self
        detailTextView.textContainerInset = UIEdgeInsets(top: 18, left: 18, bottom: 18, right: 18)
        detailTextView.linkTextAttributes = [
            .foregroundColor: UIColor.systemBlue,
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]
        detailTextView.layer.cornerRadius = 14
        detailTextView.layer.cornerCurve = .continuous
        detailTextView.backgroundColor = UIColor.secondarySystemGroupedBackground
        stack.addArrangedSubview(detailTextView)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            detailTextView.widthAnchor.constraint(equalTo: stack.widthAnchor)
        ])
    }

    private func formattedDetailText() -> NSAttributedString {
        let text = detailText
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "\n\n")
        let displayText = text.isEmpty ? "暂无收藏详情" : text
        let attributed = NSMutableAttributedString(
            string: displayText,
            attributes: [
                .font: UIFont.systemFont(ofSize: 15),
                .foregroundColor: UIColor.secondaryLabel,
                .paragraphStyle: {
                    let style = NSMutableParagraphStyle()
                    style.lineSpacing = 4
                    return style
                }()
            ]
        )
        let fullRange = NSRange(location: 0, length: attributed.length)
        let headingPattern = #"(?m)^[^：\n]{1,10}："#
        if let regex = try? NSRegularExpression(pattern: headingPattern) {
            regex.matches(in: displayText, range: fullRange).forEach { match in
                attributed.addAttributes(
                    [
                        .font: UIFont.systemFont(ofSize: 15, weight: .semibold),
                        .foregroundColor: UIColor.label
                    ],
                    range: match.range
                )
            }
        }
        return attributed
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

extension FavoriteDetailViewController: UITextViewDelegate {
    func textView(
        _ textView: UITextView,
        shouldInteractWith URL: URL,
        in characterRange: NSRange,
        interaction: UITextItemInteraction
    ) -> Bool {
        onOpenLink?(URL.absoluteString)
        return false
    }
}

final class FavoriteLibraryViewController: UIViewController {
    var onSelectItem: ((FavoriteShareItem) -> Void)?

    private let accountName: String
    private let items: [FavoriteShareItem]
    private var selectedCategory: FavoriteCategory = .recent
    private let searchField = UITextField()
    private let categoryScrollView = UIScrollView()
    private let categoryStackView = UIStackView()
    private let tableView = UITableView(frame: .zero, style: .plain)
    private var categoryButtons: [FavoriteCategory: UIButton] = [:]

    init(accountName: String, items: [FavoriteShareItem]) {
        self.accountName = accountName
        self.items = items
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "收藏"
        view.backgroundColor = UIColor(red: 0.93, green: 0.93, blue: 0.93, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "plus.circle"), style: .plain, target: nil, action: nil)
        configureSearch()
        configureCategories()
        configureTable()
        updateCategoryButtons()
    }

    private var visibleItems: [FavoriteShareItem] {
        let query = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return items.filter { item in
            let matchesCategory = selectedCategory == .recent
                ? item.category == .recent || item.dateText == "今天" || item.dateText == "昨天"
                : item.category == selectedCategory
            guard matchesCategory else { return false }
            guard !query.isEmpty else { return true }
            return item.title.localizedCaseInsensitiveContains(query)
                || item.detail.localizedCaseInsensitiveContains(query)
                || item.subtitle.localizedCaseInsensitiveContains(query)
        }
    }

    private func configureSearch() {
        searchField.placeholder = "搜索"
        searchField.font = .systemFont(ofSize: 17)
        searchField.textAlignment = .center
        searchField.backgroundColor = .white
        searchField.layer.cornerRadius = 12
        searchField.layer.cornerCurve = .continuous
        searchField.leftView = UIImageView(image: UIImage(systemName: "magnifyingglass"))
        searchField.leftView?.tintColor = .secondaryLabel
        searchField.leftViewMode = .unlessEditing
        searchField.addTarget(self, action: #selector(searchChanged), for: .editingChanged)
        searchField.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchField)

        NSLayoutConstraint.activate([
            searchField.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 10),
            searchField.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            searchField.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            searchField.heightAnchor.constraint(equalToConstant: 52)
        ])
    }

    private func configureCategories() {
        categoryScrollView.showsHorizontalScrollIndicator = false
        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(categoryScrollView)

        categoryStackView.axis = .horizontal
        categoryStackView.spacing = 22
        categoryStackView.alignment = .center
        categoryStackView.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.addSubview(categoryStackView)

        FavoriteCategory.allCases.forEach { category in
            let button = UIButton(type: .system)
            button.setTitle(category.shortTitle, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
            button.tag = FavoriteCategory.allCases.firstIndex(of: category) ?? 0
            button.addTarget(self, action: #selector(selectCategory(_:)), for: .touchUpInside)
            categoryButtons[category] = button
            categoryStackView.addArrangedSubview(button)
        }

        NSLayoutConstraint.activate([
            categoryScrollView.topAnchor.constraint(equalTo: searchField.bottomAnchor, constant: 18),
            categoryScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            categoryScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            categoryScrollView.heightAnchor.constraint(equalToConstant: 40),

            categoryStackView.topAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.topAnchor),
            categoryStackView.leadingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor),
            categoryStackView.trailingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor),
            categoryStackView.bottomAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor),
            categoryStackView.heightAnchor.constraint(equalTo: categoryScrollView.frameLayoutGuide.heightAnchor)
        ])
    }

    private func configureTable() {
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 142
        tableView.register(FavoriteLibraryCell.self, forCellReuseIdentifier: FavoriteLibraryCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: categoryScrollView.bottomAnchor, constant: 12),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func updateCategoryButtons() {
        categoryButtons.forEach { category, button in
            button.tintColor = category == selectedCategory
                ? UIColor(red: 0.18, green: 0.28, blue: 0.45, alpha: 1)
                : UIColor(red: 0.38, green: 0.45, blue: 0.58, alpha: 1)
            button.titleLabel?.font = .systemFont(ofSize: 16, weight: category == selectedCategory ? .bold : .semibold)
        }
    }

    @objc private func searchChanged() {
        tableView.reloadData()
    }

    @objc private func selectCategory(_ sender: UIButton) {
        let categories = FavoriteCategory.allCases
        guard categories.indices.contains(sender.tag) else { return }
        selectedCategory = categories[sender.tag]
        updateCategoryButtons()
        tableView.reloadData()
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

extension FavoriteLibraryViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        visibleItems.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: FavoriteLibraryCell.reuseIdentifier, for: indexPath) as! FavoriteLibraryCell
        cell.configure(with: visibleItems[indexPath.row], accountName: accountName)
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        onSelectItem?(visibleItems[indexPath.row])
    }
}

private final class FavoriteLibraryCell: UITableViewCell {
    static let reuseIdentifier = "FavoriteLibraryCell"

    private let cardView = UIView()
    private let titleLabel = UILabel()
    private let detailLabel = UILabel()
    private let sourceLabel = UILabel()
    private let dateLabel = UILabel()
    private let previewView = UIImageView()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configure()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configure() {
        backgroundColor = .clear
        selectionStyle = .none
        cardView.backgroundColor = .white
        cardView.layer.cornerRadius = 10
        cardView.layer.cornerCurve = .continuous
        cardView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(cardView)

        titleLabel.font = .systemFont(ofSize: 20, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(titleLabel)

        detailLabel.font = .systemFont(ofSize: 14, weight: .regular)
        detailLabel.textColor = .secondaryLabel
        detailLabel.numberOfLines = 2
        detailLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(detailLabel)

        sourceLabel.font = .systemFont(ofSize: 13, weight: .medium)
        sourceLabel.textColor = UIColor(white: 0.68, alpha: 1)
        sourceLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(sourceLabel)

        dateLabel.font = .systemFont(ofSize: 14, weight: .medium)
        dateLabel.textColor = UIColor(white: 0.64, alpha: 1)
        dateLabel.textAlignment = .right
        dateLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(dateLabel)

        previewView.contentMode = .scaleAspectFill
        previewView.clipsToBounds = true
        previewView.layer.cornerRadius = 4
        previewView.layer.cornerCurve = .continuous
        previewView.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(previewView)

        NSLayoutConstraint.activate([
            cardView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
            cardView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            cardView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            cardView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8),

            previewView.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),
            previewView.centerYAnchor.constraint(equalTo: cardView.centerYAnchor),
            previewView.widthAnchor.constraint(equalToConstant: 76),
            previewView.heightAnchor.constraint(equalToConstant: 76),

            titleLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 22),
            titleLabel.trailingAnchor.constraint(equalTo: previewView.leadingAnchor, constant: -16),

            detailLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            detailLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            sourceLabel.topAnchor.constraint(greaterThanOrEqualTo: detailLabel.bottomAnchor, constant: 20),
            sourceLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            sourceLabel.trailingAnchor.constraint(lessThanOrEqualTo: dateLabel.leadingAnchor, constant: -10),
            sourceLabel.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -22),

            dateLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),
            dateLabel.bottomAnchor.constraint(equalTo: sourceLabel.bottomAnchor),
            dateLabel.widthAnchor.constraint(equalToConstant: 112)
        ])
    }

    func configure(with item: FavoriteShareItem, accountName: String) {
        titleLabel.text = item.title
        detailLabel.text = item.detail.components(separatedBy: .newlines).prefix(2).joined(separator: "\n")
        sourceLabel.text = item.source.isEmpty ? accountName : item.source
        dateLabel.text = item.dateText
        previewView.image = previewImage(for: item)
        previewView.backgroundColor = previewBackground(for: item)
    }

    private func previewImage(for item: FavoriteShareItem) -> UIImage? {
        switch item.previewKind {
        case .image:
            return drawPreview(symbol: "photo.fill", text: "IMG", color: UIColor(red: 0.22, green: 0.48, blue: 0.84, alpha: 1))
        case .video:
            return drawPreview(symbol: "play.rectangle.fill", text: "VID", color: UIColor(red: 0.18, green: 0.26, blue: 0.55, alpha: 1))
        case .file:
            return drawPreview(symbol: "doc.text.fill", text: item.subtitle.components(separatedBy: " ").first ?? "DOC", color: UIColor(red: 0.24, green: 0.42, blue: 0.72, alpha: 1))
        case .link:
            return drawPreview(symbol: "link", text: "URL", color: UIColor(red: 0.86, green: 0.20, blue: 0.18, alpha: 1))
        case .chatRecord:
            return drawPreview(symbol: "bubble.left.and.bubble.right.fill", text: "CHAT", color: UIColor(red: 0.24, green: 0.58, blue: 0.48, alpha: 1))
        case .text:
            return drawPreview(symbol: "text.alignleft", text: "TXT", color: UIColor(red: 0.40, green: 0.42, blue: 0.48, alpha: 1))
        }
    }

    private func previewBackground(for item: FavoriteShareItem) -> UIColor {
        item.previewKind == .image ? UIColor(red: 0.92, green: 0.95, blue: 0.98, alpha: 1) : UIColor(red: 0.95, green: 0.96, blue: 0.98, alpha: 1)
    }

    private func drawPreview(symbol: String, text: String, color: UIColor) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 96, height: 96))
        return renderer.image { _ in
            color.withAlphaComponent(0.14).setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 96, height: 96), cornerRadius: 8).fill()
            color.setFill()
            UIBezierPath(roundedRect: CGRect(x: 16, y: 16, width: 64, height: 64), cornerRadius: 8).fill()
            let icon = UIImage(systemName: symbol)?.withTintColor(.white, renderingMode: .alwaysOriginal)
            icon?.draw(in: CGRect(x: 32, y: 24, width: 32, height: 32))
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 10, weight: .bold),
                .foregroundColor: UIColor.white.withAlphaComponent(0.92)
            ]
            (text as NSString).draw(in: CGRect(x: 18, y: 62, width: 60, height: 14), withAttributes: attributes)
        }
    }
}

