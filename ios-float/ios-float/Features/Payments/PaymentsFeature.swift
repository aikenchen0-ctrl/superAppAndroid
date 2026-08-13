import UIKit

final class RedPacketOpenViewController: UIViewController {
    var onOpen: (() -> Void)?

    private let greeting: String
    private var detail: String
    private let receiverName: String
    private var alreadyReceivedAmount: String?
    private var canOpen: Bool
    private var isFinished: Bool
    private var entries: [(name: String, amount: String)]

    private let dimView = UIView()
    private let cardView = UIView()
    private let titleLabel = UILabel()
    private let greetingLabel = UILabel()
    private let amountLabel = UILabel()
    private let openButton = UIButton(type: .system)
    private let detailsStack = UIStackView()

    init(
        greeting: String,
        detail: String,
        receiverName: String,
        alreadyReceivedAmount: String?,
        canOpen: Bool,
        isFinished: Bool,
        entries: [(name: String, amount: String)]
    ) {
        self.greeting = greeting
        self.detail = detail
        self.receiverName = receiverName
        self.alreadyReceivedAmount = alreadyReceivedAmount
        self.canOpen = canOpen
        self.isFinished = isFinished
        self.entries = entries
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        buildLayout()
        renderState()
    }

    func showOpened(amount: String, detail: String, entries: [(name: String, amount: String)]) {
        self.detail = detail
        self.entries = entries
        alreadyReceivedAmount = amount
        canOpen = false
        isFinished = false
        amountLabel.text = "¥\(amount)"
        amountLabel.alpha = 0
        amountLabel.transform = CGAffineTransform(scaleX: 0.72, y: 0.72)
        renderState()
        UIView.animate(withDuration: 0.28, delay: 0, options: [.curveEaseOut]) {
            self.amountLabel.alpha = 1
            self.amountLabel.transform = .identity
            self.cardView.transform = CGAffineTransform(scaleX: 1.03, y: 1.03)
        } completion: { _ in
            UIView.animate(withDuration: 0.18) {
                self.cardView.transform = .identity
            }
        }
    }

    func showAlreadyReceived(detail: String, entries: [(name: String, amount: String)]) {
        self.detail = detail
        self.entries = entries
        canOpen = false
        renderState()
    }

    func showFinished(detail: String, entries: [(name: String, amount: String)]) {
        self.detail = detail
        self.entries = entries
        canOpen = false
        isFinished = true
        renderState()
    }

    func showRemoteState(
        detail: String,
        entries: [(name: String, amount: String)],
        alreadyReceivedAmount: String?,
        canOpen: Bool,
        isFinished: Bool
    ) {
        self.detail = detail
        self.entries = entries
        self.alreadyReceivedAmount = alreadyReceivedAmount
        self.canOpen = canOpen
        self.isFinished = isFinished
        renderState()
    }

    private func buildLayout() {
        view.backgroundColor = .clear
        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.42)
        dimView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(dimView)

        let dismissTap = UITapGestureRecognizer(target: self, action: #selector(close))
        dimView.addGestureRecognizer(dismissTap)

        cardView.backgroundColor = UIColor(red: 0.86, green: 0.14, blue: 0.08, alpha: 1)
        cardView.layer.cornerRadius = 22
        cardView.layer.cornerCurve = .continuous
        cardView.clipsToBounds = true
        cardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cardView)

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = UIColor.white.withAlphaComponent(0.86)
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(closeButton)

        titleLabel.textAlignment = .center
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        titleLabel.textColor = UIColor(red: 1.0, green: 0.84, blue: 0.36, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(titleLabel)

        greetingLabel.textAlignment = .center
        greetingLabel.font = .systemFont(ofSize: 15, weight: .medium)
        greetingLabel.textColor = UIColor.white.withAlphaComponent(0.92)
        greetingLabel.numberOfLines = 2
        greetingLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(greetingLabel)

        openButton.setTitle("開", for: .normal)
        openButton.titleLabel?.font = .systemFont(ofSize: 30, weight: .black)
        openButton.tintColor = UIColor(red: 0.66, green: 0.24, blue: 0.02, alpha: 1)
        openButton.backgroundColor = UIColor(red: 1.0, green: 0.78, blue: 0.18, alpha: 1)
        openButton.layer.cornerRadius = 34
        openButton.layer.cornerCurve = .continuous
        openButton.addTarget(self, action: #selector(openPacket), for: .touchUpInside)
        openButton.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(openButton)

        amountLabel.textAlignment = .center
        amountLabel.font = .monospacedDigitSystemFont(ofSize: 34, weight: .black)
        amountLabel.textColor = UIColor(red: 1.0, green: 0.84, blue: 0.36, alpha: 1)
        amountLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(amountLabel)

        detailsStack.axis = .vertical
        detailsStack.spacing = 7
        detailsStack.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(detailsStack)

        NSLayoutConstraint.activate([
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            cardView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            cardView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            cardView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.78),
            cardView.heightAnchor.constraint(greaterThanOrEqualToConstant: 392),

            closeButton.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 12),
            closeButton.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 12),
            closeButton.widthAnchor.constraint(equalToConstant: 34),
            closeButton.heightAnchor.constraint(equalToConstant: 34),

            titleLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 46),
            titleLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),

            greetingLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            greetingLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 24),
            greetingLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -24),

            openButton.topAnchor.constraint(equalTo: greetingLabel.bottomAnchor, constant: 28),
            openButton.centerXAnchor.constraint(equalTo: cardView.centerXAnchor),
            openButton.widthAnchor.constraint(equalToConstant: 68),
            openButton.heightAnchor.constraint(equalToConstant: 68),

            amountLabel.topAnchor.constraint(equalTo: greetingLabel.bottomAnchor, constant: 24),
            amountLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 20),
            amountLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),

            detailsStack.topAnchor.constraint(equalTo: openButton.bottomAnchor, constant: 28),
            detailsStack.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 22),
            detailsStack.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -22),
            detailsStack.bottomAnchor.constraint(lessThanOrEqualTo: cardView.bottomAnchor, constant: -22)
        ])
    }

    private func renderState() {
        titleLabel.text = isFinished ? "红包已抢完" : "\(receiverName) 的红包"
        greetingLabel.text = greeting
        amountLabel.isHidden = canOpen
        openButton.isHidden = !canOpen
        if let alreadyReceivedAmount {
            amountLabel.text = "¥\(alreadyReceivedAmount)"
        } else if isFinished {
            amountLabel.text = "已领完"
        } else {
            amountLabel.text = ""
        }
        renderEntries()
    }

    private func renderEntries() {
        detailsStack.arrangedSubviews.forEach {
            detailsStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        let header = UILabel()
        header.text = entries.isEmpty ? "暂无领取记录" : "领取明细"
        header.font = .systemFont(ofSize: 13, weight: .bold)
        header.textColor = UIColor(red: 1.0, green: 0.84, blue: 0.36, alpha: 0.96)
        detailsStack.addArrangedSubview(header)
        entries.prefix(8).forEach { entry in
            let row = UILabel()
            row.text = "\(entry.name)  领取 ¥\(entry.amount)"
            row.font = .systemFont(ofSize: 12.5, weight: .medium)
            row.textColor = UIColor.white.withAlphaComponent(0.88)
            row.numberOfLines = 1
            detailsStack.addArrangedSubview(row)
        }
    }

    @objc private func openPacket() {
        openButton.isUserInteractionEnabled = false
        UIView.animateKeyframes(withDuration: 0.82, delay: 0, options: [.calculationModeCubic]) {
            UIView.addKeyframe(withRelativeStartTime: 0, relativeDuration: 0.45) {
                self.openButton.transform = CGAffineTransform(rotationAngle: .pi).scaledBy(x: 1.12, y: 1.12)
            }
            UIView.addKeyframe(withRelativeStartTime: 0.45, relativeDuration: 0.55) {
                self.openButton.transform = CGAffineTransform(rotationAngle: .pi * 2).scaledBy(x: 0.82, y: 0.82)
                self.openButton.alpha = 0
            }
        } completion: { _ in
            self.openButton.transform = .identity
            self.openButton.alpha = 1
            self.onOpen?()
        }
    }

    @objc private func close() {
        dismiss(animated: true)
    }
}

final class WeChatPaymentConfirmationViewController: UIViewController, UITextFieldDelegate {
    var onConfirm: ((String, String) -> Void)?
    var onCancel: (() -> Void)?

    private let titleText: String
    private let amountText: String
    private let subtitleText: String
    private let confirmTitle: String
    private let methods = [
        ("零钱", "¥8,628.66", "wallet.pass.fill"),
        ("银行卡", "招商银行储蓄卡（尾号 0828）", "creditcard.fill"),
        ("经营账户", "门店收款账户", "building.columns.fill")
    ]
    private var selectedMethodIndex = 0
    private let dimView = UIView()
    private let cardView = UIView()
    private let scrollView = UIScrollView()
    private let methodStack = UIStackView()
    private let passwordField = UITextField()
    private let dotsStack = UIStackView()
    private let confirmButton = UIButton(type: .system)
    private var methodRows: [UIButton] = []
    private var methodSubtitleLabels: [UILabel] = []
    private var didConfirm = false

    init(titleText: String, amountText: String, subtitleText: String, confirmTitle: String) {
        self.titleText = titleText
        self.amountText = amountText
        self.subtitleText = subtitleText
        self.confirmTitle = confirmTitle
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureLayout()
        updateMethodRows()
        updatePasswordDots()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut]) {
            self.dimView.alpha = 1
            self.cardView.transform = .identity
        } completion: { _ in
            self.passwordField.becomeFirstResponder()
        }
    }

    func updateWalletBalance(_ text: String) {
        guard let label = methodSubtitleLabels.first else { return }
        label.text = text
    }

    private func configureLayout() {
        view.backgroundColor = .clear

        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.38)
        dimView.alpha = 0
        dimView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(dimView)
        let tap = UITapGestureRecognizer(target: self, action: #selector(cancel))
        dimView.addGestureRecognizer(tap)

        cardView.backgroundColor = UIColor.systemBackground
        cardView.layer.cornerRadius = 24
        cardView.layer.cornerCurve = .continuous
        cardView.transform = CGAffineTransform(translationX: 0, y: 340)
        cardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cardView)

        scrollView.alwaysBounceVertical = false
        scrollView.keyboardDismissMode = .none
        scrollView.showsVerticalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(scrollView)

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .secondaryLabel
        closeButton.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(closeButton)

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(titleLabel)

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitleText
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.textAlignment = .center
        subtitleLabel.numberOfLines = 2

        let amountLabel = UILabel()
        amountLabel.text = amountText
        amountLabel.font = .systemFont(ofSize: 34, weight: .bold)
        amountLabel.textAlignment = .center
        amountLabel.adjustsFontSizeToFitWidth = true
        amountLabel.minimumScaleFactor = 0.72

        methodStack.axis = .vertical
        methodStack.spacing = 8
        methodStack.translatesAutoresizingMaskIntoConstraints = false

        let passwordTitle = UILabel()
        passwordTitle.text = "支付密码"
        passwordTitle.font = .systemFont(ofSize: 14, weight: .semibold)
        passwordTitle.textColor = .secondaryLabel

        dotsStack.axis = .horizontal
        dotsStack.distribution = .fillEqually
        dotsStack.spacing = 0
        dotsStack.layer.cornerRadius = 10
        dotsStack.layer.cornerCurve = .continuous
        dotsStack.layer.borderWidth = 1
        dotsStack.layer.borderColor = UIColor.separator.cgColor
        dotsStack.clipsToBounds = true
        dotsStack.heightAnchor.constraint(equalToConstant: 46).isActive = true

        passwordField.keyboardType = .numberPad
        passwordField.textContentType = .oneTimeCode
        passwordField.tintColor = .clear
        passwordField.textColor = .clear
        passwordField.delegate = self
        passwordField.addTarget(self, action: #selector(passwordChanged), for: .editingChanged)
        passwordField.translatesAutoresizingMaskIntoConstraints = false

        confirmButton.setTitle(confirmTitle, for: .normal)
        confirmButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        confirmButton.tintColor = .white
        confirmButton.backgroundColor = UIColor(red: 0.12, green: 0.70, blue: 0.34, alpha: 1)
        confirmButton.layer.cornerRadius = 22
        confirmButton.layer.cornerCurve = .continuous
        confirmButton.addTarget(self, action: #selector(confirm), for: .touchUpInside)
        confirmButton.heightAnchor.constraint(equalToConstant: 46).isActive = true

        methods.enumerated().forEach { index, method in
            let row = makeMethodRow(index: index, title: method.0, subtitle: method.1, symbolName: method.2)
            methodRows.append(row)
            methodStack.addArrangedSubview(row)
        }
        (0..<6).forEach { index in
            dotsStack.addArrangedSubview(makePasswordBox(index: index))
        }

        let contentStack = UIStackView(arrangedSubviews: [
            subtitleLabel,
            amountLabel,
            methodStack,
            passwordTitle,
            dotsStack,
            confirmButton
        ])
        contentStack.axis = .vertical
        contentStack.spacing = 14
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        cardView.addSubview(passwordField)

        let preferredCardHeight = cardView.heightAnchor.constraint(equalToConstant: 510)
        preferredCardHeight.priority = .defaultHigh
        preferredCardHeight.isActive = true
        let preferredCardTop = cardView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
        preferredCardTop.priority = .defaultLow
        preferredCardTop.isActive = true

        NSLayoutConstraint.activate([
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            cardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            cardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            cardView.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -8),
            cardView.topAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            cardView.heightAnchor.constraint(lessThanOrEqualToConstant: 510),
            closeButton.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 18),
            closeButton.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 16),
            closeButton.widthAnchor.constraint(equalToConstant: 32),
            closeButton.heightAnchor.constraint(equalToConstant: 32),
            titleLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            titleLabel.centerXAnchor.constraint(equalTo: cardView.centerXAnchor),
            titleLabel.leadingAnchor.constraint(greaterThanOrEqualTo: closeButton.trailingAnchor, constant: 12),
            scrollView.topAnchor.constraint(equalTo: closeButton.bottomAnchor, constant: 8),
            scrollView.leadingAnchor.constraint(equalTo: cardView.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: cardView.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -12),
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 4),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 22),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -22),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -6),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -44),
            passwordField.widthAnchor.constraint(equalToConstant: 1),
            passwordField.heightAnchor.constraint(equalToConstant: 1),
            passwordField.leadingAnchor.constraint(equalTo: cardView.leadingAnchor),
            passwordField.bottomAnchor.constraint(equalTo: cardView.bottomAnchor)
        ])
    }

    private func makeMethodRow(index: Int, title: String, subtitle: String, symbolName: String) -> UIButton {
        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: symbolName)
        configuration.imagePadding = 10
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12)
        let button = UIButton(type: .system)
        button.configuration = configuration
        button.tag = index
        button.tintColor = UIColor(red: 0.12, green: 0.47, blue: 0.32, alpha: 1)
        button.backgroundColor = UIColor.secondarySystemGroupedBackground
        button.layer.cornerRadius = 14
        button.layer.cornerCurve = .continuous
        button.contentHorizontalAlignment = .leading
        button.addTarget(self, action: #selector(selectMethod(_:)), for: .touchUpInside)
        button.heightAnchor.constraint(equalToConstant: 54).isActive = true

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        methodSubtitleLabels.append(subtitleLabel)
        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 2
        textStack.isUserInteractionEnabled = false
        textStack.translatesAutoresizingMaskIntoConstraints = false
        button.addSubview(textStack)

        let check = UIImageView(image: UIImage(systemName: "checkmark.circle.fill"))
        check.tag = 901
        check.tintColor = UIColor(red: 0.12, green: 0.70, blue: 0.34, alpha: 1)
        check.translatesAutoresizingMaskIntoConstraints = false
        button.addSubview(check)

        NSLayoutConstraint.activate([
            textStack.leadingAnchor.constraint(equalTo: button.leadingAnchor, constant: 52),
            textStack.centerYAnchor.constraint(equalTo: button.centerYAnchor),
            textStack.trailingAnchor.constraint(lessThanOrEqualTo: check.leadingAnchor, constant: -10),
            check.trailingAnchor.constraint(equalTo: button.trailingAnchor, constant: -12),
            check.centerYAnchor.constraint(equalTo: button.centerYAnchor),
            check.widthAnchor.constraint(equalToConstant: 20),
            check.heightAnchor.constraint(equalToConstant: 20)
        ])
        return button
    }

    private func makePasswordBox(index: Int) -> UIView {
        let box = UIView()
        box.backgroundColor = UIColor.systemBackground
        let dot = UIView()
        dot.tag = 700
        dot.backgroundColor = .label
        dot.layer.cornerRadius = 5
        dot.isHidden = true
        dot.translatesAutoresizingMaskIntoConstraints = false
        box.addSubview(dot)
        if index < 5 {
            let divider = UIView()
            divider.backgroundColor = UIColor.separator
            divider.translatesAutoresizingMaskIntoConstraints = false
            box.addSubview(divider)
            NSLayoutConstraint.activate([
                divider.trailingAnchor.constraint(equalTo: box.trailingAnchor),
                divider.topAnchor.constraint(equalTo: box.topAnchor),
                divider.bottomAnchor.constraint(equalTo: box.bottomAnchor),
                divider.widthAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
            ])
        }
        NSLayoutConstraint.activate([
            dot.centerXAnchor.constraint(equalTo: box.centerXAnchor),
            dot.centerYAnchor.constraint(equalTo: box.centerYAnchor),
            dot.widthAnchor.constraint(equalToConstant: 10),
            dot.heightAnchor.constraint(equalToConstant: 10)
        ])
        return box
    }

    @objc private func selectMethod(_ sender: UIButton) {
        selectedMethodIndex = sender.tag
        updateMethodRows()
    }

    private func updateMethodRows() {
        methodRows.enumerated().forEach { index, row in
            row.layer.borderWidth = index == selectedMethodIndex ? 1.5 : 0
            row.layer.borderColor = UIColor(red: 0.12, green: 0.70, blue: 0.34, alpha: 1).cgColor
            row.viewWithTag(901)?.isHidden = index != selectedMethodIndex
        }
    }

    @objc private func passwordChanged() {
        let digits = passwordField.text?.filter(\.isNumber).prefix(6) ?? ""
        let sanitized = String(digits)
        if passwordField.text != sanitized {
            passwordField.text = sanitized
        }
        updatePasswordDots()
    }

    private func updatePasswordDots() {
        let count = passwordField.text?.count ?? 0
        dotsStack.arrangedSubviews.enumerated().forEach { index, box in
            box.viewWithTag(700)?.isHidden = index >= count
        }
        confirmButton.alpha = count == 6 ? 1 : 0.52
    }

    func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
        let current = textField.text ?? ""
        guard let textRange = Range(range, in: current) else { return false }
        let next = current.replacingCharacters(in: textRange, with: string).filter(\.isNumber)
        return next.count <= 6
    }

    @objc private func confirm() {
        guard passwordField.text?.count == 6 else {
            shakePasswordBoxes()
            return
        }
        didConfirm = true
        confirmButton.isEnabled = false
        confirmButton.setTitle("支付中...", for: .normal)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            self.onConfirm?(self.methods[self.selectedMethodIndex].0, self.passwordField.text ?? "")
        }
    }

    private func shakePasswordBoxes() {
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.x")
        animation.values = [-8, 8, -6, 6, 0]
        animation.duration = 0.22
        dotsStack.layer.add(animation, forKey: "shake")
    }

    @objc private func cancel() {
        guard !didConfirm else { return }
        passwordField.resignFirstResponder()
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseIn]) {
            self.dimView.alpha = 0
            self.cardView.transform = CGAffineTransform(translationX: 0, y: 340)
        } completion: { _ in
            self.onCancel?()
            self.dismiss(animated: false)
        }
    }
}

final class TransferReceiveViewController: UIViewController {
    var onConfirm: (() -> Void)?

    private let amountText: String
    private let receiverName: String
    private let noteText: String?
    private let isReceived: Bool
    private let canConfirm: Bool
    private let dimView = UIView()
    private let cardView = UIView()

    init(amountText: String, receiverName: String, noteText: String?, isReceived: Bool, canConfirm: Bool) {
        self.amountText = amountText
        self.receiverName = receiverName
        self.noteText = noteText
        self.isReceived = isReceived
        self.canConfirm = canConfirm
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureLayout()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut]) {
            self.dimView.alpha = 1
            self.cardView.transform = .identity
        }
    }

    private func configureLayout() {
        view.backgroundColor = .clear

        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.34)
        dimView.alpha = 0
        dimView.translatesAutoresizingMaskIntoConstraints = false
        dimView.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(close)))
        view.addSubview(dimView)

        cardView.backgroundColor = UIColor.systemBackground
        cardView.layer.cornerRadius = 24
        cardView.layer.cornerCurve = .continuous
        cardView.transform = CGAffineTransform(translationX: 0, y: 300)
        cardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cardView)

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .secondaryLabel
        closeButton.addTarget(self, action: #selector(close), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = isReceived ? "转账已收款" : "转账待收款"
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let iconHost = UIView()
        iconHost.backgroundColor = UIColor(red: 0.95, green: 0.73, blue: 0.28, alpha: 0.18)
        iconHost.layer.cornerRadius = 28
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: isReceived ? "checkmark.circle.fill" : "arrow.down.circle.fill"))
        icon.tintColor = isReceived
            ? UIColor(red: 0.45, green: 0.50, blue: 0.52, alpha: 1)
            : UIColor(red: 0.91, green: 0.55, blue: 0.12, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let amountLabel = UILabel()
        amountLabel.text = amountText
        amountLabel.font = .systemFont(ofSize: 38, weight: .bold)
        amountLabel.textAlignment = .center
        amountLabel.textColor = isReceived ? .secondaryLabel : .label
        amountLabel.adjustsFontSizeToFitWidth = true
        amountLabel.minimumScaleFactor = 0.72

        let receiverLabel = UILabel()
        receiverLabel.text = "收款人：\(receiverName)"
        receiverLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        receiverLabel.textColor = UIColor(red: 0.18, green: 0.21, blue: 0.24, alpha: 1)
        receiverLabel.textAlignment = .center
        receiverLabel.numberOfLines = 1

        let stateLabel = UILabel()
        if isReceived {
            stateLabel.text = "这笔转账已经完成收款"
        } else if canConfirm {
            stateLabel.text = "确认后金额将进入当前账号"
        } else {
            stateLabel.text = "只有收款人可以确认这笔转账"
        }
        stateLabel.font = .systemFont(ofSize: 13, weight: .medium)
        stateLabel.textColor = .secondaryLabel
        stateLabel.textAlignment = .center
        stateLabel.numberOfLines = 2

        let noteLabel = UILabel()
        noteLabel.text = noteText.map { "备注：\($0)" } ?? "无备注"
        noteLabel.font = .systemFont(ofSize: 13, weight: .medium)
        noteLabel.textColor = .secondaryLabel
        noteLabel.textAlignment = .center
        noteLabel.numberOfLines = 2
        noteLabel.backgroundColor = UIColor.secondarySystemGroupedBackground
        noteLabel.layer.cornerRadius = 12
        noteLabel.layer.cornerCurve = .continuous
        noteLabel.clipsToBounds = true
        noteLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 42).isActive = true

        let confirmButton = UIButton(type: .system)
        confirmButton.setTitle(buttonTitle, for: .normal)
        confirmButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        confirmButton.tintColor = .white
        confirmButton.backgroundColor = canConfirm
            ? UIColor(red: 0.12, green: 0.70, blue: 0.34, alpha: 1)
            : UIColor(red: 0.64, green: 0.67, blue: 0.69, alpha: 1)
        confirmButton.layer.cornerRadius = 22
        confirmButton.layer.cornerCurve = .continuous
        confirmButton.isEnabled = canConfirm
        confirmButton.addTarget(self, action: #selector(confirm), for: .touchUpInside)
        confirmButton.heightAnchor.constraint(equalToConstant: 46).isActive = true

        let contentStack = UIStackView(arrangedSubviews: [
            iconHost,
            amountLabel,
            receiverLabel,
            stateLabel,
            noteLabel,
            confirmButton
        ])
        contentStack.axis = .vertical
        contentStack.alignment = .fill
        contentStack.spacing = 13
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        cardView.addSubview(closeButton)
        cardView.addSubview(titleLabel)
        cardView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            cardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            cardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            cardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            closeButton.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 18),
            closeButton.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 16),
            closeButton.widthAnchor.constraint(equalToConstant: 32),
            closeButton.heightAnchor.constraint(equalToConstant: 32),
            titleLabel.centerXAnchor.constraint(equalTo: cardView.centerXAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            contentStack.topAnchor.constraint(equalTo: closeButton.bottomAnchor, constant: 14),
            contentStack.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 24),
            contentStack.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -24),
            contentStack.bottomAnchor.constraint(equalTo: cardView.safeAreaLayoutGuide.bottomAnchor, constant: -20),
            iconHost.widthAnchor.constraint(equalToConstant: 56),
            iconHost.heightAnchor.constraint(equalToConstant: 56),
            iconHost.centerXAnchor.constraint(equalTo: contentStack.centerXAnchor),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 30),
            icon.heightAnchor.constraint(equalToConstant: 30)
        ])
    }

    private var buttonTitle: String {
        if isReceived { return "已收款" }
        if canConfirm { return "确认收款" }
        return "非收款人"
    }

    @objc private func confirm() {
        onConfirm?()
    }

    @objc private func close() {
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseIn]) {
            self.dimView.alpha = 0
            self.cardView.transform = CGAffineTransform(translationX: 0, y: 300)
        } completion: { _ in
            self.dismiss(animated: false)
        }
    }
}

final class RedPacketComposerViewController: UIViewController {
    var onSend: ((String, String, Int) -> Void)?

    private let conversationName: String
    private let maxPacketCount: Int
    private let amountField = UITextField()
    private let greetingField = UITextField()
    private let countStepper = UIStepper()
    private let countLabel = UILabel()
    private let sendButton = UIButton(type: .system)

    init(conversationName: String, maxPacketCount: Int) {
        self.conversationName = conversationName
        self.maxPacketCount = max(1, maxPacketCount)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "发红包"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        configureLayout()
    }

    private func configureLayout() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        let header = makeInfoLabel("发送到 \(conversationName)。群红包最多可设置 \(maxPacketCount) 个。")
        stack.addArrangedSubview(header)
        configureTextField(amountField, placeholder: "金额（可不填，默认 ¥66.00）", text: "", keyboardType: .decimalPad)
        configureTextField(greetingField, placeholder: "祝福语（可不填，默认恭喜发财，大吉大利）", text: "", keyboardType: .default)
        stack.addArrangedSubview(amountField)
        stack.addArrangedSubview(greetingField)

        let countRow = UIStackView()
        countRow.axis = .horizontal
        countRow.alignment = .center
        countRow.spacing = 12
        countLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        countLabel.textColor = .label
        countStepper.minimumValue = 1
        countStepper.maximumValue = Double(maxPacketCount)
        countStepper.value = 1
        countStepper.addTarget(self, action: #selector(updateCountLabel), for: .valueChanged)
        countRow.addArrangedSubview(countLabel)
        countRow.addArrangedSubview(UIView())
        countRow.addArrangedSubview(countStepper)
        countRow.backgroundColor = UIColor.secondarySystemGroupedBackground
        countRow.layer.cornerRadius = 14
        countRow.layer.cornerCurve = .continuous
        countRow.layoutMargins = UIEdgeInsets(top: 12, left: 14, bottom: 12, right: 14)
        countRow.isLayoutMarginsRelativeArrangement = true
        stack.addArrangedSubview(countRow)
        updateCountLabel()

        sendButton.setTitle("塞钱进红包", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor.systemRed
        sendButton.layer.cornerRadius = 22
        sendButton.layer.cornerCurve = .continuous
        sendButton.heightAnchor.constraint(equalToConstant: 46).isActive = true
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        stack.addArrangedSubview(sendButton)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 18),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18)
        ])
    }

    private func configureTextField(_ field: UITextField, placeholder: String, text: String, keyboardType: UIKeyboardType) {
        field.placeholder = placeholder
        field.text = text
        field.keyboardType = keyboardType
        field.borderStyle = .none
        field.backgroundColor = UIColor.secondarySystemGroupedBackground
        field.layer.cornerRadius = 14
        field.layer.cornerCurve = .continuous
        field.font = .systemFont(ofSize: 16)
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 48).isActive = true
    }

    private func makeInfoLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 13, weight: .medium)
        label.textColor = .secondaryLabel
        label.numberOfLines = 0
        return label
    }

    @objc private func updateCountLabel() {
        countLabel.text = "红包个数 \(Int(countStepper.value))/\(maxPacketCount)"
    }

    @objc private func send() {
        view.endEditing(true)
        onSend?(
            amountField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            greetingField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            Int(countStepper.value)
        )
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

final class TransferComposerViewController: UIViewController {
    var onSend: ((String, String) -> Void)?

    private let receiverName: String
    private let amountField = UITextField()
    private let noteField = UITextField()
    private let sendButton = UIButton(type: .system)

    init(receiverName: String) {
        self.receiverName = receiverName
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "转账"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        configureLayout()
    }

    private func configureLayout() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        let titleLabel = UILabel()
        titleLabel.text = "转账给 \(receiverName)"
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.textColor = .label
        stack.addArrangedSubview(titleLabel)
        configureTextField(amountField, placeholder: "金额（可不填，默认 ¥88.00）", text: "", keyboardType: .decimalPad)
        configureTextField(noteField, placeholder: "备注（可不填）", text: "", keyboardType: .default)
        stack.addArrangedSubview(amountField)
        stack.addArrangedSubview(noteField)

        sendButton.setTitle("转账", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor.systemOrange
        sendButton.layer.cornerRadius = 22
        sendButton.layer.cornerCurve = .continuous
        sendButton.heightAnchor.constraint(equalToConstant: 46).isActive = true
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        stack.addArrangedSubview(sendButton)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18)
        ])
    }

    private func configureTextField(_ field: UITextField, placeholder: String, text: String, keyboardType: UIKeyboardType) {
        field.placeholder = placeholder
        field.text = text
        field.keyboardType = keyboardType
        field.backgroundColor = UIColor.secondarySystemGroupedBackground
        field.layer.cornerRadius = 14
        field.layer.cornerCurve = .continuous
        field.font = .systemFont(ofSize: 16)
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 48).isActive = true
    }

    @objc private func send() {
        view.endEditing(true)
        onSend?(
            amountField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            noteField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        )
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

final class SplitBillComposerViewController: UIViewController {
    var onSend: ((String) -> Void)?

    private let groupName: String
    private let selectedMembers: [ChatParticipant]
    private let amountField = UITextField()
    private let sendButton = UIButton(type: .system)

    init(groupName: String, selectedMembers: [ChatParticipant]) {
        self.groupName = groupName
        self.selectedMembers = selectedMembers
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "群收款"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        configureLayout()
    }

    private func configureLayout() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        let summary = UILabel()
        summary.text = "\(groupName)\n已选择 \(selectedMembers.count) 人：\(selectedMembers.map(\.displayName).joined(separator: "、"))"
        summary.font = .systemFont(ofSize: 14, weight: .medium)
        summary.textColor = .secondaryLabel
        summary.numberOfLines = 0
        stack.addArrangedSubview(summary)

        amountField.placeholder = "总金额（可不填，默认 ¥320.00）"
        amountField.text = ""
        amountField.keyboardType = .decimalPad
        amountField.backgroundColor = UIColor.secondarySystemGroupedBackground
        amountField.layer.cornerRadius = 14
        amountField.layer.cornerCurve = .continuous
        amountField.font = .systemFont(ofSize: 16)
        amountField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 1))
        amountField.leftViewMode = .always
        amountField.heightAnchor.constraint(equalToConstant: 48).isActive = true
        stack.addArrangedSubview(amountField)

        sendButton.setTitle("发起收款", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor.systemGreen
        sendButton.layer.cornerRadius = 22
        sendButton.layer.cornerCurve = .continuous
        sendButton.heightAnchor.constraint(equalToConstant: 46).isActive = true
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        stack.addArrangedSubview(sendButton)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18)
        ])
    }

    @objc private func send() {
        view.endEditing(true)
        onSend?(amountField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "")
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

final class PaymentMemberSelectionViewController: UIViewController {
    var onConfirm: (([ChatParticipant]) -> Void)?

    private let titleText: String
    private let subtitleText: String
    private let members: [ChatParticipant]
    private let allowsMultipleSelection: Bool
    private var selectedIDs: Set<UUID>
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let confirmButton = UIButton(type: .system)

    init(
        titleText: String,
        subtitleText: String,
        members: [ChatParticipant],
        allowsMultipleSelection: Bool,
        selectedIDs: Set<UUID>
    ) {
        self.titleText = titleText
        self.subtitleText = subtitleText
        self.members = members
        self.allowsMultipleSelection = allowsMultipleSelection
        self.selectedIDs = selectedIDs
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = titleText
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        configureTable()
        configureConfirmButton()
        updateConfirmButton()
    }

    private func configureTable() {
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "MemberCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
    }

    private func configureConfirmButton() {
        confirmButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        confirmButton.tintColor = .white
        confirmButton.backgroundColor = UIColor.systemGreen
        confirmButton.layer.cornerRadius = 22
        confirmButton.layer.cornerCurve = .continuous
        confirmButton.addTarget(self, action: #selector(confirm), for: .touchUpInside)
        confirmButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(confirmButton)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: confirmButton.topAnchor, constant: -12),
            confirmButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            confirmButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            confirmButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -14),
            confirmButton.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    private func updateConfirmButton() {
        let count = selectedIDs.count
        confirmButton.setTitle(allowsMultipleSelection ? "下一步（\(count)）" : "下一步", for: .normal)
        confirmButton.isEnabled = count > 0
        confirmButton.alpha = count > 0 ? 1 : 0.45
    }

    @objc private func confirm() {
        let selectedMembers = members.filter { selectedIDs.contains($0.id) }
        guard !selectedMembers.isEmpty else { return }
        onConfirm?(selectedMembers)
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

extension PaymentMemberSelectionViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        allowsMultipleSelection ? 2 : 1
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if allowsMultipleSelection, section == 0 { return 1 }
        return members.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        if allowsMultipleSelection, section == 0 { return subtitleText }
        return allowsMultipleSelection ? "成员" : subtitleText
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "MemberCell", for: indexPath)
        if allowsMultipleSelection, indexPath.section == 0 {
            var configuration = UIListContentConfiguration.cell()
            let isAllSelected = selectedIDs.count == members.count
            configuration.text = isAllSelected ? "取消全选" : "全选"
            configuration.image = UIImage(systemName: isAllSelected ? "checkmark.circle.fill" : "circle")
            configuration.imageProperties.tintColor = UIColor.systemGreen
            cell.contentConfiguration = configuration
            cell.accessoryType = .none
            return cell
        }

        let member = members[indexPath.row]
        let isSelected = selectedIDs.contains(member.id)
        var configuration = UIListContentConfiguration.subtitleCell()
        configuration.image = UIImage(systemName: "person.crop.circle.fill")
        configuration.imageProperties.tintColor = member.tintColor
        configuration.text = member.displayName
        configuration.secondaryText = allowsMultipleSelection ? "群成员" : "收款人"
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = configuration
        cell.accessoryType = isSelected ? .checkmark : .none
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if allowsMultipleSelection, indexPath.section == 0 {
            selectedIDs = selectedIDs.count == members.count ? [] : Set(members.map(\.id))
        } else {
            let member = members[indexPath.row]
            if allowsMultipleSelection {
                if selectedIDs.contains(member.id) {
                    selectedIDs.remove(member.id)
                } else {
                    selectedIDs.insert(member.id)
                }
            } else {
                selectedIDs = [member.id]
            }
        }
        tableView.reloadData()
        updateConfirmButton()
    }
}

private final class GradientTextLabel: UILabel {
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
        gradientLayer.mask = textMaskLayer()
    }

    private func setup() {
        textColor = .clear
        gradientLayer.colors = [
            UIColor(red: 0.10, green: 0.64, blue: 0.38, alpha: 1).cgColor,
            UIColor(red: 0.28, green: 0.78, blue: 0.23, alpha: 1).cgColor,
            UIColor(red: 0.95, green: 0.70, blue: 0.20, alpha: 1).cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0.5)
        gradientLayer.endPoint = CGPoint(x: 1, y: 0.5)
        layer.addSublayer(gradientLayer)
    }

    private func textMaskLayer() -> CALayer {
        let renderer = UIGraphicsImageRenderer(size: bounds.size)
        let image = renderer.image { _ in
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = textAlignment
            (text ?? "").draw(
                in: bounds,
                withAttributes: [
                    .font: font as Any,
                    .paragraphStyle: paragraph
                ]
            )
        }
        let mask = CALayer()
        mask.contents = image.cgImage
        mask.frame = bounds
        return mask
    }
}

private final class SplitBillSummaryCell: UITableViewCell {
    static let reuseIdentifier = "SplitBillSummaryCell"

    private let amountLabel = GradientTextLabel()
    private let perPersonLabel = UILabel()
    private let summaryLabel = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(amountText: String, participantCount: Int, summaryText: String) {
        amountLabel.text = amountText
        let amount = Double(amountText.replacingOccurrences(of: "¥", with: "")) ?? 0
        let perPerson = participantCount > 0 ? amount / Double(participantCount) : amount
        perPersonLabel.text = "共 \(participantCount) 人 · 人均 ¥\(String(format: "%.2f", perPerson))"
        summaryLabel.text = summaryText
        amountLabel.setNeedsLayout()
    }

    private func setup() {
        backgroundColor = .clear
        contentView.backgroundColor = .secondarySystemGroupedBackground
        contentView.layer.cornerRadius = 16
        contentView.layer.cornerCurve = .continuous
        contentView.clipsToBounds = true

        amountLabel.font = .systemFont(ofSize: 34, weight: .heavy)
        amountLabel.textAlignment = .center
        amountLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(amountLabel)

        perPersonLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        perPersonLabel.textColor = .secondaryLabel
        perPersonLabel.textAlignment = .center
        perPersonLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(perPersonLabel)

        summaryLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        summaryLabel.textColor = .secondaryLabel
        summaryLabel.numberOfLines = 0
        summaryLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(summaryLabel)

        NSLayoutConstraint.activate([
            amountLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 20),
            amountLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            amountLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            amountLabel.heightAnchor.constraint(equalToConstant: 42),
            perPersonLabel.topAnchor.constraint(equalTo: amountLabel.bottomAnchor, constant: 6),
            perPersonLabel.leadingAnchor.constraint(equalTo: amountLabel.leadingAnchor),
            perPersonLabel.trailingAnchor.constraint(equalTo: amountLabel.trailingAnchor),
            summaryLabel.topAnchor.constraint(equalTo: perPersonLabel.bottomAnchor, constant: 14),
            summaryLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            summaryLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            summaryLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -18)
        ])
    }
}

final class SplitBillDetailViewController: UIViewController {
    var onPay: ((@escaping (Bool) -> Void) -> Void)?

    private let amountText: String
    private let summaryText: String
    private let participants: [ChatParticipant]
    private let currentUserID: UUID
    private var currentUserHasPaid: Bool
    private let payButtonTitle: String?
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let payButton = UIButton(type: .system)

    init(
        amountText: String,
        summaryText: String,
        participants: [ChatParticipant],
        currentUserID: UUID,
        currentUserHasPaid: Bool,
        payButtonTitle: String? = nil
    ) {
        self.amountText = amountText
        self.summaryText = summaryText
        self.participants = participants
        self.currentUserID = currentUserID
        self.currentUserHasPaid = currentUserHasPaid
        self.payButtonTitle = payButtonTitle
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "群收款"
        view.backgroundColor = UIColor.systemGroupedBackground
        configureTableView()
        configurePayButton()
        updatePayButton()
    }

    private func configureTableView() {
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "SplitBillCell")
        tableView.register(SplitBillSummaryCell.self, forCellReuseIdentifier: SplitBillSummaryCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
    }

    private func configurePayButton() {
        payButton.setTitle("付款", for: .normal)
        payButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        payButton.tintColor = .white
        payButton.backgroundColor = UIColor.systemGreen
        payButton.layer.cornerRadius = 22
        payButton.layer.cornerCurve = .continuous
        payButton.addTarget(self, action: #selector(payNow), for: .touchUpInside)
        payButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(payButton)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: payButton.topAnchor, constant: -12),
            payButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            payButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            payButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -14),
            payButton.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    private func isPaid(_ participant: ChatParticipant) -> Bool {
        participant.id == currentUserID ? currentUserHasPaid : participant.displayName.contains("李") || participant.displayName.contains("陈")
    }

    private func updatePayButton() {
        payButton.isEnabled = !currentUserHasPaid
        payButton.alpha = currentUserHasPaid ? 0.45 : 1
        payButton.setTitle(payButtonTitle ?? (currentUserHasPaid ? "已付款" : "付款"), for: .normal)
    }

    @objc private func payNow() {
        guard !currentUserHasPaid else { return }
        payButton.isEnabled = false
        payButton.setTitle("确认中...", for: .normal)
        onPay? { [weak self] success in
            guard let self else { return }
            self.currentUserHasPaid = success
            self.updatePayButton()
            self.tableView.reloadData()
        }
    }
}

extension SplitBillDetailViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        2
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? 1 : participants.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? nil : "收款成员"
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let cell = tableView.dequeueReusableCell(
                withIdentifier: SplitBillSummaryCell.reuseIdentifier,
                for: indexPath
            ) as! SplitBillSummaryCell
            cell.configure(amountText: amountText, participantCount: participants.count, summaryText: summaryText)
            cell.selectionStyle = .none
            cell.accessoryType = .none
            return cell
        }

        let cell = tableView.dequeueReusableCell(withIdentifier: "SplitBillCell", for: indexPath)
        let participant = participants[indexPath.row]
        let paid = isPaid(participant)
        var configuration = UIListContentConfiguration.subtitleCell()
        configuration.image = UIImage(systemName: "person.crop.circle.fill")
        configuration.imageProperties.tintColor = participant.tintColor
        configuration.text = participant.id == currentUserID ? "\(participant.displayName)（我）" : participant.displayName
        configuration.secondaryText = paid ? "已支付" : "待支付"
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = paid ? UIColor.systemGreen : UIColor.secondaryLabel
        cell.contentConfiguration = configuration
        cell.accessoryType = paid ? .checkmark : .none
        cell.selectionStyle = .none
        return cell
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        indexPath.section == 0 ? UITableView.automaticDimension : 56
    }

    func tableView(_ tableView: UITableView, estimatedHeightForRowAt indexPath: IndexPath) -> CGFloat {
        indexPath.section == 0 ? 176 : 56
    }
}

final class OpenApiPaymentStatusViewController: OpenApiRowsViewController {
    private let accountName: String
    private let weChatId: String
    private var taskSeed: Int64 = 860120

    init(accountName: String, weChatId: String) {
        self.accountName = accountName
        self.weChatId = weChatId
        super.init(nibName: nil, bundle: nil)
        title = "支付真实状态"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "刷新", style: .plain, target: self, action: #selector(refreshStatus)),
            UIBarButtonItem(title: "环境", style: .plain, target: self, action: #selector(openRuntimeEnvironment))
        ]
        rebuildSections(action: "red-packets/status-by-message", message: "红包状态查询完成")
    }

    @objc private func refreshStatus() {
        taskSeed += 1
        rebuildSections(action: taskSeed.isMultiple(of: 2) ? "transfers/take-by-message" : "red-packets/detail-by-message", message: "模拟任务已更新")
    }

    private func rebuildSections(action: String, message: String) {
        sections = [
            (
                title: "OpenAPI TaskResult",
                rows: OpenApiMockClient.envelope(endpoint: "/openapi/v1/payments/\(action)", message: message) + [
                    OpenApiFieldRow(key: "taskId", value: "\(taskSeed)", subtitle: "integer(int64)，任务 ID，用于查询异步任务结果。"),
                    OpenApiFieldRow(key: "success", value: "true", subtitle: "boolean，是否成功。"),
                    OpenApiFieldRow(key: "message", value: message, subtitle: "string，验证消息、备注消息或操作说明。"),
                    OpenApiFieldRow(key: "taskResultUrl", value: "/openapi/v1/tasks/\(taskSeed)", subtitle: "任务结果查询地址。"),
                    OpenApiFieldRow(key: "recentTaskResultsUrl", value: "/openapi/v1/tasks/recent", subtitle: "最近任务结果查询地址。")
                ]
            ),
            (
                title: "data",
                rows: [
                    OpenApiFieldRow(key: "accountName", value: accountName, subtitle: "本地模拟字段，用于展示当前执行帐号。"),
                    OpenApiFieldRow(key: "weChatId", value: weChatId, subtitle: "请求字段：微信账号标识/微信 ID。"),
                    OpenApiFieldRow(key: "action", value: "/openapi/v1/payments/\(action)", subtitle: "支付状态模拟接口。"),
                    OpenApiFieldRow(key: "amount", value: "¥128.00", subtitle: "模拟红包/转账金额。"),
                    OpenApiFieldRow(key: "state", value: "WAITING_RECEIVE -> RECEIVED", subtitle: "保留真实接口返回前的本地状态。")
                ]
            )
        ]
        tableView.reloadData()
    }
}
