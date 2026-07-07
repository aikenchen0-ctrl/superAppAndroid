#if canImport(UIKit) && canImport(AVFoundation) && canImport(Vision)
import AVFoundation
import UIKit

final class BlinkCaptureViewController: UIViewController {
    private let options: BlinkCaptureOptions
    private let completion: (BlinkCaptureResult?) -> Void
    private let classifier: BlinkEventClassifier
    private let detector = VisionBlinkDetector()
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "com.blinkvoice.visual.ios.capture-session")
    private let sampleBufferQueue = DispatchQueue(label: "com.blinkvoice.visual.ios.sample-buffer")

    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var statusLabel: UILabel?
    private var isFinishing = false
    private var didConfigureSession = false

    init(options: BlinkCaptureOptions, completion: @escaping (BlinkCaptureResult?) -> Void) {
        self.options = options
        self.completion = completion
        self.classifier = BlinkEventClassifier(options: options)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureBaseView()
        requestCameraPermission()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true {
            stopSession()
        }
    }

    private func configureBaseView() {
        view.backgroundColor = .black

        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "BlinkVoice starting..."
        label.textColor = .white
        label.font = .systemFont(ofSize: 17, weight: .semibold)
        label.textAlignment = .center
        label.numberOfLines = 0
        label.backgroundColor = UIColor.black.withAlphaComponent(0.55)
        label.layer.cornerRadius = 8
        label.layer.masksToBounds = true
        view.addSubview(label)
        statusLabel = label

        let cancelButton = UIButton(type: .system)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.setTitleColor(.white, for: .normal)
        cancelButton.backgroundColor = UIColor.black.withAlphaComponent(0.55)
        cancelButton.layer.cornerRadius = 8
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        view.addSubview(cancelButton)

        NSLayoutConstraint.activate([
            cancelButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            cancelButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            cancelButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 86),
            cancelButton.heightAnchor.constraint(equalToConstant: 44),

            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            label.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32),
            label.heightAnchor.constraint(greaterThanOrEqualToConstant: 56)
        ])
    }

    private func requestCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStartSession()

        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.configureAndStartSession()
                    } else {
                        self?.finish(with: nil)
                    }
                }
            }

        case .denied, .restricted:
            finish(with: nil)

        @unknown default:
            finish(with: nil)
        }
    }

    private func configureAndStartSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }

            if !didConfigureSession {
                do {
                    try configureSession()
                    didConfigureSession = true
                } catch {
                    DispatchQueue.main.async { [weak self] in
                        self?.finish(with: nil)
                    }
                    return
                }
            }

            if !session.isRunning {
                session.startRunning()
            }

            DispatchQueue.main.async { [weak self] in
                self?.statusLabel?.text = "Blink once, blink twice, or close eyes"
            }
        }
    }

    private func configureSession() throws {
        session.beginConfiguration()
        session.sessionPreset = .medium

        defer {
            session.commitConfiguration()
        }

        guard
            let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
            let input = try? AVCaptureDeviceInput(device: camera),
            session.canAddInput(input)
        else {
            throw CaptureError.cameraUnavailable
        }

        session.addInput(input)

        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ]
        output.setSampleBufferDelegate(self, queue: sampleBufferQueue)

        guard session.canAddOutput(output) else {
            throw CaptureError.outputUnavailable
        }

        session.addOutput(output)

        if let connection = output.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if connection.isVideoMirroringSupported {
                connection.isVideoMirrored = true
            }
        }

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = view.bounds
            view.layer.insertSublayer(layer, at: 0)
            previewLayer = layer
        }
    }

    private func stopSession() {
        sessionQueue.async { [session] in
            if session.isRunning {
                session.stopRunning()
            }
        }
    }

    @objc private func cancelTapped() {
        finish(with: nil)
    }

    private func handleFrameResult(_ frameResult: BlinkFrameResult) {
        let event = classifier.accept(
            timestampMs: currentTimeMs(),
            hasFace: frameResult.hasFace,
            leftEar: frameResult.leftEar,
            rightEar: frameResult.rightEar
        )

        guard let event, options.eventTypes.contains(event.eventType) else {
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            statusLabel?.text = "Event: \(event.eventType.rawValue)"
            if options.autoFinishOnEvent {
                finish(with: event)
            } else {
                completion(event)
            }
        }
    }

    private func finish(with result: BlinkCaptureResult?) {
        guard !isFinishing else { return }
        isFinishing = true
        stopSession()
        dismiss(animated: true) { [completion] in
            completion(result)
        }
    }

    private func currentTimeMs() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000)
    }

    private enum CaptureError: Error {
        case cameraUnavailable
        case outputUnavailable
    }
}

extension BlinkCaptureViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard !isFinishing else { return }

        do {
            let frameResult = try detector.detect(sampleBuffer: sampleBuffer)
            handleFrameResult(frameResult)
        } catch {
            handleFrameResult(BlinkFrameResult(hasFace: false, leftEar: 0, rightEar: 0))
        }
    }
}
#endif
