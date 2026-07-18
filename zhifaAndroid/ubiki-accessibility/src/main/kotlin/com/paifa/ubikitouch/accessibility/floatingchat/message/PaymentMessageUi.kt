package com.paifa.ubikitouch.accessibility.floatingchat.message

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.visibleMessagesForThread
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import java.util.Locale
import kotlinx.coroutines.delay

private enum class PaymentCardKind(val label: String) {
    RedPacket("红包"),
    Transfer("转账")
}

@Composable
internal fun PaymentCardContent(
    message: FloatingChatMessage,
    claimed: Boolean
) {
    val kind = paymentCardKindFor(message.resourceUrl, message.appName, message.text) ?: PaymentCardKind.RedPacket
    val isTransfer = kind == PaymentCardKind.Transfer
    val title = if (isTransfer) {
        paymentCardAmountTextFor(message.text)
    } else {
        message.detail?.ifBlank { null } ?: "恭喜发财，大吉大利"
    }
    val subtitle = if (isTransfer) {
        message.detail?.ifBlank { null } ?: paymentCardTransferSubtitle()
    } else {
        null
    }
    val footer = if (claimed) {
        paymentCardClaimedStatusLabel(isTransfer)
    } else if (isTransfer) {
        "转账"
    } else {
        paymentCardRedPacketFooter()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = paymentCardMinHeightDp().dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTransfer) {
                TransferPaymentGlyph(modifier = Modifier.size(paymentCardGlyphSizeDp().dp))
            } else {
                RedPacketPaymentGlyph(
                    modifier = Modifier.size(
                        width = paymentCardGlyphSizeDp().dp,
                        height = (paymentCardGlyphSizeDp() + 6).dp
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                TextLabel(
                    text = title,
                    size = paymentCardTitleTextSizeSp().sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.paymentCardText,
                    lineHeight = 16.sp,
                    maxLines = if (isTransfer) 1 else 2
                )
                subtitle?.let {
                    TextLabel(
                        text = it,
                        size = 11.sp,
                        weight = FontWeight.Normal,
                        color = OverlayTokens.paymentCardText,
                        maxLines = 1
                    )
                }
            }
        }
        TextLabel(
            text = footer,
            size = paymentCardFooterTextSizeSp().sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.paymentCardFooterText,
            maxLines = 1
        )
    }
}

@Composable
internal fun PaymentDetailOverlay(
    message: FloatingChatMessage,
    selectedThread: ChatThreadSelection,
    selectedAccount: FloatingChatContact,
    claimed: Boolean,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = paymentCardKindFor(message.resourceUrl, message.appName, message.text) ?: PaymentCardKind.RedPacket
    val isTransfer = kind == PaymentCardKind.Transfer
    val amountText = if (isTransfer) {
        transferAmountTextFor(message.text)
    } else {
        redPacketAmountTextFor(message.text)
    }
    var claiming by remember(message.id) { mutableStateOf(false) }
    val claimProgress by animateFloatAsState(
        targetValue = if (claiming) 1f else 0f,
        animationSpec = tween(durationMillis = redPacketClaimAnimationDurationMs()),
        label = "redPacketClaimProgress"
    )
    val canClaimRedPacket = !isTransfer && redPacketCanClaimInThread(
        fromMe = message.fromMe,
        selectedThread = selectedThread
    )
    val canClaimTransfer = isTransfer && transferCanClaimInThread(
        message = message,
        selectedThread = selectedThread,
        selectedAccount = selectedAccount
    )
    LaunchedEffect(claiming, claimed) {
        if (claimed) {
            claiming = false
            return@LaunchedEffect
        }
        if (claiming) {
            delay(redPacketClaimAnimationDurationMs().toLong())
            onClaim()
            claiming = false
        }
    }
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(message.id) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OverlayTokens.paymentCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTransfer) {
                        TransferPaymentGlyph(
                            modifier = Modifier
                                .size(31.dp)
                                .graphicsLayer {
                                    val pulse = claimProgress * (1f - claimProgress)
                                    scaleX = 1f + pulse * 0.2f
                                    scaleY = 1f + pulse * 0.2f
                                }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            tint = OverlayTokens.paymentCardText,
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    val pulse = claimProgress * (1f - claimProgress)
                                    scaleX = 1f + pulse * 0.34f
                                    scaleY = 1f + pulse * 0.34f
                                    rotationZ = claimProgress * 10f
                                }
                        )
                    }
                }
                TextLabel(
                    text = if (isTransfer) "转账" else paymentCardRedPacketFooter(),
                    size = 15.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                TextLabel(
                    text = if (isTransfer) {
                        message.detail?.ifBlank { null } ?: paymentCardTransferSubtitle()
                    } else {
                        message.detail?.ifBlank { null } ?: "恭喜发财，大吉大利"
                    },
                    size = 12.sp,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
                TextLabel(
                    text = amountText,
                    size = 28.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (isTransfer) {
                    TextLabel(
                        text = if (claimed) {
                            paymentCardClaimedStatusLabel(isTransfer = true)
                        } else if (!canClaimTransfer && !message.fromMe) {
                            transferOnlyRecipientCanClaimLabel()
                        } else {
                            transferDetailStatusLabel(message.fromMe)
                        },
                        size = 11.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            if (!claimed && canClaimTransfer) {
                                onClaim()
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (claimed || !canClaimTransfer) {
                                OverlayTokens.control
                            } else {
                                OverlayTokens.paymentCard
                            },
                            contentColor = OverlayTokens.paymentCardText
                        )
                    ) {
                        TextLabel(
                            text = if (!claimed && canClaimTransfer) "确认收款" else "完成",
                            size = 12.sp,
                            color = if (claimed || !canClaimTransfer) {
                                OverlayTokens.panelSecondaryText
                            } else {
                                OverlayTokens.paymentCardText
                            },
                            maxLines = 1
                        )
                    }
                } else if (canClaimRedPacket) {
                    Button(
                        onClick = {
                            if (!claimed && !claiming) {
                                claiming = true
                            }
                        },
                        enabled = !claimed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlayTokens.paymentCard,
                            contentColor = OverlayTokens.paymentCardText,
                            disabledContainerColor = OverlayTokens.control,
                            disabledContentColor = OverlayTokens.panelSecondaryText
                        )
                    ) {
                        TextLabel(
                            text = if (claiming) {
                                "领取中..."
                            } else {
                                redPacketClaimButtonLabel(claimed = claimed, amountText = amountText)
                            },
                            size = 12.sp,
                            color = if (claimed) OverlayTokens.panelSecondaryText else OverlayTokens.paymentCardText,
                            maxLines = 1
                        )
                    }
                } else {
                    TextLabel(
                        text = "已发送，仅查看金额",
                        size = 11.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

internal fun paymentCardUsesWechatStyleLayout(): Boolean = true

internal fun paymentCardMinHeightDp(): Int = 52

internal fun paymentCardTitleTextSizeSp(): Int = 13

internal fun paymentCardFooterTextSizeSp(): Int = 10

internal fun paymentCardUsesNormalTextWeight(): Boolean = true

internal fun paymentCardTextUsesShadow(): Boolean = false

internal fun paymentCardGlyphSizeDp(): Int = 32

internal fun paymentCardOuterVerticalPaddingDp(): Int = 7

internal fun paymentCardTransferSubtitle(): String = "请收款"

internal fun paymentCardClickOpensAmountViewer(resourceUrl: String?, appName: String?, text: String): Boolean {
    return paymentCardKindFor(resourceUrl, appName, text) != null
}

internal fun transferCardClickOpensAmountViewer(): Boolean = true

internal fun transferAmountTextFor(text: String): String = paymentCardAmountTextFor(text)

internal fun transferDetailStatusLabel(fromMe: Boolean): String {
    return if (fromMe) "待收款" else "待确认"
}

internal fun transferOnlyRecipientCanClaimLabel(): String = "仅指定收款人可收款"

internal fun transferPanelSupportsGroupRecipientSelection(): Boolean = true

internal fun transferMessageDetailForRecipient(recipientName: String?, note: String): String {
    val safeNote = note.trim()
    val safeRecipientName = recipientName?.trim().orEmpty()
    return when {
        safeRecipientName.isBlank() -> safeNote.ifBlank { "转账给你，请查收" }
        safeNote.isBlank() -> "转账给 $safeRecipientName"
        else -> "转账给 $safeRecipientName：$safeNote"
    }
}

internal fun transferResourceUrlWithRecipient(resourceUrl: String?, recipientId: String?): String? {
    val base = resourceUrl?.ifBlank { null } ?: return null
    val safeRecipientId = recipientId?.trim().orEmpty()
    if (safeRecipientId.isBlank()) return base
    val separator = if (base.contains("?")) "&" else "?"
    return "$base${separator}recipient=$safeRecipientId"
}

internal fun transferCanClaimInThread(
    message: FloatingChatMessage,
    selectedThread: ChatThreadSelection,
    selectedAccount: FloatingChatContact
): Boolean {
    if (paymentCardKindFor(message.resourceUrl, message.appName, message.text) != PaymentCardKind.Transfer) {
        return false
    }
    if (message.fromMe && message.connectionTargetId == selectedAccount.id) {
        return false
    }

    val recipientId = transferRecipientIdFromResourceUrl(message.resourceUrl)
    if (!recipientId.isNullOrBlank()) {
        return recipientId == selectedAccount.id
    }

    return when (selectedThread) {
        ChatThreadSelection.Group -> false
        is ChatThreadSelection.GroupChat -> {
            !message.fromMe && message.cardName?.trim() == selectedAccount.name.trim()
        }
        is ChatThreadSelection.Private -> !message.fromMe
    }
}

private fun transferRecipientIdFromResourceUrl(resourceUrl: String?): String? {
    val uri = runCatching {
        resourceUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }.getOrNull() ?: return null
    return uri.getQueryParameter("recipient")?.takeIf { it.isNotBlank() }
}

internal fun transferRecipientCandidatesForThread(
    conversation: FloatingChatConversation,
    selectedThread: ChatThreadSelection,
    selectedAccountId: String
): List<FloatingChatContact> {
    if (selectedThread is ChatThreadSelection.Private) return emptyList()
    val accountIds = conversation.accountContacts.map { account -> account.id }.toSet()
    val contactsById = conversation.contacts.associateBy { contact -> contact.id }
    val seenIds = LinkedHashSet<String>()
    return visibleMessagesForThread(
        conversation = conversation,
        selection = selectedThread,
        selectedAccountId = selectedAccountId
    ).mapNotNull { message ->
        val targetId = message.connectionTargetId
            ?.takeIf { id -> id.isNotBlank() && id !in accountIds && id != selectedAccountId }
            ?: return@mapNotNull null
        if (message.connectionTarget != FloatingChatConnectionTarget.User) return@mapNotNull null
        if (!seenIds.add(targetId)) return@mapNotNull null
        contactsById[targetId] ?: FloatingChatContact(
            id = targetId,
            name = message.senderName,
            initials = message.senderName.take(2).ifBlank { "收款" },
            description = "群聊成员",
            avatarColor = 0xFF6D8190
        )
    }
}

internal fun paymentCardRedPacketFooter(): String = "红包"

internal fun paymentCardClaimedStatusLabel(isTransfer: Boolean): String {
    return if (isTransfer) "已收款" else "已领取"
}

internal fun redPacketCardClickOpensAmountViewer(): Boolean = true

internal fun redPacketAmountTextFor(text: String): String = paymentCardAmountTextFor(text)

internal fun redPacketCanClaimInThread(fromMe: Boolean, selectedThread: ChatThreadSelection): Boolean {
    return !fromMe || selectedThread is ChatThreadSelection.Group || selectedThread is ChatThreadSelection.GroupChat
}

internal fun redPacketClaimUsesAnimatedState(): Boolean = true

internal fun redPacketClaimAnimationDurationMs(): Int = 420

internal fun redPacketClaimButtonLabel(claimed: Boolean, amountText: String): String {
    return if (claimed) "已领取 $amountText" else "领取红包"
}

internal fun paymentCardAmountTextFor(text: String): String {
    val amount = Regex("""[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?: Regex("""([0-9]+(?:\.[0-9]{1,2})?)""")
            .find(text)
            ?.value
        ?: "0.00"
    return "¥$amount"
}

internal fun paymentCardKindLabelFor(resourceUrl: String?, appName: String?, text: String): String? {
    return paymentCardKindFor(resourceUrl, appName, text)?.label
}

private fun paymentCardKindFor(resourceUrl: String?, appName: String?, text: String): PaymentCardKind? {
    val marker = listOfNotNull(resourceUrl, appName, text).joinToString(" ").lowercase(Locale.US)
    return when {
        marker.contains("red-packet") || marker.contains("红包") -> PaymentCardKind.RedPacket
        marker.contains("transfer") || marker.contains("转账") -> PaymentCardKind.Transfer
        else -> null
    }
}

internal fun FloatingChatMessage.isPaymentCardMessage(): Boolean {
    return paymentCardKindFor(resourceUrl, appName, text) != null
}
