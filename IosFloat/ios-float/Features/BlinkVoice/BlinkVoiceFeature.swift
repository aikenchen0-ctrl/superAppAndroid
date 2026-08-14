import UIKit
import BlinkVoiceKit

final class BlinkVoiceKitTestViewController: UIViewController {
    private let session = BlinkVoiceSession()
    private var isRunning = false
    private var eventCount = 0
    private var latestSample: BlinkSample?

    private let statusPill = UILabel()
    private let stateValueLabel = UILabel()
    private let eventValueLabel = UILabel()
    private let countValueLabel = UILabel()
    private let leftScoreValueLabel = UILabel()
    private let rightScoreValueLabel = UILabel()
    private let averageScoreValueLabel = UILabel()
    private let faceValueLabel = UILabel()
    private let startResultValueLabel = UILabel()
    private let errorLabel = UILabel()
    private let actionButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        configureSessionCallbacks()
        configureView()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !isRunning {
            startSession()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopSession()
    }

    private func configureSessionCallbacks() {
        session.onStateChanged = { [weak self] state in
            DispatchQueue.main.async {
                self?.stateValueLabel.text = state.rawValue
                self?.updateStatusPill()
            }
        }
        session.onEvent = { [weak self] event in
            DispatchQueue.main.async {
                guard let self else { return }
                self.eventCount += 1
                self.eventValueLabel.text = self.displayName(for: event)
                self.countValueLabel.text = "\(self.eventCount)"
                self.updateStatusPill()
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
        }
        session.onSample = { [weak self] sample in
            DispatchQueue.main.async {
                self?.latestSample = sample
                self?.render(sample)
            }
        }
        session.onError = { [weak self] error in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isRunning = false
                self.errorLabel.text = self.message(for: error)
                self.errorLabel.isHidden = false
                self.actionButton.setTitle("重新开始", for: .normal)
                self.updateStatusPill()
            }
        }
    }

    private func configureView() {
        title = "BlinkVoiceKit 测试"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(closeTapped))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "设置", style: .plain, target: self, action: #selector(openSettingsTapped))

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)

        let header = UIView()
        header.backgroundColor = .secondarySystemGroupedBackground
        header.layer.cornerRadius = 18
        header.layer.cornerCurve = .continuous
        header.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "直接调用 BlinkVoiceSession"
        titleLabel.font = .systemFont(ofSize: 20, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = "请正视前置摄像头，测试单次眨眼、快速双眨眼和长闭眼。"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        statusPill.font = .systemFont(ofSize: 13, weight: .semibold)
        statusPill.textAlignment = .center
        statusPill.layer.cornerRadius = 14
        statusPill.layer.cornerCurve = .continuous
        statusPill.clipsToBounds = true
        statusPill.translatesAutoresizingMaskIntoConstraints = false

        header.addSubview(titleLabel)
        header.addSubview(subtitleLabel)
        header.addSubview(statusPill)
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            titleLabel.topAnchor.constraint(equalTo: header.topAnchor, constant: 16),
            statusPill.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            statusPill.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            statusPill.widthAnchor.constraint(greaterThanOrEqualToConstant: 82),
            statusPill.heightAnchor.constraint(equalToConstant: 28),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            subtitleLabel.bottomAnchor.constraint(equalTo: header.bottomAnchor, constant: -16)
        ])

        stack.addArrangedSubview(header)
        stack.addArrangedSubview(makeMetricsCard(rows: [
            ("设备支持", BlinkVoiceSession.isSupported ? "支持 ARKit Face Tracking" : "不支持", nil),
            ("相机权限", BlinkVoiceSession.cameraPermissionStatus.rawValue, nil),
            ("启动结果", "--", startResultValueLabel),
            ("眼睛状态", "unknown", stateValueLabel),
            ("最近事件", "暂无", eventValueLabel),
            ("事件次数", "0", countValueLabel),
            ("检测到人脸", "--", faceValueLabel),
            ("左眼分数", "--", leftScoreValueLabel),
            ("右眼分数", "--", rightScoreValueLabel),
            ("平均分数", "--", averageScoreValueLabel)
        ]))

        errorLabel.font = .systemFont(ofSize: 14, weight: .medium)
        errorLabel.textColor = .systemRed
        errorLabel.numberOfLines = 0
        errorLabel.textAlignment = .center
        errorLabel.backgroundColor = UIColor.systemRed.withAlphaComponent(0.08)
        errorLabel.layer.cornerRadius = 14
        errorLabel.layer.cornerCurve = .continuous
        errorLabel.clipsToBounds = true
        errorLabel.isHidden = true
        errorLabel.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(errorLabel)
        errorLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true

        actionButton.setTitle("开始识别", for: .normal)
        actionButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        actionButton.backgroundColor = UIColor(red: 0.12, green: 0.45, blue: 0.78, alpha: 1)
        actionButton.tintColor = .white
        actionButton.layer.cornerRadius = 16
        actionButton.layer.cornerCurve = .continuous
        actionButton.addTarget(self, action: #selector(actionTapped), for: .touchUpInside)
        stack.addArrangedSubview(actionButton)
        actionButton.heightAnchor.constraint(equalToConstant: 52).isActive = true

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 18),
            stack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -32)
        ])
        updateStatusPill()
    }

    private func makeMetricsCard(rows: [(String, String, UILabel?)]) -> UIView {
        let card = UIStackView()
        card.axis = .vertical
        card.spacing = 0
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 18
        card.layer.cornerCurve = .continuous
        card.layoutMargins = UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
        card.isLayoutMarginsRelativeArrangement = true

        rows.enumerated().forEach { index, row in
            let line = metricRow(title: row.0, value: row.1, valueLabel: row.2)
            card.addArrangedSubview(line)
            line.heightAnchor.constraint(equalToConstant: 42).isActive = true
            if index < rows.count - 1 {
                let divider = UIView()
                divider.backgroundColor = UIColor.separator.withAlphaComponent(0.35)
                card.addArrangedSubview(divider)
                divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale).isActive = true
            }
        }
        return card
    }

    private func makeMetricsCard(rows: [(String, String)]) -> UIView {
        makeMetricsCard(rows: rows.map { ($0.0, $0.1, nil) })
    }

    private func metricRow(title: String, value: String, valueLabel externalValueLabel: UILabel?) -> UIView {
        let container = UIView()
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let valueLabel = externalValueLabel ?? UILabel()
        valueLabel.text = value
        valueLabel.font = .monospacedDigitSystemFont(ofSize: 14, weight: .semibold)
        valueLabel.textColor = .label
        valueLabel.textAlignment = .right
        valueLabel.adjustsFontSizeToFitWidth = true
        valueLabel.minimumScaleFactor = 0.72
        valueLabel.translatesAutoresizingMaskIntoConstraints = false

        container.addSubview(titleLabel)
        container.addSubview(valueLabel)
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            valueLabel.leadingAnchor.constraint(greaterThanOrEqualTo: titleLabel.trailingAnchor, constant: 12),
            valueLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            valueLabel.centerYAnchor.constraint(equalTo: container.centerYAnchor)
        ])
        return container
    }

    private func startSession() {
        errorLabel.isHidden = true
        errorLabel.text = nil
        guard BlinkVoiceSession.isSupported else {
            isRunning = false
            startResultValueLabel.text = BlinkSessionStartResult.unsupportedDevice.rawValue
            errorLabel.text = "当前设备不支持 ARKit Face Tracking，无法使用 BlinkVoiceKit。"
            errorLabel.isHidden = false
            updateStatusPill()
            return
        }

        let result = session.start()
        startResultValueLabel.text = result.rawValue
        switch result {
        case .started, .alreadyRunning, .pendingCameraPermission:
            isRunning = true
            actionButton.setTitle("停止识别", for: .normal)
        case .unsupportedDevice, .cameraPermissionDenied:
            isRunning = false
            errorLabel.text = result == .cameraPermissionDenied
                ? "相机权限未授权，请点右上角“设置”开启权限。"
                : "当前设备不支持 ARKit Face Tracking。"
            errorLabel.isHidden = false
            actionButton.setTitle("重新开始", for: .normal)
        }
        updateStatusPill()
    }

    private func stopSession() {
        session.stop()
        isRunning = false
        actionButton.setTitle("开始识别", for: .normal)
        updateStatusPill()
    }

    private func render(_ sample: BlinkSample) {
        faceValueLabel.text = sample.frame.facePresent ? "是" : "否"
        leftScoreValueLabel.text = String(format: "%.3f", sample.frame.leftClosedScore)
        rightScoreValueLabel.text = String(format: "%.3f", sample.frame.rightClosedScore)
        averageScoreValueLabel.text = String(format: "%.3f", sample.frame.averageClosedScore)
        stateValueLabel.text = sample.state.rawValue
    }

    private func updateStatusPill() {
        if isRunning {
            statusPill.text = "识别中"
            statusPill.textColor = UIColor(red: 0.07, green: 0.42, blue: 0.24, alpha: 1)
            statusPill.backgroundColor = UIColor.systemGreen.withAlphaComponent(0.16)
        } else {
            statusPill.text = "已停止"
            statusPill.textColor = .secondaryLabel
            statusPill.backgroundColor = UIColor.tertiarySystemFill
        }
    }

    private func displayName(for event: BlinkEvent) -> String {
        switch event {
        case .singleBlink:
            return "单次眨眼"
        case .doubleBlink:
            return "快速双眨眼"
        case .longEyeClose:
            return "长闭眼"
        }
    }

    private func message(for error: BlinkError) -> String {
        switch error {
        case .unsupportedDevice:
            return "当前设备不支持 ARKit Face Tracking。"
        case .cameraPermissionDenied:
            return "相机权限被拒绝，请在系统设置中允许“只发”访问相机。"
        case .providerFailed(let message):
            return "ARKit 运行失败：\(message)"
        }
    }

    @objc private func actionTapped() {
        isRunning ? stopSession() : startSession()
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func openSettingsTapped() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

final class BlinkClosedGestureTrigger: NSObject {
    var onTriggered: (() -> Void)?
    var onSingleBlink: (() -> Void)?
    var onDoubleBlink: (() -> Void)?
    var onStatus: ((String) -> Void)?
    var onTouchIsolationChanged: ((Bool) -> Void)?

    private let session = BlinkVoiceSession(config: .fastInteraction)
    private let singleBlinkFeedback = UIImpactFeedbackGenerator(style: .light)
    private let doubleBlinkFeedback = UIImpactFeedbackGenerator(style: .medium)
    private let longEyeCloseFeedback = UIImpactFeedbackGenerator(style: .heavy)
    private let longEyeCloseConfirmationFeedback = UIImpactFeedbackGenerator(style: .rigid)
    private weak var installedView: UIView?
    private var longPressGesture: UILongPressGestureRecognizer?
    private var isArmed = false
    private var didTriggerInCurrentPress = false

    override init() {
        super.init()
        configureSession()
    }

    func install(on view: UIView) {
        if let longPressGesture, let installedView {
            installedView.removeGestureRecognizer(longPressGesture)
        }
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        gesture.minimumPressDuration = 0.08
        gesture.numberOfTouchesRequired = 2
        gesture.allowableMovement = 80
        gesture.cancelsTouchesInView = true
        gesture.delaysTouchesBegan = false
        gesture.delaysTouchesEnded = false
        view.addGestureRecognizer(gesture)
        installedView = view
        longPressGesture = gesture
    }

    private func configureSession() {
        session.onEvent = { [weak self] event in
            DispatchQueue.main.async {
                guard let self, self.isArmed, !self.didTriggerInCurrentPress else { return }
                self.didTriggerInCurrentPress = true
                switch event {
                case .singleBlink:
                    self.singleBlinkFeedback.impactOccurred()
                    self.onSingleBlink?()
                case .doubleBlink:
                    self.doubleBlinkFeedback.impactOccurred()
                    self.onDoubleBlink?()
                case .longEyeClose:
                    self.longEyeCloseFeedback.impactOccurred(intensity: 1)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.07) { [weak self] in
                        self?.longEyeCloseConfirmationFeedback.impactOccurred(intensity: 0.85)
                    }
                    self.onTriggered?()
                }
                self.stopSession()
            }
        }
        session.onError = { [weak self] error in
            DispatchQueue.main.async {
                self?.onStatus?(Self.message(for: error))
                self?.stopSession()
            }
        }
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began:
            onTouchIsolationChanged?(true)
            arm()
        case .ended, .cancelled, .failed:
            onTouchIsolationChanged?(false)
            disarm()
        default:
            break
        }
    }

    private func arm() {
        guard !isArmed else { return }
        guard BlinkVoiceSession.isSupported else {
            onStatus?("当前设备不支持眨眼识别")
            return
        }
        isArmed = true
        didTriggerInCurrentPress = false
        singleBlinkFeedback.prepare()
        doubleBlinkFeedback.prepare()
        longEyeCloseFeedback.prepare()
        longEyeCloseConfirmationFeedback.prepare()
        let result = session.start()
        switch result {
        case .started, .alreadyRunning:
            onStatus?("保持双指按住 进行眼部控制")
        case .pendingCameraPermission:
            onStatus?("请允许相机权限后再闭眼触发")
        case .unsupportedDevice:
            onStatus?("当前设备不支持眨眼识别")
            disarm()
        case .cameraPermissionDenied:
            onStatus?("请在系统设置中开启相机权限")
            disarm()
        }
    }

    private func disarm() {
        guard isArmed else { return }
        stopSession()
    }

    private func stopSession() {
        isArmed = false
        session.stop()
    }

    private static func message(for error: BlinkError) -> String {
        switch error {
        case .unsupportedDevice:
            return "当前设备不支持眨眼识别"
        case .cameraPermissionDenied:
            return "请在系统设置中开启相机权限"
        case .providerFailed(let message):
            return "眨眼识别启动失败：\(message)"
        }
    }
}

final class BlinkLongEyeInputTrigger {
    var onTriggered: (() -> Void)?
    var onStatus: ((String) -> Void)?

    private let session = BlinkVoiceSession(config: .fastInteraction)
    private var isRunning = false
    private var lastTriggeredAt = Date.distantPast

    init() {
        configureSession()
    }

    func start() {
        guard !isRunning else { return }
        guard BlinkVoiceSession.isSupported else {
            onStatus?("当前设备不支持眨眼识别")
            return
        }
        let result = session.start()
        switch result {
        case .started, .alreadyRunning:
            isRunning = true
        case .pendingCameraPermission:
            isRunning = true
            onStatus?("请允许相机权限后再使用闭眼改写")
        case .unsupportedDevice:
            isRunning = false
            onStatus?("当前设备不支持眨眼识别")
        case .cameraPermissionDenied:
            isRunning = false
            onStatus?("请在系统设置中开启相机权限")
        }
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        session.stop()
    }

    private func configureSession() {
        session.onEvent = { [weak self] event in
            DispatchQueue.main.async {
                guard let self, self.isRunning, event == .longEyeClose else { return }
                guard Date().timeIntervalSince(self.lastTriggeredAt) > 0.7 else { return }
                self.lastTriggeredAt = Date()
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                self.onTriggered?()
            }
        }
        session.onError = { [weak self] error in
            DispatchQueue.main.async {
                self?.isRunning = false
                self?.onStatus?(Self.message(for: error))
            }
        }
    }

    private static func message(for error: BlinkError) -> String {
        switch error {
        case .unsupportedDevice:
            return "当前设备不支持眨眼识别"
        case .cameraPermissionDenied:
            return "请在系统设置中开启相机权限"
        case .providerFailed(let message):
            return "眨眼识别启动失败：\(message)"
        }
    }
}

extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

private extension BlinkConfig {
    static var fastInteraction: BlinkConfig {
        BlinkConfig(
            closedThreshold: 0.62,
            openThreshold: 0.40,
            closedStableMs: 24,
            openStableMs: 24,
            minBlinkCloseMs: 38,
            maxShortBlinkCloseMs: 250,
            doubleBlinkMinGapMs: 45,
            doubleBlinkMaxGapMs: 260,
            doubleBlinkStrongPeakMinGapMs: 36,
            doubleBlinkStrongPeakThreshold: 0.78,
            longCloseMs: 270,
            eventCooldownMs: 90,
            faceLostResetMs: 220,
            fastDoubleBlinkEnabled: true,
            fastDoubleBlinkDipThreshold: 0.48,
            fastDoubleBlinkRecloseThreshold: 0.52,
            fastDoubleBlinkMinDrop: 0.06,
            fastDoubleBlinkMinRise: 0.06,
            fastDoubleBlinkMinSpanMs: 70,
            fastDoubleBlinkMaxSpanMs: 300
        )
    }
}

extension UIView {
    func firstSuperview<T: UIView>(of type: T.Type) -> T? {
        var current: UIView? = self
        while let view = current {
            if let matched = view as? T {
                return matched
            }
            current = view.superview
        }
        return nil
    }
}

extension UIColor {
    convenience init?(hexString: String) {
        let cleaned = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "#").union(.whitespacesAndNewlines))
        guard cleaned.count == 6, let value = Int(cleaned, radix: 16) else { return nil }
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }

    var hexString: String {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(format: "#%02X%02X%02X", Int(red * 255), Int(green * 255), Int(blue * 255))
    }
}
