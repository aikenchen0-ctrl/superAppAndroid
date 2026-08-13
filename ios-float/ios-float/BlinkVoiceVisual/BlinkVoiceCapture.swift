#if canImport(UIKit) && canImport(AVFoundation) && canImport(Vision)
import AVFoundation
import UIKit
import Vision

public final class BlinkVoiceCapture {
    private init() {}

    @discardableResult
    @MainActor
    public static func start(
        from presentingViewController: UIViewController,
        options: BlinkCaptureOptions = BlinkCaptureOptions(),
        completion: @escaping (BlinkCaptureResult?) -> Void
    ) -> UIViewController {
        let controller = BlinkCaptureViewController(options: options, completion: completion)
        controller.modalPresentationStyle = .fullScreen
        presentingViewController.present(controller, animated: true)
        return controller
    }
}
#endif
