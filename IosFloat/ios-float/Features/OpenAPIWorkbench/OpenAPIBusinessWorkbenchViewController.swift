import UIKit
import UniformTypeIdentifiers

private final class OpenApiBusinessWorkbenchViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UIDocumentPickerDelegate {
    private struct Action {
        let title: String
        let subtitle: String
        let method: String
        let path: String
        let isRisky: Bool
        let query: (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem]
        let body: (OpenApiBusinessWorkbenchViewController) -> [String: Any]?
    }

    private let accountName: String
    private let fallbackWeChatId: String
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var sections: [(title: String, actions: [Action])] = []
    private var responseSections: [(title: String, rows: [OpenApiFieldRow])] = []
    private var serverDeviceUuid = ""
    private var serverWeChatId = ""
    private var lastUploadedVoiceURL = ""
    private var lastUploadedVoiceDuration = 5
    private let fallbackDeviceUuid: String
    private let fallbackConversationId: String

    init(accountName: String, weChatId: String, deviceUuid: String = "", conversationId: String = "") {
        self.accountName = accountName
        self.fallbackWeChatId = weChatId
        self.fallbackDeviceUuid = deviceUuid
        self.fallbackConversationId = conversationId
        super.init(nibName: nil, bundle: nil)
        title = "业务接口"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "刷新", style: .plain, target: self, action: #selector(loadQuickStart)),
            UIBarButtonItem(title: "环境", style: .plain, target: self, action: #selector(openEnvironment))
        ]
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(OpenApiFieldCell.self, forCellReuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        buildActions()
        responseSections = [
            (
                title: "当前配置",
                rows: [
                    OpenApiFieldRow(key: "帐号", value: accountName, subtitle: "来自右侧当前选中帐号。"),
                    OpenApiFieldRow(key: "本地 weChatId", value: fallbackWeChatId, subtitle: "若 quick-start 未返回账号，则作为默认请求字段。"),
                    OpenApiFieldRow(key: "X-API-Key", value: OpenApiConfiguration.current.apiKey.maskedSecret(), subtitle: "真实接口鉴权。")
                ]
            )
        ]
        tableView.reloadData()
        showCircularPageLoading(title: "加载 OpenAPI")
        loadQuickStart()
    }

    @objc private func openEnvironment() {
        navigationController?.pushViewController(OpenApiEnvironmentViewController(), animated: true)
    }

    @objc private func loadQuickStart() {
        Task { @MainActor in
            do {
                let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/quick-start")
                if let root = result.jsonObject as? [String: Any] {
                    serverDeviceUuid = (root["selectedDeviceUuid"] as? String)
                        ?? ((root["devices"] as? [[String: Any]])?.first { ($0["isOnline"] as? Bool) == true }?["uuid"] as? String)
                        ?? ""
                    serverWeChatId = (root["selectedWeChatId"] as? String)
                        ?? ((root["weChatAccounts"] as? [[String: Any]])?.first?["wxid"] as? String)
                        ?? ""
                }
                responseSections = [
                    (
                        title: "Quick Start",
                        rows: OpenApiHTTPClient.rows(from: result, maxItems: 28) + [
                            OpenApiFieldRow(key: "选中 deviceUuid", value: currentDeviceUuid, subtitle: "后续需要设备的接口会优先使用这个值。"),
                            OpenApiFieldRow(key: "选中 weChatId", value: currentWeChatId, subtitle: "后续联系人、朋友圈、群、消息接口会优先使用这个值。"),
                            OpenApiFieldRow(key: "选中 conversationId", value: currentConversationId.isEmpty ? "未选择会话" : currentConversationId, subtitle: "发送小程序、网页、文件等消息会优先使用当前左侧好友或群聊。")
                        ]
                    )
                ]
            } catch {
                responseSections = [(title: "加载失败", rows: OpenApiHTTPClient.errorRows(endpoint: "/openapi/v1/quick-start", error: error))]
            }
            tableView.reloadData()
        }
    }

    private var currentWeChatId: String {
        serverWeChatId.isEmpty ? fallbackWeChatId : serverWeChatId
    }

    private var currentDeviceUuid: String {
        serverDeviceUuid.isEmpty ? fallbackDeviceUuid : serverDeviceUuid
    }

    private var currentConversationId: String {
        fallbackConversationId
    }

    private func buildActions() {
        func get(_ title: String, _ path: String, subtitle: String, query: @escaping (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem] = { _ in [] }) -> Action {
            Action(title: title, subtitle: subtitle, method: "GET", path: path, isRisky: false, query: query, body: { _ in nil })
        }
        func post(_ title: String, _ path: String, subtitle: String, risky: Bool = true, body: @escaping (OpenApiBusinessWorkbenchViewController) -> [String: Any]) -> Action {
            Action(title: title, subtitle: subtitle, method: "POST", path: path, isRisky: risky, query: { _ in [] }, body: body)
        }
        func put(_ title: String, _ path: String, subtitle: String, risky: Bool = true, body: @escaping (OpenApiBusinessWorkbenchViewController) -> [String: Any]) -> Action {
            Action(title: title, subtitle: subtitle, method: "PUT", path: path, isRisky: risky, query: { _ in [] }, body: body)
        }
        func delete(_ title: String, _ path: String, subtitle: String, risky: Bool = true, query: @escaping (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem] = { _ in [] }, body: @escaping (OpenApiBusinessWorkbenchViewController) -> [String: Any] = { _ in [:] }) -> Action {
            Action(title: title, subtitle: subtitle, method: "DELETE", path: path, isRisky: risky, query: query, body: body)
        }
        func voiceUpload(_ title: String, subtitle: String) -> Action {
            Action(title: title, subtitle: subtitle, method: "上传", path: "__openapi_voice_upload", isRisky: false, query: { _ in [] }, body: { _ in nil })
        }

        let contextQuery: (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem] = { page in
            [URLQueryItem(name: "weChatId", value: page.currentWeChatId)]
        }
        let pageQuery: (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem] = { page in
            [
                URLQueryItem(name: "weChatId", value: page.currentWeChatId),
                URLQueryItem(name: "page", value: "1"),
                URLQueryItem(name: "pageSize", value: "20")
            ]
        }
        let friendRequestQuery: (OpenApiBusinessWorkbenchViewController) -> [URLQueryItem] = { page in
            [
                URLQueryItem(name: "weChatId", value: page.currentWeChatId),
                URLQueryItem(name: "count", value: "50"),
                URLQueryItem(name: "pendingOnly", value: "false")
            ]
        }

        sections = [
            (
                title: "基础 / 设备",
                actions: [
                    get("快速启动", "/openapi/v1/quick-start", subtitle: "读取后端推荐 deviceUuid、weChatId、下一步测试项。"),
                    get("当前 OpenAPI 身份", "/openapi/v1/me", subtitle: "确认当前 X-API-Key 对应的租户与身份。"),
                    get("接入指南", "/openapi/v1/guide", subtitle: "读取服务端当前接入要求与能力说明。"),
                    get("测试计划", "/openapi/v1/test-plan", subtitle: "读取服务端建议的真实接口验收顺序。"),
                    get("能力检查", "/openapi/v1/capabilities", subtitle: "检查当前设备和微信号可用能力。") { page in
                        [
                            URLQueryItem(name: "deviceUuid", value: page.currentDeviceUuid),
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId)
                        ]
                    },
                    get("设备列表", "/openapi/v1/devices", subtitle: "读取可访问设备。"),
                    get("微信帐号", "/openapi/v1/wechat-accounts", subtitle: "读取可访问微信号。"),
                    get("最近任务", "/openapi/v1/tasks/recent", subtitle: "查看 OpenAPI 最近任务。"),
                    get("查询指定任务", "/openapi/v1/tasks/0", subtitle: "把路径末尾改成真实 taskId，查看 pending/succeeded/failed 终态。"),
                    post("重启设备微信", "/openapi/v1/devices/restart-wechat", subtitle: "重启当前设备上的微信，执行前必须确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "reason": "只发 iOS 手动重启"
                        ]
                    }
                ]
            ),
            (
                title: "微信登录会话",
                actions: [
                    get("活跃登录会话", "/openapi/v1/wechat-login/active", subtitle: "查询当前仍在进行的微信登录会话。"),
                    post("开始登录会话", "/openapi/v1/wechat-login/start", subtitle: "为当前设备创建微信登录会话。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "loginMode": "qrcode",
                            "expireSeconds": 300,
                            "remark": "只发 iOS 登录"
                        ]
                    },
                    get("查询登录会话", "/openapi/v1/wechat-login/0", subtitle: "把路径末尾改成真实 sessionId。"),
                    post("取消登录会话", "/openapi/v1/wechat-login/0/cancel", subtitle: "把路径中的 0 改成真实 sessionId。") { _ in
                        ["reason": "用户取消"]
                    }
                ]
            ),
            (
                title: "好友 / 标签 / 客户画像",
                actions: [
                    get("联系人列表", "/openapi/v1/contacts", subtitle: "按当前 weChatId 查询联系人。", query: pageQuery),
                    get("好友 wxid", "/openapi/v1/contacts/wxids", subtitle: "获取所有好友 wxid，onlyFriends=true。") { page in
                        [
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId),
                            URLQueryItem(name: "includeDeleted", value: "false"),
                            URLQueryItem(name: "onlyFriends", value: "true")
                        ]
                    },
                    get("联系人标签", "/openapi/v1/contact-labels", subtitle: "查询当前账号标签。", query: contextQuery),
                    get("好友请求", "/openapi/v1/friend-requests", subtitle: "查询好友验证请求。", query: friendRequestQuery),
                    post("拉取好友请求", "/openapi/v1/friend-requests/pull", subtitle: "从手机端拉取好友验证请求。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "startTime": 0,
                            "onlyNew": true,
                            "getAll": false
                        ]
                    },
                    post("处理好友请求", "/openapi/v1/friend-requests/handle", subtitle: "同意或拒绝好友请求；operation 可在编辑页修改。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_request_friend",
                            "friendNick": "新好友",
                            "remark": "iOS来源",
                            "replyMsg": "你好，已通过",
                            "addWithWW": false,
                            "onlyWW": false,
                            "permission": 0,
                            "operation": 1
                        ]
                    },
                    post("查找联系人", "/openapi/v1/friends/find", subtitle: "用手机号/wxid/微信号查找联系人。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "wxid_demo_friend"
                        ]
                    },
                    post("添加好友", "/openapi/v1/friends", subtitle: "支持验证语、备注、来源、画像、标签和验证图片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendWxid": "wxid_demo_friend",
                            "message": "你好，我是只发 iOS 联调号。",
                            "remark": "iOS联调客户",
                            "label": "iOS联调",
                            "customerLevel": "A",
                            "sourceChannel": "ios-app",
                            "sourceDetail": "只发 iOS OpenAPI 业务接口",
                            "profileKey": "ios_openapi_demo",
                            "faceImageUrl": "https://cc2.cx/avatar.jpg",
                            "verifyImageUrl": "https://cc2.cx/verify.jpg",
                            "scene": 17,
                            "permission": 0,
                            "purchaseHistory": "2026-07-12 iOS 接口加好友测试",
                            "socialAccounts": "douyin:zhifa_demo",
                            "notes": "加好友请求携带来源、画像、验证图片和标签映射。",
                            "mappedLabelIds": [1, 2],
                            "mappedLabelNames": ["iOS联调", "高意向"],
                            "targetKind": "friend"
                        ]
                    },
                    delete("删除好友", "/openapi/v1/friends/wxid_demo_friend", subtitle: "删除指定好友；把 path 末尾改成真实 friendId/wxid。") { page in
                        [
                            URLQueryItem(name: "deviceUuid", value: page.currentDeviceUuid),
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId)
                        ]
                    } body: { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend"
                        ]
                    },
                    post("手机号批量加好友", "/openapi/v1/friends/by-phone", subtitle: "按手机号批量添加，发送前会确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "phones": ["13800000000"],
                            "message": "你好，我是只发 iOS 联调号。",
                            "remark": "手机号联调",
                            "label": "iOS联调",
                            "permission": 0
                        ]
                    },
                    post("通讯录加好友", "/openapi/v1/friends/from-phonebook", subtitle: "从手机通讯录入口添加好友。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "message": "你好，我是只发 iOS 联调号。",
                            "count": 10,
                            "index": 0,
                            "reset": false
                        ]
                    },
                    post("群内加好友", "/openapi/v1/friends/in-chatroom", subtitle: "从群成员添加好友，需要群 id 和好友 wxid。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "friendId": "wxid_demo_member",
                            "message": "你好，我们在群里见过。",
                            "remark": "群来源",
                            "permission": 0
                        ]
                    },
                    post("名片加好友", "/openapi/v1/friends/name-card", subtitle: "通过名片消息添加好友，需要真实 msgSvrId。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": 0,
                            "message": "你好，我是只发 iOS 联调号。",
                            "remark": "名片来源"
                        ]
                    },
                    post("发送验证消息", "/openapi/v1/friends/verify", subtitle: "向待验证联系人发送好友验证。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "message": "你好，方便通过一下吗？"
                        ]
                    },
                    post("同步联系人", "/openapi/v1/contacts/sync", subtitle: "同步当前微信号联系人。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("同步标签", "/openapi/v1/contact-labels/sync", subtitle: "同步当前微信号标签。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("新建/更新标签", "/openapi/v1/contact-labels", subtitle: "创建或更新联系人标签。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "labelName": "iOS联调"
                        ]
                    },
                    delete("删除标签", "/openapi/v1/contact-labels/1", subtitle: "删除指定标签；把 path 末尾改成真实 labelId。") { page in
                        [
                            URLQueryItem(name: "deviceUuid", value: page.currentDeviceUuid),
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId)
                        ]
                    } body: { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "labelId": 1
                        ]
                    },
                    post("好友设置标签", "/openapi/v1/contacts/labels", subtitle: "给单个好友设置标签。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "labelNames": ["iOS联调", "高意向"]
                        ]
                    },
                    post("批量设置标签", "/openapi/v1/contacts/labels/batch", subtitle: "给指定好友 wxid 列表批量设置标签。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendIds": ["wxid_demo_friend"],
                            "labelNames": ["iOS联调"],
                            "mergeExisting": true,
                            "maxCount": 50
                        ]
                    },
                    post("按筛选批量打标", "/openapi/v1/contacts/labels/batch-by-filter", subtitle: "按来源、等级、画像筛选后批量设置标签。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "labelNames": ["iOS联调"],
                            "sourceChannel": "ios-app",
                            "customerLevel": "A",
                            "profileKey": "ios_openapi_demo",
                            "profileOnly": true,
                            "mergeExisting": true,
                            "maxCount": 50
                        ]
                    },
                    get("联系人详情", "/openapi/v1/contacts/1/detail", subtitle: "读取联系人详情；把 path 中 1 改成真实 contactId。") { _ in
                        [
                            URLQueryItem(name: "commonChatRoomLimit", value: "20"),
                            URLQueryItem(name: "relationLogLimit", value: "20")
                        ]
                    },
                    get("客户画像详情", "/openapi/v1/contacts/1/customer-profile", subtitle: "读取联系人客户画像；把 path 中 1 改成真实 contactId。"),
                    put("保存客户画像", "/openapi/v1/contacts/1/customer-profile", subtitle: "保存联系人画像；把 path 中 1 改成真实 contactId。") { page in
                        [
                            "weChatId": page.currentWeChatId,
                            "customerLevel": "A",
                            "sourceChannel": "ios-app",
                            "sourceDetail": "只发 iOS OpenAPI 联调",
                            "profileKey": "ios_openapi_demo",
                            "purchaseHistory": "2026-07-12 iOS 接口联调",
                            "socialAccounts": "douyin:zhifa_demo",
                            "faceImageUrl": "https://cc2.cx/avatar.jpg",
                            "notes": "由 iOS 业务接口页保存的客户画像",
                            "mappedLabelNames": ["iOS联调", "高意向"]
                        ]
                    },
                    post("保存客户画像草稿", "/openapi/v1/contacts/customer-profile-drafts", subtitle: "创建客户画像草稿，验证画像字段。") { page in
                        [
                            "weChatId": page.currentWeChatId,
                            "customerLevel": "A",
                            "sourceChannel": "ios-app",
                            "sourceDetail": "只发 iOS OpenAPI 联调",
                            "profileKey": "ios_openapi_demo",
                            "purchaseHistory": "2026-07-12 iOS 接口联调",
                            "socialAccounts": "douyin:zhifa_demo",
                            "notes": "由 iOS 业务接口页创建的测试草稿",
                            "mappedLabelNames": ["iOS联调", "高意向"]
                        ]
                    },
                    post("设置朋友圈权限", "/openapi/v1/friends/permissions", subtitle: "控制不看对方朋友圈/不让对方看我朋友圈。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "permissionMask": 0,
                            "onlyChat": false,
                            "notSeeFriendMoments": false,
                            "notLetFriendSeeMyMoments": true
                        ]
                    },
                    post("批量设置朋友圈权限", "/openapi/v1/friends/permissions/batch", subtitle: "给指定好友列表设置朋友圈可见权限。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendIds": ["wxid_demo_friend"],
                            "permissionMask": 0,
                            "onlyChat": false,
                            "notSeeFriendMoments": false,
                            "notLetFriendSeeMyMoments": true,
                            "maxCount": 50
                        ]
                    },
                    post("按标签设置朋友圈权限", "/openapi/v1/friends/permissions/batch-by-filter", subtitle: "结合分组/标签/来源，给不同来源设置不同朋友圈可见。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "filterLabelNames": ["iOS联调"],
                            "sourceChannel": "ios-app",
                            "customerLevel": "A",
                            "profileKey": "ios_openapi_demo",
                            "profileOnly": true,
                            "permissionMask": 0,
                            "onlyChat": false,
                            "notSeeFriendMoments": false,
                            "notLetFriendSeeMyMoments": true,
                            "maxCount": 50
                        ]
                    },
                    post("修改好友资料", "/openapi/v1/friends/profile", subtitle: "修改备注、描述、电话等好友资料字段。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "remark": "iOS联调客户",
                            "description": "来源：iOS OpenAPI",
                            "phone": "13800000000",
                            "tagNames": ["iOS联调"]
                        ]
                    }
                ]
            ),
            (
                title: "朋友圈 / 素材 / 计划",
                actions: [
                    get("朋友圈素材", "/openapi/v1/moments/materials", subtitle: "读取素材库。") { _ in
                        [
                            URLQueryItem(name: "skip", value: "0"),
                            URLQueryItem(name: "take", value: "20")
                        ]
                    },
                    get("批量计划", "/openapi/v1/moments/batch/plans", subtitle: "读取朋友圈批量发布计划。") { _ in
                        [
                            URLQueryItem(name: "skip", value: "0"),
                            URLQueryItem(name: "take", value: "20")
                        ]
                    },
                    post("构建朋友圈模板", "/openapi/v1/moments/template", subtitle: "安全构建模板，不直接发布。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS OpenAPI 模板校验",
                            "attachmentType": "link",
                            "linkUrl": "https://cc2.cx",
                            "linkTitle": "只发官网",
                            "linkDescription": "来自 iOS 的朋友圈模板预览",
                            "linkThumb": "https://cc2.cx/cover.jpg",
                            "linkAppId": "wx_demo_appid",
                            "linkSourceName": "只发",
                            "linkSource": "zhifa",
                            "visibleType": "public",
                            "validateVisibleLabels": false
                        ]
                    },
                    post("朋友圈图文模板", "/openapi/v1/moments/template", subtitle: "图文朋友圈模板，支持按标签/好友可见。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 图文朋友圈模板",
                            "attachmentType": "image",
                            "attachments": ["https://cc2.cx/demo.jpg"],
                            "visibleType": "label",
                            "labelNames": ["iOS联调"],
                            "friendWxids": ["wxid_demo_friend"],
                            "invisibleFriendWxids": ["wxid_blocked_friend"],
                            "validateVisibleLabels": true
                        ]
                    },
                    post("朋友圈小程序模板", "/openapi/v1/moments/template", subtitle: "小程序链接朋友圈模板，用于充值/支付中转站等场景。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 小程序朋友圈模板",
                            "attachmentType": "weapp",
                            "weAppAppId": "wx_demo_appid",
                            "weAppTitle": "只发小程序",
                            "weAppPagePath": "pages/index/index",
                            "weAppUrl": "https://cc2.cx",
                            "weAppThumb": "https://cc2.cx/cover.jpg",
                            "weAppIcon": "https://cc2.cx/icon.png",
                            "weAppSource": "zhifa",
                            "weAppSourceName": "只发",
                            "weAppSourceUsername": "gh_demo",
                            "weAppVersion": 0,
                            "weAppDisForward": false,
                            "visibleType": "public",
                            "validateVisibleLabels": false
                        ]
                    },
                    post("朋友圈公众号文章模板", "/openapi/v1/moments/template", subtitle: "公众号文章/网页卡片朋友圈模板。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 公众号文章朋友圈模板",
                            "attachmentType": "link",
                            "linkUrl": "https://cc2.cx",
                            "linkTitle": "只发 OpenAPI 文章",
                            "linkDescription": "公众号文章卡片预览",
                            "linkThumb": "https://cc2.cx/cover.jpg",
                            "linkAppId": "wx_demo_appid",
                            "linkSourceName": "只发",
                            "linkSource": "zhifa",
                            "visibleType": "public",
                            "validateVisibleLabels": false
                        ]
                    },
                    post("创建朋友圈素材", "/openapi/v1/moments/materials", subtitle: "保存一条图文/链接素材。") { page in
                        [
                            "name": "iOS OpenAPI 素材",
                            "category": "link",
                            "content": "只发 iOS 创建的朋友圈素材。",
                            "attachmentType": "link",
                            "linkUrl": "https://cc2.cx",
                            "linkTitle": "只发官网",
                            "linkDescription": "接口联调素材",
                            "linkThumb": "https://cc2.cx/cover.jpg",
                            "linkAppId": "wx_demo_appid",
                            "linkSourceName": "只发",
                            "linkSource": "zhifa",
                            "visibleType": "public",
                            "enableImmediately": false,
                            "sendSlow": true,
                            "clientRequestId": UUID().uuidString,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    get("朋友圈素材详情", "/openapi/v1/moments/materials/1/detail", subtitle: "读取素材详情；把 path 中 1 改成真实素材 ID。"),
                    put("编辑朋友圈素材", "/openapi/v1/moments/materials/1", subtitle: "更新指定素材；把 path 中 1 改成真实素材 ID。") { page in
                        [
                            "name": "iOS OpenAPI 素材更新",
                            "category": "image",
                            "content": "已在 iOS 里编辑过的朋友圈素材。",
                            "attachmentType": "image",
                            "attachments": ["https://cc2.cx/demo.jpg"],
                            "visibleType": "label",
                            "labelNames": ["iOS联调"],
                            "friendWxids": ["wxid_demo_friend"],
                            "invisibleFriendWxids": ["wxid_blocked_friend"],
                            "sendSlow": true,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("复制朋友圈素材", "/openapi/v1/moments/materials/1/copy", subtitle: "复制素材；把 path 中 1 改成真实素材 ID。", risky: false) { _ in
                        [
                            "newName": "复制的朋友圈素材"
                        ]
                    },
                    post("归档朋友圈素材", "/openapi/v1/moments/materials/1/archive", subtitle: "归档素材；把 path 中 1 改成真实素材 ID。") { _ in
                        [
                            "archived": true
                        ]
                    },
                    post("渲染批量预览", "/openapi/v1/moments/batch/render-preview", subtitle: "计划发布前预览目标和节奏。", risky: false) { _ in
                        [
                            "name": "iOS OpenAPI 批量预览",
                            "materialId": 0,
                            "targetMode": "wxids",
                            "targets": [],
                            "scheduleMode": "immediate",
                            "intervalMinSeconds": 30,
                            "intervalMaxSeconds": 90
                        ]
                    },
                    post("创建朋友圈批量计划", "/openapi/v1/moments/batch/plans", subtitle: "按目标 wxid 或标签创建自动发朋友圈计划。") { page in
                        [
                            "name": "iOS OpenAPI 批量朋友圈计划",
                            "materialId": 0,
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "targetMode": "filter",
                            "filterLabelNames": ["iOS联调"],
                            "sourceChannel": "ios-app",
                            "scheduleMode": "immediate",
                            "intervalMinSeconds": 30,
                            "intervalMaxSeconds": 90,
                            "maxCount": 20
                        ]
                    },
                    get("朋友圈计划详情", "/openapi/v1/moments/batch/plans/1", subtitle: "读取批量计划详情；把 path 中 1 改成真实 planId。"),
                    post("暂停朋友圈计划", "/openapi/v1/moments/batch/plans/1/pause", subtitle: "暂停批量计划；把 path 中 1 改成真实 planId。") { _ in
                        ["reason": "iOS 手动暂停"]
                    },
                    post("恢复朋友圈计划", "/openapi/v1/moments/batch/plans/1/resume", subtitle: "恢复批量计划；把 path 中 1 改成真实 planId。") { _ in
                        ["reason": "iOS 手动恢复"]
                    },
                    post("取消朋友圈计划", "/openapi/v1/moments/batch/plans/1/cancel", subtitle: "取消批量计划；把 path 中 1 改成真实 planId。") { _ in
                        ["reason": "iOS 手动取消"]
                    },
                    post("同步朋友圈", "/openapi/v1/moments/sync", subtitle: "同步朋友圈内容。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("同步朋友圈消息", "/openapi/v1/moments/messages/sync", subtitle: "同步朋友圈点赞/评论消息。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "startTime": 0
                        ]
                    },
                    post("发布朋友圈", "/openapi/v1/moments", subtitle: "真实发布朋友圈，发送前会确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS OpenAPI 真实发布测试",
                            "attachmentType": "link",
                            "linkUrl": "https://cc2.cx",
                            "linkTitle": "只发官网",
                            "linkDescription": "官网链接朋友圈发布",
                            "linkThumb": "https://cc2.cx/cover.jpg",
                            "linkAppId": "wx_demo_appid",
                            "linkSourceName": "只发",
                            "linkSource": "zhifa",
                            "visibleType": "public",
                            "sendSlow": true,
                            "clientRequestId": UUID().uuidString
                        ]
                    },
                    post("发布图文朋友圈", "/openapi/v1/moments", subtitle: "真实发布图文朋友圈，可结合标签控制可见。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS OpenAPI 图文朋友圈发布测试",
                            "attachmentType": "image",
                            "attachments": ["https://cc2.cx/demo.jpg"],
                            "visibleType": "label",
                            "labelNames": ["iOS联调"],
                            "friendWxids": ["wxid_demo_friend"],
                            "invisibleFriendWxids": ["wxid_blocked_friend"],
                            "sendSlow": true,
                            "clientRequestId": UUID().uuidString
                        ]
                    },
                    post("发布小程序朋友圈", "/openapi/v1/moments", subtitle: "真实发布小程序链接朋友圈。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 小程序朋友圈发布测试",
                            "attachmentType": "weapp",
                            "weAppAppId": "wx_demo_appid",
                            "weAppTitle": "只发充值中转站",
                            "weAppPagePath": "pages/index/index",
                            "weAppUrl": "https://cc2.cx",
                            "weAppThumb": "https://cc2.cx/cover.jpg",
                            "weAppIcon": "https://cc2.cx/icon.png",
                            "weAppSource": "zhifa",
                            "weAppSourceName": "只发",
                            "weAppSourceUsername": "gh_demo",
                            "weAppVersion": 0,
                            "weAppDisForward": false,
                            "visibleType": "public",
                            "sendSlow": true,
                            "clientRequestId": UUID().uuidString
                        ]
                    },
                    post("发布公众号文章朋友圈", "/openapi/v1/moments", subtitle: "真实发布公众号文章/网页卡片朋友圈。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 公众号文章朋友圈发布测试",
                            "attachmentType": "link",
                            "linkUrl": "https://cc2.cx",
                            "linkTitle": "只发 OpenAPI 文章",
                            "linkDescription": "公众号文章卡片发布",
                            "linkThumb": "https://cc2.cx/cover.jpg",
                            "linkAppId": "wx_demo_appid",
                            "linkSourceName": "只发",
                            "linkSource": "official_article",
                            "visibleType": "label",
                            "labelNames": ["iOS联调"],
                            "friendWxids": ["wxid_demo_friend"],
                            "sendSlow": true,
                            "clientRequestId": UUID().uuidString
                        ]
                    },
                    post("朋友圈详情", "/openapi/v1/moments/detail", subtitle: "按 circleId/朋友圈 ID 查看详情。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "circleId": 0,
                            "getBigMap": true
                        ]
                    },
                    post("朋友圈点赞", "/openapi/v1/moments/like", subtitle: "对朋友圈点赞或取消点赞。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "circleId": 0,
                            "like": true
                        ]
                    },
                    post("朋友圈评论", "/openapi/v1/moments/comments", subtitle: "评论朋友圈或回复评论。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "circleId": 0,
                            "content": "来自只发 iOS 的评论",
                            "replyCommentId": 0
                        ]
                    },
                    post("删除朋友圈评论", "/openapi/v1/moments/comments/delete", subtitle: "删除指定朋友圈评论。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "circleId": 0,
                            "commentId": 0
                        ]
                    }
                ]
            ),
            (
                title: "视频号 / 互动",
                actions: [
                    post("构建视频号模板", "/openapi/v1/finder/posts/template", subtitle: "安全构建视频号发布模板。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 视频号模板校验",
                            "mediaType": "video",
                            "medias": ["https://cc2.cx/demo.mp4"],
                            "cover": "https://cc2.cx/cover.jpg",
                            "includePostRequest": true
                        ]
                    },
                    post("发布视频号", "/openapi/v1/finder/posts", subtitle: "真实发布视频号内容，发送前会确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "content": "只发 iOS 视频号发布测试",
                            "mediaType": "video",
                            "medias": ["https://cc2.cx/demo.mp4"],
                            "cover": "https://cc2.cx/cover.jpg"
                        ]
                    },
                    post("视频号提及", "/openapi/v1/finder/mentions", subtitle: "读取视频号点赞/评论/关注提醒。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("视频号主页", "/openapi/v1/finder/user-page", subtitle: "打开/查询指定视频号主页。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "sphUserName": "sph_demo"
                        ]
                    },
                    post("视频号评论列表", "/openapi/v1/finder/comments/list", subtitle: "按 feedId 查询评论。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "feedId": "demo_feed",
                            "nonceId": "demo_nonce",
                            "sortType": 0
                        ]
                    }
                ]
            ),
            (
                title: "群运营",
                actions: [
                    get("群聊列表", "/openapi/v1/chatrooms", subtitle: "查询群聊。", query: pageQuery),
                    get("群成员列表", "/openapi/v1/chatrooms/demo@chatroom/members", subtitle: "查询群成员；把 path 中 demo@chatroom 改成真实群 id。", query: contextQuery),
                    get("共同群聊", "/openapi/v1/contacts/wxid_demo_friend/common-chatrooms", subtitle: "查询指定好友与当前帐号的共同群聊；把 path 中 wxid_demo_friend 改成真实 friendId。") { page in
                        [
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId),
                            URLQueryItem(name: "page", value: "1"),
                            URLQueryItem(name: "pageSize", value: "100"),
                            URLQueryItem(name: "includeDeleted", value: "false")
                        ]
                    },
                    post("同步群聊", "/openapi/v1/chatrooms/sync", subtitle: "同步当前账号群聊。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId
                        ]
                    },
                    post("创建群聊", "/openapi/v1/chatrooms", subtitle: "按 memberWxids 创建群。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "memberWxids": ["wxid_demo_friend"]
                        ]
                    },
                    post("刷新群资料", "/openapi/v1/chatrooms/refresh", subtitle: "刷新指定群资料和成员信息。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "flag": 0
                        ]
                    },
                    post("按筛选建群", "/openapi/v1/chatrooms/by-filter", subtitle: "按标签/来源筛选好友建群。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "filterLabelNames": ["iOS联调"],
                            "sourceChannel": "ios-app",
                            "search": "联调",
                            "customerLevel": "A",
                            "profileKey": "ios_openapi_demo",
                            "profileOnly": false,
                            "maxCount": 20
                        ]
                    },
                    post("邀请群成员", "/openapi/v1/chatrooms/members/invite", subtitle: "把指定 wxid 邀请进群。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "memberWxids": ["wxid_demo_friend"]
                        ]
                    },
                    post("按筛选邀请群成员", "/openapi/v1/chatrooms/members/invite-by-filter", subtitle: "按标签/来源把好友邀请进群。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "search": "联调",
                            "filterLabelNames": ["iOS联调"],
                            "customerLevel": "A",
                            "sourceChannel": "ios-app",
                            "profileKey": "ios_openapi_demo",
                            "profileOnly": false,
                            "skipExistingMembers": true,
                            "maxCount": 20
                        ]
                    },
                    post("踢出群成员", "/openapi/v1/chatrooms/members/kick", subtitle: "从群聊移出指定成员。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "memberWxids": ["wxid_demo_friend"]
                        ]
                    },
                    post("按筛选踢出群成员", "/openapi/v1/chatrooms/members/kick-by-filter", subtitle: "按标签/来源从群里移出成员。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "search": "临时",
                            "filterLabelNames": ["临时活动"],
                            "customerLevel": "C",
                            "sourceChannel": "ios-app",
                            "profileKey": "ios_openapi_demo",
                            "profileOnly": false,
                            "onlyExistingMembers": true,
                            "maxCount": 20
                        ]
                    },
                    post("修改群名称", "/openapi/v1/chatrooms/rename", subtitle: "修改指定群聊名称。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "name": "iOS联调群"
                        ]
                    },
                    post("设置群公告", "/openapi/v1/chatrooms/notice", subtitle: "发布或更新群公告。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom",
                            "notice": "只发 iOS OpenAPI 群公告联调。"
                        ]
                    },
                    post("退出群聊", "/openapi/v1/chatrooms/exit", subtitle: "当前微信号退出指定群聊。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom"
                        ]
                    },
                    post("拉取群二维码", "/openapi/v1/chatrooms/qrcode", subtitle: "获取群二维码。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "chatRoomId": "demo@chatroom"
                        ]
                    },
                    post("扫码进群", "/openapi/v1/chatrooms/join-by-qr", subtitle: "通过群二维码链接入群。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "qrUrl": "https://weixin.qq.com/g/demo"
                        ]
                    },
                    post("同意入群邀请", "/openapi/v1/chatrooms/invites/agree", subtitle: "同意加入群聊邀请。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "talker": "wxid_demo_friend",
                            "msgSvrId": 0,
                            "msgContent": ""
                        ]
                    },
                    post("审批入群邀请", "/openapi/v1/chatrooms/invites/approve", subtitle: "群主/管理员审批入群申请。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": 0,
                            "roomId": "demo@chatroom",
                            "msgContent": "",
                            "msgId": 0
                        ]
                    }
                ]
            ),
            (
                title: "消息发送 / 卡片",
                actions: [
                    get("卡片模板", "/openapi/v1/messages/card-templates", subtitle: "获取小程序、网页、公众号卡片模板。") { page in
                        [
                            URLQueryItem(name: "deviceUuid", value: page.currentDeviceUuid),
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId),
                            URLQueryItem(name: "conversationId", value: page.currentConversationId),
                            URLQueryItem(name: "thumbUrl", value: "")
                        ]
                    },
                    get("语音环境", "/openapi/v1/media/voice/environment", subtitle: "检查语音上传/发送环境。") { page in
                        [
                            URLQueryItem(name: "deviceUuid", value: page.currentDeviceUuid),
                            URLQueryItem(name: "weChatId", value: page.currentWeChatId)
                        ]
                    },
                    voiceUpload("上传语音文件", subtitle: "从本机选择 m4a/mp3/wav/amr/aac，上传后自动填入发送语音。"),
                    post("发送文本", "/openapi/v1/messages/text", subtitle: "真实发送文本消息。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "content": "只发 iOS OpenAPI 文本联调 https://cc2.cx",
                            "atIds": []
                        ]
                    },
                    post("发送图片", "/openapi/v1/messages/image", subtitle: "按 URL 发送图片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "imageUrl": "https://cc2.cx/demo.jpg"
                        ]
                    },
                    post("发送视频", "/openapi/v1/messages/video", subtitle: "按 URL 发送视频。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "videoUrl": "https://cc2.cx/demo.mp4"
                        ]
                    },
                    post("发送文件", "/openapi/v1/messages/file", subtitle: "按 URL 发送文档、PDF、Excel 等文件。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "fileName": "只发OpenAPI接口清单.xlsx",
                            "fileUrl": "https://cc2.cx/openapi-demo.xlsx"
                        ]
                    },
                    post("发送语音", "/openapi/v1/messages/voice", subtitle: "发送已上传/可访问的语音 URL。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "voiceUrl": page.lastUploadedVoiceURL.isEmpty ? "https://cc2.cx/demo.mp3" : page.lastUploadedVoiceURL,
                            "durationSeconds": page.lastUploadedVoiceDuration
                        ]
                    },
                    post("发送表情", "/openapi/v1/messages/emoji", subtitle: "按 md5 发送表情/贴纸/GIF 类消息。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "md5": "00000000000000000000000000000000"
                        ]
                    },
                    post("发送引用消息", "/openapi/v1/messages/quote", subtitle: "发送引用/回复消息，需要真实原消息 msgSvrId。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "content": "这是引用回复内容",
                            "quoteMsgSvrId": 0
                        ]
                    },
                    post("发送网页卡片", "/openapi/v1/messages/link-card", subtitle: "发送网页链接卡片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "url": "https://cc2.cx",
                            "title": "只发官网",
                            "description": "来自 iOS OpenAPI 联调页的网页卡片",
                            "thumb": "https://cc2.cx/cover.jpg",
                            "appId": "wx_demo_appid",
                            "sourceName": "只发",
                            "source": "zhifa_web"
                        ]
                    },
                    post("发送小程序卡片", "/openapi/v1/messages/weapp-card", subtitle: "发送小程序页面卡片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": page.currentConversationId,
                            "appId": "wx91d27dbf599dff74",
                            "title": ChatWindowViewController.jdMiniProgramTitle,
                            "pagePath": "pages/index/index",
                            "url": ChatWindowViewController.jdMiniProgramShareLink,
                            "shortLink": ChatWindowViewController.jdMiniProgramShareLink,
                            "weAppShortLink": ChatWindowViewController.jdMiniProgramShareLink,
                            "source": "jd_weapp",
                            "sourceName": ChatWindowViewController.jdMiniProgramSourceName,
                            "sourceUsername": "gh_45b306365c3d",
                            "version": 0,
                            "disForward": false
                        ]
                    },
                    post("发送公众号文章", "/openapi/v1/messages/official-article-card", subtitle: "发送公众号图文/文章卡片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "url": "https://cc2.cx",
                            "title": "只发 OpenAPI 文章",
                            "description": "公众号文章卡片联调",
                            "thumb": "https://cc2.cx/cover.jpg",
                            "appId": "wx_demo_appid",
                            "sourceName": "只发",
                            "source": "official_article"
                        ]
                    },
                    post("发送收藏/笔记卡片", "/openapi/v1/messages/note-card", subtitle: "发送收藏内容/聊天记录/笔记类卡片。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationId": "wxid_demo_friend",
                            "title": "只发收藏内容",
                            "description": "来自 iOS OpenAPI 的收藏/笔记卡片",
                            "thumb": "https://cc2.cx/cover.jpg",
                            "recordItem": "<recordinfo><datalist count=\"1\"></datalist></recordinfo>"
                        ]
                    },
                    post("批量发送到指定会话", "/openapi/v1/messages/batch", subtitle: "已有好友 wxid/群 id 时，混合批量发送。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "conversationIds": ["wxid_demo_friend", "demo@chatroom"],
                            "messageType": "text",
                            "content": "只发 iOS 指定会话批量消息联调",
                            "maxCount": 20
                        ]
                    },
                    post("批量发送", "/openapi/v1/messages/batch-by-filter", subtitle: "按标签/来源筛选后批量发送。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "messageType": "text",
                            "content": "只发 iOS 批量消息联调",
                            "filterLabelNames": ["iOS联调"],
                            "sourceChannel": "ios-app",
                            "maxCount": 20
                        ]
                    }
                ]
            ),
            (
                title: "支付 / 红包 / 转账",
                actions: [
                    post("钱包余额", "/openapi/v1/payments/wallet-balance", subtitle: "查询当前微信钱包余额。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "flag": 0
                        ]
                    },
                    post("红包状态", "/openapi/v1/payments/red-packets/status-by-message", subtitle: "按消息 ID 查询红包状态，需要真实 msgSvrId。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": "0"
                        ]
                    },
                    post("红包详情", "/openapi/v1/payments/red-packets/detail-by-message", subtitle: "按消息 ID 查询红包领取明细。", risky: false) { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": "0"
                        ]
                    },
                    post("发送红包", "/openapi/v1/payments/lucky-money", subtitle: "真实发红包，发送前会确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "moneyFen": 100,
                            "number": 1,
                            "wish": "恭喜发财",
                            "paymentPassword": "000000"
                        ]
                    },
                    post("发送转账", "/openapi/v1/payments/remittance", subtitle: "真实转账，发送前会确认。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "friendId": "wxid_demo_friend",
                            "moneyFen": 100,
                            "memo": "iOS OpenAPI 转账联调",
                            "paymentPassword": "000000"
                        ]
                    },
                    post("领取红包", "/openapi/v1/payments/lucky-money/take-by-message", subtitle: "按红包消息领取，需要真实 msgSvrId。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": "0"
                        ]
                    },
                    post("领取转账", "/openapi/v1/payments/transfers/take-by-message", subtitle: "按转账消息领取，需要真实 msgSvrId。") { page in
                        [
                            "deviceUuid": page.currentDeviceUuid,
                            "weChatId": page.currentWeChatId,
                            "msgSvrId": "0"
                        ]
                    }
                ]
            )
        ]
    }

    func numberOfSections(in tableView: UITableView) -> Int {
        sections.count + responseSections.count
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section < sections.count {
            return sections[section].actions.count
        }
        return responseSections[section - sections.count].rows.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        if section < sections.count {
            return sections[section].title
        }
        return responseSections[section - sections.count].title
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiFieldCell.reuseIdentifier, for: indexPath) as? OpenApiFieldCell
            ?? OpenApiFieldCell(style: .default, reuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        if indexPath.section < sections.count {
            let action = sections[indexPath.section].actions[indexPath.row]
            cell.accessoryType = .disclosureIndicator
            let key = action.path.hasPrefix("__") ? action.method : "\(action.method) \(action.path)"
            cell.configure(OpenApiFieldRow(key: key, value: action.title, subtitle: action.subtitle))
        } else {
            cell.accessoryType = .none
            cell.configure(responseSections[indexPath.section - sections.count].rows[indexPath.row])
        }
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section < sections.count else { return }
        let action = sections[indexPath.section].actions[indexPath.row]
        if action.path.hasPrefix("__") {
            run(action)
        } else {
            presentRequestEditor(for: action)
        }
    }

    private func presentRequestEditor(for action: Action) {
        let editor = OpenApiRequestEditorViewController(
            title: action.title,
            subtitle: action.subtitle,
            method: action.method,
            path: action.path,
            query: action.query(self),
            body: action.body(self),
            isRisky: action.isRisky
        ) { [weak self] request in
            self?.runEditedRequest(title: action.title, subtitle: action.subtitle, request: request)
        }
        navigationController?.pushViewController(editor, animated: true)
    }

    private func run(_ action: Action) {
        if action.path == "__openapi_voice_upload" {
            presentVoiceUploadPicker()
            return
        }
        runEditedRequest(
            title: action.title,
            subtitle: action.subtitle,
            request: OpenApiEditableRequest(
                method: action.method,
                path: action.path,
                query: action.query(self),
                body: action.body(self)
            )
        )
    }

    private func runEditedRequest(title: String, subtitle: String, request: OpenApiEditableRequest) {
        if let validationError = validateWeAppCardRequest(request) {
            responseSections = [
                (
                    title: "\(title) · 已阻止",
                    rows: [
                        OpenApiFieldRow(
                            key: "weapp-card",
                            value: "缺少真实卡片字段",
                            subtitle: validationError
                        ),
                        OpenApiFieldRow(
                            key: "建议",
                            value: "先调用 /openapi/v1/messages/card-templates",
                            subtitle: "复制后端返回 items[].payload 中的真实 appId、pagePath、url 后再发送。"
                        )
                    ]
                )
            ]
            tableView.reloadData()
            return
        }
        responseSections = [(title: "请求中", rows: [OpenApiFieldRow(key: "request", value: "\(request.method) \(request.path)", subtitle: subtitle)])]
        tableView.reloadData()
        Task { @MainActor in
            do {
                let result = try await OpenApiHTTPClient.request(
                    request.method,
                    path: request.path,
                    query: request.query,
                    body: request.body
                )
                responseSections = [
                    (title: "\(title) · 响应", rows: OpenApiHTTPClient.rows(from: result, maxItems: 40))
                ]
            } catch {
                responseSections = [
                    (title: "\(title) · 失败", rows: OpenApiHTTPClient.errorRows(endpoint: request.path, error: error))
                ]
            }
            tableView.reloadData()
        }
    }

    private func validateWeAppCardRequest(_ request: OpenApiEditableRequest) -> String? {
        guard request.path == "/openapi/v1/messages/weapp-card" else { return nil }
        guard let body = request.body as? [String: Any] else {
            return "小程序卡片不能空 body；需要真实 conversationId、appId、pagePath、url。"
        }
        var missing: [String] = []
        let conversationID = cleanedString(body["conversationId"])
        let appID = cleanedString(body["appId"])
        let pagePath = cleanedString(body["pagePath"])
        let url = cleanedString(body["url"])
        if conversationID.isEmpty { missing.append("真实 conversationId") }
        if appID.isEmpty || isDemoWeAppField(key: "appId", value: appID) { missing.append("真实 appId") }
        if pagePath.isEmpty { missing.append("真实 pagePath") }
        if url.isEmpty || isDemoWeAppField(key: "url", value: url) { missing.append("真实 url") }
        guard !missing.isEmpty else { return nil }
        return "已阻止发送：\(missing.joined(separator: "、"))。不能发送 wx_demo_appid、cc2.cx、example.com 或空字段。"
    }

    private func cleanedString(_ value: Any?) -> String {
        if let string = value as? String {
            return string.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return ""
    }

    private func isDemoWeAppField(key: String, value: String) -> Bool {
        let lowered = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if key == "appId" {
            return lowered == "wx_demo_appid" || lowered == "wx1234567890abcdef"
        }
        if key == "url" {
            guard let host = URL(string: value)?.host?.lowercased() else {
                return lowered.contains("cc2.cx") || lowered.contains("example.com")
            }
            return host == "cc2.cx"
                || host.hasSuffix(".cc2.cx")
                || host == "example.com"
                || host.hasSuffix(".example.com")
        }
        return false
    }

    private func runReceiveDiagnostic(_ action: Action) {
        responseSections = [(title: "收消息能力", rows: [
            OpenApiFieldRow(key: "状态", value: "未启用", subtitle: "当前 OpenAPI 文档没有声明聊天消息读取/历史/收件箱接口。"),
            OpenApiFieldRow(key: "主界面同步", value: "仅帐号、好友、群、好友申请、任务回执", subtitle: "不会请求未文档化的消息读取路径，也不会从 /tasks/recent 伪造来信。"),
            OpenApiFieldRow(key: "TaskResult", value: "/openapi/v1/tasks/{taskId} 与 /openapi/v1/tasks/recent", subtitle: "仅用于发送任务回执，不作为好友来信来源。")
        ])]
        tableView.reloadData()
    }

    private func presentVoiceUploadPicker() {
        let audioTypes: [UTType] = [
            .audio,
            UTType(filenameExtension: "m4a") ?? .audio,
            UTType(filenameExtension: "mp3") ?? .audio,
            UTType(filenameExtension: "wav") ?? .audio,
            UTType(filenameExtension: "amr") ?? .audio,
            UTType(filenameExtension: "aac") ?? .audio
        ]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: audioTypes, asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        uploadSelectedVoiceFile(url)
    }

    private func uploadSelectedVoiceFile(_ fileURL: URL) {
        responseSections = [
            (
                title: "上传语音文件",
                rows: [
                    OpenApiFieldRow(key: "file", value: fileURL.lastPathComponent, subtitle: "正在调用 /openapi/v1/media/voice。")
                ]
            )
        ]
        tableView.reloadData()

        Task { @MainActor in
            let scoped = fileURL.startAccessingSecurityScopedResource()
            defer {
                if scoped {
                    fileURL.stopAccessingSecurityScopedResource()
                }
            }

            do {
                let result = try await OpenApiHTTPClient.uploadVoiceFile(fileURL)
                let uploadedURL = openApiVoiceURL(from: result.jsonObject) ?? ""
                let duration = openApiVoiceDuration(from: result.jsonObject) ?? lastUploadedVoiceDuration
                if !uploadedURL.isEmpty {
                    lastUploadedVoiceURL = uploadedURL
                    lastUploadedVoiceDuration = max(1, duration)
                }
                responseSections = [
                    (
                        title: "上传语音文件 · 响应",
                        rows: [
                            OpenApiFieldRow(key: "voiceUrl", value: uploadedURL.isEmpty ? "未返回" : uploadedURL, subtitle: "发送语音 action 会优先使用最近一次上传结果。"),
                            OpenApiFieldRow(key: "durationSeconds", value: "\(max(1, duration))", subtitle: "发送语音时带给 /openapi/v1/messages/voice。")
                        ] + OpenApiHTTPClient.rows(from: result, maxItems: 32)
                    )
                ]
            } catch {
                responseSections = [
                    (title: "上传语音文件 · 失败", rows: OpenApiHTTPClient.errorRows(endpoint: "/openapi/v1/media/voice", error: error))
                ]
            }
            tableView.reloadData()
        }
    }

    private func openApiVoiceURL(from object: Any?) -> String? {
        if let root = object as? [String: Any] {
            if let value = firstCleanedVoiceString([
                root["voiceUrl"]
            ]) {
                return value
            }
            if let data = root["data"] as? [String: Any] {
                return firstCleanedVoiceString([
                    data["voiceUrl"]
                ])
            }
        }
        return nil
    }

    private func openApiVoiceDuration(from object: Any?) -> Int? {
        if let root = object as? [String: Any] {
            if let value = intValue(root["durationSeconds"]) ?? intValue(root["duration"]) {
                return value
            }
            if let data = root["data"] as? [String: Any] {
                return intValue(data["durationSeconds"]) ?? intValue(data["duration"])
            }
        }
        return nil
    }

    private func firstCleanedVoiceString(_ values: [Any?]) -> String? {
        for value in values {
            if let text = stringValue(value) {
                return text
            }
        }
        return nil
    }

    private func stringValue(_ value: Any?) -> String? {
        if let text = value as? String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return nil
    }

    private func intValue(_ value: Any?) -> Int? {
        if let number = value as? NSNumber {
            return number.intValue
        }
        if let text = stringValue(value), let int = Int(text) {
            return int
        }
        return nil
    }
}

