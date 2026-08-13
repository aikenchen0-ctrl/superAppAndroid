import UIKit

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private let safeAreaSummaryOverlayCoordinator = SafeAreaSummaryOverlayCoordinator()

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        let rootViewController = ChatWindowViewController()
        let navigationController = UINavigationController(rootViewController: rootViewController)
        navigationController.setNavigationBarHidden(true, animated: false)
        window.rootViewController = navigationController
        window.makeKeyAndVisible()
        self.window = window
        AppQualityBaseline.shared.install(on: window)
        safeAreaSummaryOverlayCoordinator.install(on: window)
        AIVoiceAssistantOverlayCoordinator.shared.install(on: window)

        if connectionOptions.urlContexts.contains(where: { $0.url.host == "chat" }) {
            rootViewController.showChatFromIsland()
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard URLContexts.contains(where: { $0.url.host == "chat" }),
              let navigationController = window?.rootViewController as? UINavigationController,
              let rootViewController = navigationController.viewControllers.first as? ChatWindowViewController
        else { return }

        rootViewController.showChatFromIsland()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        let navigationController = window?.rootViewController as? UINavigationController
        (navigationController?.viewControllers.first as? ChatWindowViewController)?.moveChatToIslandForBackground()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        let navigationController = window?.rootViewController as? UINavigationController
        (navigationController?.viewControllers.first as? ChatWindowViewController)?.restoreFloatingChatFromForeground()
    }
}
