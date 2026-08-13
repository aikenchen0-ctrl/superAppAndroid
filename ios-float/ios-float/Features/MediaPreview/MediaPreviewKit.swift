import UIKit
import QuickLook

enum MediaPreviewKit {
    static func imageController(
        image: UIImage,
        title: String,
        onDragMedia: ((MediaDragPayload, UIGestureRecognizer.State, CGPoint) -> Void)?,
        onBackgroundRemoved: ((UIImage, String) -> Void)?
    ) -> FullscreenMediaPreviewController {
        let controller = FullscreenMediaPreviewController.image(image, title: title)
        controller.onDragMedia = onDragMedia
        controller.onBackgroundRemoved = onBackgroundRemoved
        return controller
    }

    static func videoController(
        url: URL,
        onDragMedia: ((MediaDragPayload, UIGestureRecognizer.State, CGPoint) -> Void)?
    ) -> FullscreenMediaPreviewController {
        let controller = FullscreenMediaPreviewController.video(url)
        controller.onDragMedia = onDragMedia
        return controller
    }

    static func pagedController(
        items: [ChatMediaPreviewItem],
        startIndex: Int,
        onRequestAccess: ((ChatMediaPreviewItem) -> Void)?,
        onDragMedia: ((MediaDragPayload, UIGestureRecognizer.State, CGPoint) -> Void)?,
        onBackgroundRemoved: ((UIImage, String) -> Void)?
    ) -> PagedMediaPreviewController {
        let controller = PagedMediaPreviewController(items: items, startIndex: startIndex)
        controller.onRequestAccess = onRequestAccess
        controller.onDragMedia = onDragMedia
        controller.onBackgroundRemoved = onBackgroundRemoved
        return controller
    }

    static func inlineFileControllerIfSupported(url: URL) -> UIViewController? {
        guard InlineFilePreviewViewController.canPreview(url) else { return nil }
        return UINavigationController(rootViewController: InlineFilePreviewViewController(url: url))
    }
}
