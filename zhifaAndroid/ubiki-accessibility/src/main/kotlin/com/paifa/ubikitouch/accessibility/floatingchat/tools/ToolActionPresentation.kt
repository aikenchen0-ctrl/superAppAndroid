package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachMoney
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
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal fun toolActionLabel(action: FloatingChatToolAction): String {
    return when (action) {
        FloatingChatToolAction.Contacts -> "\u8054\u7cfb\u4eba"
        FloatingChatToolAction.Assistant -> "\u673a\u5668\u4eba"
        FloatingChatToolAction.AiVoice -> "AI\u8bed\u97f3"
        FloatingChatToolAction.Blink -> "\u7728\u773c"
        FloatingChatToolAction.Gallery -> "\u76f8\u518c"
        FloatingChatToolAction.Camera -> "\u6444\u5f71"
        FloatingChatToolAction.Location -> "\u4f4d\u7f6e"
        FloatingChatToolAction.Favorite -> "\u6536\u85cf"
        FloatingChatToolAction.RedPacket -> "\u7ea2\u5305"
        FloatingChatToolAction.Transfer -> "\u8f6c\u8d26"
        FloatingChatToolAction.Files -> "\u6587\u6863"
        FloatingChatToolAction.Card -> "\u63a8\u540d\u7247"
        FloatingChatToolAction.Moments -> "\u670b\u53cb\u5708"
        FloatingChatToolAction.MomentMaterials -> "\u670b\u53cb\u5708\u7d20\u6750"
        FloatingChatToolAction.QuickPhrase -> "\u5feb\u6377\u8bed"
        FloatingChatToolAction.Voice -> "\u8bed\u97f3"
        FloatingChatToolAction.Device -> "\u8bbe\u5907"
        FloatingChatToolAction.Notes -> "\u7b14\u8bb0"
        FloatingChatToolAction.Wallet -> "\u94b1\u5305"
        FloatingChatToolAction.Search -> "\u641c\u7d22"
        FloatingChatToolAction.Pin -> "\u7f6e\u9876"
        FloatingChatToolAction.Translate -> "\u7ffb\u8bd1"
        FloatingChatToolAction.Screenshot -> "\u622a\u56fe"
        FloatingChatToolAction.Reminder -> "\u63d0\u9192"
        FloatingChatToolAction.Command -> "\u6307\u4ee4"
        FloatingChatToolAction.Share -> "\u5206\u4eab"
    }
}

internal fun toolActionIcon(action: FloatingChatToolAction): ImageVector {
    return when (action) {
        FloatingChatToolAction.Contacts -> Icons.Filled.Contacts
        FloatingChatToolAction.Assistant -> Icons.Filled.SmartToy
        FloatingChatToolAction.AiVoice -> Icons.Filled.Mic
        FloatingChatToolAction.Blink -> Icons.Filled.Visibility
        FloatingChatToolAction.Gallery -> Icons.Filled.Image
        FloatingChatToolAction.Camera -> Icons.Filled.CameraAlt
        FloatingChatToolAction.Location -> Icons.Filled.LocationOn
        FloatingChatToolAction.Favorite -> Icons.Filled.Collections
        FloatingChatToolAction.RedPacket -> Icons.Filled.CardGiftcard
        FloatingChatToolAction.Transfer -> Icons.Filled.AttachMoney
        FloatingChatToolAction.Files -> Icons.Filled.Article
        FloatingChatToolAction.Card -> Icons.Filled.CreditCard
        FloatingChatToolAction.Moments -> Icons.Filled.VideoLibrary
        FloatingChatToolAction.MomentMaterials -> Icons.Filled.Collections
        FloatingChatToolAction.QuickPhrase -> Icons.Filled.Textsms
        FloatingChatToolAction.Voice -> Icons.Filled.Mic
        FloatingChatToolAction.Device -> Icons.Filled.Checklist
        FloatingChatToolAction.Notes -> Icons.Filled.Article
        FloatingChatToolAction.Wallet -> Icons.Filled.CardGiftcard
        FloatingChatToolAction.Search -> Icons.Filled.CheckCircle
        FloatingChatToolAction.Pin -> Icons.Filled.Star
        FloatingChatToolAction.Translate -> Icons.Filled.FormatQuote
        FloatingChatToolAction.Screenshot -> Icons.Filled.Image
        FloatingChatToolAction.Reminder -> Icons.Filled.Notifications
        FloatingChatToolAction.Command -> Icons.Filled.Checklist
        FloatingChatToolAction.Share -> Icons.AutoMirrored.Filled.Forward
    }
}
