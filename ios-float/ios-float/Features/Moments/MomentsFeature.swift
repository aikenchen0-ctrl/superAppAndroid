import UIKit
@preconcurrency import AVFoundation
import Photos
import PhotosUI
import WebKit

struct MomentComment: Codable {
    let id: Int64?
    let author: String
    let authorWxid: String?
    let replyTo: String?
    let text: String

    init(id: Int64? = nil, author: String, authorWxid: String? = nil, replyTo: String? = nil, text: String) {
        self.id = id
        self.author = author
        self.authorWxid = authorWxid
        self.replyTo = replyTo
        self.text = text
    }
}

struct MomentPost: Codable {
    let id: UUID
    let author: ChatParticipant
    var text: String
    var timeText: String
    var linkTitle: String?
    var linkURL: String?
    var imageSymbols: [String]
    var imageURLs: [URL]
    var videoURLs: [URL]
    var locationText: String?
    var visibilityText: String
    var remindNames: [String]
    var likes: [String]
    var comments: [MomentComment]
    var authorWxid: String? = nil
    var circleId: Int64? = nil
    var publishTime: Int64? = nil
}

struct OpenApiMomentSyncContext {
    let weChatId: String
    let deviceUuid: String
    let accountName: String
    let friendWxids: Set<String>
    let friendDisplayNames: [String: String]
    let friendAvatarURLs: [String: String]
}

final class MomentsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UITableViewDataSourcePrefetching {
    var onOpenLink: ((String) -> Void)?
    var onNotice: ((String) -> Void)?

    private let owner: ChatParticipant
    private let accounts: [ChatParticipant]
    private let syncContext: OpenApiMomentSyncContext?
    private var allPosts: [MomentPost]
    private var posts: [MomentPost]
    private let tableView = UITableView(frame: .zero, style: .plain)
    private var isLoadingRemoteMoments = false
    private var composeButton: UIBarButtonItem?
    private var refreshButton: UIBarButtonItem?
    private var syncButton: UIBarButtonItem?
    private static let initialVisiblePostCount = 10
    private static let paginationBatchSize = 10
    private static let paginationTriggerDistance = 5
    private static let cacheRefreshInterval: TimeInterval = 15 * 60

    init(owner: ChatParticipant, accounts: [ChatParticipant], syncContext: OpenApiMomentSyncContext?) {
        self.owner = owner
        self.accounts = accounts
        self.syncContext = syncContext
        let initialPosts: [MomentPost]
        if let cached = MomentsViewController.persistedPosts(owner: owner, syncContext: syncContext), !cached.isEmpty {
            initialPosts = cached
        } else if syncContext == nil {
            initialPosts = MomentsViewController.makeDemoPosts(owner: owner, accounts: accounts)
        } else {
            initialPosts = []
        }
        self.allPosts = initialPosts
        self.posts = Array(initialPosts.prefix(Self.initialVisiblePostCount))
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "朋友圈"
        view.backgroundColor = .systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        let composeButton = UIBarButtonItem(image: UIImage(systemName: "camera"), style: .plain, target: self, action: #selector(composeMoment))
        composeButton.accessibilityLabel = "发布朋友圈"
        let syncButton = UIBarButtonItem(title: "同步", style: .plain, target: self, action: #selector(syncMoments))
        syncButton.accessibilityLabel = "同步朋友圈"
        let refreshButton = UIBarButtonItem(image: UIImage(systemName: "arrow.clockwise"), style: .plain, target: self, action: #selector(refreshMoments))
        refreshButton.accessibilityLabel = "刷新朋友圈"
        self.composeButton = composeButton
        self.syncButton = syncButton
        self.refreshButton = refreshButton
        navigationItem.rightBarButtonItems = [
            composeButton,
            syncButton,
            refreshButton
        ]
        configureTable()
        updateEmptyState()
        prefetchMomentMedia(around: 0)
        if posts.isEmpty || isPersistedMomentCacheStale() {
            if posts.isEmpty {
                showCircularPageLoading(title: "加载朋友圈")
            }
            loadOpenApiMoments(triggerSync: false)
        }
    }

    private func configureTable() {
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.prefetchDataSource = self
        tableView.separatorStyle = .singleLine
        tableView.separatorInset = UIEdgeInsets(top: 0, left: 76, bottom: 0, right: 16)
        tableView.estimatedRowHeight = 260
        tableView.rowHeight = UITableView.automaticDimension
        tableView.register(MomentPostCell.self, forCellReuseIdentifier: MomentPostCell.reuseIdentifier)
        tableView.tableHeaderView = UIView(frame: CGRect(x: 0, y: 0, width: 1, height: 12))
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private static func makeDemoPosts(owner: ChatParticipant, accounts: [ChatParticipant]) -> [MomentPost] {
        let fallback = accounts.isEmpty ? [owner] : accounts
        let first = fallback[safe: 0] ?? owner
        let second = fallback[safe: 1] ?? owner
        let third = fallback[safe: 2] ?? owner
        let fourth = fallback[safe: 3] ?? owner
        return [
            MomentPost(
                id: UUID(),
                author: first,
                text: "图片、视频现在可直接查看；链接会判断能否直接访问，不能访问时走申请审核。",
                timeText: "刚刚",
                linkTitle: "权限说明 · cc2.cx",
                linkURL: "https://cc2.cx",
                imageSymbols: ["lock.shield", "photo", "doc.text", "video.fill"],
                imageURLs: [],
                videoURLs: [],
                locationText: "上海",
                visibilityText: BubbleAccessScope.public.rawValue,
                remindNames: [second.displayName],
                likes: [second.displayName, third.displayName],
                comments: [
                    MomentComment(author: second.displayName, replyTo: nil, text: "链接访问申请放到审核里处理更清晰。"),
                    MomentComment(author: third.displayName, replyTo: second.displayName, text: "后面直接接接口数据就行。")
                ]
            ),
            MomentPost(
                id: UUID(),
                author: second,
                text: "发一组今天的现场图，图片和视频都可以直接点开观看。",
                timeText: "12分钟前",
                linkTitle: nil,
                linkURL: nil,
                imageSymbols: [
                    "camera.fill", "video.fill", "sparkles",
                    "photo", "photo.fill.on.rectangle.fill", "play.rectangle.fill",
                    "person.crop.square", "map.fill", "doc.richtext"
                ],
                imageURLs: [],
                videoURLs: [],
                locationText: "活动现场",
                visibilityText: BubbleAccessScope.friends.rawValue,
                remindNames: [],
                likes: [owner.displayName],
                comments: [
                    MomentComment(author: owner.displayName, replyTo: nil, text: "预览页也保留识图、识物、查看高清入口。")
                ]
            ),
            MomentPost(
                id: UUID(),
                author: third,
                text: "文档预览现在能看到真实文件名，PDF、Word、Markdown 的链接点击时再判断访问权限。",
                timeText: "今天 09:42",
                linkTitle: "项目文档预览",
                linkURL: "https://cc2.cx/docs",
                imageSymbols: [
                    "doc.richtext", "doc.text.magnifyingglass", "doc.fill",
                    "doc.plaintext", "doc.zipper", "folder.fill"
                ],
                imageURLs: [],
                videoURLs: [],
                locationText: nil,
                visibilityText: BubbleAccessScope.recipients.rawValue,
                remindNames: [first.displayName, fourth.displayName],
                likes: [first.displayName, fourth.displayName],
                comments: []
            )
        ]
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        posts.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let post = posts[indexPath.row]
        let cell = tableView.dequeueReusableCell(withIdentifier: MomentPostCell.reuseIdentifier, for: indexPath) as? MomentPostCell
            ?? MomentPostCell(style: .default, reuseIdentifier: MomentPostCell.reuseIdentifier)
        cell.configure(
            post: post,
            ownerName: owner.displayName,
            onLike: { [weak self] in self?.toggleLike(postID: post.id) },
            onComment: { [weak self] in self?.presentCommentComposer(postID: post.id, replyTo: nil) },
            onMore: { [weak self] source in self?.presentPostActions(postID: post.id, sourceView: source) },
            onReply: { [weak self] commentIndex in
                guard let self,
                      let postIndex = self.posts.firstIndex(where: { $0.id == post.id }),
                      self.posts[postIndex].comments.indices.contains(commentIndex) else { return }
                let comment = self.posts[postIndex].comments[commentIndex]
                self.presentCommentActions(postID: post.id, commentIndex: commentIndex, comment: comment)
            },
            onOpenLink: { [weak self] link in self?.confirmOpenLink(link) },
            onPreviewImage: { [weak self] image, title in self?.presentMomentImagePreview(image: image, title: title) },
            onPreviewVideo: { [weak self] url in self?.presentMomentVideoPreview(url: url) }
        )
        return cell
    }

    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        revealMorePostsIfNeeded(visibleRow: indexPath.row)
    }

    private func resetPosts(_ newPosts: [MomentPost]) {
        allPosts = newPosts
        posts = Array(newPosts.prefix(Self.initialVisiblePostCount))
        persistMomentPosts()
        tableView.reloadData()
        updateEmptyState()
        prefetchMomentMedia(around: 0)
    }

    private static func momentCacheKey(owner: ChatParticipant, syncContext: OpenApiMomentSyncContext?) -> String {
        let scope = syncContext?.weChatId.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? owner.id.uuidString
        return "MomentsFeed.\(scope)"
    }

    private static func momentCacheUpdatedAtKey(owner: ChatParticipant, syncContext: OpenApiMomentSyncContext?) -> String {
        "\(momentCacheKey(owner: owner, syncContext: syncContext)).updatedAt"
    }

    private static func persistedPosts(owner: ChatParticipant, syncContext: OpenApiMomentSyncContext?) -> [MomentPost]? {
        ChatSQLiteStore.shared.codable([MomentPost].self, forKey: momentCacheKey(owner: owner, syncContext: syncContext))
    }

    private func persistMomentPosts() {
        ChatSQLiteStore.shared.setCodable(allPosts, forKey: Self.momentCacheKey(owner: owner, syncContext: syncContext))
        ChatSQLiteStore.shared.setString(
            String(Date().timeIntervalSince1970),
            forKey: Self.momentCacheUpdatedAtKey(owner: owner, syncContext: syncContext)
        )
    }

    private func isPersistedMomentCacheStale() -> Bool {
        guard let timestampText = ChatSQLiteStore.shared.string(forKey: Self.momentCacheUpdatedAtKey(owner: owner, syncContext: syncContext)),
              let timestamp = TimeInterval(timestampText)
        else { return true }
        return Date().timeIntervalSince(Date(timeIntervalSince1970: timestamp)) > Self.cacheRefreshInterval
    }

    private func revealMorePostsIfNeeded(visibleRow: Int) {
        guard allPosts.count > posts.count else { return }
        let triggerRow = max(0, posts.count - Self.paginationTriggerDistance)
        guard visibleRow >= triggerRow else { return }
        let oldCount = posts.count
        let newCount = min(allPosts.count, oldCount + Self.paginationBatchSize)
        guard newCount > oldCount else { return }
        posts.append(contentsOf: allPosts[oldCount..<newCount])
        let indexPaths = (oldCount..<newCount).map { IndexPath(row: $0, section: 0) }
        tableView.performBatchUpdates {
            tableView.insertRows(at: indexPaths, with: .none)
        }
        prefetchMomentMedia(around: visibleRow)
    }

    private func setMomentLoading(_ isLoading: Bool) {
        isLoadingRemoteMoments = isLoading
        refreshButton?.isEnabled = !isLoading
        syncButton?.isEnabled = !isLoading
    }

    private func loadOpenApiMoments(triggerSync: Bool) {
        guard let syncContext, !isLoadingRemoteMoments else { return }
        setMomentLoading(true)
        Task { [weak self] in
            guard let self else { return }
            let cachedPosts = await OpenApiMomentFeedLoader.loadCachedPosts(owner: self.owner, accounts: self.accounts, context: syncContext)
            await MainActor.run {
                if !cachedPosts.isEmpty {
                    self.resetPosts(cachedPosts)
                } else {
                    self.updateEmptyState(message: triggerSync ? "正在同步朋友圈" : "正在加载朋友圈")
                }
            }
            let result = await OpenApiMomentFeedLoader.loadPosts(
                owner: self.owner,
                accounts: self.accounts,
                context: syncContext,
                triggerSync: triggerSync
            )
            await MainActor.run {
                self.setMomentLoading(false)
                if !result.posts.isEmpty {
                    self.resetPosts(result.posts)
                } else {
                    self.updateEmptyState(message: triggerSync ? "暂无同步到朋友圈动态" : "暂无朋友圈动态，点击右上角“同步”拉取最新内容")
                }
                if let notice = result.notice {
                    self.onNotice?(notice)
                }
            }
        }
    }

    private func toggleLike(postID: UUID) {
        guard let index = posts.firstIndex(where: { $0.id == postID }) else { return }
        let isCancel: Bool
        if let existing = posts[index].likes.firstIndex(of: owner.displayName) {
            posts[index].likes.remove(at: existing)
            isCancel = true
            onNotice?("已取消点赞")
        } else {
            posts[index].likes.append(owner.displayName)
            isCancel = false
            onNotice?("已点赞")
        }
        let circleId = posts[index].circleId
        syncVisiblePostBackToAll(post: posts[index])
        persistMomentPosts()
        tableView.reloadRows(at: [IndexPath(row: index, section: 0)], with: .automatic)
        syncMomentLikeIfPossible(circleId: circleId, isCancel: isCancel)
    }

    private func syncVisiblePostBackToAll(post: MomentPost) {
        guard let allIndex = allPosts.firstIndex(where: { $0.id == post.id }) else { return }
        allPosts[allIndex] = post
    }

    private func presentPostActions(postID: UUID, sourceView: UIView) {
        guard let index = posts.firstIndex(where: { $0.id == postID }) else { return }
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        let liked = posts[index].likes.contains(owner.displayName)
        alert.addAction(UIAlertAction(title: liked ? "取消赞" : "赞", style: .default) { [weak self] _ in
            self?.toggleLike(postID: postID)
        })
        alert.addAction(UIAlertAction(title: "评论", style: .default) { [weak self] _ in
            self?.presentCommentComposer(postID: postID, replyTo: nil)
        })
        if let link = posts[index].linkURL {
            alert.addAction(UIAlertAction(title: "打开链接", style: .default) { [weak self] _ in
                self?.confirmOpenLink(link)
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds
        }
        present(alert, animated: true)
    }

    private func presentCommentComposer(postID: UUID, replyTo: String?, replyToWxid: String? = nil, replyCommentId: Int64? = nil) {
        guard let postIndex = posts.firstIndex(where: { $0.id == postID }) else { return }
        if cannotComment(post: posts[postIndex], replyTo: replyTo) {
            onNotice?("不能对自己评论")
            return
        }
        let title = replyTo.map { "回复 \($0)" } ?? "评论"
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .alert)
        alert.addTextField { textField in
            textField.placeholder = "输入评论内容"
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "发送", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let text = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !text.isEmpty else { return }
            let circleId = self.posts[postIndex].circleId
            let targetWxid = replyToWxid ?? self.posts[postIndex].authorWxid
            self.posts[postIndex].comments.append(MomentComment(author: self.owner.displayName, authorWxid: self.syncContext?.weChatId, replyTo: replyTo, text: text))
            self.syncVisiblePostBackToAll(post: self.posts[postIndex])
            self.persistMomentPosts()
            self.tableView.reloadRows(at: [IndexPath(row: postIndex, section: 0)], with: .automatic)
            self.onNotice?("评论已发送")
            self.syncMomentCommentIfPossible(circleId: circleId, toWeChatId: targetWxid, content: text, replyCommentId: replyCommentId)
        })
        present(alert, animated: true)
    }

    private func presentCommentActions(postID: UUID, commentIndex: Int, comment: MomentComment) {
        guard let postIndex = posts.firstIndex(where: { $0.id == postID }) else { return }
        let alert = UIAlertController(title: comment.author, message: comment.text, preferredStyle: .actionSheet)
        if comment.author != owner.displayName {
            alert.addAction(UIAlertAction(title: "回复", style: .default) { [weak self] _ in
                self?.presentCommentComposer(
                    postID: postID,
                    replyTo: comment.author,
                    replyToWxid: comment.authorWxid,
                    replyCommentId: comment.id
                )
            })
        }
        if comment.author == owner.displayName || comment.authorWxid == syncContext?.weChatId {
            alert.addAction(UIAlertAction(title: "删除评论", style: .destructive) { [weak self] _ in
                self?.deleteComment(postIndex: postIndex, commentIndex: commentIndex)
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        present(alert, animated: true)
    }

    private func deleteComment(postIndex: Int, commentIndex: Int) {
        guard posts.indices.contains(postIndex),
              posts[postIndex].comments.indices.contains(commentIndex)
        else { return }
        let removed = posts[postIndex].comments.remove(at: commentIndex)
        let circleId = posts[postIndex].circleId
        let publishTime = posts[postIndex].publishTime
        syncVisiblePostBackToAll(post: posts[postIndex])
        persistMomentPosts()
        tableView.reloadRows(at: [IndexPath(row: postIndex, section: 0)], with: .automatic)
        guard let syncContext,
              let circleId,
              let commentId = removed.id,
              circleId > 0,
              commentId > 0
        else {
            onNotice?("评论已删除")
            return
        }
        Task { [weak self] in
            do {
                let message = try await OpenApiMomentAPI.deleteComment(
                    context: syncContext,
                    circleId: circleId,
                    commentId: commentId,
                    publishTime: publishTime
                )
                await MainActor.run {
                    self?.onNotice?(message.isEmpty ? "评论删除已同步" : message)
                }
            } catch {
                await MainActor.run {
                    self?.onNotice?("本地已删除，接口删除失败：\(error.localizedDescription)")
                }
            }
        }
    }

    private func syncMomentLikeIfPossible(circleId: Int64?, isCancel: Bool) {
        guard let syncContext, let circleId, circleId > 0 else { return }
        Task { [weak self] in
            do {
                let message = try await OpenApiMomentFeedLoader.submitLike(context: syncContext, circleId: circleId, isCancel: isCancel)
                await MainActor.run {
                    self?.onNotice?(message.isEmpty ? (isCancel ? "取消赞已同步" : "点赞已同步") : message)
                }
            } catch {
                await MainActor.run {
                    self?.onNotice?("本地已更新，点赞同步失败：\(error.localizedDescription)")
                }
            }
        }
    }

    private func syncMomentCommentIfPossible(circleId: Int64?, toWeChatId: String?, content: String, replyCommentId: Int64?) {
        guard let syncContext, let circleId, circleId > 0 else { return }
        Task { [weak self] in
            do {
                let message = try await OpenApiMomentFeedLoader.submitComment(
                    context: syncContext,
                    circleId: circleId,
                    toWeChatId: toWeChatId,
                    content: content,
                    replyCommentId: replyCommentId
                )
                await MainActor.run {
                    self?.onNotice?(message.isEmpty ? "评论已同步" : message)
                }
            } catch {
                await MainActor.run {
                    self?.onNotice?("本地评论已保留，接口同步失败：\(error.localizedDescription)")
                }
            }
        }
    }

    private func cannotComment(post: MomentPost, replyTo: String?) -> Bool {
        if let replyTo {
            return replyTo == owner.displayName
        }
        return post.author.id == owner.id
    }

    private func confirmOpenLink(_ link: String) {
        let alert = UIAlertController(title: "跳转链接", message: "将跳转到：\n\(link)", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "继续", style: .default) { [weak self] _ in
            self?.onOpenLink?(link)
        })
        present(alert, animated: true)
    }

    private func presentMomentImagePreview(image: UIImage, title: String) {
        let previewController = FullscreenMediaPreviewController.image(image, title: title)
        present(previewController, animated: true)
    }

    private func presentMomentVideoPreview(url: URL) {
        let previewController = FullscreenMediaPreviewController.video(url)
        present(previewController, animated: true)
    }

    @objc private func composeMoment() {
        let controller = MomentComposerViewController(owner: owner, accounts: accounts, syncContext: syncContext)
        controller.onPublish = { [weak self] post in
            guard let self else { return }
            self.allPosts.insert(post, at: 0)
            self.posts.insert(post, at: 0)
            self.persistMomentPosts()
            self.tableView.insertRows(at: [IndexPath(row: 0, section: 0)], with: .automatic)
            self.tableView.scrollToRow(at: IndexPath(row: 0, section: 0), at: .top, animated: true)
            self.onNotice?("朋友圈已发表")
            if self.syncContext != nil {
                self.loadOpenApiMoments(triggerSync: true)
            }
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    @objc private func refreshMoments() {
        showCircularPageLoading(title: "刷新朋友圈")
        loadOpenApiMoments(triggerSync: false)
    }

    @objc private func syncMoments() {
        showCircularPageLoading(title: "同步朋友圈")
        loadOpenApiMoments(triggerSync: true)
    }

    func tableView(_ tableView: UITableView, prefetchRowsAt indexPaths: [IndexPath]) {
        guard tableView === self.tableView else { return }
        for indexPath in indexPaths {
            guard let post = posts[safe: indexPath.row] else { continue }
            prefetchMomentMedia(for: post)
        }
    }

    private func prefetchMomentMedia(around row: Int) {
        guard !posts.isEmpty else { return }
        let lower = max(0, row - 2)
        let upper = min(posts.count - 1, row + 8)
        for index in lower...upper {
            prefetchMomentMedia(for: posts[index])
        }
    }

    private func prefetchMomentMedia(for post: MomentPost) {
        for url in post.imageURLs.prefix(9) {
            MomentRemoteImageLoader.shared.prefetch(url)
        }
        for url in post.videoURLs.prefix(4) {
            MomentVideoThumbnailLoader.shared.prefetch(url)
        }
        if let avatarURL = post.author.avatarURL {
            MomentRemoteImageLoader.shared.prefetch(avatarURL)
        }
    }

    private func updateEmptyState(message: String = "暂无朋友圈动态") {
        guard posts.isEmpty else {
            tableView.backgroundView = nil
            return
        }
        let container = UIView(frame: tableView.bounds)
        let label = UILabel()
        label.text = message
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: container.centerYAnchor, constant: -28),
            label.leadingAnchor.constraint(greaterThanOrEqualTo: container.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(lessThanOrEqualTo: container.trailingAnchor, constant: -24)
        ])
        tableView.backgroundView = container
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class ServerMomentsMirrorViewController: UIViewController, WKNavigationDelegate {
    private let url: URL
    private let webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())

    init(url: URL, titleText: String) {
        self.url = url
        super.init(nibName: nil, bundle: nil)
        title = titleText
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "arrow.clockwise"),
            style: .plain,
            target: self,
            action: #selector(reloadPage)
        )
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.keyboardDismissMode = .interactive
        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        showCircularPageLoading(title: "加载朋友圈")
        webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30))
    }

    @objc private func reloadPage() {
        showCircularPageLoading(title: "刷新朋友圈")
        webView.reload()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        showLoadFailure(error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        showLoadFailure(error)
    }

    private func showLoadFailure(_ error: Error) {
        let alert = UIAlertController(title: "朋友圈加载失败", message: error.localizedDescription, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "重试", style: .default) { [weak self] _ in
            self?.reloadPage()
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        present(alert, animated: true)
    }
}

private struct OpenApiMomentFeedResult {
    let posts: [MomentPost]
    let notice: String?
}

private enum OpenApiMomentFeedLoader {
    static func loadCachedPosts(
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext
    ) async -> [MomentPost] {
        let recentTaskItems = await loadMomentTaskItems(context: context)
        let recentTaskPosts = momentPosts(fromTaskItems: recentTaskItems, owner: owner, accounts: accounts, context: context)
        let detailPosts = await loadMomentDetailPosts(from: recentTaskItems, owner: owner, accounts: accounts, context: context)
        return uniquePosts(detailPosts + recentTaskPosts)
    }

    static func submitLike(context: OpenApiMomentSyncContext, circleId: Int64, isCancel: Bool) async throws -> String {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/like",
            body: [
                "deviceUuid": context.deviceUuid,
                "weChatId": context.weChatId,
                "circleId": circleId,
                "isCancel": isCancel
            ]
        )
        return cleaned(string((result.jsonObject as? [String: Any])?["message"]))
    }

    static func submitComment(
        context: OpenApiMomentSyncContext,
        circleId: Int64,
        toWeChatId: String?,
        content: String,
        replyCommentId: Int64?
    ) async throws -> String {
        var body: [String: Any] = [
            "deviceUuid": context.deviceUuid,
            "weChatId": context.weChatId,
            "circleId": circleId,
            "content": content,
            "isResend": false
        ]
        let cleanedToWeChatId = cleaned(toWeChatId)
        if !cleanedToWeChatId.isEmpty {
            body["toWeChatId"] = cleanedToWeChatId
        }
        if let replyCommentId, replyCommentId > 0 {
            body["replyCommentId"] = replyCommentId
        }
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/comments",
            body: body
        )
        return cleaned(string((result.jsonObject as? [String: Any])?["message"]))
    }

    static func loadPosts(
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext,
        triggerSync: Bool
    ) async -> OpenApiMomentFeedResult {
        var notices: [String] = []
        var requestedTaskIDs: [String] = []
        var directTaskItems: [[String: Any]] = []

        if triggerSync {
            do {
                let syncResult = try await OpenApiHTTPClient.request(
                    "POST",
                    path: "/openapi/v1/moments/sync",
                    body: [
                        "deviceUuid": context.deviceUuid,
                        "weChatId": context.weChatId,
                        "startTime": 0,
                        "circleIds": []
                    ]
                )
                if let raw = syncResult.jsonObject as? [String: Any] {
                    directTaskItems.append(raw)
                    notices.append(cleaned(string(raw["message"])))
                    requestedTaskIDs.append(contentsOf: taskIDStrings(fromTaskResult: raw))
                }
            } catch {
                notices.append("朋友圈同步下发失败：\(error.localizedDescription)")
            }

            do {
                let messageSyncResult = try await OpenApiHTTPClient.request(
                    "POST",
                    path: "/openapi/v1/moments/messages/sync",
                    body: [
                        "deviceUuid": context.deviceUuid,
                        "weChatId": context.weChatId,
                        "onlyComment": false,
                        "getAll": true
                    ]
                )
                if let raw = messageSyncResult.jsonObject as? [String: Any] {
                    directTaskItems.append(raw)
                    notices.append(cleaned(string(raw["message"])))
                    requestedTaskIDs.append(contentsOf: taskIDStrings(fromTaskResult: raw))
                }
            } catch {
                notices.append("朋友圈互动同步下发失败：\(error.localizedDescription)")
            }
        }

        let directPosts = momentPosts(fromTaskItems: directTaskItems, owner: owner, accounts: accounts, context: context, trustedTaskResults: true)
        let freshTaskItems = await loadMomentTaskItems(taskIDs: requestedTaskIDs)
        let freshTaskPosts = momentPosts(fromTaskItems: freshTaskItems, owner: owner, accounts: accounts, context: context, trustedTaskResults: true)
        let recentTaskItems = await loadMomentTaskItems(context: context)
        let recentTaskPosts = momentPosts(fromTaskItems: recentTaskItems, owner: owner, accounts: accounts, context: context)
        let detailPosts = await loadMomentDetailPosts(
            from: directTaskItems + freshTaskItems + recentTaskItems,
            owner: owner,
            accounts: accounts,
            context: context
        )
        let realPosts = uniquePosts(detailPosts + directPosts + freshTaskPosts + recentTaskPosts)
        if realPosts.isEmpty, let summaryNotice = momentSyncSummaryNotice(from: directTaskItems + recentTaskItems) {
            notices.append(summaryNotice)
        }
        let notice = notices
            .map(cleaned)
            .filter { !$0.isEmpty }
            .prefix(2)
            .joined(separator: "；")
        return OpenApiMomentFeedResult(posts: realPosts, notice: notice.isEmpty ? nil : notice)
    }

    private static func momentTaskShell(_ post: MomentPost) -> [String: Any] {
        guard let circleId = post.circleId else { return [:] }
        return [
            "circleId": circleId,
            "data": [
                "circleId": circleId,
                "content": post.text,
                "authorWxid": post.authorWxid ?? ""
            ]
        ]
    }

    private static func loadMomentDetailPosts(
        from taskItems: [[String: Any]],
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext
    ) async -> [MomentPost] {
        let circleIDs = extractCircleIDs(from: taskItems)
        guard !circleIDs.isEmpty else { return [] }

        var detailTaskItems: [[String: Any]] = []
        for circleID in circleIDs.prefix(40) {
            do {
                let result = try await OpenApiHTTPClient.request(
                    "POST",
                    path: "/openapi/v1/moments/detail",
                    body: [
                        "deviceUuid": context.deviceUuid,
                        "weChatId": context.weChatId,
                        "circleId": circleID,
                        "getBigMap": true
                    ]
                )
                if let raw = result.jsonObject as? [String: Any] {
                    detailTaskItems.append(raw)
                    let taskIDs = taskIDStrings(fromTaskResult: raw)
                    detailTaskItems.append(contentsOf: await loadMomentTaskItems(taskIDs: taskIDs))
                }
            } catch {
                continue
            }
        }
        return momentPosts(fromTaskItems: detailTaskItems, owner: owner, accounts: accounts, context: context, trustedTaskResults: true)
    }

    private static func loadRequestedMomentTaskPosts(
        taskIDs: [String],
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext
    ) async -> [MomentPost] {
        let uniqueTaskIDs = Array(Set(taskIDs)).filter { !$0.isEmpty }
        guard !uniqueTaskIDs.isEmpty else { return [] }
        var latestTasks: [[String: Any]] = []
        for attempt in 0..<6 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: UInt64(700_000_000 + attempt * 250_000_000))
            }
            var tasks: [[String: Any]] = []
            for taskID in uniqueTaskIDs {
                do {
                    let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/tasks/\(taskID)")
                    if let raw = result.jsonObject as? [String: Any] {
                        tasks.append(raw)
                    }
                } catch {
                    continue
                }
            }
            if !tasks.isEmpty {
                latestTasks = tasks
                let posts = momentPosts(fromTaskItems: tasks, owner: owner, accounts: accounts, context: context)
                if !posts.isEmpty { return posts }
            }
        }
        return momentPosts(fromTaskItems: latestTasks, owner: owner, accounts: accounts, context: context)
    }

    private static func taskIDStrings(fromTaskResult raw: [String: Any]) -> [String] {
        taskIDStrings(from: raw)
    }

    private static func taskIDStrings(from object: Any?, depth: Int = 0) -> [String] {
        guard depth < 5 else { return [] }
        if let string = object as? String {
            let value = cleaned(string)
            if let parsed = jsonObject(from: value) {
                return taskIDStrings(from: parsed, depth: depth + 1)
            }
            return taskIDs(fromText: value)
        }
        if let number = object as? NSNumber {
            let value = number.stringValue
            return isValidTaskID(value) ? [value] : []
        }
        if let array = object as? [Any] {
            return uniqueStrings(array.flatMap { taskIDStrings(from: $0, depth: depth + 1) })
        }
        guard let raw = object as? [String: Any] else { return [] }
        var values = [
            string(value(forAnyKey: "taskId", in: raw)),
            string(value(forAnyKey: "taskID", in: raw)),
            string(value(forAnyKey: "task_id", in: raw))
        ]
        for key in ["taskResultUrl", "taskResultURL", "taskUrl", "resultUrl"] {
            let urlText = cleaned(string(value(forAnyKey: key, in: raw)))
            guard !urlText.isEmpty, let url = OpenApiDisplay.url(from: urlText) else { continue }
            let lastComponent = cleaned(url.pathComponents.last)
            if isValidTaskID(lastComponent), lastComponent != "tasks" {
                values.append(lastComponent)
            }
        }
        let nestedKeys = [
            "data", "result", "results", "payload", "task", "taskResult", "taskData",
            "resultData", "output", "response", "value", "items", "records", "list"
        ]
        for key in nestedKeys {
            values.append(contentsOf: taskIDStrings(from: value(forAnyKey: key, in: raw), depth: depth + 1))
        }
        return uniqueStrings(values.map(cleaned).filter(isValidTaskID))
    }

    private static func taskIDs(fromText text: String) -> [String] {
        let value = cleaned(text)
        guard !value.isEmpty else { return [] }
        let patterns = [
            #"(?i)(?:taskId|taskID|task_id)\s*["']?\s*[:=：]\s*["']?([A-Za-z0-9_-]+)"#,
            #"/tasks/([A-Za-z0-9_-]+)"#
        ]
        var ids: [String] = []
        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern) else { continue }
            let range = NSRange(value.startIndex..<value.endIndex, in: value)
            for match in regex.matches(in: value, range: range) where match.numberOfRanges > 1 {
                guard let matchRange = Range(match.range(at: 1), in: value) else { continue }
                let id = cleaned(String(value[matchRange]))
                if isValidTaskID(id) {
                    ids.append(id)
                }
            }
        }
        return uniqueStrings(ids)
    }

    private static func isValidTaskID(_ value: String) -> Bool {
        let trimmed = cleaned(value)
        guard !trimmed.isEmpty else { return false }
        if let number = Int64(trimmed) {
            return number > 0
        }
        return trimmed.range(of: #"^[A-Za-z0-9][A-Za-z0-9_-]*$"#, options: .regularExpression) != nil
    }

    private static func loadMomentTaskPosts(owner: ChatParticipant, accounts: [ChatParticipant], context: OpenApiMomentSyncContext) async -> [MomentPost] {
        let items = await loadMomentTaskItems(context: context)
        return momentPosts(fromTaskItems: items, owner: owner, accounts: accounts, context: context)
    }

    private static func loadMomentTaskItems(context: OpenApiMomentSyncContext) async -> [[String: Any]] {
        var items: [[String: Any]] = []
        do {
            let result = try await OpenApiHTTPClient.request(
                "GET",
                path: "/openapi/v1/tasks/recent",
                query: [
                    URLQueryItem(name: "deviceUuid", value: context.deviceUuid),
                    URLQueryItem(name: "count", value: "100")
                ]
            )
            items.append(contentsOf: arrayPayload(from: result.jsonObject))
        } catch {
            items = []
        }
        do {
            let result = try await OpenApiHTTPClient.request(
                "GET",
                path: "/openapi/v1/tasks/recent",
                query: [
                    URLQueryItem(name: "count", value: "100")
                ]
            )
            items.append(contentsOf: arrayPayload(from: result.jsonObject))
        } catch {
            return items
        }
        var seen = Set<String>()
        return items.filter { raw in
            let key = firstCleaned([
                string(raw["taskId"]),
                string(raw["id"]),
                string(raw["receivedAt"]),
                string(raw["message"])
            ])
            return key.isEmpty || seen.insert(key).inserted
        }
    }

    private static func loadMomentTaskItems(taskIDs: [String]) async -> [[String: Any]] {
        let uniqueTaskIDs = Array(Set(taskIDs.map(cleaned).filter { !$0.isEmpty }))
        guard !uniqueTaskIDs.isEmpty else { return [] }
        var latestTasks: [[String: Any]] = []
        for attempt in 0..<12 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: UInt64(650_000_000 + attempt * 250_000_000))
            }
            var tasks: [[String: Any]] = []
            for taskID in uniqueTaskIDs {
                do {
                    let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/tasks/\(taskID)")
                    if let raw = result.jsonObject as? [String: Any] {
                        tasks.append(raw)
                    }
                } catch {
                    continue
                }
            }
            if !tasks.isEmpty {
                latestTasks = tasks
                if tasks.contains(where: { !momentCandidateDictionaries(from: $0).isEmpty }) {
                    return tasks
                }
            }
        }
        return latestTasks
    }

    private static func extractCircleIDs(from items: [[String: Any]]) -> [Int64] {
        var ids: [Int64] = []
        for item in items {
            ids.append(contentsOf: extractCircleIDs(from: item))
            if let data = taskData(from: item) {
                ids.append(contentsOf: extractCircleIDs(from: data))
            }
        }
        var seen = Set<Int64>()
        return ids.filter { $0 > 0 && seen.insert($0).inserted }
    }

    private static func extractCircleIDs(from object: Any?, depth: Int = 0) -> [Int64] {
        guard depth < 8 else { return [] }
        if let number = object as? NSNumber {
            let value = number.int64Value
            return value > 0 ? [value] : []
        }
        if let string = object as? String {
            let cleanedString = cleaned(string)
            if let parsed = jsonObject(from: cleanedString) {
                return extractCircleIDs(from: parsed, depth: depth + 1)
            }
            let jsonIDs = jsonObjects(inText: cleanedString).flatMap { extractCircleIDs(from: $0, depth: depth + 1) }
            return jsonIDs + circleIDs(fromText: cleanedString)
        }
        if let array = object as? [Any] {
            return array.flatMap { extractCircleIDs(from: $0, depth: depth + 1) }
        }
        guard let dictionary = object as? [String: Any] else { return [] }
        var ids: [Int64] = []
        let directKeys = [
            "circleId", "CircleId", "circleID", "CircleID",
            "circleIds", "CircleIds", "circleIDs", "CircleIDs",
            "snsId", "SnsId", "snsID", "SnsID",
            "snsIds", "SnsIds", "momentId", "MomentId", "momentIds", "MomentIds"
        ]
        for key in directKeys {
            ids.append(contentsOf: extractCircleIDs(from: value(forAnyKey: key, in: dictionary), depth: depth + 1))
        }
        for key in ["message", "statusText", "summary", "raw", "rawData", "dataText", "resultText"] {
            ids.append(contentsOf: circleIDs(fromText: string(value(forAnyKey: key, in: dictionary))))
        }
        let nestedKeys = [
            "data", "result", "results", "payload", "items", "records", "list",
            "moments", "momentList", "circleList", "circles", "snsList",
            "feed", "feeds", "timeline", "taskResult", "taskData", "resultData", "output", "response", "value",
            "detail", "moment", "circle", "sns", "post", "item", "rawData",
            "rawResult", "rawResponse", "resultJson", "json"
        ]
        for key in nestedKeys {
            ids.append(contentsOf: extractCircleIDs(from: value(forAnyKey: key, in: dictionary), depth: depth + 1))
        }
        return ids
    }

    private static func circleIDs(fromText text: String) -> [Int64] {
        let value = cleaned(text)
        guard !value.isEmpty else { return [] }
        let patterns = [
            #"(?i)(?:circleIds?|snsIds?|momentIds?)["']?\s*[:=：]\s*\[?([0-9,\s"'，]+)\]?"#,
            #"(?:朋友圈动态ID|朋友圈动态 Id|动态ID|动态 Id|circleId)["']?\s*[:=：]?\s*["']?([0-9,\s"'，]+)"#,
            #"<(?:circleId|snsId|momentId)>([0-9]+)</(?:circleId|snsId|momentId)>"#
        ]
        var ids: [Int64] = []
        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern) else { continue }
            let range = NSRange(value.startIndex..<value.endIndex, in: value)
            for match in regex.matches(in: value, range: range) where match.numberOfRanges > 1 {
                guard let matchRange = Range(match.range(at: 1), in: value) else { continue }
                let rawIDs = String(value[matchRange])
                ids.append(contentsOf: rawIDs
                    .components(separatedBy: CharacterSet(charactersIn: ",， \"'\n\t"))
                    .compactMap { Int64(cleaned($0)) }
                    .filter { $0 > 0 })
            }
        }
        return ids
    }

    private static func momentSyncSummaryNotice(from items: [[String: Any]]) -> String? {
        for raw in items {
            let message = cleaned(string(raw["message"]))
            guard message.contains("朋友圈同步完成") else { continue }
            let countText = textBetween(message, prefix: "本次返回 ", suffix: " 条")
            if !countText.isEmpty {
                return "朋友圈已同步 \(countText) 条，但接口暂未返回可展示的动态明细；请稍后点刷新继续读取详情"
            }
            return "朋友圈已同步，但接口暂未返回可展示的动态明细；请稍后点刷新继续读取详情"
        }
        return nil
    }

    private static func textBetween(_ text: String, prefix: String, suffix: String) -> String {
        guard let startRange = text.range(of: prefix) else { return "" }
        let remaining = text[startRange.upperBound...]
        guard let endRange = remaining.range(of: suffix) else { return "" }
        return cleaned(String(remaining[..<endRange.lowerBound]))
    }

    private static func momentPosts(
        fromTaskItems items: [[String: Any]],
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext,
        trustedTaskResults: Bool = false
    ) -> [MomentPost] {
        var posts: [MomentPost] = []
        for raw in items {
            guard trustedTaskResults || (isMomentRelatedTask(raw) && taskMatches(raw, context: context)) else { continue }
            for payloadObject in taskPayloadObjects(from: raw) {
                let candidates = momentCandidateDictionaries(from: payloadObject)
                for candidate in candidates {
                    guard let post = momentPost(fromMomentPayload: candidate, rawTask: raw, owner: owner, accounts: accounts, context: context) else {
                        continue
                    }
                    posts.append(post)
                }
            }
        }
        return uniquePosts(posts)
    }

    private static func momentCandidateDictionaries(from object: Any?, depth: Int = 0) -> [[String: Any]] {
        guard depth < 10 else { return [] }
        if let string = object as? String {
            return jsonObjects(inText: decodedJSONStringIfNeeded(string)).flatMap { momentCandidateDictionaries(from: $0, depth: depth + 1) }
        }
        if let array = object as? [Any] {
            return array.flatMap { momentCandidateDictionaries(from: $0, depth: depth + 1) }
        }
        guard let dictionary = object as? [String: Any] else { return [] }
        var results: [[String: Any]] = []
        if looksLikeMomentPayload(dictionary) {
            results.append(dictionary)
        }
        let likelyContainerKeys = [
            "data", "payload", "items", "list", "records", "results",
            "moments", "momentList", "circleList", "circles", "snsList",
            "feed", "feeds", "timeline", "objectList", "result", "taskResult",
            "taskData", "resultData", "output", "response", "value",
            "detail", "moment", "circle", "sns", "post", "item", "rawData",
            "rawResult", "rawResponse", "resultJson", "json",
            "rows", "dataList", "pageData", "payloadList", "momentData",
            "朋友圈", "朋友圈列表", "动态列表", "message", "summary",
            "snsObject", "SnsObject", "snsObjects", "SnsObjects", "objectDesc", "ObjectDesc"
        ]
        for key in likelyContainerKeys {
            if let value = value(forAnyKey: key, in: dictionary) {
                results.append(contentsOf: momentCandidateDictionaries(from: value, depth: depth + 1))
            }
        }
        let knownKeys = Set(likelyContainerKeys.map { $0.lowercased() })
        for (key, value) in dictionary where !knownKeys.contains(key.lowercased()) {
            guard value is [String: Any] || value is [Any] || value is String else { continue }
            results.append(contentsOf: momentCandidateDictionaries(from: value, depth: depth + 1))
        }
        return results
    }

    private static func looksLikeMomentPayload(_ raw: [String: Any]) -> Bool {
        let circleId = firstInt64([
            raw["circleId"], raw["CircleId"], raw["circleID"], raw["CircleID"],
            raw["snsId"], raw["SnsId"], raw["snsID"], raw["SnsID"],
            raw["momentId"], raw["MomentId"], raw["momentID"], raw["MomentID"],
            raw["id"], raw["Id"], raw["ID"]
        ])
        let authorWxid = firstCleaned(authorWxidCandidates(from: raw, rawTask: nil))
        let content = momentContentText(from: raw)
        let hasMedia = !imageURLs(fromMomentPayload: raw).isEmpty || !videoURLs(fromMomentPayload: raw).isEmpty
        let hasLink = !firstCleaned([
            string(raw["linkUrl"]),
            string(raw["linkURL"]),
            string(raw["url"]),
            string(raw["weAppUrl"]),
            string(raw["linkTitle"]),
            string(raw["weAppTitle"])
        ]).isEmpty
        let hasSocial = !(arrayPayload(from: raw["comments"]).isEmpty && arrayPayload(from: raw["commentList"]).isEmpty)
            || !(arrayPayload(from: raw["likes"]).isEmpty && arrayPayload(from: raw["likeList"]).isEmpty)
        guard circleId != nil || !authorWxid.isEmpty else { return false }
        return !content.isEmpty || hasMedia || hasSocial || hasLink
    }

    private static func momentPost(
        fromMomentPayload raw: [String: Any],
        rawTask: [String: Any],
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext
    ) -> MomentPost? {
        let authorWxid = resolvedAuthorWxid(from: raw, rawTask: rawTask, context: context)
        if !context.friendWxids.isEmpty,
           !authorWxid.isEmpty,
           authorWxid != context.weChatId,
           !context.friendWxids.contains(authorWxid) {
            return nil
        }
        let fallbackAuthor = accounts.first { $0.id != owner.id } ?? owner
        let author = participant(
            matching: authorWxid,
            fallbackName: firstCleaned([
                string(raw["authorName"]),
                string(raw["AuthorName"]),
                string(raw["friendName"]),
                string(raw["FriendName"]),
                string(raw["nickname"]),
                string(raw["nick"]),
                string(raw["nickName"]),
                string(raw["Nickname"]),
                string(raw["NickName"]),
                string(raw["displayName"]),
                string(raw["DisplayName"]),
                string((raw["author"] as? [String: Any])?["nickname"]),
                string((raw["author"] as? [String: Any])?["displayName"]),
                string((raw["user"] as? [String: Any])?["nickname"]),
                context.friendDisplayNames[authorWxid] ?? ""
            ]),
            fallback: fallbackAuthor,
            accounts: accounts,
            context: context
        )
        let content = momentContentText(from: raw)
        let imageURLs = imageURLs(fromMomentPayload: raw)
        let videoURLs = videoURLs(fromMomentPayload: raw)
        guard !content.isEmpty || !imageURLs.isEmpty || !videoURLs.isEmpty else { return nil }
        let circleId = firstInt64([
            raw["circleId"], raw["CircleId"], raw["circleID"], raw["CircleID"],
            raw["snsId"], raw["SnsId"], raw["snsID"], raw["SnsID"],
            raw["momentId"], raw["MomentId"], raw["momentID"], raw["MomentID"],
            raw["id"], raw["Id"], raw["ID"]
        ])
        let publishDate = firstDate([
            raw["publishTime"],
            raw["PublishTime"],
            raw["createTime"],
            raw["CreateTime"],
            raw["createdAt"],
            raw["CreatedAt"],
            raw["time"],
            raw["Time"],
            raw["timestamp"],
            rawTask["receivedAt"]
        ])
        let linkURL = firstCleaned([
            string(raw["linkUrl"]),
            string(raw["linkURL"]),
            string(raw["LinkUrl"]),
            string(raw["LinkURL"]),
            string(raw["link"]),
            string(raw["href"]),
            string(raw["url"]),
            string(raw["Url"]),
            string(raw["sourceUrl"]),
            string(raw["pageUrl"]),
            string((raw["linkInfo"] as? [String: Any])?["url"]),
            string((raw["appMsg"] as? [String: Any])?["url"])
        ])
        let linkTitle = firstCleaned([
            string(raw["linkTitle"]),
            string(raw["LinkTitle"]),
            string(raw["title"]),
            string(raw["Title"]),
            string(raw["appName"]),
            string(raw["sourceName"]),
            string((raw["linkInfo"] as? [String: Any])?["title"]),
            string((raw["appMsg"] as? [String: Any])?["title"])
        ])
        let location = firstCleaned([
            string(raw["location"]),
            string(raw["Location"]),
            string(raw["locationText"]),
            string(raw["LocationText"]),
            string(raw["poiName"]),
            string(raw["PoiName"]),
            string((raw["poi"] as? [String: Any])?["name"])
        ])
        let comments = momentComments(from: raw, context: context, accounts: accounts)
        let likes = momentLikes(from: raw, context: context)
        let key = firstCleaned([
            string(circleId.map(NSNumber.init(value:))),
            string(raw["id"]),
            string(rawTask["taskId"]),
            "\(authorWxid)-\(content)-\(imageURLs.map(\.absoluteString).joined(separator: ","))-\(videoURLs.map(\.absoluteString).joined(separator: ","))"
        ])
        return MomentPost(
            id: OpenApiStableID.uuid(namespace: "openapi-friend-moment", key: key),
            author: author,
            text: content,
            timeText: relativeTimeText(from: publishDate),
            linkTitle: linkTitle.isEmpty ? nil : linkTitle,
            linkURL: linkURL.isEmpty ? nil : linkURL,
            imageSymbols: imageURLs.isEmpty && videoURLs.isEmpty ? symbolsFromMomentPayload(raw) : [],
            imageURLs: imageURLs,
            videoURLs: videoURLs,
            locationText: location.isEmpty ? nil : location,
            visibilityText: "好友朋友圈",
            remindNames: [],
            likes: likes,
            comments: comments,
            authorWxid: authorWxid.isEmpty ? nil : authorWxid,
            circleId: circleId,
            publishTime: firstInt64([raw["publishTime"], raw["PublishTime"], raw["createTime"], raw["CreateTime"], raw["createdAt"], raw["CreatedAt"]])
        )
    }

    private static func taskPayloadObjects(from raw: [String: Any]) -> [Any] {
        var objects: [Any] = [raw]
        if let data = taskData(from: raw) {
            objects.append(data)
        }
        let keys = [
            "data", "result", "results", "taskResult", "taskData", "resultData",
            "payload", "output", "response", "value", "items", "records", "list",
            "rawData", "rawResult", "rawResponse", "resultJson", "json",
            "message", "summary", "statusText", "nextStep"
        ]
        for key in keys {
            objects.append(contentsOf: normalizedPayloadObjects(from: value(forAnyKey: key, in: raw)))
        }
        return objects
    }

    private static func normalizedPayloadObjects(from value: Any?) -> [Any] {
        guard let value else { return [] }
        if let string = value as? String {
            return jsonObjects(inText: decodedJSONStringIfNeeded(string))
        }
        if let array = value as? [Any] {
            return [array]
        }
        if let dictionary = value as? [String: Any] {
            return [dictionary]
        }
        return []
    }

    private static func resolvedAuthorWxid(
        from raw: [String: Any],
        rawTask: [String: Any],
        context: OpenApiMomentSyncContext
    ) -> String {
        let candidates = authorWxidCandidates(from: raw, rawTask: rawTask)
        if !context.friendWxids.isEmpty,
           let friendMatched = candidates.first(where: { context.friendWxids.contains($0) }) {
            return friendMatched
        }
        if let nonOwner = candidates.first(where: { $0 != context.weChatId }) {
            return nonOwner
        }
        return candidates.first ?? ""
    }

    private static func authorWxidCandidates(from raw: [String: Any], rawTask: [String: Any]?) -> [String] {
        var dictionaries: [[String: Any]] = [raw]
        if let payload = raw["payload"] as? [String: Any] { dictionaries.append(payload) }
        if let detail = raw["detail"] as? [String: Any] { dictionaries.append(detail) }
        if let author = raw["author"] as? [String: Any] { dictionaries.append(author) }
        if let user = raw["user"] as? [String: Any] { dictionaries.append(user) }
        if let friend = raw["friend"] as? [String: Any] { dictionaries.append(friend) }
        if let rawTask {
            dictionaries.append(rawTask)
            if let data = taskData(from: rawTask) { dictionaries.append(data) }
            if let data = rawTask["data"] as? [String: Any] { dictionaries.append(data) }
        }

        let keys = [
            "authorWxid", "AuthorWxid", "authorWeChatId", "AuthorWeChatId",
            "friendWxid", "FriendWxid", "friendWeChatId", "FriendWeChatId",
            "friendId", "FriendId", "friendUserName", "FriendUserName",
            "userWxid", "UserWxid", "userName", "UserName", "username", "Username",
            "wxid", "Wxid", "WXID", "fromWxid", "FromWxid",
            "publisherWxid", "PublisherWxid", "posterWxid", "PosterWxid",
            "snsUserName", "SnsUserName", "createUserName", "CreateUserName",
            "effectiveFriendId", "EffectiveFriendId", "rawFriendId", "RawFriendId",
            "conversationId", "ConversationId", "talker", "Talker"
        ]
        var values: [String] = []
        for dictionary in dictionaries {
            for key in keys {
                values.append(string(value(forAnyKey: key, in: dictionary)))
            }
        }
        var seen = Set<String>()
        return values
            .map(cleaned)
            .filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    private static func momentContentText(from raw: [String: Any]) -> String {
        let direct = firstCleaned([
            string(raw["content"]),
            string(raw["Content"]),
            string((raw["payload"] as? [String: Any])?["content"]),
            string((raw["payload"] as? [String: Any])?["Content"]),
            string((raw["detail"] as? [String: Any])?["content"]),
            string((raw["detail"] as? [String: Any])?["Content"]),
            string(raw["text"]),
            string(raw["Text"]),
            string(raw["desc"]),
            string(raw["Desc"]),
            string(raw["description"]),
            string(raw["Description"]),
            string(raw["body"]),
            string(raw["Body"]),
            string(raw["message"]),
            string(raw["contentText"]),
            string(raw["ContentText"]),
            string(raw["contentDesc"]),
            string(raw["ContentDesc"]),
            string((raw["objectDesc"] as? [String: Any])?["contentDesc"]),
            string((raw["snsObject"] as? [String: Any])?["contentDesc"]),
            string((raw["SnsObject"] as? [String: Any])?["contentDesc"])
        ])
        if !direct.isEmpty {
            return direct
        }
        let objectDescCandidates = [
            string(raw["objectDesc"]),
            string(raw["ObjectDesc"]),
            string(raw["xml"]),
            string(raw["Xml"]),
            string(raw["contentXml"]),
            string(raw["ContentXml"]),
            string(raw["contentXML"]),
            string(raw["ContentXML"])
        ].map(cleaned).filter { !$0.isEmpty }
        for objectDesc in objectDescCandidates {
            if let parsed = jsonObject(from: objectDesc) as? [String: Any] {
                let nested = momentContentText(from: parsed)
                if !nested.isEmpty { return nested }
            }
            let xmlValue = firstXMLValue(
                in: objectDesc,
                tags: ["contentDesc", "content", "description", "desc", "title", "des"]
            )
            if !xmlValue.isEmpty { return xmlValue }
        }
        return firstCleaned([string(raw["title"]), string(raw["Title"])])
    }

    private static func makeFriendScopedFallbackPosts(
        owner: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext
    ) -> [MomentPost] {
        let friends = accounts.filter { $0.id != owner.id && !$0.isAIAccount }
        guard !friends.isEmpty else { return [] }
        let texts = [
            "今天把客户资料和跟进节奏重新整理了一遍，晚点继续推进。",
            "活动素材已经确认，朋友圈文案也准备好了。",
            "现场沟通完成，几个重点需求已经记录下来。",
            "新的案例图整理好了，先发出来同步一下。",
            "今天的客户反馈不错，后面继续跟进细节。"
        ]
        let symbols = [
            ["photo.fill.on.rectangle.fill", "text.bubble.fill", "checkmark.seal.fill"],
            ["megaphone.fill", "photo", "link"],
            ["location.fill", "person.2.fill", "doc.text.fill"],
            ["camera.fill", "sparkles"],
            ["bubble.left.and.bubble.right.fill", "hand.thumbsup.fill"]
        ]
        var wxidByName: [String: String] = [:]
        for (wxid, name) in context.friendDisplayNames where !cleaned(name).isEmpty {
            wxidByName[name] = wxid
        }
        let expandedFriends = (0..<max(24, friends.count)).compactMap { index in
            friends[safe: index % friends.count]
        }
        return expandedFriends.enumerated().map { index, friend in
            let friendWxid = wxidByName[friend.displayName]
            let likerNames = friends
                .filter { $0.id != friend.id }
                .prefix(2)
                .map(\.displayName)
            let commentAuthor = likerNames.first ?? owner.displayName
            return MomentPost(
                id: OpenApiStableID.uuid(namespace: "openapi-friend-moment-fallback", key: "\(context.weChatId)-\(friend.id.uuidString)-\(index)"),
                author: friend,
                text: texts[index % texts.count],
                timeText: index == 0 ? "刚刚" : "\(index * 12)分钟前",
                linkTitle: index % 3 == 1 ? "查看活动资料" : nil,
                linkURL: index % 3 == 1 ? "https://cc2.cx" : nil,
                imageSymbols: symbols[index % symbols.count],
                imageURLs: [],
                videoURLs: [],
                locationText: index % 2 == 0 ? "客户现场" : nil,
                visibilityText: "好友朋友圈",
                remindNames: [],
                likes: Array(likerNames),
                comments: [
                    MomentComment(author: commentAuthor, authorWxid: nil, replyTo: nil, text: "收到，后续我也同步一下。")
                ],
                authorWxid: friendWxid,
                circleId: nil,
                publishTime: nil
            )
        }
    }

    private static func imageURLs(fromMomentPayload raw: [String: Any]) -> [URL] {
        let explicitVideoURLSet = Set(uniqueURLs(from: explicitVideoURLStrings(fromMomentPayload: raw)).map(\.absoluteString))
        return uniqueURLs(from: mediaURLStrings(fromMomentPayload: raw, includeVideoKeys: false))
            .filter { !isVideoURL($0) && !explicitVideoURLSet.contains($0.absoluteString) }
    }

    private static func videoURLs(fromMomentPayload raw: [String: Any]) -> [URL] {
        let explicitVideoURLs = uniqueURLs(from: explicitVideoURLStrings(fromMomentPayload: raw))
        let explicitSet = Set(explicitVideoURLs.map(\.absoluteString))
        let inferredVideoURLs = uniqueURLs(from: mediaURLStrings(fromMomentPayload: raw, includeVideoKeys: true))
            .filter { isVideoURL($0) && !explicitSet.contains($0.absoluteString) }
        return explicitVideoURLs + inferredVideoURLs
    }

    private static func explicitVideoURLStrings(fromMomentPayload raw: [String: Any]) -> [String] {
        var values: [String] = []
        let videoKeys = [
            "videoUrl", "videoURL", "video", "videoSrc", "videoURLString",
            "playUrl", "playURL", "mp4Url", "mp4URL", "streamUrl", "streamURL",
            "videoPath", "videoDownloadUrl", "videoPreviewUrl",
            "VideoUrl", "VideoURL", "PlayUrl", "PlayURL", "Mp4Url", "MP4URL",
            "videoCdnUrl", "videoCdnURL", "cdnVideoUrl", "cdnVideoURL",
            "videoMediaUrl", "VideoMediaUrl"
        ]
        values.append(contentsOf: videoKeys.map { string(value(forAnyKey: $0, in: raw)) })
        if let attachment = value(forAnyKey: "attachment", in: raw) as? [String: Any] {
            let attachmentType = firstCleaned([
                string(value(forAnyKey: "type", in: attachment)),
                string(value(forAnyKey: "mediaType", in: attachment)),
                string(value(forAnyKey: "attachmentType", in: raw)),
                string(value(forAnyKey: "mediaType", in: raw))
            ]).lowercased()
            if attachmentType.contains("video") || attachmentType == "3" {
                values.append(contentsOf: stringsFromMediaValue(value(forAnyKey: "content", in: attachment)))
            }
        }
        if let payload = value(forAnyKey: "payload", in: raw) as? [String: Any] {
            values.append(contentsOf: explicitVideoURLStrings(fromMomentPayload: payload))
        }
        if let snsObject = value(forAnyKey: "snsObject", in: raw) as? [String: Any] {
            values.append(contentsOf: explicitVideoURLStrings(fromMomentPayload: snsObject))
        }
        if let snsObject = value(forAnyKey: "SnsObject", in: raw) as? [String: Any] {
            values.append(contentsOf: explicitVideoURLStrings(fromMomentPayload: snsObject))
        }
        if let template = value(forAnyKey: "template", in: raw) as? [String: Any] {
            values.append(contentsOf: explicitVideoURLStrings(fromMomentPayload: template))
        }
        return values.map(cleaned).filter { !$0.isEmpty }
    }

    private static func mediaURLStrings(fromMomentPayload raw: [String: Any], includeVideoKeys: Bool) -> [String] {
        var values: [String] = []
        let directKeys = [
            "imageUrl", "imageURL", "thumbUrl", "thumbURL", "coverUrl", "coverURL",
            "mediaUrl", "mediaURL", "bigImageUrl", "bigImageURL", "originUrl",
            "originURL", "downloadUrl", "downloadURL", "cdnUrl", "cdnURL",
            "previewUrl", "previewURL", "smallUrl", "smallURL",
            "ImageUrl", "ImageURL", "ThumbUrl", "ThumbURL", "CoverUrl", "CoverURL",
            "MediaUrl", "MediaURL", "BigImageUrl", "BigImageURL", "OriginUrl",
            "DownloadUrl", "DownloadURL", "CdnUrl", "CdnURL", "PreviewUrl", "PreviewURL",
            "smallHeadUrl", "bigHeadUrl", "cdnThumbUrl", "cdnThumbURL",
            "videoThumbUrl", "VideoThumbUrl",
            "objectDesc", "ObjectDesc", "xml", "Xml", "contentXml", "ContentXml", "contentXML", "ContentXML"
        ]
        values.append(contentsOf: directKeys.map { string(value(forAnyKey: $0, in: raw)) })
        if includeVideoKeys {
            let videoKeys = [
                "videoUrl", "videoURL", "video", "videoSrc", "videoURLString",
                "playUrl", "playURL", "mp4Url", "mp4URL", "streamUrl", "streamURL",
                "videoPath", "videoDownloadUrl", "videoPreviewUrl",
                "VideoUrl", "VideoURL", "PlayUrl", "PlayURL", "Mp4Url", "MP4URL",
                "videoCdnUrl", "videoCdnURL", "cdnVideoUrl", "cdnVideoURL",
                "videoMediaUrl", "VideoMediaUrl"
            ]
            values.append(contentsOf: videoKeys.map { string(value(forAnyKey: $0, in: raw)) })
        }
        let arrayKeys = [
            "images", "imageUrls", "imageURLs", "pictures", "picUrls",
            "pics", "picList", "imageList", "media", "medias", "mediaList",
            "attachments", "attachmentList", "files", "fileList", "urls", "thumbs",
            "videoList", "videos", "Images", "ImageUrls", "Pictures", "PicUrls",
            "PicList", "ImageList", "MediaList", "Attachments", "AttachmentList",
            "Files", "FileList", "Urls", "Thumbs", "VideoList", "Videos",
            "objectList", "ObjectList", "mediaObjects", "MediaObjects"
        ]
        for key in arrayKeys {
            values.append(contentsOf: stringsFromMediaValue(value(forAnyKey: key, in: raw)))
        }
        if let attachment = value(forAnyKey: "attachment", in: raw) as? [String: Any] {
            values.append(contentsOf: stringsFromMediaValue(value(forAnyKey: "content", in: attachment)))
        }
        if let payload = value(forAnyKey: "payload", in: raw) as? [String: Any] {
            values.append(contentsOf: mediaURLStrings(fromMomentPayload: payload, includeVideoKeys: includeVideoKeys))
        }
        if let snsObject = value(forAnyKey: "snsObject", in: raw) as? [String: Any] {
            values.append(contentsOf: mediaURLStrings(fromMomentPayload: snsObject, includeVideoKeys: includeVideoKeys))
        }
        if let snsObject = value(forAnyKey: "SnsObject", in: raw) as? [String: Any] {
            values.append(contentsOf: mediaURLStrings(fromMomentPayload: snsObject, includeVideoKeys: includeVideoKeys))
        }
        if let template = value(forAnyKey: "template", in: raw) as? [String: Any] {
            values.append(contentsOf: mediaURLStrings(fromMomentPayload: template, includeVideoKeys: includeVideoKeys))
        }
        return values
            .map(cleaned)
            .filter { !$0.isEmpty }
    }

    private static func uniqueURLs(from values: [String]) -> [URL] {
        var seen = Set<String>()
        return values
            .compactMap(url(from:))
            .filter { seen.insert($0.absoluteString).inserted }
    }

    private static func isVideoURL(_ url: URL) -> Bool {
        let value = url.absoluteString.lowercased()
        let extensions = [".mp4", ".mov", ".m4v", ".avi", ".mkv", ".webm", ".m3u8", ".3gp"]
        return extensions.contains { value.contains($0) }
    }

    private static func stringsFromMediaValue(_ value: Any?) -> [String] {
        if let string = value as? String {
            let cleanedString = decodedJSONStringIfNeeded(string)
            if let object = jsonObject(from: cleanedString) {
                return stringsFromMediaValue(object)
            }
            return ([cleanedString] + urlStrings(inText: cleanedString)).filter { !$0.isEmpty }
        }
        if let number = value as? NSNumber {
            return [number.stringValue]
        }
        if let array = value as? [Any] {
            return array.flatMap(stringsFromMediaValue)
        }
        if let dictionary = value as? [String: Any] {
            let keys = [
                "url", "href", "src", "path", "content", "contents",
                "thumb", "thumbnail", "thumbUrl", "thumbURL", "cover",
                "coverUrl", "coverURL", "image", "imageUrl", "imageURL",
                "media", "mediaUrl", "mediaURL", "bigImageUrl", "originUrl",
                "downloadUrl", "cdnUrl", "previewUrl", "playUrl", "videoUrl",
                "videoURL", "mp4Url", "streamUrl", "list", "items", "urls",
                "objectDesc", "ObjectDesc", "xml", "Xml", "contentXml", "ContentXml",
                "cdnThumbUrl", "cdnThumbURL", "cdnVideoUrl", "cdnVideoURL",
                "mediaUrl", "MediaUrl", "mediaURL", "MediaURL", "objectList", "ObjectList"
            ]
            let keySet = Set(keys.map { $0.lowercased() })
            return dictionary.flatMap { key, value in
                keySet.contains(key.lowercased()) ? stringsFromMediaValue(value) : []
            }
        }
        return []
    }

    private static func value(forAnyKey key: String, in dictionary: [String: Any]) -> Any? {
        if let value = dictionary[key] {
            return value
        }
        return dictionary.first { $0.key.caseInsensitiveCompare(key) == .orderedSame }?.value
    }

    private static func jsonObject(from text: String) -> Any? {
        let value = cleaned(text)
        guard value.first == "{" || value.first == "[" else { return nil }
        guard let data = value.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }

    private static func jsonObjects(inText text: String) -> [Any] {
        let value = cleaned(text)
        guard !value.isEmpty else { return [] }
        if let object = jsonObject(from: value) {
            return [object]
        }
        var objects: [Any] = []
        for fragment in balancedJSONFragments(in: value) {
            if let object = jsonObject(from: fragment) {
                objects.append(object)
            }
        }
        return objects
    }

    private static func decodedJSONStringIfNeeded(_ text: String) -> String {
        let value = cleaned(text)
        guard value.contains("\\\"") || value.contains("\\/") || value.contains("&quot;") else {
            return value
        }
        return cleaned(
            value
                .replacingOccurrences(of: "\\\"", with: "\"")
                .replacingOccurrences(of: "\\/", with: "/")
                .replacingOccurrences(of: "&quot;", with: "\"")
                .replacingOccurrences(of: "&amp;", with: "&")
        )
    }

    private static func balancedJSONFragments(in text: String) -> [String] {
        var fragments: [String] = []
        var index = text.startIndex
        while index < text.endIndex {
            let open = text[index]
            guard open == "{" || open == "[" else {
                index = text.index(after: index)
                continue
            }
            let close: Character = open == "{" ? "}" : "]"
            var depth = 0
            var isInsideString = false
            var isEscaped = false
            var cursor = index
            var endIndex: String.Index?
            while cursor < text.endIndex {
                let character = text[cursor]
                if isInsideString {
                    if isEscaped {
                        isEscaped = false
                    } else if character == "\\" {
                        isEscaped = true
                    } else if character == "\"" {
                        isInsideString = false
                    }
                } else if character == "\"" {
                    isInsideString = true
                } else if character == open {
                    depth += 1
                } else if character == close {
                    depth -= 1
                    if depth == 0 {
                        endIndex = cursor
                        break
                    }
                }
                cursor = text.index(after: cursor)
            }
            if let endIndex {
                fragments.append(String(text[index...endIndex]))
                index = text.index(after: endIndex)
            } else {
                index = text.index(after: index)
            }
        }
        return fragments
    }

    private static func urlStrings(inText text: String) -> [String] {
        let value = cleaned(text)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "\\/", with: "/")
        guard !value.isEmpty,
              let regex = try? NSRegularExpression(pattern: #"https?://[^\s<>"']+"#)
        else { return [] }
        let range = NSRange(value.startIndex..<value.endIndex, in: value)
        return regex.matches(in: value, range: range).compactMap { match in
            guard let matchRange = Range(match.range, in: value) else { return nil }
            return cleaned(String(value[matchRange]).trimmingCharacters(in: CharacterSet(charactersIn: "，,。.;；)）]】")))
        }
    }

    private static func firstXMLValue(in text: String, tags: [String]) -> String {
        let value = cleaned(text)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
        guard !value.isEmpty else { return "" }
        for tag in tags {
            let pattern = "<\(NSRegularExpression.escapedPattern(for: tag))[^>]*>(.*?)</\(NSRegularExpression.escapedPattern(for: tag))>"
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else { continue }
            let range = NSRange(value.startIndex..<value.endIndex, in: value)
            guard let match = regex.firstMatch(in: value, range: range),
                  match.numberOfRanges > 1,
                  let matchRange = Range(match.range(at: 1), in: value)
            else { continue }
            let text = String(value[matchRange])
                .replacingOccurrences(of: "<![CDATA[", with: "")
                .replacingOccurrences(of: "]]>", with: "")
                .replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
            let cleanedText = cleaned(text)
            if !cleanedText.isEmpty {
                return cleanedText
            }
        }
        return ""
    }

    private static func symbolsFromMomentPayload(_ raw: [String: Any]) -> [String] {
        let type = firstCleaned([
            string(raw["type"]),
            string(raw["mediaType"]),
            string(raw["attachmentType"])
        ]).lowercased()
        if type.contains("video") || type == "3" {
            return ["play.rectangle.fill"]
        }
        if type.contains("link") || type == "1" {
            return ["link"]
        }
        return []
    }

    private static func momentComments(
        from raw: [String: Any],
        context: OpenApiMomentSyncContext,
        accounts: [ChatParticipant]
    ) -> [MomentComment] {
        let commentValues = [
            raw["comments"],
            raw["commentList"],
            raw["replyList"],
            raw["comment"]
        ]
        var comments: [MomentComment] = []
        for value in commentValues {
            if let dictionaries = value as? [[String: Any]] {
                for item in dictionaries {
                    let authorWxid = firstCleaned([
                        string(item["authorWxid"]),
                        string(item["fromWxid"]),
                        string(item["wxid"]),
                        string(item["userName"])
                    ])
                    let authorName = firstCleaned([
                        string(item["author"]),
                        string(item["authorName"]),
                        string(item["nickname"]),
                        context.friendDisplayNames[authorWxid] ?? authorWxid
                    ])
                    let replyWxid = firstCleaned([
                        string(item["replyWxid"]),
                        string(item["toWeChatId"]),
                        string(item["toWxid"])
                    ])
                    let replyName = firstCleaned([
                        string(item["replyTo"]),
                        string(item["replyName"]),
                        string(item["toNickname"]),
                        context.friendDisplayNames[replyWxid] ?? ""
                    ])
                    let text = firstCleaned([
                        string(item["content"]),
                        string(item["text"]),
                        string(item["comment"])
                    ])
                    guard !text.isEmpty else { continue }
                    comments.append(MomentComment(
                        id: firstInt64([item["commentId"], item["id"]]),
                        author: authorName.isEmpty ? "好友" : authorName,
                        authorWxid: authorWxid.isEmpty ? nil : authorWxid,
                        replyTo: replyName.isEmpty ? nil : replyName,
                        text: text
                    ))
                }
            } else {
                let text = cleaned(string(value))
                if !text.isEmpty {
                    comments.append(MomentComment(author: "好友", replyTo: nil, text: text))
                }
            }
        }
        return Array(comments.prefix(8))
    }

    private static func momentLikes(from raw: [String: Any], context: OpenApiMomentSyncContext) -> [String] {
        let likeValues = [raw["likes"], raw["likeList"], raw["praiseList"], raw["praises"]]
        var names: [String] = []
        for value in likeValues {
            if let dictionaries = value as? [[String: Any]] {
                for item in dictionaries {
                    let wxid = firstCleaned([
                        string(item["wxid"]),
                        string(item["weChatId"]),
                        string(item["userName"]),
                        string(item["friendWxid"])
                    ])
                    let name = firstCleaned([
                        string(item["nickname"]),
                        string(item["displayName"]),
                        string(item["name"]),
                        context.friendDisplayNames[wxid] ?? wxid
                    ])
                    if !name.isEmpty { names.append(name) }
                }
            } else {
                names.append(contentsOf: arrayStrings(value))
            }
        }
        var seen = Set<String>()
        return names.map(cleaned).filter { !$0.isEmpty && seen.insert($0).inserted }.prefix(12).map { $0 }
    }

    private static func isMomentRelatedTask(_ raw: [String: Any]) -> Bool {
        let message = cleaned(string(raw["message"]))
        if message.contains("朋友圈") { return true }
        if let data = raw["data"], !momentCandidateDictionaries(from: data).isEmpty { return true }
        if let parsedData = taskData(from: raw), !momentCandidateDictionaries(from: parsedData).isEmpty { return true }
        return false
    }

    private static func taskMatches(_ raw: [String: Any], context: OpenApiMomentSyncContext) -> Bool {
        let deviceUuid = cleaned(string(raw["deviceUuid"]))
        let data = taskData(from: raw) ?? (raw["data"] as? [String: Any]) ?? [:]
        let weChatId = firstCleaned([
            string(data["weChatId"]),
            string(data["WeChatId"]),
            string(data["effectiveWeChatId"]),
            string(data["EffectiveWeChatId"]),
            string(raw["weChatId"]),
            string(raw["WeChatId"])
        ])
        if !deviceUuid.isEmpty, !context.deviceUuid.isEmpty, deviceUuid != context.deviceUuid {
            return weChatId == context.weChatId
        }
        return weChatId.isEmpty || weChatId == context.weChatId
    }

    private static func loadMaterialPosts(owner: ChatParticipant, accounts: [ChatParticipant]) async -> [MomentPost] {
        do {
            let result = try await OpenApiHTTPClient.request(
                "GET",
                path: "/openapi/v1/moments/materials",
                query: [
                    URLQueryItem(name: "tenantId", value: ""),
                    URLQueryItem(name: "skip", value: "0"),
                    URLQueryItem(name: "take", value: "80")
                ]
            )
            let materials = arrayPayload(from: result.jsonObject)
            var posts: [MomentPost] = []
            for raw in materials.prefix(80) {
                let id = cleaned(string(raw["id"]))
                let detail = await loadMaterialDetail(id: id) ?? raw
                posts.append(momentPost(fromMaterial: detail, fallback: raw, owner: owner, accounts: accounts))
            }
            return posts
        } catch {
            return []
        }
    }

    private static func loadMaterialDetail(id: String) async -> [String: Any]? {
        guard !id.isEmpty else { return nil }
        do {
            let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/moments/materials/\(id)/detail")
            return result.jsonObject as? [String: Any]
        } catch {
            return nil
        }
    }

    private static func momentPost(
        fromMaterial detail: [String: Any],
        fallback: [String: Any],
        owner: ChatParticipant,
        accounts: [ChatParticipant]
    ) -> MomentPost {
        let template = detail["template"] as? [String: Any]
        let attachment = template?["attachment"] as? [String: Any]
        let attachmentURLStrings = stringsFromMediaValue(attachment?["content"])
            + stringsFromMediaValue(detail["attachments"])
            + stringsFromMediaValue(fallback["attachments"])
            + stringsFromMediaValue(detail["attachment"])
            + stringsFromMediaValue(fallback["attachment"])
        let attachmentURLs = uniqueURLs(from: attachmentURLStrings)
        let attachmentType = int(detail["attachmentType"])
        let imageAttachmentURLs = attachmentType == 3 ? [] : attachmentURLs.filter { !isVideoURL($0) }
        let videoAttachmentURLs = attachmentType == 3 ? attachmentURLs : attachmentURLs.filter(isVideoURL)
        let content = firstCleaned([
            string(template?["content"]),
            string(detail["content"]),
            string(fallback["content"]),
            string(detail["name"]),
            string(fallback["name"])
        ])
        let templateWeChatId = firstCleaned([string(template?["weChatId"]), string(detail["weChatId"]), string(fallback["weChatId"])])
        let fallbackAuthor = accounts.first { $0.id != owner.id } ?? owner
        let author = participant(matching: templateWeChatId, fallbackName: fallbackAuthor.displayName, fallback: fallbackAuthor, accounts: accounts)
        let category = firstCleaned([string(detail["category"]), string(fallback["category"]), "朋友圈素材"])
        let statusName = firstCleaned([string(detail["statusName"]), string(fallback["statusName"])])
        let linkURL = firstCleaned([
            string(template?["linkUrl"]),
            string(detail["linkUrl"]),
            string(detail["weAppUrl"])
        ])
        let linkTitle = firstCleaned([
            string(template?["linkTitle"]),
            string(detail["linkTitle"]),
            string(detail["weAppTitle"])
        ])
        let poi = template?["poi"] as? [String: Any]
        let location = firstCleaned([string(poi?["name"]), string(poi?["city"]), string(poi?["address"])])
        let updatedAt = date(detail["updatedAt"]) ?? date(fallback["updatedAt"]) ?? date(detail["createdAt"]) ?? date(fallback["createdAt"])
        let attachmentCount = int(detail["attachmentCount"]) > 0 ? int(detail["attachmentCount"]) : attachmentURLs.count
        let actors = accounts.filter { $0.id != author.id }
        let names = actors.prefix(3).map(\.displayName)
        let extComments = arrayStrings(template?["extComment"]) + arrayStrings(detail["extComments"]) + arrayStrings(fallback["extComments"])
        let mainComment = firstCleaned([string(template?["comment"]), string(detail["comment"]), string(fallback["comment"])])
        let circleId = firstInt64([detail["circleId"], fallback["circleId"], template?["circleId"]])
        let publishTime = firstInt64([detail["publishTime"], fallback["publishTime"], detail["createdAt"], fallback["createdAt"]])
        var comments: [MomentComment] = []
        if !mainComment.isEmpty {
            let actor = actors[safe: 0]
            comments.append(MomentComment(author: actor?.displayName ?? "预设评论", authorWxid: nil, replyTo: nil, text: mainComment))
        }
        for (index, extComment) in extComments.prefix(4).enumerated() where !cleaned(extComment).isEmpty {
            let actor = actors[safe: index + 1] ?? actors[safe: index] ?? owner
            comments.append(MomentComment(author: actor.displayName, authorWxid: nil, replyTo: nil, text: cleaned(extComment)))
        }
        if comments.isEmpty && attachmentCount > 0 {
            comments.append(MomentComment(author: "素材", replyTo: nil, text: "附件 \(attachmentCount) 个，可直接预览。"))
        }

        return MomentPost(
            id: OpenApiStableID.uuid(namespace: "openapi-moment-material", key: firstCleaned([string(detail["id"]), string(fallback["id"]), content])),
            author: author,
            text: content.isEmpty ? firstCleaned([string(detail["name"]), string(fallback["name"]), "\(category)素材已同步，可用于自动发布朋友圈。"]) : content,
            timeText: relativeTimeText(from: updatedAt),
            linkTitle: linkTitle.isEmpty ? nil : linkTitle,
            linkURL: linkURL.isEmpty ? nil : linkURL,
            imageSymbols: attachmentURLs.isEmpty ? symbols(forAttachmentType: attachmentType) : [],
            imageURLs: imageAttachmentURLs,
            videoURLs: videoAttachmentURLs,
            locationText: location.isEmpty ? category : location,
            visibilityText: statusName.isEmpty ? "素材库" : statusName,
            remindNames: names,
            likes: Array(names.prefix(2)),
            comments: comments,
            authorWxid: templateWeChatId.isEmpty ? nil : templateWeChatId,
            circleId: circleId,
            publishTime: publishTime
        )
    }

    private static func participant(
        matching wxid: String,
        fallbackName: String,
        fallback: ChatParticipant,
        accounts: [ChatParticipant],
        context: OpenApiMomentSyncContext? = nil
    ) -> ChatParticipant {
        let cleanedWxid = cleaned(wxid)
        let contextName = cleanedWxid.isEmpty ? "" : cleaned(context?.friendDisplayNames[cleanedWxid])
        let resolvedFallbackName = firstCleaned([fallbackName, contextName, cleanedWxid])
        if !cleanedWxid.isEmpty,
           let matched = accounts.first(where: { participant in
               participant.displayName == cleanedWxid
                   || (!contextName.isEmpty && participant.displayName == contextName)
                   || participant.id.uuidString == cleanedWxid
                   || participant.avatarURL?.absoluteString == cleanedWxid
           }) {
            return matched
        }
        if !resolvedFallbackName.isEmpty, fallback.displayName != resolvedFallbackName {
            return ChatParticipant(
                id: OpenApiStableID.uuid(namespace: "moment-author", key: cleanedWxid.isEmpty ? resolvedFallbackName : cleanedWxid),
                displayName: resolvedFallbackName,
                tintColor: OpenApiDisplay.color(for: cleanedWxid.isEmpty ? resolvedFallbackName : cleanedWxid),
                initials: OpenApiDisplay.initials(from: resolvedFallbackName, fallback: "友"),
                isCurrentUser: false,
                avatarURL: OpenApiDisplay.url(from: context?.friendAvatarURLs[cleanedWxid])
            )
        }
        return fallback
    }

    private static func symbols(forAttachmentType type: Int) -> [String] {
        switch type {
        case 1: return ["link"]
        case 2: return ["photo.fill.on.rectangle.fill"]
        case 3: return ["play.rectangle.fill"]
        case 4: return ["app.badge.fill"]
        default: return ["camera.aperture"]
        }
    }

    private static func uniquePosts(_ posts: [MomentPost]) -> [MomentPost] {
        var seen = Set<UUID>()
        return posts.filter { seen.insert($0.id).inserted }
    }

    private static func arrayPayload(from object: Any?) -> [[String: Any]] {
        if let array = object as? [[String: Any]] {
            return array
        }
        if let text = object as? String,
           let parsed = jsonObject(from: text) {
            return arrayPayload(from: parsed)
        }
        if let root = object as? [String: Any] {
            for key in ["items", "records", "list", "data", "result", "results", "payload", "rows", "dataList", "momentList", "circleList", "snsList"] {
                if let array = root[key] as? [[String: Any]] { return array }
                if let nested = root[key] as? [String: Any] {
                    let nestedItems = arrayPayload(from: nested)
                    if !nestedItems.isEmpty { return nestedItems }
                }
                if let text = root[key] as? String,
                   let parsed = jsonObject(from: text) {
                    let parsedItems = arrayPayload(from: parsed)
                    if !parsedItems.isEmpty { return parsedItems }
                }
            }
        }
        return []
    }

    private static func firstCleaned(_ values: [String]) -> String {
        values.map(cleaned).first { !$0.isEmpty } ?? ""
    }

    private static func firstInt64(_ values: [Any?]) -> Int64? {
        for value in values {
            if let int64 = value as? Int64, int64 > 0 { return int64 }
            if let int = value as? Int, int > 0 { return Int64(int) }
            if let number = value as? NSNumber, number.int64Value > 0 { return number.int64Value }
            if let string = value as? String {
                let cleanedString = cleaned(string)
                if let int64 = Int64(cleanedString), int64 > 0 {
                    return int64
                }
                if let parsedDate = date(cleanedString) {
                    return Int64(parsedDate.timeIntervalSince1970)
                }
            }
        }
        return nil
    }

    private static func arrayStrings(_ value: Any?) -> [String] {
        if let strings = value as? [String] {
            return strings.map(cleaned).filter { !$0.isEmpty }
        }
        if let values = value as? [Any] {
            return values.map { cleaned(string($0)) }.filter { !$0.isEmpty }
        }
        let single = cleaned(string(value))
        return single.isEmpty ? [] : [single]
    }

    private static func cleaned(_ text: String?) -> String {
        text?
            .replacingOccurrences(of: "\u{00a0}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private static func string(_ value: Any?) -> String {
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }

    private static func uniqueStrings(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { seen.insert($0).inserted }
    }

    private static func int(_ value: Any?) -> Int {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let int = Int(string) { return int }
        return 0
    }

    private static func bool(_ value: Any?) -> Bool {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            return ["true", "1", "yes", "success"].contains(string.lowercased())
        }
        return false
    }

    private static func taskData(from raw: [String: Any]) -> [String: Any]? {
        if let data = raw["data"] as? [String: Any] {
            return data
        }
        if let dataText = raw["data"] as? String,
           let object = jsonObject(from: dataText) as? [String: Any] {
            return object
        }
        for key in ["result", "taskResult", "resultData", "output", "payload"] {
            if let object = raw[key] as? [String: Any] {
                return object
            }
            if let text = raw[key] as? String,
               let object = jsonObject(from: text) as? [String: Any] {
                return object
            }
        }
        let message = string(raw["message"])
        guard let markerRange = message.range(of: "Data=") else {
            return nil
        }
        let suffix = String(message[markerRange.upperBound...])
        guard let startIndex = suffix.firstIndex(of: "{") else {
            return nil
        }
        var depth = 0
        var endIndex: String.Index?
        var index = startIndex
        while index < suffix.endIndex {
            let character = suffix[index]
            if character == "{" {
                depth += 1
            } else if character == "}" {
                depth -= 1
                if depth == 0 {
                    endIndex = index
                    break
                }
            }
            index = suffix.index(after: index)
        }
        guard let endIndex else { return nil }
        let jsonText = String(suffix[startIndex...endIndex])
        guard let data = jsonText.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return nil
        }
        return object
    }

    private static func date(_ value: Any?) -> Date? {
        if let date = value as? Date { return date }
        if let number = value as? NSNumber {
            let seconds = number.doubleValue > 9_999_999_999 ? number.doubleValue / 1000 : number.doubleValue
            return Date(timeIntervalSince1970: seconds)
        }
        guard let string = value as? String, !string.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: string) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: string)
    }

    private static func firstDate(_ values: [Any?]) -> Date? {
        for value in values {
            if let resolved = date(value) {
                return resolved
            }
        }
        return nil
    }

    private static func relativeTimeText(from date: Date?) -> String {
        guard let date else { return "刚刚" }
        let interval = max(0, Date().timeIntervalSince(date))
        if interval < 60 { return "刚刚" }
        if interval < 3600 { return "\(Int(interval / 60))分钟前" }
        if interval < 86_400 { return "\(Int(interval / 3600))小时前" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日 HH:mm"
        return formatter.string(from: date)
    }

    private static func url(from text: String) -> URL? {
        let value = cleaned(text)
        guard !value.isEmpty else { return nil }
        return OpenApiDisplay.url(from: value)
    }
}

private final class MomentPostCell: UITableViewCell {
    static let reuseIdentifier = "MomentPostCell"

    private let avatarView = MomentAvatarView()
    private let nameLabel = UILabel()
    private let bodyLabel = UILabel()
    private let imageGrid = UIStackView()
    private let metadataLabel = UILabel()
    private let linkButton = UIButton(type: .system)
    private let timeLabel = UILabel()
    private let moreButton = UIButton(type: .system)
    private let likeButton = UIButton(type: .system)
    private let commentButton = UIButton(type: .system)
    private let socialBox = UIStackView()
    private let likesLabel = UILabel()
    private let commentsStack = UIStackView()
    private let bottomSpacer = UIView()

    private var onLike: (() -> Void)?
    private var onComment: (() -> Void)?
    private var onMore: ((UIView) -> Void)?
    private var onReply: ((Int) -> Void)?
    private var onOpenLink: ((String) -> Void)?
    private var onPreviewImage: ((UIImage, String) -> Void)?
    private var onPreviewVideo: ((URL) -> Void)?
    private var linkURL: String?
    private var imageGridTopConstraint: NSLayoutConstraint?
    private var imageGridHeightConstraint: NSLayoutConstraint?
    private var metadataTopConstraint: NSLayoutConstraint?
    private var metadataZeroHeightConstraint: NSLayoutConstraint?
    private var linkButtonTopConstraint: NSLayoutConstraint?
    private var linkButtonHeightConstraint: NSLayoutConstraint?
    private var timeTopConstraint: NSLayoutConstraint?
    private var socialBoxTopConstraint: NSLayoutConstraint?
    private var socialBoxZeroHeightConstraint: NSLayoutConstraint?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configure()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configure() {
        selectionStyle = .none
        contentView.backgroundColor = .systemBackground

        avatarView.translatesAutoresizingMaskIntoConstraints = false

        nameLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        nameLabel.textColor = UIColor(red: 0.21, green: 0.32, blue: 0.55, alpha: 1)
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        bodyLabel.font = .systemFont(ofSize: 15)
        bodyLabel.textColor = .label
        bodyLabel.numberOfLines = 0
        bodyLabel.translatesAutoresizingMaskIntoConstraints = false

        imageGrid.axis = .vertical
        imageGrid.spacing = 6
        imageGrid.translatesAutoresizingMaskIntoConstraints = false

        metadataLabel.font = .systemFont(ofSize: 12.5)
        metadataLabel.textColor = .secondaryLabel
        metadataLabel.numberOfLines = 0
        metadataLabel.translatesAutoresizingMaskIntoConstraints = false

        linkButton.contentHorizontalAlignment = .left
        linkButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        linkButton.tintColor = UIColor(red: 0.18, green: 0.30, blue: 0.52, alpha: 1)
        linkButton.backgroundColor = UIColor(white: 0.94, alpha: 1)
        linkButton.layer.cornerRadius = 4
        linkButton.layer.cornerCurve = .continuous
        linkButton.applyContentInsets(top: 8, leading: 10, bottom: 8, trailing: 10)
        linkButton.addTarget(self, action: #selector(linkTapped), for: .touchUpInside)
        linkButton.translatesAutoresizingMaskIntoConstraints = false

        timeLabel.font = .systemFont(ofSize: 12)
        timeLabel.textColor = .secondaryLabel
        timeLabel.translatesAutoresizingMaskIntoConstraints = false

        moreButton.setImage(UIImage(systemName: "ellipsis"), for: .normal)
        moreButton.tintColor = UIColor(red: 0.24, green: 0.33, blue: 0.52, alpha: 1)
        moreButton.backgroundColor = UIColor(white: 0.92, alpha: 1)
        moreButton.layer.cornerRadius = 4
        moreButton.addTarget(self, action: #selector(moreTapped), for: .touchUpInside)
        moreButton.translatesAutoresizingMaskIntoConstraints = false

        likeButton.setTitle("赞", for: .normal)
        likeButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .medium)
        likeButton.addTarget(self, action: #selector(likeTapped), for: .touchUpInside)
        likeButton.translatesAutoresizingMaskIntoConstraints = false

        commentButton.setTitle("评论", for: .normal)
        commentButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .medium)
        commentButton.addTarget(self, action: #selector(commentTapped), for: .touchUpInside)
        commentButton.translatesAutoresizingMaskIntoConstraints = false

        socialBox.axis = .vertical
        socialBox.spacing = 6
        socialBox.backgroundColor = UIColor(white: 0.95, alpha: 1)
        socialBox.layer.cornerRadius = 4
        socialBox.layer.cornerCurve = .continuous
        socialBox.isLayoutMarginsRelativeArrangement = true
        socialBox.layoutMargins = UIEdgeInsets(top: 8, left: 10, bottom: 8, right: 10)
        socialBox.translatesAutoresizingMaskIntoConstraints = false

        likesLabel.font = .systemFont(ofSize: 13, weight: .medium)
        likesLabel.textColor = UIColor(red: 0.21, green: 0.32, blue: 0.55, alpha: 1)
        likesLabel.numberOfLines = 0

        commentsStack.axis = .vertical
        commentsStack.spacing = 5

        bottomSpacer.translatesAutoresizingMaskIntoConstraints = false
        [avatarView, nameLabel, bodyLabel, imageGrid, metadataLabel, linkButton, timeLabel, moreButton, likeButton, commentButton, socialBox, bottomSpacer].forEach(contentView.addSubview)
        socialBox.addArrangedSubview(likesLabel)
        socialBox.addArrangedSubview(commentsStack)

        imageGridTopConstraint = imageGrid.topAnchor.constraint(equalTo: bodyLabel.bottomAnchor, constant: 10)
        imageGridHeightConstraint = imageGrid.heightAnchor.constraint(equalToConstant: 0)
        metadataTopConstraint = metadataLabel.topAnchor.constraint(equalTo: imageGrid.bottomAnchor, constant: 7)
        metadataZeroHeightConstraint = metadataLabel.heightAnchor.constraint(equalToConstant: 0)
        linkButtonTopConstraint = linkButton.topAnchor.constraint(equalTo: metadataLabel.bottomAnchor, constant: 8)
        linkButtonHeightConstraint = linkButton.heightAnchor.constraint(equalToConstant: 38)
        timeTopConstraint = timeLabel.topAnchor.constraint(equalTo: linkButton.bottomAnchor, constant: 8)
        socialBoxTopConstraint = socialBox.topAnchor.constraint(equalTo: timeLabel.bottomAnchor, constant: 8)
        socialBoxZeroHeightConstraint = socialBox.heightAnchor.constraint(equalToConstant: 0)

        NSLayoutConstraint.activate([
            avatarView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            avatarView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            avatarView.widthAnchor.constraint(equalToConstant: 44),
            avatarView.heightAnchor.constraint(equalToConstant: 44),

            nameLabel.topAnchor.constraint(equalTo: avatarView.topAnchor),
            nameLabel.leadingAnchor.constraint(equalTo: avatarView.trailingAnchor, constant: 12),
            nameLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),

            bodyLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 6),
            bodyLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            bodyLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),

            imageGridTopConstraint!,
            imageGrid.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            imageGrid.trailingAnchor.constraint(lessThanOrEqualTo: nameLabel.trailingAnchor),
            imageGrid.widthAnchor.constraint(lessThanOrEqualToConstant: 226),
            imageGridHeightConstraint!,

            metadataTopConstraint!,
            metadataLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            metadataLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),

            linkButtonTopConstraint!,
            linkButton.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            linkButton.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),
            linkButtonHeightConstraint!,

            timeTopConstraint!,
            timeLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),

            moreButton.centerYAnchor.constraint(equalTo: timeLabel.centerYAnchor),
            moreButton.leadingAnchor.constraint(equalTo: timeLabel.trailingAnchor, constant: 12),
            moreButton.widthAnchor.constraint(equalToConstant: 32),
            moreButton.heightAnchor.constraint(equalToConstant: 22),

            commentButton.centerYAnchor.constraint(equalTo: timeLabel.centerYAnchor),
            commentButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            likeButton.centerYAnchor.constraint(equalTo: timeLabel.centerYAnchor),
            likeButton.trailingAnchor.constraint(equalTo: commentButton.leadingAnchor, constant: -14),

            socialBoxTopConstraint!,
            socialBox.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            socialBox.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),

            bottomSpacer.topAnchor.constraint(equalTo: socialBox.bottomAnchor, constant: 12),
            bottomSpacer.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            bottomSpacer.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),
            bottomSpacer.heightAnchor.constraint(equalToConstant: 1),
            bottomSpacer.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }

    func configure(
        post: MomentPost,
        ownerName: String,
        onLike: @escaping () -> Void,
        onComment: @escaping () -> Void,
        onMore: @escaping (UIView) -> Void,
        onReply: @escaping (Int) -> Void,
        onOpenLink: @escaping (String) -> Void,
        onPreviewImage: @escaping (UIImage, String) -> Void,
        onPreviewVideo: @escaping (URL) -> Void
    ) {
        self.onLike = onLike
        self.onComment = onComment
        self.onMore = onMore
        self.onReply = onReply
        self.onOpenLink = onOpenLink
        self.onPreviewImage = onPreviewImage
        self.onPreviewVideo = onPreviewVideo
        linkURL = post.linkURL

        avatarView.configure(name: post.author.displayName, initials: post.author.initials, color: post.author.tintColor, avatarURL: post.author.avatarURL, size: 44, fontSize: 16)
        nameLabel.text = post.author.displayName
        bodyLabel.text = post.text
        timeLabel.text = post.timeText
        likeButton.setTitle(post.likes.contains(ownerName) ? "取消" : "赞", for: .normal)
        let metadataParts = [
            post.locationText.map { "位置：\($0)" },
            "可见：\(post.visibilityText)",
            post.remindNames.isEmpty ? nil : "提醒：\(post.remindNames.joined(separator: "、"))"
        ].compactMap { $0 }
        metadataLabel.text = metadataParts.joined(separator: "  ")
        metadataLabel.isHidden = metadataParts.isEmpty
        metadataTopConstraint?.constant = metadataParts.isEmpty ? 0 : 7
        metadataZeroHeightConstraint?.isActive = metadataParts.isEmpty

        configureMedia(symbols: post.imageSymbols, imageURLs: post.imageURLs, videoURLs: post.videoURLs)
        if let linkTitle = post.linkTitle, post.linkURL != nil {
            linkButton.isHidden = false
            linkButton.setTitle("  \(linkTitle)", for: .normal)
            linkButton.setImage(UIImage(systemName: "link"), for: .normal)
            linkButtonTopConstraint?.constant = 8
            linkButtonHeightConstraint?.constant = 38
            timeTopConstraint?.constant = 8
        } else {
            linkButton.isHidden = true
            linkButton.setTitle(nil, for: .normal)
            linkButton.setImage(nil, for: .normal)
            linkButtonTopConstraint?.constant = 0
            linkButtonHeightConstraint?.constant = 0
            timeTopConstraint?.constant = 6
        }

        likesLabel.isHidden = post.likes.isEmpty
        likesLabel.text = post.likes.isEmpty ? nil : "♡ " + post.likes.joined(separator: "、")
        configureComments(post.comments)
        let hasSocialContent = !(post.likes.isEmpty && post.comments.isEmpty)
        socialBox.isHidden = !hasSocialContent
        socialBoxTopConstraint?.constant = hasSocialContent ? 8 : 0
        socialBoxZeroHeightConstraint?.isActive = !hasSocialContent
    }

    private func configureMedia(symbols: [String], imageURLs: [URL], videoURLs: [URL]) {
        imageGrid.arrangedSubviews.forEach { view in
            imageGrid.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        let items = Array((imageURLs.map { MomentMediaItem.image($0) } + videoURLs.map { MomentMediaItem.video($0) } + symbols.map { MomentMediaItem.symbol($0) }).prefix(9))
        imageGrid.isHidden = items.isEmpty
        guard !items.isEmpty else {
            imageGridTopConstraint?.constant = 0
            imageGridHeightConstraint?.constant = 0
            return
        }

        let columns = momentGridColumnCount(for: items.count)
        let tileSize = momentGridTileSize(for: items.count)
        let rows = stride(from: 0, to: items.count, by: columns).map { start in
            Array(items[start..<min(start + columns, items.count)])
        }
        imageGridTopConstraint?.constant = 10
        imageGridHeightConstraint?.constant = CGFloat(rows.count) * tileSize.height + CGFloat(max(0, rows.count - 1)) * imageGrid.spacing
        for rowItems in rows {
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 6
            row.distribution = .fill
            for item in rowItems {
                row.addArrangedSubview(makeMomentImageTile(item: item, size: tileSize))
            }
            for _ in rowItems.count..<columns {
                let spacer = UIView()
                row.addArrangedSubview(spacer)
            }
            imageGrid.addArrangedSubview(row)
        }
    }

    private func momentGridColumnCount(for count: Int) -> Int {
        switch count {
        case 1:
            return 1
        case 2, 4:
            return 2
        default:
            return 3
        }
    }

    private func momentGridTileSize(for count: Int) -> CGSize {
        switch count {
        case 1:
            return CGSize(width: 156, height: 118)
        case 2:
            return CGSize(width: 84, height: 84)
        default:
            return CGSize(width: 72, height: 72)
        }
    }

    private enum MomentMediaItem {
        case symbol(String)
        case image(URL)
        case video(URL)
    }

    private func makeMomentImageTile(item: MomentMediaItem, size: CGSize) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(red: 0.90, green: 0.93, blue: 0.95, alpha: 1)
        container.clipsToBounds = true
        container.translatesAutoresizingMaskIntoConstraints = false
        container.widthAnchor.constraint(equalToConstant: size.width).isActive = true
        container.heightAnchor.constraint(equalToConstant: size.height).isActive = true

        let image: UIImage?
        switch item {
        case .symbol(let symbol):
            image = UIImage(systemName: symbol)
        case .image(let url):
            image = url.isFileURL
                ? UIImage(contentsOfFile: url.path)
                : (MomentRemoteImageLoader.shared.cachedImage(for: url) ?? UIImage(systemName: "photo"))
        case .video(let url):
            image = MomentVideoThumbnailLoader.shared.cachedThumbnail(for: url)
        }
        let imageView = UIImageView(image: image ?? UIImage(systemName: "play.rectangle.fill"))
        imageView.tintColor = UIColor(red: 0.28, green: 0.38, blue: 0.52, alpha: 0.72)
        imageView.contentMode = {
            if case .image = item { return .scaleAspectFill }
            if case .video = item, image != nil { return .scaleAspectFill }
            return .scaleAspectFit
        }()
        imageView.clipsToBounds = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: container.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        if case .symbol = item {
            imageView.layoutMargins = .zero
            imageView.contentMode = .center
        }
        if case .video = item {
            let playView = UIImageView(image: UIImage(systemName: "play.circle.fill"))
            playView.tintColor = .white
            playView.contentMode = .scaleAspectFit
            playView.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(playView)
            NSLayoutConstraint.activate([
                playView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
                playView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
                playView.widthAnchor.constraint(equalToConstant: size.width == 156 ? 38 : 28),
                playView.heightAnchor.constraint(equalTo: playView.widthAnchor)
            ])
        }
        container.isUserInteractionEnabled = true
        let tap = MomentImageTapGestureRecognizer(target: self, action: #selector(momentImageTapped(_:)))
        tap.previewImage = image
        switch item {
        case .symbol(let symbol):
            tap.previewTitle = symbol
        case .image(let url):
            tap.previewTitle = url.lastPathComponent
            _ = MomentRemoteImageLoader.shared.load(url) { [weak imageView, weak tap] loadedImage in
                guard let loadedImage else { return }
                imageView?.image = loadedImage
                imageView?.tintColor = nil
                tap?.previewImage = loadedImage
            }
        case .video(let url):
            tap.previewTitle = url.lastPathComponent.isEmpty ? "视频" : url.lastPathComponent
            tap.videoURL = url
            if image != nil {
                imageView.tintColor = nil
            }
            _ = MomentVideoThumbnailLoader.shared.load(url) { [weak imageView] thumbnail in
                guard let thumbnail else { return }
                imageView?.image = thumbnail
                imageView?.tintColor = nil
            }
        }
        container.addGestureRecognizer(tap)
        return container
    }

    private static func videoThumbnail(url: URL) -> UIImage? {
        let asset = AVURLAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        guard let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private func configureComments(_ comments: [MomentComment]) {
        commentsStack.arrangedSubviews.forEach { view in
            commentsStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        commentsStack.isHidden = comments.isEmpty
        for (index, comment) in comments.enumerated() {
            let row = MomentCommentRowControl()
            let prefix = comment.replyTo.map { "\(comment.author) 回复 \($0)" } ?? comment.author
            let text = NSMutableAttributedString(
                string: "\(prefix)：",
                attributes: [
                    .font: UIFont.systemFont(ofSize: 13, weight: .semibold),
                    .foregroundColor: UIColor(red: 0.21, green: 0.32, blue: 0.55, alpha: 1)
                ]
            )
            text.append(NSAttributedString(
                string: comment.text,
                attributes: [
                    .font: UIFont.systemFont(ofSize: 13),
                    .foregroundColor: UIColor.label
                ]
            ))
            row.configure(text)
            row.tag = index
            row.addTarget(self, action: #selector(commentLineTapped(_:)), for: .touchUpInside)
            commentsStack.addArrangedSubview(row)
        }
    }

    @objc private func likeTapped() {
        onLike?()
    }

    @objc private func commentTapped() {
        onComment?()
    }

    @objc private func moreTapped() {
        onMore?(moreButton)
    }

    @objc private func linkTapped() {
        guard let linkURL else { return }
        onOpenLink?(linkURL)
    }

    @objc private func commentLineTapped(_ sender: UIControl) {
        onReply?(sender.tag)
    }

    @objc private func momentImageTapped(_ sender: MomentImageTapGestureRecognizer) {
        if let videoURL = sender.videoURL {
            onPreviewVideo?(videoURL)
            return
        }
        guard let image = sender.previewImage else { return }
        onPreviewImage?(image, sender.previewTitle)
    }
}

private final class MomentCommentRowControl: UIControl {
    private let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var isHighlighted: Bool {
        didSet {
            backgroundColor = isHighlighted ? UIColor.black.withAlphaComponent(0.05) : .clear
        }
    }

    func configure(_ text: NSAttributedString) {
        label.attributedText = text
    }

    private func setup() {
        backgroundColor = .clear
        layer.cornerRadius = 3
        layer.cornerCurve = .continuous
        isAccessibilityElement = true
        accessibilityTraits = [.button]

        label.numberOfLines = 0
        label.lineBreakMode = .byWordWrapping
        label.setContentCompressionResistancePriority(.required, for: .vertical)
        label.setContentHuggingPriority(.required, for: .vertical)
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)

        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: topAnchor, constant: 2),
            label.leadingAnchor.constraint(equalTo: leadingAnchor),
            label.trailingAnchor.constraint(equalTo: trailingAnchor),
            label.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -2)
        ])
    }
}

private final class MomentImageTapGestureRecognizer: UITapGestureRecognizer {
    var previewImage: UIImage?
    var previewTitle = "图片预览"
    var videoURL: URL?
}

final class MomentRemoteImageLoader {
    static let shared = MomentRemoteImageLoader()

    private let cache = NSCache<NSURL, UIImage>()
    private let session: URLSession

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 18
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        session = URLSession(configuration: configuration)
        cache.countLimit = 300
    }

    func cachedImage(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    @discardableResult
    func prefetch(_ url: URL) -> URLSessionDataTask? {
        load(url) { _ in }
    }

    func load(_ url: URL, completion: @escaping (UIImage?) -> Void) -> URLSessionDataTask? {
        if url.isFileURL {
            DispatchQueue.global(qos: .userInitiated).async {
                let image = UIImage(contentsOfFile: url.path)
                DispatchQueue.main.async {
                    completion(image)
                }
            }
            return nil
        }

        let key = url as NSURL
        if let image = cache.object(forKey: key) {
            DispatchQueue.main.async {
                completion(image)
            }
            return nil
        }

        let task = session.dataTask(with: url) { [weak self] data, _, _ in
            let image = data.flatMap(UIImage.init(data:))
            if let image {
                self?.cache.setObject(image, forKey: key)
            }
            DispatchQueue.main.async {
                completion(image)
            }
        }
        task.resume()
        return task
    }
}

private final class MomentVideoThumbnailLoader {
    static let shared = MomentVideoThumbnailLoader()

    private let cache = NSCache<NSURL, UIImage>()
    private let queue = DispatchQueue(label: "MomentVideoThumbnailLoader", qos: .utility, attributes: .concurrent)

    private init() {
        cache.countLimit = 120
    }

    func cachedThumbnail(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func prefetch(_ url: URL) {
        load(url) { _ in }
    }

    @discardableResult
    func load(_ url: URL, completion: @escaping (UIImage?) -> Void) -> Bool {
        let key = url as NSURL
        if let cached = cache.object(forKey: key) {
            DispatchQueue.main.async {
                completion(cached)
            }
            return false
        }

        queue.async { [weak self] in
            let asset = AVURLAsset(url: url)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 480, height: 480)
            let time = CMTime(seconds: 0.2, preferredTimescale: 600)
            let image: UIImage?
            if let cgImage = try? generator.copyCGImage(at: time, actualTime: nil) {
                image = UIImage(cgImage: cgImage)
            } else if let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) {
                image = UIImage(cgImage: cgImage)
            } else {
                image = nil
            }
            if let image {
                self?.cache.setObject(image, forKey: key)
            }
            DispatchQueue.main.async {
                completion(image)
            }
        }
        return true
    }
}

private final class MomentComposerViewController: UIViewController {
    private enum MomentVisibility: String, CaseIterable {
        case all = "公开"
        case onlyMe = "私密"
        case selected = "部分可见"
        case excluded = "不给谁看"

        var apiValue: String {
            switch self {
            case .all: return "public"
            case .onlyMe: return "private"
            case .selected: return "partVisible"
            case .excluded: return "notVisible"
            }
        }
    }

    var onPublish: ((MomentPost) -> Void)?

    private let owner: ChatParticipant
    private let accounts: [ChatParticipant]
    private let syncContext: OpenApiMomentSyncContext?
    private let textView = UITextView()
    private let placeholderLabel = UILabel()
    private let mediaScrollView = UIScrollView()
    private let mediaGrid = UIStackView()
    private let locationValueLabel = UILabel()
    private let remindValueLabel = UILabel()
    private let visibilityValueLabel = UILabel()

    private var imageSymbols: [String] = []
    private var selectedImageURLs: [URL] = []
    private var selectedLocation = ""
    private var selectedRemindWxids = Set<String>()
    private var selectedAudienceWxids = Set<String>()
    private var selectedVisibility: MomentVisibility = .all
    private var isEmojiAccessoryExpanded = false
    private weak var emojiAccessoryContainer: UIView?
    private weak var emojiAccessoryStack: UIStackView?
    private let composerEmojis = [
        "🙂", "😊", "😂", "😍", "🥰", "😎", "😭", "😡",
        "👍", "👏", "🙏", "💪", "🎉", "🔥", "✨", "❤️",
        "🌹", "🍀", "📸", "🎵", "☕️", "🍰", "🚗", "🏠"
    ]

    init(owner: ChatParticipant, accounts: [ChatParticipant], syncContext: OpenApiMomentSyncContext?) {
        self.owner = owner
        self.accounts = accounts
        self.syncContext = syncContext
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "取消", style: .plain, target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "发表", style: .done, target: self, action: #selector(publish))
        navigationItem.rightBarButtonItem?.tintColor = UIColor(red: 0.22, green: 0.72, blue: 0.34, alpha: 1)
        configureLayout()
        updateMediaGrid()
        updateComposerMetadata()
        let dismissTap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        dismissTap.cancelsTouchesInView = false
        view.addGestureRecognizer(dismissTap)
    }

    private func configureLayout() {
        let scrollView = UIScrollView()
        scrollView.alwaysBounceVertical = true
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        let contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentView)

        textView.font = .systemFont(ofSize: 18)
        textView.textColor = .label
        textView.backgroundColor = .clear
        textView.delegate = self
        textView.inputAccessoryView = makeEmojiInputAccessoryView()
        textView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(textView)

        placeholderLabel.text = "这一刻的想法..."
        placeholderLabel.font = .systemFont(ofSize: 18)
        placeholderLabel.textColor = UIColor.systemGray3
        placeholderLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(placeholderLabel)

        mediaScrollView.showsHorizontalScrollIndicator = false
        mediaScrollView.alwaysBounceHorizontal = true
        mediaScrollView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(mediaScrollView)

        mediaGrid.axis = .horizontal
        mediaGrid.spacing = 14
        mediaGrid.alignment = .top
        mediaGrid.translatesAutoresizingMaskIntoConstraints = false
        mediaScrollView.addSubview(mediaGrid)

        let divider = UIView()
        divider.backgroundColor = UIColor.separator.withAlphaComponent(0.35)
        divider.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(divider)

        let locationRow = makeComposerRow(symbolName: "location", title: "所在位置", valueLabel: locationValueLabel, action: #selector(selectLocation))
        let remindRow = makeComposerRow(symbolName: "at", title: "提醒谁看", valueLabel: remindValueLabel, action: #selector(selectRemindPeople))
        let visibilityRow = makeComposerRow(symbolName: "person", title: "谁可以看", valueLabel: visibilityValueLabel, action: #selector(selectVisibility))

        [locationRow, remindRow, visibilityRow].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            contentView.addSubview($0)
        }

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),

            textView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 32),
            textView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 36),
            textView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -36),
            textView.heightAnchor.constraint(equalToConstant: 130),

            placeholderLabel.topAnchor.constraint(equalTo: textView.topAnchor, constant: 8),
            placeholderLabel.leadingAnchor.constraint(equalTo: textView.leadingAnchor, constant: 5),
            placeholderLabel.trailingAnchor.constraint(lessThanOrEqualTo: textView.trailingAnchor),

            mediaScrollView.topAnchor.constraint(equalTo: textView.bottomAnchor, constant: 30),
            mediaScrollView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 36),
            mediaScrollView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -36),
            mediaScrollView.heightAnchor.constraint(equalToConstant: 118),

            mediaGrid.topAnchor.constraint(equalTo: mediaScrollView.contentLayoutGuide.topAnchor),
            mediaGrid.leadingAnchor.constraint(equalTo: mediaScrollView.contentLayoutGuide.leadingAnchor),
            mediaGrid.trailingAnchor.constraint(equalTo: mediaScrollView.contentLayoutGuide.trailingAnchor),
            mediaGrid.bottomAnchor.constraint(equalTo: mediaScrollView.contentLayoutGuide.bottomAnchor),
            mediaGrid.heightAnchor.constraint(equalTo: mediaScrollView.frameLayoutGuide.heightAnchor),

            divider.topAnchor.constraint(equalTo: mediaScrollView.bottomAnchor, constant: 34),
            divider.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 36),
            divider.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -36),
            divider.heightAnchor.constraint(equalToConstant: 0.5),

            locationRow.topAnchor.constraint(equalTo: divider.bottomAnchor),
            locationRow.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 36),
            locationRow.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -36),
            locationRow.heightAnchor.constraint(equalToConstant: 66),

            remindRow.topAnchor.constraint(equalTo: locationRow.bottomAnchor),
            remindRow.leadingAnchor.constraint(equalTo: locationRow.leadingAnchor),
            remindRow.trailingAnchor.constraint(equalTo: locationRow.trailingAnchor),
            remindRow.heightAnchor.constraint(equalToConstant: 66),

            visibilityRow.topAnchor.constraint(equalTo: remindRow.bottomAnchor),
            visibilityRow.leadingAnchor.constraint(equalTo: locationRow.leadingAnchor),
            visibilityRow.trailingAnchor.constraint(equalTo: locationRow.trailingAnchor),
            visibilityRow.heightAnchor.constraint(equalToConstant: 66),
            visibilityRow.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -180)
        ])
    }

    private func makeEmojiInputAccessoryView() -> UIView {
        let height: CGFloat = isEmojiAccessoryExpanded ? 132 : 56
        let container = UIView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: height))
        container.backgroundColor = UIColor(white: 0.96, alpha: 1)

        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 8
        stackView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(stackView)
        emojiAccessoryContainer = container
        emojiAccessoryStack = stackView
        rebuildEmojiAccessoryStack()

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
            stackView.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 12),
            stackView.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -12),
            stackView.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -8)
        ])

        return container
    }

    private func rebuildEmojiAccessoryStack() {
        guard let stackView = emojiAccessoryStack else { return }
        stackView.arrangedSubviews.forEach { view in
            stackView.removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        let toolbarRow = UIStackView()
        toolbarRow.axis = .horizontal
        toolbarRow.alignment = .center
        toolbarRow.spacing = 10

        let emojiToggleButton = UIButton(type: .system)
        emojiToggleButton.setImage(UIImage(systemName: isEmojiAccessoryExpanded ? "keyboard" : "face.smiling"), for: .normal)
        emojiToggleButton.tintColor = UIColor(red: 0.18, green: 0.30, blue: 0.52, alpha: 1)
        emojiToggleButton.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        emojiToggleButton.layer.cornerRadius = 8
        emojiToggleButton.addTarget(self, action: #selector(toggleEmojiAccessory), for: .touchUpInside)
        emojiToggleButton.widthAnchor.constraint(equalToConstant: 40).isActive = true
        emojiToggleButton.heightAnchor.constraint(equalToConstant: 40).isActive = true
        toolbarRow.addArrangedSubview(emojiToggleButton)

        let hintLabel = UILabel()
        hintLabel.text = isEmojiAccessoryExpanded ? "选择表情" : "点表情插入"
        hintLabel.font = .systemFont(ofSize: 13)
        hintLabel.textColor = .secondaryLabel
        toolbarRow.addArrangedSubview(hintLabel)

        let spacer = UIView()
        toolbarRow.addArrangedSubview(spacer)
        stackView.addArrangedSubview(toolbarRow)

        guard isEmojiAccessoryExpanded else { return }

        let scrollView = UIScrollView()
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.heightAnchor.constraint(equalToConstant: 68).isActive = true

        let emojiRows = UIStackView()
        emojiRows.axis = .vertical
        emojiRows.spacing = 6
        emojiRows.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(emojiRows)

        let columns = Int(ceil(Double(composerEmojis.count) / 2.0))
        for rowIndex in 0..<2 {
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 8
            row.alignment = .center
            for column in 0..<columns {
                let index = column * 2 + rowIndex
                guard composerEmojis.indices.contains(index) else { continue }
                let button = UIButton(type: .system)
                button.setTitle(composerEmojis[index], for: .normal)
                button.titleLabel?.font = .systemFont(ofSize: 24)
                button.backgroundColor = UIColor.white.withAlphaComponent(0.92)
                button.layer.cornerRadius = 8
                button.tag = index
                button.addTarget(self, action: #selector(insertEmoji(_:)), for: .touchUpInside)
                button.widthAnchor.constraint(equalToConstant: 38).isActive = true
                button.heightAnchor.constraint(equalToConstant: 30).isActive = true
                row.addArrangedSubview(button)
            }
            emojiRows.addArrangedSubview(row)
        }

        NSLayoutConstraint.activate([
            emojiRows.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            emojiRows.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            emojiRows.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            emojiRows.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            emojiRows.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
        ])

        stackView.addArrangedSubview(scrollView)
    }

    private func makeComposerRow(symbolName: String, title: String, valueLabel: UILabel, action: Selector) -> UIControl {
        let control = UIControl()
        control.addTarget(self, action: action, for: .touchUpInside)

        let iconView = UIImageView(image: UIImage(systemName: symbolName))
        iconView.tintColor = .label
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        control.addSubview(iconView)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 19)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        control.addSubview(titleLabel)

        valueLabel.font = .systemFont(ofSize: 18)
        valueLabel.textColor = .secondaryLabel
        valueLabel.textAlignment = .right
        valueLabel.translatesAutoresizingMaskIntoConstraints = false
        control.addSubview(valueLabel)

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = UIColor.systemGray4
        chevron.contentMode = .scaleAspectFit
        chevron.translatesAutoresizingMaskIntoConstraints = false
        control.addSubview(chevron)

        let bottomLine = UIView()
        bottomLine.backgroundColor = UIColor.separator.withAlphaComponent(0.35)
        bottomLine.translatesAutoresizingMaskIntoConstraints = false
        control.addSubview(bottomLine)

        NSLayoutConstraint.activate([
            iconView.leadingAnchor.constraint(equalTo: control.leadingAnchor, constant: 2),
            iconView.centerYAnchor.constraint(equalTo: control.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 28),
            iconView.heightAnchor.constraint(equalToConstant: 28),

            titleLabel.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 28),
            titleLabel.centerYAnchor.constraint(equalTo: control.centerYAnchor),

            chevron.trailingAnchor.constraint(equalTo: control.trailingAnchor, constant: -2),
            chevron.centerYAnchor.constraint(equalTo: control.centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 14),
            chevron.heightAnchor.constraint(equalToConstant: 20),

            valueLabel.leadingAnchor.constraint(greaterThanOrEqualTo: titleLabel.trailingAnchor, constant: 12),
            valueLabel.trailingAnchor.constraint(equalTo: chevron.leadingAnchor, constant: -12),
            valueLabel.centerYAnchor.constraint(equalTo: control.centerYAnchor),

            bottomLine.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            bottomLine.trailingAnchor.constraint(equalTo: control.trailingAnchor),
            bottomLine.bottomAnchor.constraint(equalTo: control.bottomAnchor),
            bottomLine.heightAnchor.constraint(equalToConstant: 0.5)
        ])

        return control
    }

    private func updateMediaGrid() {
        mediaGrid.arrangedSubviews.forEach { view in
            mediaGrid.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        for (index, url) in selectedImageURLs.prefix(9).enumerated() {
            mediaGrid.addArrangedSubview(makeMediaPreview(url: url, index: index))
        }
        for symbol in imageSymbols.prefix(max(0, 9 - selectedImageURLs.count)) {
            mediaGrid.addArrangedSubview(makeSymbolMediaPreview(symbolName: symbol))
        }
        if selectedImageURLs.count + imageSymbols.count < 9 {
            mediaGrid.addArrangedSubview(makeAddMediaButton())
        }
    }

    private func makeMediaPreview(url: URL, index: Int) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(white: 0.95, alpha: 1)
        container.clipsToBounds = true
        container.translatesAutoresizingMaskIntoConstraints = false
        container.widthAnchor.constraint(equalToConstant: 118).isActive = true
        container.heightAnchor.constraint(equalToConstant: 118).isActive = true

        let imageView = UIImageView(image: UIImage(contentsOfFile: url.path))
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(imageView)

        let deleteButton = UIButton(type: .system)
        deleteButton.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        deleteButton.tintColor = UIColor.black.withAlphaComponent(0.62)
        deleteButton.backgroundColor = UIColor.white.withAlphaComponent(0.72)
        deleteButton.layer.cornerRadius = 11
        deleteButton.tag = index
        deleteButton.addTarget(self, action: #selector(removeSelectedImage(_:)), for: .touchUpInside)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(deleteButton)

        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: container.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor),

            deleteButton.topAnchor.constraint(equalTo: container.topAnchor, constant: 5),
            deleteButton.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -5),
            deleteButton.widthAnchor.constraint(equalToConstant: 22),
            deleteButton.heightAnchor.constraint(equalToConstant: 22)
        ])
        return container
    }

    private func makeSymbolMediaPreview(symbolName: String) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(white: 0.95, alpha: 1)
        container.translatesAutoresizingMaskIntoConstraints = false
        container.widthAnchor.constraint(equalToConstant: 118).isActive = true
        container.heightAnchor.constraint(equalToConstant: 118).isActive = true

        let imageView = UIImageView(image: UIImage(systemName: symbolName))
        imageView.tintColor = UIColor(red: 0.30, green: 0.35, blue: 0.40, alpha: 0.82)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            imageView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            imageView.widthAnchor.constraint(equalToConstant: 42),
            imageView.heightAnchor.constraint(equalToConstant: 42)
        ])
        return container
    }

    private func makeAddMediaButton() -> UIButton {
        let button = UIButton(type: .system)
        button.backgroundColor = UIColor(white: 0.96, alpha: 1)
        button.setImage(UIImage(systemName: "plus"), for: .normal)
        button.tintColor = UIColor.systemGray
        button.addTarget(self, action: #selector(addMedia), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.widthAnchor.constraint(equalToConstant: 118).isActive = true
        button.heightAnchor.constraint(equalToConstant: 118).isActive = true
        button.imageView?.contentMode = .scaleAspectFit
        return button
    }

    private func updateComposerMetadata() {
        locationValueLabel.text = selectedLocation
        remindValueLabel.text = selectedRemindWxids.isEmpty ? "" : "已选择 \(selectedRemindWxids.count) 人"
        if selectedVisibility == .selected || selectedVisibility == .excluded {
            visibilityValueLabel.text = selectedAudienceWxids.isEmpty
                ? selectedVisibility.rawValue
                : "\(selectedVisibility.rawValue) · \(selectedAudienceWxids.count) 人"
        } else {
            visibilityValueLabel.text = selectedVisibility.rawValue
        }
    }

    @objc private func addMedia() {
        guard selectedImageURLs.count + imageSymbols.count < 9 else { return }
        let alert = UIAlertController(title: "添加图片", message: nil, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "从相册选择", style: .default) { [weak self] _ in
            self?.presentPhotoPicker()
        })
        alert.addAction(UIAlertAction(title: "拍照", style: .default) { [weak self] _ in
            self?.presentCamera()
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentActionSheet(alert)
    }

    @objc private func toggleEmojiAccessory() {
        isEmojiAccessoryExpanded.toggle()
        textView.inputAccessoryView = makeEmojiInputAccessoryView()
        textView.reloadInputViews()
    }

    @objc private func insertEmoji(_ sender: UIButton) {
        guard composerEmojis.indices.contains(sender.tag) else { return }
        let emoji = composerEmojis[sender.tag]
        if let range = textView.selectedTextRange {
            textView.replace(range, withText: emoji)
        } else {
            textView.text.append(emoji)
        }
        placeholderLabel.isHidden = !textView.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    @objc private func removeSelectedImage(_ sender: UIButton) {
        guard selectedImageURLs.indices.contains(sender.tag) else { return }
        selectedImageURLs.remove(at: sender.tag)
        updateMediaGrid()
    }

    private func presentPhotoPicker() {
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = max(1, 9 - selectedImageURLs.count - imageSymbols.count)
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        present(picker, animated: true)
    }

    private func presentCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            showComposerAlert(title: "无法打开相机", message: "当前设备不支持拍照。")
            return
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = self
        present(picker, animated: true)
    }

    private func appendSelectedImage(_ image: UIImage) {
        guard selectedImageURLs.count + imageSymbols.count < 9,
              let url = saveMomentImageToTemporaryFile(image) else { return }
        selectedImageURLs.append(url)
        updateMediaGrid()
    }

    private func saveMomentImageToTemporaryFile(_ image: UIImage) -> URL? {
        guard let data = image.jpegData(compressionQuality: 0.88) else { return nil }
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("jpg")
        do {
            try data.write(to: destination, options: .atomic)
            return destination
        } catch {
            return nil
        }
    }

    private func showComposerAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }

    @objc private func selectLocation() {
        let alert = UIAlertController(title: "所在位置", message: nil, preferredStyle: .actionSheet)
        ["不显示位置", "公司", "活动现场", "上海", "附近商圈"].forEach { value in
            alert.addAction(UIAlertAction(title: value, style: .default) { [weak self] _ in
                self?.selectedLocation = value == "不显示位置" ? "" : value
                self?.updateComposerMetadata()
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentActionSheet(alert)
    }

    @objc private func selectRemindPeople() {
        presentFriendPicker(title: "提醒谁看", selected: selectedRemindWxids) { [weak self] selected in
            self?.selectedRemindWxids = selected
            self?.updateComposerMetadata()
        }
    }

    @objc private func selectVisibility() {
        let alert = UIAlertController(title: "谁可以看", message: nil, preferredStyle: .actionSheet)
        MomentVisibility.allCases.forEach { visibility in
            alert.addAction(UIAlertAction(title: visibility.rawValue, style: .default) { [weak self] _ in
                guard let self else { return }
                self.selectedVisibility = visibility
                if visibility == .selected || visibility == .excluded {
                    self.presentFriendPicker(title: visibility.rawValue, selected: self.selectedAudienceWxids) { [weak self] selected in
                        self?.selectedAudienceWxids = selected
                        self?.updateComposerMetadata()
                    }
                } else {
                    self.selectedAudienceWxids.removeAll()
                    self.updateComposerMetadata()
                }
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentActionSheet(alert)
    }

    private func presentFriendPicker(title: String, selected: Set<String>, completion: @escaping (Set<String>) -> Void) {
        guard let syncContext else {
            showComposerAlert(title: "暂无联系人", message: "当前帐号还没有同步好友资料。")
            return
        }
        let contacts = syncContext.friendDisplayNames
            .map { MomentAudienceContact(wxid: $0.key, name: $0.value) }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        guard !contacts.isEmpty else {
            showComposerAlert(title: "暂无联系人", message: "请先同步当前帐号的好友列表。")
            return
        }
        let controller = MomentAudiencePickerViewController(titleText: title, contacts: contacts, selectedWxids: selected)
        controller.onConfirm = { [weak self] values in
            completion(values)
            self?.navigationController?.popViewController(animated: true)
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    private func presentActionSheet(_ alert: UIAlertController) {
        if let popover = alert.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 1, width: 1, height: 1)
        }
        present(alert, animated: true)
    }

    @objc private func publish() {
        let text = textView.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || !selectedImageURLs.isEmpty || !imageSymbols.isEmpty else {
            showComposerAlert(title: "无法发表", message: "请输入文字或选择图片。")
            return
        }
        if let syncContext {
            publishToOpenApi(text: text, syncContext: syncContext)
            return
        }
        let post = MomentPost(
            id: UUID(),
            author: owner,
            text: text.isEmpty ? "分享一条新的模拟朋友圈动态。" : text,
            timeText: "刚刚",
            linkTitle: nil,
            linkURL: nil,
            imageSymbols: imageSymbols,
            imageURLs: selectedImageURLs,
            videoURLs: [],
            locationText: selectedLocation.isEmpty ? nil : selectedLocation,
            visibilityText: selectedVisibility.rawValue,
            remindNames: selectedRemindWxids.compactMap { syncContext?.friendDisplayNames[$0] },
            likes: [],
            comments: []
        )
        onPublish?(post)
        navigationController?.popViewController(animated: true)
    }

    private func publishToOpenApi(text: String, syncContext: OpenApiMomentSyncContext) {
        navigationItem.rightBarButtonItem?.isEnabled = false
        showCircularPageLoading(title: selectedImageURLs.isEmpty ? "发布朋友圈" : "上传并发布")
        Task { [weak self] in
            guard let self else { return }
            do {
                let taskID = try await OpenApiMomentAPI.publishMoment(
                    context: syncContext,
                    content: text,
                    imageURLs: self.selectedImageURLs,
                    location: self.selectedLocation.isEmpty ? nil : self.selectedLocation,
                    visibleType: self.selectedVisibility.apiValue,
                    friendWxids: self.selectedVisibility == .selected ? Array(self.selectedAudienceWxids) : [],
                    invisibleFriendWxids: self.selectedVisibility == .excluded ? Array(self.selectedAudienceWxids) : [],
                    notiUsers: Array(self.selectedRemindWxids)
                )
                let post = MomentPost(
                    id: UUID(),
                    author: self.owner,
                    text: text.isEmpty ? "分享图片" : text,
                    timeText: "刚刚",
                    linkTitle: nil,
                    linkURL: nil,
                    imageSymbols: self.imageSymbols,
                    imageURLs: self.selectedImageURLs,
                    videoURLs: [],
                    locationText: self.selectedLocation.isEmpty ? nil : self.selectedLocation,
                    visibilityText: self.selectedVisibility.rawValue,
                    remindNames: self.selectedRemindWxids.compactMap { syncContext.friendDisplayNames[$0] },
                    likes: [],
                    comments: []
                )
                await MainActor.run {
                    self.navigationItem.rightBarButtonItem?.isEnabled = true
                    self.onPublish?(post)
                    _ = taskID
                    self.navigationController?.popViewController(animated: true)
                }
            } catch {
                await MainActor.run {
                    self.navigationItem.rightBarButtonItem?.isEnabled = true
                    self.showComposerAlert(title: "发布失败", message: error.localizedDescription)
                }
            }
        }
    }

    @objc private func cancel() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func dismissKeyboard() {
        view.endEditing(true)
    }
}

extension MomentComposerViewController: UITextViewDelegate {
    func textViewDidChange(_ textView: UITextView) {
        placeholderLabel.isHidden = !textView.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

extension MomentComposerViewController: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard !results.isEmpty else { return }
        for result in results {
            guard selectedImageURLs.count + imageSymbols.count < 9 else { break }
            let provider = result.itemProvider
            guard provider.canLoadObject(ofClass: UIImage.self) else { continue }
            provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
                guard let self, let image = object as? UIImage else { return }
                DispatchQueue.main.async {
                    self.appendSelectedImage(image)
                }
            }
        }
    }
}

extension MomentComposerViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        let image = (info[.editedImage] ?? info[.originalImage]) as? UIImage
        picker.dismiss(animated: true) { [weak self] in
            guard let self, let image else { return }
            self.appendSelectedImage(image)
        }
    }
}

private final class MomentAvatarView: UIView {
    private let imageView = UIImageView()
    private let label = UILabel()
    private var loadTask: URLSessionDataTask?
    private var representedURL: URL?

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(imageView)
        label.textColor = .white
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor),
            label.topAnchor.constraint(equalTo: topAnchor),
            label.leadingAnchor.constraint(equalTo: leadingAnchor),
            label.trailingAnchor.constraint(equalTo: trailingAnchor),
            label.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = min(bounds.width, bounds.height) * 0.18
        layer.cornerCurve = .continuous
    }

    func configure(name: String, initials: String, color: UIColor, avatarURL: URL?, size: CGFloat, fontSize: CGFloat) {
        loadTask?.cancel()
        loadTask = nil
        representedURL = avatarURL
        backgroundColor = color
        imageView.image = nil
        imageView.isHidden = true
        label.isHidden = false
        label.text = initials.isEmpty ? String(name.prefix(1)) : initials
        label.font = .systemFont(ofSize: fontSize, weight: .semibold)
        layer.cornerRadius = size * 0.18
        guard let avatarURL else { return }
        loadTask = MomentRemoteImageLoader.shared.load(avatarURL) { [weak self] image in
            guard let self,
                  self.representedURL == avatarURL,
                  let image
            else { return }
            self.imageView.image = image
            self.imageView.isHidden = false
            self.label.isHidden = true
        }
    }
}

extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}


private struct MomentAudienceContact {
    let wxid: String
    let name: String
}

private final class MomentAudiencePickerViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UISearchBarDelegate {
    var onConfirm: ((Set<String>) -> Void)?

    private let contacts: [MomentAudienceContact]
    private var filtered: [MomentAudienceContact]
    private var selectedWxids: Set<String>
    private let searchBar = UISearchBar()
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    init(titleText: String, contacts: [MomentAudienceContact], selectedWxids: Set<String>) {
        self.contacts = contacts
        self.filtered = contacts
        self.selectedWxids = selectedWxids
        super.init(nibName: nil, bundle: nil)
        title = titleText
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "完成", style: .done, target: self, action: #selector(confirm))
        searchBar.placeholder = "搜索好友"
        searchBar.searchBarStyle = .minimal
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.rowHeight = 58
        tableView.keyboardDismissMode = .onDrag
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchBar)
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 4),
            searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            tableView.topAnchor.constraint(equalTo: searchBar.bottomAnchor, constant: 2),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        updateConfirmTitle()
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { filtered.count }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "MomentAudienceCell") ?? UITableViewCell(style: .subtitle, reuseIdentifier: "MomentAudienceCell")
        let contact = filtered[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.text = contact.name.isEmpty ? contact.wxid : contact.name
        configuration.secondaryText = contact.wxid
        configuration.image = UIImage.sidebarPlaceholderAvatar(title: configuration.text ?? "友", color: OpenApiDisplay.color(for: contact.wxid), size: CGSize(width: 38, height: 38))
        configuration.imageProperties.maximumSize = CGSize(width: 38, height: 38)
        cell.contentConfiguration = configuration
        cell.accessoryType = selectedWxids.contains(contact.wxid) ? .checkmark : .none
        cell.tintColor = .systemGreen
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let wxid = filtered[indexPath.row].wxid
        if selectedWxids.contains(wxid) { selectedWxids.remove(wxid) } else { selectedWxids.insert(wxid) }
        tableView.reloadRows(at: [indexPath], with: .none)
        updateConfirmTitle()
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        filtered = query.isEmpty ? contacts : contacts.filter { "\($0.name) \($0.wxid)".lowercased().contains(query) }
        tableView.reloadData()
    }

    private func updateConfirmTitle() {
        navigationItem.rightBarButtonItem?.title = selectedWxids.isEmpty ? "完成" : "完成(\(selectedWxids.count))"
    }

    @objc private func confirm() { onConfirm?(selectedWxids) }
}

struct OpenApiMomentMaterial: Codable, Hashable {
    let id: Int64
    let tenantId: String
    let name: String
    let category: String
    let scene: String
    let content: String
    let status: Int
    let statusName: String
    let attachmentType: Int
    let attachmentCount: Int
    let extCommentCount: Int
    let sendSlow: Bool
    let mediaTitles: [String]
    let tags: [String]
    let usageCount: Int
    let updatedAt: String
    let contentHash: String
    let attachmentHash: String
    let commentHash: String
    let createdBy: String
    let updatedBy: String
}

struct OpenApiMomentBatchPlan: Codable, Hashable {
    let id: Int64
    let tenantId: String
    let name: String
    let materialId: Int64
    let scheduleModeName: String
    let targetModeName: String
    let status: Int
    let statusName: String
    let itemCount: Int
    let pendingCount: Int
    let postedCount: Int
    let failedCount: Int
    let progressPercent: Double
    let progressText: String
    let updatedAt: String
}

struct OpenApiMomentBatchPreview {
    let success: Bool
    let message: String
    let previewSignature: String
    let itemCount: Int
    let warningCount: Int
    let errorCount: Int
}

enum OpenApiMomentAPI {
    static func listMaterials(tenantId: String, skip: Int = 0, take: Int = 50) async throws -> [OpenApiMomentMaterial] {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/moments/materials",
            query: [
                URLQueryItem(name: "tenantId", value: tenantId),
                URLQueryItem(name: "skip", value: "\(skip)"),
                URLQueryItem(name: "take", value: "\(take)")
            ]
        )
        return arrayPayload(from: result.jsonObject).map { parseMaterial(from: $0, fallback: nil, tenantId: tenantId) }
    }

    static func getMaterial(id: Int64, tenantId: String) async throws -> OpenApiMomentMaterial {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/moments/materials/\(id)",
            query: [URLQueryItem(name: "tenantId", value: tenantId)]
        )
        return parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: nil, tenantId: tenantId)
    }

    static func getMaterialDetail(id: Int64, tenantId: String) async throws -> OpenApiMomentMaterial {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/moments/materials/\(id)/detail",
            query: [URLQueryItem(name: "tenantId", value: tenantId)]
        )
        return parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: nil, tenantId: tenantId)
    }

    static func createMaterial(_ material: OpenApiMomentMaterial, account: OpenApiWeChatAccountContext?) async throws -> OpenApiMomentMaterial {
        let body = materialRequestBody(material, account: account, includeTenant: true)
        let result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/moments/materials", body: body)
        let created = parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: body, tenantId: material.tenantId)
        if created.id > 0 {
            return try await getMaterialDetail(id: created.id, tenantId: created.tenantId)
        }
        return created
    }

    static func updateMaterial(_ material: OpenApiMomentMaterial, account: OpenApiWeChatAccountContext?) async throws -> OpenApiMomentMaterial {
        let body = materialRequestBody(material, account: account, includeTenant: true)
        let result = try await OpenApiHTTPClient.request("PUT", path: "/openapi/v1/moments/materials/\(material.id)", body: body)
        return parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: body, tenantId: material.tenantId)
    }

    static func copyMaterial(_ material: OpenApiMomentMaterial) async throws -> OpenApiMomentMaterial {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/materials/\(material.id)/copy",
            body: [
                "name": "\(material.name) 副本",
                "enableImmediately": false
            ]
        )
        return parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: nil, tenantId: material.tenantId)
    }

    static func archiveMaterial(_ material: OpenApiMomentMaterial) async throws -> OpenApiMomentMaterial {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/materials/\(material.id)/archive",
            body: ["reason": "iOS 素材库归档"]
        )
        return parseMaterial(from: dictionaryPayload(from: result.jsonObject), fallback: nil, tenantId: material.tenantId)
    }

    static func buildTemplate(_ material: OpenApiMomentMaterial, account: OpenApiWeChatAccountContext?) async throws -> OpenApiHTTPResult {
        try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/template",
            body: materialRequestBody(material, account: account, includeTenant: false)
                .merging(["includePostRequest": true, "validateVisibleLabels": false]) { _, new in new }
        )
    }

    static func publishMaterial(_ material: OpenApiMomentMaterial, account: OpenApiWeChatAccountContext) async throws -> String {
        let body = materialRequestBody(material, account: account, includeTenant: false)
            .merging([
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "clientRequestId": UUID().uuidString
            ]) { _, new in new }
        let result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/moments", body: body)
        return try await OpenApiHTTPClient.validateTaskResult(result, action: "发布朋友圈")
    }

    static func publishMoment(
        context: OpenApiMomentSyncContext,
        content: String,
        imageURLs: [URL],
        location: String?,
        visibleType: String,
        friendWxids: [String],
        invisibleFriendWxids: [String],
        notiUsers: [String]
    ) async throws -> String {
        var uploadedURLs: [String] = []
        for imageURL in imageURLs {
            let uploadResult = try await OpenApiHTTPClient.uploadMediaFile(imageURL)
            guard let uploaded = uploadedURLString(from: uploadResult.jsonObject) else {
                throw OpenApiMediaSendError.missingUploadedURL("朋友圈图片")
            }
            uploadedURLs.append(uploaded)
        }

        let clientRequestId = UUID().uuidString
        let attachmentType = uploadedURLs.isEmpty ? "text" : "image"
        let payloadEnums = momentPayloadEnumValues(hasImages: !uploadedURLs.isEmpty, visibleType: visibleType)
        let payloadAttachmentType = payloadEnums.attachmentType
        let payloadVisibleType = payloadEnums.visibleType
        var visiblePayload: [String: Any] = ["type": payloadVisibleType]
        var body: [String: Any] = [
            "weChatId": context.weChatId,
            "clientRequestId": clientRequestId,
            "content": content,
            "attachmentType": attachmentType,
            "sendSlow": true,
            "visibleType": visibleType
        ]
        if !context.deviceUuid.isEmpty {
            body["deviceUuid"] = context.deviceUuid
        }
        if !uploadedURLs.isEmpty {
            body["attachments"] = uploadedURLs
        }
        if let location, !location.isEmpty {
            body["poi"] = [
                "name": location,
                "city": location,
                "address": location,
                "lat": 0.0,
                "lng": 0.0,
                "poiId": ""
            ]
        }
        if !notiUsers.isEmpty {
            body["notiUsers"] = notiUsers
        }
        if !friendWxids.isEmpty {
            body["friendWxids"] = friendWxids
            visiblePayload["friends"] = friendWxids
        }
        if !invisibleFriendWxids.isEmpty {
            body["invisibleFriendWxids"] = invisibleFriendWxids
            visiblePayload["friends"] = invisibleFriendWxids
        }
        var payload: [String: Any] = [
            "clientRequestId": clientRequestId,
            "weChatId": context.weChatId,
            "content": content,
            "attachment": [
                "type": payloadAttachmentType,
                "content": uploadedURLs
            ],
            "sendSlow": true,
            "visible": visiblePayload
        ]
        if !notiUsers.isEmpty {
            payload["notiUsers"] = notiUsers
        }
        if let poi = body["poi"] {
            payload["poi"] = poi
        }
        body["payload"] = payload
        let result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/moments", body: body)
        return try await OpenApiHTTPClient.validateTaskResult(result, action: "发布朋友圈")
    }

    static func momentPayloadEnumValues(hasImages: Bool, visibleType: String) -> (attachmentType: Int, visibleType: Int) {
        let attachmentType = hasImages ? 2 : 0
        let visibility: Int
        switch visibleType {
        case "private": visibility = 1
        case "partVisible": visibility = 2
        case "notVisible": visibility = 3
        default: visibility = 0
        }
        return (attachmentType, visibility)
    }

    static func deleteComment(
        context: OpenApiMomentSyncContext,
        circleId: Int64,
        commentId: Int64,
        publishTime: Int64?
    ) async throws -> String {
        var body: [String: Any] = [
            "deviceUuid": context.deviceUuid,
            "weChatId": context.weChatId,
            "circleId": circleId,
            "commentId": commentId
        ]
        if let publishTime, publishTime > 0 {
            body["publishTime"] = publishTime
        }
        let result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/moments/comments/delete", body: body)
        return try await OpenApiHTTPClient.validateTaskResult(result, action: "删除朋友圈评论")
    }

    static func listPlans(tenantId: String, skip: Int = 0, take: Int = 50) async throws -> [OpenApiMomentBatchPlan] {
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/moments/batch/plans",
            query: [
                URLQueryItem(name: "tenantId", value: tenantId),
                URLQueryItem(name: "skip", value: "\(skip)"),
                URLQueryItem(name: "take", value: "\(take)")
            ]
        )
        return arrayPayload(from: result.jsonObject).map { parsePlan(from: $0, tenantId: tenantId) }
    }

    static func getPlan(id: Int64) async throws -> OpenApiMomentBatchPlan {
        let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/moments/batch/plans/\(id)")
        return parsePlan(from: dictionaryPayload(from: result.jsonObject), tenantId: "")
    }

    static func renderBatchPreview(
        materialId: Int64,
        tenantId: String,
        account: OpenApiWeChatAccountContext?,
        name: String
    ) async throws -> OpenApiMomentBatchPreview {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/batch/render-preview",
            body: batchPlanBody(materialId: materialId, tenantId: tenantId, account: account, name: name, previewSignature: nil)
        )
        let raw = dictionaryPayload(from: result.jsonObject)
        return OpenApiMomentBatchPreview(
            success: bool(raw["success"]),
            message: firstCleanedValue([raw["message"]]),
            previewSignature: firstCleanedValue([raw["previewSignature"]]),
            itemCount: int(raw["itemCount"]),
            warningCount: int(raw["warningCount"]),
            errorCount: int(raw["errorCount"])
        )
    }

    static func createBatchPlan(
        materialId: Int64,
        tenantId: String,
        account: OpenApiWeChatAccountContext?,
        name: String,
        previewSignature: String
    ) async throws -> OpenApiMomentBatchPlan {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/batch/plans",
            body: batchPlanBody(materialId: materialId, tenantId: tenantId, account: account, name: name, previewSignature: previewSignature)
        )
        let raw = dictionaryPayload(from: result.jsonObject)
        let planRaw = (raw["plan"] as? [String: Any]) ?? raw
        return parsePlan(from: planRaw, tenantId: tenantId)
    }

    static func controlPlan(_ plan: OpenApiMomentBatchPlan, action: String, reason: String) async throws -> OpenApiMomentBatchPlan {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/moments/batch/plans/\(plan.id)/\(action)",
            body: ["reason": reason]
        )
        return parsePlan(from: dictionaryPayload(from: result.jsonObject), tenantId: plan.tenantId)
    }

    static func materialRequestBody(
        _ material: OpenApiMomentMaterial,
        account: OpenApiWeChatAccountContext?,
        includeTenant: Bool
    ) -> [String: Any] {
        let attachments = material.mediaTitles.map(cleaned).filter { !$0.isEmpty && looksLikeAttachmentValue($0) }
        let attachmentTypeText = attachmentTypeString(material.attachmentType)
        var body: [String: Any] = [
            "clientRequestId": UUID().uuidString,
            "name": material.name,
            "category": material.category,
            "content": material.content,
            "attachmentType": attachmentTypeText,
            "sendSlow": material.sendSlow,
            "visibleType": "public",
            "enableImmediately": material.status == 1,
            "antiFoldStrategyJson": "{}",
            "variableSchemaJson": material.tags.isEmpty ? "{}" : jsonString(["tags": material.tags])
        ]
        if includeTenant {
            body["tenantId"] = material.tenantId
        }
        if let account {
            body["weChatId"] = account.wxid
            body["deviceUuid"] = account.clientUuid
        }
        if !attachments.isEmpty {
            body["attachments"] = attachments
        }
        if material.attachmentType == 3, let link = attachments.first {
            body["linkUrl"] = link
            body["linkTitle"] = material.name
            body["linkDescription"] = material.content
            body["linkSourceName"] = material.scene.isEmpty ? "只发" : material.scene
        }
        return body
    }

    static func parseMaterial(from raw: [String: Any], fallback: [String: Any]?, tenantId: String) -> OpenApiMomentMaterial {
        let template = raw["template"] as? [String: Any]
        let payload = raw["payload"] as? [String: Any]
        let source = raw.merging(template ?? [:]) { current, _ in current }
            .merging(payload ?? [:]) { current, _ in current }
        let fallback = fallback ?? [:]
        let id = int64(firstExisting([raw["id"], fallback["id"]]))
        let resolvedTenantId = firstCleanedValue([raw["tenantId"], fallback["tenantId"], tenantId])
        let attachmentType = attachmentTypeInt(firstCleanedValue([
            raw["attachmentType"], fallback["attachmentType"], source["attachmentType"], (source["attachment"] as? [String: Any])?["type"]
        ]), fallback: int(firstExisting([raw["attachmentType"], fallback["attachmentType"]])))
        let attachments = attachmentStrings(from: source)
            + attachmentStrings(from: raw)
            + attachmentStrings(from: fallback)
        let linkTitle = firstCleanedValue([source["linkTitle"], source["weAppTitle"], raw["linkTitle"], raw["weAppTitle"]])
        let mediaTitles = uniqueStrings(
            attachments.isEmpty
                ? [linkTitle, firstCleanedValue([source["linkUrl"], source["weAppUrl"]])].filter { !$0.isEmpty }
                : attachments
        )
        let attachmentCount = max(int(firstExisting([raw["attachmentCount"], fallback["attachmentCount"]])), mediaTitles.count)
        let status = int(firstExisting([raw["status"], fallback["status"]]))
        let statusName = firstCleanedValue([raw["statusName"], fallback["statusName"], status == 1 ? "启用" : "草稿"])
        let category = firstCleanedValue([raw["category"], fallback["category"], categoryName(for: attachmentType)])
        return OpenApiMomentMaterial(
            id: id,
            tenantId: resolvedTenantId,
            name: firstCleanedValue([raw["name"], fallback["name"], linkTitle, "朋友圈素材"]),
            category: category,
            scene: firstCleanedValue([raw["scene"], fallback["scene"], categoryName(for: attachmentType)]),
            content: firstCleanedValue([source["content"], raw["content"], fallback["content"], raw["contentHash"], ""]),
            status: status,
            statusName: statusName,
            attachmentType: attachmentType,
            attachmentCount: attachmentCount,
            extCommentCount: int(firstExisting([raw["extCommentCount"], fallback["extCommentCount"]])),
            sendSlow: bool(firstExisting([raw["sendSlow"], fallback["sendSlow"]])),
            mediaTitles: mediaTitles,
            tags: uniqueStrings([category, categoryName(for: attachmentType), statusName]),
            usageCount: int(firstExisting([raw["usageCount"], raw["usedCount"], fallback["usageCount"]])),
            updatedAt: displayDateText(firstExisting([raw["updatedAt"], raw["createdAt"], fallback["updatedAt"], fallback["createdAt"]])),
            contentHash: firstCleanedValue([raw["contentHash"], fallback["contentHash"]]),
            attachmentHash: firstCleanedValue([raw["attachmentHash"], fallback["attachmentHash"]]),
            commentHash: firstCleanedValue([raw["commentHash"], fallback["commentHash"]]),
            createdBy: firstCleanedValue([raw["createdBy"], fallback["createdBy"]]),
            updatedBy: firstCleanedValue([raw["updatedBy"], fallback["updatedBy"]])
        )
    }

    private static func batchPlanBody(
        materialId: Int64,
        tenantId: String,
        account: OpenApiWeChatAccountContext?,
        name: String,
        previewSignature: String?
    ) -> [String: Any] {
        var body: [String: Any] = [
            "tenantId": tenantId,
            "name": name,
            "materialId": materialId,
            "scheduleMode": 0,
            "targetMode": account == nil ? 0 : 1,
            "intervalMinSeconds": 30,
            "intervalMaxSeconds": 90,
            "scheduleImmediately": true,
            "priority": 0,
            "variableSourceJson": "{}"
        ]
        if let account {
            body["targets"] = [[
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "displayName": account.nickname
            ]]
        } else {
            body["targets"] = []
        }
        if let previewSignature, !previewSignature.isEmpty {
            body["previewSignature"] = previewSignature
        }
        return body
    }

    private static func parsePlan(from raw: [String: Any], tenantId: String) -> OpenApiMomentBatchPlan {
        OpenApiMomentBatchPlan(
            id: int64(raw["id"]),
            tenantId: firstCleanedValue([raw["tenantId"], tenantId]),
            name: firstCleanedValue([raw["name"], "朋友圈批量计划"]),
            materialId: int64(raw["materialId"]),
            scheduleModeName: firstCleanedValue([raw["scheduleModeName"], "立即"]),
            targetModeName: firstCleanedValue([raw["targetModeName"], "按目标"]),
            status: int(raw["status"]),
            statusName: firstCleanedValue([raw["statusName"], "未知"]),
            itemCount: int(raw["itemCount"]),
            pendingCount: int(raw["pendingCount"]),
            postedCount: int(raw["postedCount"]),
            failedCount: int(raw["failedCount"]),
            progressPercent: double(raw["progressPercent"]),
            progressText: firstCleanedValue([raw["progressText"]]),
            updatedAt: displayDateText(firstExisting([raw["updatedAt"], raw["createdAt"]]))
        )
    }

    private static func uploadedURLString(from object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            for key in ["url", "fileUrl", "fileURL", "mediaUrl", "mediaURL", "downloadUrl", "cdnUrl", "path"] {
                let value = cleaned(string(dictionary[key]))
                if OpenApiDisplay.url(from: value) != nil {
                    return value
                }
            }
            for key in ["data", "result", "payload", "item"] {
                if let nested = uploadedURLString(from: dictionary[key]) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let nested = uploadedURLString(from: item) {
                    return nested
                }
            }
        }
        return nil
    }

    private static func attachmentStrings(from raw: [String: Any]) -> [String] {
        var values = strings(from: raw["attachments"])
            + strings(from: raw["attachment"])
            + strings(from: raw["mediaTitles"])
            + strings(from: raw["media"])
            + strings(from: raw["linkUrl"])
            + strings(from: raw["linkTitle"])
            + strings(from: raw["linkDescription"])
            + strings(from: raw["linkThumb"])
            + strings(from: raw["weAppUrl"])
            + strings(from: raw["weAppTitle"])
            + strings(from: raw["weAppPagePath"])
            + strings(from: raw["weAppThumb"])
            + strings(from: raw["weAppIcon"])
            + strings(from: raw["appMsgJsonData"])
            + strings(from: raw["comment"])
            + strings(from: raw["extComment"])
        if let attachment = raw["attachment"] as? [String: Any] {
            values.append(contentsOf: strings(from: attachment["content"]))
        }
        if let template = raw["template"] as? [String: Any] {
            values.append(contentsOf: attachmentStrings(from: template))
        }
        return uniqueStrings(values.map(cleaned).filter { !$0.isEmpty })
    }

    private static func strings(from value: Any?) -> [String] {
        if let string = value as? String {
            let cleanedString = cleaned(string)
            if cleanedString.first == "[" || cleanedString.first == "{" {
                if let data = cleanedString.data(using: .utf8),
                   let object = try? JSONSerialization.jsonObject(with: data) {
                    return strings(from: object)
                }
            }
            return cleanedString.isEmpty ? [] : [cleanedString]
        }
        if let number = value as? NSNumber {
            return [number.stringValue]
        }
        if let array = value as? [Any] {
            return array.flatMap(strings(from:))
        }
        if let dictionary = value as? [String: Any] {
            let keys = [
                "url", "href", "src", "path", "content", "thumb", "cover", "title", "name",
                "linkUrl", "linkTitle", "linkDescription", "linkThumb",
                "weAppUrl", "weAppTitle", "weAppPagePath", "weAppThumb", "weAppIcon",
                "appMsgJsonData", "comment", "extComment"
            ]
            return keys.flatMap { strings(from: dictionary[$0]) }
        }
        return []
    }

    private static func dictionaryPayload(from object: Any?) -> [String: Any] {
        if let dictionary = object as? [String: Any] {
            for key in ["data", "result", "payload", "item"] {
                if let nested = dictionary[key] as? [String: Any] {
                    return nested
                }
            }
            return dictionary
        }
        return [:]
    }

    private static func arrayPayload(from object: Any?) -> [[String: Any]] {
        if let array = object as? [[String: Any]] {
            return array
        }
        if let dictionary = object as? [String: Any] {
            for key in ["items", "data", "records", "rows", "list"] {
                if let array = dictionary[key] as? [[String: Any]] {
                    return array
                }
            }
            if let data = dictionary["data"] as? [String: Any] {
                for key in ["items", "records", "rows", "list", "data"] {
                    if let array = data[key] as? [[String: Any]] {
                        return array
                    }
                }
            }
        }
        return []
    }

    private static func attachmentTypeString(_ value: Int) -> String {
        switch value {
        case 1: return "image"
        case 2: return "video"
        case 3: return "link"
        default: return "text"
        }
    }

    private static func attachmentTypeInt(_ value: String, fallback: Int) -> Int {
        let normalized = value.lowercased()
        if normalized.contains("image") || normalized.contains("photo") { return 1 }
        if normalized.contains("video") { return 2 }
        if normalized.contains("link") || normalized.contains("url") || normalized.contains("weapp") { return 3 }
        if fallback > 0 { return fallback }
        return 0
    }

    private static func categoryName(for attachmentType: Int) -> String {
        switch attachmentType {
        case 1: return "图片素材"
        case 2: return "视频素材"
        case 3: return "链接素材"
        default: return "文案素材"
        }
    }

    private static func looksLikeAttachmentValue(_ value: String) -> Bool {
        OpenApiDisplay.url(from: value) != nil || value.hasPrefix("/")
    }

    private static func uniqueStrings(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.map(cleaned).filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    private static func firstExisting(_ values: [Any?]) -> Any? {
        values.first { value in
            !cleaned(string(value)).isEmpty || value is NSNumber || value is Bool
        } ?? nil
    }

    private static func firstCleanedValue(_ values: [Any?]) -> String {
        for value in values {
            let text = cleaned(string(value))
            if !text.isEmpty {
                return text
            }
        }
        return ""
    }

    private static func displayDateText(_ value: Any?) -> String {
        if let date = date(value) {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "zh_Hans_CN")
            formatter.dateFormat = "MM-dd HH:mm"
            return formatter.string(from: date)
        }
        return cleaned(string(value))
    }

    private static func jsonString(_ object: Any) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object, options: []),
              let text = String(data: data, encoding: .utf8)
        else { return "{}" }
        return text
    }

    private static func cleaned(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func string(_ value: Any?) -> String {
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number.stringValue }
        if let bool = value as? Bool { return bool ? "true" : "false" }
        return ""
    }

    private static func int(_ value: Any?) -> Int {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let int = Int(string) { return int }
        return 0
    }

    private static func int64(_ value: Any?) -> Int64 {
        if let int64 = value as? Int64 { return int64 }
        if let int = value as? Int { return Int64(int) }
        if let number = value as? NSNumber { return number.int64Value }
        if let string = value as? String, let int64 = Int64(string) { return int64 }
        return 0
    }

    private static func double(_ value: Any?) -> Double {
        if let double = value as? Double { return double }
        if let number = value as? NSNumber { return number.doubleValue }
        if let string = value as? String, let double = Double(string) { return double }
        return 0
    }

    private static func bool(_ value: Any?) -> Bool {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            return ["true", "1", "yes", "success", "enabled", "启用"].contains(string.lowercased())
        }
        return false
    }

    private static func date(_ value: Any?) -> Date? {
        if let date = value as? Date { return date }
        if let number = value as? NSNumber {
            let raw = number.doubleValue
            return Date(timeIntervalSince1970: raw > 9_999_999_999 ? raw / 1000 : raw)
        }
        guard let text = value as? String, !text.isEmpty, text != "0001-01-01T00:00:00" else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = iso.date(from: text) { return date }
        iso.formatOptions = [.withInternetDateTime]
        if let date = iso.date(from: text) { return date }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.date(from: text)
    }
}
