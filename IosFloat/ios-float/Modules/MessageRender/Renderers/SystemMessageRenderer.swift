final class SystemMessageRenderer: BubbleBackedMessageRenderer {
    init() {
        super.init(supportedTypes: [.system])
    }
}
