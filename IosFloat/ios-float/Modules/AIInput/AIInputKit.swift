import Foundation

enum AIInputKitAction: String, CaseIterable {
    case rewrite = "AI重写"
    case polish = "AI润色"
    case continueWriting = "AI续写"
}

protocol AIInputKitProviding {
    func rewrite(_ text: String, completion: @escaping (String) -> Void)
    func polish(_ text: String, completion: @escaping (String) -> Void)
    func continueWriting(_ text: String, completion: @escaping (String) -> Void)
}

final class LocalAIInputKitAdapter: AIInputKitProviding {
    func rewrite(_ text: String, completion: @escaping (String) -> Void) {
        completion(text.isEmpty ? "我稍后回复你。" : "换一种说法：\(text)")
    }

    func polish(_ text: String, completion: @escaping (String) -> Void) {
        completion(text.isEmpty ? "收到，我稍后确认。" : "\(text)\n\n我确认后尽快同步。")
    }

    func continueWriting(_ text: String, completion: @escaping (String) -> Void) {
        completion(text + (text.isEmpty ? "我看到了，稍后处理。" : "，我这边会继续跟进。"))
    }
}

enum AIInputKitDemo {
    static func run(action: AIInputKitAction, text: String, completion: @escaping (String) -> Void) {
        let adapter = LocalAIInputKitAdapter()
        switch action {
        case .rewrite:
            adapter.rewrite(text, completion: completion)
        case .polish:
            adapter.polish(text, completion: completion)
        case .continueWriting:
            adapter.continueWriting(text, completion: completion)
        }
    }
}
