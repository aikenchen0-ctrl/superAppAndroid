package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.paifa.univerge.core.model.FloatingChatToolAction

internal fun toolActionLabel(action: FloatingChatToolAction): String {
    return when (action) {
        FloatingChatToolAction.Contacts -> "\u8054\u7cfb\u4eba"
        FloatingChatToolAction.Assistant -> "\u673a\u5668\u4eba"
        FloatingChatToolAction.AiVoice -> "AI\u8bed\u97f3"
        FloatingChatToolAction.Blink -> "\u7728\u773c"
        FloatingChatToolAction.Gallery -> "\u76f8\u518c"
        FloatingChatToolAction.Video -> "\u89c6\u9891/\u77ed\u89c6\u9891"
        FloatingChatToolAction.ChannelsVideo -> "\u89c6\u9891\u53f7\u89c6\u9891"
        FloatingChatToolAction.ChannelsLive -> "\u89c6\u9891\u53f7\u76f4\u64ad"
        FloatingChatToolAction.WebLink -> "\u7f51\u9875\u94fe\u63a5"
        FloatingChatToolAction.Camera -> "\u6444\u5f71"
        FloatingChatToolAction.Location -> "\u4f4d\u7f6e"
        FloatingChatToolAction.Favorite -> "\u6536\u85cf"
        FloatingChatToolAction.RedPacket -> "\u7ea2\u5305"
        FloatingChatToolAction.Transfer -> "\u8f6c\u8d26"
        FloatingChatToolAction.Files -> "\u6587\u6863"
        FloatingChatToolAction.Card -> "\u63a8\u540d\u7247"
        FloatingChatToolAction.GroupInvite -> "\u7fa4\u9080\u8bf7\u5361"
        FloatingChatToolAction.Relay -> "\u63a5\u9f99\u6d88\u606f"
        FloatingChatToolAction.SideEffect -> "\u4fa7\u8fb9\u7279\u6548"
        FloatingChatToolAction.Moments -> "\u670b\u53cb\u5708"
        FloatingChatToolAction.Finder -> "\u89c6\u9891\u53f7"
        FloatingChatToolAction.MomentMaterials -> "\u670b\u53cb\u5708\u7d20\u6750"
        FloatingChatToolAction.QuickPhrase -> "\u5feb\u6377\u8bed"
        FloatingChatToolAction.Voice -> "\u8bed\u97f3"
        FloatingChatToolAction.VoiceCall -> "\u8bed\u97f3\u901a\u8bdd"
        FloatingChatToolAction.VideoCall -> "\u89c6\u9891\u901a\u8bdd"
        FloatingChatToolAction.Device -> "\u8bbe\u5907"
        FloatingChatToolAction.Notes -> "\u7b14\u8bb0"
        FloatingChatToolAction.Wallet -> "\u94b1\u5305"
        FloatingChatToolAction.Search -> "\u641c\u7d22"
        FloatingChatToolAction.Pin -> "\u7f6e\u9876"
        FloatingChatToolAction.Translate -> "\u7ffb\u8bd1"
        FloatingChatToolAction.Screenshot -> "\u622a\u56fe"
        FloatingChatToolAction.Reminder -> "\u63d0\u9192"
        FloatingChatToolAction.UiComponents -> "UI\u7ec4\u4ef6"
        FloatingChatToolAction.MiniProgram -> "\u5fae\u4fe1\u5c0f\u7a0b\u5e8f"
        FloatingChatToolAction.ReviewRequests -> "\u7533\u8bf7\u5ba1\u6838"
        FloatingChatToolAction.HiddenUsers -> "\u9690\u85cf\u7528\u6237"
        FloatingChatToolAction.SendName -> "\u643a\u5e26\u540d\u5b57"
        FloatingChatToolAction.Command -> "\u6307\u4ee4"
        FloatingChatToolAction.Share -> "\u5206\u4eab"
    }
}

/** Labels shown in the right SCRM workflow rail. The action enum remains the dispatch key. */
internal fun rightRailWorkflowLabel(action: FloatingChatToolAction): String {
    return when (action) {
        FloatingChatToolAction.Assistant -> "AIFF流程"
        FloatingChatToolAction.Search -> "筛选KOL"
        FloatingChatToolAction.Contacts -> "沟通策略"
        FloatingChatToolAction.Notes -> "人脉画像"
        FloatingChatToolAction.Card -> "人设策略"
        FloatingChatToolAction.Reminder -> "择机熟络"
        FloatingChatToolAction.QuickPhrase -> "请身帮腔"
        FloatingChatToolAction.MomentMaterials -> "人脉管理"
        FloatingChatToolAction.Finder -> "视频号"
        FloatingChatToolAction.Wallet -> "快捷回复"
        FloatingChatToolAction.Share -> "群发计划"
        FloatingChatToolAction.Pin -> "记忆编辑"
        FloatingChatToolAction.Transfer -> "知识编辑"
        FloatingChatToolAction.Favorite -> "抢单换量"
        FloatingChatToolAction.Device -> "标价"
        FloatingChatToolAction.Gallery -> "投流"
        FloatingChatToolAction.Voice -> "钱包"
        FloatingChatToolAction.Files -> "文件"
        else -> toolActionLabel(action)
    }
}

internal fun toolActionIcon(action: FloatingChatToolAction): ImageVector {
    return when (action) {
        FloatingChatToolAction.Contacts -> Icons.Filled.Contacts
        FloatingChatToolAction.Assistant -> Icons.Filled.SmartToy
        FloatingChatToolAction.AiVoice -> Icons.Filled.Mic
        FloatingChatToolAction.Blink -> Icons.Filled.Visibility
        FloatingChatToolAction.Gallery -> Icons.Filled.Image
        FloatingChatToolAction.Video -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.ChannelsVideo -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.ChannelsLive -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.WebLink -> Icons.Filled.Article
        FloatingChatToolAction.Camera -> Icons.Filled.CameraAlt
        FloatingChatToolAction.Location -> Icons.Filled.LocationOn
        FloatingChatToolAction.Favorite -> Icons.Filled.Collections
        FloatingChatToolAction.RedPacket -> Icons.Filled.CardGiftcard
        FloatingChatToolAction.Transfer -> Icons.Filled.AttachMoney
        FloatingChatToolAction.Files -> Icons.Filled.Article
        FloatingChatToolAction.Card -> Icons.Filled.CreditCard
        FloatingChatToolAction.GroupInvite -> Icons.Filled.Contacts
        FloatingChatToolAction.Relay -> Icons.Filled.Article
        FloatingChatToolAction.SideEffect -> Icons.Filled.AutoAwesome
        FloatingChatToolAction.Moments -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.Finder -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.MomentMaterials -> Icons.Filled.Collections
        FloatingChatToolAction.QuickPhrase -> Icons.Filled.Textsms
        FloatingChatToolAction.Voice -> Icons.Filled.Mic
        FloatingChatToolAction.VoiceCall -> Icons.Filled.Mic
        FloatingChatToolAction.VideoCall -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.Device -> Icons.Filled.Checklist
        FloatingChatToolAction.Notes -> Icons.Filled.Article
        FloatingChatToolAction.Wallet -> Icons.Filled.CardGiftcard
        FloatingChatToolAction.Search -> Icons.Filled.CheckCircle
        FloatingChatToolAction.Pin -> Icons.Filled.Star
        FloatingChatToolAction.Translate -> Icons.Filled.FormatQuote
        FloatingChatToolAction.Screenshot -> Icons.Filled.Image
        FloatingChatToolAction.Reminder -> Icons.Filled.Notifications
        FloatingChatToolAction.UiComponents -> Icons.Filled.AutoAwesome
        FloatingChatToolAction.MiniProgram -> Icons.Filled.Article
        FloatingChatToolAction.ReviewRequests -> Icons.Filled.Checklist
        FloatingChatToolAction.HiddenUsers -> Icons.Filled.Contacts
        FloatingChatToolAction.SendName -> Icons.Filled.AccountBox
        FloatingChatToolAction.Command -> Icons.Filled.Checklist
        FloatingChatToolAction.Share -> Icons.AutoMirrored.Filled.Forward
    }
}
