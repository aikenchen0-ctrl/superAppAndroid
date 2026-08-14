import Foundation

enum BusinessPageKind: String, CaseIterable {
    case moments = "朋友圈"
    case materialLibrary = "朋友圈素材库"
    case customerProfile = "客户档案"
    case channelsPublish = "视频号发布"
    case paymentState = "支付状态"
    case cardWallet = "卡包"
    case appQuality = "质量报告"
}

struct BusinessPageDescriptor: Equatable {
    let kind: BusinessPageKind
    let routeKey: String
    let title: String
    let requiresAccountContext: Bool
}

protocol BusinessPageKitProviding {
    func pageDescriptors() -> [BusinessPageDescriptor]
    func descriptor(for kind: BusinessPageKind) -> BusinessPageDescriptor?
}

struct DefaultBusinessPageKitAdapter: BusinessPageKitProviding {
    private let descriptors: [BusinessPageDescriptor] = [
        BusinessPageDescriptor(kind: .moments, routeKey: "moments", title: "朋友圈", requiresAccountContext: true),
        BusinessPageDescriptor(kind: .materialLibrary, routeKey: "material-library", title: "朋友圈素材库", requiresAccountContext: true),
        BusinessPageDescriptor(kind: .customerProfile, routeKey: "customer-profile", title: "客户档案", requiresAccountContext: false),
        BusinessPageDescriptor(kind: .channelsPublish, routeKey: "channels-publish", title: "视频号发布", requiresAccountContext: true),
        BusinessPageDescriptor(kind: .paymentState, routeKey: "payment-state", title: "支付状态", requiresAccountContext: true),
        BusinessPageDescriptor(kind: .cardWallet, routeKey: "card-wallet", title: "卡包", requiresAccountContext: true),
        BusinessPageDescriptor(kind: .appQuality, routeKey: "app-quality", title: "质量报告", requiresAccountContext: false)
    ]

    func pageDescriptors() -> [BusinessPageDescriptor] {
        descriptors
    }

    func descriptor(for kind: BusinessPageKind) -> BusinessPageDescriptor? {
        descriptors.first { $0.kind == kind }
    }
}

enum BusinessPageKitDemo {
    static func descriptors() -> [BusinessPageDescriptor] {
        DefaultBusinessPageKitAdapter().pageDescriptors()
    }
}
