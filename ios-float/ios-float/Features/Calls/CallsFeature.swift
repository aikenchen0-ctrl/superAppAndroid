import UIKit
import AVFoundation

private final class SpeakingWaveView: UIView {
    private let iconView = UIImageView(image: UIImage(systemName: "mic.fill"))
    private let barStack = UIStackView()
    private var bars: [UIView] = []
    private let activeColor: UIColor
    private var isActiveState = false

    init(activeColor: UIColor = UIColor(red: 0.42, green: 0.84, blue: 0.55, alpha: 1)) {
        self.activeColor = activeColor
        super.init(frame: .zero)
        setup()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setup() {
        backgroundColor = UIColor.white.withAlphaComponent(0.09)
        layer.cornerRadius = 18
        layer.cornerCurve = .continuous

        iconView.tintColor = activeColor
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        barStack.axis = .horizontal
        barStack.alignment = .center
        barStack.spacing = 4
        barStack.translatesAutoresizingMaskIntoConstraints = false

        addSubview(iconView)
        addSubview(barStack)

        (0..<8).forEach { index in
            let bar = UIView()
            bar.backgroundColor = activeColor
            bar.layer.cornerRadius = 2
            bar.alpha = 0.48
            bar.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                bar.widthAnchor.constraint(equalToConstant: 4),
                bar.heightAnchor.constraint(equalToConstant: CGFloat(9 + (index % 4) * 4))
            ])
            bars.append(bar)
            barStack.addArrangedSubview(bar)
        }

        NSLayoutConstraint.activate([
            iconView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            iconView.centerYAnchor.constraint(equalTo: centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 17),
            iconView.heightAnchor.constraint(equalToConstant: 17),
            barStack.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 10),
            barStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            barStack.centerYAnchor.constraint(equalTo: centerYAnchor),
            barStack.heightAnchor.constraint(equalToConstant: 30)
        ])
    }

    func setActive(_ active: Bool) {
        guard active != isActiveState else { return }
        isActiveState = active
        alpha = active ? 1 : 0.46
        iconView.image = UIImage(systemName: active ? "mic.fill" : "mic.slash.fill")
        bars.enumerated().forEach { index, bar in
            bar.layer.removeAnimation(forKey: "speaking-wave-scale")
            bar.layer.removeAnimation(forKey: "speaking-wave-opacity")
            bar.transform = .identity
            bar.alpha = active ? 0.48 : 0.24
            guard active else { return }

            let scale = CAKeyframeAnimation(keyPath: "transform.scale.y")
            scale.values = [0.35, 1.25, 0.48, 0.9, 0.35]
            scale.keyTimes = [0, 0.22, 0.48, 0.72, 1]
            scale.duration = 0.92
            scale.beginTime = CACurrentMediaTime() + Double(index) * 0.075
            scale.repeatCount = .infinity
            scale.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)

            let opacity = CAKeyframeAnimation(keyPath: "opacity")
            opacity.values = [0.42, 1, 0.52, 0.86, 0.42]
            opacity.keyTimes = scale.keyTimes
            opacity.duration = scale.duration
            opacity.beginTime = scale.beginTime
            opacity.repeatCount = .infinity
            opacity.timingFunction = scale.timingFunction

            bar.layer.add(scale, forKey: "speaking-wave-scale")
            bar.layer.add(opacity, forKey: "speaking-wave-opacity")
        }
    }
}

private final class CallAudioLevelMonitor {
    var onLevelChanged: ((Float) -> Void)?

    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var recordingURL: URL?

    func start() {
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard granted else {
                    self?.onLevelChanged?(0)
                    return
                }
                self?.startRecordingForMetering()
            }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        recorder?.stop()
        recorder = nil
        if let recordingURL {
            try? FileManager.default.removeItem(at: recordingURL)
        }
        recordingURL = nil
        onLevelChanged?(0)
    }

    private func startRecordingForMetering() {
        stop()
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("call-meter-\(UUID().uuidString).caf")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 12_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.min.rawValue
        ]

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.isMeteringEnabled = true
            recorder.record()
            self.recorder = recorder
            recordingURL = url
            timer = Timer.scheduledTimer(withTimeInterval: 0.10, repeats: true) { [weak self] _ in
                self?.sampleLevel()
            }
        } catch {
            onLevelChanged?(0)
        }
    }

    private func sampleLevel() {
        guard let recorder else {
            onLevelChanged?(0)
            return
        }
        recorder.updateMeters()
        let power = recorder.averagePower(forChannel: 0)
        let normalized = max(0, min(1, pow(10, power / 20)))
        onLevelChanged?(normalized)
    }
}

final class VoiceCallViewController: UIViewController {
    var onEndCall: ((TimeInterval) -> Void)?

    private let localParticipant: ChatParticipant
    private let remoteParticipants: [ChatParticipant]
    private let isGroup: Bool
    private let startedAt = Date()
    private let titleLabel = UILabel()
    private let statusLabel = UILabel()
    private let durationLabel = UILabel()
    private let avatarContainer = UIView()
    private let participantStack = UIStackView()
    private let speakingWaveView = SpeakingWaveView()
    private let muteButton = UIButton(type: .system)
    private let speakerButton = UIButton(type: .system)
    private let endButton = UIButton(type: .system)
    private let muteLabel = UILabel()
    private let speakerLabel = UILabel()
    private let endLabel = UILabel()
    private var gradientLayer: CAGradientLayer?
    private var timer: Timer?
    private let audioLevelMonitor = CallAudioLevelMonitor()
    private var isMuted = false
    private var isSpeakerOn = true

    init(localParticipant: ChatParticipant, remoteParticipants: [ChatParticipant], isGroup: Bool) {
        self.localParticipant = localParticipant
        self.remoteParticipants = remoteParticipants
        self.isGroup = isGroup
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureBackground()
        configureHeader()
        configureParticipants()
        configureWaveform()
        configureControls()
        startTimer()
        startAudioLevelMonitor()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        gradientLayer?.frame = view.bounds
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true {
            timer?.invalidate()
            audioLevelMonitor.stop()
        }
    }

    private func configureBackground() {
        let gradient = CAGradientLayer()
        gradient.colors = [
            UIColor(red: 0.10, green: 0.15, blue: 0.18, alpha: 1).cgColor,
            UIColor(red: 0.03, green: 0.06, blue: 0.08, alpha: 1).cgColor
        ]
        gradient.startPoint = CGPoint(x: 0.2, y: 0)
        gradient.endPoint = CGPoint(x: 0.8, y: 1)
        view.layer.insertSublayer(gradient, at: 0)
        gradientLayer = gradient
    }

    private func configureHeader() {
        titleLabel.text = isGroup ? "群语音通话" : "语音通话"
        titleLabel.textAlignment = .center
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 21, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = callTargetText()
        statusLabel.textAlignment = .center
        statusLabel.textColor = UIColor.white.withAlphaComponent(0.72)
        statusLabel.font = .systemFont(ofSize: 15, weight: .medium)
        statusLabel.numberOfLines = 2
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        durationLabel.text = "00:00"
        durationLabel.textAlignment = .center
        durationLabel.textColor = UIColor.white.withAlphaComponent(0.78)
        durationLabel.font = .monospacedDigitSystemFont(ofSize: 15, weight: .medium)
        durationLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(titleLabel)
        view.addSubview(statusLabel)
        view.addSubview(durationLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 22),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            statusLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 36),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -36),

            durationLabel.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 8),
            durationLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }

    private func configureParticipants() {
        avatarContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(avatarContainer)

        participantStack.axis = isGroup ? .vertical : .horizontal
        participantStack.alignment = .center
        participantStack.distribution = .fillEqually
        participantStack.spacing = 12
        participantStack.translatesAutoresizingMaskIntoConstraints = false
        avatarContainer.addSubview(participantStack)

        let participants = displayParticipants()
        if isGroup {
            let rows = stride(from: 0, to: participants.count, by: 3).map {
                Array(participants[$0..<min($0 + 3, participants.count)])
            }
            rows.forEach { rowParticipants in
                let row = UIStackView()
                row.axis = .horizontal
                row.alignment = .center
                row.distribution = .equalCentering
                row.spacing = 16
                rowParticipants.forEach { row.addArrangedSubview(makeParticipantAvatar(for: $0, size: 58, showsName: true)) }
                participantStack.addArrangedSubview(row)
            }
        } else {
            participantStack.addArrangedSubview(makeParticipantAvatar(for: participants[0], size: 104, showsName: true))
        }

        NSLayoutConstraint.activate([
            avatarContainer.topAnchor.constraint(equalTo: durationLabel.bottomAnchor, constant: 48),
            avatarContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            avatarContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            avatarContainer.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: isGroup ? 0.28 : 0.24),

            participantStack.centerXAnchor.constraint(equalTo: avatarContainer.centerXAnchor),
            participantStack.centerYAnchor.constraint(equalTo: avatarContainer.centerYAnchor),
            participantStack.leadingAnchor.constraint(greaterThanOrEqualTo: avatarContainer.leadingAnchor),
            participantStack.trailingAnchor.constraint(lessThanOrEqualTo: avatarContainer.trailingAnchor)
        ])
    }

    private func makeParticipantAvatar(for participant: ChatParticipant, size: CGFloat, showsName: Bool) -> UIView {
        let container = UIStackView()
        container.axis = .vertical
        container.alignment = .center
        container.spacing = 10

        let avatar = UILabel()
        avatar.text = participant.initials
        avatar.textAlignment = .center
        avatar.textColor = .white
        avatar.font = .systemFont(ofSize: size >= 90 ? 38 : 22, weight: .bold)
        avatar.backgroundColor = participant.tintColor
        avatar.layer.cornerRadius = size / 2
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: size),
            avatar.heightAnchor.constraint(equalToConstant: size)
        ])
        container.addArrangedSubview(avatar)

        if showsName {
            let nameLabel = UILabel()
            nameLabel.text = participant.displayName
            nameLabel.textAlignment = .center
            nameLabel.textColor = .white
            nameLabel.font = .systemFont(ofSize: size >= 90 ? 17 : 12, weight: .semibold)
            nameLabel.lineBreakMode = .byTruncatingTail
            nameLabel.widthAnchor.constraint(lessThanOrEqualToConstant: size >= 90 ? 180 : 76).isActive = true
            container.addArrangedSubview(nameLabel)
        }

        return container
    }

    private func configureWaveform() {
        speakingWaveView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(speakingWaveView)

        NSLayoutConstraint.activate([
            speakingWaveView.topAnchor.constraint(equalTo: avatarContainer.bottomAnchor, constant: 26),
            speakingWaveView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            speakingWaveView.widthAnchor.constraint(equalToConstant: 112),
            speakingWaveView.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    private func configureControls() {
        let muteControl = makeControlItem(button: muteButton, label: muteLabel, symbolName: "mic.fill", title: "静音", action: #selector(toggleMute))
        let speakerControl = makeControlItem(button: speakerButton, label: speakerLabel, symbolName: "speaker.wave.2.fill", title: "免提", action: #selector(toggleSpeaker))
        let endControl = makeControlItem(button: endButton, label: endLabel, symbolName: "phone.down.fill", title: "结束", action: #selector(endCall))
        endButton.backgroundColor = UIColor.systemRed

        let controlStack = UIStackView(arrangedSubviews: [muteControl, speakerControl, endControl])
        controlStack.axis = .horizontal
        controlStack.alignment = .center
        controlStack.distribution = .equalSpacing
        controlStack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(controlStack)

        NSLayoutConstraint.activate([
            controlStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 44),
            controlStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -44),
            controlStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -34),
            controlStack.heightAnchor.constraint(equalToConstant: 92)
        ])
    }

    private func makeControlItem(button: UIButton, label: UILabel, symbolName: String, title: String, action: Selector) -> UIView {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 8

        button.setImage(UIImage(systemName: symbolName), for: .normal)
        button.tintColor = .white
        button.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        button.layer.cornerRadius = 30
        button.accessibilityLabel = title
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: action, for: .touchUpInside)
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 60),
            button.heightAnchor.constraint(equalToConstant: 60)
        ])

        label.text = title
        label.textAlignment = .center
        label.textColor = UIColor.white.withAlphaComponent(0.82)
        label.font = .systemFont(ofSize: 12, weight: .medium)

        stack.addArrangedSubview(button)
        stack.addArrangedSubview(label)
        return stack
    }

    private func displayParticipants() -> [ChatParticipant] {
        let fallback = ChatParticipant(id: UUID(), displayName: "当前好友", tintColor: .systemBlue, initials: "友", isCurrentUser: false)
        if remoteParticipants.isEmpty {
            return [fallback]
        }
        return Array(remoteParticipants.prefix(9))
    }

    private func callTargetText() -> String {
        let participants = displayParticipants()
        if isGroup {
            return "已接通 \(participants.count) 人"
        }
        return participants[0].displayName
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.updateDuration()
        }
        updateDuration()
    }

    private func updateDuration() {
        let seconds = max(0, Int(Date().timeIntervalSince(startedAt)))
        durationLabel.text = String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    private func startAudioLevelMonitor() {
        speakingWaveView.setActive(false)
        audioLevelMonitor.onLevelChanged = { [weak self] level in
            guard let self else { return }
            self.speakingWaveView.setActive(!self.isMuted && level > 0.035)
        }
        audioLevelMonitor.start()
    }

    @objc private func toggleMute() {
        isMuted.toggle()
        muteButton.setImage(UIImage(systemName: isMuted ? "mic.slash.fill" : "mic.fill"), for: .normal)
        muteButton.backgroundColor = isMuted ? UIColor.systemOrange : UIColor.white.withAlphaComponent(0.16)
        muteLabel.text = isMuted ? "已静音" : "静音"
        speakingWaveView.setActive(!isMuted)
        if isMuted {
            speakingWaveView.setActive(false)
        } else {
            audioLevelMonitor.start()
        }
    }

    @objc private func toggleSpeaker() {
        isSpeakerOn.toggle()
        speakerButton.setImage(UIImage(systemName: isSpeakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill"), for: .normal)
        speakerButton.backgroundColor = isSpeakerOn ? UIColor.white.withAlphaComponent(0.16) : UIColor.systemOrange
        speakerLabel.text = isSpeakerOn ? "免提" : "听筒"
    }

    @objc private func endCall() {
        let duration = Date().timeIntervalSince(startedAt)
        timer?.invalidate()
        audioLevelMonitor.stop()
        dismiss(animated: true) { [onEndCall] in
            onEndCall?(duration)
        }
    }
}

final class VideoCallViewController: UIViewController {
    var onEndCall: ((TimeInterval) -> Void)?

    private let localParticipant: ChatParticipant
    private let remoteParticipants: [ChatParticipant]
    private let isGroup: Bool
    private let startedAt = Date()
    private let titleLabel = UILabel()
    private let durationLabel = UILabel()
    private let remoteGrid = UIStackView()
    private let localPreviewView = UIView()
    private let localInitialLabel = UILabel()
    private let speakingWaveView = SpeakingWaveView(activeColor: UIColor(red: 0.35, green: 0.78, blue: 1.0, alpha: 1))
    private let muteButton = UIButton(type: .system)
    private let cameraButton = UIButton(type: .system)
    private let switchCameraButton = UIButton(type: .system)
    private let endButton = UIButton(type: .system)
    private var timer: Timer?
    private let audioLevelMonitor = CallAudioLevelMonitor()
    private var captureSession: AVCaptureSession?
    private var currentCameraInput: AVCaptureDeviceInput?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let cameraSessionQueue = DispatchQueue(label: "local.ios-float.video-call.camera")
    private var isMuted = false
    private var isCameraOn = true
    private var currentCameraPosition: AVCaptureDevice.Position = .front
    private var isSwitchingCamera = false

    init(localParticipant: ChatParticipant, remoteParticipants: [ChatParticipant], isGroup: Bool) {
        self.localParticipant = localParticipant
        self.remoteParticipants = remoteParticipants
        self.isGroup = isGroup
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.04, green: 0.07, blue: 0.09, alpha: 1)
        configureHeader()
        configureSpeakingWave()
        configureRemoteGrid()
        configureLocalPreview()
        configureControls()
        startTimer()
        startAudioLevelMonitor()
        startCameraPreview()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = localPreviewView.bounds
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true {
            stopCameraPreview()
            audioLevelMonitor.stop()
            timer?.invalidate()
        }
    }

    private func configureHeader() {
        titleLabel.text = isGroup ? "群视频通话" : "视频通话"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 22, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        durationLabel.text = "00:00"
        durationLabel.textColor = UIColor.white.withAlphaComponent(0.72)
        durationLabel.font = .monospacedDigitSystemFont(ofSize: 14, weight: .medium)
        durationLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(titleLabel)
        view.addSubview(durationLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 18),
            titleLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            durationLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            durationLabel.centerXAnchor.constraint(equalTo: titleLabel.centerXAnchor)
        ])
    }

    private func configureRemoteGrid() {
        remoteGrid.axis = .vertical
        remoteGrid.spacing = 10
        remoteGrid.distribution = .fillEqually
        remoteGrid.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(remoteGrid)

        let participants = remoteParticipants.isEmpty
            ? [ChatParticipant(id: UUID(), displayName: "当前好友", tintColor: .systemBlue, initials: "友", isCurrentUser: false)]
            : remoteParticipants
        let rows = stride(from: 0, to: participants.count, by: 2).map {
            Array(participants[$0..<min($0 + 2, participants.count)])
        }
        rows.forEach { rowParticipants in
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 10
            row.distribution = .fillEqually
            rowParticipants.forEach { row.addArrangedSubview(makeRemoteTile(for: $0)) }
            if rowParticipants.count == 1, participants.count > 1 {
                row.addArrangedSubview(UIView())
            }
            remoteGrid.addArrangedSubview(row)
        }

        NSLayoutConstraint.activate([
            remoteGrid.topAnchor.constraint(equalTo: speakingWaveView.bottomAnchor, constant: 14),
            remoteGrid.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            remoteGrid.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            remoteGrid.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -132)
        ])
    }

    private func makeRemoteTile(for participant: ChatParticipant) -> UIView {
        let tile = UIView()
        tile.backgroundColor = UIColor(red: 0.10, green: 0.14, blue: 0.17, alpha: 1)
        tile.layer.cornerRadius = 14
        tile.layer.cornerCurve = .continuous
        tile.clipsToBounds = true

        let avatar = UILabel()
        avatar.text = participant.initials
        avatar.textAlignment = .center
        avatar.textColor = .white
        avatar.font = .systemFont(ofSize: 28, weight: .bold)
        avatar.backgroundColor = participant.tintColor
        avatar.layer.cornerRadius = 32
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false

        let nameLabel = UILabel()
        nameLabel.text = participant.displayName
        nameLabel.textColor = .white
        nameLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        let statusLabel = UILabel()
        statusLabel.text = "视频已连接"
        statusLabel.textColor = UIColor.white.withAlphaComponent(0.62)
        statusLabel.font = .systemFont(ofSize: 12, weight: .medium)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        tile.addSubview(avatar)
        tile.addSubview(nameLabel)
        tile.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            avatar.centerXAnchor.constraint(equalTo: tile.centerXAnchor),
            avatar.centerYAnchor.constraint(equalTo: tile.centerYAnchor, constant: -14),
            avatar.widthAnchor.constraint(equalToConstant: 64),
            avatar.heightAnchor.constraint(equalToConstant: 64),
            nameLabel.topAnchor.constraint(equalTo: avatar.bottomAnchor, constant: 12),
            nameLabel.centerXAnchor.constraint(equalTo: tile.centerXAnchor),
            statusLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            statusLabel.centerXAnchor.constraint(equalTo: tile.centerXAnchor)
        ])
        return tile
    }

    private func configureLocalPreview() {
        localPreviewView.backgroundColor = UIColor(red: 0.13, green: 0.17, blue: 0.20, alpha: 1)
        localPreviewView.layer.cornerRadius = 16
        localPreviewView.layer.cornerCurve = .continuous
        localPreviewView.layer.borderWidth = 1
        localPreviewView.layer.borderColor = UIColor.white.withAlphaComponent(0.24).cgColor
        localPreviewView.clipsToBounds = true
        localPreviewView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(localPreviewView)

        localInitialLabel.text = localParticipant.initials
        localInitialLabel.textAlignment = .center
        localInitialLabel.textColor = .white
        localInitialLabel.font = .systemFont(ofSize: 20, weight: .bold)
        localInitialLabel.backgroundColor = localParticipant.tintColor
        localInitialLabel.layer.cornerRadius = 24
        localInitialLabel.clipsToBounds = true
        localInitialLabel.translatesAutoresizingMaskIntoConstraints = false
        localPreviewView.addSubview(localInitialLabel)

        let nameLabel = UILabel()
        nameLabel.text = "我"
        nameLabel.textColor = .white
        nameLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        localPreviewView.addSubview(nameLabel)

        NSLayoutConstraint.activate([
            localPreviewView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            localPreviewView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -120),
            localPreviewView.widthAnchor.constraint(equalToConstant: 118),
            localPreviewView.heightAnchor.constraint(equalToConstant: 158),
            localInitialLabel.centerXAnchor.constraint(equalTo: localPreviewView.centerXAnchor),
            localInitialLabel.centerYAnchor.constraint(equalTo: localPreviewView.centerYAnchor),
            localInitialLabel.widthAnchor.constraint(equalToConstant: 48),
            localInitialLabel.heightAnchor.constraint(equalToConstant: 48),
            nameLabel.leadingAnchor.constraint(equalTo: localPreviewView.leadingAnchor, constant: 10),
            nameLabel.bottomAnchor.constraint(equalTo: localPreviewView.bottomAnchor, constant: -8)
        ])
    }

    private func configureSpeakingWave() {
        speakingWaveView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(speakingWaveView)
        NSLayoutConstraint.activate([
            speakingWaveView.topAnchor.constraint(equalTo: durationLabel.bottomAnchor, constant: 8),
            speakingWaveView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            speakingWaveView.widthAnchor.constraint(equalToConstant: 112),
            speakingWaveView.heightAnchor.constraint(equalToConstant: 34)
        ])
    }

    private func configureControls() {
        let controlStack = UIStackView(arrangedSubviews: [muteButton, cameraButton, switchCameraButton, endButton])
        controlStack.axis = .horizontal
        controlStack.alignment = .center
        controlStack.distribution = .equalSpacing
        controlStack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(controlStack)

        configureControlButton(muteButton, symbolName: "mic.fill", title: "静音", action: #selector(toggleMute))
        configureControlButton(cameraButton, symbolName: "video.fill", title: "摄像头", action: #selector(toggleCamera))
        configureControlButton(switchCameraButton, symbolName: "arrow.triangle.2.circlepath.camera", title: "切换", action: #selector(switchCamera))
        configureControlButton(endButton, symbolName: "phone.down.fill", title: "结束", action: #selector(endCall))
        endButton.backgroundColor = UIColor.systemRed

        NSLayoutConstraint.activate([
            controlStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 34),
            controlStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -34),
            controlStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -26),
            controlStack.heightAnchor.constraint(equalToConstant: 64)
        ])
    }

    private func configureControlButton(_ button: UIButton, symbolName: String, title: String, action: Selector) {
        button.setImage(UIImage(systemName: symbolName), for: .normal)
        button.tintColor = .white
        button.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        button.layer.cornerRadius = 28
        button.accessibilityLabel = title
        button.widthAnchor.constraint(equalToConstant: 56).isActive = true
        button.heightAnchor.constraint(equalToConstant: 56).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.updateDuration()
        }
        updateDuration()
    }

    private func updateDuration() {
        let seconds = max(0, Int(Date().timeIntervalSince(startedAt)))
        durationLabel.text = String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    private func startAudioLevelMonitor() {
        speakingWaveView.setActive(false)
        audioLevelMonitor.onLevelChanged = { [weak self] level in
            guard let self else { return }
            self.speakingWaveView.setActive(!self.isMuted && level > 0.035)
        }
        audioLevelMonitor.start()
    }

    private func startCameraPreview() {
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized,
              let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentCameraPosition),
              let input = try? AVCaptureDeviceInput(device: camera)
        else { return }
        let session = AVCaptureSession()
        session.sessionPreset = .medium
        guard session.canAddInput(input) else { return }
        session.addInput(input)
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = localPreviewView.bounds
        localPreviewView.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
        captureSession = session
        currentCameraInput = input
        cameraSessionQueue.async {
            session.startRunning()
        }
    }

    private func stopCameraPreview() {
        let session = captureSession
        cameraSessionQueue.async {
            session?.stopRunning()
        }
        previewLayer = nil
        currentCameraInput = nil
        captureSession = nil
        localPreviewView.layer.sublayers?
            .filter { $0 is AVCaptureVideoPreviewLayer }
            .forEach { $0.removeFromSuperlayer() }
    }

    @objc private func toggleMute() {
        isMuted.toggle()
        muteButton.setImage(UIImage(systemName: isMuted ? "mic.slash.fill" : "mic.fill"), for: .normal)
        muteButton.backgroundColor = isMuted ? UIColor.systemOrange : UIColor.white.withAlphaComponent(0.16)
        if isMuted {
            speakingWaveView.setActive(false)
        } else {
            audioLevelMonitor.start()
        }
    }

    @objc private func toggleCamera() {
        isCameraOn.toggle()
        cameraButton.setImage(UIImage(systemName: isCameraOn ? "video.fill" : "video.slash.fill"), for: .normal)
        cameraButton.backgroundColor = isCameraOn ? UIColor.white.withAlphaComponent(0.16) : UIColor.systemOrange
        localPreviewView.alpha = isCameraOn ? 1 : 0.72
        if isCameraOn {
            startCameraPreview()
        } else {
            stopCameraPreview()
        }
    }

    @objc private func switchCamera() {
        guard !isSwitchingCamera else { return }
        let nextPosition: AVCaptureDevice.Position = currentCameraPosition == .front ? .back : .front
        currentCameraPosition = nextPosition

        guard isCameraOn else {
            switchCameraButton.tintColor = .white
            return
        }
        guard let session = captureSession,
              let currentInput = currentCameraInput,
              let nextCamera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: nextPosition),
              let nextInput = try? AVCaptureDeviceInput(device: nextCamera)
        else {
            stopCameraPreview()
            startCameraPreview()
            return
        }

        isSwitchingCamera = true
        switchCameraButton.isEnabled = false
        switchCameraButton.alpha = 0.55
        cameraSessionQueue.async { [weak self, weak session] in
            guard let self, let session else { return }
            session.beginConfiguration()
            session.removeInput(currentInput)
            if session.canAddInput(nextInput) {
                session.addInput(nextInput)
                DispatchQueue.main.async {
                    self.currentCameraInput = nextInput
                    self.finishCameraSwitch()
                }
            } else {
                session.addInput(currentInput)
                DispatchQueue.main.async {
                    self.currentCameraPosition = currentInput.device.position
                    self.finishCameraSwitch()
                }
            }
            session.commitConfiguration()
        }
    }

    private func finishCameraSwitch() {
        isSwitchingCamera = false
        switchCameraButton.isEnabled = true
        switchCameraButton.alpha = 1
    }

    @objc private func endCall() {
        let duration = Date().timeIntervalSince(startedAt)
        timer?.invalidate()
        stopCameraPreview()
        audioLevelMonitor.stop()
        dismiss(animated: true) { [onEndCall] in
            onEndCall?(duration)
        }
    }
}

