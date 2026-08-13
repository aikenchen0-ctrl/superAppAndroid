@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.account.*
import com.paifa.ubikitouch.accessibility.floatingchat.input.*
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCoordinator
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceApiConfig
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCallRuntime
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AndroidAiVoiceAudioEngine
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiDraftMessageActions
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.DoubaoVoiceTtsApi
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.DoubaoVoiceCloneApi
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.VoiceCloneRecorder
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.VoiceCloneReferenceText
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.VoiceProfileStep
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoicePanel
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiConfigTestActions
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiDraftOverlayHost
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.MessageAsideAnalysisActions
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.MessageAsideAnalysisState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.BlinkInputAiActions
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.FloatingChatBlinkInputEffects
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiDraftGenerationActions
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.editedAiDraftMessage
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.OkHttpAiVoiceRealtimeClient
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.DoubaoRealtimeCredentials
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.probeDoubaoRealtime
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.RealtimeCallState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.withTranscript
import com.paifa.ubikitouch.accessibility.floatingchat.chat.SessionRailItem
import com.paifa.ubikitouch.accessibility.floatingchat.chat.sessionRailItemsByLatestChatTime
import com.paifa.ubikitouch.accessibility.floatingchat.chat.*
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.*
import com.paifa.ubikitouch.accessibility.floatingchat.group.*
import com.paifa.ubikitouch.accessibility.floatingchat.tools.*
import com.paifa.ubikitouch.accessibility.floatingchat.message.*
import com.paifa.ubikitouch.accessibility.floatingchat.media.*
import com.paifa.ubikitouch.accessibility.floatingchat.moments.*
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderSession
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderWorkspaceView
import com.paifa.ubikitouch.accessibility.floatingchat.finder.ScrmFinderApi
import com.paifa.ubikitouch.accessibility.floatingchat.finder.finderUserNameForMessage
import com.paifa.ubikitouch.accessibility.floatingchat.finder.isFinderMessage
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingBottomPanel
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatConversationSyncEffects
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatInternalEdgeGestureDefaults
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPreviewChromeEffects
import com.paifa.ubikitouch.accessibility.floatingchat.shell.floatingChatFrostedBackdrop
import com.paifa.ubikitouch.accessibility.floatingchat.shell.floatingChatOverlayGestureBinding
import com.paifa.ubikitouch.accessibility.floatingchat.shell.isCenteredToolFeaturePanel
import com.paifa.ubikitouch.accessibility.floatingchat.shell.isBottomComposerDrawer
import com.paifa.ubikitouch.accessibility.floatingchat.shell.rememberFloatingChatMediaOverlayState
import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Dp.Companion.Unspecified
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.EdgeZoneConfig
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import com.paifa.ubikitouch.core.model.FloatingChatVisibilityScope
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatExpandedBottomGestureBar
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatRuntimeSections
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatHeaderIcon
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatUnreadDot
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatImageActionPill
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatMediaStatusPill
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatTinyChip
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatLocationGlyph
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatVideoTimelineSlider
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatMiniVideoControlButton
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatVideoPlayerControlBar
import com.paifa.ubikitouch.accessibility.floatingchat.media.DocumentReaderStatus
import com.paifa.ubikitouch.accessibility.floatingchat.message.RedPacketPaymentGlyph as MessageRedPacketPaymentGlyph
import com.paifa.ubikitouch.accessibility.floatingchat.message.TransferPaymentGlyph as MessageTransferPaymentGlyph
import com.paifa.ubikitouch.accessibility.floatingchat.message.isTransferPaymentCard
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.accessibility.data.LocalMomentComment
import com.paifa.ubikitouch.accessibility.data.LocalMomentPost
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.data.localThreadIdForSelection
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.AndroidScrmMediaContentResolver
import com.paifa.ubikitouch.accessibility.scrm.ScrmMediaUploadRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessageApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialDetail
import com.paifa.ubikitouch.accessibility.scrm.ScrmQueuedMediaPayload
import com.paifa.ubikitouch.accessibility.scrm.ScrmRequestException
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncMomentMessagesRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskPollState
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmPaymentApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmRedPacketQueryByMessageRequest
import com.paifa.ubikitouch.accessibility.scrm.PaymentDetailParser
import com.paifa.ubikitouch.accessibility.scrm.PaymentIdempotencyRegistry
import com.paifa.ubikitouch.accessibility.scrm.PaymentMessageIds
import com.paifa.ubikitouch.accessibility.scrm.PaymentReadback
import com.paifa.ubikitouch.accessibility.scrm.PaymentReadbackState
import com.paifa.ubikitouch.accessibility.scrm.PaymentRequestFactory
import com.paifa.ubikitouch.accessibility.scrm.PaymentTaskRunner
import com.paifa.ubikitouch.accessibility.scrm.PaymentTaskState
import com.paifa.ubikitouch.accessibility.scrm.toPaymentReadback
import com.paifa.ubikitouch.accessibility.scrm.scrmContactsPanelRouteForSelectedAccount
import com.paifa.ubikitouch.accessibility.scrm.resolveScrmTaskResult
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountRouteForContactId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingScopedThreadId
import com.paifa.ubikitouch.accessibility.scrm.scrmMessageOperationType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class FloatingChatHeaderRoute {
    AllAccountsUnread,
    SingleAccountUnread,
    Conversation
}

private const val FloatingChatHeaderRestoreDelayMillis = 1_000L

internal data class FloatingChatHeaderState(
    val leadingLabel: String,
    val title: String,
    val showUnreadDot: Boolean,
    val showEdit: Boolean,
    val compact: Boolean
)

internal fun floatingChatHeaderState(
    route: FloatingChatHeaderRoute,
    accountName: String,
    conversationTitle: String,
    unreadCount: Int,
    messageScrollInProgress: Boolean,
    editable: Boolean
): FloatingChatHeaderState {
    return when (route) {
        FloatingChatHeaderRoute.AllAccountsUnread -> FloatingChatHeaderState(
            leadingLabel = "返回",
            title = "全部未回消息",
            showUnreadDot = false,
            showEdit = false,
            compact = false
        )
        FloatingChatHeaderRoute.SingleAccountUnread -> FloatingChatHeaderState(
            leadingLabel = "返回",
            title = "${accountName.ifBlank { "当前账号" }}的未回消息",
            showUnreadDot = false,
            showEdit = false,
            compact = false
        )
        FloatingChatHeaderRoute.Conversation -> FloatingChatHeaderState(
            leadingLabel = if (unreadCount > 0) "未回 $unreadCount" else "未回",
            title = conversationTitle.ifBlank { "消息" },
            showUnreadDot = true,
            showEdit = editable,
            compact = messageScrollInProgress
        )
    }
}

@Composable
internal fun FloatingChatWorkspaceHeader(
    state: FloatingChatHeaderState,
    accountName: String,
    onLeadingClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onScanClick: () -> Unit,
    onAddFriendClick: () -> Unit
) {
    var utilityMenuExpanded by remember { mutableStateOf(false) }
    val compactOffset by animateFloatAsState(
        targetValue = if (state.compact) -76f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "floating-chat-header-compact"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((chatStatusBarHeightDp() + chatToolbarHeightDp()).dp)
            .background(OverlayTokens.toolbarSurface)
            .padding(start = 8.dp, top = chatStatusBarHeightDp().dp, end = 8.dp)
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = compactOffset.dp.toPx()
                    alpha = if (state.compact) 0f else 1f
                }
                .clickable(onClick = onLeadingClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = state.leadingLabel,
                tint = OverlayTokens.panelPrimaryText,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = state.leadingLabel,
                color = OverlayTokens.panelPrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (state.showUnreadDot) FloatingChatUnreadDot()
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { translationX = compactOffset.dp.toPx() }
                .padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title,
                modifier = Modifier.weight(1f),
                color = OverlayTokens.panelPrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                    text = accountName,
                modifier = Modifier.width(62.dp),
                color = OverlayTokens.panelSecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.showEdit) {
                FloatingChatHeaderIcon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑会话备注",
                    onClick = onEditClick
                )
            }
            FloatingChatHeaderIcon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索聊天记录",
                onClick = onSearchClick
            )
            Box {
                FloatingChatHeaderIcon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "扫一扫与添加朋友",
                    onClick = { utilityMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = utilityMenuExpanded,
                    onDismissRequest = { utilityMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("扫一扫") },
                        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                        onClick = {
                            utilityMenuExpanded = false
                            onScanClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加朋友") },
                        leadingIcon = { Icon(Icons.Filled.PersonAddAlt1, contentDescription = null) },
                        onClick = {
                            utilityMenuExpanded = false
                            onAddFriendClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingChatOverlay(
    conversation: FloatingChatConversation,
    frostedBackgroundEnabled: Boolean = defaultFloatingChatFrostedBackgroundEnabled(),
    backgroundOpacityPercent: Int = defaultFloatingChatBackgroundOpacityPercent(),
    blurRadiusDp: Int = defaultFloatingChatBlurRadiusDp(),
    backgroundColorRgb: Int = defaultFloatingChatBackgroundColorRgb(),
    initialLocalMessages: List<FloatingChatMessage> = emptyList(),
    initialMessageSequence: Int = 0,
    initialMomentPosts: List<AppMomentPost> = emptyList(),
    initialContactProfiles: List<LocalContactProfile> = emptyList(),
    initialGroupProfiles: List<LocalGroupProfile> = emptyList(),
    initialSelectedAccountId: String? = null,
    onLocalMessagesChanged: (List<FloatingChatMessage>, Int) -> Unit = { _, _ -> },
    onPrepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage = { message, _ -> message },
    onPersistLocalMessage: (FloatingChatMessage, String) -> Unit = { _, _ -> },
    onPersistForwardedMessage: (FloatingChatMessage, FloatingChatMessage, String, String?) -> Unit = { _, _, _, _ -> },
    onPersistMomentPost: (AppMomentPost) -> Unit = {},
    onPersistContactProfile: (LocalContactProfile) -> Unit = {},
    onPersistGroupProfile: (LocalGroupProfile) -> Unit = {},
    onThreadContextChanged: (ChatThreadSelection, String) -> Unit = { _, _ -> },
    onOpenExternalDocument: (FloatingChatMessage) -> Boolean = { false },
    onPreviewChromeChanged: (Boolean) -> Unit = {},
    edgeGestureShortThresholdDp: Int = FloatingChatInternalEdgeGestureDefaults.ShortThresholdDp,
    edgeGestureLongThresholdDp: Int = FloatingChatInternalEdgeGestureDefaults.LongThresholdDp,
    leftEdgeConfigs: List<EdgeZoneConfig> = emptyList(),
    rightEdgeConfigs: List<EdgeZoneConfig> = emptyList(),
    onEdgeGesture: (EdgeSide, GestureType, GestureData) -> Unit = { _, _, _ -> },
    onBottomGesture: (BottomGestureBarGestureType, GestureData) -> Unit = { _, _ -> },
    onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit = { _, _ -> },
    onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean = { _, _, _ -> false },
    onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit = { _, _ -> },
    onBackGestureCancel: () -> Unit = {},
    voicePermissionRequestToken: Int = 0,
    locationPermissionRequestToken: Int = 0,
    runtimeState: FloatingChatOverlayRuntimeState = FloatingChatOverlayRuntimeState(),
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var liveConversation by remember { mutableStateOf(conversation) }
    var inputText by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    var bottomPanelMode by remember { mutableStateOf(BottomPanelMode.None) }
    var displayedBottomPanelMode by remember { mutableStateOf(BottomPanelMode.None) }
    var contactsOpenAddFriend by remember { mutableStateOf(false) }
    var displayedContactsOpenAddFriend by remember { mutableStateOf(false) }
    val bottomPanelVisibility = remember { MutableTransitionState(false) }
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    LaunchedEffect(bottomPanelMode) {
        if (bottomPanelMode != BottomPanelMode.None) {
            displayedBottomPanelMode = bottomPanelMode
        }
        if (bottomPanelMode == BottomPanelMode.Contacts) {
            displayedContactsOpenAddFriend = contactsOpenAddFriend
        }
        bottomPanelVisibility.targetState = bottomPanelMode != BottomPanelMode.None
        if (bottomPanelMode != BottomPanelMode.Contacts) {
            contactsOpenAddFriend = false
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible && bottomPanelMode.isBottomComposerDrawer()) {
            bottomPanelMode = BottomPanelMode.None
        }
    }
    val aiVoiceApiConfig = remember {
        AiVoiceApiConfig(
            gatewayBaseUrl = BuildConfig.AI_VOICE_GATEWAY_BASE_URL,
            sessionToken = BuildConfig.AI_VOICE_SESSION_TOKEN
        )
    }
    val aiVoiceCoordinator = remember(aiVoiceApiConfig) {
        AiVoiceCoordinator(
            hasVoiceProfile = false,
            apiConfig = aiVoiceApiConfig
        )
    }
    var aiVoiceState by remember { mutableStateOf<AiVoiceState>(AiVoiceState.Menu) }
    var aiVoiceRuntime by remember { mutableStateOf<AiVoiceCallRuntime?>(null) }
    val doubaoVoiceTtsApi = remember { DoubaoVoiceTtsApi() }
    val doubaoVoiceCloneApi = remember { DoubaoVoiceCloneApi() }
    val voiceCloneRecorder = remember(context) { VoiceCloneRecorder(context) }
    var aiVoiceCapabilityConfigState by remember { mutableStateOf(AiVoiceCapabilityConfigState()) }
    var aiConfig by remember(context) { mutableStateOf(loadFloatingChatAiConfig(context)) }
    var aiConfigStatus by remember { mutableStateOf<String?>(null) }
    var aiPredicting by remember { mutableStateOf(false) }
    var blinkInputAiBusy by remember { mutableStateOf(false) }
    var blinkInputStatusText by remember { mutableStateOf<String?>(null) }
    var blinkInputStatusAutoDismiss by remember { mutableStateOf(false) }
    var blinkInputStatusVersion by remember { mutableIntStateOf(0) }
    var blinkGeneratedInputClearable by remember { mutableStateOf(false) }
    var aiConfigTesting by remember { mutableStateOf(false) }
    var aiDraftActionMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var aiDraftEditMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    val mediaOverlayState = rememberFloatingChatMediaOverlayState()
    var longPressMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var longPressAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var asideAnalysisState by remember { mutableStateOf<MessageAsideAnalysisState?>(null) }
    var textZoomMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var paymentDetailMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var paymentReadback by remember { mutableStateOf(PaymentReadback()) }
    var paymentOperationStatus by remember { mutableStateOf<String?>(null) }
    var paymentOperationInProgress by remember { mutableStateOf(false) }
    val paymentIdempotencyRegistry = remember { PaymentIdempotencyRegistry() }
    var forwardMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var forwardModeMessages by remember { mutableStateOf<List<FloatingChatMessage>>(emptyList()) }
    var pendingForwardMessages by remember { mutableStateOf<List<FloatingChatMessage>>(emptyList()) }
    var pendingForwardMode by remember { mutableStateOf<MultiForwardMode?>(null) }
    var chatHistoryPreviewMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var quotedMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var favoritePreviewItem by remember { mutableStateOf<FavoriteCollectionItem?>(null) }
    var favoriteLongPressItem by remember { mutableStateOf<FavoriteCollectionItem?>(null) }
    var favoriteLongPressAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var favoriteMultiSelectMode by remember { mutableStateOf(false) }
    var contactEditorTarget by remember { mutableStateOf<ContactEditorTarget?>(null) }
    var groupMemberAddFriendTargetId by remember { mutableStateOf<String?>(null) }
    var groupMemberAddFriendLoading by remember { mutableStateOf(false) }
    var groupMemberAddFriendStatus by remember { mutableStateOf<String?>(null) }
    var groupMemberAddFriendError by remember { mutableStateOf<String?>(null) }
    var accountEditorTarget by remember { mutableStateOf<FloatingChatContact?>(null) }
    var pendingAvatarAccountId by remember { mutableStateOf<String?>(null) }
    val favoriteMediaIds = mediaOverlayState.favoriteMediaIds
    val favoriteMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val reminderMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val claimedPaymentMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val sentAiDraftMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val selectedMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val selectedFavoriteItemIds = remember { mutableStateMapOf<String, Boolean>() }
    val hiddenMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val contactProfiles = remember(initialContactProfiles) {
        mutableStateMapOf<String, LocalContactProfile>().apply {
            initialContactProfiles.forEach { profile ->
                this[contactProfileKey(profile.accountId, profile.contactId)] = profile
            }
        }
    }
    val groupProfiles = remember(initialGroupProfiles) {
        mutableStateMapOf<String, LocalGroupProfile>().apply {
            initialGroupProfiles.forEach { profile ->
                this[groupProfileKey(profile.accountId, profile.groupId)] = profile
            }
        }
    }
    val accountProfiles = remember(context, liveConversation.accountContacts) {
        mutableStateMapOf<String, FloatingChatAccountProfile>().apply {
            liveConversation.accountContacts.forEach { account ->
                this[account.id] = loadAccountProfile(context, account)
            }
        }
    }
    val initialAccountScopeId = remember(initialSelectedAccountId, liveConversation.accountContacts) {
        initialSelectedAccountId
            ?.takeIf { accountId -> liveConversation.accountContacts.any { account -> account.id == accountId } }
            ?: liveConversation.accountContacts.firstOrNull { account -> account.selected }?.id
            ?: liveConversation.accountContacts.firstOrNull()?.id
            ?: ""
    }
    val loadedFavoriteItems = remember(context) { loadFavoriteCollectionItems(context) }
    val storedFavoriteItems = remember(context, initialAccountScopeId) {
        mutableStateListOf<FavoriteCollectionItem>().apply {
            addAll(
                loadedFavoriteItems.map { item ->
                    if (item.accountId == LegacyFavoriteAccountId && initialAccountScopeId.isNotBlank()) {
                        item.copy(accountId = initialAccountScopeId)
                    } else {
                        item
                    }
                }
            )
        }
    }
    var quickPhrases by remember(context) { mutableStateOf(loadQuickPhrases(context)) }
    val momentPosts = remember(initialMomentPosts, initialAccountScopeId) {
        mutableStateListOf<AppMomentPost>().apply {
            addAll(
                initialMomentPosts.map { post ->
                    if (post.accountId.isBlank() && initialAccountScopeId.isNotBlank()) {
                        post.copy(accountId = initialAccountScopeId)
                    } else {
                        post
                    }
                }
            )
        }
    }
    val pendingMomentMediaByAccount = remember { mutableStateMapOf<String, AppMomentMedia>() }
    LaunchedEffect(initialAccountScopeId) {
        if (initialAccountScopeId.isBlank()) return@LaunchedEffect
        if (loadedFavoriteItems.any { item -> item.accountId == LegacyFavoriteAccountId }) {
            saveFavoriteCollectionItems(context, storedFavoriteItems.toList())
        }
        momentPosts
            .filter { post ->
                post.accountId == initialAccountScopeId &&
                    initialMomentPosts.any { initial -> initial.id == post.id && initial.accountId.isBlank() }
            }
            .forEach(onPersistMomentPost)
    }
    val accountProfileList = accountProfiles.values.toList()
    val profiledConversation = remember(liveConversation, accountProfileList) {
        liveConversation.copy(
            accountContacts = liveConversation.accountContacts.map { account ->
                (accountProfiles[account.id] ?: defaultAccountProfileFor(account)).toContact(account)
            }
        )
    }
    val accountIds = remember(liveConversation.accountContacts) {
        liveConversation.accountContacts.map { account -> account.id }
    }
    var chatNavigationState by remember(runtimeState) {
        val restored = runtimeState.chatNavigationState
        val initialAccountId = restored.activeAccountId.takeIf(accountIds::contains)
            ?: initialSelectedAccountId?.takeIf(accountIds::contains)
            ?: profiledConversation.accountContacts.firstOrNull { account -> account.selected }?.id
            ?: profiledConversation.accountContacts.firstOrNull()?.id
            ?: ""
        val initialConversation = accountScopedConversation(profiledConversation, initialAccountId)
        mutableStateOf(
            restored.copy(
                activeAccountId = initialAccountId,
                selectedThread = initialChatThreadSelection(
                    conversation = initialConversation,
                    preferredSelection = restored.selectedThread
                )
            ).also { initial -> runtimeState.chatNavigationState = initial }
        )
    }
    fun updateChatNavigationState(next: ChatNavigationState) {
        chatNavigationState = next
        runtimeState.chatNavigationState = next
    }
    val activeAccountId = chatNavigationState.activeAccountId
    val selectedThread = chatNavigationState.selectedThread
    val homeOverviewVisible = chatNavigationState.route != ChatNavigationRoute.Conversation
    val homeOverviewAccountId = chatNavigationState.activeAccountId.takeIf {
        chatNavigationState.route == ChatNavigationRoute.SingleAccountUnread
    }
    LaunchedEffect(accountIds) {
        if (activeAccountId.isNotBlank() && activeAccountId in accountIds) return@LaunchedEffect
        val nextAccountId = initialSelectedAccountId
            ?.takeIf { accountId -> accountId in accountIds }
            ?: profiledConversation.accountContacts.firstOrNull { account -> account.selected }?.id
            ?: accountIds.firstOrNull()
            ?: ""
        val nextThread = initialChatThreadSelection(
            conversation = accountScopedConversation(profiledConversation, nextAccountId),
            preferredSelection = selectedThread
        )
        updateChatNavigationState(
            chatNavigationState.copy(
                activeAccountId = nextAccountId,
                selectedThread = nextThread,
                unreadSourceAccountId = null
            )
        )
    }
    val accountConversationCache = remember(profiledConversation) {
        AccountScopedConversationCache(profiledConversation)
    }
    val effectiveConversation = remember(accountConversationCache, activeAccountId) {
        accountConversationCache.conversationFor(activeAccountId)
    }
    val groupProfileList = groupProfiles.values.toList()
    val groupProfiledConversation = remember(effectiveConversation, groupProfileList, activeAccountId) {
        applyGroupProfilesToConversation(
            conversation = effectiveConversation,
            profiles = groupProfileList,
            accountId = activeAccountId
        )
    }
    val contactProfileList = contactProfiles.values.toList()
    val contactProfiledConversation = remember(groupProfiledConversation, contactProfileList) {
        applyContactProfilesToConversation(
            conversation = groupProfiledConversation,
            profiles = contactProfileList
        )
    }
    var finderUserPageRequestKey by remember { mutableIntStateOf(0) }
    var finderInitialUserName by remember { mutableStateOf<String?>(null) }
    var messageListScrolling by remember { mutableStateOf(false) }
    var headerCompact by remember { mutableStateOf(false) }
    var openChatSearchRequestKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(messageListScrolling, selectedThread, homeOverviewVisible) {
        if (homeOverviewVisible || selectedThread !is ChatThreadSelection.Private) {
            headerCompact = false
        } else if (messageListScrolling) {
            headerCompact = true
        } else {
            delay(FloatingChatHeaderRestoreDelayMillis)
            headerCompact = false
        }
    }
    val unreadThreadIds = remember { mutableStateMapOf<String, Boolean>() }
    val chatNavigationActions = ChatNavigationActions(
        unreadThreadIds = unreadThreadIds,
        state = { chatNavigationState },
        onStateChanged = ::updateChatNavigationState
    )
    val localMessages = remember(liveConversation, initialLocalMessages) {
        mutableStateListOf<FloatingChatMessage>().apply {
            addAll(initialLocalMessages)
        }
    }
    var localMessageSequence by remember(liveConversation, initialMessageSequence) {
        mutableStateOf(initialMessageSequence)
    }
    var localMessageVersion by remember(liveConversation) {
        mutableIntStateOf(0)
    }
    val selectedAccount = remember(contactProfiledConversation, selectedThread, activeAccountId) {
        selectedAccountForThread(
            conversation = contactProfiledConversation,
            selection = selectedThread,
            overrideAccountId = activeAccountId
        )
    }
    val currentMomentPosts = remember(momentPosts.toList(), selectedAccount.id) {
        momentPostsForAccount(momentPosts, selectedAccount.id)
    }
    val pendingMomentMedia = pendingMomentMediaByAccount[selectedAccount.id]
    val momentToolActions = MomentToolActions(
        accountId = { selectedAccount.id },
        momentPosts = momentPosts,
        runtimeState = runtimeState,
        onPersistMomentPost = onPersistMomentPost,
        onPendingMomentMediaChanged = { media ->
            if (media == null) {
                pendingMomentMediaByAccount.remove(selectedAccount.id)
            } else {
                pendingMomentMediaByAccount[selectedAccount.id] = media
            }
        }
    )
    val scrmProfileManager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val displayConversation = remember(contactProfiledConversation, localMessages.size, localMessageVersion, hiddenMessageIds.size) {
        applyContactProfilesToConversation(
            conversation = contactProfiledConversation.copy(
                messages = (contactProfiledConversation.messages + localMessages).filter { message ->
                    hiddenMessageIds[message.id] != true
                }
            ),
            profiles = contactProfileList
        )
    }
    val accountScopedDisplayConversations = remember(
        homeOverviewVisible,
        profiledConversation,
        contactProfileList,
        groupProfileList,
        localMessages.size,
        localMessageVersion,
        hiddenMessageIds.size
    ) {
        if (!shouldBuildAllAccountHomeOverview(homeOverviewVisible)) {
            return@remember emptyList<AccountScopedConversation>()
        }
        val visibleLocalMessages = localMessages.filter { message -> hiddenMessageIds[message.id] != true }
        accountScopedConversations(profiledConversation).map { scoped ->
            val groupAppliedConversation = applyGroupProfilesToConversation(
                conversation = scoped.conversation,
                profiles = groupProfileList,
                accountId = scoped.accountId
            )
            val profileAppliedConversation = applyContactProfilesToConversation(
                conversation = groupAppliedConversation,
                profiles = contactProfileList
            )
            scoped.copy(
                conversation = applyContactProfilesToConversation(
                    conversation = profileAppliedConversation.copy(
                        messages = (profileAppliedConversation.messages + visibleLocalMessages).filter { message ->
                            hiddenMessageIds[message.id] != true
                        }
                    ),
                    profiles = contactProfileList
                )
            )
        }
    }
    val visibleHomeOverviewConversations = remember(accountScopedDisplayConversations, homeOverviewAccountId) {
        homeOverviewAccountId?.let { accountId ->
            accountScopedDisplayConversations.filter { scoped -> scoped.accountId == accountId }
        } ?: accountScopedDisplayConversations
    }
    val homeDisplayConversation = remember(profiledConversation, visibleHomeOverviewConversations) {
        if (visibleHomeOverviewConversations.isEmpty()) {
            profiledConversation
        } else {
            allAccountHomeConversation(
                baseConversation = profiledConversation,
                accountConversations = visibleHomeOverviewConversations
            )
        }
    }
    val activeGroupProfilesById = remember(groupProfileList, activeAccountId) {
        groupProfileList
            .filter { profile -> profile.accountId == activeAccountId }
            .associateBy { profile -> profile.groupId }
    }
    val currentGroupMemberAvatarsVisible = groupMemberAvatarsVisibleForSelection(
        selection = selectedThread,
        groupProfilesById = activeGroupProfilesById
    )
    val previewableMedia = remember(displayConversation, selectedThread, selectedAccount.id) {
        previewableThreadMedia(
            visibleMessagesForThread(
                conversation = displayConversation,
                selection = selectedThread,
                selectedAccountId = selectedAccount.id
            )
        )
    }
    val savedFavoriteItems = storedFavoriteItems.toList()
    val favoriteItems = remember(
        displayConversation,
        selectedThread,
        selectedAccount.id,
        favoriteMessageIds.size,
        favoriteMediaIds.size,
        savedFavoriteItems
    ) {
        favoriteCollectionItems(
            messages = visibleMessagesForThread(
                conversation = displayConversation,
                selection = selectedThread,
                selectedAccountId = selectedAccount.id
            ),
            favoriteMessageIds = favoriteMessageIds,
            favoriteMediaIds = favoriteMediaIds,
            storedItems = savedFavoriteItems,
            accountId = selectedAccount.id
        )
    }
    val favoriteCollectionActions = FavoriteCollectionActions(
        context = context,
        favoriteItems = { favoriteItems },
        favoriteMessageIds = favoriteMessageIds,
        favoriteMediaIds = favoriteMediaIds,
        storedFavoriteItems = storedFavoriteItems,
        selectedFavoriteItemIds = selectedFavoriteItemIds,
        runtimeState = runtimeState,
        mediaOverlayState = mediaOverlayState,
        accountId = { selectedAccount.id },
        onPreviewItem = { item -> favoritePreviewItem = item },
        onForwardMessage = { message -> forwardMessage = message },
        onMultiSelectModeChanged = { enabled -> favoriteMultiSelectMode = enabled }
    )
    fun syncLocalMessageState() {
        localMessageVersion += 1
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
    }
    fun markUnreadSummaryReplied(threadId: String) {
        homeUnreadThreadSummaries(displayConversation)
            .firstOrNull { summary ->
                summary.accountId == activeAccountId && summary.threadId == threadId
            }
            ?.let(chatNavigationActions::markReplied)
    }
    FloatingChatConversationSyncEffects(
        conversation = conversation,
        runtimeState = runtimeState,
        onLiveConversationChanged = { nextConversation -> liveConversation = nextConversation },
        chatNavigationState = chatNavigationState,
        onChatNavigationStateChanged = ::updateChatNavigationState,
        onLocalMessagesReplaced = { messages ->
            localMessages.clear()
            localMessages.addAll(messages)
        },
        onLocalMessageSequenceChanged = { sequence -> localMessageSequence = sequence },
        onLocalMessagesSynced = ::syncLocalMessageState
    )
    val outgoingMessageActions = OutgoingMessageActions(
        conversation = effectiveConversation,
        selectedThread = selectedThread,
        selectedAccount = selectedAccount,
        nextSequence = {
            localMessageSequence += 1
            localMessageSequence
        },
        prepareOutgoingMessage = onPrepareOutgoingMessage,
        onOutgoingMessageCreated = { message, threadId ->
            markUnreadSummaryReplied(threadId)
            localMessages += message
            syncLocalMessageState()
            onPersistLocalMessage(message, threadId)
        }
    )
    val pickedMediaMessageActions = PickedMediaMessageActions(
        conversation = effectiveConversation,
        selectedThread = selectedThread,
        selectedAccountId = selectedAccount.id,
        nextSequence = {
            localMessageSequence += 1
            localMessageSequence
        },
        prepareOutgoingMessage = onPrepareOutgoingMessage,
        onPickedMessageCreated = { message, threadId ->
            markUnreadSummaryReplied(threadId)
            localMessages += message
            syncLocalMessageState()
            onPersistLocalMessage(message, threadId)
        }
    )
    val inputMessageActions = InputMessageActions(
        outgoingMessageActions = outgoingMessageActions,
        quotedMessage = { quotedMessage },
        inputText = { inputText },
        onQuotedMessageChanged = { message -> quotedMessage = message },
        onInputTextChanged = { text -> inputText = text },
        onBlinkGeneratedInputClearableChanged = { clearable -> blinkGeneratedInputClearable = clearable },
        onBottomPanelModeChanged = { mode -> bottomPanelMode = mode },
        onBlinkInputStatusChanged = { message, autoDismiss ->
            blinkInputStatusText = message
            blinkInputStatusAutoDismiss = autoDismiss
        },
        onBlinkInputStatusVersionIncremented = { blinkInputStatusVersion += 1 }
    )
    val profilePersistenceActions = ProfilePersistenceActions(
        context = context,
        accountProfiles = accountProfiles,
        contactProfiles = contactProfiles,
        groupProfiles = groupProfiles,
        onPersistContactProfile = onPersistContactProfile,
        onPersistGroupProfile = onPersistGroupProfile
    )
    val contactRemoteTaskActions = ContactRemoteTaskActions(
        context = context,
        coroutineScope = coroutineScope,
        scrmProfileManager = scrmProfileManager,
        selectedAccountId = { selectedAccount.id },
        onContactEditorClosed = { contactEditorTarget = null },
        onGroupMemberAddFriendStateChanged = { state ->
            groupMemberAddFriendTargetId = state.targetId
            groupMemberAddFriendLoading = state.loading
            groupMemberAddFriendStatus = state.status
            groupMemberAddFriendError = state.error
        }
    )
    val toolMessageActions = ToolMessageActions(
        conversation = { effectiveConversation },
        fallbackConversation = { liveConversation },
        selectedAccount = { selectedAccount },
        accountProfile = { account -> accountProfiles[account.id] ?: defaultAccountProfileFor(account) },
        outgoingMessageActions = outgoingMessageActions,
        onPendingAvatarAccountIdChanged = { accountId -> pendingAvatarAccountId = accountId },
        onBottomPanelModeChanged = { mode -> bottomPanelMode = mode },
        onAssistantPanelOpened = {
            aiConfigStatus = null
            bottomPanelMode = BottomPanelMode.Assistant
        },
        onAiVoicePanelOpened = {
            aiVoiceState = AiVoiceState.Menu
            bottomPanelMode = BottomPanelMode.AiVoice
        },
        onTransferRequested = {
            val requiresRecipientSelection = selectedThread is ChatThreadSelection.Group ||
                selectedThread is ChatThreadSelection.GroupChat
            val recipients = when (val thread = selectedThread) {
                is ChatThreadSelection.Private -> displayConversation.contacts
                    .firstOrNull { contact -> contact.id == thread.contactId }
                    ?.let(::listOf).orEmpty()
                else -> transferRecipientCandidatesForThread(
                    conversation = displayConversation,
                    selectedThread = thread,
                    selectedAccountId = selectedAccount.id
                ).ifEmpty {
                    (thread as? ChatThreadSelection.GroupChat)
                        ?.let { groupThread -> displayConversation.groupContacts.firstOrNull { it.id == groupThread.groupId } }
                        ?.groupMemberContacts.orEmpty()
                }
            }.map { contact ->
                FloatingChatTransferRecipient(
                    id = contact.id,
                    name = contact.name,
                    weChatId = contact.description,
                    avatarUri = contact.avatarUrl
                )
            }
            FloatingChatTransferBridge.open(
                FloatingChatTransferSession(
                    accountName = selectedAccount.name,
                    recipients = recipients,
                    requiresRecipientSelection = requiresRecipientSelection,
                    accountId = selectedAccount.id,
                    roomId = (selectedThread as? ChatThreadSelection.GroupChat)
                        ?.let { thread -> scrmFloatingContactConversationId(thread.groupId) }
                )
            )
        },
        onRedPacketRequested = {
            val recipients = when (val thread = selectedThread) {
                is ChatThreadSelection.Private -> displayConversation.contacts
                    .firstOrNull { contact -> contact.id == thread.contactId }
                    ?.let(::listOf).orEmpty()
                else -> transferRecipientCandidatesForThread(
                    conversation = displayConversation,
                    selectedThread = thread,
                    selectedAccountId = selectedAccount.id
                ).ifEmpty {
                    (thread as? ChatThreadSelection.GroupChat)
                        ?.let { groupThread -> displayConversation.groupContacts.firstOrNull { it.id == groupThread.groupId } }
                        ?.groupMemberContacts.orEmpty()
                }
            }
            FloatingChatRedPacketBridge.open(
                FloatingChatRedPacketSession(
                    accountName = selectedAccount.name,
                    recipientNames = recipients.map { contact -> contact.name }.filter { name -> name.isNotBlank() }.take(3)
                )
            )
        }
    )
    val aiDraftMessageActions = AiDraftMessageActions(
        localMessages = localMessages,
        hiddenMessageIds = hiddenMessageIds,
        sentDraftMessageIds = sentAiDraftMessageIds,
        selectedThread = selectedThread,
        selectedAccountId = selectedAccount.id,
        nextSequence = {
            localMessageSequence += 1
            localMessageSequence
        },
        onDraftMessagesChanged = ::syncLocalMessageState,
        prepareOutgoingMessage = onPrepareOutgoingMessage,
        onPersistLocalMessage = onPersistLocalMessage,
        onOutgoingMessageCreated = { _, threadId -> markUnreadSummaryReplied(threadId) },
        onDraftOverlaysClosed = {
            aiDraftActionMessage = null
            aiDraftEditMessage = null
        }
    )
    val aiDraftGenerationActions = AiDraftGenerationActions(
        coroutineScope = coroutineScope,
        aiConfig = { aiConfig },
        aiPredicting = { aiPredicting },
        effectiveConversation = { effectiveConversation },
        displayConversation = { displayConversation },
        selectedThread = { selectedThread },
        selectedAccountId = { selectedAccount.id },
        selectedAccountName = { selectedAccount.name },
        nextSequence = {
            localMessageSequence += 1
            localMessageSequence
        },
        sentAiDraftMessageIds = sentAiDraftMessageIds,
        aiDraftMessageActions = aiDraftMessageActions,
        onAiPredictingChanged = { predicting -> aiPredicting = predicting },
        onAiConfigStatusChanged = { status -> aiConfigStatus = status },
        onOpenAssistantPanel = { bottomPanelMode = BottomPanelMode.Assistant },
        onCloseAssistantPanel = { bottomPanelMode = BottomPanelMode.None },
        onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    )
    val messageAsideAnalysisActions = MessageAsideAnalysisActions(
        coroutineScope = coroutineScope,
        aiConfig = { aiConfig },
        displayConversation = { displayConversation },
        selectedThread = { selectedThread },
        selectedAccountId = { selectedAccount.id },
        selectedAccountName = { selectedAccount.name },
            onStateChanged = { state -> asideAnalysisState = state },
        onOpenAssistantPanel = {
            aiConfigStatus = "请先配置 AI API"
            bottomPanelMode = BottomPanelMode.Assistant
        }
    )
    val blinkInputAiActions = BlinkInputAiActions(
        coroutineScope = coroutineScope,
        aiConfig = { aiConfig },
        blinkInputAiBusy = { blinkInputAiBusy },
        aiPredicting = { aiPredicting },
        inputText = { inputText },
        displayConversation = { displayConversation },
        selectedThread = { selectedThread },
        selectedAccountId = { selectedAccount.id },
        selectedAccountName = { selectedAccount.name },
        onAiConfigStatusChanged = { status -> aiConfigStatus = status },
        onBlinkInputBusyChanged = { busy -> blinkInputAiBusy = busy },
        onInputTextChanged = { text -> inputText = text },
        onBlinkGeneratedInputClearableChanged = { clearable -> blinkGeneratedInputClearable = clearable },
        onShowBlinkInputStatus = { message, autoDismiss ->
            inputMessageActions.showBlinkInputStatus(message = message, autoDismiss = autoDismiss)
        },
        onOpenAssistantPanel = { bottomPanelMode = BottomPanelMode.Assistant },
        onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    )
    val aiConfigTestActions = AiConfigTestActions(
        coroutineScope = coroutineScope,
        aiConfigTesting = { aiConfigTesting },
        onAiConfigTestingChanged = { testing -> aiConfigTesting = testing },
        onAiConfigStatusChanged = { status -> aiConfigStatus = status }
    )
    val quickPhraseActions = QuickPhraseActions(
        context = context,
        quickPhrases = { quickPhrases },
        onQuickPhrasesChanged = { phrases -> quickPhrases = phrases },
        onSendText = { text -> inputMessageActions.sendTextToCurrentThread(text) },
        onBottomPanelModeChanged = { mode -> bottomPanelMode = mode }
    )
    val messageForwardingActions = MessageForwardingActions(
        conversation = effectiveConversation,
        nextSequence = {
            localMessageSequence += 1
            localMessageSequence
        },
        prepareOutgoingMessage = onPrepareOutgoingMessage,
        onForwardMessageCreated = { message, threadId ->
            markUnreadSummaryReplied(threadId)
            localMessages += message
            syncLocalMessageState()
            onPersistLocalMessage(message, threadId)
        }
    )
    val startForwardingMessages: (List<FloatingChatMessage>) -> Unit = forward@ { messages ->
        val selection = forwardStartSelection(messages) ?: return@forward
        pendingForwardMessages = selection.pendingMessages
        pendingForwardMode = selection.pendingMode
        forwardModeMessages = selection.modeMessages
    }
    val selectedMessagesForCurrentAction = {
        selectedMessagesForAction(displayConversation.messages, selectedMessageIds)
    }
    var scrmMessageOperationTarget by remember { mutableStateOf<FloatingChatMessage?>(null) }
    val messageLongPressActions = MessageLongPressActions(
        favoriteMessageIds = favoriteMessageIds,
        hiddenMessageIds = hiddenMessageIds,
        selectedMessageIds = selectedMessageIds,
        onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
        onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
        onBeginForward = { messages -> startForwardingMessages(messages) },
        onFavoriteChanged = { message, favorite ->
            updateFavoriteCollectionItems(context, storedFavoriteItems, message, favorite, selectedAccount.id)
        },
        onMultiSelectModeChanged = { enabled -> multiSelectMode = enabled },
        onQuoteMessage = { message ->
            quotedMessage = message
            inputFocused = true
        },
        onListenMessage = { message -> messageAsideAnalysisActions.analyze(message) },
        onZoomMessage = { message ->
            when (messageZoomMode(message.type)) {
                MessageZoomMode.Text -> textZoomMessage = message
                MessageZoomMode.Media -> {
                    val previewIndex = previewableMedia.indexOfFirst { preview -> preview.id == message.id }
                    if (previewIndex >= 0) {
                        FloatingChatMediaPreviewBridge.open(
                            mediaMessages = previewableMedia,
                            initialIndex = previewIndex,
                            runtimeState = runtimeState,
                            accountId = selectedAccount.id
                        )
                    } else {
                        Toast.makeText(context, "媒体资源暂不可用", Toast.LENGTH_SHORT).show()
                    }
                }
                MessageZoomMode.Unsupported -> {
                    Toast.makeText(context, "该消息类型暂不支持放大", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onScrmOperationRequested = { message -> scrmMessageOperationTarget = message },
        onCloseLongPressMenu = { longPressMessage = null }
    )
    PickedMediaEffects(
        pickedMediaEvent = runtimeState.pickedMediaEvent,
        pickedDocumentEvent = runtimeState.pickedDocumentEvent,
        pendingAvatarAccountId = pendingAvatarAccountId,
        accountContacts = effectiveConversation.accountContacts,
        fallbackAccountContacts = liveConversation.accountContacts,
        accountProfile = { account -> accountProfiles[account.id] ?: defaultAccountProfileFor(account) },
        pickedMediaMessageActions = pickedMediaMessageActions,
        onAccountProfileChanged = { accountId, profile ->
            profilePersistenceActions.updateAccountProfile(accountId, profile)
        },
        onPendingAvatarAccountIdChanged = { accountId -> pendingAvatarAccountId = accountId },
        onPendingMomentMediaChanged = { media ->
            if (media == null) {
                pendingMomentMediaByAccount.remove(selectedAccount.id)
            } else {
                pendingMomentMediaByAccount[selectedAccount.id] = media
            }
        },
        onBottomPanelModeChanged = { mode -> bottomPanelMode = mode },
        onClearPickedMediaEvent = { token -> runtimeState.clearPickedMediaEvent(token) },
        onClearPickedDocumentEvent = { token -> runtimeState.clearPickedDocumentEvent(token) }
    )
    FloatingChatBlinkInputEffects(
        runtimeState = runtimeState,
        inputFocused = inputFocused,
        inputText = inputText,
        blinkInputAiBusy = blinkInputAiBusy,
        aiPredicting = aiPredicting,
        blinkInputStatusText = blinkInputStatusText,
        blinkInputStatusAutoDismiss = blinkInputStatusAutoDismiss,
        blinkInputStatusVersion = blinkInputStatusVersion,
        onShowBlinkInputStatus = { message, autoDismiss ->
            inputMessageActions.showBlinkInputStatus(message = message, autoDismiss = autoDismiss)
        },
        onRunBlinkInputAiAction = { action, eventType ->
            blinkInputAiActions.run(action = action, eventType = eventType)
        },
        onSendRecognizedText = { text -> inputMessageActions.sendTextToCurrentThread(text) },
        onClearBlinkInputStatus = { inputMessageActions.clearBlinkInputStatus() }
    )
    val mediaActionDispatchActions = MediaActionDispatchActions(
        context = context,
        favoriteMediaIds = favoriteMediaIds,
        onOpenActions = { message -> mediaOverlayState.openActions(message) },
        onFavoriteChanged = { favoriteMessage, favorite ->
            updateFavoriteCollectionItems(context, storedFavoriteItems, favoriteMessage, favorite, selectedAccount.id)
        },
        onActionResult = { result -> mediaOverlayState.applyActionResult(result) },
        onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    )
    val hideKeyboardFromBlankArea = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        inputFocused = false
        bottomPanelMode = BottomPanelMode.None
    }
    val headerRoute = when (chatNavigationState.route) {
        ChatNavigationRoute.SingleAccountUnread -> FloatingChatHeaderRoute.SingleAccountUnread
        ChatNavigationRoute.AllAccountsUnread -> FloatingChatHeaderRoute.AllAccountsUnread
        ChatNavigationRoute.Conversation -> FloatingChatHeaderRoute.Conversation
    }
    val headerEditableContact = (selectedThread as? ChatThreadSelection.Private)?.let { thread ->
        displayConversation.contacts.firstOrNull { contact -> contact.id == thread.contactId }
    }
    val headerTitle = chatToolbarTitle(
        conversation = displayConversation,
        selectedThread = selectedThread,
        homeOverviewVisible = false
    )
    val currentUnreadCount = unreadThreadIds.values.count { unread -> unread }
    val headerState = floatingChatHeaderState(
        route = headerRoute,
        accountName = selectedAccount.name,
        conversationTitle = headerTitle,
        unreadCount = currentUnreadCount,
        messageScrollInProgress = headerCompact,
        editable = headerEditableContact != null
    )
    val openUnreadOverview = {
        chatNavigationActions.openAllAccountsUnread()
        bottomPanelMode = BottomPanelMode.None
    }
    ChatThreadEffects(
        effectiveConversation = effectiveConversation,
        selectedThread = selectedThread,
        selectedAccountId = selectedAccount.id,
        unreadThreadIds = unreadThreadIds,
        runtimeState = runtimeState,
        onSelectedThreadChanged = { thread ->
            updateChatNavigationState(chatNavigationState.copy(selectedThread = thread))
        },
        onThreadContextChanged = onThreadContextChanged
    )
    FloatingChatPreviewChromeEffects(
        runtimeState = runtimeState,
        mediaOverlayState = mediaOverlayState,
        onPreviewChromeChanged = onPreviewChromeChanged
    )
    val currentOnBottomGesture by rememberUpdatedState(onBottomGesture)
    FloatingChatRuntimeSections(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .floatingChatOverlayGestureBinding(
                edgeGestureShortThresholdDp = edgeGestureShortThresholdDp,
                edgeGestureLongThresholdDp = edgeGestureLongThresholdDp,
                leftEdgeConfigs = leftEdgeConfigs,
                rightEdgeConfigs = rightEdgeConfigs,
                onEdgeGesture = onEdgeGesture,
                onBackGestureProgress = onBackGestureProgress,
                onBackGestureCommit = onBackGestureCommit,
                onBackGestureEnd = onBackGestureEnd,
                onBackGestureCancel = onBackGestureCancel
            )
            .floatingChatFrostedBackdrop(
                enabled = frostedBackgroundEnabled,
                opacityPercent = backgroundOpacityPercent,
                blurRadiusDp = blurRadiusDp,
                backgroundColorRgb = backgroundColorRgb
            ),
        topContent = {
            FloatingChatWorkspaceHeader(
                state = headerState,
                accountName = selectedAccount.name,
                onLeadingClick = {
                    when (chatNavigationActions.back()) {
                        ChatNavigationBackResult.Collapse -> onCollapse()
                        is ChatNavigationBackResult.Navigate -> Unit
                    }
                    bottomPanelMode = BottomPanelMode.None
                },
                onEditClick = headerEditableContact?.let { contact ->
                    { contactEditorTarget = ContactEditorTarget.User(contact) }
                },
                onSearchClick = { openChatSearchRequestKey += 1 },
                onScanClick = { FloatingChatMediaPickerBridge.requestScan() },
                onAddFriendClick = {
                    contactsOpenAddFriend = true
                    bottomPanelMode = BottomPanelMode.Contacts
                }
            )
        },
        mainContent = {
            CoordinateChatBody(
            conversation = if (homeOverviewVisible) homeDisplayConversation else displayConversation,
            homeOverviewConversations = visibleHomeOverviewConversations,
            accountProfiles = accountProfiles,
            navigationState = chatNavigationState,
            activeAccountId = selectedAccount.id,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            unreadThreadIds = unreadThreadIds.filterValues { unread -> unread }.keys.toSet(),
            inputFocused = inputFocused,
            groupMemberAvatarsVisible = currentGroupMemberAvatarsVisible,
            onThreadSelected = { thread -> chatNavigationActions.openChatThread(thread) },
            onHomeUnreadSelected = { summary -> chatNavigationActions.openHomeUnread(summary) },
            onToolAction = { action -> toolMessageActions.sendToolMessage(action) },
            onGroupAvatarLongClick = { group ->
                contactEditorTarget = ContactEditorTarget.Group(group)
            },
            onContactAvatarLongClick = { contact ->
                contactEditorTarget = ContactEditorTarget.User(contact)
            },
            onAccountAvatarClick = { account ->
                if (homeOverviewVisible) {
                    if (chatNavigationState.route == ChatNavigationRoute.AllAccountsUnread) {
                        chatNavigationActions.openSingleAccountUnread(account.id)
                    } else {
                        chatNavigationActions.switchUnreadAccount(account.id)
                    }
                    return@CoordinateChatBody
                }
                if (!shouldHandleAccountAvatarClick(activeAccountId, account.id)) {
                    return@CoordinateChatBody
                }
                val nextAccountId = selectedAccountIdAfterAccountAvatarClick(
                    currentAccountId = activeAccountId,
                    clickedAccountId = account.id
                )
                val nextThread = selectedThreadAfterAccountAvatarClick(
                    conversationCache = accountConversationCache,
                    clickedAccountId = nextAccountId,
                    currentThread = selectedThread
                )
                // Commit the account choice before the next frame. Waiting for
                // ChatThreadEffects' LaunchedEffect leaves a window in which an
                // older refresh response can still restore the previous account.
                runtimeState.conversationUpdateEvent?.let { pendingUpdate ->
                    runtimeState.clearConversationUpdate(pendingUpdate.token)
                }
                chatNavigationActions.switchConversationAccount(nextAccountId, nextThread)
                onThreadContextChanged(nextThread, nextAccountId)
                bottomPanelMode = BottomPanelMode.None
            },
            onAccountAvatarLongClick = { account ->
                accountEditorTarget = account
            },
            onPreviewMedia = { message ->
                if (isFinderMessage(message)) {
                    finderInitialUserName = finderUserNameForMessage(message)
                    finderUserPageRequestKey += 1
                    bottomPanelMode = BottomPanelMode.Finder
                    return@CoordinateChatBody
                }
                val previewIndex = previewableMedia.indexOfFirst { preview -> preview.id == message.id }
                    .takeIf { it >= 0 }
                    ?: return@CoordinateChatBody
                FloatingChatMediaPreviewBridge.open(
                    mediaMessages = previewableMedia,
                    initialIndex = previewIndex,
                    runtimeState = runtimeState,
                    accountId = selectedAccount.id
                )
                mediaOverlayState.clearStatus()
            },
            onPreviewDocument = { message -> runtimeState.openDocumentPreview(message) },
            onOpenMediaActions = { message -> mediaOverlayState.openActions(message) },
            onPaymentCardClick = { message ->
                paymentDetailMessage = message
                bottomPanelMode = BottomPanelMode.None
            },
            onChatHistoryClick = { message ->
                chatHistoryPreviewMessage = message
                bottomPanelMode = BottomPanelMode.None
            },
            onAiDraftClick = { message ->
                aiDraftActionMessage = message
                bottomPanelMode = BottomPanelMode.None
            },
            onMessageClick = { message ->
                if (isFinderMessage(message)) {
                    finderInitialUserName = finderUserNameForMessage(message)
                    finderUserPageRequestKey += 1
                    bottomPanelMode = BottomPanelMode.Finder
                    return@CoordinateChatBody
                }
                // Reuse the existing in-tree action group; this is presentation-only.
                messageAsideAnalysisActions.dismiss()
                textZoomMessage = null
                longPressMessage = message
                longPressAnchorBounds = null
                bottomPanelMode = BottomPanelMode.None
            },
            onLongPressMessage = { message, bounds ->
                messageAsideAnalysisActions.dismiss()
                textZoomMessage = null
                longPressMessage = message
                longPressAnchorBounds = bounds
            },
            multiSelectMode = multiSelectMode,
            selectedMessageIds = selectedMessageIds,
            remindedMessageIds = reminderMessageIds,
            favoriteMessageIds = favoriteMessageIds,
            claimedPaymentMessageIds = claimedPaymentMessageIds,
            onToggleMessageSelection = { message ->
                if (selectedMessageIds[message.id] == true) {
                    selectedMessageIds.remove(message.id)
                } else {
                    selectedMessageIds[message.id] = true
                }
            },
            onBlankAreaTap = hideKeyboardFromBlankArea,
            onCloseChat = onCollapse,
            onScanClick = {
                FloatingChatMediaPickerBridge.requestScan()
            },
            onAddFriendClick = {
                contactsOpenAddFriend = true
                bottomPanelMode = BottomPanelMode.Contacts
            },
            showTopToolbar = false,
            onMessageScrollStateChanged = { scrolling -> messageListScrolling = scrolling },
            openSearchRequestKey = openChatSearchRequestKey,
            modifier = Modifier.fillMaxSize()
        )
        },
        bottomContent = {
        if (bottomInputBarVisibleForCenteredToolPanel(bottomPanelMode.isCenteredToolFeaturePanel()) &&
            !bottomPanelMode.isBottomComposerDrawer()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                blinkInputStatusText?.let { statusText ->
                    BlinkVoiceInputStatusBar(
                        text = statusText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                BottomInputBar(
                    inputText = inputText,
                    onInputTextChange = {
                        inputText = it
                        blinkGeneratedInputClearable = false
                    },
                    quotedMessage = quotedMessage,
                    onClearQuote = { quotedMessage = null },
                    aiGeneratedClearable = blinkGeneratedInputClearable,
                    onClearAiGeneratedInput = {
                        inputText = ""
                        blinkGeneratedInputClearable = false
                        blinkInputStatusText = null
                        blinkInputStatusAutoDismiss = false
                    },
                    inputFocused = inputFocused,
                    onInputFocusedChange = { inputFocused = it },
                    panelMode = bottomPanelMode,
                    onPanelModeChange = { nextMode ->
                        if (nextMode == BottomPanelMode.Emoji || nextMode == BottomPanelMode.More) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                        bottomPanelMode = nextMode
                    },
                    onSend = { inputMessageActions.sendInputMessage() },
                    onAssistantPredict = { aiDraftGenerationActions.generate() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        },
        panelContent = {
        if (bottomPanelVisibility.currentState || bottomPanelVisibility.targetState) {
            val displayedPanelIsBottomDrawer = displayedBottomPanelMode.isBottomComposerDrawer()
            AnimatedVisibility(
                modifier = if (displayedPanelIsBottomDrawer) {
                    Modifier.align(Alignment.BottomCenter)
                } else {
                    Modifier.fillMaxSize()
                },
                visibleState = bottomPanelVisibility,
                enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut()
            ) {
            Box(
                modifier = if (displayedPanelIsBottomDrawer) Modifier else Modifier.fillMaxSize()
            ) {
                if (displayedBottomPanelMode.isCenteredToolFeaturePanel()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayTokens.centerPanelScrim)
                            .pointerInput(displayedBottomPanelMode) {
                                detectTapGestures(onTap = {
                                    if (displayedBottomPanelMode == BottomPanelMode.AiVoice) aiVoiceRuntime?.stop()
                                    bottomPanelMode = BottomPanelMode.None
                                })
                            }
                    )
                }
                FloatingBottomPanel(
                mode = displayedBottomPanelMode,
                scrmContactsRoute = scrmContactsPanelRouteForSelectedAccount(
                    selectedAccountId = selectedAccount.id,
                    fallbackDeviceUuid = null,
                    fallbackWeChatId = null
                ),
                contactsOpenAddFriend = displayedContactsOpenAddFriend,
                scrmMomentsRoute = scrmContactsPanelRouteForSelectedAccount(
                    selectedAccountId = selectedAccount.id,
                    fallbackDeviceUuid = null,
                    fallbackWeChatId = null
                ),
                scrmMessageRoute = scrmFloatingAccountRouteForContactId(selectedAccount.id),
                finderSession = scrmFloatingAccountRouteForContactId(selectedAccount.id)?.let { route ->
                    FinderSession(deviceUuid = route.deviceUuid, weChatId = route.weChatId)
                },
                finderApi = remember(selectedAccount.id) {
                    runCatching {
                        ScrmFinderApi(
                            ScrmSettingsManager(context.applicationContext).loadApiConfig()
                        )
                    }.getOrNull()
                },
                finderConfigurationError = remember(selectedAccount.id) {
                    runCatching {
                        ScrmSettingsManager(context.applicationContext).loadApiConfig()
                    }.exceptionOrNull()?.message
                },
                finderInitialSphUserName = finderInitialUserName,
                finderUserPageRequestKey = finderUserPageRequestKey,
                scrmMessageConversationId = when (val thread = selectedThread) {
                    is ChatThreadSelection.Private -> scrmFloatingContactConversationId(thread.contactId)
                    is ChatThreadSelection.GroupChat -> scrmFloatingContactConversationId(thread.groupId)
                    ChatThreadSelection.Group -> null
                },
                voicePermissionRequestToken = voicePermissionRequestToken,
                locationPermissionRequestToken = locationPermissionRequestToken,
                onClose = {
                    if (bottomPanelMode == BottomPanelMode.AiVoice) aiVoiceRuntime?.stop()
                    bottomPanelMode = BottomPanelMode.None
                },
                aiVoiceState = aiVoiceState,
                aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState,
                onAiVoiceEvent = { event ->
                    val generationState = aiVoiceState as? AiVoiceState.GeneratingMessage
                    val voiceProfileState = aiVoiceState as? AiVoiceState.CreatingVoiceProfile
                    val wasRealtimeCall = aiVoiceState is AiVoiceState.RealtimeCall
                    val generatedAudioUrl = generationState?.generatedAudioUrl
                    if (event == AiVoiceEvent.CallEnded ||
                        (event == AiVoiceEvent.BackRequested && wasRealtimeCall)
                    ) {
                        aiVoiceRuntime?.stop()
                        aiVoiceRuntime = null
                    }
                    aiVoiceState = aiVoiceCoordinator.dispatch(event)
                    if (event == AiVoiceEvent.VoiceProfileRecordingStarted) {
                        runCatching { voiceCloneRecorder.start() }
                            .onFailure { error ->
                                aiVoiceState = AiVoiceState.Failed(error.message ?: "声音样本录制失败")
                            }
                    }
                    if (event == AiVoiceEvent.VoiceProfileRecordingStopped) {
                        runCatching { voiceCloneRecorder.stop() }
                            .onSuccess { file ->
                                aiVoiceState = aiVoiceCoordinator.dispatch(
                                    AiVoiceEvent.VoiceProfileRecordingCompleted(file.absolutePath)
                                )
                            }
                            .onFailure { error ->
                                aiVoiceState = AiVoiceState.Failed(error.message ?: "停止录音失败")
                            }
                    }
                    if (event == AiVoiceEvent.VoiceProfileSubmitRequested && voiceProfileState?.recordedAudioPath != null) {
                        coroutineScope.launch {
                            val result = runCatching {
                                doubaoVoiceCloneApi.train(
                                    apiKey = aiVoiceCapabilityConfigState.apiKeyInput,
                                    speakerId = voiceProfileState.speakerId,
                                    audioFile = File(voiceProfileState.recordedAudioPath),
                                    referenceText = VoiceCloneReferenceText
                                )
                            }
                            aiVoiceState = result.fold(
                                onSuccess = { AiVoiceState.CreatingVoiceProfile(VoiceProfileStep.Completed, speakerId = it) },
                                onFailure = { AiVoiceState.Failed(it.message ?: "声音复刻训练提交失败") }
                            )
                        }
                    }
                    if (event == AiVoiceEvent.PreviewGeneratedVoice && generatedAudioUrl != null) {
                        runCatching { playGeneratedAiVoice(context, generatedAudioUrl) }
                            .onFailure { error ->
                                aiVoiceState = aiVoiceCoordinator.dispatch(
                                    AiVoiceEvent.CallFailed(error.message ?: "语音试听失败")
                                )
                            }
                    }
                    if (event == AiVoiceEvent.SendGeneratedVoice && generatedAudioUrl != null) {
                        val durationMs = generatedAiVoiceDurationMs(context, generatedAudioUrl)
                        inputMessageActions.sendVoiceMessage(generatedAudioUrl, durationMs)
                        aiVoiceState = AiVoiceState.Menu
                        bottomPanelMode = BottomPanelMode.None
                    }
                    if (event == AiVoiceEvent.GenerateMessageRequested && generationState != null) {
                        coroutineScope.launch {
                            val result = runCatching {
                                val audio = doubaoVoiceTtsApi.synthesize(
                                    apiKey = aiVoiceCapabilityConfigState.apiKeyInput,
                                    text = generationState.text
                                )
                                val directory = File(context.cacheDir, "doubao-voice").apply { mkdirs() }
                                File(directory, "voice-${System.currentTimeMillis()}.mp3")
                                    .apply { writeBytes(audio) }
                            }
                            aiVoiceState = result.fold(
                                onSuccess = { file ->
                                    aiVoiceCoordinator.dispatch(
                                        AiVoiceEvent.VoiceMessageGenerated(Uri.fromFile(file).toString())
                                    )
                                },
                                onFailure = { error ->
                                    aiVoiceCoordinator.dispatch(
                                        AiVoiceEvent.CallFailed(error.message ?: "豆包语音生成失败")
                                    )
                                }
                            )
                        }
                    }
                    if (event is AiVoiceEvent.FeatureSelected &&
                        aiVoiceState == AiVoiceState.RealtimeCall(RealtimeCallState.Connecting)
                    ) {
                        val credentials = DoubaoRealtimeCredentials(
                            appId = aiVoiceCapabilityConfigState.appIdInput,
                            accessToken = aiVoiceCapabilityConfigState.accessTokenInput
                        )
                        aiVoiceRuntime = AiVoiceCallRuntime(
                            connector = OkHttpAiVoiceRealtimeClient(credentials),
                            audioEngine = AndroidAiVoiceAudioEngine(context),
                            onStateChanged = { state ->
                                val current = aiVoiceState as? AiVoiceState.RealtimeCall
                                aiVoiceState = AiVoiceState.RealtimeCall(
                                    state = state,
                                    messages = current?.messages.orEmpty()
                                )
                            },
                            onError = { message -> aiVoiceState = AiVoiceState.Failed(message) },
                            onTranscript = { speaker, text ->
                                val current = aiVoiceState as? AiVoiceState.RealtimeCall ?: return@AiVoiceCallRuntime
                                aiVoiceState = current.copy(messages = current.messages.withTranscript(speaker, text))
                            }
                        ).also { it.start(event.feature, voiceProfileId = null) }
                    }
                },
                onAiVoiceCapabilityConfigEvent = { event ->
                    when (event) {
                        is AiVoiceCapabilityConfigEvent.ApiKeyChanged -> {
                            aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState.withApiKeyInput(event.value)
                        }
                        is AiVoiceCapabilityConfigEvent.AppIdChanged -> {
                            aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState.withAppIdInput(event.value)
                        }
                        is AiVoiceCapabilityConfigEvent.AccessTokenChanged -> {
                            aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState.withAccessTokenInput(event.value)
                        }
                        AiVoiceCapabilityConfigEvent.VerifyCapabilitiesRequested -> {
                            val apiKey = aiVoiceCapabilityConfigState.apiKeyInput
                            val realtimeCredentials = DoubaoRealtimeCredentials(
                                appId = aiVoiceCapabilityConfigState.appIdInput,
                                accessToken = aiVoiceCapabilityConfigState.accessTokenInput
                            )
                            aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState.verifyingCapabilities()
                            coroutineScope.launch {
                                val ttsResult = runCatching {
                                    doubaoVoiceTtsApi.synthesize(apiKey, "豆包语音能力验证")
                                }
                                val realtimeResult = runCatching { probeDoubaoRealtime(realtimeCredentials) }
                                aiVoiceCapabilityConfigState = aiVoiceCapabilityConfigState.withProbeResults(
                                    ttsAvailable = ttsResult.isSuccess,
                                    realtimeAvailable = realtimeResult.isSuccess
                                )
                            }
                        }
                    }
                },
                onOpenAiVoice = {
                    aiVoiceState = AiVoiceState.Menu
                    bottomPanelMode = BottomPanelMode.AiVoice
                },
                onOpenToolPanel = { nextMode -> bottomPanelMode = nextMode },
                onOpenPrivateChat = { route, contact ->
                    val threadId = scrmPrivateChatThreadIdForContact(route, contact)
                    if (threadId == null) {
                        Toast.makeText(context, "当前联系人缺少可打开私聊的 wxid", Toast.LENGTH_SHORT).show()
                    } else {
                        chatNavigationActions.openChatThread(ChatThreadSelection.Private(threadId))
                        bottomPanelMode = BottomPanelMode.None
                    }
                },
                onOpenFriendProfile = { route, contact ->
                    val profileContact = scrmFloatingContactForProfile(route, contact)
                    if (profileContact == null) {
                        Toast.makeText(context, "当前联系人缺少可打开资料页的 wxid", Toast.LENGTH_SHORT).show()
                    } else {
                        contactEditorTarget = ContactEditorTarget.User(profileContact)
                        bottomPanelMode = BottomPanelMode.None
                    }
                },
                onInsertText = { inserted ->
                    inputText += inserted
                    if (bottomPanelMode != BottomPanelMode.Emoji) {
                        bottomPanelMode = BottomPanelMode.None
                    }
                },
                onSendVoice = { audioUri, durationMs -> inputMessageActions.sendVoiceMessage(audioUri, durationMs) },
                quickPhrases = quickPhrases,
                momentPosts = currentMomentPosts,
                pendingMomentMedia = pendingMomentMedia,
                favoriteItems = favoriteItems,
                accounts = effectiveConversation.accountContacts,
                accountProfiles = accountProfiles,
                aiConfig = aiConfig,
                aiConfigStatus = aiConfigStatus,
                aiPredicting = aiPredicting,
                aiConfigTesting = aiConfigTesting,
                transferRecipients = transferRecipientCandidatesForThread(
                    conversation = displayConversation,
                    selectedThread = selectedThread,
                    selectedAccountId = selectedAccount.id
                ),
                paymentOperationStatus = paymentOperationStatus,
                paymentOperationInProgress = paymentOperationInProgress,
                onSaveAiConfig = { nextConfig ->
                    aiConfig = nextConfig
                    saveFloatingChatAiConfig(context, nextConfig)
                    aiConfigStatus = if (nextConfig.isConfigured) {
                        "AI 配置已保存"
                    } else {
                        "请填写 API 地址、API Key 和模型"
                    }
                },
                onTestAiConfig = { candidate -> aiConfigTestActions.test(candidate) },
                onSendQuickPhrase = { phrase -> quickPhraseActions.sendQuickPhrase(phrase) },
                onAddQuickPhrase = { phrase -> quickPhraseActions.addQuickPhrase(phrase) },
                onUpdateQuickPhrase = { index, phrase -> quickPhraseActions.updateQuickPhrase(index, phrase) },
                onDeleteQuickPhrase = { index -> quickPhraseActions.deleteQuickPhrase(index) },
                onPickMomentMedia = { momentToolActions.pickMomentMedia() },
                onClearMomentMedia = { momentToolActions.clearMomentMedia() },
                onPreviewMomentMedia = { post -> momentToolActions.previewMomentMedia(post) },
                onUpdateMomentPost = { post -> momentToolActions.upsertMomentPost(post) },
                favoriteMultiSelectMode = favoriteMultiSelectMode,
                selectedFavoriteItemIds = selectedFavoriteItemIds,
                onPreviewFavoriteItem = { item -> favoriteCollectionActions.previewFavoriteItem(item) },
                onFavoriteItemLongPress = { item, bounds ->
                    favoriteLongPressItem = item
                    favoriteLongPressAnchorBounds = bounds
                },
                onToggleFavoriteSelection = { item ->
                    if (selectedFavoriteItemIds[item.messageId] == true) {
                        selectedFavoriteItemIds.remove(item.messageId)
                    } else {
                        selectedFavoriteItemIds[item.messageId] = true
                    }
                },
                onForwardSelectedFavorites = { favoriteCollectionActions.forwardSelectedFavoriteItems() },
                onDeleteSelectedFavorites = { favoriteCollectionActions.deleteSelectedFavoriteItems() },
                onCancelFavoriteSelection = {
                    selectedFavoriteItemIds.clear()
                    favoriteMultiSelectMode = false
                },
                onSendRedPacket = { amount, greeting, paymentPassword, packetCount ->
                    val route = scrmFloatingAccountRouteForContactId(selectedAccount.id)
                    val conversationId = when (val thread = selectedThread) {
                        is ChatThreadSelection.Private -> scrmFloatingContactConversationId(thread.contactId)
                        is ChatThreadSelection.GroupChat -> scrmFloatingContactConversationId(thread.groupId)
                        ChatThreadSelection.Group -> null
                    }
                    if (route == null || conversationId.isNullOrBlank()) {
                        paymentOperationStatus = "未提交：缺少真实 SCRM 账号或会话 wxid"
                        return@FloatingBottomPanel
                    }
                    paymentOperationInProgress = true
                    paymentOperationStatus = "正在提交红包并等待设备任务终态"
                    coroutineScope.launch {
                        val intent = "lucky:${route.deviceUuid}:${route.weChatId}:$conversationId:$amount:$packetCount:$greeting"
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val session = ScrmSettingsManager(context.applicationContext)
                                    .loadSelectedSessionOrBootstrap()
                                val api = session.readApi as? ScrmPaymentApi
                                    ?: error("当前 SCRM 客户端不支持发送红包")
                                val request = PaymentRequestFactory.sendLuckyMoney(
                                    route = route,
                                    conversationId = conversationId,
                                    amountText = amount,
                                    packetCount = packetCount,
                                    paymentPassword = paymentPassword,
                                    wish = greeting
                                )
                                val key = paymentIdempotencyRegistry.keyFor(intent, paymentPassword)
                                PaymentTaskRunner(session.taskApi).submitAndAwait {
                                    api.sendLuckyMoney(request, key)
                                }
                            }
                        }
                        paymentOperationInProgress = false
                        result.onSuccess { outcome ->
                            paymentOperationStatus = outcome.message
                            if (outcome.completed) {
                                paymentIdempotencyRegistry.complete(intent, paymentPassword)
                                toolMessageActions.addToolMessage(FloatingChatToolAction.RedPacket) { message ->
                                    message.copy(
                                        text = "红包 ¥$amount",
                                        appName = "微信红包",
                                        detail = greeting.trim().ifBlank { "恭喜发财，大吉大利" },
                                        remoteTaskId = outcome.taskId,
                                        sendState = FloatingChatSendState.Succeeded
                                    )
                                }
                            }
                        }.onFailure { error ->
                            paymentOperationStatus = "红包提交失败：${error.message ?: "未知错误"}"
                        }
                    }
                },
                onSendTransfer = { amount, note, recipient, paymentPassword ->
                    val privateRecipient = (selectedThread as? ChatThreadSelection.Private)?.let { thread ->
                        displayConversation.contacts.firstOrNull { contact -> contact.id == thread.contactId }
                    }
                    val targetRecipient = recipient ?: privateRecipient
                    val route = scrmFloatingAccountRouteForContactId(selectedAccount.id)
                    val recipientId = targetRecipient?.id?.let(::scrmFloatingContactConversationId)
                    val roomId = (selectedThread as? ChatThreadSelection.GroupChat)
                        ?.let { thread -> scrmFloatingContactConversationId(thread.groupId) }
                    if (route == null || recipientId.isNullOrBlank() ||
                        (selectedThread is ChatThreadSelection.GroupChat && roomId.isNullOrBlank())
                    ) {
                        paymentOperationStatus = "未提交：缺少真实 SCRM 账号、收款人或群聊 wxid"
                        return@FloatingBottomPanel
                    }
                    paymentOperationInProgress = true
                    paymentOperationStatus = "正在提交转账并等待设备任务终态"
                    coroutineScope.launch {
                        val intent = "transfer:${route.deviceUuid}:${route.weChatId}:$recipientId:${roomId.orEmpty()}:$amount:$note"
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val session = ScrmSettingsManager(context.applicationContext)
                                    .loadSelectedSessionOrBootstrap()
                                val api = session.readApi as? ScrmPaymentApi
                                    ?: error("当前 SCRM 客户端不支持发送转账")
                                val request = PaymentRequestFactory.sendRemittance(
                                    route = route,
                                    recipientId = recipientId,
                                    roomId = roomId,
                                    amountText = amount,
                                    paymentPassword = paymentPassword,
                                    memo = note
                                )
                                val key = paymentIdempotencyRegistry.keyFor(intent, paymentPassword)
                                PaymentTaskRunner(session.taskApi).submitAndAwait {
                                    api.sendRemittance(request, key)
                                }
                            }
                        }
                        paymentOperationInProgress = false
                        result.onSuccess { outcome ->
                            paymentOperationStatus = outcome.message
                            if (outcome.completed) {
                                paymentIdempotencyRegistry.complete(intent, paymentPassword)
                                toolMessageActions.addToolMessage(FloatingChatToolAction.Transfer) { message ->
                                    message.copy(
                                        text = "转账 ¥$amount",
                                        appName = "微信转账",
                                        detail = transferMessageDetailForRecipient(targetRecipient?.name, note),
                                        cardName = targetRecipient?.name,
                                        cardSubtitle = targetRecipient?.description,
                                        resourceUrl = transferResourceUrlWithRecipient(
                                            resourceUrl = message.resourceUrl,
                                            recipientId = targetRecipient?.id
                                        ),
                                        remoteTaskId = outcome.taskId,
                                        sendState = FloatingChatSendState.Succeeded
                                    )
                                }
                            }
                        }.onFailure { error ->
                            paymentOperationStatus = "转账提交失败：${error.message ?: "未知错误"}"
                        }
                    }
                },
                onSendLocation = { location ->
                    toolMessageActions.addToolMessage(FloatingChatToolAction.Location) { message ->
                        message.copy(
                            text = location.title,
                            locationTitle = location.title,
                            locationAddress = location.address,
                            resourceUrl = location.geoUri
                        )
                    }
                },
                onSendAccountCard = { accountId -> toolMessageActions.sendAccountCard(accountId) },
                modifier = if (displayedBottomPanelMode.isCenteredToolFeaturePanel()) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 18.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                },
                composerHeader = if (displayedBottomPanelMode.isBottomComposerDrawer()) {
                    {
                        BottomInputBar(
                            inputText = inputText,
                            onInputTextChange = {
                                inputText = it
                                blinkGeneratedInputClearable = false
                            },
                            quotedMessage = quotedMessage,
                            onClearQuote = { quotedMessage = null },
                            aiGeneratedClearable = blinkGeneratedInputClearable,
                            onClearAiGeneratedInput = {
                                inputText = ""
                                blinkGeneratedInputClearable = false
                                blinkInputStatusText = null
                                blinkInputStatusAutoDismiss = false
                            },
                            inputFocused = inputFocused,
                            onInputFocusedChange = { inputFocused = it },
                            panelMode = bottomPanelMode,
                            onPanelModeChange = { nextMode ->
                                if (nextMode == BottomPanelMode.Emoji || nextMode == BottomPanelMode.More) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                                bottomPanelMode = nextMode
                            },
                            onSend = { inputMessageActions.sendInputMessage() },
                            onAssistantPredict = { aiDraftGenerationActions.generate() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    null
                }
            )
            }
        }
        }
        },
        overlayContent = {
        FloatingChatExpandedBottomGestureBar(
            onGesture = currentOnBottomGesture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .zIndex(48f)
        )
        FavoriteCollectionOverlayHost(
            previewItem = favoritePreviewItem,
            longPressItem = favoriteLongPressItem,
            longPressAnchorBounds = favoriteLongPressAnchorBounds,
            favoriteActions = favoriteCollectionActions,
            selectedFavoriteItemIds = selectedFavoriteItemIds,
            onPreviewItemChanged = { item -> favoritePreviewItem = item },
            onLongPressItemChanged = { item, bounds ->
                favoriteLongPressItem = item
                favoriteLongPressAnchorBounds = bounds
            },
            onMultiSelectModeChanged = { enabled -> favoriteMultiSelectMode = enabled },
            modifier = Modifier
        )
        AiDraftOverlayHost(
            actionMessage = aiDraftActionMessage,
            editMessage = aiDraftEditMessage,
            draftMessageActions = aiDraftMessageActions,
            draftGenerationActions = aiDraftGenerationActions,
            onActionMessageChanged = { message -> aiDraftActionMessage = message },
            onEditMessageChanged = { message -> aiDraftEditMessage = message },
            modifier = Modifier
        )
        MessageForwardingOverlayHost(
            forwardMessage = forwardMessage,
            forwardModeMessages = forwardModeMessages,
            pendingForwardMessages = pendingForwardMessages,
            pendingForwardMode = pendingForwardMode,
            forwardTargetConversation = forwardTargetConversationFor(
                conversation = contactProfiledConversation,
                profiles = contactProfileList
            ),
            forwardingActions = messageForwardingActions,
            onForwardMessageChanged = { message -> forwardMessage = message },
            onForwardModeMessagesChanged = { messages -> forwardModeMessages = messages },
            onPendingForwardChanged = { messages, mode ->
                pendingForwardMessages = messages
                pendingForwardMode = mode
            },
            onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
            modifier = Modifier
        )
        MessageInteractionOverlayHost(
            paymentDetailMessage = paymentDetailMessage,
            longPressMessage = longPressMessage,
            longPressAnchorBounds = longPressAnchorBounds,
            asideAnalysisState = asideAnalysisState,
            textZoomMessage = textZoomMessage,
            multiSelectMode = multiSelectMode,
            chatHistoryPreviewMessage = chatHistoryPreviewMessage,
            selectedThread = selectedThread,
            selectedAccount = selectedAccount,
            selectedMessages = selectedMessagesForCurrentAction,
            messageLongPressActions = messageLongPressActions,
            onPaymentDetailMessageChanged = { message ->
                paymentDetailMessage = message
                paymentReadback = PaymentReadback()
            },
            isPaymentClaimed = { message -> claimedPaymentMessageIds[message.id] == true },
            paymentReadback = paymentReadback,
            onRefreshPaymentStatus = { message ->
                if (message.isTransferPaymentCard()) {
                    paymentReadback = PaymentReadback(
                        state = PaymentReadbackState.UNKNOWN,
                        message = "转账状态需使用支付任务回读；当前接口文档未提供按消息查询转账详情的只读接口"
                    )
                    return@MessageInteractionOverlayHost
                }
                val route = scrmFloatingAccountRouteForContactId(selectedAccount.id)
                val messageId = message.remoteMessageId
                val msgSvrId = message.remoteMessageServerId
                if (route == null || messageId == null || messageId <= 0L || msgSvrId.isNullOrBlank()) {
                    paymentReadback = PaymentReadback(
                        state = PaymentReadbackState.FAILED,
                        message = "缺少 SCRM 账号、messageId 或真实 msgSvrId，已阻止查询"
                    )
                } else {
                    paymentReadback = PaymentReadback(state = PaymentReadbackState.LOADING)
                    coroutineScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val session = ScrmSettingsManager(context.applicationContext)
                                    .loadSelectedSessionOrBootstrap()
                                val api = session.readApi as? ScrmPaymentApi
                                    ?: error("当前 SCRM 客户端不支持支付状态查询")
                                val runner = PaymentTaskRunner(session.taskApi)
                                val request = PaymentRequestFactory.redPacketQuery(
                                    route = route,
                                    serverMessageId = messageId,
                                    msgSvrId = msgSvrId
                                )
                                val status = runner.submitAndAwait { api.getRedPacketStatus(request) }
                                if (!status.completed) return@withContext status.toPaymentReadback()
                                val detail = runner.submitAndAwait { api.getRedPacketDetail(request) }
                                detail.toPaymentReadback(
                                    detail = detail.data?.let(PaymentDetailParser::parseRedPacket)
                                )
                            }
                        }.onSuccess { readback ->
                            paymentReadback = readback
                        }.onFailure { error ->
                            paymentReadback = PaymentReadback(
                                state = PaymentReadbackState.FAILED,
                                message = error.message ?: "支付状态查询失败"
                            )
                        }
                    }
                }
            },
            onClaimPayment = { message ->
                val route = scrmFloatingAccountRouteForContactId(selectedAccount.id)
                val messageId = message.remoteMessageId
                val msgSvrId = message.remoteMessageServerId
                if (route == null || messageId == null || messageId <= 0L || msgSvrId.isNullOrBlank()) {
                    paymentReadback = PaymentReadback(
                        state = PaymentReadbackState.FAILED,
                        message = "缺少 SCRM 账号、messageId 或真实 msgSvrId，已阻止领取"
                    )
                    return@MessageInteractionOverlayHost
                }
                paymentReadback = PaymentReadback(
                    state = PaymentReadbackState.PROCESSING,
                    message = "正在提交领取任务并等待设备终态"
                )
                coroutineScope.launch {
                    val intent = if (message.isTransferPaymentCard()) {
                        "take-transfer:${route.deviceUuid}:${messageId}"
                    } else {
                        "take-lucky:${route.deviceUuid}:${messageId}"
                    }
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val session = ScrmSettingsManager(context.applicationContext)
                                .loadSelectedSessionOrBootstrap()
                            val api = session.readApi as? ScrmPaymentApi
                                ?: error("当前 SCRM 客户端不支持领取支付任务")
                            val key = paymentIdempotencyRegistry.keyFor(intent)
                            val outcome = PaymentTaskRunner(session.taskApi).submitAndAwait {
                                if (message.isTransferPaymentCard()) {
                                    api.takeTransfer(
                                        PaymentRequestFactory.takeTransfer(route, messageId, msgSvrId),
                                        key
                                    )
                                } else {
                                    api.takeLuckyMoney(
                                        PaymentRequestFactory.takeLuckyMoney(route, messageId, msgSvrId),
                                        key
                                    )
                                }
                            }
                            outcome
                        }
                    }
                    result.onSuccess { outcome ->
                        paymentReadback = outcome.toPaymentReadback()
                        if (outcome.completed) {
                            paymentIdempotencyRegistry.complete(intent)
                            claimedPaymentMessageIds[message.id] = true
                            paymentReadback = PaymentReadback(
                                state = PaymentReadbackState.RECEIVED,
                                taskId = outcome.taskId,
                                message = outcome.message
                            )
                        }
                    }.onFailure { error ->
                        paymentReadback = PaymentReadback(
                            state = PaymentReadbackState.FAILED,
                            message = error.message ?: "领取支付任务失败"
                        )
                    }
                }
            },
            onLongPressMessageChanged = { message -> longPressMessage = message },
            onAsideAnalysisDismissed = { messageAsideAnalysisActions.dismiss() },
            onTextZoomDismissed = { textZoomMessage = null },
            onStartForwardingMessages = startForwardingMessages,
            onStartCombinedForwardingMessages = { messages ->
                if (messages.isNotEmpty()) {
                    pendingForwardMessages = messages
                    pendingForwardMode = MultiForwardMode.Combined
                }
            },
            onClearSelectedMessages = { selectedMessageIds.clear() },
            onMultiSelectModeChanged = { enabled -> multiSelectMode = enabled },
            onChatHistoryPreviewMessageChanged = { message -> chatHistoryPreviewMessage = message },
            modifier = Modifier,
            multiSelectBarModifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 48.dp, end = 48.dp, bottom = 64.dp)
        )
        scrmMessageOperationTarget?.let { message ->
            ScrmMessageOperationPreviewPanel(
                message = message,
                selectedAccountId = selectedAccount.id,
                onDismiss = { scrmMessageOperationTarget = null }
            )
        }
        ProfileEditorOverlayHost(
            contactEditorTarget = contactEditorTarget,
            accountEditorTarget = accountEditorTarget,
            selectedAccount = selectedAccount,
            groupProfiles = groupProfiles,
            visibleMessages = visibleMessagesForThread(
                conversation = displayConversation,
                selection = selectedThread,
                selectedAccountId = selectedAccount.id
            ),
            contacts = displayConversation.contacts,
            contactProfiles = contactProfiles,
            accountProfile = { account -> accountProfiles[account.id] ?: defaultAccountProfileFor(account) },
            groupMemberAddFriendTargetId = groupMemberAddFriendTargetId,
            groupMemberAddFriendLoading = groupMemberAddFriendLoading,
            groupMemberAddFriendStatus = groupMemberAddFriendStatus,
            groupMemberAddFriendError = groupMemberAddFriendError,
            onGroupProfileChange = { profile -> profilePersistenceActions.updateGroupProfile(profile) },
            onContactProfileChange = { profile -> profilePersistenceActions.updateContactProfile(profile) },
            onDeleteFriend = { contact -> contactRemoteTaskActions.deleteFriendFromProfile(contact) },
            onOpenPrivateChat = { contact -> chatNavigationActions.openChatThread(ChatThreadSelection.Private(contact.id)) },
            onAddFriendFromGroupMember = { member -> contactRemoteTaskActions.addFriendFromGroupMember(member) },
            onContactEditorTargetChanged = { target -> contactEditorTarget = target },
            onPickAccountAvatar = { account -> toolMessageActions.pickAccountAvatar(account.id) },
            onSaveAccountProfile = { account, profile ->
                profilePersistenceActions.updateAccountProfile(account.id, profile)
            },
            onAccountEditorTargetChanged = { account -> accountEditorTarget = account },
            modifier = Modifier
        )
        MediaOverlayHost(
            actionMessage = mediaOverlayState.actionMessage,
            previewSession = runtimeState.previewSession,
            documentPreviewMessage = runtimeState.documentPreviewMessage,
            onCloseActions = { mediaOverlayState.closeActions() },
            onMediaAction = { message, action ->
                mediaActionDispatchActions.handleMediaAction(message, action)
            },
            onCloseMediaPreview = { runtimeState.closeMediaPreview() },
            onCloseDocumentPreview = { runtimeState.closeDocumentPreview() },
            onOpenExternalDocument = onOpenExternalDocument,
            onShowToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
            modifier = Modifier
        )
        }
    )
}
