package com.paifa.ubikitouch.core.model

import java.util.Locale
import kotlin.math.ceil

data class FloatingChatConversation(
    val peerName: String,
    val accountName: String,
    val contacts: List<FloatingChatContact>,
    val accountContacts: List<FloatingChatContact>,
    val messages: List<FloatingChatMessage>,
    val toolActions: List<FloatingChatToolAction>,
    val groupContacts: List<FloatingChatContact> = emptyList()
)

data class FloatingChatContact(
    val id: String,
    val name: String,
    val initials: String,
    val description: String,
    val avatarColor: Long,
    val selected: Boolean = false,
    val online: Boolean = true
)

data class FloatingChatMessage(
    val id: String,
    val type: FloatingChatMessageType,
    val text: String,
    val fromMe: Boolean,
    val senderName: String,
    val time: String,
    val kind: FloatingChatMessageKind = FloatingChatMessageKind.Normal,
    val presentation: FloatingChatMessagePresentation = FloatingChatMessagePresentation.Bubble,
    val connectionTarget: FloatingChatConnectionTarget = if (fromMe) {
        FloatingChatConnectionTarget.Account
    } else {
        FloatingChatConnectionTarget.User
    },
    val connectionTargetId: String? = null,
    val threadContactId: String? = null,
    val detail: String? = null,
    val quoteAuthor: String? = null,
    val quoteText: String? = null,
    val cardKind: FloatingChatContactCardKind? = null,
    val cardName: String? = null,
    val cardSubtitle: String? = null,
    val appName: String? = null,
    val locationTitle: String? = null,
    val locationAddress: String? = null,
    val resourceUrl: String? = null,
    val fileName: String? = null,
    val fileFormat: FloatingChatFileFormat? = null,
    val fileSizeLabel: String? = null,
    val filePreviewLines: List<String> = emptyList(),
    val visibility: FloatingChatVisibilityScope? = null,
    val accessState: FloatingChatAccessState? = null,
    val thumbnailOrientation: FloatingChatThumbnailOrientation? = null,
    val mediaAspectRatio: Float? = null,
    val thumbnailUrl: String? = null,
    val mediaDurationMs: Int? = null,
    val mediaMimeType: String? = null,
    val inlineTokens: List<FloatingChatInlineToken> = emptyList()
)

enum class FloatingChatMessageType(val label: String) {
    Location("位置消息"),
    ContactLink("名片链接消息"),
    MiniProgramLink("小程序链接消息"),
    Text("文本消息"),
    MixedText("文本夹杂消息"),
    Quote("引用消息"),
    FilePreview("文件消息"),
    ImageThumbnail("文本夹杂缩略图"),
    VideoPreview("视频消息"),
    Voice("语音消息"),
    InlineContact("夹杂名片"),
    InlineLocation("夹杂定位")
}

enum class FloatingChatContactCardKind(val label: String) {
    WeCom("企微名片"),
    Personal("个人名片"),
    OfficialAccount("公众号名片"),
    MiniProgram("小程序名片"),
    Channel("视频号名片")
}

enum class FloatingChatFileFormat(val label: String) {
    Txt("txt"),
    Markdown("md"),
    Word("word"),
    Pdf("pdf")
}

enum class FloatingChatVisibilityScope(val label: String) {
    Public("公开"),
    FriendFans("仅好友粉丝"),
    Friends("仅好友"),
    Recipients("仅收件者")
}

enum class FloatingChatAccessState(val label: String) {
    Visible("可查看"),
    NeedsApply("申请查看"),
    Applied("已申请"),
    Approved("已同意")
}

enum class FloatingChatThumbnailOrientation {
    Vertical,
    Horizontal
}

data class FloatingChatInlineToken(
    val type: FloatingChatInlineTokenType,
    val text: String
)

enum class FloatingChatInlineTokenType {
    Plain,
    PaidianLink,
    FileLink,
    Url,
    Mention,
    Ai,
    ImageName
}

enum class FloatingChatMessageKind {
    Normal,
    AiDraft,
    System
}

enum class FloatingChatMessagePresentation {
    Bubble,
    SpecialCard,
    MediaStandalone,
    System
}

enum class FloatingChatConnectionTarget {
    User,
    Account,
    None
}

enum class FloatingChatToolAction {
    Assistant,
    Blink,
    Gallery,
    Camera,
    Voice,
    Files,
    Device,
    Notes,
    Card,
    Wallet,
    Favorite,
    Location,
    RedPacket,
    Transfer,
    Moments,
    QuickPhrase,
    Search,
    Pin,
    Translate,
    Screenshot,
    Reminder,
    Command,
    Share
}

object FloatingChatPrototype {
    private const val VoiceMimeType = "audio/mp4"

    sealed interface ToolThreadSelection {
        data object Group : ToolThreadSelection
        data class GroupChat(val groupId: String) : ToolThreadSelection
        data class Private(val contactId: String) : ToolThreadSelection
    }

    enum class PickedMediaKind {
        Any,
        Image,
        Video
    }

    fun pairedAccountFor(
        conversation: FloatingChatConversation,
        contactId: String
    ): FloatingChatContact {
        val activeAccounts = conversation.accountContacts.filter { account ->
            conversation.messages.any { message ->
                message.connectionTarget == FloatingChatConnectionTarget.Account &&
                    message.connectionTargetId == account.id
            }
        }.ifEmpty {
            conversation.accountContacts
        }
        require(activeAccounts.isNotEmpty()) { "Conversation must have at least one account." }

        val threadAccountId = conversation.threadAccountIdFor(contactId)
        if (threadAccountId != null) {
            activeAccounts.firstOrNull { account -> account.id == threadAccountId }?.let { account ->
                return account
            }
        }

        val contactIndex = when {
            conversation.groupContacts.any { group -> group.id == contactId } -> {
                conversation.groupContacts.indexOfFirst { group -> group.id == contactId }
            }
            else -> conversation.contacts.indexOfFirst { contact -> contact.id == contactId }
        }.takeIf { it >= 0 } ?: 0

        return activeAccounts[contactIndex % activeAccounts.size]
    }

    fun privateMessagesFor(
        conversation: FloatingChatConversation,
        contactId: String,
        accountId: String
    ): List<FloatingChatMessage> {
        val threadMessages = conversation.messages.filter { message ->
            message.threadContactId == contactId
        }
        if (threadMessages.isNotEmpty()) {
            return threadMessages
        }

        return conversation.messages.filter { message ->
            if (message.threadContactId != null) {
                return@filter message.threadContactId == contactId
            }
            when (message.connectionTarget) {
                FloatingChatConnectionTarget.User -> message.connectionTargetId == contactId
                FloatingChatConnectionTarget.Account -> message.connectionTargetId == accountId
                FloatingChatConnectionTarget.None -> true
            }
        }
    }

    fun groupMessagesFor(
        conversation: FloatingChatConversation,
        groupId: String? = null
    ): List<FloatingChatMessage> {
        return conversation.messages.filter { message ->
            if (groupId == conversation.defaultGroupContactId()) {
                message.threadContactId == null || message.threadContactId == groupId
            } else {
                message.threadContactId == groupId
            }
        }
    }

    fun simulatedOutgoingTextMessage(
        conversation: FloatingChatConversation,
        contactId: String,
        accountId: String,
        text: String,
        sequence: Int
    ): FloatingChatMessage {
        val accountName = conversation.accountNameFor(accountId)
        val sanitizedContactId = contactId.ifBlank { "unknown" }
        val sanitizedAccountId = accountId.ifBlank { "account" }

        return FloatingChatMessage(
            id = "local-$sanitizedContactId-$sanitizedAccountId-$sequence",
            type = FloatingChatMessageType.Text,
            text = text.trim(),
            fromMe = true,
            senderName = accountName,
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.Bubble,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = contactId,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    fun simulatedOutgoingGroupTextMessage(
        conversation: FloatingChatConversation,
        accountId: String,
        text: String,
        sequence: Int,
        groupId: String? = null
    ): FloatingChatMessage {
        val sanitizedAccountId = accountId.ifBlank { "account" }
        val sanitizedGroupId = groupId ?: "group"

        return FloatingChatMessage(
            id = "local-$sanitizedGroupId-$sanitizedAccountId-$sequence",
            type = FloatingChatMessageType.Text,
            text = text.trim(),
            fromMe = true,
            senderName = conversation.accountNameFor(accountId),
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.Bubble,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = groupId,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    fun simulatedOutgoingVoiceMessage(
        conversation: FloatingChatConversation,
        contactId: String,
        accountId: String,
        audioUri: String,
        durationMs: Int,
        sequence: Int
    ): FloatingChatMessage {
        val durationLabel = formatVoiceDuration(durationMs)
        return FloatingChatMessage(
            id = "local-voice-${contactId.ifBlank { "unknown" }}-${accountId.ifBlank { "account" }}-$sequence",
            type = FloatingChatMessageType.Voice,
            text = "语音 $durationLabel",
            fromMe = true,
            senderName = conversation.accountNameFor(accountId),
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.Bubble,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = contactId,
            detail = durationLabel,
            resourceUrl = audioUri,
            mediaDurationMs = durationMs,
            mediaMimeType = VoiceMimeType,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    fun simulatedOutgoingGroupVoiceMessage(
        conversation: FloatingChatConversation,
        accountId: String,
        audioUri: String,
        durationMs: Int,
        sequence: Int,
        groupId: String? = null
    ): FloatingChatMessage {
        val durationLabel = formatVoiceDuration(durationMs)
        val sanitizedGroupId = groupId ?: "group"
        return FloatingChatMessage(
            id = "local-$sanitizedGroupId-voice-${accountId.ifBlank { "account" }}-$sequence",
            type = FloatingChatMessageType.Voice,
            text = "语音 $durationLabel",
            fromMe = true,
            senderName = conversation.accountNameFor(accountId),
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.Bubble,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = groupId,
            detail = durationLabel,
            resourceUrl = audioUri,
            mediaDurationMs = durationMs,
            mediaMimeType = VoiceMimeType,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    fun simulatedToolMessage(
        conversation: FloatingChatConversation,
        action: FloatingChatToolAction,
        selection: ToolThreadSelection,
        accountId: String,
        sequence: Int
    ): FloatingChatMessage {
        val accountName = conversation.accountNameFor(accountId)
        val threadContactId = when (selection) {
            ToolThreadSelection.Group -> null
            is ToolThreadSelection.GroupChat -> selection.groupId
            is ToolThreadSelection.Private -> selection.contactId
        }
        val idPrefix = when (selection) {
            ToolThreadSelection.Group -> "local-tool-group"
            is ToolThreadSelection.GroupChat -> "local-tool-${selection.groupId}"
            is ToolThreadSelection.Private -> "local-tool-${selection.contactId}"
        }

        return when (action) {
            FloatingChatToolAction.Assistant -> FloatingChatMessage(
                id = "$idPrefix-assistant-$accountId-$sequence",
                type = FloatingChatMessageType.MixedText,
                text = "ai 预测回复：我会把图片、视频和文件都按链接留痕，方便截图后继续访问。",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                kind = FloatingChatMessageKind.AiDraft,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                inlineTokens = listOf(
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Ai, "ai"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " 预测回复：我会把 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.ImageName, "[图片]"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " 和 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Url, "https://aiff.app/media/cashier-flow.mp4"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " 以及 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.FileLink, "#文件链接"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " 都按链接留痕。")
                )
            )
            FloatingChatToolAction.Location,
            FloatingChatToolAction.Favorite,
            FloatingChatToolAction.RedPacket,
            FloatingChatToolAction.Transfer,
            FloatingChatToolAction.Files,
            FloatingChatToolAction.Card,
            FloatingChatToolAction.Moments,
            FloatingChatToolAction.QuickPhrase -> simulatedToolFeatureMessage(
                idPrefix = idPrefix,
                action = action,
                accountName = accountName,
                accountId = accountId,
                threadContactId = threadContactId,
                sequence = sequence
            )
            FloatingChatToolAction.Gallery -> simulatedToolImageMessage(
                id = "$idPrefix-image-$accountId-$sequence",
                accountName = accountName,
                accountId = accountId,
                threadContactId = threadContactId,
                sequence = sequence
            )
            FloatingChatToolAction.Voice -> simulatedToolVideoMessage(
                id = "$idPrefix-video-$accountId-$sequence",
                accountName = accountName,
                accountId = accountId,
                threadContactId = threadContactId,
                sequence = sequence
            )
            else -> FloatingChatMessage(
                id = "$idPrefix-text-$accountId-$sequence",
                type = FloatingChatMessageType.Text,
                text = "我已经把这次操作记录到当前会话，稍后继续处理。",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId
            )
        }
    }

    fun simulatedPickedMediaMessage(
        conversation: FloatingChatConversation,
        mediaKind: PickedMediaKind,
        mediaUri: String,
        previewUri: String = mediaUri,
        orientation: FloatingChatThumbnailOrientation = when (mediaKind) {
            PickedMediaKind.Any,
            PickedMediaKind.Image -> FloatingChatThumbnailOrientation.Vertical
            PickedMediaKind.Video -> FloatingChatThumbnailOrientation.Horizontal
        },
        aspectRatio: Float? = null,
        selection: ToolThreadSelection,
        accountId: String,
        sequence: Int
    ): FloatingChatMessage {
        val accountName = conversation.accountNameFor(accountId)
        val threadContactId = when (selection) {
            ToolThreadSelection.Group -> null
            is ToolThreadSelection.GroupChat -> selection.groupId
            is ToolThreadSelection.Private -> selection.contactId
        }
        val idPrefix = when (selection) {
            ToolThreadSelection.Group -> "local-picked-group"
            is ToolThreadSelection.GroupChat -> "local-picked-${selection.groupId}"
            is ToolThreadSelection.Private -> "local-picked-${selection.contactId}"
        }

        return when (mediaKind) {
            PickedMediaKind.Any,
            PickedMediaKind.Image -> FloatingChatMessage(
                id = "$idPrefix-image-$accountId-$sequence",
                type = FloatingChatMessageType.ImageThumbnail,
                text = "",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.MediaStandalone,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                thumbnailOrientation = orientation,
                mediaAspectRatio = aspectRatio,
                thumbnailUrl = previewUri,
                resourceUrl = mediaUri,
                visibility = FloatingChatVisibilityScope.Public,
                accessState = FloatingChatAccessState.Visible
            )
            PickedMediaKind.Video -> FloatingChatMessage(
                id = "$idPrefix-video-$accountId-$sequence",
                type = FloatingChatMessageType.VideoPreview,
                text = "",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.MediaStandalone,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                thumbnailOrientation = orientation,
                mediaAspectRatio = aspectRatio,
                thumbnailUrl = previewUri,
                resourceUrl = mediaUri,
                visibility = FloatingChatVisibilityScope.Public,
                accessState = FloatingChatAccessState.Visible
            )
        }
    }

    fun pickedDocumentMessage(
        conversation: FloatingChatConversation,
        documentUri: String,
        displayName: String,
        fileFormat: FloatingChatFileFormat?,
        fileSizeLabel: String?,
        previewLines: List<String>,
        mimeType: String?,
        selection: ToolThreadSelection,
        accountId: String,
        sequence: Int
    ): FloatingChatMessage {
        val accountName = conversation.accountNameFor(accountId)
        val threadContactId = when (selection) {
            ToolThreadSelection.Group -> null
            is ToolThreadSelection.GroupChat -> selection.groupId
            is ToolThreadSelection.Private -> selection.contactId
        }
        val idPrefix = when (selection) {
            ToolThreadSelection.Group -> "local-picked-group"
            is ToolThreadSelection.GroupChat -> "local-picked-${selection.groupId}"
            is ToolThreadSelection.Private -> "local-picked-${selection.contactId}"
        }
        val safeName = displayName.trim().ifBlank { "未命名文档" }
        return FloatingChatMessage(
            id = "$idPrefix-document-$accountId-$sequence",
            type = FloatingChatMessageType.FilePreview,
            text = safeName,
            fromMe = true,
            senderName = accountName,
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.SpecialCard,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = threadContactId,
            resourceUrl = documentUri,
            fileName = safeName,
            fileFormat = fileFormat,
            fileSizeLabel = fileSizeLabel,
            filePreviewLines = previewLines,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible,
            mediaMimeType = mimeType
        )
    }

    fun sampleConversation(): FloatingChatConversation {
        return FloatingChatConversation(
            peerName = "星河产品小组",
            accountName = "林舟",
            contacts = contacts(),
            accountContacts = accountContacts(),
            messages = sharedConversationMessages() + additionalGroupThreadMessages() + privateThreadMessages(),
            toolActions = FloatingChatToolAction.entries,
            groupContacts = groupContacts()
        )
    }

    private fun groupContacts(): List<FloatingChatContact> {
        return listOf(
            FloatingChatContact("group-product", "星河产品小组群", "产品", "产品评审 · 研发协作", 0xFF5B7CFA, selected = true),
            FloatingChatContact("group-ops", "门店运营群", "门店", "活动排期 · 到店核销", 0xFF2A9D8F),
            FloatingChatContact("group-ai", "AI 内测群", "AI", "模型回复 · 权限验证", 0xFF7B2CBF),
            FloatingChatContact("group-camping", "周末露营群", "露营", "朋友聚会 · 行程照片", 0xFFE07A5F, online = false)
        )
    }

    private fun contacts(): List<FloatingChatContact> {
        return listOf(
            FloatingChatContact("li-si", "沈嘉木", "嘉木", "前端开发 · 组件库", 0xFF1B9AAA),
            FloatingChatContact("wang-wu", "许知南", "知南", "视觉设计 · 移动端", 0xFFE07A5F, online = false),
            FloatingChatContact("zhao-liu", "唐一澈", "一澈", "测试工程师 · Android", 0xFF8E7DBE),
            FloatingChatContact("xiao-chen", "陈若川", "若川", "后端开发 · 消息服务", 0xFF2A9D8F, online = false),
            FloatingChatContact("sun-lin", "孙临", "孙临", "门店运营 · 华东区", 0xFFB56576),
            FloatingChatContact("he-miao", "何苗", "何苗", "客户成功 · 续费跟进", 0xFFEF476F),
            FloatingChatContact("qian-yue", "钱越", "钱越", "财务专员 · 对账转账", 0xFFFFB703),
            FloatingChatContact("gu-yan", "顾言", "顾言", "品牌设计 · 朋友圈素材", 0xFF457B9D),
            FloatingChatContact("luo-bei", "罗北", "罗北", "摄影剪辑 · 活动视频", 0xFF118AB2, online = false),
            FloatingChatContact("assistant", "灵犀助手", "灵犀", "AI 助手 · 会话摘要", 0xFF6C757D)
        )
    }

    private fun accountContacts(): List<FloatingChatContact> {
        return listOf(
            FloatingChatContact("account-main", "林舟", "林舟", "个人微信 · 项目负责人", 0xFF3A86FF, selected = true),
            FloatingChatContact("account-work", "企业微信号", "企微", "客户沟通 · 工单跟进", 0xFF06D6A0),
            FloatingChatContact("account-bot", "灵犀托管", "托管", "AI 自动回复 · 夜间值守", 0xFF7B2CBF),
            FloatingChatContact("account-team", "小组群号", "小组", "团队公告 · 发布同步", 0xFFFFB703, online = false),
            FloatingChatContact("account-market", "运营助手", "运营", "活动推送 · 社群运营", 0xFFEF476F),
            FloatingChatContact("account-live", "直播客服", "直播", "直播间咨询 · 售前答疑", 0xFF118AB2),
            FloatingChatContact("account-store", "门店服务", "门店", "线下门店 · 到店核销", 0xFFB56576, online = false),
            FloatingChatContact("account-service", "售后专员", "售后", "售后服务 · 退款换货", 0xFF457B9D),
            FloatingChatContact("account-private", "私域顾问", "私域", "会员维护 · 高意向客户", 0xFF9A8C98, online = false)
        )
    }

    private fun sharedConversationMessages(): List<FloatingChatMessage> {
        return listOf(
            message(1, FloatingChatMessageType.Text, "早上 10 点评审，我把首页动线和支付入口都放到同一版里了。", false, "沈嘉木", connectionTargetId = "li-si"),
            message(2, FloatingChatMessageType.Location, "深圳湾科技生态园 9 栋", false, "陈若川", connectionTargetId = "xiao-chen", locationTitle = "深圳湾科技生态园", locationAddress = "深圳市南山区高新南十道 9 号"),
            message(
                index = 3,
                type = FloatingChatMessageType.MixedText,
                text = "请看 [门店收银台] @交互说明.md https://aiff.app/spec/cashier @许知南 ai [图片:首页标注.png]",
                fromMe = true,
                senderName = "企业微信号",
                connectionTargetId = "account-work",
                kind = FloatingChatMessageKind.AiDraft,
                inlineTokens = listOf(
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, "请看 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.PaidianLink, "[门店收银台]"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.FileLink, "@交互说明.md"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Url, "https://aiff.app/spec/cashier"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Mention, "@许知南"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Ai, "ai"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.ImageName, "[图片:首页标注.png]")
                )
            ),
            contactLinkMessage(4, FloatingChatContactCardKind.WeCom, "梁晨", "企微客户经理 · 华南区", true, "account-main"),
            contactLinkMessage(5, FloatingChatContactCardKind.Personal, "顾言", "个人微信 · 设计协作", false, "li-si"),
            contactLinkMessage(6, FloatingChatContactCardKind.OfficialAccount, "星河产品实验室", "公众号 · 交互研究", false, "assistant"),
            contactLinkMessage(7, FloatingChatContactCardKind.MiniProgram, "门店助手", "小程序名片 · 到店核销", true, "account-team"),
            contactLinkMessage(8, FloatingChatContactCardKind.Channel, "星河视频号", "视频号名片 · 活动回放", false, "wang-wu"),
            message(9, FloatingChatMessageType.MiniProgramLink, "任务看板 · 周三评审清单", true, "灵犀托管", connectionTargetId = "account-bot", appName = "星河协作", resourceUrl = "https://aiff.app/miniprogram/task-board"),
            message(10, FloatingChatMessageType.Quote, "这段我同意，动效可以先按 180ms 做第一版。", false, "唐一澈", connectionTargetId = "zhao-liu", quoteAuthor = "林舟", quoteText = "支付确认页先保证状态清楚，动画可以后补。"),
            filePreviewMessage(11, FloatingChatFileFormat.Txt, "门店反馈.txt", listOf("1. 夜间收银员更关注退款入口", "2. 优惠券核销需要显示剩余次数"), true, "小组群号", "account-team", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible),
            filePreviewMessage(12, FloatingChatFileFormat.Markdown, "评审记录.md", listOf("## 周三评审", "- 支付页增加异常状态", "- 图片和文件保留访问链接"), false, "沈嘉木", "li-si", FloatingChatVisibilityScope.FriendFans, FloatingChatAccessState.NeedsApply),
            filePreviewMessage(13, FloatingChatFileFormat.Word, "客户确认.docx", listOf("客户：星河科技", "范围：仅好友可查看", "状态：申请已提交"), true, "灵犀托管", "account-bot", FloatingChatVisibilityScope.Friends, FloatingChatAccessState.Applied),
            filePreviewMessage(14, FloatingChatFileFormat.Pdf, "权限说明.pdf", listOf("仅收件者可查看原文", "同意后 AI 可通过链接读取"), true, "小组群号", "account-team", FloatingChatVisibilityScope.Recipients, FloatingChatAccessState.Approved),
            imageThumbnailMessage(15, FloatingChatThumbnailOrientation.Vertical, "收银台长截图", false, "陈若川", "xiao-chen", FloatingChatVisibilityScope.Friends, FloatingChatAccessState.NeedsApply),
            imageThumbnailMessage(16, FloatingChatThumbnailOrientation.Horizontal, "首页横版标注图", true, "林舟", "account-main", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible),
            videoPreviewMessage(17, "15 秒操作录屏", true, "林舟", "account-main", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible),
            voiceMessage(18, "语音 0:08", true, "企业微信号", "account-work", null, "https://aiff.app/audio/group-18.m4a", 8_000),
            message(19, FloatingChatMessageType.InlineContact, "推名片: 许知南", true, "林舟", connectionTargetId = "account-main", cardName = "许知南", cardSubtitle = "视觉设计 · 移动端"),
            message(20, FloatingChatMessageType.InlineLocation, "坐标：深圳湾 9 栋北门", false, "许知南", connectionTargetId = "wang-wu", locationTitle = "深圳湾 9 栋北门", locationAddress = "离地铁口 280 米"),
            message(21, FloatingChatMessageType.Text, "系统提示：钱越加入群聊，沈嘉木更新了一条文件权限。", false, "系统", presentation = FloatingChatMessagePresentation.System, connectionTarget = FloatingChatConnectionTarget.None)
        )
    }

    private fun additionalGroupThreadMessages(): List<FloatingChatMessage> {
        return listOf(
            message(31, FloatingChatMessageType.Text, "今晚华东两家门店做会员日，红包入口先放在收银完成页下面。", false, "孙临", connectionTargetId = "sun-lin", threadContactId = "group-ops"),
            message(32, FloatingChatMessageType.Location, "上海静安嘉里中心店", false, "何苗", connectionTargetId = "he-miao", threadContactId = "group-ops", locationTitle = "上海静安嘉里中心店", locationAddress = "上海市静安区南京西路 1515 号"),
            filePreviewMessage(33, FloatingChatFileFormat.Pdf, "会员日排班.pdf", listOf("一楼门店：18:00-22:00", "二楼门店：17:30-21:30"), true, "运营助手", "account-market", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible, "group-ops"),
            imageThumbnailMessage(34, FloatingChatThumbnailOrientation.Horizontal, "门店海报横版", false, "顾言", "gu-yan", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible, "group-ops"),
            message(35, FloatingChatMessageType.MiniProgramLink, "会员日核销台", true, "门店服务", connectionTargetId = "account-store", threadContactId = "group-ops", appName = "门店助手", resourceUrl = "https://aiff.app/miniprogram/store-checkout"),
            voiceMessage(36, "语音 0:12", false, "孙临", "sun-lin", "group-ops", "https://aiff.app/audio/ops-36.m4a", 12_000),

            message(41, FloatingChatMessageType.Text, "AI 预测回复的灰度我开到 20%，先只覆盖工作时间。", false, "灵犀助手", connectionTargetId = "assistant", threadContactId = "group-ai"),
            message(
                index = 42,
                type = FloatingChatMessageType.MixedText,
                text = "请同步 @接口日志.md https://aiff.app/logs/ai-reply @陈若川 ai [图片:召回曲线.png]",
                fromMe = true,
                senderName = "灵犀托管",
                connectionTargetId = "account-bot",
                threadContactId = "group-ai",
                kind = FloatingChatMessageKind.AiDraft,
                inlineTokens = listOf(
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, "请同步 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.FileLink, "@接口日志.md"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Url, "https://aiff.app/logs/ai-reply"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Mention, "@陈若川"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Ai, "ai"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.ImageName, "[图片:召回曲线.png]")
                )
            ),
            filePreviewMessage(43, FloatingChatFileFormat.Markdown, "灰度策略.md", listOf("## 范围", "- 工作日 9:00-19:00", "- 仅好友和收件者可申请查看"), false, "陈若川", "xiao-chen", FloatingChatVisibilityScope.Friends, FloatingChatAccessState.Applied, "group-ai"),
            videoPreviewMessage(44, "预测回复录屏", true, "林舟", "account-main", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible, "group-ai"),
            message(45, FloatingChatMessageType.Quote, "我把异常回复先收敛到客服账号，别影响私人号。", false, "沈嘉木", connectionTargetId = "li-si", threadContactId = "group-ai", quoteAuthor = "灵犀助手", quoteText = "这条建议可能需要人工确认。"),
            message(46, FloatingChatMessageType.InlineContact, "推名片: 陈若川", true, "林舟", connectionTargetId = "account-main", threadContactId = "group-ai", cardName = "陈若川", cardSubtitle = "后端开发 · 消息服务"),

            message(51, FloatingChatMessageType.Text, "周六如果不下雨就去大鹏，帐篷我带两顶。", false, "罗北", connectionTargetId = "luo-bei", threadContactId = "group-camping"),
            imageThumbnailMessage(52, FloatingChatThumbnailOrientation.Vertical, "上次海边照片", false, "顾言", "gu-yan", FloatingChatVisibilityScope.FriendFans, FloatingChatAccessState.Visible, "group-camping"),
            videoPreviewMessage(53, "营地路线视频", false, "罗北", "luo-bei", FloatingChatVisibilityScope.Friends, FloatingChatAccessState.NeedsApply, "group-camping"),
            message(54, FloatingChatMessageType.InlineLocation, "大鹏较场尾停车场", true, "林舟", connectionTargetId = "account-private", threadContactId = "group-camping", locationTitle = "大鹏较场尾停车场", locationAddress = "深圳市龙岗区较场尾路"),
            message(55, FloatingChatMessageType.Text, "我负责饮料和一次性餐具，账单晚点发群里。", false, "钱越", connectionTargetId = "qian-yue", threadContactId = "group-camping"),
            filePreviewMessage(56, FloatingChatFileFormat.Txt, "露营清单.txt", listOf("帐篷 2 顶", "折叠椅 6 把", "饮料和餐具由钱越准备"), true, "私域顾问", "account-private", FloatingChatVisibilityScope.Public, FloatingChatAccessState.Visible, "group-camping")
        )
    }

    private fun privateThreadMessages(): List<FloatingChatMessage> {
        val pairs = listOf(
            ThreadPair("li-si", "沈嘉木", "account-work", "企业微信号"),
            ThreadPair("wang-wu", "许知南", "account-bot", "灵犀托管"),
            ThreadPair("zhao-liu", "唐一澈", "account-team", "小组群号"),
            ThreadPair("xiao-chen", "陈若川", "account-main", "林舟"),
            ThreadPair("sun-lin", "孙临", "account-market", "运营助手"),
            ThreadPair("he-miao", "何苗", "account-service", "售后专员"),
            ThreadPair("qian-yue", "钱越", "account-private", "私域顾问"),
            ThreadPair("gu-yan", "顾言", "account-main", "林舟"),
            ThreadPair("luo-bei", "罗北", "account-live", "直播客服"),
            ThreadPair("assistant", "灵犀助手", "account-work", "企业微信号")
        )
        val fileFormats = FloatingChatFileFormat.entries
        val visibilityScopes = FloatingChatVisibilityScope.entries
        val accessStates = FloatingChatAccessState.entries

        return pairs.flatMapIndexed { pairIndex, pair ->
            val baseIndex = 101 + pairIndex * 6
            val format = fileFormats[pairIndex % fileFormats.size]
            listOf(
                message(baseIndex, FloatingChatMessageType.Text, "${pair.contactName}：我把刚才确认的内容发你，方便你单独核对。", false, pair.contactName, connectionTargetId = pair.contactId, threadContactId = pair.contactId),
                message(baseIndex + 1, FloatingChatMessageType.Text, "${pair.accountName}：收到，我先按这个版本推进，有变化再同步你。", true, pair.accountName, connectionTargetId = pair.accountId, threadContactId = pair.contactId),
                imageThumbnailMessage(
                    baseIndex + 2,
                    if (pairIndex % 2 == 0) FloatingChatThumbnailOrientation.Vertical else FloatingChatThumbnailOrientation.Horizontal,
                    "${pair.contactName} 发来的截图",
                    false,
                    pair.contactName,
                    pair.contactId,
                    visibilityScopes[pairIndex % visibilityScopes.size],
                    accessStates[pairIndex % accessStates.size],
                    pair.contactId
                ),
                filePreviewMessage(
                    baseIndex + 3,
                    format,
                    "跟进记录-${pair.contactName}.${format.label}",
                    listOf("沟通对象：${pair.contactName}", "下一步：确认时间和负责人"),
                    true,
                    pair.accountName,
                    pair.accountId,
                    visibilityScopes[(pairIndex + 1) % visibilityScopes.size],
                    accessStates[(pairIndex + 1) % accessStates.size],
                    pair.contactId
                ),
                latestIncomingPrivateThreadMessage(
                    index = baseIndex + 4,
                    pairIndex = pairIndex,
                    pair = pair,
                    format = format,
                    visibility = visibilityScopes[pairIndex % visibilityScopes.size],
                    accessState = accessStates[pairIndex % accessStates.size]
                ),
                message(baseIndex + 5, FloatingChatMessageType.Quote, "${pair.accountName}：这条我已经标记，晚上给你最终确认。", true, pair.accountName, connectionTargetId = pair.accountId, threadContactId = pair.contactId, quoteAuthor = pair.contactName, quoteText = "如果今天来不及，明早 10 点前给我也可以。")
            )
        }
    }

    private fun latestIncomingPrivateThreadMessage(
        index: Int,
        pairIndex: Int,
        pair: ThreadPair,
        format: FloatingChatFileFormat,
        visibility: FloatingChatVisibilityScope,
        accessState: FloatingChatAccessState
    ): FloatingChatMessage {
        return when (pairIndex % 10) {
            0 -> message(
                index,
                FloatingChatMessageType.Text,
                "${pair.contactName}：我把新的确认点发你，先按这个版本处理。",
                false,
                pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId
            )
            1 -> voiceMessage(
                index,
                "语音 0:12",
                false,
                pair.contactName,
                pair.contactId,
                pair.contactId,
                "https://aiff.app/audio/private-${pair.contactId}-$index.m4a",
                12_000
            )
            2 -> filePreviewMessage(
                index,
                format,
                "最新跟进-${pair.contactName}.${format.label}",
                listOf("沟通对象：${pair.contactName}", "状态：等对方确认最后一版"),
                false,
                pair.contactName,
                pair.contactId,
                visibility,
                accessState,
                pair.contactId
            )
            3 -> message(
                index,
                FloatingChatMessageType.Location,
                "${pair.contactName} 发来的会面地点",
                false,
                pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId,
                locationTitle = "${pair.contactName} 发来的会面地点",
                locationAddress = "地铁站 B 口附近，步行约 5 分钟"
            )
            4 -> message(
                index,
                FloatingChatMessageType.ContactLink,
                "推名片：${pair.contactName}",
                false,
                pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId,
                cardKind = FloatingChatContactCardKind.Personal,
                cardName = pair.contactName,
                cardSubtitle = "${pair.accountName} 正在跟进"
            )
            5 -> imageThumbnailMessage(
                index,
                if (pairIndex % 2 == 0) FloatingChatThumbnailOrientation.Vertical else FloatingChatThumbnailOrientation.Horizontal,
                "${pair.contactName} 发来的现场图",
                false,
                pair.contactName,
                pair.contactId,
                visibility,
                accessState,
                pair.contactId
            )
            6 -> message(
                index = index,
                type = FloatingChatMessageType.MixedText,
                text = "请看 @跟进记录.md https://aiff.app/thread/${pair.contactId} @${pair.accountName} ai [图片:现场补充.png]",
                fromMe = false,
                senderName = pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId,
                inlineTokens = listOf(
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, "请看 "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.FileLink, "@跟进记录.md"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Url, "https://aiff.app/thread/${pair.contactId}"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Mention, "@${pair.accountName}"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Ai, "ai"),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " "),
                    FloatingChatInlineToken(FloatingChatInlineTokenType.ImageName, "[图片:现场补充.png]")
                )
            )
            7 -> videoPreviewMessage(
                index,
                "${pair.contactName} 发来 15 秒视频",
                false,
                pair.contactName,
                pair.contactId,
                visibility,
                accessState,
                pair.contactId
            )
            8 -> message(
                index,
                FloatingChatMessageType.Quote,
                "${pair.contactName}：我按你刚才说的改好了。",
                false,
                pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId,
                quoteAuthor = pair.accountName,
                quoteText = "这版先收口，后续问题再开新记录。"
            )
            else -> message(
                index,
                FloatingChatMessageType.MiniProgramLink,
                "最新工单：${pair.contactName} 的处理进度",
                false,
                pair.contactName,
                connectionTargetId = pair.contactId,
                threadContactId = pair.contactId,
                appName = "协作看板",
                resourceUrl = "https://aiff.app/miniprogram/thread-${pair.contactId}"
            )
        }
    }

    private fun contactLinkMessage(
        index: Int,
        cardKind: FloatingChatContactCardKind,
        cardName: String,
        cardSubtitle: String,
        fromMe: Boolean,
        connectionTargetId: String
    ): FloatingChatMessage {
        return message(
            index = index,
            type = FloatingChatMessageType.ContactLink,
            text = "${cardKind.label}: $cardName",
            fromMe = fromMe,
            senderName = if (fromMe) "林舟" else cardName,
            connectionTargetId = connectionTargetId,
            cardKind = cardKind,
            cardName = cardName,
            cardSubtitle = cardSubtitle,
            resourceUrl = "https://aiff.app/cards/${cardKind.name.lowercase(Locale.US)}-$index"
        )
    }

    private fun filePreviewMessage(
        index: Int,
        format: FloatingChatFileFormat,
        fileName: String,
        previewLines: List<String>,
        fromMe: Boolean,
        senderName: String,
        connectionTargetId: String,
        visibility: FloatingChatVisibilityScope,
        accessState: FloatingChatAccessState,
        threadContactId: String? = null
    ): FloatingChatMessage {
        return message(
            index = index,
            type = FloatingChatMessageType.FilePreview,
            text = fileName,
            fromMe = fromMe,
            senderName = senderName,
            connectionTargetId = connectionTargetId,
            threadContactId = threadContactId,
            resourceUrl = "https://aiff.app/files/$index/${fileName.encodeForUrlPath()}",
            fileName = fileName,
            fileFormat = format,
            fileSizeLabel = "${24 + index} KB",
            filePreviewLines = previewLines,
            visibility = visibility,
            accessState = accessState
        )
    }

    private fun imageThumbnailMessage(
        index: Int,
        orientation: FloatingChatThumbnailOrientation,
        text: String,
        fromMe: Boolean,
        senderName: String,
        connectionTargetId: String,
        visibility: FloatingChatVisibilityScope,
        accessState: FloatingChatAccessState,
        threadContactId: String? = null
    ): FloatingChatMessage {
        return message(
            index = index,
            type = FloatingChatMessageType.ImageThumbnail,
            text = text,
            fromMe = fromMe,
            senderName = senderName,
            connectionTargetId = connectionTargetId,
            threadContactId = threadContactId,
            thumbnailOrientation = orientation,
            thumbnailUrl = "https://aiff.app/images/$index/${orientation.name.lowercase(Locale.US)}-preview.jpg",
            visibility = visibility,
            accessState = accessState
        )
    }

    private fun simulatedToolImageMessage(
        id: String,
        accountName: String,
        accountId: String,
        threadContactId: String?,
        sequence: Int
    ): FloatingChatMessage {
        return FloatingChatMessage(
            id = id,
            type = FloatingChatMessageType.ImageThumbnail,
            text = "",
            fromMe = true,
            senderName = accountName,
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.MediaStandalone,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = threadContactId,
            thumbnailOrientation = FloatingChatThumbnailOrientation.Vertical,
            thumbnailUrl = "https://aiff.app/images/local-$sequence/tool-image-preview.jpg",
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    private fun simulatedToolFeatureMessage(
        idPrefix: String,
        action: FloatingChatToolAction,
        accountName: String,
        accountId: String,
        threadContactId: String?,
        sequence: Int
    ): FloatingChatMessage {
        val commonId = "$idPrefix-${action.name.lowercase(Locale.US)}-$accountId-$sequence"
        return when (action) {
            FloatingChatToolAction.Location -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.Location,
                text = "发送位置",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                locationTitle = "星河创意园 3 号楼",
                locationAddress = "深圳市南山区科苑南路 28 号，距地铁口约 260 米",
                visibility = FloatingChatVisibilityScope.Public,
                accessState = FloatingChatAccessState.Visible
            )
            FloatingChatToolAction.Favorite -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.Quote,
                text = "从收藏发送：项目会议纪要重点",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                quoteAuthor = "收藏",
                quoteText = "已收藏的图片、文件或聊天记录可以作为引用发送。"
            )
            FloatingChatToolAction.RedPacket -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.MiniProgramLink,
                text = "浮窗红包 ¥8.88",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                appName = "浮窗红包",
                detail = "恭喜发财，大吉大利",
                resourceUrl = "https://aiff.app/app/red-packet/$sequence"
            )
            FloatingChatToolAction.Transfer -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.MiniProgramLink,
                text = "转账 ¥88.00",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                appName = "浮窗转账",
                detail = "转账给你，请查收",
                resourceUrl = "https://aiff.app/app/transfer/$sequence"
            )
            FloatingChatToolAction.Files -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.FilePreview,
                text = "项目排期.md",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                resourceUrl = "https://aiff.app/files/local/project-plan-$sequence.md",
                fileName = "项目排期.md",
                fileFormat = FloatingChatFileFormat.Markdown,
                fileSizeLabel = "36 KB",
                filePreviewLines = listOf("# 项目排期", "- 今天：确认交互", "- 明天：联调安装"),
                visibility = FloatingChatVisibilityScope.Public,
                accessState = FloatingChatAccessState.Visible
            )
            FloatingChatToolAction.Card -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.ContactLink,
                text = "推名片：周雨晴",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                cardKind = FloatingChatContactCardKind.Personal,
                cardName = "周雨晴",
                cardSubtitle = "产品经理 · 星河 App",
                resourceUrl = "https://aiff.app/cards/local-contact-$sequence"
            )
            FloatingChatToolAction.Moments -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.MiniProgramLink,
                text = "浮窗朋友圈动态",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                presentation = FloatingChatMessagePresentation.SpecialCard,
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId,
                appName = "浮窗朋友圈",
                detail = "在 App 内发表了 1 条动态",
                resourceUrl = "https://aiff.app/app/moments/local-$sequence"
            )
            FloatingChatToolAction.QuickPhrase -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.Text,
                text = "收到，我先看一下，稍后同步进展。",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId
            )
            else -> FloatingChatMessage(
                id = commonId,
                type = FloatingChatMessageType.Text,
                text = "这个功能已打开。",
                fromMe = true,
                senderName = accountName,
                time = "刚刚",
                connectionTarget = FloatingChatConnectionTarget.Account,
                connectionTargetId = accountId,
                threadContactId = threadContactId
            )
        }
    }

    private fun simulatedToolVideoMessage(
        id: String,
        accountName: String,
        accountId: String,
        threadContactId: String?,
        sequence: Int
    ): FloatingChatMessage {
        return FloatingChatMessage(
            id = id,
            type = FloatingChatMessageType.VideoPreview,
            text = "",
            fromMe = true,
            senderName = accountName,
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.MediaStandalone,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = accountId,
            threadContactId = threadContactId,
            thumbnailOrientation = FloatingChatThumbnailOrientation.Horizontal,
            thumbnailUrl = "https://aiff.app/videos/local-$sequence/tool-video-cover.jpg",
            resourceUrl = "https://aiff.app/videos/local-$sequence/tool-video.mp4",
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    private fun videoPreviewMessage(
        index: Int,
        text: String,
        fromMe: Boolean,
        senderName: String,
        connectionTargetId: String,
        visibility: FloatingChatVisibilityScope,
        accessState: FloatingChatAccessState,
        threadContactId: String? = null
    ): FloatingChatMessage {
        return message(
            index = index,
            type = FloatingChatMessageType.VideoPreview,
            text = text,
            fromMe = fromMe,
            senderName = senderName,
            connectionTargetId = connectionTargetId,
            threadContactId = threadContactId,
            thumbnailOrientation = FloatingChatThumbnailOrientation.Horizontal,
            thumbnailUrl = "https://aiff.app/videos/$index/cover.jpg",
            resourceUrl = "https://aiff.app/videos/$index/chat-preview.mp4",
            visibility = visibility,
            accessState = accessState
        )
    }

    private fun voiceMessage(
        index: Int,
        text: String,
        fromMe: Boolean,
        senderName: String,
        connectionTargetId: String,
        threadContactId: String?,
        resourceUrl: String,
        durationMs: Int
    ): FloatingChatMessage {
        return message(
            index = index,
            type = FloatingChatMessageType.Voice,
            text = text,
            fromMe = fromMe,
            senderName = senderName,
            connectionTargetId = connectionTargetId,
            threadContactId = threadContactId,
            detail = formatVoiceDuration(durationMs),
            resourceUrl = resourceUrl,
            mediaDurationMs = durationMs,
            mediaMimeType = VoiceMimeType,
            visibility = FloatingChatVisibilityScope.Public,
            accessState = FloatingChatAccessState.Visible
        )
    }

    private data class ThreadPair(
        val contactId: String,
        val contactName: String,
        val accountId: String,
        val accountName: String
    )

    private fun message(
        index: Int,
        type: FloatingChatMessageType,
        text: String,
        fromMe: Boolean,
        senderName: String,
        kind: FloatingChatMessageKind = FloatingChatMessageKind.Normal,
        presentation: FloatingChatMessagePresentation = FloatingChatMessagePresentation.Bubble,
        connectionTarget: FloatingChatConnectionTarget = if (fromMe) {
            FloatingChatConnectionTarget.Account
        } else {
            FloatingChatConnectionTarget.User
        },
        connectionTargetId: String? = null,
        threadContactId: String? = null,
        detail: String? = null,
        quoteAuthor: String? = null,
        quoteText: String? = null,
        cardKind: FloatingChatContactCardKind? = null,
        cardName: String? = null,
        cardSubtitle: String? = null,
        appName: String? = null,
        locationTitle: String? = null,
        locationAddress: String? = null,
        resourceUrl: String? = null,
        fileName: String? = null,
        fileFormat: FloatingChatFileFormat? = null,
        fileSizeLabel: String? = null,
        filePreviewLines: List<String> = emptyList(),
        visibility: FloatingChatVisibilityScope? = null,
        accessState: FloatingChatAccessState? = null,
        thumbnailOrientation: FloatingChatThumbnailOrientation? = null,
        mediaAspectRatio: Float? = null,
        thumbnailUrl: String? = null,
        mediaDurationMs: Int? = null,
        mediaMimeType: String? = null,
        inlineTokens: List<FloatingChatInlineToken> = emptyList()
    ): FloatingChatMessage {
        val minute = (20 + index) % 60
        return FloatingChatMessage(
            id = "m$index",
            type = type,
            text = text,
            fromMe = fromMe,
            senderName = senderName,
            time = "14:${minute.toString().padStart(2, '0')}",
            kind = if (presentation == FloatingChatMessagePresentation.System) {
                FloatingChatMessageKind.System
            } else {
                kind
            },
            presentation = presentation,
            connectionTarget = connectionTarget,
            connectionTargetId = connectionTargetId,
            threadContactId = threadContactId,
            detail = detail,
            quoteAuthor = quoteAuthor,
            quoteText = quoteText,
            cardKind = cardKind,
            cardName = cardName,
            cardSubtitle = cardSubtitle,
            appName = appName,
            locationTitle = locationTitle,
            locationAddress = locationAddress,
            resourceUrl = resourceUrl,
            fileName = fileName,
            fileFormat = fileFormat,
            fileSizeLabel = fileSizeLabel,
            filePreviewLines = filePreviewLines,
            visibility = visibility,
            accessState = accessState,
            thumbnailOrientation = thumbnailOrientation,
            mediaAspectRatio = mediaAspectRatio,
            thumbnailUrl = thumbnailUrl,
            mediaDurationMs = mediaDurationMs,
            mediaMimeType = mediaMimeType,
            inlineTokens = inlineTokens
        )
    }

    private fun FloatingChatConversation.accountNameFor(accountId: String): String {
        return accountContacts.firstOrNull { account -> account.id == accountId }?.name ?: accountName
    }

    private fun FloatingChatConversation.defaultGroupContactId(): String? {
        return groupContacts.firstOrNull { group -> group.selected }?.id
            ?: groupContacts.firstOrNull()?.id
    }

    private fun FloatingChatConversation.threadAccountIdFor(contactId: String): String? {
        val targetThreadId = if (contactId == defaultGroupContactId()) {
            null
        } else {
            contactId
        }
        return messages.firstOrNull { message ->
            message.threadContactId == targetThreadId &&
                message.connectionTarget == FloatingChatConnectionTarget.Account &&
                message.connectionTargetId != null
        }?.connectionTargetId
    }

    private fun formatVoiceDuration(durationMs: Int): String {
        val totalSeconds = ceil(durationMs.coerceAtLeast(0) / 1000.0).toInt().coerceAtLeast(1)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun String.encodeForUrlPath(): String {
        return map { char ->
            when {
                char.isLetterOrDigit() -> char
                char == '.' || char == '-' || char == '_' -> char
                else -> '-'
            }
        }.joinToString("")
    }
}
