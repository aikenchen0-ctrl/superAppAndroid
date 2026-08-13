import XCTest
@testable import ios_float

final class OpenAPISocialManagementServiceTests: XCTestCase {
    private final class RequestSpy {
        struct Call {
            let method: String
            let path: String
            let query: [URLQueryItem]
            let body: [String: Any]
        }

        var calls: [Call] = []
        var responses: [OpenApiHTTPResult] = []

        func request(_ method: String, _ path: String, _ query: [URLQueryItem], _ body: Any?) async throws -> OpenApiHTTPResult {
            calls.append(Call(method: method, path: path, query: query, body: body as? [String: Any] ?? [:]))
            return responses.removeFirst()
        }
    }

    private let account = OpenApiSocialAccount(deviceUUID: "device-1", weChatID: "wxid_owner")

    func testRefreshChatRoomBuildsValidatedTaskRequest() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "refresh-1"])]
        var validatedAction = ""
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { _, action in
                validatedAction = action
                return "refresh-1"
            }
        )

        let taskID = try await service.refreshChatRoom(
            account: account,
            chatRoomID: "123@chatroom"
        )

        XCTAssertEqual(taskID, "refresh-1")
        XCTAssertEqual(validatedAction, "刷新群资料")
        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "POST")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/chatrooms/refresh")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["chatRoomId"] as? String, "123@chatroom")
        XCTAssertEqual(spy.calls[0].body["flag"] as? Int, 1)
    }

    func testFetchChatRoomQRCodeBuildsValidatedRequestWithoutPolling() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "qr-1"])]
        let service = OpenApiSocialManagementService(request: spy.request)

        let response = try await service.fetchChatRoomQRCode(
            account: account,
            chatRoomID: "123@chatroom"
        )

        XCTAssertEqual(response.statusCode, 200)
        XCTAssertEqual(spy.calls[0].method, "POST")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/chatrooms/qrcode")
        XCTAssertEqual(spy.calls[0].body["chatRoomId"] as? String, "123@chatroom")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
    }

    func testReadMethodsBuildAccountScopedQueries() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["items": []]), result(["items": []]), result(["items": []]), result(["items": []])]
        let service = OpenApiSocialManagementService(request: spy.request)

        _ = try await service.listContacts(account: account, page: 0, pageSize: 500)
        _ = try await service.listChatRooms(account: account, page: 2, pageSize: 100)
        _ = try await service.listChatRoomMembers(account: account, chatRoomID: "123@chatroom", page: 1, pageSize: 300)
        _ = try await service.listFriendRequests(account: account, count: 200)

        XCTAssertEqual(spy.calls.map(\.path), [
            "/openapi/v1/contacts",
            "/openapi/v1/chatrooms",
            "/openapi/v1/chatrooms/123@chatroom/members",
            "/openapi/v1/friend-requests"
        ])
        XCTAssertEqual(spy.calls[0].query.first?.name, "weChatId")
        XCTAssertEqual(spy.calls[0].query.first?.value, "wxid_owner")
        XCTAssertEqual(spy.calls[0].query.first(where: { $0.name == "page" })?.value, "1")
        XCTAssertEqual(spy.calls[0].query.first(where: { $0.name == "pageSize" })?.value, "100")
        XCTAssertEqual(spy.calls[2].query.first(where: { $0.name == "pageSize" })?.value, "200")
        XCTAssertEqual(spy.calls[3].query.first(where: { $0.name == "count" })?.value, "100")
    }

    func testPullFriendRequestsBuildsValidatedTaskRequest() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "pull-1"])]
        var validatedAction = ""
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { _, action in
                validatedAction = action
                return "pull-1"
            }
        )

        let taskID = try await service.pullFriendRequests(
            account: account,
            onlyNew: false
        )

        XCTAssertEqual(taskID, "pull-1")
        XCTAssertEqual(validatedAction, "拉取好友申请")
        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "POST")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/friend-requests/pull")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["startTime"] as? Int, 0)
        XCTAssertEqual(spy.calls[0].body["onlyNew"] as? Bool, false)
        XCTAssertEqual(spy.calls[0].body["getAll"] as? Bool, true)
    }

    func testRenameChatRoomBuildsValidatedTaskRequest() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "task-1"])]
        var validatedAction = ""
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { _, action in validatedAction = action; return "task-1" }
        )

        let taskID = try await service.renameChatRoom(
            account: account,
            chatRoomID: "123@chatroom",
            name: " 新群名 "
        )

        XCTAssertEqual(taskID, "task-1")
        XCTAssertEqual(validatedAction, "修改群名称")
        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "POST")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/chatrooms/rename")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["chatRoomId"] as? String, "123@chatroom")
        XCTAssertEqual(spy.calls[0].body["name"] as? String, "新群名")
    }

    func testInvalidChatRoomIsRejectedBeforeNetworkRequest() async {
        let spy = RequestSpy()
        let service = OpenApiSocialManagementService(request: spy.request)

        do {
            _ = try await service.exitChatRoom(account: account, chatRoomID: "not-a-group")
            XCTFail("Expected invalid chat room error")
        } catch {
            XCTAssertEqual(error as? OpenApiSocialManagementError, .invalidChatRoomID("not-a-group"))
        }
        XCTAssertTrue(spy.calls.isEmpty)
    }

    func testInviteMembersRemovesDuplicatesAndRejectsChatroomIDs() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "task-2"])]
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { _, _ in "task-2" }
        )

        _ = try await service.inviteMembers(
            account: account,
            chatRoomID: "123@chatroom",
            memberWxids: [" wxid_a ", "wxid_a", "wxid_b"]
        )

        XCTAssertEqual(spy.calls[0].body["memberWxids"] as? [String], ["wxid_a", "wxid_b"])

        do {
            _ = try await service.inviteMembers(
                account: account,
                chatRoomID: "123@chatroom",
                memberWxids: ["456@chatroom"]
            )
            XCTFail("Expected invalid member error")
        } catch {
            XCTAssertEqual(error as? OpenApiSocialManagementError, .invalidFriendID("456@chatroom"))
        }
        XCTAssertEqual(spy.calls.count, 1)
    }

    func testAddFriendFromChatRoomUsesGroupContextAndValidatesFriendID() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "add-from-group-1"])]
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { _, _ in "add-from-group-1" }
        )

        let taskID = try await service.addFriendFromChatRoom(
            account: account,
            chatRoomID: "123@chatroom",
            friendWxid: " wxid_friend ",
            message: " ",
            remark: "群备注"
        )

        XCTAssertEqual(taskID, "add-from-group-1")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/friends/in-chatroom")
        XCTAssertEqual(spy.calls[0].body["chatRoomId"] as? String, "123@chatroom")
        XCTAssertEqual(spy.calls[0].body["friendId"] as? String, "wxid_friend")
        XCTAssertEqual(spy.calls[0].body["message"] as? String, "你好，我们在群里见过。")
        XCTAssertEqual(spy.calls[0].body["remark"] as? String, "群备注")

        do {
            _ = try await service.addFriendFromChatRoom(
                account: account,
                chatRoomID: "123@chatroom",
                friendWxid: "123@chatroom"
            )
            XCTFail("Expected invalid friend id error")
        } catch {
            XCTAssertEqual(error as? OpenApiSocialManagementError, .invalidFriendID("123@chatroom"))
        }
        XCTAssertEqual(spy.calls.count, 1)
    }

    func testFriendLookupPollsAcceptedTaskUntilWxidIsReturned() async throws {
        let spy = RequestSpy()
        spy.responses = [
            result(["taskId": "lookup-1"]),
            result(["data": ["status": "running"]]),
            result(["result": ["friendWxid": "wxid_found"]])
        ]
        var sleeps: [UInt64] = []
        let service = OpenApiSocialManagementService(
            request: spy.request,
            sleep: { sleeps.append($0) }
        )

        let wxid = try await service.findFriendWxid(account: account, target: "wechat-name")

        XCTAssertEqual(wxid, "wxid_found")
        XCTAssertEqual(spy.calls.map(\.path), [
            "/openapi/v1/friends/find",
            "/openapi/v1/tasks/lookup-1",
            "/openapi/v1/tasks/lookup-1"
        ])
        XCTAssertEqual(sleeps.count, 1)
    }

    func testAddFriendAndAcceptRequestUseBackendContract() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "add-1"]), result(["taskId": "accept-1"])]
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { result, _ in
                (result.jsonObject as? [String: String])?["taskId"] ?? ""
            }
        )
        var options = OpenApiFriendAddOptions()
        options.remark = "客户备注"
        options.labelNames = ["重点", "成交"]

        _ = try await service.addFriend(account: account, friendWxid: "wxid_friend", options: options)
        _ = try await service.handleFriendRequest(
            account: account,
            friendWxid: "wxid_requester",
            friendNickname: "申请人",
            decision: .accept,
            remark: "已通过",
            reply: "你好"
        )

        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/friends")
        XCTAssertEqual(spy.calls[0].body["friendWxid"] as? String, "wxid_friend")
        XCTAssertEqual(spy.calls[0].body["mappedLabelNames"] as? [String], ["重点", "成交"])
        XCTAssertEqual(spy.calls[1].path, "/openapi/v1/friend-requests/handle")
        XCTAssertEqual(spy.calls[1].body["operation"] as? Int, 1)
        XCTAssertEqual(spy.calls[1].body["friendId"] as? String, "wxid_requester")
        XCTAssertEqual(spy.calls[1].body["replyMsg"] as? String, "你好")
    }

    func testPhoneAddAndDeleteFriendUseAccountScopedContracts() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "phone-add-1"]), result(["taskId": "delete-1"])]
        let service = OpenApiSocialManagementService(
            request: spy.request,
            validateTask: { result, _ in
                (result.jsonObject as? [String: String])?["taskId"] ?? ""
            }
        )

        _ = try await service.addFriendByPhone(
            account: account,
            phone: "13800138000",
            options: OpenApiFriendAddOptions(verificationMessage: "你好")
        )
        _ = try await service.deleteFriend(account: account, friendID: "wxid_friend")

        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/friends/by-phone")
        XCTAssertEqual(spy.calls[0].body["phones"] as? [String], ["13800138000"])
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[1].method, "DELETE")
        XCTAssertEqual(spy.calls[1].path, "/openapi/v1/friends/wxid_friend")
        XCTAssertEqual(spy.calls[1].query.first(where: { $0.name == "deviceUuid" })?.value, "device-1")
        XCTAssertEqual(spy.calls[1].query.first(where: { $0.name == "weChatId" })?.value, "wxid_owner")
    }

    func testMessageServiceBuildsQuoteRequestWithRealServerMessageID() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "quote-1"])]
        var validatedAction = ""
        let service = OpenApiMessageService(
            request: spy.request,
            validateTask: { _, action in
                validatedAction = action
                return "quote-1"
            }
        )

        let taskID = try await service.sendQuote(
            account: account,
            conversationID: "wxid_friend",
            content: "引用回复",
            quoteMessageServerID: " 9001 "
        )

        XCTAssertEqual(taskID, "quote-1")
        XCTAssertEqual(validatedAction, "发送引用消息")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/messages/quote")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["conversationId"] as? String, "wxid_friend")
        XCTAssertEqual(spy.calls[0].body["content"] as? String, "引用回复")
        XCTAssertEqual(spy.calls[0].body["quoteMsgSvrId"] as? String, "9001")
    }

    func testMessageServiceExtractsNestedMediaDownloadURL() async throws {
        let spy = RequestSpy()
        spy.responses = [result([
            "data": [
                "media": [
                    "downloadUrl": "https://media.example.test/message-9001.gif",
                    "mimeType": "image/gif",
                    "fileName": "message-9001.gif"
                ]
            ]
        ])]
        let service = OpenApiMessageService(request: spy.request)

        let resource = try await service.fetchMedia(account: account, messageID: " 9001 ")

        XCTAssertEqual(spy.calls[0].method, "GET")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/messages/9001/media")
        XCTAssertEqual(spy.calls[0].query.first?.name, "weChatId")
        XCTAssertEqual(resource.url.absoluteString, "https://media.example.test/message-9001.gif")
        XCTAssertEqual(resource.mimeType, "image/gif")
        XCTAssertEqual(resource.fileName, "message-9001.gif")
    }

    func testFinderTemplateBuildsRealAccountAndMediaRequest() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["valid": true])]
        let service = OpenApiFinderService(request: spy.request)
        let options = OpenApiFinderPostOptions(
            content: "门店活动",
            mediaURLs: ["https://media.example.test/store.mp4"],
            coverURL: "https://media.example.test/store.jpg",
            poiName: "门店",
            poiAddress: "上海"
        )

        _ = try await service.validatePost(
            account: OpenApiFinderAccount(deviceUUID: "device-1", weChatID: "wxid_owner"),
            options: options
        )

        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "POST")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/finder/posts/template")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["content"] as? String, "门店活动")
        XCTAssertEqual(spy.calls[0].body["mediaType"] as? String, "video")
        XCTAssertEqual(spy.calls[0].body["medias"] as? [String], ["https://media.example.test/store.mp4"])
        XCTAssertEqual(spy.calls[0].body["cover"] as? String, "https://media.example.test/store.jpg")
        XCTAssertEqual(spy.calls[0].body["poi"] as? [String: String], ["name": "门店", "address": "上海"])
    }

    func testFinderPublishWaitsForTaskTerminalState() async throws {
        let spy = RequestSpy()
        spy.responses = [result(["taskId": "finder-1"])]
        var validatedAction = ""
        let service = OpenApiFinderService(
            request: spy.request,
            validateTask: { _, action in
                validatedAction = action
                return "finder-1"
            }
        )

        let response = try await service.publish(
            account: OpenApiFinderAccount(deviceUUID: "device-1", weChatID: "wxid_owner"),
            options: OpenApiFinderPostOptions(
                content: "真实发布",
                mediaURLs: ["https://media.example.test/store.mp4"]
            )
        )

        XCTAssertEqual(response.statusCode, 200)
        XCTAssertEqual(validatedAction, "发布视频号内容")
        XCTAssertEqual(spy.calls.map(\.path), ["/openapi/v1/finder/posts"])
    }

    func testFinderReadEndpointsUseRealPayloads() async throws {
        let spy = RequestSpy()
        spy.responses = [result([:]), result([:]), result([:])]
        let service = OpenApiFinderService(request: spy.request)
        let account = OpenApiFinderAccount(deviceUUID: "device-1", weChatID: "wxid_owner")

        _ = try await service.fetchMentions(account: account)
        _ = try await service.fetchUserPage(account: account, sphUserName: "sph_user")
        _ = try await service.fetchComments(account: account, feedID: "feed-1", nonceID: "nonce-1", sortType: 1)

        XCTAssertEqual(spy.calls.map(\.path), [
            "/openapi/v1/finder/mentions",
            "/openapi/v1/finder/user-page",
            "/openapi/v1/finder/comments/list"
        ])
        XCTAssertEqual(spy.calls[1].body["sphUserName"] as? String, "sph_user")
        XCTAssertEqual(spy.calls[2].body["feedId"] as? String, "feed-1")
        XCTAssertEqual(spy.calls[2].body["nonceId"] as? String, "nonce-1")
        XCTAssertEqual(spy.calls[2].body["sortType"] as? Int, 1)
    }

    func testFinderRejectsDemoOrLocalMediaBeforeNetworkRequest() async {
        let spy = RequestSpy()
        let service = OpenApiFinderService(request: spy.request)

        do {
            _ = try await service.validatePost(
                account: OpenApiFinderAccount(weChatID: "wxid_owner"),
                options: OpenApiFinderPostOptions(content: "内容", mediaURLs: ["media.demo/file.mp4"])
            )
            XCTFail("Expected invalid media URL")
        } catch {
            XCTAssertEqual(error as? OpenApiFinderServiceError, .invalidMediaURL("media.demo/file.mp4"))
        }
        XCTAssertTrue(spy.calls.isEmpty)
    }

    func testFinderDoesNotPublishAfterRejectedTemplate() async {
        let spy = RequestSpy()
        spy.responses = [result(["success": false, "message": "媒体不可访问"])]
        let service = OpenApiFinderService(request: spy.request)

        do {
            _ = try await service.validatePost(
                account: OpenApiFinderAccount(weChatID: "wxid_owner"),
                options: OpenApiFinderPostOptions(
                    content: "内容",
                    mediaURLs: ["https://media.example.test/store.mp4"]
                )
            )
            XCTFail("Expected template rejection")
        } catch {
            XCTAssertEqual(error as? OpenApiFinderServiceError, .templateRejected("媒体不可访问"))
        }
        XCTAssertEqual(spy.calls.map(\.path), ["/openapi/v1/finder/posts/template"])
    }

    private func result(_ object: Any?) -> OpenApiHTTPResult {
        OpenApiHTTPResult(statusCode: 200, endpoint: "test", method: "POST", jsonObject: object, rawText: "")
    }
}

final class OpenApiPaymentServiceTests: XCTestCase {
    private final class RequestSpy {
        struct Call {
            let method: String
            let path: String
            let body: [String: Any]
        }

        var calls: [Call] = []
        var responses: [OpenApiHTTPResult] = []

        func request(_ method: String, _ path: String, _ query: [URLQueryItem], _ body: Any?) async throws -> OpenApiHTTPResult {
            calls.append(Call(method: method, path: path, body: body as? [String: Any] ?? [:]))
            return responses.removeFirst()
        }
    }

    private let account = OpenApiSocialAccount(deviceUUID: "device-1", weChatID: "wxid_owner")

    func testReadStatusAndDetailUseMessageServerID() async throws {
        let spy = RequestSpy()
        spy.responses = [
            OpenApiHTTPResult(statusCode: 200, endpoint: "status", method: "POST", jsonObject: ["status": "waiting"], rawText: ""),
            OpenApiHTTPResult(statusCode: 200, endpoint: "detail", method: "POST", jsonObject: ["items": []], rawText: "")
        ]
        let service = OpenApiPaymentService(request: spy.request)

        _ = try await service.redPacketStatus(account: account, messageServerID: " 9001 ")
        _ = try await service.redPacketDetail(account: account, messageServerID: "9001")

        XCTAssertEqual(spy.calls.map(\.path), [
            "/openapi/v1/payments/red-packets/status-by-message",
            "/openapi/v1/payments/red-packets/detail-by-message"
        ])
        XCTAssertEqual(spy.calls[0].body["msgSvrId"] as? String, "9001")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
    }

    func testTakeEndpointsValidateTaskAndRejectMissingMessageID() async throws {
        let spy = RequestSpy()
        spy.responses = [OpenApiHTTPResult(statusCode: 200, endpoint: "take", method: "POST", jsonObject: ["taskId": "take-1"], rawText: "")]
        let service = OpenApiPaymentService(
            request: spy.request,
            validateTask: { _, action in
                XCTAssertEqual(action, "领取红包")
                return "take-1"
            }
        )

        let taskID = try await service.takeRedPacket(account: account, messageServerID: "9001")
        XCTAssertEqual(taskID, "take-1")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/payments/lucky-money/take-by-message")

        do {
            _ = try await service.takeTransfer(account: account, messageServerID: " ")
            XCTFail("Expected missing message server ID")
        } catch {
            XCTAssertEqual(error as? OpenApiSocialManagementError, .missingField("msgSvrId"))
        }
        XCTAssertEqual(spy.calls.count, 1)
    }
}

final class OpenApiCustomerProfileServiceTests: XCTestCase {
    private final class RequestSpy {
        struct Call {
            let method: String
            let path: String
            let query: [URLQueryItem]
            let body: [String: Any]
        }

        var calls: [Call] = []
        var response = OpenApiHTTPResult(
            statusCode: 200,
            endpoint: "test",
            method: "GET",
            jsonObject: nil,
            rawText: ""
        )

        func request(_ method: String, _ path: String, _ query: [URLQueryItem], _ body: Any?) async throws -> OpenApiHTTPResult {
            calls.append(Call(method: method, path: path, query: query, body: body as? [String: Any] ?? [:]))
            return response
        }
    }

    func testLoadBuildsReadOnlyRequestAndMapsNestedProfile() async throws {
        let spy = RequestSpy()
        spy.response = OpenApiHTTPResult(
            statusCode: 200,
            endpoint: "test",
            method: "GET",
            jsonObject: [
                "data": [
                    "customerProfile": [
                        "customerLevel": "A",
                        "sourceChannel": "门店",
                        "sourceDetail": "活动二维码",
                        "profileKey": "高意向",
                        "purchaseHistory": "订单 3 笔",
                        "socialAccounts": "视频号",
                        "notes": "下周回访",
                        "mobilePhone": "13800000000",
                        "labels": [["labelName": "重点"], ["name": "复购"]]
                    ]
                ]
            ],
            rawText: ""
        )

        let profile = try await OpenApiCustomerProfileService.load(
            contactID: " contact/123 ",
            weChatID: "wxid_owner",
            request: spy.request
        )

        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "GET")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/contacts/contact123/customer-profile")
        XCTAssertEqual(spy.calls[0].query, [URLQueryItem(name: "weChatId", value: "wxid_owner")])
        XCTAssertTrue(spy.calls[0].body.isEmpty)
        XCTAssertEqual(profile.customerLevel, "A")
        XCTAssertEqual(profile.sourceChannel, "门店")
        XCTAssertEqual(profile.sourceDetail, "活动二维码")
        XCTAssertEqual(profile.profileKey, "高意向")
        XCTAssertEqual(profile.purchaseHistory, "订单 3 笔")
        XCTAssertEqual(profile.socialAccounts, "视频号")
        XCTAssertEqual(profile.notes, "下周回访")
        XCTAssertEqual(profile.phone, "13800000000")
        XCTAssertEqual(profile.mappedLabelNames, ["重点", "复购"])
    }

    func testSaveBuildsExpectedPutBodyWithoutUsingNetwork() async throws {
        let spy = RequestSpy()
        let profile = OpenApiCustomerProfile(
            customerLevel: "B",
            sourceChannel: "转介绍",
            sourceDetail: "老客户",
            profileKey: "待跟进",
            purchaseHistory: "暂无",
            socialAccounts: "wx-channel",
            notes: "仅测试请求构造",
            phone: "13900000000",
            mappedLabelNames: ["新客", "意向"]
        )

        try await OpenApiCustomerProfileService.save(
            profile,
            contactID: "contact-1",
            weChatID: "wxid_owner",
            request: spy.request
        )

        XCTAssertEqual(spy.calls.count, 1)
        XCTAssertEqual(spy.calls[0].method, "PUT")
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/contacts/contact-1/customer-profile")
        XCTAssertTrue(spy.calls[0].query.isEmpty)
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["customerLevel"] as? String, "B")
        XCTAssertEqual(spy.calls[0].body["sourceChannel"] as? String, "转介绍")
        XCTAssertEqual(spy.calls[0].body["sourceDetail"] as? String, "老客户")
        XCTAssertEqual(spy.calls[0].body["profileKey"] as? String, "待跟进")
        XCTAssertEqual(spy.calls[0].body["purchaseHistory"] as? String, "暂无")
        XCTAssertEqual(spy.calls[0].body["socialAccounts"] as? String, "wx-channel")
        XCTAssertEqual(spy.calls[0].body["notes"] as? String, "仅测试请求构造")
        XCTAssertEqual(spy.calls[0].body["phone"] as? String, "13900000000")
        XCTAssertEqual(spy.calls[0].body["mappedLabelNames"] as? [String], ["新客", "意向"])
    }

    func testLoadResolvesLatestContactIDAfterCachedIDReturns404() async throws {
        var paths: [String] = []
        let profile = try await OpenApiCustomerProfileService.load(
            contactID: "old-id",
            friendID: "wxid_friend",
            weChatID: "wxid_owner",
            request: { method, path, _, _ in
                paths.append(path)
                if path == "/openapi/v1/contacts/old-id/customer-profile" {
                    throw OpenApiHTTPClient.ClientError.badStatus(404, "联系人不存在或已删除")
                }
                let object: Any?
                if path == "/openapi/v1/contacts" {
                    object = ["data": ["items": [["id": 99, "wxid": "wxid_friend"]]]]
                } else {
                    object = ["data": ["customerLevel": "A", "mappedLabelNames": ["重点"]]]
                }
                return OpenApiHTTPResult(statusCode: 200, endpoint: path, method: method, jsonObject: object, rawText: "")
            }
        )

        XCTAssertEqual(paths, [
            "/openapi/v1/contacts/old-id/customer-profile",
            "/openapi/v1/contacts",
            "/openapi/v1/contacts/99/customer-profile"
        ])
        XCTAssertEqual(profile.customerLevel, "A")
        XCTAssertEqual(profile.mappedLabelNames, ["重点"])
    }
}

final class OpenApiContactLabelsPermissionsServiceTests: XCTestCase {
    private final class RequestSpy: @unchecked Sendable {
        struct Call {
            let method: String
            let path: String
            let query: [URLQueryItem]
            let body: [String: Any]
        }

        private let lock = NSLock()
        private var storedCalls: [Call] = []
        var calls: [Call] { lock.withLock { storedCalls } }

        func request(_ method: String, _ path: String, _ query: [URLQueryItem], _ body: Any?) async throws -> OpenApiHTTPResult {
            lock.withLock {
                storedCalls.append(Call(method: method, path: path, query: query, body: body as? [String: Any] ?? [:]))
            }
            let object: Any?
            switch path {
            case "/openapi/v1/contact-labels":
                object = ["data": ["items": [["labelName": "重点"], ["name": "复购"]]]]
            case "/openapi/v1/contacts/contact-1/detail":
                object = ["data": ["contact": ["permissions": [
                    "onlyChat": false,
                    "notSeeFriendMoments": true,
                    "notLetFriendSeeMyMoments": false
                ]]]]
            case "/openapi/v1/contacts/contact-1/customer-profile":
                object = ["data": ["mappedLabelNames": ["重点"]]]
            default:
                object = ["taskId": "task-test"]
            }
            return OpenApiHTTPResult(statusCode: 200, endpoint: path, method: method, jsonObject: object, rawText: "")
        }
    }

    func testLoadCombinesLabelsProfileAndContactPermissions() async throws {
        let spy = RequestSpy()
        let service = OpenApiContactLabelsPermissionsService(request: spy.request)

        let snapshot = try await service.load(contactID: "contact-1", friendID: "wxid_friend", weChatID: "wxid_owner")

        XCTAssertEqual(Set(spy.calls.map(\.path)), [
            "/openapi/v1/contact-labels",
            "/openapi/v1/contacts/contact-1/detail",
            "/openapi/v1/contacts/contact-1/customer-profile"
        ])
        XCTAssertEqual(snapshot.availableLabels, ["重点", "复购"])
        XCTAssertEqual(snapshot.selectedLabels, ["重点"])
        XCTAssertEqual(snapshot.onlyChat, false)
        XCTAssertEqual(snapshot.notSeeFriendMoments, true)
        XCTAssertEqual(snapshot.notLetFriendSeeMyMoments, false)
    }

    func testMissingPermissionFieldsRemainUnknown() {
        let root = OpenApiContactLabelsPermissionsService.findPermissionDictionary(in: ["data": ["contact": ["nickname": "测试"]]])
        XCTAssertNil(root)
        XCTAssertNil(OpenApiContactLabelsPermissionsService.bool(in: [:], keys: ["onlyChat"]))
    }

    func testSaveRequestsUseRealFriendContractWithoutNetwork() async throws {
        let spy = RequestSpy()
        var actions: [String] = []
        let service = OpenApiContactLabelsPermissionsService(
            request: spy.request,
            validateTask: { _, action in actions.append(action); return "task-test" }
        )
        let account = OpenApiSocialAccount(deviceUUID: "device-1", weChatID: "wxid_owner")

        try await service.saveLabels(["复购", "重点"], account: account, friendID: "wxid_friend")
        try await service.savePermissions(
            onlyChat: false,
            notSeeFriendMoments: true,
            notLetFriendSeeMyMoments: false,
            account: account,
            friendID: "wxid_friend"
        )
        try await service.createLabel(" 测试 ", account: account)

        XCTAssertEqual(actions, ["设置联系人标签", "设置朋友圈权限"])
        XCTAssertEqual(spy.calls[0].path, "/openapi/v1/contacts/labels")
        XCTAssertEqual(spy.calls[0].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[0].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[0].body["friendId"] as? String, "wxid_friend")
        XCTAssertEqual(spy.calls[0].body["labelNames"] as? [String], ["复购", "重点"])
        XCTAssertEqual(spy.calls[1].path, "/openapi/v1/friends/permissions")
        XCTAssertEqual(spy.calls[1].body["onlyChat"] as? Bool, false)
        XCTAssertEqual(spy.calls[1].body["notSeeFriendMoments"] as? Bool, true)
        XCTAssertEqual(spy.calls[1].body["notLetFriendSeeMyMoments"] as? Bool, false)
        XCTAssertEqual(spy.calls[2].path, "/openapi/v1/contact-labels")
        XCTAssertEqual(spy.calls[2].body["deviceUuid"] as? String, "device-1")
        XCTAssertEqual(spy.calls[2].body["weChatId"] as? String, "wxid_owner")
        XCTAssertEqual(spy.calls[2].body["labelName"] as? String, "测试")
    }
}
