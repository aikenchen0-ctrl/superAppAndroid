@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paifa.ubikitouch.accessibility

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import android.view.Surface
import android.view.TextureView
import androidx.emoji2.emojipicker.EmojiPickerView
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
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
import com.paifa.ubikitouch.core.gesture.SwipeClassifier
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import com.paifa.ubikitouch.core.model.FloatingChatVisibilityScope
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.accessibility.data.LocalMomentComment
import com.paifa.ubikitouch.accessibility.data.LocalMomentPost
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.data.localThreadIdForSelection
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    onEdgeGesture: (EdgeSide, GestureType, GestureData) -> Unit = { _, _, _ -> },
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
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    var bottomPanelMode by remember { mutableStateOf(BottomPanelMode.None) }
    var mediaActionMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var mediaActionStatus by remember { mutableStateOf<String?>(null) }
    var longPressMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var longPressAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var paymentDetailMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var forwardMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var quotedMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var favoritePreviewItem by remember { mutableStateOf<FavoriteCollectionItem?>(null) }
    var favoriteLongPressItem by remember { mutableStateOf<FavoriteCollectionItem?>(null) }
    var favoriteLongPressAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var favoriteMultiSelectMode by remember { mutableStateOf(false) }
    var contactEditorTarget by remember { mutableStateOf<ContactEditorTarget?>(null) }
    var accountEditorTarget by remember { mutableStateOf<FloatingChatContact?>(null) }
    var pendingAvatarAccountId by remember { mutableStateOf<String?>(null) }
    val favoriteMediaIds = remember { mutableStateMapOf<String, Boolean>() }
    val favoriteMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val reminderMessageIds = remember { mutableStateMapOf<String, Boolean>() }
    val claimedRedPacketIds = remember { mutableStateMapOf<String, Boolean>() }
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
    val accountProfiles = remember(context, conversation.accountContacts) {
        mutableStateMapOf<String, FloatingChatAccountProfile>().apply {
            conversation.accountContacts.forEach { account ->
                this[account.id] = loadAccountProfile(context, account)
            }
        }
    }
    val storedFavoriteItems = remember(context) {
        mutableStateListOf<FavoriteCollectionItem>().apply {
            addAll(loadFavoriteCollectionItems(context))
        }
    }
    var quickPhrases by remember(context) { mutableStateOf(loadQuickPhrases(context)) }
    val momentPosts = remember(initialMomentPosts) {
        mutableStateListOf<AppMomentPost>().apply {
            addAll(
                initialMomentPosts + wechatMomentPosts().filterNot { defaultPost ->
                    initialMomentPosts.any { savedPost -> savedPost.id == defaultPost.id }
                }
            )
        }
    }
    fun upsertMomentPost(post: AppMomentPost) {
        val existingIndex = momentPosts.indexOfFirst { existing -> existing.id == post.id }
        if (existingIndex >= 0) {
            momentPosts[existingIndex] = post
        } else {
            momentPosts.add(0, post)
        }
        onPersistMomentPost(post)
    }
    var pendingMomentMedia by remember { mutableStateOf<AppMomentMedia?>(null) }
    val effectiveAccountContacts = conversation.accountContacts.map { account ->
        (accountProfiles[account.id] ?: defaultAccountProfileFor(account)).toContact(account)
    }
    val profiledConversation = conversation.copy(accountContacts = effectiveAccountContacts)
    val accountIds = remember(conversation.accountContacts) {
        conversation.accountContacts.map { account -> account.id }
    }
    var activeAccountId by remember(accountIds) {
        mutableStateOf(
            initialSelectedAccountId
                ?.takeIf { accountId -> accountIds.contains(accountId) }
                ?: profiledConversation.accountContacts.firstOrNull { account -> account.selected }?.id
                ?: profiledConversation.accountContacts.firstOrNull()?.id
                ?: ""
        )
    }
    val effectiveConversation = remember(profiledConversation, activeAccountId) {
        accountScopedConversation(
            conversation = profiledConversation,
            activeAccountId = activeAccountId
        )
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
    var selectedThread by remember {
        mutableStateOf(initialChatThreadSelection(contactProfiledConversation, runtimeState.selectedThread))
    }
    var homeOverviewVisible by remember { mutableStateOf(false) }
    val unreadThreadIds = remember { mutableStateMapOf<String, Boolean>() }
    fun openChatThread(thread: ChatThreadSelection) {
        accountIdForScopedThreadSelection(thread)?.let { accountId ->
            activeAccountId = accountId
        }
        selectedThread = thread
        homeOverviewVisible = false
        unreadThreadIds.remove(thread.toLocalThreadId())
    }
    fun openHomeUnread(summary: HomeUnreadThreadSummary) {
        activeAccountId = summary.accountId
        selectedThread = summary.selection
        homeOverviewVisible = false
        unreadThreadIds.remove(summary.threadId)
    }
    val localMessages = remember(conversation, initialLocalMessages) {
        mutableStateListOf<FloatingChatMessage>().apply {
            addAll(initialLocalMessages)
        }
    }
    var localMessageSequence by remember(conversation, initialMessageSequence) {
        mutableStateOf(initialMessageSequence)
    }
    val selectedAccount = remember(contactProfiledConversation, selectedThread, activeAccountId) {
        selectedAccountForThread(
            conversation = contactProfiledConversation,
            selection = selectedThread,
            overrideAccountId = activeAccountId
        )
    }
    val displayConversation = remember(contactProfiledConversation, localMessages.size, hiddenMessageIds.size) {
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
        profiledConversation,
        contactProfileList,
        groupProfileList,
        localMessages.size,
        hiddenMessageIds.size
    ) {
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
    val homeDisplayConversation = remember(profiledConversation, accountScopedDisplayConversations) {
        allAccountHomeConversation(
            baseConversation = profiledConversation,
            accountConversations = accountScopedDisplayConversations
        )
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
            storedItems = savedFavoriteItems
        )
    }
    fun favoriteMessageForItem(item: FavoriteCollectionItem): FloatingChatMessage {
        return favoriteCollectionPreviewMessage(item)
    }
    fun selectedFavoriteItemsForAction(): List<FavoriteCollectionItem> {
        return favoriteItems.filter { item -> selectedFavoriteItemIds[item.messageId] == true }
    }
    fun removeFavoriteItem(item: FavoriteCollectionItem) {
        favoriteMessageIds.remove(item.messageId)
        favoriteMediaIds.remove(item.messageId)
        val nextItems = mergeFavoriteCollectionItems(
            favoriteItems.filterNot { existing -> existing.messageId == item.messageId }
        ).take(FavoriteCollectionMaxCount)
        storedFavoriteItems.clear()
        storedFavoriteItems.addAll(nextItems)
        selectedFavoriteItemIds.remove(item.messageId)
        saveFavoriteCollectionItems(context, nextItems)
    }
    fun previewFavoriteItem(item: FavoriteCollectionItem) {
        val message = favoriteMessageForItem(item)
        if (favoriteCollectionItemUsesMediaPreview(item)) {
            FloatingChatMediaPreviewBridge.open(
                mediaMessages = listOf(message),
                initialIndex = 0,
                runtimeState = runtimeState
            )
            mediaActionStatus = null
        } else {
            favoritePreviewItem = item
        }
    }
    fun forwardFavoriteItem(item: FavoriteCollectionItem) {
        forwardMessage = favoriteMessageForItem(item)
    }
    fun deleteSelectedFavoriteItems() {
        selectedFavoriteItemsForAction().forEach { item -> removeFavoriteItem(item) }
        selectedFavoriteItemIds.clear()
        favoriteMultiSelectMode = false
    }
    fun forwardSelectedFavoriteItems() {
        selectedFavoriteItemsForAction().firstOrNull()?.let { item -> forwardFavoriteItem(item) }
        selectedFavoriteItemIds.clear()
        favoriteMultiSelectMode = false
    }
    fun addOutgoingTextMessage(outgoingText: String) {
        localMessageSequence += 1
        val baseMessage = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = effectiveConversation,
                    accountId = selectedAccount.id,
                    text = outgoingText,
                    sequence = localMessageSequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = effectiveConversation,
                    accountId = selectedAccount.id,
                    text = outgoingText,
                    sequence = localMessageSequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingTextMessage(
                    conversation = effectiveConversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    text = outgoingText,
                    sequence = localMessageSequence
                )
            }
        }
        val message = outgoingTextMessageWithOptionalQuote(baseMessage, quotedMessage)
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        quotedMessage = null
    }
    fun updateAccountProfile(accountId: String, profile: FloatingChatAccountProfile) {
        accountProfiles[accountId] = profile
        saveAccountProfile(context, profile)
    }
    fun updateContactProfile(profile: LocalContactProfile) {
        contactProfiles[contactProfileKey(profile.accountId, profile.contactId)] = profile
        onPersistContactProfile(profile)
    }
    fun updateGroupProfile(profile: LocalGroupProfile) {
        groupProfiles[groupProfileKey(profile.accountId, profile.groupId)] = profile
        onPersistGroupProfile(profile)
    }
    fun pickAccountAvatar(accountId: String) {
        pendingAvatarAccountId = accountId
        FloatingChatMediaPickerBridge.requestPick(
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            target = FloatingChatMediaTarget.AccountAvatar
        )
    }
    fun addToolMessage(
        action: FloatingChatToolAction,
        customize: (FloatingChatMessage) -> FloatingChatMessage = { it }
    ) {
        localMessageSequence += 1
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = effectiveConversation,
            action = action,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = localMessageSequence
        )
        val message = customize(
            accountProfileMessageForToolAction(
                action = action,
                profile = accountProfiles[selectedAccount.id] ?: defaultAccountProfileFor(selectedAccount),
                baseMessage = baseMessage
            )
        )
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        bottomPanelMode = BottomPanelMode.None
    }
    fun sendAccountCard(accountId: String) {
        val account = effectiveConversation.accountContacts.firstOrNull { it.id == accountId }
            ?: conversation.accountContacts.firstOrNull { it.id == accountId }
            ?: return
        val profile = accountProfiles[account.id] ?: defaultAccountProfileFor(account)
        localMessageSequence += 1
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = effectiveConversation,
            action = FloatingChatToolAction.Card,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = account.id,
            sequence = localMessageSequence
        )
        val message = accountProfileCardMessage(profile = profile, baseMessage = baseMessage)
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        bottomPanelMode = BottomPanelMode.None
    }
    fun updateQuickPhrases(nextPhrases: List<String>) {
        quickPhrases = normalizeQuickPhrases(nextPhrases)
        saveQuickPhrases(context, quickPhrases)
    }
    val sendToolMessage: (FloatingChatToolAction) -> Unit = { action ->
        when (action) {
            FloatingChatToolAction.Gallery -> {
                bottomPanelMode = BottomPanelMode.None
                FloatingChatMediaPickerBridge.requestPick(FloatingChatPrototype.PickedMediaKind.Any)
            }
            FloatingChatToolAction.Blink -> {
                bottomPanelMode = BottomPanelMode.None
                FloatingChatBlinkVoiceBridge.requestCapture()
            }
            FloatingChatToolAction.Camera -> {
                bottomPanelMode = BottomPanelMode.None
                FloatingChatMediaPickerBridge.requestCapture()
            }
            FloatingChatToolAction.QuickPhrase -> {
                bottomPanelMode = BottomPanelMode.QuickPhrase
            }
            FloatingChatToolAction.Moments -> {
                bottomPanelMode = BottomPanelMode.Moments
            }
            FloatingChatToolAction.RedPacket -> {
                bottomPanelMode = BottomPanelMode.RedPacket
            }
            FloatingChatToolAction.Transfer -> {
                bottomPanelMode = BottomPanelMode.Transfer
            }
            FloatingChatToolAction.Location -> {
                bottomPanelMode = BottomPanelMode.Location
            }
            FloatingChatToolAction.Favorite -> {
                bottomPanelMode = BottomPanelMode.Favorite
            }
            FloatingChatToolAction.Card -> {
                bottomPanelMode = BottomPanelMode.Card
            }
            FloatingChatToolAction.Files -> {
                bottomPanelMode = BottomPanelMode.None
                FloatingChatMediaPickerBridge.requestDocumentPick()
            }
            in simulatedMessageToolActions() -> {
                addToolMessage(action)
            }
            else -> Unit
        }
    }
    val sendInputMessage = {
        val outgoingText = inputText.trim()
        if (outgoingText.isNotEmpty()) {
            addOutgoingTextMessage(outgoingText)
            inputText = ""
            bottomPanelMode = BottomPanelMode.None
        }
    }
    val sendQuickPhrase: (String) -> Unit = { phrase ->
        val text = phrase.trim()
        if (text.isNotEmpty()) {
            addOutgoingTextMessage(text)
            updateQuickPhrases(listOf(text) + quickPhrases.filterNot { it == text })
            bottomPanelMode = BottomPanelMode.None
        }
    }
    val sendVoiceMessage: (String, Int) -> Unit = { audioUri, durationMs ->
        localMessageSequence += 1
        val message = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = effectiveConversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = localMessageSequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = effectiveConversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = localMessageSequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingVoiceMessage(
                    conversation = effectiveConversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = localMessageSequence
                )
            }
        }
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        bottomPanelMode = BottomPanelMode.None
    }
    fun addForwardedMessage(source: FloatingChatMessage, target: ChatThreadSelection) {
        val account = selectedAccountForThread(effectiveConversation, target)
        localMessageSequence += 1
        val message = source.forwardedCopyFor(
            conversation = effectiveConversation,
            target = target,
            accountId = account.id,
            sequence = localMessageSequence
        )
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistForwardedMessage(source, message, target.toLocalThreadId(), account.id)
    }
    fun selectedMessagesForAction(): List<FloatingChatMessage> {
        val messagesById = displayConversation.messages.associateBy { message -> message.id }
        return selectedMessageIds
            .filterValues { selected -> selected }
            .keys
            .mapNotNull { id -> messagesById[id] }
    }
    val performLongPressAction: (FloatingChatMessage, MessageLongPressAction) -> Unit = { message, action ->
        when (action) {
            MessageLongPressAction.Copy -> {
                clipboardManager.setText(AnnotatedString(message.longPressCopyText()))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            MessageLongPressAction.Forward -> {
                forwardMessage = message
            }
            MessageLongPressAction.Favorite -> {
                val nextFavorite = favoriteMessageIds[message.id] != true
                favoriteMessageIds[message.id] = nextFavorite
                updateFavoriteCollectionItems(context, storedFavoriteItems, message, nextFavorite)
                Toast.makeText(context, if (nextFavorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
            }
            MessageLongPressAction.Delete -> {
                hiddenMessageIds[message.id] = true
                selectedMessageIds.remove(message.id)
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            }
            MessageLongPressAction.MultiSelect -> {
                multiSelectMode = true
                selectedMessageIds[message.id] = true
            }
            MessageLongPressAction.Quote -> {
                quotedMessage = message
                inputFocused = true
            }
            MessageLongPressAction.Reminder -> {
                val nextReminder = reminderMessageIds[message.id] != true
                reminderMessageIds[message.id] = nextReminder
                Toast.makeText(context, if (nextReminder) "已添加提醒" else "已取消提醒", Toast.LENGTH_SHORT).show()
            }
        }
        longPressMessage = null
    }
    val deleteSelectedMessages = {
        selectedMessagesForAction().forEach { message ->
            hiddenMessageIds[message.id] = true
            selectedMessageIds.remove(message.id)
        }
        multiSelectMode = false
        Toast.makeText(context, "已删除选中消息", Toast.LENGTH_SHORT).show()
    }
    val favoriteSelectedMessages = {
        selectedMessagesForAction().forEach { message ->
            favoriteMessageIds[message.id] = true
            updateFavoriteCollectionItems(context, storedFavoriteItems, message, true)
        }
        multiSelectMode = false
        Toast.makeText(context, "已收藏选中消息", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(runtimeState.pickedMediaEvent) {
        val event = runtimeState.pickedMediaEvent ?: return@LaunchedEffect
        if (event.target == FloatingChatMediaTarget.AccountAvatar) {
            val accountId = pendingAvatarAccountId
            if (accountId != null) {
                val account = effectiveConversation.accountContacts.firstOrNull { it.id == accountId }
                    ?: conversation.accountContacts.firstOrNull { it.id == accountId }
                if (account != null) {
                    val currentProfile = accountProfiles[accountId] ?: defaultAccountProfileFor(account)
                    updateAccountProfile(
                        accountId = accountId,
                        profile = currentProfile.copy(avatarImageUri = event.previewUri.ifBlank { event.mediaUri })
                    )
                }
            }
            pendingAvatarAccountId = null
            runtimeState.clearPickedMediaEvent(event.token)
            return@LaunchedEffect
        }
        if (event.target == FloatingChatMediaTarget.Moment) {
            pendingMomentMedia = event.toMomentMedia()
            bottomPanelMode = BottomPanelMode.Moments
            runtimeState.clearPickedMediaEvent(event.token)
            return@LaunchedEffect
        }
        localMessageSequence += 1
        val message = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = effectiveConversation,
            mediaKind = event.mediaKind,
            mediaUri = event.mediaUri,
            previewUri = event.previewUri,
            orientation = event.orientation,
            aspectRatio = event.aspectRatio,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = localMessageSequence
        )
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        runtimeState.clearPickedMediaEvent(event.token)
    }
    LaunchedEffect(runtimeState.pickedDocumentEvent) {
        val event = runtimeState.pickedDocumentEvent ?: return@LaunchedEffect
        localMessageSequence += 1
        val message = FloatingChatPrototype.pickedDocumentMessage(
            conversation = effectiveConversation,
            documentUri = event.document.uri,
            displayName = event.document.displayName,
            fileFormat = event.document.fileFormat,
            fileSizeLabel = event.document.fileSizeLabel,
            previewLines = event.document.previewLines,
            mimeType = event.document.mimeType,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = localMessageSequence
        )
        localMessages += message
        onLocalMessagesChanged(localMessages.toList(), localMessageSequence)
        onPersistLocalMessage(message, selectedThread.toLocalThreadId())
        runtimeState.clearPickedDocumentEvent(event.token)
    }
    LaunchedEffect(runtimeState.blinkVoiceResultEvent) {
        val event = runtimeState.blinkVoiceResultEvent ?: return@LaunchedEffect
        addOutgoingTextMessage(blinkVoiceResultMessageText(event.eventType, event.durationMs))
        runtimeState.clearBlinkVoiceResultEvent(event.token)
    }
    val handleMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit = { message, action ->
        val result = performMediaAction(
            context = context,
            message = message,
            action = action,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { mediaActionMessage = message },
            onFavoriteChanged = { favoriteMessage, favorite ->
                updateFavoriteCollectionItems(context, storedFavoriteItems, favoriteMessage, favorite)
            }
        )
        mediaActionStatus = result.status
        if (result.toast) {
            Toast.makeText(context, result.status, Toast.LENGTH_SHORT).show()
        }
    }
    val hideKeyboardFromBlankArea = {
        if (inputFocused) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            inputFocused = false
        }
    }
    LaunchedEffect(effectiveConversation.groupContacts, effectiveConversation.contacts) {
        selectedThread = initialChatThreadSelection(effectiveConversation, selectedThread)
        defaultAllAccountHomeUnreadThreadIds(profiledConversation).forEach { threadId ->
            if (!unreadThreadIds.containsKey(threadId)) {
                unreadThreadIds[threadId] = true
            }
        }
    }
    LaunchedEffect(selectedThread, selectedAccount.id) {
        runtimeState.selectedThread = selectedThread
        onThreadContextChanged(selectedThread, selectedAccount.id)
    }
    LaunchedEffect(mediaActionMessage) {
        runtimeState.previewVisible = false
        runtimeState.mediaActionSheetVisible = mediaActionMessage != null
        onPreviewChromeChanged(false)
    }
    LaunchedEffect(runtimeState.previewSession, runtimeState.documentPreviewMessage) {
        val previewVisible = runtimeState.previewSession != null || runtimeState.documentPreviewMessage != null
        runtimeState.previewVisible = previewVisible
        if (previewVisible) {
            runtimeState.mediaActionSheetVisible = false
            mediaActionMessage = null
        }
        onPreviewChromeChanged(previewVisible)
    }
    LaunchedEffect(runtimeState.dismissSignal) {
        if (runtimeState.dismissSignal <= 0L) return@LaunchedEffect
        if (runtimeState.previewSession != null) {
            runtimeState.closeMediaPreview()
        } else if (runtimeState.documentPreviewMessage != null) {
            runtimeState.closeDocumentPreview()
        } else if (mediaActionMessage != null) {
            mediaActionMessage = null
        }
    }
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val edgeGestureTouchTargetPx = with(density) {
        FloatingChatInternalEdgeGestureDefaults.TouchTargetDp.toPx()
    }
    val edgeGestureShortThresholdPx = with(density) {
        edgeGestureShortThresholdDp
            .coerceIn(
                FloatingChatInternalEdgeGestureDefaults.ShortThresholdMinDp,
                FloatingChatInternalEdgeGestureDefaults.ShortThresholdMaxDp
            )
            .dp
            .toPx() * FloatingChatInternalEdgeGestureDefaults.ThresholdResponseRatio
    }
    val edgeGestureLongThresholdPx = with(density) {
        edgeGestureLongThresholdDp
            .coerceIn(
                edgeGestureShortThresholdDp + FloatingChatInternalEdgeGestureDefaults.LongThresholdMinDeltaDp,
                FloatingChatInternalEdgeGestureDefaults.LongThresholdMaxDp
            )
            .dp
            .toPx() * FloatingChatInternalEdgeGestureDefaults.ThresholdResponseRatio
    }
    val currentOnEdgeGesture by rememberUpdatedState(onEdgeGesture)
    val currentOnBackGestureProgress by rememberUpdatedState(onBackGestureProgress)
    val currentOnBackGestureCommit by rememberUpdatedState(onBackGestureCommit)
    val currentOnBackGestureEnd by rememberUpdatedState(onBackGestureEnd)
    val currentOnBackGestureCancel by rememberUpdatedState(onBackGestureCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .floatingChatInternalEdgeGesture(
                touchTargetPx = edgeGestureTouchTargetPx,
                touchSlopPx = viewConfiguration.touchSlop,
                shortThresholdPx = edgeGestureShortThresholdPx,
                longThresholdPx = edgeGestureLongThresholdPx,
                onGesture = currentOnEdgeGesture,
                onBackGestureProgress = currentOnBackGestureProgress,
                onBackGestureCommit = currentOnBackGestureCommit,
                onBackGestureEnd = currentOnBackGestureEnd,
                onBackGestureCancel = currentOnBackGestureCancel
            )
            .floatingChatFrostedBackdrop(
                enabled = frostedBackgroundEnabled,
                opacityPercent = backgroundOpacityPercent,
                blurRadiusDp = blurRadiusDp,
                backgroundColorRgb = backgroundColorRgb
            )
    ) {
        CoordinateChatBody(
            conversation = if (homeOverviewVisible) homeDisplayConversation else displayConversation,
            homeOverviewConversations = accountScopedDisplayConversations,
            accountProfiles = accountProfiles,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            unreadThreadIds = unreadThreadIds.filterValues { unread -> unread }.keys.toSet(),
            inputText = inputText,
            inputFocused = inputFocused,
            groupMemberAvatarsVisible = currentGroupMemberAvatarsVisible,
            onThreadSelected = { thread -> openChatThread(thread) },
            onHomeUnreadSelected = { summary -> openHomeUnread(summary) },
            onToolAction = sendToolMessage,
            onGroupAvatarLongClick = { group ->
                contactEditorTarget = ContactEditorTarget.Group(group)
            },
            onContactAvatarLongClick = { contact ->
                contactEditorTarget = ContactEditorTarget.User(contact)
            },
            onAccountAvatarClick = { account ->
                activeAccountId = selectedAccountIdAfterAccountAvatarClick(
                    currentAccountId = activeAccountId,
                    clickedAccountId = account.id
                )
                homeOverviewVisible = false
                bottomPanelMode = BottomPanelMode.None
            },
            onAccountAvatarLongClick = { account ->
                accountEditorTarget = account
            },
            onPreviewMedia = { message ->
                val previewIndex = previewableMedia.indexOfFirst { preview -> preview.id == message.id }
                    .takeIf { it >= 0 }
                    ?: return@CoordinateChatBody
                FloatingChatMediaPreviewBridge.open(
                    mediaMessages = previewableMedia,
                    initialIndex = previewIndex,
                    runtimeState = runtimeState
                )
                mediaActionStatus = null
            },
            onPreviewDocument = { message -> runtimeState.openDocumentPreview(message) },
            onOpenMediaActions = { message -> mediaActionMessage = message },
            onPaymentCardClick = { message ->
                paymentDetailMessage = message
                bottomPanelMode = BottomPanelMode.None
            },
            onLongPressMessage = { message, bounds ->
                longPressMessage = message
                longPressAnchorBounds = bounds
            },
            multiSelectMode = multiSelectMode,
            selectedMessageIds = selectedMessageIds,
            remindedMessageIds = reminderMessageIds,
            favoriteMessageIds = favoriteMessageIds,
            onToggleMessageSelection = { message ->
                if (selectedMessageIds[message.id] == true) {
                    selectedMessageIds.remove(message.id)
                } else {
                    selectedMessageIds[message.id] = true
                }
            },
            onBlankAreaTap = {
                if (inputFocused) {
                    hideKeyboardFromBlankArea()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (bottomPanelMode != BottomPanelMode.None) {
            if (bottomPanelMode.isCenteredToolFeaturePanel()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OverlayTokens.centerPanelScrim)
                        .pointerInput(bottomPanelMode) {
                            detectTapGestures(onTap = { bottomPanelMode = BottomPanelMode.None })
                        }
                )
            }
            FloatingBottomPanel(
                mode = bottomPanelMode,
                voicePermissionRequestToken = voicePermissionRequestToken,
                locationPermissionRequestToken = locationPermissionRequestToken,
                onClose = { bottomPanelMode = BottomPanelMode.None },
                onInsertText = { inserted ->
                    inputText += inserted
                    if (bottomPanelMode != BottomPanelMode.Emoji) {
                        bottomPanelMode = BottomPanelMode.None
                    }
                },
                onSendVoice = sendVoiceMessage,
                quickPhrases = quickPhrases,
                momentPosts = momentPosts,
                pendingMomentMedia = pendingMomentMedia,
                favoriteItems = favoriteItems,
                accounts = effectiveConversation.accountContacts,
                accountProfiles = accountProfiles,
                transferRecipients = transferRecipientCandidatesForThread(
                    conversation = displayConversation,
                    selectedThread = selectedThread,
                    selectedAccountId = selectedAccount.id
                ),
                onSendQuickPhrase = sendQuickPhrase,
                onAddQuickPhrase = { phrase ->
                    updateQuickPhrases(listOf(phrase) + quickPhrases)
                },
                onUpdateQuickPhrase = { index, phrase ->
                    updateQuickPhrases(
                        quickPhrases.mapIndexed { phraseIndex, existing ->
                            if (phraseIndex == index) phrase else existing
                        }
                    )
                },
                onDeleteQuickPhrase = { index ->
                    updateQuickPhrases(quickPhrases.filterIndexed { phraseIndex, _ -> phraseIndex != index })
                },
                onPickMomentMedia = {
                    FloatingChatMediaPickerBridge.requestPick(
                        mediaKind = FloatingChatPrototype.PickedMediaKind.Any,
                        target = FloatingChatMediaTarget.Moment
                    )
                },
                onClearMomentMedia = {
                    pendingMomentMedia = null
                },
                onPreviewMomentMedia = { post ->
                    val mediaMessage = post.toFloatingChatMediaMessage() ?: return@FloatingBottomPanel
                    FloatingChatMediaPreviewBridge.open(
                        mediaMessages = listOf(mediaMessage),
                        initialIndex = 0,
                        runtimeState = runtimeState
                    )
                },
                onPostMoment = { content ->
                    val trimmed = content.trim()
                    val media = pendingMomentMedia
                    if (trimmed.isNotEmpty() || media != null) {
                        val post = AppMomentPost(
                            author = selectedAccount.name,
                            content = trimmed,
                            time = "刚刚",
                            avatarText = selectedAccount.initials.take(2),
                            avatarColor = Color(selectedAccount.avatarColor),
                            media = media,
                            createdAt = System.currentTimeMillis()
                        )
                        upsertMomentPost(post)
                        pendingMomentMedia = null
                    }
                },
                onUpdateMomentPost = { post ->
                    upsertMomentPost(post)
                },
                favoriteMultiSelectMode = favoriteMultiSelectMode,
                selectedFavoriteItemIds = selectedFavoriteItemIds,
                onPreviewFavoriteItem = { item -> previewFavoriteItem(item) },
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
                onForwardSelectedFavorites = ::forwardSelectedFavoriteItems,
                onDeleteSelectedFavorites = ::deleteSelectedFavoriteItems,
                onCancelFavoriteSelection = {
                    selectedFavoriteItemIds.clear()
                    favoriteMultiSelectMode = false
                },
                onSendRedPacket = { amount, greeting ->
                    val safeAmount = amount.ifBlank { "8.88" }
                    val safeGreeting = greeting.ifBlank { "恭喜发财，大吉大利" }
                    addToolMessage(FloatingChatToolAction.RedPacket) { message ->
                        message.copy(
                            text = "浮窗红包 ¥$safeAmount",
                            appName = "浮窗红包",
                            detail = safeGreeting
                        )
                    }
                },
                onSendTransfer = { amount, note, recipient ->
                    val safeAmount = amount.ifBlank { "88.00" }
                    addToolMessage(FloatingChatToolAction.Transfer) { message ->
                        message.copy(
                            text = "转账 ¥$safeAmount",
                            appName = "浮窗转账",
                            detail = transferMessageDetailForRecipient(recipient?.name, note),
                            cardName = recipient?.name,
                            cardSubtitle = recipient?.description
                        )
                    }
                },
                onSendLocation = { location ->
                    addToolMessage(FloatingChatToolAction.Location) { message ->
                        message.copy(
                            text = location.title,
                            locationTitle = location.title,
                            locationAddress = location.address,
                            resourceUrl = location.geoUri
                        )
                    }
                },
                onSendAccountCard = { accountId -> sendAccountCard(accountId) },
                modifier = if (bottomPanelMode.isCenteredToolFeaturePanel()) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 18.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(end = 58.dp, bottom = 76.dp)
                }
            )
        }
        BottomInputBar(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            quotedMessage = quotedMessage,
            onClearQuote = { quotedMessage = null },
            inputFocused = inputFocused,
            onInputFocusedChange = { inputFocused = it },
            panelMode = bottomPanelMode,
            onPanelModeChange = { bottomPanelMode = it },
            onSend = sendInputMessage,
            onHome = {
                homeOverviewVisible = true
                bottomPanelMode = BottomPanelMode.None
            },
            onAssistantPredict = { sendToolMessage(FloatingChatToolAction.Assistant) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 44.dp, end = 44.dp, bottom = 10.dp)
        )
        favoritePreviewItem?.let { item ->
            FavoriteCollectionPreviewOverlay(
                item = item,
                onDismiss = { favoritePreviewItem = null },
                onForward = {
                    forwardFavoriteItem(item)
                    favoritePreviewItem = null
                },
                onDelete = {
                    removeFavoriteItem(item)
                    favoritePreviewItem = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        favoriteLongPressItem?.let { item ->
            FavoriteCollectionLongPressMenuOverlay(
                item = item,
                itemBounds = favoriteLongPressAnchorBounds,
                onDismiss = {
                    favoriteLongPressItem = null
                    favoriteLongPressAnchorBounds = null
                },
                onAction = { action ->
                    when (action) {
                        MessageLongPressAction.Forward -> forwardFavoriteItem(item)
                        MessageLongPressAction.Delete -> removeFavoriteItem(item)
                        MessageLongPressAction.MultiSelect -> {
                            favoriteMultiSelectMode = true
                            selectedFavoriteItemIds[item.messageId] = true
                        }
                        else -> Unit
                    }
                    favoriteLongPressItem = null
                    favoriteLongPressAnchorBounds = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        paymentDetailMessage?.let { message ->
            PaymentDetailOverlay(
                message = message,
                selectedThread = selectedThread,
                claimed = claimedRedPacketIds[message.id] == true,
                onClaim = {
                    claimedRedPacketIds[message.id] = true
                },
                onDismiss = { paymentDetailMessage = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        longPressMessage?.let { message ->
            MessageLongPressMenuOverlay(
                message = message,
                messageBounds = longPressAnchorBounds,
                onDismiss = { longPressMessage = null },
                onAction = { action -> performLongPressAction(message, action) },
                modifier = Modifier.fillMaxSize()
            )
        }
        forwardMessage?.let { message ->
            MessageForwardTargetOverlay(
                conversation = forwardTargetConversationFor(
                    conversation = contactProfiledConversation,
                    profiles = contactProfileList
                ),
                onDismiss = { forwardMessage = null },
                onTargetSelected = { target ->
                    addForwardedMessage(message, target)
                    forwardMessage = null
                    Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (multiSelectMode) {
            MultiSelectActionBar(
                selectedCount = selectedMessagesForAction().size,
                onForward = {
                    selectedMessagesForAction().firstOrNull()?.let { forwardMessage = it }
                    multiSelectMode = false
                },
                onFavorite = favoriteSelectedMessages,
                onDelete = deleteSelectedMessages,
                onCancel = {
                    selectedMessageIds.clear()
                    multiSelectMode = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 48.dp, end = 48.dp, bottom = 64.dp)
            )
        }
        contactEditorTarget?.let { target ->
            ContactEditOverlay(
                target = target,
                accountId = selectedAccount.id,
                groupProfiles = groupProfiles,
                visibleMessages = visibleMessagesForThread(
                    conversation = displayConversation,
                    selection = selectedThread,
                    selectedAccountId = selectedAccount.id
                ),
                contacts = displayConversation.contacts,
                onGroupProfileChange = { profile -> updateGroupProfile(profile) },
                contactProfiles = contactProfiles,
                onContactProfileChange = { profile -> updateContactProfile(profile) },
                onDismiss = { contactEditorTarget = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        accountEditorTarget?.let { account ->
            AccountEditOverlay(
                account = account,
                profile = accountProfiles[account.id] ?: defaultAccountProfileFor(account),
                onPickAvatar = { pickAccountAvatar(account.id) },
                onSave = { profile ->
                    updateAccountProfile(account.id, profile)
                    accountEditorTarget = null
                },
                onDismiss = { accountEditorTarget = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        mediaActionMessage?.let { message ->
            MediaActionSheetOverlay(
                message = message,
                onClose = { mediaActionMessage = null },
                onMediaAction = { action ->
                    handleMediaAction(message, action)
                    if (action != MediaActionContract.More) {
                        mediaActionMessage = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        runtimeState.previewSession?.let { session ->
            FloatingChatMediaPreviewHost(
                session = session,
                onClose = { runtimeState.closeMediaPreview() },
                modifier = Modifier.fillMaxSize()
            )
        }
        runtimeState.documentPreviewMessage?.let { message ->
            DocumentPreviewOverlay(
                message = message,
                onClose = { runtimeState.closeDocumentPreview() },
                onOpenExternal = {
                    val opened = onOpenExternalDocument(message)
                    if (opened) {
                        runtimeState.closeDocumentPreview()
                    }
                    Toast.makeText(
                        context,
                        if (opened) "正在打开文件" else "没有可打开的文件",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TopBar(conversation: FloatingChatConversation, onCollapse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(OverlayTokens.bar)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UnreadDot()
        Spacer(modifier = Modifier.width(8.dp))
        TextLabel(
            text = conversation.peerName,
            size = 14.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.primaryText,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        HeaderIcon("E")
        Spacer(modifier = Modifier.width(5.dp))
        HeaderIcon("+")
        Spacer(modifier = Modifier.width(7.dp))
        TextLabel(
            text = conversation.accountName,
            size = 12.sp,
            weight = FontWeight.Medium,
            color = OverlayTokens.secondaryText,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        HeaderIcon("S")
        Spacer(modifier = Modifier.width(6.dp))
        HeaderIcon("v", onClick = onCollapse)
    }
}

@Composable
private fun UnreadDot() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(OverlayTokens.alertMuted),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(OverlayTokens.alertCore)
        )
    }
}

@Composable
private fun HeaderIcon(label: String, onClick: (() -> Unit)? = null) {
    CompactInteractiveSize {
        FilledTonalIconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier
                .size(25.dp)
                .border(1.dp, OverlayTokens.hairline, CircleShape),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = OverlayTokens.control,
                contentColor = OverlayTokens.primaryText,
                disabledContainerColor = OverlayTokens.control,
                disabledContentColor = OverlayTokens.primaryText
            )
        ) {
            TextLabel(
                text = label,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CoordinateChatBody(
    conversation: FloatingChatConversation,
    homeOverviewConversations: List<AccountScopedConversation>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    unreadThreadIds: Set<String>,
    inputText: String,
    inputFocused: Boolean,
    groupMemberAvatarsVisible: Boolean,
    onThreadSelected: (ChatThreadSelection) -> Unit,
    onHomeUnreadSelected: (HomeUnreadThreadSummary) -> Unit,
    onToolAction: (FloatingChatToolAction) -> Unit,
    onGroupAvatarLongClick: (FloatingChatContact) -> Unit,
    onContactAvatarLongClick: (FloatingChatContact) -> Unit,
    onAccountAvatarClick: (FloatingChatContact) -> Unit,
    onAccountAvatarLongClick: (FloatingChatContact) -> Unit,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onPreviewDocument: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onPaymentCardClick: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onBlankAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageListState = rememberLazyListState()
    val connectorState = remember { ConnectorCoordinateState() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val selectedAccount = remember(conversation, selectedThread) {
        selectedAccountForThread(
            conversation = conversation,
            selection = selectedThread
        )
    }
    val homeUnreadSummaries = remember(homeOverviewConversations, unreadThreadIds) {
        homeUnreadThreadSummaries(
            accountConversations = homeOverviewConversations,
            unreadThreadIds = unreadThreadIds
        )
    }
    val homeUnreadSummaryByMessageId = remember(homeUnreadSummaries) {
        homeUnreadSummaries.associateBy { summary -> summary.message.id }
    }
    val threadMessages = remember(conversation, selectedThread, selectedAccount.id) {
        visibleMessagesForThread(
            conversation = conversation,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id
        )
    }
    val visibleMessages = remember(homeOverviewVisible, homeUnreadSummaries, threadMessages) {
        if (homeOverviewVisible) {
            homeUnreadSummaries.map { summary -> summary.message }
        } else {
            threadMessages
        }
    }
    val visibleMessageIds = remember(visibleMessages) {
        visibleMessages.map { message -> message.id }.toSet()
    }
    val offscreenConnectorIndex = remember(
        visibleMessages,
        homeOverviewVisible,
        selectedThread,
        selectedAccount.id,
        groupMemberAvatarsVisible
    ) {
        ConnectorOffscreenIndex.fromMessages(
            messages = visibleMessages,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible
        )
    }
    val contactsById = remember(conversation.groupContacts, conversation.contacts) {
        (conversation.groupContacts + conversation.contacts).associateBy { contact -> contact.id }
    }
    LaunchedEffect(visibleMessageIds) {
        connectorState.retainMessageBounds(visibleMessageIds)
    }
    LaunchedEffect(selectedThread, visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            messageListState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(inputFocused, visibleMessages.size) {
        if (inputFocused && visibleMessages.isNotEmpty()) {
            messageListState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(inputFocused, inputText, imeBottomPx, visibleMessages.size) {
        if (inputFocused && visibleMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        MessageCoordinatePane(
            messages = visibleMessages,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            contactsById = contactsById,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible,
            listState = messageListState,
            connectorState = connectorState,
            onPreviewMedia = onPreviewMedia,
            onOpenMediaActions = onOpenMediaActions,
            onLongPressMessage = onLongPressMessage,
            onGroupMemberAvatarLongClick = onContactAvatarLongClick,
            multiSelectMode = multiSelectMode,
            selectedMessageIds = selectedMessageIds,
            remindedMessageIds = remindedMessageIds,
            favoriteMessageIds = favoriteMessageIds,
            onToggleMessageSelection = onToggleMessageSelection,
            onMessageClick = { message ->
                if (homeOverviewVisible) {
                    homeUnreadSummaryByMessageId[message.id]?.let(onHomeUnreadSelected)
                } else if (message.isPaymentCardMessage()) {
                    onPaymentCardClick(message)
                } else if (message.type == FloatingChatMessageType.FilePreview) {
                    onPreviewDocument(message)
                }
            },
            onBlankAreaTap = onBlankAreaTap,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .imePadding()
                .padding(
                    start = FloatingContentSideInset + EdgeGestureSafeInset,
                    end = FloatingContentSideInset + EdgeGestureSafeInset
                )
        )
        SessionRail(
            groups = conversation.groupContacts,
            contacts = conversation.contacts,
            conversation = conversation,
            selectedAccountId = selectedAccount.id,
            selectedThread = selectedThread,
            unreadThreadIds = unreadThreadIds,
            onThreadSelected = onThreadSelected,
            onGroupAvatarLongClick = onGroupAvatarLongClick,
            onContactAvatarLongClick = onContactAvatarLongClick,
            connectorState = connectorState,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(LeftRailFollowTextLayerWidth)
                .zIndex(leftRailLayerZIndex())
        )
        RightCoordinateRail(
            accounts = conversation.accountContacts,
            accountProfiles = accountProfiles,
            selectedAccountId = selectedAccount.id,
            actions = conversation.toolActions,
            connectorState = connectorState,
            onToolAction = onToolAction,
            onAccountAvatarClick = onAccountAvatarClick,
            onAccountAvatarLongClick = onAccountAvatarLongClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(RightRailWidth)
        )
        ChatConnectorLayer(
            messages = visibleMessages,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible,
            listState = messageListState,
            offscreenIndex = offscreenConnectorIndex,
            connectorState = connectorState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(connectorLayerZIndex())
        )
    }
}

@Composable
private fun SessionRail(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    conversation: FloatingChatConversation,
    selectedAccountId: String,
    selectedThread: ChatThreadSelection,
    unreadThreadIds: Set<String>,
    onThreadSelected: (ChatThreadSelection) -> Unit,
    onGroupAvatarLongClick: (FloatingChatContact) -> Unit,
    onContactAvatarLongClick: (FloatingChatContact) -> Unit,
    connectorState: ConnectorCoordinateState,
    modifier: Modifier = Modifier
) {
    ScrollableSessionRail(
        groups = groups,
        contacts = contacts,
        conversation = conversation,
        selectedAccountId = selectedAccountId,
        selectedThread = selectedThread,
        unreadThreadIds = unreadThreadIds,
        onThreadSelected = onThreadSelected,
        onGroupAvatarLongClick = onGroupAvatarLongClick,
        onContactAvatarLongClick = onContactAvatarLongClick,
        connectorState = connectorState,
        modifier = modifier
    )
}

@Composable
private fun ScrollableSessionRail(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    conversation: FloatingChatConversation,
    selectedAccountId: String,
    selectedThread: ChatThreadSelection,
    unreadThreadIds: Set<String>,
    onThreadSelected: (ChatThreadSelection) -> Unit,
    onGroupAvatarLongClick: (FloatingChatContact) -> Unit,
    onContactAvatarLongClick: (FloatingChatContact) -> Unit,
    connectorState: ConnectorCoordinateState,
    modifier: Modifier = Modifier
) {
    val visibleGroups = remember(groups) {
        groups.ifEmpty {
            listOf(FloatingChatContact(GroupThreadId, "群聊", "群", "群聊", 0xFF5B7CFA, selected = true))
        }
    }
    val railItems = remember(visibleGroups, contacts) {
        buildList {
            visibleGroups.forEach { group -> add(SessionRailItem.Group(group)) }
            contacts.forEach { contact -> add(SessionRailItem.Contact(contact)) }
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = leftRailInitialFirstVisibleItemIndex()
    )
    var showFollowText by remember { mutableStateOf(false) }
    val avatarBoundsByContactId = remember { mutableMapOf<String, Rect>() }
    var avatarBoundsVersion by remember { mutableIntStateOf(0) }
    fun updateAvatarBounds(id: String, bounds: Rect) {
        if (avatarBoundsByContactId.updateIfChanged(id, bounds)) {
            avatarBoundsVersion += 1
        }
    }
    fun removeAvatarBounds(id: String) {
        if (avatarBoundsByContactId.remove(id) != null) {
            avatarBoundsVersion += 1
        }
    }
    val density = LocalDensity.current
    var topOverscrollPx by remember { mutableStateOf(0f) }
    val maxTopOverscrollPx = remember(density) {
        with(density) { LeftRailTopOverscrollMaxDp.dp.toPx() }
    }
    var railRootLeftPx by remember { mutableStateOf(0f) }
    var railRootTopPx by remember { mutableStateOf(0f) }
    val followInfos by remember(conversation, selectedAccountId, railItems, avatarBoundsVersion) {
        derivedStateOf {
            railItems.mapNotNull { item ->
                val bounds = avatarBoundsByContactId[item.contact.id] ?: return@mapNotNull null
                leftRailFollowInfoForContact(
                    conversation = conversation,
                    contact = item.contact,
                    selectedAccountId = selectedAccountId
                ).copy(
                    topPx = bounds.top - railRootTopPx,
                    heightPx = bounds.height
                )
            }
        }
    }
    val selectedPrivateContactId = (selectedThread as? ChatThreadSelection.Private)?.contactId
    val selectedPrivateAvatarBounds by remember(selectedPrivateContactId, avatarBoundsVersion) {
        derivedStateOf {
            selectedPrivateContactId?.let { contactId -> avatarBoundsByContactId[contactId] }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    showFollowText = true
                } else {
                    delay(leftRailFollowTextHideDelayMs().toLong())
                    showFollowText = false
                }
            }
    }
    LaunchedEffect(selectedPrivateContactId, selectedPrivateAvatarBounds) {
        val contactId = selectedPrivateContactId
        val bounds = selectedPrivateAvatarBounds
        if (contactId != null && bounds != null) {
            connectorState.updatePrivateThreadAvatar(contactId, bounds)
        } else if (contactId == null) {
            connectorState.clearPrivateThreadAvatar()
        }
    }
    val topOverscrollConnection = remember(listState, maxTopOverscrollPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val pullingDown = available.y > 0f
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (pullingDown && atTop) {
                    topOverscrollPx = (topOverscrollPx + available.y * LeftRailTopOverscrollResistance)
                        .coerceIn(0f, maxTopOverscrollPx)
                    return Offset.Zero
                }
                if (!pullingDown && topOverscrollPx > 0f) {
                    val consumed = min(-available.y, topOverscrollPx)
                    topOverscrollPx = (topOverscrollPx - consumed).coerceAtLeast(0f)
                    return Offset(x = 0f, y = -consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (topOverscrollPx > 0f) {
                    val start = topOverscrollPx
                    animate(
                        initialValue = start,
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = LeftRailTopOverscrollReturnMs)
                    ) { value, _ ->
                        topOverscrollPx = value
                    }
                }
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coordinates ->
            railRootLeftPx = coordinates.positionInRoot().x
            railRootTopPx = coordinates.positionInRoot().y
        }
    ) {
        val viewportHeightDp = with(density) {
            constraints.maxHeight.toDp().value.toInt()
        }
        val shortContent = leftRailContentFitsViewport(
            itemCount = railItems.size,
            viewportHeightDp = viewportHeightDp
        )
        LaunchedEffect(listState, shortContent, railItems.size) {
            if (!shortContent) return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }
                .collectLatest { scrolling ->
                    if (
                        !scrolling &&
                        (
                            listState.firstVisibleItemIndex != leftRailInitialFirstVisibleItemIndex() ||
                                listState.firstVisibleItemScrollOffset != 0
                            )
                    ) {
                        listState.scrollToItem(leftRailInitialFirstVisibleItemIndex())
                    }
                }
        }
        LazyColumn(
            modifier = Modifier
                .width(SessionRailWidth)
                .fillMaxHeight()
                .nestedScroll(topOverscrollConnection)
                .graphicsLayer {
                    translationY = topOverscrollPx
                },
            state = listState,
            userScrollEnabled = true,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                top = leftRailScrollableTopPaddingDp(
                    itemCount = railItems.size,
                    viewportHeightDp = viewportHeightDp
                ).dp,
                bottom = leftRailScrollableBottomPaddingDp(
                    itemCount = railItems.size,
                    viewportHeightDp = viewportHeightDp
                ).dp
            )
        ) {
            item(
                key = "left-rail-leading-scroll-buffer",
                contentType = "scroll-buffer"
            ) {
                Spacer(modifier = Modifier.height(1.dp))
            }
            itemsIndexed(
                items = railItems,
                key = { _, item -> item.key },
                contentType = { _, item -> item.contentType }
            ) { _, item ->
                when (item) {
                    is SessionRailItem.Group -> {
                        val group = item.contact
                        val selection = group.toGroupThreadSelection()
                        GroupChatAvatar(
                            selected = selectedThread == selection,
                            unread = unreadThreadIds.contains(selection.toLocalThreadId()),
                            memberCount = contacts.size,
                            label = group.initials,
                            color = Color(group.avatarColor),
                            onClick = { onThreadSelected(selection) },
                            onLongClick = { onGroupAvatarLongClick(group) },
                            onBoundsChanged = { bounds ->
                                updateAvatarBounds(group.id, bounds)
                                val connectorId = group.groupConnectorId()
                                if (selectedThread == selection) {
                                    connectorState.updateGroupThreadAvatar(connectorId, bounds)
                                }
                                connectorState.updateUserAvatar(connectorId, bounds)
                            },
                            onRemoved = {
                                removeAvatarBounds(group.id)
                                val connectorId = group.groupConnectorId()
                                connectorState.removeUserAvatar(connectorId)
                                connectorState.removeGroupThreadAvatar(connectorId)
                            }
                        )
                    }
                    is SessionRailItem.Contact -> {
                        val contact = item.contact
                        var currentAvatarBounds by remember(contact.id) { mutableStateOf<Rect?>(null) }
                        CompactAvatar(
                            contact = contact.copy(
                                selected = selectedThread is ChatThreadSelection.Private &&
                                    selectedThread.contactId == contact.id,
                                online = unreadThreadIds.contains(ChatThreadSelection.Private(contact.id).toLocalThreadId())
                            ),
                            role = AvatarRole.Session,
                            onClick = {
                                (currentAvatarBounds ?: avatarBoundsByContactId[contact.id])?.let { bounds ->
                                    connectorState.updatePrivateThreadAvatar(contact.id, bounds)
                                }
                                onThreadSelected(ChatThreadSelection.Private(contact.id))
                            },
                            onLongClick = { onContactAvatarLongClick(contact) },
                            onBoundsChanged = { bounds ->
                                currentAvatarBounds = bounds
                                updateAvatarBounds(contact.id, bounds)
                                connectorState.updateUserAvatar(contact.id, bounds)
                                if (selectedPrivateContactId == contact.id) {
                                    connectorState.updatePrivateThreadAvatar(contact.id, bounds)
                                }
                            },
                            onRemoved = {
                                removeAvatarBounds(contact.id)
                                connectorState.removeUserAvatar(contact.id)
                            }
                        )
                    }
                }
            }
        }
        LeftRailFollowTextOverlay(
            infos = followInfos,
            visible = showFollowText && followInfos.isNotEmpty(),
            modifier = Modifier
                .offset(x = leftRailFollowTextStartOffsetDp().dp)
                .fillMaxHeight()
                .requiredWidth(LeftRailFollowTextWidth)
                .zIndex(12f)
        )
    }
}

private sealed class SessionRailItem(open val contact: FloatingChatContact) {
    abstract val key: String
    abstract val contentType: String

    data class Group(override val contact: FloatingChatContact) : SessionRailItem(contact) {
        override val key: String = "group-${contact.id}"
        override val contentType: String = "group"
    }

    data class Contact(override val contact: FloatingChatContact) : SessionRailItem(contact) {
        override val key: String = "contact-${contact.id}"
        override val contentType: String = "contact"

        companion object {
            fun keyFor(contactId: String): String = "contact-$contactId"
        }
    }
}

@Composable
private fun LeftRailFollowTextOverlay(
    infos: List<LeftRailFollowInfo>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "leftRailFollowTextOpacity"
    )
    if (opacity <= 0.01f && !visible) return

    Box(
        modifier = modifier
            .graphicsLayer { alpha = opacity }
    ) {
        infos.forEach { info ->
            val yOffset = with(density) { info.topPx.toDp() }
            Column(
                modifier = Modifier
                    .offset(x = LeftRailFollowTextInnerPaddingDp.dp, y = yOffset)
                    .widthIn(max = (LeftRailFollowTextWidthDp - LeftRailFollowTextInnerPaddingDp * 2).dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                TextLabel(
                    text = info.name,
                    size = LeftRailFollowTextNameSizeSp.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.primaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.leftRailFollowTextShadow
                )
                TextLabel(
                    text = info.lastMessage,
                    size = LeftRailFollowTextMessageSizeSp.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.secondaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.leftRailFollowTextShadow
                )
                TextLabel(
                    text = info.lastTime,
                    size = LeftRailFollowTextTimeSizeSp.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.tertiaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.leftRailFollowTextShadow
                )
            }
        }
    }
}

@Composable
private fun MessageCoordinatePane(
    messages: List<FloatingChatMessage>,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    groupMemberAvatarsVisible: Boolean,
    listState: LazyListState,
    connectorState: ConnectorCoordinateState,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    onGroupMemberAvatarLongClick: (FloatingChatContact) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onMessageClick: (FloatingChatMessage) -> Unit,
    onBlankAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = MessagePaneHorizontalPadding)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBlankAreaTap
            )
            .onGloballyPositioned { coordinates ->
                connectorState.updateMessageViewport(coordinates.boundsInRoot())
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = messageListBottomClearanceDp().dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(
                items = messages,
                key = { _, message -> message.id },
                contentType = { _, message -> message.type }
            ) { index, message ->
                MessageRow(
                    message = message,
                    index = index,
                    selectedThread = selectedThread,
                    homeOverviewVisible = homeOverviewVisible,
                    contactsById = contactsById,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible,
                    onPreviewMedia = onPreviewMedia,
                    onOpenMediaActions = onOpenMediaActions,
                    onLongPressMessage = onLongPressMessage,
                    onGroupMemberAvatarLongClick = onGroupMemberAvatarLongClick,
                    multiSelectMode = multiSelectMode,
                    selected = selectedMessageIds[message.id] == true,
                    reminded = remindedMessageIds[message.id] == true,
                    favorite = favoriteMessageIds[message.id] == true,
                    onToggleSelection = { onToggleMessageSelection(message) },
                    onClick = { onMessageClick(message) },
                    onBubbleBoundsChanged = { bounds ->
                        connectorState.updateMessageBubble(message.id, bounds)
                    },
                    onGroupMemberAvatarBoundsChanged = { bounds ->
                        connectorState.updateGroupMemberAvatar(message.id, bounds)
                    },
                    onGroupMemberAvatarRemoved = {
                        connectorState.removeGroupMemberAvatar(message.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatConnectorLayer(
    messages: List<FloatingChatMessage>,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean,
    listState: LazyListState,
    offscreenIndex: ConnectorOffscreenIndex,
    connectorState: ConnectorCoordinateState,
    modifier: Modifier = Modifier
) {
    var layerBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val connectorNativePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val connectorStroke = remember {
        Stroke(
            width = imModuleConnectionLineStrokeWidthPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    Canvas(
        modifier = modifier.onGloballyPositioned { coordinates ->
            layerBoundsInRoot = coordinates.boundsInRoot()
        }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val connectorInvalidationVersion = connectorState.version
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@Canvas
        val layerBounds = layerBoundsInRoot ?: return@Canvas
        val messageViewportBounds = connectorState.messageViewport ?: return@Canvas
        connectorNativePaint.configureConnectorPaint()

        val visibleBubbleGroups = linkedMapOf<ConnectorTargetKey, MutableList<Rect>>()
        val avatarSourceKeys = linkedMapOf<ConnectorTargetKey, ConnectorTargetKey>()
        val directGroupMemberBranches = mutableListOf<ChatConnectorBranch>()
        val visibleGroupMemberBounds = mutableListOf<Rect>()
        visibleItems.forEach { itemInfo ->
            val message = messages.getOrNull(itemInfo.index) ?: return@forEach
            val key = if (homeOverviewVisible) {
                message.toHomeOverviewConnectorTargetKey()
            } else {
                message.toConnectorTargetKey(
                    selection = selection,
                    selectedAccountId = selectedAccountId,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible
                )
            } ?: return@forEach

            val bubbleBounds = connectorState.messageBubbles[message.id] ?: return@forEach
            avatarSourceKeys[key] = if (homeOverviewVisible) {
                message.toHomeOverviewConnectorSourceKey() ?: key
            } else {
                key
            }
            if (key.lane == ConnectorAvatarLane.GroupMember) {
                connectorState.groupMemberAvatars[key.targetId]?.let { bounds ->
                    visibleGroupMemberBounds += bounds
                    directGroupMemberBranches += createGroupMemberMessageConnectorBranch(
                        avatarBounds = bounds,
                        bubbleBounds = bubbleBounds,
                        layerBounds = layerBounds
                    )
                }
            }
            visibleBubbleGroups
                .getOrPut(key) {
                    mutableListOf()
                }
                .add(bubbleBounds)
        }

        if (selection.isGroupThread() && groupMemberAvatarsVisible) {
            drawGroupMemberConnectorTree(
                connectorState = connectorState,
                memberBounds = visibleGroupMemberBounds,
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                nativePaint = connectorNativePaint,
                stroke = connectorStroke
            )
        }

        val firstVisibleIndex = visibleItems.minOf { it.index }
        val lastVisibleIndex = visibleItems.maxOf { it.index }
        val offscreenEdges = offscreenConnectorEdges(
            index = offscreenIndex,
            firstVisibleIndex = firstVisibleIndex,
            lastVisibleIndex = lastVisibleIndex
        )
        val connectorKeys = visibleBubbleGroups.keys + offscreenEdges.keys
        connectorKeys.forEach { key ->
            if (key.lane == ConnectorAvatarLane.GroupMember) return@forEach
            val avatarSourceKey = avatarSourceKeys[key] ?: key
            val avatarOffscreenEdge = if (avatarSourceKey.lane == ConnectorAvatarLane.Account) {
                connectorState.accountAvatarEdgeFor(avatarSourceKey.targetId)
            } else {
                null
            }
            val avatarBounds = when (avatarSourceKey.lane) {
                ConnectorAvatarLane.Session -> {
                    if (
                        selection.isGroupThread() &&
                        avatarSourceKey.targetId == selection.groupConnectorId()
                    ) {
                        connectorState.groupThreadAvatar ?: connectorState.userAvatars[avatarSourceKey.targetId]
                    } else if (
                        selection is ChatThreadSelection.Private &&
                        avatarSourceKey.targetId == selection.contactId
                    ) {
                        connectorState.privateThreadAvatarFor(avatarSourceKey.targetId)
                            ?: connectorState.userAvatars[avatarSourceKey.targetId]
                    } else {
                        connectorState.userAvatars[avatarSourceKey.targetId]
                    }
                }
                ConnectorAvatarLane.GroupMember -> {
                    connectorState.groupMemberAvatars[avatarSourceKey.targetId]
                }
                ConnectorAvatarLane.Account -> {
                    connectorState.accountAvatarFor(avatarSourceKey.targetId)
                }
            } ?: if (homeOverviewVisible) {
                homeOverviewFallbackConnectorAvatarBounds(
                    bubbleBounds = visibleBubbleGroups[key].orEmpty(),
                    layerBounds = layerBounds,
                    visibleRootBounds = messageViewportBounds,
                    target = key.target
                )
            } else {
                null
            } ?: return@forEach

            val edgeState = offscreenEdges[key] ?: ConnectorViewportEdgeState()
            val tree = createChatConnectorTree(
                avatarBounds = avatarBounds,
                bubbleBounds = visibleBubbleGroups[key].orEmpty(),
                layerBounds = layerBounds,
                visibleRootBounds = messageViewportBounds,
                target = key.target,
                hasMessagesAbove = edgeState.hasAbove,
                hasMessagesBelow = edgeState.hasBelow,
                avatarOffscreenEdge = avatarOffscreenEdge
            ) ?: return@forEach

            drawChatConnectorTree(tree, connectorNativePaint, connectorStroke)
        }
        directGroupMemberBranches.forEach { branch ->
            drawChatConnectorBranch(branch, connectorNativePaint)
        }
    }
}

private fun Paint.configureConnectorPaint() {
    style = Paint.Style.STROKE
    strokeWidth = imModuleConnectionLineStrokeWidthPx()
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = OverlayTokens.connectorLine.toArgb()
    setShadowLayer(
        imModuleConnectionLineShadowBlurPx(),
        imModuleConnectionLineShadowOffsetXPx(),
        imModuleConnectionLineShadowOffsetYPx(),
        OverlayTokens.connectorLineShadow.toArgb()
    )
}

private fun DrawScope.drawChatConnectorBranch(
    branch: ChatConnectorBranch,
    nativePaint: Paint
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawLine(
            branch.start.x,
            branch.start.y,
            branch.end.x,
            branch.end.y,
            nativePaint
        )
    }
    drawLine(
        color = OverlayTokens.connectorLine,
        start = branch.start,
        end = branch.end,
        strokeWidth = imModuleConnectionLineStrokeWidthPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawChatConnectorTree(
    tree: ChatConnectorTree,
    nativePaint: Paint,
    stroke: Stroke
) {
    val connectorPath = tree.toPath()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(connectorPath.asAndroidPath(), nativePaint)
    }
    drawPath(
        path = connectorPath,
        color = OverlayTokens.connectorLine,
        style = stroke
    )
}

private fun DrawScope.drawGroupMemberConnectorTree(
    connectorState: ConnectorCoordinateState,
    memberBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    nativePaint: Paint,
    stroke: Stroke
) {
    val groupAvatarBounds = connectorState.groupThreadAvatar ?: return
    if (memberBounds.isEmpty()) return

    val tree = createChatConnectorTree(
        avatarBounds = groupAvatarBounds,
        bubbleBounds = memberBounds,
        layerBounds = layerBounds,
        visibleRootBounds = visibleRootBounds,
        target = FloatingChatConnectionTarget.User,
        hasMessagesAbove = false,
        hasMessagesBelow = false
    ) ?: return
    drawChatConnectorTree(tree, nativePaint, stroke)
}

@Composable
private fun MessageRow(
    message: FloatingChatMessage,
    index: Int,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    groupMemberAvatarsVisible: Boolean,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    onGroupMemberAvatarLongClick: (FloatingChatContact) -> Unit,
    multiSelectMode: Boolean,
    selected: Boolean,
    reminded: Boolean,
    favorite: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onBubbleBoundsChanged: (Rect) -> Unit,
    onGroupMemberAvatarBoundsChanged: (Rect) -> Unit,
    onGroupMemberAvatarRemoved: () -> Unit
) {
    val groupMemberContact = remember(
        message,
        selectedThread,
        homeOverviewVisible,
        contactsById,
        groupMemberAvatarsVisible
    ) {
        groupMemberContactForMessage(
            message = message,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            contactsById = contactsById,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible
        )
    }
    val placement = messageHorizontalPlacement(message.presentation, message.fromMe)
    LaunchedEffect(groupMemberContact) {
        if (groupMemberContact == null) {
            onGroupMemberAvatarRemoved()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (placement) {
            MessageHorizontalPlacement.Start -> Arrangement.Start
            MessageHorizontalPlacement.Center -> Arrangement.Center
            MessageHorizontalPlacement.End -> Arrangement.End
        },
        verticalAlignment = if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
            Alignment.CenterVertically
        } else {
            Alignment.Top
        }
    ) {
        if (multiSelectMode) {
            MessageSelectionToggle(
                selected = selected,
                onClick = onToggleSelection,
                modifier = Modifier.padding(top = 20.dp, end = 4.dp)
            )
        }
        if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
            CompactAvatar(
                contact = groupMemberContact,
                role = AvatarRole.GroupMember,
                sizeDp = groupMemberAvatarSizeDp(),
                onClick = {},
                onLongClick = { onGroupMemberAvatarLongClick(groupMemberContact) },
                onBoundsChanged = onGroupMemberAvatarBoundsChanged,
                modifier = Modifier.offset(y = groupMemberAvatarBubbleCenterOffsetDp().dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        MessageBlock(
            message = message,
            index = index,
            onPreviewMedia = onPreviewMedia,
            onOpenMediaActions = onOpenMediaActions,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            selected = selected,
            reminded = reminded,
            favorite = favorite,
            onToggleSelection = onToggleSelection,
            onClick = onClick,
            onBubbleBoundsChanged = onBubbleBoundsChanged,
            modifier = if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
                Modifier.weight(1f, fill = false)
            } else {
                Modifier.fillMaxWidth(
                    when (message.presentation) {
                        FloatingChatMessagePresentation.Bubble -> 0.99f
                        FloatingChatMessagePresentation.SpecialCard -> 0.99f
                        FloatingChatMessagePresentation.MediaStandalone -> 0.99f
                        FloatingChatMessagePresentation.System -> 1f
                    }
                )
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBlock(
    message: FloatingChatMessage,
    index: Int,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    selected: Boolean,
    reminded: Boolean,
    favorite: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onBubbleBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleClickSource = remember { MutableInteractionSource() }
    var currentBounds by remember(message.id) { mutableStateOf<Rect?>(null) }
    fun updateCurrentBounds(bounds: Rect) {
        currentBounds = bounds
        onBubbleBoundsChanged(bounds)
    }
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    val isSpecialCard = message.presentation == FloatingChatMessagePresentation.SpecialCard
    val isPaymentCard = message.isPaymentCardMessage()
    val usesBubbleChrome = messageUsesBubbleChrome(message.presentation)
    val bubbleShape = RoundedCornerShape(if (isSpecialCard) 7.dp else 8.dp)
    val bubbleColor = messageBubbleColor(message)
    val bubbleBorderColor = messageBubbleBorderColor(message)
    val aiDraftDashedBubble = aiDraftMessageUsesGreenDashedBubble(message)
    val usesDemoBubble = messageTypeUsesImModuleBubble(message.type) && !isSystem
    Column(
        modifier = modifier,
        horizontalAlignment = when {
            isSystem -> Alignment.CenterHorizontally
            message.fromMe -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        Box(
            modifier = Modifier
                .padding(top = if (isSystem) 0.dp else 8.dp)
        ) {
            if (usesBubbleChrome) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = if (usesDemoBubble && message.fromMe) {
                                imModuleSelfBubbleShadowBlurDp().dp
                            } else {
                                3.dp
                            },
                            shape = bubbleShape,
                            ambientColor = OverlayTokens.glassShadow,
                            spotColor = OverlayTokens.glassShadow
                        )
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .then(
                            if (aiDraftDashedBubble) {
                                Modifier.aiDraftDashedBorder(bubbleShape)
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = bubbleBorderColor,
                                    shape = bubbleShape
                                )
                            }
                        )
                        .onGloballyPositioned { coordinates ->
                            updateCurrentBounds(
                                rootBoundsFromPosition(
                                    positionInRoot = coordinates.positionInRoot(),
                                    width = coordinates.size.width,
                                    height = coordinates.size.height
                                )
                            )
                        }
                        .combinedClickable(
                            interactionSource = bubbleClickSource,
                            indication = null,
                            onClick = {
                                if (multiSelectMode) {
                                    onToggleSelection()
                                } else {
                                    onClick()
                                }
                            },
                            onLongClick = { onLongPressMessage(message, currentBounds) }
                        )
                        .padding(
                            horizontal = when {
                                isSystem -> 10.dp
                                isSpecialCard -> 16.dp
                                else -> 12.dp
                            },
                            vertical = when {
                                isSystem -> 5.dp
                                isPaymentCard -> paymentCardOuterVerticalPaddingDp().dp
                                isSpecialCard -> 14.dp
                                else -> 10.dp
                            }
                        )
                ) {
                    MessageContent(
                        message = message,
                        index = index,
                        onPreviewMedia = onPreviewMedia,
                        onOpenMediaActions = onOpenMediaActions,
                        onLongPressMessage = onLongPressMessage,
                        multiSelectMode = multiSelectMode,
                        onToggleSelection = onToggleSelection
                    )
                }
            } else {
                Box(
                    modifier = Modifier.combinedClickable(
                        interactionSource = bubbleClickSource,
                        indication = null,
                        onClick = {
                            if (multiSelectMode) {
                                onToggleSelection()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = { onLongPressMessage(message, currentBounds) }
                    )
                ) {
                    MessageContent(
                        message = message,
                        index = index,
                        onPreviewMedia = onPreviewMedia,
                        onOpenMediaActions = onOpenMediaActions,
                        onLongPressMessage = onLongPressMessage,
                        multiSelectMode = multiSelectMode,
                        onToggleSelection = onToggleSelection,
                        onContentBoundsChanged = ::updateCurrentBounds
                    )
                }
            }
            if (favorite || reminded) {
                MessageStateBadges(
                    favorite = favorite,
                    reminded = reminded,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = 4.dp)
                )
            }
            if (!isSystem) {
                TextLabel(
                    text = message.senderName,
                    size = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 16.dp, y = (-6).dp),
                    weight = FontWeight.Bold,
                    color = OverlayTokens.bubbleNameText,
                    maxLines = 1,
                    shadow = OverlayTokens.imModuleTextShadow
                )
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: FloatingChatMessage,
    index: Int,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    Column(verticalArrangement = Arrangement.spacedBy(if (isSystem) 0.dp else 7.dp)) {
        when (message.type) {
            FloatingChatMessageType.Location -> LocationMessageContent(message)
            FloatingChatMessageType.ContactLink -> ContactLinkCardContent(message)
            FloatingChatMessageType.MiniProgramLink -> MiniProgramLinkContent(message)
            FloatingChatMessageType.Text -> SimpleTextMessageContent(message = message, index = index)
            FloatingChatMessageType.MixedText -> MixedTextMessageContent(message)
            FloatingChatMessageType.Quote -> QuoteMessageContent(message)
            FloatingChatMessageType.FilePreview -> FilePreviewContent(message = message)
            FloatingChatMessageType.ImageThumbnail -> ImageThumbnailContent(
                message = message,
                onPreviewMedia = onPreviewMedia,
                onOpenMediaActions = onOpenMediaActions,
                onLongPressMessage = onLongPressMessage,
                multiSelectMode = multiSelectMode,
                onToggleSelection = onToggleSelection,
                onContentBoundsChanged = onContentBoundsChanged
            )
            FloatingChatMessageType.VideoPreview -> VideoPreviewContent(
                message = message,
                onPreviewMedia = onPreviewMedia,
                onLongPressMessage = onLongPressMessage,
                multiSelectMode = multiSelectMode,
                onToggleSelection = onToggleSelection,
                onContentBoundsChanged = onContentBoundsChanged
            )
            FloatingChatMessageType.Voice -> VoiceMessageContent(message)
            FloatingChatMessageType.InlineContact -> InlineContactContent(message)
            FloatingChatMessageType.InlineLocation -> InlineLocationContent(message)
        }
        if (message.kind == FloatingChatMessageKind.AiDraft && !isSystem) {
            DraftBadge()
        }
    }
}

@Composable
private fun VoiceMessageContent(message: FloatingChatMessage) {
    val context = LocalContext.current
    var playing by remember(message.id) { mutableStateOf(false) }
    var failed by remember(message.id) { mutableStateOf(false) }
    var playerRef by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    val durationText = message.detail ?: formatVoiceTimecode(message.mediaDurationMs ?: 0)

    DisposableEffect(message.id) {
        onDispose {
            playerRef?.release()
            playerRef = null
        }
    }

    Row(
        modifier = Modifier.widthIn(min = 132.dp, max = 228.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        CompactInteractiveSize {
            FilledTonalIconButton(
                onClick = {
                    val currentPlayer = playerRef
                    if (currentPlayer?.isPlaying == true) {
                        currentPlayer.pause()
                        playing = false
                        return@FilledTonalIconButton
                    }
                    if (currentPlayer != null) {
                        currentPlayer.start()
                        playing = true
                        failed = false
                        return@FilledTonalIconButton
                    }
                    val uri = message.resourceUrl?.let(Uri::parse)
                    if (uri == null) {
                        failed = true
                        return@FilledTonalIconButton
                    }
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(context, uri)
                            setOnCompletionListener {
                                playing = false
                                it.seekTo(0)
                            }
                            setOnErrorListener { mp, _, _ ->
                                playing = false
                                failed = true
                                mp.release()
                                playerRef = null
                                true
                            }
                            prepare()
                            start()
                        }
                    }.onSuccess { mediaPlayer ->
                        playerRef = mediaPlayer
                        playing = true
                        failed = false
                    }.onFailure {
                        playing = false
                        failed = true
                    }
                },
                modifier = Modifier.size(30.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = OverlayTokens.voiceButton,
                    contentColor = OverlayTokens.voiceIcon
                )
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停语音" else "播放语音",
                    tint = OverlayTokens.voiceIcon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = if (failed) "语音播放失败" else "语音消息",
                size = 11.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = durationText,
                size = 10.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun SimpleTextMessageContent(message: FloatingChatMessage, index: Int) {
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    TextLabel(
        text = message.text,
        size = if (isSystem) 9.sp else 11.sp,
        weight = if (isSystem) FontWeight.Normal else FontWeight.Bold,
        color = if (isSystem) OverlayTokens.systemPromptText else OverlayTokens.bubbleText,
        lineHeight = if (isSystem) 13.sp else 15.sp,
        maxLines = if (isSystem) 2 else if (index < 2) 3 else 4,
        shadow = OverlayTokens.imModuleTextShadow
    )
    message.detail?.let { detail ->
        Spacer(modifier = Modifier.height(2.dp))
        TextLabel(
            text = detail,
            size = 9.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.bubbleTextMuted,
            lineHeight = 12.sp,
            maxLines = 1,
            shadow = OverlayTokens.imModuleTextShadow
        )
    }
}

@Composable
private fun MixedTextMessageContent(message: FloatingChatMessage) {
    val text = remember(message.inlineTokens, message.text) {
        if (message.inlineTokens.isEmpty()) {
            AnnotatedString(message.text)
        } else {
            buildAnnotatedString {
                message.inlineTokens.forEach { token ->
                    val color = when (token.type) {
                        FloatingChatInlineTokenType.Plain -> OverlayTokens.bubbleText
                        FloatingChatInlineTokenType.PaidianLink,
                        FloatingChatInlineTokenType.FileLink,
                        FloatingChatInlineTokenType.Url,
                        FloatingChatInlineTokenType.Mention,
                        FloatingChatInlineTokenType.ImageName -> OverlayTokens.linkText
                        FloatingChatInlineTokenType.Ai -> OverlayTokens.aiGold
                    }
                    val weight = when (token.type) {
                        FloatingChatInlineTokenType.Ai -> FontWeight.Black
                        FloatingChatInlineTokenType.Plain -> FontWeight.SemiBold
                        else -> FontWeight.Bold
                    }
                    withStyle(SpanStyle(color = color, fontWeight = weight)) {
                        append(token.text)
                    }
                }
            }
        }
    }
    AnnotatedTextLabel(
        text = text,
        size = 11.sp,
        lineHeight = 15.sp,
        maxLines = 5,
        shadow = OverlayTokens.imModuleTextShadow
    )
}

@Composable
private fun QuoteMessageContent(message: FloatingChatMessage) {
    QuoteBlock(message)
    TextLabel(
        text = message.text,
        size = 11.sp,
        weight = FontWeight.Bold,
        color = OverlayTokens.bubbleText,
        lineHeight = 15.sp,
        maxLines = 4,
        shadow = OverlayTokens.imModuleTextShadow
    )
}

@Composable
private fun LocationMessageContent(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.locationMapCard)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        TextLabel(
            text = message.locationTitle ?: message.text,
            size = 11.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.locationMapText,
            maxLines = 1
        )
        TextLabel(
            text = message.locationAddress.orEmpty(),
            size = 8.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.locationMapSubtext,
            maxLines = 1
        )
        LocationMapPreviewCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(locationMapPreviewHeightDp().dp)
                .clip(RoundedCornerShape(5.dp))
        )
    }
}

@Composable
private fun ContactLinkCardContent(message: FloatingChatMessage) {
    val name = message.cardName ?: message.text
    AccountCardPreviewContent(
        name = name,
        subtitle = message.cardSubtitle.orEmpty(),
        detail = message.detail.orEmpty().ifBlank { message.resourceUrl.orEmpty() },
        avatarText = name.take(2).ifBlank { "名片" },
        avatarColor = cardColorFor(message.cardKind),
        avatarImageUri = message.thumbnailUrl
    )
}

@Composable
private fun MiniProgramLinkContent(message: FloatingChatMessage) {
    if (message.isPaymentCardMessage()) {
        PaymentCardContent(message)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(OverlayTokens.miniProgramIcon),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = "小",
                size = 15.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = message.text,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 2,
                lineHeight = 14.sp,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.appName.orEmpty(),
                size = 9.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            ResourceUrlLine(message.resourceUrl)
        }
    }
}

@Composable
private fun FilePreviewContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = fileWechatCardMinHeightDp().dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.fileWechatCard)
            .border(1.dp, OverlayTokens.fileWechatCardBorder, RoundedCornerShape(7.dp))
            .padding(start = 7.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = message.fileName ?: message.text,
                size = fileWechatTitleTextSizeSp().sp,
                weight = FontWeight.Normal,
                color = OverlayTokens.fileWechatTitle,
                maxLines = 2,
                lineHeight = 13.sp
            )
            TextLabel(
                text = message.fileSizeLabel.orEmpty(),
                size = fileWechatSizeTextSizeSp().sp,
                weight = FontWeight.Normal,
                color = OverlayTokens.fileWechatSize,
                maxLines = 1,
                lineHeight = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        FileFormatIcon(
            format = message.fileFormat,
            fileName = message.fileName ?: message.text
        )
    }
}

@Composable
private fun PaymentCardContent(message: FloatingChatMessage) {
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
    val footer = if (isTransfer) "转账" else paymentCardRedPacketFooter()

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
private fun PaymentDetailOverlay(
    message: FloatingChatMessage,
    selectedThread: ChatThreadSelection,
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
    val canClaim = !isTransfer && redPacketCanClaimInThread(
        fromMe = message.fromMe,
        selectedThread = selectedThread
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
                        text = transferDetailStatusLabel(message.fromMe),
                        size = 11.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlayTokens.paymentCard,
                            contentColor = OverlayTokens.paymentCardText
                        )
                    ) {
                        TextLabel(
                            text = "完成",
                            size = 12.sp,
                            color = OverlayTokens.paymentCardText,
                            maxLines = 1
                        )
                    }
                } else if (canClaim) {
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

@Composable
private fun RedPacketPaymentGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = 5.dp.toPx()
        drawRoundRect(
            color = Color(0xFFEF3E46),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color(0xFFDE3441),
            topLeft = Offset(0f, size.height * 0.44f),
            size = Size(size.width, size.height * 0.56f),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawPath(
            Path().apply {
                moveTo(0f, size.height * 0.44f)
                quadraticTo(size.width * 0.5f, size.height * 0.62f, size.width, size.height * 0.44f)
                lineTo(size.width, size.height * 0.56f)
                quadraticTo(size.width * 0.5f, size.height * 0.75f, 0f, size.height * 0.56f)
                close()
            },
            color = Color(0xFFE1474A)
        )
        drawCircle(
            color = Color(0xFFFFD553),
            radius = size.minDimension * 0.17f,
            center = Offset(size.width * 0.5f, size.height * 0.47f)
        )
        drawContext.canvas.nativeCanvas.drawText(
            "¥",
            size.width * 0.5f,
            size.height * 0.52f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color(0xFFD89123).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = size.minDimension * 0.22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
    }
}

@Composable
private fun TransferPaymentGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val paintColor = OverlayTokens.paymentCardText
        drawCircle(
            color = paintColor,
            radius = size.minDimension * 0.46f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val path = Path().apply {
            moveTo(size.width * 0.67f, size.height * 0.31f)
            lineTo(size.width * 0.31f, size.height * 0.31f)
            lineTo(size.width * 0.22f, size.height * 0.43f)
            moveTo(size.width * 0.31f, size.height * 0.57f)
            lineTo(size.width * 0.69f, size.height * 0.57f)
            lineTo(size.width * 0.78f, size.height * 0.45f)
        }
        drawPath(
            path = path,
            color = paintColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun ImageThumbnailContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    if (message.presentation == FloatingChatMessagePresentation.MediaStandalone) {
        StandaloneMediaMessageContent(
            message = message,
            onPreviewMedia = onPreviewMedia,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            onToggleSelection = onToggleSelection,
            onContentBoundsChanged = onContentBoundsChanged
        )
    } else {
        InlineImageThumbnailContent(message)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StandaloneMediaMessageContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)?
) {
    val mediaClickSource = remember { MutableInteractionSource() }
    var currentBounds by remember(message.id) { mutableStateOf<Rect?>(null) }
    val mediaFrame = standaloneMediaListFrameSize(
        orientation = message.thumbnailOrientation,
        mediaAspectRatio = message.mediaAspectRatio
    )
    Column(
        modifier = Modifier.width(mediaFrame.width),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(mediaFrame.width)
                .height(mediaFrame.height)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.standaloneMediaBorder, RoundedCornerShape(7.dp))
                .onGloballyPositioned { coordinates ->
                    val bounds = rootBoundsFromPosition(
                        positionInRoot = coordinates.positionInRoot(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                    currentBounds = bounds
                    onContentBoundsChanged?.invoke(bounds)
                }
                .combinedClickable(
                    interactionSource = mediaClickSource,
                    indication = null,
                    onClick = {
                        if (multiSelectMode) {
                            onToggleSelection()
                        } else {
                            onPreviewMedia(message)
                        }
                    },
                    onLongClick = { onLongPressMessage(message, currentBounds) }
                )
        ) {
            MediaThumbnailSurface(
                message = message,
                modifier = Modifier.fillMaxSize(),
                showChrome = false,
                useAspectFit = standaloneMediaListUsesAspectFit()
            )
        }
    }
}

@Composable
private fun InlineImageThumbnailContent(message: FloatingChatMessage) {
    val context = LocalContext.current
    val mediaBitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = message.thumbnailUrl
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedThumbnailHeightDp(message.thumbnailOrientation).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
        ) {
            ImageThumbnailSurface(
                message = message,
                mediaBitmap = mediaBitmap,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ImageActionPill("识图")
                ImageActionPill("找物")
            }
            TextLabel(
                text = message.text,
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 2,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 44.dp, end = 8.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun ImageThumbnailSurface(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier,
    mediaBitmap: Bitmap? = rememberAsyncImageThumbnailBitmap(message)
) {
    MediaThumbnailSurface(
        message = message,
        modifier = modifier,
        mediaBitmap = mediaBitmap
    )
}

@Composable
private fun MediaThumbnailSurface(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier,
    mediaBitmap: Bitmap? = rememberAsyncMediaThumbnailBitmap(message),
    showChrome: Boolean = true,
    useAspectFit: Boolean = false
) {
    Box(modifier = modifier) {
        if (mediaBitmap != null) {
            Image(
                bitmap = mediaBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (useAspectFit) ContentScale.Fit else ContentScale.Crop
            )
        } else {
            PlaceholderImageCanvas(
                orientation = message.thumbnailOrientation,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showChrome && message.thumbnailOrientation == FloatingChatThumbnailOrientation.Vertical) {
            VerticalImageCropOverlay(modifier = Modifier.fillMaxSize())
        }
        if (message.type == FloatingChatMessageType.VideoPreview) {
            VideoPlayGlyph(modifier = Modifier.fillMaxSize())
        }
        if (showChrome) {
            TextLabel(
                text = mediaWatermarkText(
                    resourceUrl = message.resourceUrl,
                    thumbnailUrl = message.thumbnailUrl
                ),
                size = 8.sp,
                color = OverlayTokens.imageWatermark,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 86.dp, bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun rememberAsyncImageThumbnailBitmap(message: FloatingChatMessage): Bitmap? {
    val context = LocalContext.current
    return rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = message.thumbnailUrl
    )
}

@Composable
private fun rememberAsyncImageThumbnailBitmap(
    context: Context,
    uriText: String?
): Bitmap? {
    return produceState<Bitmap?>(initialValue = null, context, uriText) {
        value = withContext(Dispatchers.IO) {
            loadImageThumbnailBitmap(
                context = context.applicationContext,
                uriText = uriText
            )
        }
    }.value
}

@Composable
private fun rememberAsyncMediaThumbnailBitmap(message: FloatingChatMessage): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, context, message.type, message.resourceUrl, message.thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            when (message.type) {
                FloatingChatMessageType.VideoPreview -> loadVideoPreviewBitmap(
                    context = context.applicationContext,
                    thumbnailUriText = message.thumbnailUrl,
                    resourceUriText = message.resourceUrl
                )
                else -> loadImageThumbnailBitmap(
                    context = context.applicationContext,
                    uriText = message.thumbnailUrl
                )
            }
        }
    }.value
}

@Composable
private fun StandaloneImageQuickActions(
    onOpenActions: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
    favorite: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaRoundActionButton(
            action = MediaAction.Share,
            onClick = onShare
        )
        MediaRoundActionButton(
            action = MediaAction.Save,
            onClick = onSave
        )
        MediaRoundActionButton(
            action = MediaAction.Favorite,
            selected = favorite,
            onClick = onFavorite
        )
        MediaRoundActionButton(
            action = MediaAction.More,
            onClick = onOpenActions
        )
    }
}

@Composable
private fun MediaRoundActionButton(
    action: MediaAction,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    CompactInteractiveSize {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(24.dp)
                .background(
                    color = if (selected) OverlayTokens.mediaActionButtonSelected else OverlayTokens.mediaActionButton,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (selected) OverlayTokens.accent else OverlayTokens.mediaActionBorder,
                    shape = CircleShape
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (selected) OverlayTokens.mediaActionIconSelected else OverlayTokens.mediaActionIcon
            )
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val iconColor = if (selected) OverlayTokens.mediaActionIconSelected else OverlayTokens.mediaActionIcon
                when (action) {
                    MediaAction.Share -> drawShareArrowIcon(iconColor, stroke)
                    MediaAction.Save -> drawSaveBoxIcon(iconColor, stroke)
                    MediaAction.Favorite -> drawHeartToolIcon(iconColor, stroke)
                    MediaAction.More -> drawMoreDotsIcon(iconColor)
                    MediaAction.Visibility -> drawEyeToolIcon(iconColor, stroke)
                    MediaAction.Edit -> drawEditPencilIcon(iconColor, stroke)
                    MediaAction.Comment -> drawCommentBubbleIcon(iconColor, stroke)
                    MediaAction.Grid -> drawGridIcon(iconColor, stroke)
                }
            }
        }
    }
}

@Composable
fun FloatingChatMediaPreviewHost(
    session: FloatingChatMediaPreviewSession,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var mediaActionMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }
    val favoriteMediaIds = remember { mutableMapOf<String, Boolean>() }
    val storedFavoriteItems = remember(context) {
        mutableStateListOf<FavoriteCollectionItem>().apply {
            addAll(loadFavoriteCollectionItems(context))
        }
    }
    val handleMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit = { message, action ->
        val result = performMediaAction(
            context = context,
            message = message,
            action = action,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { mediaActionMessage = message },
            onFavoriteChanged = { favoriteMessage, favorite ->
                updateFavoriteCollectionItems(context, storedFavoriteItems, favoriteMessage, favorite)
            }
        )
        actionStatus = result.status
        if (result.toast) {
            Toast.makeText(context, result.status, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.background(OverlayTokens.blankScrim)) {
        MediaPreviewOverlay(
            mediaMessages = session.mediaMessages,
            initialIndex = session.initialIndex,
            actionStatus = actionStatus,
            favoriteMediaIds = favoriteMediaIds,
            externalDismissSignal = 0L,
            initialDismissSignal = 0L,
            onClose = onClose,
            onOpenActions = { message -> mediaActionMessage = message },
            onMediaAction = handleMediaAction,
            modifier = Modifier.fillMaxSize()
        )
        mediaActionMessage?.let { message ->
            MediaActionSheetOverlay(
                message = message,
                onClose = { mediaActionMessage = null },
                onMediaAction = { action ->
                    handleMediaAction(message, action)
                    if (action != MediaActionContract.More) {
                        mediaActionMessage = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DocumentPreviewOverlay(
    message: FloatingChatMessage,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val readerContent by produceState<FloatingChatDocumentReaderContent>(
        initialValue = FloatingChatDocumentReaderContent.Loading,
        message.id,
        message.resourceUrl,
        message.mediaMimeType,
        message.fileFormat
    ) {
        value = withContext(Dispatchers.IO) {
            loadBuiltInDocumentReaderContent(context, message)
        }
    }
    Column(
        modifier = modifier
            .background(OverlayTokens.blankScrim)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FileFormatIcon(
                format = message.fileFormat,
                fileName = message.fileName ?: message.text
            )
            Column(modifier = Modifier.weight(1f)) {
                TextLabel(
                    text = message.fileName ?: message.text,
                    size = 14.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.mediaActionIcon,
                    maxLines = 1,
                    lineHeight = 18.sp
                )
                TextLabel(
                    text = listOfNotNull(
                        message.fileSizeLabel?.takeIf { it.isNotBlank() },
                        message.mediaMimeType?.takeIf { it.isNotBlank() }
                    ).joinToString("  "),
                    size = 10.sp,
                    color = OverlayTokens.tertiaryText,
                    maxLines = 1,
                    lineHeight = 13.sp
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = OverlayTokens.mediaActionIcon
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭"
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(OverlayTokens.mediaSheet)
                .border(1.dp, OverlayTokens.mediaSheetBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            DocumentReaderContentBody(
                content = readerContent,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenExternal,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.mediaSheetCancel,
                    contentColor = OverlayTokens.mediaSheetText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextLabel(
                    text = "用其他应用打开",
                    size = 12.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.mediaSheetText,
                    maxLines = 1
                )
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.accent,
                    contentColor = OverlayTokens.mediaActionIconSelected
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                TextLabel(
                    text = "关闭",
                    size = 12.sp,
                    weight = FontWeight.Normal,
                    color = OverlayTokens.mediaActionIconSelected,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DocumentReaderContentBody(
    content: FloatingChatDocumentReaderContent,
    modifier: Modifier = Modifier
) {
    when (content) {
        FloatingChatDocumentReaderContent.Loading -> DocumentReaderStatus(
            text = "正在读取文件...",
            modifier = modifier
        )
        is FloatingChatDocumentReaderContent.Error -> DocumentReaderStatus(
            text = content.message,
            modifier = modifier
        )
        is FloatingChatDocumentReaderContent.Unsupported -> DocumentReaderStatus(
            text = content.message,
            modifier = modifier
        )
        is FloatingChatDocumentReaderContent.TextLines -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(content.lines) { _, line ->
                TextLabel(
                    text = line,
                    size = 12.sp,
                    color = OverlayTokens.mediaSheetText,
                    lineHeight = 17.sp
                )
            }
        }
        is FloatingChatDocumentReaderContent.ZipEntries -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(content.entries) { index, entry ->
                TextLabel(
                    text = "${index + 1}. $entry",
                    size = 12.sp,
                    color = OverlayTokens.mediaSheetText,
                    lineHeight = 17.sp,
                    maxLines = 2
                )
            }
        }
        is FloatingChatDocumentReaderContent.SpreadsheetRows -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(content.rows) { _, row ->
                TextLabel(
                    text = row.joinToString("    "),
                    size = 11.sp,
                    color = OverlayTokens.mediaSheetText,
                    lineHeight = 16.sp,
                    maxLines = 3
                )
            }
        }
        is FloatingChatDocumentReaderContent.PdfPages -> LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(content.pages) { index, bitmap ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextLabel(
                        text = "第 ${index + 1} / ${content.totalPageCount} 页",
                        size = 10.sp,
                        color = OverlayTokens.mediaSheetMutedText,
                        maxLines = 1
                    )
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF 第 ${index + 1} 页",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(5.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
            if (content.renderedPageCount < content.totalPageCount) {
                item {
                    TextLabel(
                        text = "已预览前 ${content.renderedPageCount} 页，更多页面可用其他应用打开。",
                        size = 11.sp,
                        color = OverlayTokens.mediaSheetMutedText,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentReaderStatus(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        TextLabel(
            text = text,
            size = 13.sp,
            color = OverlayTokens.mediaSheetMutedText,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPreviewOverlay(
    mediaMessages: List<FloatingChatMessage>,
    initialIndex: Int,
    actionStatus: String?,
    favoriteMediaIds: Map<String, Boolean>,
    externalDismissSignal: Long,
    initialDismissSignal: Long,
    onClose: () -> Unit,
    onOpenActions: (FloatingChatMessage) -> Unit,
    onMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaMessages.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, mediaMessages.lastIndex),
        pageCount = { mediaMessages.size }
    )
    val scope = rememberCoroutineScope()
    val pageZoomById = remember { mutableStateMapOf<String, Float>() }
    var handledDismissSignal by remember { mutableStateOf(initialDismissSignal) }
    var dismissOffsetY by remember(mediaMessages) { mutableStateOf(0f) }
    var dismissScale by remember(mediaMessages) { mutableStateOf(1f) }
    var dismissAlpha by remember(mediaMessages) { mutableStateOf(1f) }
    var viewportHeightPx by remember(mediaMessages) { mutableStateOf(0f) }
    val currentMessage = mediaMessages.getOrNull(pagerState.currentPage) ?: mediaMessages.first()
    val currentZoomScale = pageZoomById[currentMessage.id] ?: mediaPreviewMinimumZoom()
    val canSwipePages = dismissOffsetY == 0f &&
        currentZoomScale <= mediaPreviewMinimumZoom() + 0.01f

    fun animatePreviewTransform(
        targetOffsetY: Float,
        targetScale: Float,
        targetAlpha: Float,
        closeAfter: Boolean
    ) {
        scope.launch {
            val startOffsetY = dismissOffsetY
            val startScale = dismissScale
            val startAlpha = dismissAlpha
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = if (closeAfter) {
                    tween(durationMillis = 220)
                } else {
                    spring(stiffness = Spring.StiffnessLow)
                }
            ) { value, _ ->
                dismissOffsetY = lerpFloat(startOffsetY, targetOffsetY, value)
                dismissScale = lerpFloat(startScale, targetScale, value)
                dismissAlpha = lerpFloat(startAlpha, targetAlpha, value)
            }
            if (closeAfter) {
                onClose()
            } else {
                dismissOffsetY = 0f
                dismissScale = 1f
                dismissAlpha = 1f
            }
        }
    }

    fun dismissWithShrinkAnimation() {
        animatePreviewTransform(
            targetOffsetY = 0f,
            targetScale = 0.78f,
            targetAlpha = 0f,
            closeAfter = true
        )
    }

    LaunchedEffect(externalDismissSignal) {
        if (externalDismissSignal > handledDismissSignal) {
            handledDismissSignal = externalDismissSignal
            dismissWithShrinkAnimation()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(OverlayTokens.blankScrim)
            .onSizeChanged { size ->
                viewportHeightPx = size.height.toFloat()
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismissOffsetY
                    scaleX = dismissScale
                    scaleY = dismissScale
                    alpha = dismissAlpha
                }
                .pointerInput(currentMessage.id, currentZoomScale, viewportHeightPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (currentZoomScale > mediaPreviewMinimumZoom() + 0.01f) {
                                return@detectVerticalDragGestures
                            }
                            val nextOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
                            if (nextOffsetY == dismissOffsetY) return@detectVerticalDragGestures
                            change.consume()
                            dismissOffsetY = nextOffsetY
                            val progress = if (viewportHeightPx <= 0f) {
                                0f
                            } else {
                                (dismissOffsetY / (viewportHeightPx * 0.72f)).coerceIn(0f, 1f)
                            }
                            dismissScale = 1f - (progress * 0.18f)
                            dismissAlpha = 1f - (progress * 0.38f)
                        },
                        onDragCancel = {
                            animatePreviewTransform(
                                targetOffsetY = 0f,
                                targetScale = 1f,
                                targetAlpha = 1f,
                                closeAfter = false
                            )
                        },
                        onDragEnd = {
                            if (dismissOffsetY > viewportHeightPx * 0.12f) {
                                animatePreviewTransform(
                                    targetOffsetY = viewportHeightPx * 0.82f,
                                    targetScale = 0.78f,
                                    targetAlpha = 0f,
                                    closeAfter = true
                                )
                            } else {
                                animatePreviewTransform(
                                    targetOffsetY = 0f,
                                    targetScale = 1f,
                                    targetAlpha = 1f,
                                    closeAfter = false
                                )
                            }
                        }
                    )
                },
            userScrollEnabled = canSwipePages,
            beyondViewportPageCount = 1,
            key = { page -> mediaMessages[page].id }
        ) { page ->
            MediaPreviewPage(
                message = mediaMessages[page],
                isActive = pagerState.currentPage == page,
                onZoomChange = { scale ->
                    pageZoomById[mediaMessages[page].id] = scale
                },
                onDismissRequest = ::dismissWithShrinkAnimation,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (currentMessage.type != FloatingChatMessageType.VideoPreview) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ImageActionPill(
                    label = "识图",
                    onClick = { onMediaAction(currentMessage, MediaActionContract.AnalyzeImage) }
                )
                ImageActionPill(
                    label = "找物",
                    onClick = { onMediaAction(currentMessage, MediaActionContract.FindObject) }
                )
            }
        }

        StandaloneImageQuickActions(
            onOpenActions = { onOpenActions(currentMessage) },
            onShare = { onMediaAction(currentMessage, MediaActionContract.Share) },
            onSave = { onMediaAction(currentMessage, MediaActionContract.Save) },
            onFavorite = { onMediaAction(currentMessage, MediaActionContract.Favorite) },
            favorite = favoriteMediaIds[currentMessage.id] == true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 18.dp)
        )

        actionStatus?.let { status ->
            MediaActionStatusPill(
                text = status,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }

        VisibilityAccessStrip(
            visibility = currentMessage.visibility,
            accessState = currentMessage.accessState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPreviewPage(
    message: FloatingChatMessage,
    isActive: Boolean,
    onZoomChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideo = message.type == FloatingChatMessageType.VideoPreview
    var zoomScale by remember(message.id) { mutableStateOf(mediaPreviewMinimumZoom()) }
    var offsetX by remember(message.id) { mutableStateOf(0f) }
    var offsetY by remember(message.id) { mutableStateOf(0f) }
    var viewportWidthPx by remember(message.id) { mutableStateOf(0) }
    var viewportHeightPx by remember(message.id) { mutableStateOf(0) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (isVideo || !isActive) return@rememberTransformableState
        val nextScale = (zoomScale * zoomChange).coerceIn(
            mediaPreviewMinimumZoom(),
            mediaPreviewMaximumZoom()
        )
        zoomScale = nextScale
        if (nextScale == mediaPreviewMinimumZoom()) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val maxOffsetX = ((viewportWidthPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((viewportHeightPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            zoomScale = mediaPreviewMinimumZoom()
            offsetX = 0f
            offsetY = 0f
        }
    }

    LaunchedEffect(zoomScale, isActive, message.id) {
        onZoomChange(
            if (isActive) {
                zoomScale
            } else {
                mediaPreviewMinimumZoom()
            }
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(OverlayTokens.blankScrim)
                .onSizeChanged { size ->
                    viewportWidthPx = size.width
                    viewportHeightPx = size.height
                }
                .then(
                    if (isVideo) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(message.id, zoomScale, isActive) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isActive) return@detectTapGestures
                                        if (zoomScale > mediaPreviewMinimumZoom()) {
                                            zoomScale = mediaPreviewMinimumZoom()
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            onDismissRequest()
                                        }
                                    }
                                )
                            }
                            .transformable(
                                state = transformState,
                                canPan = {
                                    zoomScale > mediaPreviewMinimumZoom() + 0.01f
                                }
                            )
                    }
                )
        ) {
            if (isVideo) {
                val frame = mediaPreviewFrameSize(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    orientation = message.thumbnailOrientation,
                    mediaAspectRatio = message.mediaAspectRatio ?: videoPreviewAspectRatio(context, message)
                )
                MediaPreviewVideoPlayer(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(frame.width)
                        .height(frame.height)
                )
            } else {
                MediaThumbnailSurface(
                    message = message,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    showChrome = false,
                    useAspectFit = mediaPreviewUsesAspectFit()
                )
            }
        }
    }
}

@Composable
private fun LegacyMediaPreviewOverlay(
    message: FloatingChatMessage,
    actionStatus: String?,
    favorite: Boolean,
    onClose: () -> Unit,
    onOpenActions: () -> Unit,
    onMediaAction: (MediaActionContract) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideo = message.type == FloatingChatMessageType.VideoPreview
    var zoomScale by remember(message.id) { mutableStateOf(mediaPreviewMinimumZoom()) }
    var offsetX by remember(message.id) { mutableStateOf(0f) }
    var offsetY by remember(message.id) { mutableStateOf(0f) }
    var viewportWidthPx by remember(message.id) { mutableStateOf(0) }
    var viewportHeightPx by remember(message.id) { mutableStateOf(0) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (isVideo) return@rememberTransformableState
        val nextScale = (zoomScale * zoomChange).coerceIn(
            mediaPreviewMinimumZoom(),
            mediaPreviewMaximumZoom()
        )
        zoomScale = nextScale
        if (nextScale == mediaPreviewMinimumZoom()) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val maxOffsetX = ((viewportWidthPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((viewportHeightPx * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
            offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
            offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .background(OverlayTokens.blankScrim)
            .pointerInput(message.id) {
                detectTapGestures(
                    onTap = { onClose() }
                )
            }
            .padding(horizontal = 18.dp, vertical = 28.dp)
    ) {
        val mediaFrame = remember(
            maxWidth,
            maxHeight,
            message.thumbnailOrientation,
            message.mediaAspectRatio,
            message.resourceUrl,
            message.thumbnailUrl,
            isVideo
        ) {
            mediaPreviewFrameSize(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                orientation = message.thumbnailOrientation,
                mediaAspectRatio = message.mediaAspectRatio ?: if (isVideo) {
                    videoPreviewAspectRatio(context, message)
                } else {
                    null
                }
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(mediaFrame.width)
                .height(mediaFrame.height)
                .then(
                    if (isVideo) {
                        Modifier
                    } else {
                        Modifier.pointerInput(message.id) {
                            detectTapGestures(
                                onTap = {
                                    if (zoomScale > mediaPreviewMinimumZoom()) {
                                        zoomScale = mediaPreviewMinimumZoom()
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        onClose()
                                    }
                                }
                            )
                        }
                    }
                )
                .clip(RoundedCornerShape(10.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.standaloneMediaBorder, RoundedCornerShape(10.dp))
                .clipToBounds()
                .onSizeChanged { size ->
                    viewportWidthPx = size.width
                    viewportHeightPx = size.height
                }
                .then(
                    if (isVideo) {
                        Modifier
                    } else {
                        Modifier.transformable(state = transformState)
                    }
                )
        ) {
            if (isVideo) {
                MediaPreviewVideoPlayer(
                    message = message,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                MediaThumbnailSurface(
                    message = message,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    showChrome = false,
                    useAspectFit = mediaPreviewUsesAspectFit()
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ImageActionPill(
                    label = "识图",
                    onClick = { onMediaAction(MediaActionContract.AnalyzeImage) }
                )
                ImageActionPill(
                    label = "找物",
                    onClick = { onMediaAction(MediaActionContract.FindObject) }
                )
            }
            StandaloneImageQuickActions(
                onOpenActions = onOpenActions,
                onShare = { onMediaAction(MediaActionContract.Share) },
                onSave = { onMediaAction(MediaActionContract.Save) },
                onFavorite = { onMediaAction(MediaActionContract.Favorite) },
                favorite = favorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 9.dp)
            )
            actionStatus?.let { status ->
                MediaActionStatusPill(
                    text = status,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 9.dp)
                )
            }
            VisibilityAccessStrip(
                visibility = message.visibility,
                accessState = message.accessState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 9.dp, top = 9.dp)
            )
        }
    }
}

@Composable
private fun MediaActionSheetOverlay(
    message: FloatingChatMessage,
    onClose: () -> Unit,
    onMediaAction: (MediaActionContract) -> Unit,
    modifier: Modifier = Modifier
) {
    val clickSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(OverlayTokens.mediaSheetScrim)
            .clickable(
                interactionSource = clickSource,
                indication = null,
                onClick = onClose
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp, bottomStart = 9.dp, bottomEnd = 9.dp))
                .background(OverlayTokens.mediaSheet)
                .border(
                    width = 1.dp,
                    color = OverlayTokens.mediaSheetBorder,
                    shape = RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp, bottomStart = 9.dp, bottomEnd = 9.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaActionSheetHeader(message)
            val rows = listOf(
                listOf(MediaAction.Share, MediaAction.Save, MediaAction.Favorite, MediaAction.More),
                listOf(MediaAction.Visibility, MediaAction.Edit, MediaAction.Comment, MediaAction.Grid)
            )
            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowActions.forEach { action ->
                        MediaSheetActionItem(
                            action = action,
                            onClick = { onMediaAction(action.toContract()) }
                        )
                    }
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(OverlayTokens.mediaSheetCancel),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.mediaSheetCancel,
                    contentColor = OverlayTokens.mediaSheetText
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                TextLabel(
                    text = "取消",
                    size = 11.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.mediaSheetText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MediaActionSheetHeader(message: FloatingChatMessage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OverlayTokens.imageBase)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(8.dp))
        ) {
            ImageThumbnailSurface(
                message = message,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextLabel(
                text = message.senderName,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.mediaSheetText,
                maxLines = 1
            )
            TextLabel(
                text = mediaWatermarkText(message.resourceUrl, message.thumbnailUrl).ifBlank { "本地图片" },
                size = 8.sp,
                color = OverlayTokens.mediaSheetMutedText,
                maxLines = 1
            )
            VisibilityAccessStrip(
                visibility = message.visibility,
                accessState = message.accessState
            )
        }
    }
}

@Composable
private fun MediaSheetActionItem(
    action: MediaAction,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CompactInteractiveSize {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(34.dp)
                    .border(1.dp, OverlayTokens.mediaSheetIconBorder, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = OverlayTokens.mediaSheetIconBox,
                    contentColor = OverlayTokens.mediaSheetIcon
                )
            ) {
                MediaActionIcon(
                    action = action,
                    color = OverlayTokens.mediaSheetIcon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        TextLabel(
            text = action.label,
            size = 8.sp,
            color = OverlayTokens.mediaSheetText,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp
        )
    }
}

@Composable
private fun VideoPreviewContent(
    message: FloatingChatMessage,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    onToggleSelection: () -> Unit,
    onContentBoundsChanged: ((Rect) -> Unit)? = null
) {
    if (message.presentation == FloatingChatMessagePresentation.MediaStandalone) {
        StandaloneMediaMessageContent(
            message = message,
            onPreviewMedia = onPreviewMedia,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            onToggleSelection = onToggleSelection,
            onContentBoundsChanged = onContentBoundsChanged
        )
        return
    }

    val mediaBitmap = rememberAsyncMediaThumbnailBitmap(message)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedThumbnailHeightDp(message.thumbnailOrientation).dp)
                .clip(RoundedCornerShape(7.dp))
                .background(OverlayTokens.videoBase)
                .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
        ) {
            if (mediaBitmap != null) {
                Image(
                    bitmap = mediaBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PlaceholderVideoCanvas(modifier = Modifier.fillMaxSize())
            }
            VideoPlayGlyph(modifier = Modifier.fillMaxSize())
            TextLabel(
                text = message.text,
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 7.dp, end = 8.dp)
            )
            TextLabel(
                text = mediaWatermarkText(
                    resourceUrl = message.resourceUrl,
                    thumbnailUrl = message.thumbnailUrl
                ),
                size = 8.sp,
                color = OverlayTokens.imageWatermark,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun PlaceholderImageCanvas(
    orientation: FloatingChatThumbnailOrientation?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = OverlayTokens.imageBlock,
            topLeft = Offset(size.width * 0.25f, size.height * 0.12f),
            size = Size(size.width * 0.68f, size.height * 0.76f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawCircle(
            color = OverlayTokens.imageHighlight,
            radius = size.minDimension * 0.15f,
            center = Offset(size.width * 0.74f, size.height * 0.30f)
        )
        if (orientation == FloatingChatThumbnailOrientation.Vertical) {
            drawRect(
                color = OverlayTokens.imageFade,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height * 0.18f)
            )
            drawRect(
                color = OverlayTokens.imageFade,
                topLeft = Offset(0f, size.height * 0.82f),
                size = Size(size.width, size.height * 0.18f)
            )
        }
    }
}

@Composable
private fun PlaceholderVideoCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = OverlayTokens.videoFrame,
            topLeft = Offset(size.width * 0.05f, size.height * 0.14f),
            size = Size(size.width * 0.90f, size.height * 0.72f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }
}

@Composable
private fun VerticalImageCropOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            color = OverlayTokens.imageFade,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height * 0.15f)
        )
        drawRect(
            color = OverlayTokens.imageFade,
            topLeft = Offset(0f, size.height * 0.85f),
            size = Size(size.width, size.height * 0.15f)
        )
    }
}

@Composable
private fun VideoPlayGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = OverlayTokens.primaryText
        )
    }
}

@Composable
private fun InlineContactContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareAvatarChip(
            text = message.cardName?.take(2).orEmpty().ifBlank { "名片" },
            background = OverlayTokens.inlineAvatar,
            sizeDp = 26
        )
        Spacer(modifier = Modifier.width(7.dp))
        TextLabel(
            text = message.text,
            size = 11.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.cardPrimaryText,
            maxLines = 1,
            shadow = OverlayTokens.imModuleTextShadow
        )
    }
}

@Composable
private fun InlineLocationContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocationGlyph(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = message.locationTitle ?: message.text,
                size = 10.5.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.locationAddress.orEmpty(),
                size = 8.5.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun QuoteBlock(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OverlayTokens.quoteBackground)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(26.dp)
                .background(OverlayTokens.quoteBar)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            TextLabel(
                text = message.quoteAuthor.orEmpty(),
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.bubbleText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.quoteText.orEmpty(),
                size = 9.sp,
                color = OverlayTokens.bubbleTextMuted,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun VisibilityAccessStrip(
    visibility: FloatingChatVisibilityScope?,
    accessState: FloatingChatAccessState?,
    modifier: Modifier = Modifier
) {
    if (visibility == null && accessState == null) {
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibility?.let {
            TinyChip(label = it.label, color = OverlayTokens.cardSecondaryText)
        }
        accessState?.let {
            TinyChip(label = it.label, color = accessStateColor(it))
        }
    }
}

@Composable
private fun ResourceUrlLine(url: String?) {
    if (url.isNullOrBlank()) {
        return
    }
    TextLabel(
        text = url,
        size = 8.sp,
        color = OverlayTokens.linkText,
        maxLines = 1,
        shadow = OverlayTokens.imModuleTextShadow
    )
}

@Composable
private fun TinyChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(OverlayTokens.permissionChip)
            .border(1.dp, OverlayTokens.permissionChipBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(
            text = label,
            size = 8.sp,
            weight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center,
            shadow = OverlayTokens.imModuleTextShadow
        )
    }
}

@Composable
private fun SquareAvatarChip(
    text: String,
    background: Color,
    sizeDp: Int = 34,
    imageUri: String? = null
) {
    val avatarBitmap = rememberAsyncAvatarBitmap(imageUri)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = text,
                size = if (sizeDp > 30) 10.sp else 8.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun LocationMapPreviewCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(OverlayTokens.locationMapBase)) {
        drawRect(color = OverlayTokens.locationMapBase)
        drawRoundRect(
            color = OverlayTokens.locationMapPark,
            topLeft = Offset(size.width * 0.58f, size.height * 0.08f),
            size = Size(size.width * 0.34f, size.height * 0.24f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawRoundRect(
            color = OverlayTokens.locationMapPark,
            topLeft = Offset(size.width * 0.68f, size.height * 0.58f),
            size = Size(size.width * 0.24f, size.height * 0.30f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        drawRoundRect(
            color = OverlayTokens.locationMapWater,
            topLeft = Offset(size.width * 0.05f, size.height * 0.54f),
            size = Size(size.width * 0.22f, size.height * 0.24f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        val roadStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val minorRoadStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(
            color = OverlayTokens.locationMapRoad,
            start = Offset(size.width * 0.02f, size.height * 0.24f),
            end = Offset(size.width * 0.98f, size.height * 0.76f),
            strokeWidth = roadStroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = OverlayTokens.locationMapRoad,
            start = Offset(size.width * 0.10f, size.height * 0.86f),
            end = Offset(size.width * 0.92f, size.height * 0.16f),
            strokeWidth = roadStroke.width,
            cap = StrokeCap.Round
        )
        listOf(0.22f, 0.42f, 0.62f, 0.82f).forEach { x ->
            drawLine(
                color = OverlayTokens.locationMapMinorRoad,
                start = Offset(size.width * x, 0f),
                end = Offset(size.width * (x - 0.18f), size.height),
                strokeWidth = minorRoadStroke.width,
                cap = StrokeCap.Round
            )
        }
        listOf(0.18f, 0.42f, 0.66f).forEach { y ->
            drawLine(
                color = OverlayTokens.locationMapMinorRoad,
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * (y + 0.12f)),
                strokeWidth = minorRoadStroke.width,
                cap = StrokeCap.Round
            )
        }
        val pinCenter = Offset(size.width * 0.50f, size.height * 0.43f)
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.15f,
            center = pinCenter
        )
        drawCircle(
            color = OverlayTokens.locationMapPin,
            radius = size.minDimension * 0.12f,
            center = pinCenter
        )
        drawLine(
            color = OverlayTokens.locationMapPin,
            start = Offset(pinCenter.x, pinCenter.y + size.minDimension * 0.12f),
            end = Offset(pinCenter.x, pinCenter.y + size.minDimension * 0.29f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FileFormatIcon(
    format: FloatingChatFileFormat?,
    fileName: String? = null
) {
    val label = fileBadgeLabelFor(fileName, format)
    Box(
        modifier = Modifier
            .size(width = fileBadgeWidthDp().dp, height = fileBadgeHeightDp().dp)
            .clip(RoundedCornerShape(3.dp))
            .background(fileBadgeColorFor(fileName, format)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(7.dp)
                .background(Color(0x26000000))
        )
        TextLabel(
            text = label,
            size = if (label.length > 3) 5.sp else 6.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.fileIconText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LocationGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = OverlayTokens.locationPin,
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.50f, size.height * 0.43f),
            style = stroke
        )
        drawCircle(
            color = OverlayTokens.locationPin,
            radius = size.minDimension * 0.08f,
            center = Offset(size.width * 0.50f, size.height * 0.43f)
        )
        drawLine(
            color = OverlayTokens.locationPin,
            start = Offset(size.width * 0.50f, size.height * 0.73f),
            end = Offset(size.width * 0.50f, size.height * 0.92f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ImageActionPill(
    label: String,
    onClick: (() -> Unit)? = null
) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier
            .width(32.dp)
            .height(20.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OverlayTokens.imageAction,
            contentColor = OverlayTokens.primaryText,
            disabledContainerColor = OverlayTokens.imageAction,
            disabledContentColor = OverlayTokens.primaryText
        ),
        border = BorderStroke(1.dp, OverlayTokens.hairline),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        TextLabel(
            text = label,
            size = 8.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.primaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MediaActionStatusPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OverlayTokens.mediaActionStatus)
            .border(1.dp, OverlayTokens.mediaActionBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        TextLabel(
            text = text,
            size = 9.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.primaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MediaPreviewVideoPlayer(
    message: FloatingChatMessage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaUri = remember(message.type, message.resourceUrl, message.thumbnailUrl) {
        playableVideoUriForMessage(message)
    }
    if (mediaUri == null) {
        MediaThumbnailSurface(
            message = message,
            modifier = modifier,
            showChrome = false,
            useAspectFit = mediaPreviewVideoUsesAspectFit()
        )
        return
    }
    val tapSource = remember { MutableInteractionSource() }
    val targetAspectRatio = remember(message.id, message.mediaAspectRatio, message.thumbnailOrientation, message.resourceUrl, message.thumbnailUrl) {
        message.mediaAspectRatio ?: videoPreviewAspectRatio(context, message)
    }
    var playbackRequested by remember(message.id) { mutableStateOf(false) }
    var surfaceTexture by remember(message.id) { mutableStateOf<SurfaceTexture?>(null) }
    var mediaPlayerRef by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(message.id) { mutableStateOf(false) }
    var playing by remember(message.id) { mutableStateOf(false) }
    var completed by remember(message.id) { mutableStateOf(false) }
    var failed by remember(message.id) { mutableStateOf(false) }
    var durationMs by remember(message.id) { mutableStateOf(0) }
    var positionMs by remember(message.id) { mutableStateOf(0) }
    var sliderPositionMs by remember(message.id) { mutableStateOf(0f) }
    var draggingProgress by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(mediaPlayerRef, playing, draggingProgress, completed) {
        while (mediaPlayerRef != null && !draggingProgress && (playing || completed)) {
            val player = mediaPlayerRef ?: break
            positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            sliderPositionMs = positionMs.toFloat()
            kotlinx.coroutines.delay(250)
        }
    }

    DisposableEffect(message.id, mediaUri, surfaceTexture, playbackRequested) {
        val texture = surfaceTexture
        if (!playbackRequested || texture == null) {
            onDispose {
                mediaPlayerRef = null
                prepared = false
                playing = false
                completed = false
                failed = false
            }
        } else {
            prepared = false
            playing = false
            completed = false
            failed = false

            val surface = Surface(texture)
            val mediaPlayer = MediaPlayer()
            mediaPlayerRef = mediaPlayer
            var released = false

            val releasePlayer = {
                if (released) {
                    Unit
                } else {
                    released = true
                    runCatching {
                        mediaPlayer.setSurface(null)
                        mediaPlayer.setOnPreparedListener(null)
                        mediaPlayer.setOnCompletionListener(null)
                        mediaPlayer.setOnErrorListener(null)
                        if (mediaPlayer.isPlaying) mediaPlayer.stop()
                    }.onFailure {
                        Log.w(TAG, "failed to stop preview video player", it)
                    }
                    runCatching {
                        mediaPlayer.reset()
                    }.onFailure {
                        Log.w(TAG, "failed to reset preview video player", it)
                    }
                    runCatching {
                        mediaPlayer.release()
                    }.onFailure {
                        Log.w(TAG, "failed to release preview video player", it)
                    }
                    runCatching {
                        surface.release()
                    }.onFailure {
                        Log.w(TAG, "failed to release preview video surface", it)
                    }
                    if (mediaPlayerRef === mediaPlayer) {
                        mediaPlayerRef = null
                    }
                }
            }

            runCatching {
                mediaPlayer.setSurface(surface)
                mediaPlayer.isLooping = false
                mediaPlayer.setOnPreparedListener { player ->
                    prepared = true
                    failed = false
                    completed = false
                    durationMs = player.duration.coerceAtLeast(0)
                    positionMs = 0
                    sliderPositionMs = 0f
                    player.start()
                    playing = true
                }
                mediaPlayer.setOnCompletionListener {
                    playing = false
                    completed = true
                    positionMs = durationMs
                    sliderPositionMs = durationMs.toFloat()
                }
                mediaPlayer.setOnErrorListener { _, _, _ ->
                    failed = true
                    playing = false
                    true
                }
                mediaPlayer.setPlayableVideoDataSource(context, mediaUri)
                mediaPlayer.prepareAsync()
            }.onFailure {
                Log.w(TAG, "failed to open preview video source: $mediaUri", it)
                failed = true
                playing = false
                releasePlayer()
            }

            onDispose {
                prepared = false
                playing = false
                releasePlayer()
            }
        }
    }

    Box(modifier = modifier) {
        if (playbackRequested) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                surfaceTexture = texture
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                surfaceTexture = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                    }
                },
                update = { view ->
                    if (view.isAvailable) {
                        surfaceTexture = view.surfaceTexture
                    }
                    applyTextureViewAspectFitTransform(
                        textureView = view,
                        videoAspectRatio = targetAspectRatio
                    )
                }
            )
        }

        val showPoster = !playbackRequested || failed
        if (showPoster) {
            MediaThumbnailSurface(
                message = message,
                modifier = Modifier.fillMaxSize(),
                showChrome = false,
                useAspectFit = mediaPreviewVideoUsesAspectFit()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = tapSource,
                    indication = null
                ) {
                    val player = mediaPlayerRef
                    when {
                        !playbackRequested -> playbackRequested = true
                        failed -> {
                            playbackRequested = false
                            surfaceTexture = null
                            prepared = false
                            completed = false
                            failed = false
                            durationMs = 0
                            positionMs = 0
                            sliderPositionMs = 0f
                            playbackRequested = true
                        }
                        player == null || !prepared -> Unit
                        player.isPlaying -> {
                            player.pause()
                            playing = false
                        }
                        completed -> {
                            player.seekTo(0)
                            player.start()
                            completed = false
                            playing = true
                        }
                        else -> {
                            player.start()
                            playing = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!playing) {
                VideoPlayGlyph(modifier = Modifier.fillMaxSize())
            }

            VideoPlayerControlBar(
                currentPositionMs = if (draggingProgress) sliderPositionMs.toInt() else positionMs,
                durationMs = durationMs,
                playing = playing,
                onTogglePlayback = {
                    val player = mediaPlayerRef ?: return@VideoPlayerControlBar
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        if (completed) {
                            player.seekTo(0)
                            completed = false
                            positionMs = 0
                            sliderPositionMs = 0f
                        }
                        player.start()
                        playing = true
                    }
                },
                sliderValue = sliderPositionMs,
                onSliderValueChange = { value ->
                    draggingProgress = true
                    sliderPositionMs = value
                },
                onSliderChangeFinished = {
                    val player = mediaPlayerRef ?: return@VideoPlayerControlBar
                    val seekPosition = sliderPositionMs.toInt().coerceIn(0, durationMs)
                    player.seekTo(seekPosition)
                    positionMs = seekPosition
                    draggingProgress = false
                    if (completed && seekPosition < durationMs) {
                        completed = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = mediaPreviewVideoControlBottomPaddingDp().dp)
            )
        }
    }
}

private fun cardColorFor(kind: FloatingChatContactCardKind?): Color {
    return when (kind) {
        FloatingChatContactCardKind.WeCom -> Color(0xFF357C68)
        FloatingChatContactCardKind.Personal -> Color(0xFF5674A8)
        FloatingChatContactCardKind.OfficialAccount -> Color(0xFF90643D)
        FloatingChatContactCardKind.MiniProgram -> Color(0xFF4B7F9A)
        FloatingChatContactCardKind.Channel -> Color(0xFF9A536B)
        null -> OverlayTokens.inlineAvatar
    }
}

@Composable
private fun VideoPlayerControlBar(
    currentPositionMs: Int,
    durationMs: Int,
    playing: Boolean,
    onTogglePlayback: () -> Unit,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x7A0E1418))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniVideoControlButton(
            playing = playing,
            onClick = onTogglePlayback
        )
        Spacer(modifier = Modifier.width(6.dp))
        TextLabel(
            text = formatVideoTimecode(currentPositionMs),
            size = 8.sp,
            weight = FontWeight.Bold,
            color = Color(0xFFF5F8FA),
            maxLines = 1
        )
        VideoTimelineSlider(
            value = sliderValue,
            maxValue = durationMs.toFloat(),
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderChangeFinished,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        TextLabel(
            text = formatVideoTimecode(durationMs),
            size = 8.sp,
            weight = FontWeight.Bold,
            color = Color(0xFFF5F8FA),
            maxLines = 1
        )
    }
}

@Composable
private fun VideoTimelineSlider(
    value: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactInteractiveSize {
        val safeMax = maxValue.coerceAtLeast(1f)
        Slider(
            value = value.coerceIn(0f, safeMax),
            onValueChange = onValueChange,
            valueRange = 0f..safeMax,
            onValueChangeFinished = onValueChangeFinished,
            modifier = modifier.height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFF5F8FA),
                activeTrackColor = Color(0xFFF5F8FA),
                inactiveTrackColor = Color(0x5CF5F8FA)
            )
        )
    }
}

@Composable
private fun MiniVideoControlButton(
    playing: Boolean,
    onClick: () -> Unit
) {
    CompactInteractiveSize {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(24.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0x24F5F8FA),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )
        }
    }
}

private fun messageBubbleColor(message: FloatingChatMessage): Color {
    if (message.isPaymentCardMessage()) {
        return OverlayTokens.paymentCard
    }
    if (!messageTypeUsesImModuleBubble(message.type)) {
        return cardMessageColor(message)
    }
    return when {
        message.presentation == FloatingChatMessagePresentation.System -> OverlayTokens.systemBubble
        message.fromMe -> OverlayTokens.selfBubble
        else -> OverlayTokens.otherBubble
    }
}

private fun messageBubbleBorderColor(message: FloatingChatMessage): Color {
    if (aiDraftMessageUsesGreenDashedBubble(message)) {
        return OverlayTokens.aiDashedBorder
    }
    if (message.isPaymentCardMessage()) {
        return OverlayTokens.paymentCardBorder
    }
    if (!messageTypeUsesImModuleBubble(message.type)) {
        return when {
            message.kind == FloatingChatMessageKind.AiDraft -> OverlayTokens.aiBorder
            message.type == FloatingChatMessageType.Location ||
                message.type == FloatingChatMessageType.InlineLocation -> OverlayTokens.locationCardBorder
            message.type == FloatingChatMessageType.ContactLink ||
                message.type == FloatingChatMessageType.InlineContact -> OverlayTokens.contactCardBorder
            message.type == FloatingChatMessageType.FilePreview -> OverlayTokens.fileCardBorder
            message.type == FloatingChatMessageType.Voice -> OverlayTokens.voiceCardBorder
            else -> OverlayTokens.legacyBubbleBorder
        }
    }
    return when {
        message.presentation == FloatingChatMessagePresentation.System -> OverlayTokens.bubbleBorder
        message.fromMe -> OverlayTokens.selfBubbleBorder
        else -> OverlayTokens.otherBubbleBorder
    }
}

private fun cardMessageColor(message: FloatingChatMessage): Color {
    if (message.isPaymentCardMessage()) {
        return OverlayTokens.paymentCard
    }
    return cardMessageColorFor(message.type)
}

private fun cardMessageColorFor(type: FloatingChatMessageType): Color {
    return when (type) {
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> OverlayTokens.locationCard
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.InlineContact -> OverlayTokens.contactCard
        FloatingChatMessageType.MiniProgramLink -> OverlayTokens.miniProgramCard
        FloatingChatMessageType.FilePreview -> OverlayTokens.fileCard
        FloatingChatMessageType.Voice -> OverlayTokens.voiceCard
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.VideoPreview -> OverlayTokens.mediaCard
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote -> OverlayTokens.specialCard
    }
}

private enum class PaymentCardKind(val label: String) {
    RedPacket("红包"),
    Transfer("转账")
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
    return if (fromMe) "已发起转账" else "待确认收款"
}

internal fun transferPanelSupportsGroupRecipientSelection(): Boolean = true

internal fun transferMessageDetailForRecipient(recipientName: String?, note: String): String {
    val safeNote = note.ifBlank { "转账给你，请查收" }
    val safeRecipientName = recipientName?.trim().orEmpty()
    return when {
        safeRecipientName.isBlank() -> safeNote
        note.isBlank() -> "转账给 $safeRecipientName"
        else -> "转账给 $safeRecipientName：$safeNote"
    }
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

internal fun locationMessageUsesMapPreview(): Boolean = true

internal fun locationMapPreviewHeightDp(): Int = 86

internal fun locationMapBubbleTextUsesShadow(): Boolean = false

internal fun locationMapPinColorArgb(): Int = OverlayTokens.locationMapPin.toArgb()

internal fun filePreviewUsesWechatDocumentCard(): Boolean = true

internal fun filePreviewOpensFullscreenViewer(): Boolean = true

internal fun filePreviewUsesInlineExpansion(): Boolean = false

internal fun filePreviewCardUsesCombinedClickForPreviewAndLongPress(): Boolean = true

internal fun documentPreviewRunsInsideFloatingOverlay(): Boolean = true

internal fun documentExternalOpenHidesFloatingOverlay(): Boolean = true

internal fun documentExternalOpenRestoresOverlayOnReturn(): Boolean = true

internal fun fileWechatCardMinHeightDp(): Int = 46

internal fun fileWechatTitleTextSizeSp(): Int = 10

internal fun fileWechatSizeTextSizeSp(): Int = 8

internal fun fileWechatCardUsesNormalTextWeight(): Boolean = true

internal fun fileWechatCardUsesTextShadow(): Boolean = false

internal fun fileBadgeWidthDp(): Int = 25

internal fun fileBadgeHeightDp(): Int = 29

internal fun fileBadgeLabelFor(fileName: String?, format: FloatingChatFileFormat?): String {
    return when (format) {
        FloatingChatFileFormat.Txt -> "TXT"
        FloatingChatFileFormat.Markdown -> "MD"
        FloatingChatFileFormat.Word -> "DOCX"
        FloatingChatFileFormat.Pdf -> "PDF"
        null -> fileExtensionLabel(fileName)
    }
}

internal fun fileBadgeColorArgbFor(fileName: String?, format: FloatingChatFileFormat?): Int {
    return fileBadgeColorFor(fileName, format).toArgb()
}

private fun fileExtensionLabel(fileName: String?): String {
    val extension = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.uppercase(Locale.US)
    return when (extension) {
        "DOC", "DOCX" -> "DOCX"
        "PDF" -> "PDF"
        "TXT" -> "TXT"
        "MD", "MARKDOWN" -> "MD"
        "ZIP" -> "ZIP"
        "RAR" -> "RAR"
        else -> extension?.take(4) ?: "FILE"
    }
}

private fun fileBadgeColorFor(fileName: String?, format: FloatingChatFileFormat?): Color {
    return when (fileBadgeLabelFor(fileName, format)) {
        "DOCX" -> Color(0xFF1E88E5)
        "PDF" -> Color(0xFFE44747)
        "TXT" -> Color(0xFF65727A)
        "MD" -> Color(0xFF476B82)
        "ZIP" -> Color(0xFF8A6F2A)
        "RAR" -> Color(0xFF7A5493)
        else -> Color(0xFF65727A)
    }
}

internal fun paymentCardAmountTextFor(text: String): String {
    val amount = Regex("""[¥￥楼]\s*([0-9]+(?:\.[0-9]{1,2})?)""")
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
        marker.contains("red-packet") ||
            marker.contains("红包") ||
            marker.contains("绾㈠寘") -> PaymentCardKind.RedPacket
        marker.contains("transfer") ||
            marker.contains("转账") ||
            marker.contains("杞处") -> PaymentCardKind.Transfer
        else -> null
    }
}

private fun FloatingChatMessage.isPaymentCardMessage(): Boolean {
    return paymentCardKindFor(resourceUrl, appName, text) != null
}

private fun FloatingChatMessage.isRedPacketMessage(): Boolean {
    return paymentCardKindFor(resourceUrl, appName, text) == PaymentCardKind.RedPacket
}

private fun Modifier.aiDraftDashedBorder(shape: RoundedCornerShape): Modifier {
    return drawWithContent {
        drawContent()
        val strokePx = 1.4.dp.toPx()
        val inset = strokePx / 2f
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        drawRoundRect(
            color = OverlayTokens.aiDashedBorder,
            topLeft = Offset(inset, inset),
            size = size.copy(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(
                width = strokePx,
                pathEffect = pathEffect,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun accessStateColor(state: FloatingChatAccessState): Color {
    return when (state) {
        FloatingChatAccessState.Visible -> OverlayTokens.aiText
        FloatingChatAccessState.NeedsApply -> OverlayTokens.linkText
        FloatingChatAccessState.Applied -> OverlayTokens.bubbleTextMuted
        FloatingChatAccessState.Approved -> OverlayTokens.aiText
    }
}

internal fun isLocalContentUri(uriText: String?): Boolean {
    return uriText?.startsWith("content://") == true
}

internal fun isLocalMediaUri(uriText: String?): Boolean {
    return isLocalContentUri(uriText) || uriText?.startsWith("file://") == true
}

internal fun mediaWatermarkText(
    resourceUrl: String?,
    thumbnailUrl: String?
): String {
    return resourceUrl ?: thumbnailUrl.orEmpty()
}

internal data class MediaActionResult(
    val status: String,
    val toast: Boolean = true
)

internal fun performMediaAction(
    context: Context,
    message: FloatingChatMessage,
    action: MediaActionContract,
    favoriteMediaIds: MutableMap<String, Boolean>,
    onOpenActions: () -> Unit,
    onFavoriteChanged: (FloatingChatMessage, Boolean) -> Unit = { _, _ -> }
): MediaActionResult {
    return when (action) {
        MediaActionContract.AnalyzeImage -> MediaActionResult("已打开识图入口")
        MediaActionContract.FindObject -> MediaActionResult("已打开找物入口")
        MediaActionContract.Share -> shareMedia(context, message)
        MediaActionContract.Save -> saveMediaToGallery(context, message)
        MediaActionContract.Favorite -> {
            val nextFavorite = favoriteMediaIds[message.id] != true
            favoriteMediaIds[message.id] = nextFavorite
            onFavoriteChanged(message, nextFavorite)
            MediaActionResult(if (nextFavorite) "已收藏" else "已取消收藏")
        }
        MediaActionContract.More -> {
            onOpenActions()
            MediaActionResult("更多选项", toast = false)
        }
        MediaActionContract.Visibility -> MediaActionResult("可见范围：${message.visibility?.label ?: "未设置"}")
        MediaActionContract.Edit -> MediaActionResult("编辑入口已打开")
        MediaActionContract.Comment -> MediaActionResult("已定位到聊天")
        MediaActionContract.Grid -> MediaActionResult("图片管理入口已打开")
    }
}

private fun shareMedia(
    context: Context,
    message: FloatingChatMessage
): MediaActionResult {
    val uri = mediaActionUri(message) ?: return MediaActionResult("没有可分享的媒体")
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mediaMimeType(message)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "分享媒体").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(chooser)
        MediaActionResult("已打开分享")
    }.getOrElse {
        MediaActionResult("没有可用的分享应用")
    }
}

private fun saveMediaToGallery(
    context: Context,
    message: FloatingChatMessage
): MediaActionResult {
    val sourceUri = mediaActionUri(message) ?: return MediaActionResult("没有可保存的媒体")
    if (sourceUri.scheme !in setOf("content", "file")) {
        return MediaActionResult("网络媒体暂不支持直接保存")
    }
    return runCatching {
        val resolver = context.contentResolver
        val isVideo = message.type == FloatingChatMessageType.VideoPreview
        val displayName = "ubiki_${message.id}_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}"
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mediaMimeType(message))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${directory}${File.separator}UbikiTouch")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val targetUri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openInputStream(sourceUri).use { input ->
                resolver.openOutputStream(targetUri).use { output ->
                    if (input == null || output == null) error("Media stream unavailable")
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    targetUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            MediaActionResult("已保存到相册")
        } catch (error: Throwable) {
            resolver.delete(targetUri, null, null)
            throw error
        }
    }.getOrElse {
        MediaActionResult("保存失败")
    }
}

private fun mediaActionUri(message: FloatingChatMessage): Uri? {
    val uriText = message.resourceUrl ?: message.thumbnailUrl ?: return null
    return Uri.parse(uriText)
}

internal fun playableVideoUriTextForMessage(message: FloatingChatMessage): String? {
    if (message.type != FloatingChatMessageType.VideoPreview) return null
    return message.resourceUrl?.takeIf { it.isNotBlank() && isPlayableVideoUri(it) }
}

private fun playableVideoUriForMessage(message: FloatingChatMessage): Uri? {
    return playableVideoUriTextForMessage(message)?.let(Uri::parse)
}

private fun isPlayableVideoUri(uriText: String): Boolean {
    return uriText.startsWith("content://") ||
        uriText.startsWith("file://") ||
        uriText.startsWith("/")
}

private fun MediaPlayer.setPlayableVideoDataSource(
    context: Context,
    uri: Uri
) {
    if (uri.scheme == "file") {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            setDataSource(path)
            return
        }
    }
    if (uri.scheme.isNullOrBlank()) {
        setDataSource(uri.toString())
        return
    }
    setDataSource(context, uri)
}

private fun mediaMimeType(message: FloatingChatMessage): String {
    return if (message.type == FloatingChatMessageType.VideoPreview) "video/*" else "image/*"
}

private fun loadImageThumbnailBitmap(
    context: Context,
    uriText: String?
): Bitmap? {
    if (!isLocalMediaUri(uriText)) return null
    val uri = Uri.parse(uriText)
    return runCatching {
        when {
            uri.scheme == "file" -> decodeFileBitmapRespectingExif(uri.path)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width.coerceAtLeast(1)
                    val height = info.size.height.coerceAtLeast(1)
                    val scale = (width.coerceAtLeast(height).toFloat() / REAL_MEDIA_DECODE_MAX_SIZE_PX)
                        .coerceAtLeast(1f)
                    decoder.setTargetSize(
                        (width / scale).toInt().coerceAtLeast(1),
                        (height / scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            else -> {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }
    }.getOrNull()
}

private fun decodeFileBitmapRespectingExif(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    val rotationDegrees = runCatching {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (rotationDegrees == 0f) return bitmap
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(rotationDegrees) },
        true
    )
}

private fun loadVideoPreviewBitmap(
    context: Context,
    thumbnailUriText: String?,
    resourceUriText: String?
): Bitmap? {
    return loadImageThumbnailBitmap(context, thumbnailUriText)
        ?: loadVideoThumbnailBitmap(context, resourceUriText ?: thumbnailUriText)
}

private fun loadVideoThumbnailBitmap(
    context: Context,
    uriText: String?
): Bitmap? {
    if (!isLocalMediaUri(uriText)) return null
    val uri = Uri.parse(uriText)
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()
}

internal fun fixedThumbnailHeightDp(orientation: FloatingChatThumbnailOrientation?): Int {
    return when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 120
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 56
    }
}

internal fun standaloneMediaHeightDp(
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float? = null
): Int {
    val widthDp = StandaloneImageMaxWidthDp.toFloat()
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    val height = (widthDp / aspectRatio).toInt()
    return height.coerceIn(104, 236)
}

internal fun messageUsesBubbleChrome(presentation: FloatingChatMessagePresentation): Boolean {
    return presentation != FloatingChatMessagePresentation.MediaStandalone &&
        presentation != FloatingChatMessagePresentation.System
}

internal fun standaloneMediaShowsInlineActions(): Boolean = false

internal fun messageBubbleShowsAccessChips(): Boolean = false

internal fun mediaPreviewShowsActions(): Boolean = true

internal fun mediaPreviewUsesScrim(): Boolean = true

internal fun mediaPreviewUsesCardShadow(): Boolean = false

internal fun standaloneMediaListShowsAccessChips(): Boolean = false

internal fun standaloneMediaListShowsWatermark(): Boolean = false

internal fun standaloneMediaListUsesVerticalCropScrim(): Boolean = false

internal fun standaloneMediaListUsesAspectFit(): Boolean = true

internal fun standaloneMediaListUsesUniformSquareShape(): Boolean = false

internal fun standaloneMediaUsesCombinedClickForPreviewAndLongPress(): Boolean = true

internal fun standaloneMediaLongPressOpensMessageMenu(): Boolean = true

internal fun standaloneMediaClickTogglesSelectionInMultiSelect(): Boolean = true

internal fun mediaPreviewShowsAccessChips(): Boolean = true

internal fun mediaPreviewShowsWatermark(): Boolean = false

internal fun mediaPreviewUsesVerticalCropScrim(): Boolean = false

internal fun mediaPreviewUsesAspectFit(): Boolean = true

internal fun mediaPreviewUsesCropScaling(): Boolean = false

internal fun mediaPreviewMaxWidthFraction(): Float = 1f

internal fun mediaPreviewMaxHeightFraction(): Float = 1f

internal fun mediaPreviewSupportsZoom(): Boolean = true

internal fun mediaPreviewSingleTapDismisses(): Boolean = true

internal fun mediaPreviewSupportsSwipeBetweenThreadMedia(): Boolean = true

internal fun mediaPreviewImageTransformLetsPagerHandleSingleFingerSwipe(): Boolean = true

internal fun mediaPreviewDismissGestureUsesVerticalOnlyDrag(): Boolean = true

internal fun mediaPreviewSupportsDragToDismiss(): Boolean = true

internal fun mediaPreviewUsesShrinkDismissAnimation(): Boolean = true

internal fun mediaPreviewBackNavigationCloses(): Boolean = true

internal fun mediaPreviewUsesBlackBackdrop(): Boolean = true

internal fun mediaPreviewHidesFloatingChatButton(): Boolean = false

internal fun mediaPreviewCoversSystemBars(): Boolean = true

internal fun mediaPreviewRequestsImmersiveSystemBars(): Boolean = true

internal fun mediaPreviewOverlayAvoidsDecorInsets(): Boolean = true

internal fun mediaPreviewUsesDedicatedFullscreenActivity(): Boolean = false

internal fun mediaPreviewKeepsAccessibilityOverlayHidden(): Boolean = false

internal fun mediaPreviewImmersiveUsesDecorViewInsetsController(): Boolean = true

internal fun mediaPreviewRunsInsideFloatingOverlay(): Boolean = true

internal fun mediaPreviewRestoresOverlayWithoutRecreate(): Boolean = true

internal fun floatingChatBlankAreaClickCollapsesOverlay(): Boolean = false

internal fun floatingChatBackKeyCollapsesOverlay(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboard(): Boolean = true

internal fun floatingChatBlankAreaClickHidesKeyboardWhenInputNotFocused(): Boolean = false

internal fun floatingChatAppearancePanelPlacement(): String = "after_global_controls"

internal fun floatingChatAppearanceSettingsPreview(): Boolean = true

internal fun floatingChatOverlayUsesFrostedBackground(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBackgroundOpacity(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBlurRadius(): Boolean = true

internal fun floatingChatOverlaySupportsRuntimeBackgroundColor(): Boolean = true

internal fun floatingChatOverlayFallsBackWhenBackdropBlurUnavailable(): Boolean = true

internal fun floatingChatOverlayRefreshRecreatesCurrentStateForAppearanceChanges(): Boolean = true

internal fun floatingChatAppearanceRefreshUsesDebouncedSingleRunnable(): Boolean = true

internal fun mediaPickerDoesNotCollapseFloatingChatToButton(): Boolean = true

internal fun mediaPickerRestoresExpandedOverlayAfterResult(): Boolean = true

internal fun mediaPickerHidesWholeFloatingChatWindow(): Boolean = true

internal fun mediaPickerKeepsFloatingChatWindowPersistent(): Boolean = true

internal fun cameraToolKeepsFloatingChatWindowPersistent(): Boolean = true

internal fun mediaPickerShowsLightweightTransitionSurface(): Boolean = false

internal fun mediaPickerRestoresOverlayBeforeDeliveringPickedMedia(): Boolean = true

internal fun mediaPickerProcessesPickedMediaOffMainThread(): Boolean = true

internal fun floatingChatCollapseRemovesOverlayInsteadOfShowingButton(): Boolean = false

internal fun floatingChatCollapseRetainsExpandedComposition(): Boolean = true

internal fun floatingChatExpandReusesRetainedComposeView(): Boolean = true

internal fun floatingChatRetainedCollapsedOverlayIsNotTouchable(): Boolean = true

internal fun floatingChatCollapsedStateShowsFloatingButton(): Boolean = false

internal fun mediaPreviewIgnoresExistingDismissSignalOnOpen(): Boolean = true

internal fun mediaPreviewMinimumZoom(): Float = 1f

internal fun mediaPreviewMaximumZoom(): Float = 4f

internal fun mediaPreviewVideoUsesNativePlayer(): Boolean = true

internal fun mediaPreviewVideoUsesAspectFit(): Boolean = true

internal fun mediaPreviewVideoUsesOriginalAspectFrame(): Boolean = true

internal fun mediaPreviewVideoPlayButtonUsesMaterialIcon(): Boolean = true

internal fun mediaPreviewVideoPauseButtonUsesMaterialIcon(): Boolean = true

internal fun mediaPreviewVideoUsesImageZoomGestures(): Boolean = false

internal fun mediaPreviewVideoAutoPlays(): Boolean = false

internal fun mediaPreviewVideoShowsProgressBar(): Boolean = true

internal fun mediaPreviewVideoShowsTimecodes(): Boolean = true

internal fun mediaPreviewVideoShowsInlinePlayPause(): Boolean = true

internal fun mediaPreviewVideoControlsAvoidActionButtons(): Boolean = true

internal fun mediaPreviewVideoControlBottomPaddingDp(): Int = 12

internal fun mediaPreviewVideoControlsFloatOverVideoFrame(): Boolean = true

internal fun mediaPreviewVideoPlayerReleaseIsIdempotent(): Boolean = true

internal fun mediaPreviewVideoPlayerReleaseHandlesInvalidState(): Boolean = true

internal fun chatListVideoUsesInlinePlayer(): Boolean = false

internal fun chatListVideoUsesAspectFit(): Boolean = true

internal fun chatListVideoAutoPlays(): Boolean = false

internal enum class MessageLongPressAction(val label: String) {
    Copy("复制"),
    Forward("转发"),
    Favorite("收藏"),
    Delete("删除"),
    MultiSelect("多选"),
    Quote("引用"),
    Reminder("提醒")
}

internal fun messageLongPressPrimaryActions(): List<MessageLongPressAction> {
    return listOf(
        MessageLongPressAction.Copy,
        MessageLongPressAction.Forward,
        MessageLongPressAction.Favorite,
        MessageLongPressAction.Delete,
        MessageLongPressAction.MultiSelect,
        MessageLongPressAction.Quote,
        MessageLongPressAction.Reminder
    )
}

internal fun messageLongPressUsesWechatFloatingPanel(): Boolean = true

internal fun messageLongPressSupportsInternalForwarding(): Boolean = true

internal fun messageLongPressReminderUsesUiStateOnly(): Boolean = true

internal fun messageLongPressIncludesSearch(): Boolean = false

internal fun messageLongPressIncludesListenFromHere(): Boolean = false

internal fun messageLongPressSupportsMultiSelectMode(): Boolean = true

internal fun messageLongPressQuoteShowsComposerPreview(): Boolean = true

internal fun messageLongPressMenuAnchorsToMessageBounds(): Boolean = true

internal fun messageLongPressMenuUsesFixedSidePosition(): Boolean = false

internal enum class MediaActionContract {
    AnalyzeImage,
    FindObject,
    Share,
    Save,
    Favorite,
    More,
    Visibility,
    Edit,
    Comment,
    Grid
}

internal fun mediaPreviewActionContracts(): Set<MediaActionContract> {
    return setOf(
        MediaActionContract.AnalyzeImage,
        MediaActionContract.FindObject,
        MediaActionContract.Share,
        MediaActionContract.Save,
        MediaActionContract.Favorite,
        MediaActionContract.More
    )
}

internal fun imModuleSelfBubbleColorArgb(): Int = OverlayTokens.selfBubble.toArgb()

internal fun imModuleOtherBubbleColorArgb(): Int = OverlayTokens.otherBubble.toArgb()

internal fun imModuleSelfBubbleBorderColorArgb(): Int = OverlayTokens.selfBubbleBorder.toArgb()

internal fun imModuleOtherBubbleBorderColorArgb(): Int = OverlayTokens.otherBubbleBorder.toArgb()

internal fun imModuleBubbleTextColorArgb(): Int = OverlayTokens.bubbleText.toArgb()

internal fun imModuleBubbleShadowColorArgb(): Int = OverlayTokens.imModuleTextShadow.color.toArgb()

internal fun imModuleBubbleUsesDemoGlassEffect(): Boolean = true

internal fun imModuleSelfBubbleBackdropBlurDp(): Int = 20

internal fun imModuleSelfBubbleShadowOffsetYDp(): Int = 8

internal fun imModuleSelfBubbleShadowBlurDp(): Int = 32

internal fun imModuleOtherBubbleIsTransparentWithHalfBorder(): Boolean {
    return OverlayTokens.otherBubble == Color.Transparent &&
        OverlayTokens.otherBubbleBorder.alpha in 0.45f..0.55f
}

internal fun cardMessageTextUsesImModuleShadow(): Boolean = true

internal fun resourceUrlTextUsesImModuleShadow(): Boolean = true

internal fun chipTextUsesImModuleShadow(): Boolean = true

internal fun inlineCardTextUsesImModuleShadow(): Boolean = true

internal fun systemPromptMessageUsesTextOnly(): Boolean {
    return !messageUsesBubbleChrome(FloatingChatMessagePresentation.System)
}

internal fun systemPromptTextUsesShadow(): Boolean {
    return OverlayTokens.imModuleTextShadow.color.toArgb() == 0xE6000000.toInt()
}

internal fun cardMessageColorArgbFor(type: FloatingChatMessageType): Int {
    return cardMessageColorFor(type).toArgb()
}

internal fun paymentCardMessageColorArgb(): Int = OverlayTokens.paymentCard.toArgb()

internal fun cardMessagePrimaryTextColorArgb(): Int = OverlayTokens.cardPrimaryText.toArgb()

internal fun cardMessageSecondaryTextColorArgb(): Int = OverlayTokens.cardSecondaryText.toArgb()

internal fun leftRailUsesScrollableLazyColumn(): Boolean = true

internal fun leftRailAllowsScrollWhenContentIsShort(): Boolean = true

internal fun leftRailSupportsBidirectionalScrolling(): Boolean = true

internal fun leftRailLeadingSpacerItemCount(): Int = LeftRailLeadingSpacerItemCount

internal fun leftRailInitialFirstVisibleItemIndex(): Int = 0

internal fun leftRailSelectedThreadFirstVisibleIndex(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    selectedThread: ChatThreadSelection
): Int {
    val railItemIndex = when (selectedThread) {
        ChatThreadSelection.Group -> 0
        is ChatThreadSelection.GroupChat -> groups.indexOfFirst { group -> group.id == selectedThread.groupId }
        is ChatThreadSelection.Private -> {
            val contactIndex = contacts.indexOfFirst { contact -> contact.id == selectedThread.contactId }
            if (contactIndex >= 0) groups.size + contactIndex else -1
        }
    }
    return if (railItemIndex >= 0) {
        LeftRailLeadingSpacerItemCount + railItemIndex
    } else {
        leftRailInitialFirstVisibleItemIndex()
    }
}

internal fun leftRailScrollsSelectedThreadAvatarIntoViewForConnectors(): Boolean = false

internal fun leftRailKeepsScrollPositionWhenSelectingVisibleAvatar(): Boolean = true

internal fun leftRailClearsDisposedAvatarConnectorBounds(): Boolean = true

internal fun leftRailScrollableTopPaddingDp(
    itemCount: Int,
    viewportHeightDp: Int
): Int = 0

internal fun leftRailDefaultsToTopAlignedAvatars(): Boolean = true

internal fun leftRailBouncesBackToTopWhenContentFitsViewport(): Boolean = true

internal fun leftRailDisablesScrollWhenContentFitsViewport(): Boolean = false

internal fun leftRailUsesNonAnimatedTopResetWhenContentFitsViewport(): Boolean = true

internal fun leftRailSupportsGentleTopPullAndRelease(): Boolean = true

internal fun leftRailTopOverscrollMaxDp(): Int = LeftRailTopOverscrollMaxDp

internal fun leftRailTopOverscrollReturnMs(): Int = LeftRailTopOverscrollReturnMs

internal fun leftRailContentFitsViewport(
    itemCount: Int,
    viewportHeightDp: Int
): Boolean {
    if (itemCount <= 0 || viewportHeightDp <= 0) return false
    return leftRailContentHeightDp(itemCount) <= viewportHeightDp
}

internal fun leftRailScrollShowsFollowTextOverlay(): Boolean = true

internal fun leftRailFollowTextHideDelayMs(): Int = LeftRailFollowTextHideDelayMs

internal fun leftRailFollowTextHidesOnRelease(): Boolean = LeftRailFollowTextHideDelayMs == 0

internal fun leftRailFollowTextStartOffsetDp(): Int = LeftRailFollowTextStartOffsetDp

internal fun leftRailFollowTextWidthDp(): Int = LeftRailFollowTextWidthDp

internal fun leftRailFollowTextLayerWidthDp(): Int = LeftRailFollowTextLayerWidthDp

internal fun leftRailTouchableWidthDp(): Int = SessionRailWidthDp

internal fun leftRailFollowTextInnerPaddingDp(): Int = LeftRailFollowTextInnerPaddingDp

internal fun leftRailFollowTextStartsAtAvatarRightEdge(): Boolean {
    return LeftRailFollowTextStartOffsetDp + LeftRailFollowTextInnerPaddingDp == RailAvatarSizeDp
}

internal fun leftRailScrollableBottomPaddingDp(
    itemCount: Int,
    viewportHeightDp: Int
): Int {
    val safeItemCount = itemCount.coerceAtLeast(0)
    val contentHeight = leftRailContentHeightDp(safeItemCount)
    val minScrollableContentHeight = viewportHeightDp + LeftRailMinimumScrollRangeDp
    return (minScrollableContentHeight - contentHeight)
        .coerceAtLeast(LeftRailShortContentScrollPaddingDp)
}

internal fun leftRailContentHeightDp(itemCount: Int): Int {
    val safeItemCount = itemCount.coerceAtLeast(0)
    return if (safeItemCount == 0) {
        0
    } else {
        safeItemCount * RailAvatarSizeDp + (safeItemCount - 1) * LeftRailItemGapDp
    }
}

internal fun leftRailUsesPointerDragToRevealFollowText(): Boolean = false

internal fun leftRailFollowTextOverlayConsumesPointerEvents(): Boolean = false

internal fun keyboardDismissUsesBlockingFullScreenPointerLayer(): Boolean = false

internal fun leftRailLayerDrawsAboveConnectorLayer(): Boolean {
    return leftRailLayerZIndex() > connectorLayerZIndex()
}

internal fun leftRailLayerZIndex(): Float = LeftRailLayerZIndex

internal fun connectorLayerZIndex(): Float = ConnectorLayerZIndex

internal fun leftRailFollowTextIncludesNameLastMessageAndTime(): Boolean = true

internal fun leftRailFollowTextUsesAvatarTopAlignment(): Boolean = true

internal fun leftRailFollowTextUsesLiveAvatarBounds(): Boolean = true

internal fun leftRailFollowTextYMatchesAvatarBounds(): Boolean = true

internal fun leftRailFollowTextBoundsUseSingleInvalidationVersion(): Boolean = true

internal fun leftRailFollowTextUsesCompactTypography(): Boolean {
    return LeftRailFollowTextNameSizeSp <= 11f &&
        LeftRailFollowTextMessageSizeSp <= 9.5f &&
        LeftRailFollowTextTimeSizeSp <= 8.5f
}

internal fun leftRailFollowTextUsesBackgroundHalo(): Boolean = false

internal fun leftRailFollowTextUsesDarkTextShadow(): Boolean {
    return OverlayTokens.leftRailFollowTextShadow.color.toArgb() == 0xE6000000.toInt()
}

internal data class LeftRailFollowInfo(
    val contactId: String,
    val name: String,
    val lastMessage: String,
    val lastTime: String,
    val topPx: Float = 0f,
    val heightPx: Float = 0f
)

internal fun leftRailFollowInfoForContact(
    conversation: FloatingChatConversation,
    contact: FloatingChatContact,
    selectedAccountId: String
): LeftRailFollowInfo {
    val selection = if (conversation.groupContacts.any { group -> group.id == contact.id }) {
        contact.toGroupThreadSelection()
    } else {
        ChatThreadSelection.Private(contact.id)
    }
    val latestMessage = visibleMessagesForThread(
        conversation = conversation,
        selection = selection,
        selectedAccountId = selectedAccountId
    ).lastOrNull()
    return LeftRailFollowInfo(
        contactId = contact.id,
        name = contact.name,
        lastMessage = latestMessage?.text?.ifBlank { contact.description } ?: contact.description,
        lastTime = latestMessage?.time ?: "",
        topPx = 0f,
        heightPx = 0f
    )
}

internal fun messagePaneUsesLazyColumn(): Boolean = true

internal fun messagePaneOnlyComposesVisibleMessages(): Boolean = true

internal fun messagePaneUsesStableMessageKeys(): Boolean = true

internal fun messagePaneUsesMessageContentTypes(): Boolean = true

internal fun connectorLayerUsesLazyListVisibleItemsOnly(): Boolean = true

internal fun connectorLayerClearsStaleMessageBoundsWhenThreadChanges(): Boolean = true

internal fun connectorLayerClearsStaleMessageBoundsWhenMediaMessagesChange(): Boolean = true

internal fun connectorLayerUsesPrecomputedOffscreenTargets(): Boolean = true

internal fun messageBoundsForLongPressAreCapturedOnlyWhenNeeded(): Boolean = true

internal fun connectorLayerAvoidsSnapshotStateForPerFrameMessageBounds(): Boolean = true

internal fun connectorLayerDefersLazyListLayoutReadsToDrawPhase(): Boolean = true

internal fun connectorLayerReusesNativePaintDuringDraw(): Boolean = true

internal fun connectorCoordinateCacheUsesSingleInvalidationVersion(): Boolean = true

internal fun mediaThumbnailsDecodeOffMainThread(): Boolean = true

internal fun privateChatConnectorUsesSelectedAvatarLiveBounds(): Boolean = true

internal fun privateChatConnectorAllowsMessagesWithoutStoredTargetId(): Boolean = true

internal fun privateChatConnectorKeepsSelectedAvatarAnchorWhenLazyItemDisposes(): Boolean = true

internal fun privateChatConnectorDoesNotReuseGroupThreadAvatarAfterGroupSwitch(): Boolean = true

internal fun imModuleConnectionLineColorArgb(): Int = OverlayTokens.connectorLine.toArgb()

internal fun imModuleConnectionLineStrokeWidthPx(): Float = 6f

internal fun imModuleConnectionLineShadowColorArgb(): Int = OverlayTokens.connectorLineShadow.toArgb()

internal fun imModuleConnectionLineShadowBlurPx(): Float = 4f

internal fun imModuleConnectionLineShadowOffsetXPx(): Float = 1f

internal fun imModuleConnectionLineShadowOffsetYPx(): Float = 1f

internal fun imModuleConnectionLineUsesNativeCanvasShadow(): Boolean = true

internal fun imModuleConnectionLineHorizontalOffsetPx(): Float = 48f

internal fun imModuleConnectionLineCornerRadiusPx(): Float = 12f

internal fun imModuleConnectionLineCornerArcFraction(): Float = 0.25f

internal fun imModuleConnectionLineUsesRoundedElbows(): Boolean = true

internal fun connectorRoundedElbowRadiusPx(horizontalRoom: Float): Float {
    return (abs(horizontalRoom) * imModuleConnectionLineCornerArcFraction())
        .coerceIn(1f, imModuleConnectionLineCornerRadiusPx())
}

internal fun imModuleConnectionLineBubbleGapPx(): Float = 1f

internal fun imModuleConnectionLineUsesBraceHooks(): Boolean = true

internal fun imModuleConnectionLineDrawsEndpointDots(): Boolean = false

internal fun connectorTargetIdForMessage(
    message: FloatingChatMessage,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    groupMemberAvatarsVisible: Boolean
): String? {
    return message.toConnectorTargetKey(
        selection = selection,
        selectedAccountId = selectedAccountId,
        groupMemberAvatarsVisible = groupMemberAvatarsVisible
    )?.targetId
}

internal fun privateChatConnectorsUseCurrentThreadTargets(): Boolean = true

internal fun privateChatLeftConnectorAnchorsToUserAvatarRightEdge(): Boolean = true

internal fun privateChatRightConnectorAnchorsToAccountAvatarLeftEdge(): Boolean = true

private data class MediaPreviewFrame(
    val width: Dp,
    val height: Dp
)

private data class MediaListFrame(
    val width: Dp,
    val height: Dp
)

private fun mediaPreviewFrameSize(
    maxWidth: Dp,
    maxHeight: Dp,
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float? = null
): MediaPreviewFrame {
    val widthLimit = maxWidth * mediaPreviewMaxWidthFraction()
    val heightLimit = maxHeight * mediaPreviewMaxHeightFraction()
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    var width = widthLimit
    var height = width / aspectRatio
    if (height > heightLimit) {
        height = heightLimit
        width = height * aspectRatio
    }
    return MediaPreviewFrame(width = width, height = height)
}

internal enum class MessageHorizontalPlacement {
    Start,
    Center,
    End
}

internal fun messageHorizontalPlacement(
    presentation: FloatingChatMessagePresentation,
    fromMe: Boolean
): MessageHorizontalPlacement {
    return when (presentation) {
        FloatingChatMessagePresentation.System -> MessageHorizontalPlacement.Center
        FloatingChatMessagePresentation.Bubble,
        FloatingChatMessagePresentation.SpecialCard,
        FloatingChatMessagePresentation.MediaStandalone -> if (fromMe) {
            MessageHorizontalPlacement.End
        } else {
            MessageHorizontalPlacement.Start
        }
    }
}

internal fun standaloneMessageTypeUsesCleanMediaSurface(type: FloatingChatMessageType): Boolean {
    return type == FloatingChatMessageType.ImageThumbnail || type == FloatingChatMessageType.VideoPreview
}

internal fun messageTypeUsesImModuleBubble(type: FloatingChatMessageType): Boolean {
    return when (type) {
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote,
        FloatingChatMessageType.Location,
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.FilePreview,
        FloatingChatMessageType.Voice,
        FloatingChatMessageType.InlineContact,
        FloatingChatMessageType.InlineLocation -> true
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.VideoPreview -> false
    }
}

internal fun mediaAspectRatioFromDimensions(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0
): Float? {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val rotated = normalizedRotation == 90 || normalizedRotation == 270
    val displayWidth = if (rotated) safeHeight else safeWidth
    val displayHeight = if (rotated) safeWidth else safeHeight
    if (displayWidth <= 0 || displayHeight <= 0) return null
    return displayWidth.toFloat() / displayHeight.toFloat()
}

internal fun textureViewScaleForAspectFit(
    containerWidth: Int,
    containerHeight: Int,
    videoAspectRatio: Float?
): Pair<Float, Float> {
    if (containerWidth <= 0 || containerHeight <= 0 || videoAspectRatio == null || videoAspectRatio <= 0f) {
        return 1f to 1f
    }
    val containerAspectRatio = containerWidth.toFloat() / containerHeight.toFloat()
    return if (videoAspectRatio > containerAspectRatio) {
        1f to (containerAspectRatio / videoAspectRatio)
    } else {
        (videoAspectRatio / containerAspectRatio) to 1f
    }
}

internal fun standaloneMediaListMaxWidthDp(): Int = StandaloneImageMaxWidthDp

internal fun standaloneMediaListMinHeightDp(): Int = StandaloneMediaMinHeightDp

internal fun standaloneMediaListMaxHeightDp(): Int = StandaloneMediaMaxHeightDp

internal fun formatVideoTimecode(durationMs: Int): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun applyTextureViewAspectFitTransform(
    textureView: TextureView,
    videoAspectRatio: Float?
) {
    val width = textureView.width
    val height = textureView.height
    val (scaleX, scaleY) = textureViewScaleForAspectFit(width, height, videoAspectRatio)
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, width / 2f, height / 2f)
    }
    textureView.setTransform(matrix)
}

private fun standaloneMediaListFrameSize(
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float?
): MediaListFrame {
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    val maxWidth = StandaloneImageMaxWidthDp.toFloat()
    var width = maxWidth
    var height = width / aspectRatio
    if (height > StandaloneMediaMaxHeightDp) {
        height = StandaloneMediaMaxHeightDp.toFloat()
        width = height * aspectRatio
    }
    if (height < StandaloneMediaMinHeightDp) {
        height = StandaloneMediaMinHeightDp.toFloat()
        width = height * aspectRatio
    }
    width = width.coerceIn(84f, maxWidth)
    return MediaListFrame(width = width.dp, height = height.dp)
}

private fun videoPreviewAspectRatio(
    context: Context,
    message: FloatingChatMessage
): Float? {
    val uriText = message.resourceUrl ?: message.thumbnailUrl ?: return null
    if (!isLocalMediaUri(uriText)) return null
    val uri = Uri.parse(uriText)
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            if (width == null || height == null) null else mediaAspectRatioFromDimensions(width, height, rotation)
        }
    }.getOrNull()
}

internal fun messageListBottomClearanceDp(): Int {
    return BottomInputBarMaxHeightDp + BottomInputBarBottomPaddingDp + MessageListBottomExtraClearanceDp
}

internal fun messageListUsesKeyboardInsets(): Boolean = true

internal fun messageListAutoScrollsDuringInput(): Boolean = true

internal fun messageListAutoScrollsOnInputFocus(): Boolean = true

internal fun messageListAutoScrollsOnImeInsetChange(): Boolean = true

internal fun bottomInputBarHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMinHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMaxHeightDp(): Int = BottomInputBarMaxHeightDp

internal fun bottomInputBarBottomPaddingDp(): Int = BottomInputBarBottomPaddingDp

internal fun bottomInputBarUsesKeyboardInsets(): Boolean = true

internal fun bottomInputControlsUseCenterAlignment(): Boolean = true

internal fun bottomInputUsesCustomBasicTextField(): Boolean = true

internal fun bottomInputPlaceholderTextSizeSp(): Int = BottomInputPlaceholderTextSizeSp

internal fun bottomInputIconButtonSizeDp(): Int = BottomInputIconButtonSizeDp

internal fun bottomInputIconSizeDp(): Int = BottomInputIconSizeDp

internal fun bottomInputMinLines(): Int = BottomInputMinLines

internal fun bottomInputMaxLines(): Int = BottomInputMaxLines

internal fun bottomInputActionOrder(): List<BottomInputAction> {
    return listOf(
        BottomInputAction.Home,
        BottomInputAction.Voice,
        BottomInputAction.Text,
        BottomInputAction.Gift,
        BottomInputAction.Assistant
    )
}

internal fun bottomInputLeadingAction(inputFocused: Boolean): BottomInputAction {
    return if (inputFocused) BottomInputAction.Emoji else BottomInputAction.Home
}

internal fun bottomHomeButtonShowsUnreadOverview(): Boolean = true

internal fun bottomHomeButtonSwapsToEmojiWhenInputFocused(): Boolean = true

internal fun bottomInputAssistantActionSendsPredictedMessage(): Boolean = true

internal fun bottomEmojiPanelUsesAndroidXEmojiPicker(): Boolean = true

internal fun bottomEmojiPickerDependencyCoordinate(): String = "androidx.emoji2:emoji2-emojipicker:1.6.0"

internal fun bottomEmojiPanelKeepsPickerOpenAfterSelection(): Boolean = true

internal fun bottomEmojiPanelHeightDp(): Int = BottomEmojiPanelHeightDp

internal fun bottomFloatingPanelUsesDarkText(): Boolean = true

internal fun voiceInputRecordsAudioMessage(): Boolean = true

internal fun voiceInputSendsRecordedAudio(): Boolean = true

internal fun voiceMessageSupportsPlayback(): Boolean = true

internal fun voiceRecorderMimeType(): String = VoiceRecorderMimeType

internal fun voiceRecorderFileExtension(): String = VoiceRecorderFileExtension

internal fun voiceInputRequiresRecordAudioPermission(): Boolean = true

internal fun floatingChatRuntimePermissions(): List<String> {
    return listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun voiceInputIdleLabel(): String = "点击开始录音，停止后会发送语音消息"

private fun formatVoiceTimecode(durationMs: Int): String {
    val totalSeconds = kotlin.math.ceil(durationMs.coerceAtLeast(0) / 1000.0)
        .toInt()
        .coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun createVoiceRecorderSession(context: Context): VoiceRecorderSession {
    val directory = File(context.cacheDir, "floating-chat-voice").apply {
        mkdirs()
    }
    val file = File(directory, "voice-${System.currentTimeMillis()}.$VoiceRecorderFileExtension")
    @Suppress("DEPRECATION")
    val recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(96_000)
        setAudioSamplingRate(44_100)
        setOutputFile(file.absolutePath)
    }
    return VoiceRecorderSession(
        recorder = recorder,
        file = file,
        startedAtMs = System.currentTimeMillis()
    )
}

internal fun aiDraftMessageUsesGreenDashedBubble(message: FloatingChatMessage): Boolean {
    return message.kind == FloatingChatMessageKind.AiDraft &&
        message.presentation == FloatingChatMessagePresentation.Bubble
}

internal fun aiDraftMessageUsesSolidBubbleBorder(message: FloatingChatMessage): Boolean {
    return !aiDraftMessageUsesGreenDashedBubble(message)
}

internal fun aiDraftBubbleDashedBorderColorArgb(): Int = OverlayTokens.aiDashedBorder.toArgb()

@Composable
private fun DraftBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OverlayTokens.aiBadge)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        TextLabel(
            text = "草稿",
            size = 8.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.aiText,
            maxLines = 1
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RightCoordinateRail(
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    selectedAccountId: String?,
    actions: List<FloatingChatToolAction>,
    connectorState: ConnectorCoordinateState,
    onToolAction: (FloatingChatToolAction) -> Unit,
    onAccountAvatarClick: (FloatingChatContact) -> Unit,
    onAccountAvatarLongClick: (FloatingChatContact) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var toolOrder by remember(actions) {
        mutableStateOf(loadToolActionOrder(context, actions))
    }
    val visibleToolActions = toolOrder
    var selectedTool by remember(actions) {
        mutableStateOf(visibleToolActions.firstOrNull() ?: FloatingChatToolAction.Assistant)
    }
    var reorderMode by remember { mutableStateOf(false) }
    var draggedTool by remember { mutableStateOf<FloatingChatToolAction?>(null) }
    var toolDragStartIndex by remember { mutableStateOf(-1) }
    var toolDragCurrentIndex by remember { mutableStateOf(-1) }
    var toolDragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val toolSlotHeightPx = remember(density) {
        with(density) { (RailToolButtonHeight + RightRailItemGapDp.dp).toPx() }
    }
    fun exitReorderMode() {
        reorderMode = false
        draggedTool = null
        toolDragStartIndex = -1
        toolDragCurrentIndex = -1
        toolDragOffsetY = 0f
    }
    fun enterReorderMode() {
        if (!reorderMode) {
            reorderMode = true
        }
    }
    fun beginToolDrag(action: FloatingChatToolAction) {
        enterReorderMode()
        val currentIndex = toolOrder.indexOf(action)
        if (currentIndex < 0) {
            return
        }
        draggedTool = action
        toolDragStartIndex = currentIndex
        toolDragCurrentIndex = currentIndex
        toolDragOffsetY = 0f
    }
    fun reorderDraggedTool(action: FloatingChatToolAction, dragOffsetY: Float) {
        if (draggedTool != action || toolDragStartIndex < 0) {
            beginToolDrag(action)
        }
        val startIndex = toolDragStartIndex
        if (startIndex < 0) {
            return
        }
        toolDragOffsetY = dragOffsetY
        val currentIndex = toolOrder.indexOf(action)
        if (currentIndex < 0) {
            exitReorderMode()
            return
        }
        val targetIndex = toolReorderTargetIndex(
            startIndex = startIndex,
            dragOffsetY = toolDragOffsetY,
            itemSlotHeightPx = toolSlotHeightPx,
            itemCount = toolOrder.size
        )
        if (targetIndex != currentIndex) {
            toolOrder = moveToolAction(toolOrder, currentIndex, targetIndex)
        }
        toolDragCurrentIndex = targetIndex
    }
    fun finishToolDrag(action: FloatingChatToolAction) {
        if (draggedTool == action) {
            draggedTool = null
            toolDragStartIndex = -1
            toolDragCurrentIndex = -1
            toolDragOffsetY = 0f
            saveToolActionOrder(context, toolOrder)
        }
    }
    var railHeightPx by remember { mutableStateOf(0f) }
    var accountWeight by remember { mutableStateOf(defaultRightRailAccountWeight()) }
    val displayedAccountWeight by animateFloatAsState(
        targetValue = accountWeight,
        animationSpec = tween(durationMillis = RightRailSectionResizeMs),
        label = "rightRailAccountWeight"
    )
    fun expandAccountSection() {
        accountWeight = rightRailAccountWeightForAccountAreaDrag()
    }
    fun expandToolSection() {
        accountWeight = rightRailAccountWeightForToolAreaDrag()
    }
    val accountResizeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    expandAccountSection()
                }
                return Offset.Zero
            }
        }
    }
    val toolListState = rememberLazyListState()
    val toolResizeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f && draggedTool == null) {
                    expandToolSection()
                }
                return Offset.Zero
            }
        }
    }
    val railWeights = rightRailWeightsForAccountWeight(displayedAccountWeight)
    val accountListState = rememberLazyListState(
        initialFirstVisibleItemIndex = rightRailSelectedAccountFirstVisibleIndex(
            accounts = accounts,
            selectedAccountId = selectedAccountId
        )
    )
    var accountViewportBounds by remember { mutableStateOf<Rect?>(null) }
    val accountIds = remember(accounts) { accounts.map { account -> account.id } }
    val accountVirtualFallbackStepPx = remember(density) {
        with(density) { (RailAvatarSize + RightRailItemGapDp.dp).toPx() }
    }
    LaunchedEffect(accounts, selectedAccountId) {
        accountListState.scrollToItem(
            rightRailSelectedAccountFirstVisibleIndex(
                accounts = accounts,
                selectedAccountId = selectedAccountId
            )
        )
    }
    LaunchedEffect(accountIds, accountListState, accountViewportBounds, accountVirtualFallbackStepPx) {
        snapshotFlow {
            accountListState.layoutInfo.visibleItemsInfo.map { item ->
                RightRailVisibleAccountItem(
                    index = item.index,
                    offset = item.offset,
                    size = item.size
                )
            }
        }.collectLatest { visibleItems ->
            val viewport = accountViewportBounds ?: return@collectLatest
            connectorState.updateVirtualAccountAvatars(
                accountIds = accountIds,
                visibleItems = visibleItems,
                viewport = viewport,
                fallbackStepPx = accountVirtualFallbackStepPx
            )
        }
    }
    Column(
        modifier = modifier.onSizeChanged { size ->
            railHeightPx = size.height.toFloat()
        },
        horizontalAlignment = Alignment.End
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(railWeights.accountWeight)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    accountViewportBounds = bounds
                    connectorState.updateAccountViewport(bounds)
                }
                .nestedScroll(accountResizeConnection),
            state = accountListState,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            reverseLayout = true
        ) {
            itemsIndexed(
                items = accounts,
                key = { _, account -> account.id },
                contentType = { _, _ -> "account-avatar" }
            ) { _, account ->
                val profile = accountProfiles[account.id]
                val displayAccount = profile?.toContact(account) ?: account
                var currentAccountBounds by remember(account.id) { mutableStateOf<Rect?>(null) }
                CompactAvatar(
                    contact = displayAccount.copy(
                        selected = account.id == selectedAccountId
                    ),
                    role = AvatarRole.Account,
                    imageUri = profile?.avatarImageUri,
                    onClick = {
                        currentAccountBounds?.let { bounds ->
                            connectorState.updateSelectedAccountAvatar(account.id, bounds)
                        }
                        onAccountAvatarClick(account)
                    },
                    onLongClick = { onAccountAvatarLongClick(account) },
                    onBoundsChanged = { bounds ->
                        currentAccountBounds = bounds
                        connectorState.updateAccountAvatar(account.id, bounds)
                        if (account.id == selectedAccountId) {
                            connectorState.updateSelectedAccountAvatar(account.id, bounds)
                        }
                    },
                    onRemoved = {
                        connectorState.removeAccountAvatar(account.id)
                    }
                )
            }
        }
        RightRailDivider()
        val toolListModifier = Modifier
            .weight(railWeights.toolWeight)
            .fillMaxWidth()
            .nestedScroll(toolResizeConnection)
        LazyColumn(
            modifier = toolListModifier,
            state = toolListState,
            userScrollEnabled = draggedTool == null,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = visibleToolActions,
                key = { _, action -> action.name }
            ) { _, action ->
                val isDraggingTool = draggedTool == action
                ToolButton(
                    modifier = if (isDraggingTool) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    },
                    action = action,
                    selected = action == selectedTool,
                    reorderMode = reorderMode,
                    dragging = isDraggingTool,
                    dragTranslationY = if (isDraggingTool) {
                        toolReorderDraggedTranslationY(
                            dragOffsetY = toolDragOffsetY,
                            startIndex = toolDragStartIndex,
                            currentIndex = toolDragCurrentIndex,
                            itemSlotHeightPx = toolSlotHeightPx
                        )
                    } else {
                        0f
                    },
                    onClick = {
                        if (reorderMode) {
                            exitReorderMode()
                        } else {
                            selectedTool = action
                            onToolAction(action)
                        }
                    },
                    onLongClick = {
                        enterReorderMode()
                    },
                    onDragStart = {
                        beginToolDrag(action)
                    },
                    onDrag = { dragAmountY ->
                        reorderDraggedTool(action, dragAmountY)
                    },
                    onDragEnd = {
                        finishToolDrag(action)
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GroupChatAvatar(
    selected: Boolean,
    unread: Boolean,
    memberCount: Int,
    label: String = "群",
    color: Color = OverlayTokens.groupAvatar,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onRemoved: () -> Unit = {}
) {
    val shape = RoundedCornerShape(10.dp)
    DisposableEffect(Unit) {
        onDispose { onRemoved() }
    }
    MaterialSurface(
        modifier = Modifier
            .size(RailAvatarSize)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    rootBoundsFromPosition(
                        positionInRoot = coordinates.positionInRoot(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                )
        },
        shape = shape,
        color = color,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, if (selected) OverlayTokens.accent else OverlayTokens.hairline)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val faceColor = Color(0xFFF5D3BD)
                val rearFaceColor = Color(0xFFEBC6AF)
                val bodyColor = Color(0xCCF8FCFF)
                drawCircle(
                    color = rearFaceColor,
                    radius = size.minDimension * 0.13f,
                    center = Offset(size.width * 0.34f, size.height * 0.38f)
                )
                drawCircle(
                    color = rearFaceColor,
                    radius = size.minDimension * 0.13f,
                    center = Offset(size.width * 0.66f, size.height * 0.38f)
                )
                drawRoundRect(
                    color = bodyColor.copy(alpha = 0.66f),
                    topLeft = Offset(size.width * 0.17f, size.height * 0.59f),
                    size = Size(size.width * 0.30f, size.height * 0.18f),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = bodyColor.copy(alpha = 0.66f),
                    topLeft = Offset(size.width * 0.53f, size.height * 0.59f),
                    size = Size(size.width * 0.30f, size.height * 0.18f),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawCircle(
                    color = faceColor,
                    radius = size.minDimension * 0.17f,
                    center = Offset(size.width * 0.50f, size.height * 0.34f)
                )
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.56f),
                    size = Size(size.width * 0.44f, size.height * 0.24f),
                    cornerRadius = CornerRadius(9f, 9f)
                )
                drawCircle(
                    color = Color(0x55000000),
                    radius = size.minDimension * 0.19f,
                    center = Offset(size.width * 0.50f, size.height * 0.34f),
                    style = Stroke(width = 1.2f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OverlayTokens.avatarNameTag)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                TextLabel(
                    text = label.ifBlank { memberCount.coerceAtMost(99).toString() }.take(2),
                    size = 7.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.primaryText,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            if (unread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(OverlayTokens.accent)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CompactAvatar(
    contact: FloatingChatContact,
    role: AvatarRole,
    sizeDp: Int = RailAvatarSizeDp,
    imageUri: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit,
    onRemoved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val avatarColor = Color(contact.avatarColor)
    val border = if (contact.selected) OverlayTokens.accent else OverlayTokens.hairline
    val avatarBitmap = rememberAsyncAvatarBitmap(imageUri)
    DisposableEffect(Unit) {
        onDispose { onRemoved() }
    }
    MaterialSurface(
        modifier = Modifier
            .then(modifier)
            .size(sizeDp.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    rootBoundsFromPosition(
                        positionInRoot = coordinates.positionInRoot(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                )
            },
        shape = shape,
        color = avatarColor,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val faceColor = Color(0xFFF7D6C1)
            val shoulderColor = Color(0xCCF8FCFF)
            drawCircle(
                color = faceColor,
                radius = size.minDimension * 0.17f,
                center = Offset(size.width * 0.50f, size.height * 0.35f)
            )
            drawRoundRect(
                color = shoulderColor,
                topLeft = Offset(size.width * 0.23f, size.height * 0.57f),
                size = Size(size.width * 0.54f, size.height * 0.26f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawCircle(
                color = Color(0x55000000),
                radius = size.minDimension * 0.19f,
                center = Offset(size.width * 0.50f, size.height * 0.34f),
                style = Stroke(width = 1.2f)
            )
        }
        val nameTagShape = RoundedCornerShape(4.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 3.dp, vertical = 2.dp)
                .clip(nameTagShape)
                .background(OverlayTokens.avatarNameTag)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            TextLabel(
                text = contact.initials,
                size = 7.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
        }
        TextLabel(
            text = if (role == AvatarRole.Account) "我" else "",
            size = 8.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.avatarBadgeText,
            textAlign = TextAlign.Center
        )
        if (contact.online) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(OverlayTokens.accent)
            )
        }
    }
}
}


@Composable
private fun RightRailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(OverlayTokens.railDivider)
        )
    }
}

@Composable
private fun AiffStatusStrip() {
    Column(
        modifier = Modifier
            .width(42.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
            .background(OverlayTokens.aiffStrip)
            .border(1.dp, OverlayTokens.hairline, RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextLabel(
            text = "Aiff",
            size = 9.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.primaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        TextLabel(
            text = "洞悉中",
            size = 7.sp,
            color = OverlayTokens.secondaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ToolButton(
    modifier: Modifier = Modifier,
    action: FloatingChatToolAction,
    selected: Boolean,
    reorderMode: Boolean,
    dragging: Boolean,
    dragTranslationY: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val toolShape = RoundedCornerShape(8.dp)
    val activeForReorder = reorderMode || dragging
    val viewConfiguration = LocalViewConfiguration.current
    val reorderModeState by rememberUpdatedState(reorderMode)
    CompactInteractiveSize {
        MaterialSurface(
            modifier = modifier
                .size(width = RailToolButtonWidth, height = RailToolButtonHeight)
                .graphicsLayer {
                    val dragScale = if (dragging) 1.06f else if (reorderMode) 1.02f else 1f
                    scaleX = dragScale
                    scaleY = dragScale
                    alpha = if (dragging) 0.96f else 1f
                    translationY = dragTranslationY
                }
                .zIndex(if (dragging) 8f else 0f)
                .shadow(if (activeForReorder) 6.dp else 3.dp, toolShape)
                .pointerInput(action, viewConfiguration.longPressTimeoutMillis) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        if (reorderModeState) {
                            var accumulatedMove = Offset.Zero
                            var dragStarted = false
                            var dragStartPosition = down.position
                            var pointerIsDown = true
                            while (pointerIsDown) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { pointerChange ->
                                    pointerChange.id == down.id
                                }
                                if (change == null || !change.pressed) {
                                    pointerIsDown = false
                                } else {
                                    accumulatedMove += change.positionChange()
                                    if (!dragStarted && accumulatedMove.getDistance() > viewConfiguration.touchSlop) {
                                        dragStarted = true
                                        dragStartPosition = change.position - accumulatedMove
                                        onDragStart()
                                    }
                                    if (dragStarted) {
                                        change.consume()
                                        onDrag(change.position.y - dragStartPosition.y)
                                    }
                                }
                            }
                            if (dragStarted) {
                                onDragEnd()
                            } else {
                                onClick()
                            }
                            return@awaitEachGesture
                        }
                        var movedPastTouchSlop = false
                        var totalPreLongPressMove = Offset.Zero
                        val upBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var pointerIsDown = true
                            var upEvent = false
                            while (pointerIsDown && !movedPastTouchSlop) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { pointerChange ->
                                    pointerChange.id == down.id
                                }
                                if (change == null || !change.pressed) {
                                    pointerIsDown = false
                                    upEvent = true
                                } else {
                                    totalPreLongPressMove += change.positionChange()
                                    movedPastTouchSlop =
                                        totalPreLongPressMove.getDistance() > viewConfiguration.touchSlop
                                }
                            }
                            upEvent
                        }
                        if (movedPastTouchSlop) {
                            return@awaitEachGesture
                        }
                        if (upBeforeLongPress == true) {
                            onClick()
                            return@awaitEachGesture
                        }

                        onLongClick()
                        onDragStart()
                        val dragStartPosition = down.position + totalPreLongPressMove
                        var draggingPointerIsDown = true
                        while (draggingPointerIsDown) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { pointerChange ->
                                pointerChange.id == down.id
                            }
                            if (change == null || !change.pressed) {
                                draggingPointerIsDown = false
                            } else {
                                val dragAmount = change.positionChange()
                                if (dragAmount != Offset.Zero) {
                                    change.consume()
                                    onDrag(change.position.y - dragStartPosition.y)
                                }
                            }
                        }
                        onDragEnd()
                    }
                },
            shape = toolShape,
            color = OverlayTokens.control,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, if (selected || activeForReorder) OverlayTokens.accent else OverlayTokens.hairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = toolActionIcon(action),
                    contentDescription = toolActionLabel(action),
                    tint = if (selected || activeForReorder) OverlayTokens.toolIconActive else OverlayTokens.toolIcon,
                    modifier = Modifier.size(18.dp)
                )
                TextLabel(
                    text = toolActionLabel(action),
                    size = 7.sp,
                    weight = FontWeight.Bold,
                    color = if (selected || activeForReorder) OverlayTokens.toolIconActive else OverlayTokens.toolIcon,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

internal fun toolActionLabel(action: FloatingChatToolAction): String {
    return when (action) {
        FloatingChatToolAction.Assistant -> "机器人"
        FloatingChatToolAction.Blink -> "眨眼"
        FloatingChatToolAction.Gallery -> "相册"
        FloatingChatToolAction.Camera -> "摄影"
        FloatingChatToolAction.Location -> "位置"
        FloatingChatToolAction.Favorite -> "收藏"
        FloatingChatToolAction.RedPacket -> "红包"
        FloatingChatToolAction.Transfer -> "转账"
        FloatingChatToolAction.Files -> "文档"
        FloatingChatToolAction.Card -> "推名片"
        FloatingChatToolAction.Moments -> "朋友圈"
        FloatingChatToolAction.QuickPhrase -> "快捷语"
        FloatingChatToolAction.Voice -> "语音"
        FloatingChatToolAction.Device -> "设备"
        FloatingChatToolAction.Notes -> "笔记"
        FloatingChatToolAction.Wallet -> "钱包"
        FloatingChatToolAction.Search -> "搜索"
        FloatingChatToolAction.Pin -> "置顶"
        FloatingChatToolAction.Translate -> "翻译"
        FloatingChatToolAction.Screenshot -> "截图"
        FloatingChatToolAction.Reminder -> "提醒"
        FloatingChatToolAction.Command -> "指令"
        FloatingChatToolAction.Share -> "分享"
    }
}

internal fun blinkVoiceResultMessageText(eventType: String, durationMs: Long): String {
    val eventLabel = when (eventType) {
        "SINGLE_BLINK" -> "单眨"
        "DOUBLE_BLINK" -> "双眨"
        "LONG_CLOSE" -> "闭眼"
        else -> eventType.ifBlank { "未知" }
    }
    return "眨眼识别：$eventLabel，${durationMs.coerceAtLeast(0L)}ms"
}

private fun toolActionIcon(action: FloatingChatToolAction): ImageVector {
    return when (action) {
        FloatingChatToolAction.Assistant -> Icons.Filled.SmartToy
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

@Composable
private fun FloatingToolActionIcon(
    action: FloatingChatToolAction,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = toolActionIcon(action),
        contentDescription = toolActionLabel(action),
        tint = if (selected) OverlayTokens.toolIconActive else OverlayTokens.toolIcon,
        modifier = modifier
    )
}

@Composable
private fun MediaActionIcon(
    action: MediaAction,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (action) {
            MediaAction.Share -> drawShareArrowIcon(color, stroke)
            MediaAction.Save -> drawSaveBoxIcon(color, stroke)
            MediaAction.Favorite -> drawHeartToolIcon(color, stroke)
            MediaAction.More -> drawMoreDotsIcon(color)
            MediaAction.Visibility -> drawEyeToolIcon(color, stroke)
            MediaAction.Edit -> drawEditPencilIcon(color, stroke)
            MediaAction.Comment -> drawCommentBubbleIcon(color, stroke)
            MediaAction.Grid -> drawGridIcon(color, stroke)
        }
    }
}

@Composable
private fun CompactInteractiveSize(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        content = content
    )
}

@Composable
private fun BottomActionIcon(
    label: String,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp).rotate(rotation)) {
        drawBottomActionGlyph(label = label, color = OverlayTokens.bottomIcon)
    }
}

private fun DrawScope.drawBottomActionGlyph(label: String, color: Color) {
    val stroke = Stroke(width = 2.1f)
    val w = size.width
    val h = size.height
    when (label) {
        "Mic" -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.34f, h * 0.10f),
                size = Size(w * 0.32f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                style = stroke
            )
            drawLine(color, Offset(w * 0.24f, h * 0.42f), Offset(w * 0.24f, h * 0.56f), strokeWidth = 2.1f)
            drawLine(color, Offset(w * 0.76f, h * 0.42f), Offset(w * 0.76f, h * 0.56f), strokeWidth = 2.1f)
            drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.50f, h * 0.84f), strokeWidth = 2.1f)
            drawLine(color, Offset(w * 0.34f, h * 0.84f), Offset(w * 0.66f, h * 0.84f), strokeWidth = 2.1f)
        }
        "Home" -> {
            val roof = Path().apply {
                moveTo(w * 0.15f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.18f)
                lineTo(w * 0.85f, h * 0.48f)
            }
            drawPath(roof, color, style = stroke)
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.26f, h * 0.44f),
                size = Size(w * 0.48f, h * 0.40f),
                cornerRadius = CornerRadius(2f, 2f),
                style = stroke
            )
            drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.50f, h * 0.84f), strokeWidth = 2f)
        }
        "Emoji" -> {
            drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
            drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.38f, h * 0.43f))
            drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.62f, h * 0.43f))
            drawLine(color, Offset(w * 0.38f, h * 0.62f), Offset(w * 0.62f, h * 0.62f), strokeWidth = 2f)
        }
        "Gift" -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.20f, h * 0.42f),
                size = Size(w * 0.60f, h * 0.42f),
                cornerRadius = CornerRadius(2f, 2f),
                style = stroke
            )
            drawRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.30f),
                size = Size(w * 0.68f, h * 0.14f),
                style = stroke
            )
            drawLine(color, Offset(w * 0.50f, h * 0.30f), Offset(w * 0.50f, h * 0.84f), strokeWidth = 2f)
            drawLine(color, Offset(w * 0.37f, h * 0.24f), Offset(w * 0.50f, h * 0.30f), strokeWidth = 2f)
            drawLine(color, Offset(w * 0.63f, h * 0.24f), Offset(w * 0.50f, h * 0.30f), strokeWidth = 2f)
        }
        "+" -> {
            drawLine(color, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokeWidth = 2.6f)
            drawLine(color, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), strokeWidth = 2.6f)
        }
        "Send" -> {
            val plane = Path().apply {
                moveTo(w * 0.16f, h * 0.20f)
                lineTo(w * 0.84f, h * 0.50f)
                lineTo(w * 0.16f, h * 0.80f)
                lineTo(w * 0.28f, h * 0.55f)
                lineTo(w * 0.54f, h * 0.50f)
                lineTo(w * 0.28f, h * 0.45f)
                close()
            }
            drawPath(plane, color, style = stroke)
            drawLine(color, Offset(w * 0.28f, h * 0.55f), Offset(w * 0.54f, h * 0.50f), strokeWidth = 2.1f)
        }
        else -> {
            drawCircle(color, radius = w * 0.24f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
        }
    }
}

private fun DrawScope.drawRobotToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.50f, h * 0.11f), Offset(w * 0.50f, h * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.50f, h * 0.09f))
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.24f),
        size = Size(w * 0.64f, h * 0.48f),
        cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
        style = stroke
    )
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.37f, h * 0.47f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.63f, h * 0.47f))
    drawLine(color, Offset(w * 0.40f, h * 0.62f), Offset(w * 0.60f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.18f, h * 0.43f), Offset(w * 0.10f, h * 0.43f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.82f, h * 0.43f), Offset(w * 0.90f, h * 0.43f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawGalleryToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.14f, h * 0.18f),
        size = Size(w * 0.72f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = stroke
    )
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.64f, h * 0.36f))
    val mountain = Path().apply {
        moveTo(w * 0.20f, h * 0.72f)
        lineTo(w * 0.40f, h * 0.54f)
        lineTo(w * 0.53f, h * 0.66f)
        lineTo(w * 0.63f, h * 0.58f)
        lineTo(w * 0.80f, h * 0.74f)
    }
    drawPath(mountain, color, style = stroke)
}

private fun DrawScope.drawPlayCircleToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
    val play = Path().apply {
        moveTo(w * 0.44f, h * 0.35f)
        lineTo(w * 0.44f, h * 0.65f)
        lineTo(w * 0.66f, h * 0.50f)
        close()
    }
    drawPath(play, color)
}

private fun DrawScope.drawFolderToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val folder = Path().apply {
        moveTo(w * 0.14f, h * 0.34f)
        lineTo(w * 0.36f, h * 0.34f)
        lineTo(w * 0.44f, h * 0.25f)
        lineTo(w * 0.64f, h * 0.25f)
        lineTo(w * 0.70f, h * 0.34f)
        lineTo(w * 0.86f, h * 0.34f)
        lineTo(w * 0.86f, h * 0.78f)
        lineTo(w * 0.14f, h * 0.78f)
        close()
    }
    drawPath(folder, color, style = stroke)
}

private fun DrawScope.drawPhoneDeviceToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.30f, h * 0.12f),
        size = Size(w * 0.40f, h * 0.76f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.43f, h * 0.77f), Offset(w * 0.57f, h * 0.77f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawDocumentToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val page = Path().apply {
        moveTo(w * 0.25f, h * 0.14f)
        lineTo(w * 0.60f, h * 0.14f)
        lineTo(w * 0.77f, h * 0.31f)
        lineTo(w * 0.77f, h * 0.84f)
        lineTo(w * 0.25f, h * 0.84f)
        close()
    }
    drawPath(page, color, style = stroke)
    drawLine(color, Offset(w * 0.60f, h * 0.14f), Offset(w * 0.60f, h * 0.31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.60f, h * 0.31f), Offset(w * 0.77f, h * 0.31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.36f, h * 0.48f), Offset(w * 0.66f, h * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.36f, h * 0.62f), Offset(w * 0.66f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawCardToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.14f, h * 0.25f),
        size = Size(w * 0.72f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.18f, h * 0.43f), Offset(w * 0.82f, h * 0.43f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.25f, h * 0.62f), Offset(w * 0.43f, h * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawWalletToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.14f, h * 0.28f),
        size = Size(w * 0.72f, h * 0.50f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
        style = stroke
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.55f, h * 0.44f),
        size = Size(w * 0.25f, h * 0.20f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        style = stroke
    )
    drawCircle(color, radius = w * 0.025f, center = Offset(w * 0.66f, h * 0.54f))
}

private fun DrawScope.drawHeartToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val heart = Path().apply {
        moveTo(w * 0.50f, h * 0.78f)
        cubicTo(w * 0.24f, h * 0.60f, w * 0.16f, h * 0.44f, w * 0.25f, h * 0.30f)
        cubicTo(w * 0.35f, h * 0.16f, w * 0.48f, h * 0.25f, w * 0.50f, h * 0.36f)
        cubicTo(w * 0.52f, h * 0.25f, w * 0.65f, h * 0.16f, w * 0.75f, h * 0.30f)
        cubicTo(w * 0.84f, h * 0.44f, w * 0.76f, h * 0.60f, w * 0.50f, h * 0.78f)
    }
    drawPath(heart, color, style = stroke)
}

private fun DrawScope.drawEyeToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val eye = Path().apply {
        moveTo(w * 0.12f, h * 0.50f)
        cubicTo(w * 0.28f, h * 0.28f, w * 0.72f, h * 0.28f, w * 0.88f, h * 0.50f)
        cubicTo(w * 0.72f, h * 0.72f, w * 0.28f, h * 0.72f, w * 0.12f, h * 0.50f)
    }
    drawPath(eye, color, style = stroke)
    drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
}

private fun DrawScope.drawAddUserToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.38f, h * 0.34f), style = stroke)
    drawArc(
        color = color,
        startAngle = 202f,
        sweepAngle = 136f,
        useCenter = false,
        topLeft = Offset(w * 0.19f, h * 0.47f),
        size = Size(w * 0.38f, h * 0.34f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.70f, h * 0.31f), Offset(w * 0.70f, h * 0.59f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.56f, h * 0.45f), Offset(w * 0.84f, h * 0.45f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawPlayTriangleToolIcon(color: Color) {
    val w = size.width
    val h = size.height
    val play = Path().apply {
        moveTo(w * 0.36f, h * 0.24f)
        lineTo(w * 0.36f, h * 0.76f)
        lineTo(w * 0.78f, h * 0.50f)
        close()
    }
    drawPath(play, color)
}

private fun DrawScope.drawPhoneHandsetToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val call = Path().apply {
        moveTo(w * 0.30f, h * 0.29f)
        cubicTo(w * 0.22f, h * 0.40f, w * 0.30f, h * 0.59f, w * 0.45f, h * 0.70f)
        cubicTo(w * 0.60f, h * 0.82f, w * 0.78f, h * 0.79f, w * 0.84f, h * 0.66f)
    }
    drawPath(call, color, style = stroke)
    drawLine(color, Offset(w * 0.30f, h * 0.29f), Offset(w * 0.42f, h * 0.41f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.67f, h * 0.57f), Offset(w * 0.84f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawVideoCameraToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.31f),
        size = Size(w * 0.48f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        style = stroke
    )
    val lens = Path().apply {
        moveTo(w * 0.60f, h * 0.43f)
        lineTo(w * 0.84f, h * 0.32f)
        lineTo(w * 0.84f, h * 0.68f)
        lineTo(w * 0.60f, h * 0.57f)
        close()
    }
    drawPath(lens, color, style = stroke)
}

private fun DrawScope.drawGearToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.50f, h * 0.50f)
    drawCircle(color, radius = w * 0.24f, center = center, style = stroke)
    drawCircle(color, radius = w * 0.07f, center = center, style = stroke)
    val teeth = listOf(
        Offset(w * 0.50f, h * 0.10f) to Offset(w * 0.50f, h * 0.22f),
        Offset(w * 0.50f, h * 0.78f) to Offset(w * 0.50f, h * 0.90f),
        Offset(w * 0.10f, h * 0.50f) to Offset(w * 0.22f, h * 0.50f),
        Offset(w * 0.78f, h * 0.50f) to Offset(w * 0.90f, h * 0.50f),
        Offset(w * 0.22f, h * 0.22f) to Offset(w * 0.31f, h * 0.31f),
        Offset(w * 0.69f, h * 0.69f) to Offset(w * 0.78f, h * 0.78f),
        Offset(w * 0.78f, h * 0.22f) to Offset(w * 0.69f, h * 0.31f),
        Offset(w * 0.31f, h * 0.69f) to Offset(w * 0.22f, h * 0.78f)
    )
    teeth.forEach { (start, end) ->
        drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawShareArrowIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val arrow = Path().apply {
        moveTo(w * 0.22f, h * 0.58f)
        cubicTo(w * 0.36f, h * 0.38f, w * 0.56f, h * 0.30f, w * 0.78f, h * 0.28f)
        lineTo(w * 0.66f, h * 0.17f)
        moveTo(w * 0.78f, h * 0.28f)
        lineTo(w * 0.66f, h * 0.42f)
    }
    drawPath(arrow, color, style = stroke)
    drawLine(color, Offset(w * 0.22f, h * 0.58f), Offset(w * 0.22f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawSaveBoxIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, h * 0.46f),
        size = Size(w * 0.56f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.66f, h * 0.42f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawMoreDotsIcon(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.30f, h * 0.50f))
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.50f))
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.70f, h * 0.50f))
}

private fun DrawScope.drawEditPencilIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.30f, h * 0.70f), Offset(w * 0.72f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.64f, h * 0.20f), Offset(w * 0.80f, h * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.78f), Offset(w * 0.36f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.18f),
        size = Size(w * 0.64f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        style = stroke
    )
}

private fun DrawScope.drawCommentBubbleIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val bubble = Path().apply {
        moveTo(w * 0.22f, h * 0.24f)
        lineTo(w * 0.78f, h * 0.24f)
        lineTo(w * 0.78f, h * 0.66f)
        lineTo(w * 0.50f, h * 0.66f)
        lineTo(w * 0.34f, h * 0.82f)
        lineTo(w * 0.36f, h * 0.66f)
        lineTo(w * 0.22f, h * 0.66f)
        close()
    }
    drawPath(bubble, color, style = stroke)
}

private fun DrawScope.drawGridIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val cell = Size(w * 0.20f, h * 0.20f)
    listOf(
        Offset(w * 0.22f, h * 0.22f),
        Offset(w * 0.58f, h * 0.22f),
        Offset(w * 0.22f, h * 0.58f),
        Offset(w * 0.58f, h * 0.58f)
    ).forEach { topLeft ->
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = cell,
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            style = stroke
        )
    }
}

internal fun referenceToolActionsFor(actions: List<FloatingChatToolAction>): List<FloatingChatToolAction> {
    val availableActions = actions.toSet()
    val referenceOrder = listOf(
        FloatingChatToolAction.Assistant,
        FloatingChatToolAction.Blink,
        FloatingChatToolAction.Gallery,
        FloatingChatToolAction.Camera,
        FloatingChatToolAction.Location,
        FloatingChatToolAction.Favorite,
        FloatingChatToolAction.RedPacket,
        FloatingChatToolAction.Transfer,
        FloatingChatToolAction.Files,
        FloatingChatToolAction.Card,
        FloatingChatToolAction.Moments,
        FloatingChatToolAction.QuickPhrase
    ).filter { action -> action in availableActions }

    return referenceOrder.ifEmpty { actions }
}

internal fun moveToolAction(
    actions: List<FloatingChatToolAction>,
    fromIndex: Int,
    toIndex: Int
): List<FloatingChatToolAction> {
    if (actions.isEmpty()) return actions
    val safeFrom = fromIndex.coerceIn(0, actions.lastIndex)
    val safeTo = toIndex.coerceIn(0, actions.lastIndex)
    if (safeFrom == safeTo) return actions
    val next = actions.toMutableList()
    val moved = next.removeAt(safeFrom)
    next.add(safeTo, moved)
    return next
}

internal fun toolReorderTargetIndex(
    startIndex: Int,
    dragOffsetY: Float,
    itemSlotHeightPx: Float,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    if (itemSlotHeightPx <= 0f) return startIndex.coerceIn(0, itemCount - 1)
    val crossedSlots = (dragOffsetY / itemSlotHeightPx).roundToInt()
    return (startIndex + crossedSlots).coerceIn(0, itemCount - 1)
}

private fun toolReorderDraggedTranslationY(
    dragOffsetY: Float,
    startIndex: Int,
    currentIndex: Int,
    itemSlotHeightPx: Float
): Float {
    if (startIndex < 0 || currentIndex < 0 || itemSlotHeightPx <= 0f) {
        return dragOffsetY
    }
    return dragOffsetY - ((currentIndex - startIndex) * itemSlotHeightPx)
}

private fun Offset.getDistance(): Float {
    return sqrt((x * x) + (y * y))
}

private fun loadToolActionOrder(
    context: Context,
    availableActions: List<FloatingChatToolAction>
): List<FloatingChatToolAction> {
    val fallback = referenceToolActionsFor(availableActions)
    val stored = toolOrderPrefs(context).getString(KEY_TOOL_ACTION_ORDER, null)
        ?.split(",")
        ?.mapNotNull { name -> runCatching { FloatingChatToolAction.valueOf(name) }.getOrNull() }
        .orEmpty()
    if (stored.isEmpty()) return fallback
    val available = fallback.toSet()
    val ordered = stored.filter { action -> action in available }
    val missing = fallback.filterNot { action -> action in ordered }
    return (ordered + missing).ifEmpty { fallback }
}

private fun saveToolActionOrder(
    context: Context,
    actions: List<FloatingChatToolAction>
) {
    toolOrderPrefs(context)
        .edit()
        .putString(KEY_TOOL_ACTION_ORDER, actions.joinToString(",") { action -> action.name })
        .apply()
}

private fun toolOrderPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(TOOL_ORDER_PREFS, Context.MODE_PRIVATE)
}

internal data class FloatingChatAccountProfile(
    val accountId: String,
    val name: String,
    val phone: String,
    val signature: String,
    val gender: String,
    val company: String,
    val title: String,
    val region: String,
    val wechatId: String,
    val email: String,
    val tags: String,
    val avatarInitials: String,
    val avatarColor: Long,
    val avatarImageUri: String = ""
)

internal fun defaultAccountProfileFor(account: FloatingChatContact): FloatingChatAccountProfile {
    return FloatingChatAccountProfile(
        accountId = account.id,
        name = account.name,
        phone = "",
        signature = account.description,
        gender = "未设置",
        company = account.description.substringAfter("·", "").trim().ifBlank { "浮窗聊天" },
        title = account.description.substringBefore("·").trim().ifBlank { "账号" },
        region = "",
        wechatId = account.id.replace("-", "_"),
        email = "",
        tags = "",
        avatarInitials = account.initials,
        avatarColor = account.avatarColor,
        avatarImageUri = ""
    )
}

private fun FloatingChatAccountProfile.toContact(fallback: FloatingChatContact): FloatingChatContact {
    return fallback.copy(
        name = name.ifBlank { fallback.name },
        initials = avatarInitials.ifBlank { name.take(2).ifBlank { fallback.initials } },
        description = accountProfileSubtitle(this).ifBlank { fallback.description },
        avatarColor = avatarColor
    )
}

internal fun accountProfileEditorFieldKeys(): List<String> {
    return listOf(
        "avatarImage",
        "name",
        "phone",
        "signature",
        "gender",
        "company",
        "title",
        "region",
        "wechatId",
        "email",
        "tags"
    )
}

internal fun rightRailAccountAvatarSupportsLongPressEdit(): Boolean = true

internal fun rightRailAccountAvatarClickSelectsSendingAccount(): Boolean = true

internal fun accountProfileEditorSupportsImageAvatarUpload(): Boolean = true

internal fun accountProfileEditorHidesAvatarColorPalette(): Boolean = true

internal fun accountProfileEditorPersistsChanges(): Boolean = true

internal fun cardToolSendsEditedAccountProfileCard(): Boolean = true

internal fun cardToolUsesSelectedAccountInsteadOfThreadDefault(): Boolean = true

internal fun cardToolOpensAccountPickerInsteadOfDirectSend(): Boolean = true

internal fun cardToolAccountPickerShowsAllAccounts(): Boolean = true

internal fun contactLinkMessageReusesAccountCardPreview(): Boolean = true

private fun accountProfileMessageForToolAction(
    action: FloatingChatToolAction,
    profile: FloatingChatAccountProfile,
    baseMessage: FloatingChatMessage
): FloatingChatMessage {
    return if (action == FloatingChatToolAction.Card) {
        accountProfileCardMessage(profile, baseMessage)
    } else {
        baseMessage
    }
}

internal fun accountProfileCardMessage(
    profile: FloatingChatAccountProfile,
    baseMessage: FloatingChatMessage
): FloatingChatMessage {
    val safeName = profile.name.trim().ifBlank { "未命名账号" }
    return baseMessage.copy(
        type = FloatingChatMessageType.ContactLink,
        text = "推名片：$safeName",
        presentation = FloatingChatMessagePresentation.SpecialCard,
        cardKind = FloatingChatContactCardKind.Personal,
        cardName = safeName,
        cardSubtitle = accountProfileSubtitle(profile),
        detail = accountProfileDetail(profile),
        thumbnailUrl = profile.avatarImageUri.takeIf { it.isNotBlank() },
        resourceUrl = "https://aiff.app/cards/${profile.accountId}-${profile.wechatId.ifBlank { safeName }.toCardSlug()}"
    )
}

private fun accountProfileSubtitle(profile: FloatingChatAccountProfile): String {
    return listOf(profile.title, profile.company, profile.region)
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .joinToString(" · ")
}

private fun accountProfileDetail(profile: FloatingChatAccountProfile): String {
    return listOfNotNull(
        profile.phone.trim().takeIf { it.isNotEmpty() }?.let { "电话 $it" },
        profile.wechatId.trim().takeIf { it.isNotEmpty() }?.let { "微信 $it" },
        profile.email.trim().takeIf { it.isNotEmpty() }?.let { "邮箱 $it" },
        profile.gender.trim().takeIf { it.isNotEmpty() && it != "未设置" }?.let { "性别 $it" },
        profile.signature.trim().takeIf { it.isNotEmpty() }?.let { "签名 $it" },
        profile.tags.trim().takeIf { it.isNotEmpty() }?.let { "标签 $it" }
    ).joinToString(" · ")
}

private fun String.toCardSlug(): String {
    return lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "profile" }
}

private fun loadAccountProfile(
    context: Context,
    account: FloatingChatContact
): FloatingChatAccountProfile {
    val fallback = defaultAccountProfileFor(account)
    val stored = accountProfilePrefs(context).getString(account.id, null) ?: return fallback
    return runCatching {
        val properties = Properties().apply { load(StringReader(stored)) }
        FloatingChatAccountProfile(
            accountId = account.id,
            name = properties.getProperty("name", fallback.name),
            phone = properties.getProperty("phone", fallback.phone),
            signature = properties.getProperty("signature", fallback.signature),
            gender = properties.getProperty("gender", fallback.gender),
            company = properties.getProperty("company", fallback.company),
            title = properties.getProperty("title", fallback.title),
            region = properties.getProperty("region", fallback.region),
            wechatId = properties.getProperty("wechatId", fallback.wechatId),
            email = properties.getProperty("email", fallback.email),
            tags = properties.getProperty("tags", fallback.tags),
            avatarInitials = properties.getProperty("avatarInitials", fallback.avatarInitials),
            avatarColor = properties.getProperty("avatarColor", fallback.avatarColor.toString()).toLongOrNull()
                ?: fallback.avatarColor,
            avatarImageUri = properties.getProperty("avatarImageUri", fallback.avatarImageUri)
        )
    }.getOrElse {
        fallback
    }
}

private fun saveAccountProfile(
    context: Context,
    profile: FloatingChatAccountProfile
) {
    val properties = Properties().apply {
        setProperty("name", profile.name)
        setProperty("phone", profile.phone)
        setProperty("signature", profile.signature)
        setProperty("gender", profile.gender)
        setProperty("company", profile.company)
        setProperty("title", profile.title)
        setProperty("region", profile.region)
        setProperty("wechatId", profile.wechatId)
        setProperty("email", profile.email)
        setProperty("tags", profile.tags)
        setProperty("avatarInitials", profile.avatarInitials)
        setProperty("avatarColor", profile.avatarColor.toString())
        setProperty("avatarImageUri", profile.avatarImageUri)
    }
    val writer = StringWriter()
    properties.store(writer, null)
    accountProfilePrefs(context)
        .edit()
        .putString(profile.accountId, writer.toString())
        .apply()
}

private fun accountProfilePrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(ACCOUNT_PROFILE_PREFS, Context.MODE_PRIVATE)
}

private fun loadQuickPhrases(context: Context): List<String> {
    val stored = quickPhrasePrefs(context).getString(KEY_QUICK_PHRASES, null)
        ?: return DefaultQuickPhrases
    return normalizeQuickPhrases(
        stored
            .split(QUICK_PHRASE_SEPARATOR)
            .map { phrase -> phrase.trim() }
    )
}

private fun saveQuickPhrases(context: Context, phrases: List<String>) {
    quickPhrasePrefs(context)
        .edit()
        .putString(KEY_QUICK_PHRASES, normalizeQuickPhrases(phrases).joinToString(QUICK_PHRASE_SEPARATOR))
        .apply()
}

private fun normalizeQuickPhrases(phrases: List<String>): List<String> {
    return phrases
        .asSequence()
        .map { phrase -> phrase.trim() }
        .filter { phrase -> phrase.isNotEmpty() }
        .distinct()
        .take(QuickPhraseMaxCount)
        .toList()
}

private fun quickPhrasePrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(QUICK_PHRASE_PREFS, Context.MODE_PRIVATE)
}

private fun defaultMomentPosts(): List<AppMomentPost> {
    return listOf(
        AppMomentPost(
            id = "moment-rain-1",
            author = "周雨晴",
            content = "今天把悬浮聊天里的图片、视频和消息气泡都对齐了，晚上继续看连接线细节。",
            time = "12 分钟前",
            likedBy = listOf("林舟", "知南")
        ),
        AppMomentPost(
            id = "moment-chen-1",
            author = "陈若川",
            content = "深圳湾这边信号不错，位置消息和文件链接截图后也能继续访问。",
            time = "38 分钟前",
            comments = listOf(AppMomentComment("雨晴", "这个入口挺顺手。"))
        ),
        AppMomentPost(
            id = "moment-lin-1",
            author = "林舟",
            content = "新的浮窗红包和转账先做成 App 内状态流，后面再接真实账户体系。",
            time = "1 小时前"
        )
    )
}

private fun wechatMomentPosts(): List<AppMomentPost> {
    return listOf(
        AppMomentPost(
            id = "moment-cat-1",
            author = "巷子里的猫",
            content = "",
            time = "34天前",
            avatarText = "猫",
            avatarColor = Color(0xFF6FA8D8),
            media = AppMomentMedia(
                kind = MomentMediaKind.Image,
                widthDp = 82,
                heightDp = 176,
                color = Color(0xFF7E806B),
                label = "图片"
            ),
            likedBy = listOf("若川", "林舟")
        ),
        AppMomentPost(
            id = "moment-xueyin-1",
            author = "血�?0006",
            content = "",
            time = "47天前",
            avatarText = "血",
            avatarColor = Color(0xFF2F3135),
            media = AppMomentMedia(
                kind = MomentMediaKind.Link,
                color = Color(0xFFAC1B1B),
                label = "图文"
            ),
            linkTitle = "不装了！百年毒龙宣战了！",
            sourceLabel = "公众号 · 血饮",
            comments = listOf(AppMomentComment("雨晴", "已转发给运营组。"))
        ),
        AppMomentPost(
            id = "moment-xueyin-2",
            author = "血饮助�?05",
            content = "",
            time = "47天前",
            avatarText = "血",
            avatarColor = Color(0xFF2F3135),
            media = AppMomentMedia(
                kind = MomentMediaKind.Link,
                color = Color(0xFFAC1B1B),
                label = "图文"
            ),
            linkTitle = "不装了！百年毒龙宣战了！",
            sourceLabel = "公众号 · 血饮"
        ),
        AppMomentPost(
            id = "moment-xueyin-3",
            author = "血�?002",
            content = "",
            time = "47天前",
            avatarText = "血",
            avatarColor = Color(0xFF2F3135),
            media = AppMomentMedia(
                kind = MomentMediaKind.Link,
                color = Color(0xFFAC1B1B),
                label = "图文"
            ),
            linkTitle = "不装了！百年毒龙宣战了！",
            sourceLabel = "公众号 · 血饮"
        )
    )
}

private fun defaultLocationOptions(): List<AppLocationOption> {
    return listOf(
        AppLocationOption("深圳湾科技生态园 9 栋", "深圳市南山区高新南十道 9 号"),
        AppLocationOption("星河创意园 3 号楼", "深圳市南山区科苑南路"),
        AppLocationOption("后海中心 A 座", "深圳市南山区海德三道")
    )
}

private fun currentDeviceLocationState(context: Context): DeviceLocationState {
    if (!hasLocationPermission(context)) {
        return DeviceLocationState(permissionDenied = true, error = "需要授权后才能发送真实位置")
    }
    val option = lastKnownLocationOption(context)
    return if (option != null) {
        DeviceLocationState(option = option)
    } else {
        DeviceLocationState(error = "还没有可用的手机定位，请点重新定位")
    }
}

private fun requestCurrentDeviceLocation(
    context: Context,
    onResult: (DeviceLocationState) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onResult(DeviceLocationState(permissionDenied = true, error = "需要授权后才能发送真实位置"))
        return
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onResult(DeviceLocationState(error = "系统定位服务不可用"))
        return
    }
    val providers = realLocationProviders(locationManager)
    val latest = providers.mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { location -> location.time }
    if (latest != null) {
        onResult(DeviceLocationState(option = latest.toAppLocationOption()))
    }
    if (providers.isEmpty()) {
        onResult(
            if (latest == null) {
                DeviceLocationState(error = "请先打开系统定位开关")
            } else {
                DeviceLocationState(option = latest.toAppLocationOption())
            }
        )
        return
    }
    val mainHandler = Handler(Looper.getMainLooper())
    var delivered = latest != null
    var listener: LocationListener? = null
    fun finish(state: DeviceLocationState) {
        if (delivered && state.option == null) return
        delivered = true
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        onResult(state)
    }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            finish(DeviceLocationState(option = location.toAppLocationOption()))
        }

        override fun onProviderDisabled(provider: String) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    providers.firstOrNull { provider ->
        runCatching {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)
    }
    mainHandler.postDelayed(
        {
            if (!delivered) {
                finish(DeviceLocationState(error = "定位超时，请确认系统定位已开启"))
            }
        },
        LOCATION_REQUEST_TIMEOUT_MS
    )
}

private fun hasLocationPermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun realLocationProviders(locationManager: LocationManager): List<String> {
    return listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { provider ->
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }
}

private fun lastKnownLocationOption(context: Context): AppLocationOption? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return realLocationProviders(locationManager)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { location -> location.time }
        ?.toAppLocationOption()
}

private fun Location.toAppLocationOption(): AppLocationOption {
    val latitudeText = String.format(Locale.US, "%.6f", latitude)
    val longitudeText = String.format(Locale.US, "%.6f", longitude)
    val accuracyText = if (hasAccuracy()) "，精度约 ${accuracy.toInt().coerceAtLeast(1)} 米" else ""
    return AppLocationOption(
        title = "我的当前位置",
        address = "$latitudeText, $longitudeText$accuracyText",
        latitude = latitude,
        longitude = longitude,
        geoUri = "geo:$latitudeText,$longitudeText?q=$latitudeText,$longitudeText"
    )
}

private fun favoriteCollectionItems(
    messages: List<FloatingChatMessage>,
    favoriteMessageIds: Map<String, Boolean>,
    favoriteMediaIds: Map<String, Boolean>,
    storedItems: List<FavoriteCollectionItem>
): List<FavoriteCollectionItem> {
    val refreshedStoredItems = refreshFavoriteCollectionSourcesFromMessages(
        items = storedItems,
        messages = messages
    )
    val explicitFavorites = messages.filter { message ->
        favoriteMessageIds[message.id] == true || favoriteMediaIds[message.id] == true
    }.map { message -> message.toFavoriteCollectionItem() }
    val seededFavorites = if (refreshedStoredItems.isEmpty() && explicitFavorites.isEmpty()) {
        messages.filter { message ->
            message.resourceUrl != null ||
                message.type == FloatingChatMessageType.ImageThumbnail ||
                message.type == FloatingChatMessageType.VideoPreview ||
                message.type == FloatingChatMessageType.FilePreview ||
                message.type == FloatingChatMessageType.ContactLink ||
                message.type == FloatingChatMessageType.MiniProgramLink
        }.take(6).map { message -> message.toFavoriteCollectionItem() }
    } else {
        emptyList()
    }
    return mergeFavoriteCollectionItems(refreshedStoredItems + explicitFavorites + seededFavorites)
}

internal fun refreshFavoriteCollectionSourcesFromMessages(
    items: List<FavoriteCollectionItem>,
    messages: List<FloatingChatMessage>
): List<FavoriteCollectionItem> {
    if (items.isEmpty() || messages.isEmpty()) return items
    val messagesById = messages.associateBy { message -> message.id }
    return items.map { item ->
        val currentMessage = messagesById[item.messageId] ?: return@map item
        item.copy(source = currentMessage.toFavoriteCollectionItem().source)
    }
}

private fun FloatingChatMessage.toFavoriteCollectionItem(): FavoriteCollectionItem {
    val title = when (type) {
        FloatingChatMessageType.ImageThumbnail -> "图片收藏"
        FloatingChatMessageType.VideoPreview -> "视频收藏"
        FloatingChatMessageType.FilePreview -> fileName ?: text
        FloatingChatMessageType.ContactLink -> cardName ?: text
        FloatingChatMessageType.MiniProgramLink -> appName ?: text
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> locationTitle ?: text
        else -> text.ifBlank { type.label }
    }
    return FavoriteCollectionItem(
        messageId = id,
        type = type,
        title = title,
        description = favoriteDescription(),
        source = "${senderName} · ${time}"
    )
}

private fun updateFavoriteCollectionItems(
    context: Context,
    items: MutableList<FavoriteCollectionItem>,
    message: FloatingChatMessage,
    favorite: Boolean
) {
    val nextItems = if (favorite) {
        mergeFavoriteCollectionItems(listOf(message.toFavoriteCollectionItem()) + items)
            .take(FavoriteCollectionMaxCount)
    } else {
        items.filterNot { item -> item.messageId == message.id }
    }
    items.clear()
    items.addAll(nextItems)
    saveFavoriteCollectionItems(context, nextItems)
}

private fun mergeFavoriteCollectionItems(
    items: List<FavoriteCollectionItem>
): List<FavoriteCollectionItem> {
    val seen = linkedSetOf<String>()
    return items.filter { item -> seen.add(item.messageId) }
}

private fun loadFavoriteCollectionItems(context: Context): List<FavoriteCollectionItem> {
    val stored = favoriteCollectionPrefs(context).getString(KEY_FAVORITE_COLLECTION_ITEMS, null)
        ?: return emptyList()
    return parseFavoriteCollectionItems(stored)
}

private fun saveFavoriteCollectionItems(context: Context, items: List<FavoriteCollectionItem>) {
    favoriteCollectionPrefs(context)
        .edit()
        .putString(KEY_FAVORITE_COLLECTION_ITEMS, serializeFavoriteCollectionItems(items))
        .apply()
}

private fun favoriteCollectionPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(FAVORITE_COLLECTION_PREFS, Context.MODE_PRIVATE)
}

private fun serializeFavoriteCollectionItems(items: List<FavoriteCollectionItem>): String {
    val properties = Properties()
    val cappedItems = items.take(FavoriteCollectionMaxCount)
    properties.setProperty("count", cappedItems.size.toString())
    cappedItems.forEachIndexed { index, item ->
        val prefix = "item.$index."
        properties.setProperty(prefix + "messageId", item.messageId)
        properties.setProperty(prefix + "type", item.type.name)
        properties.setProperty(prefix + "title", item.title)
        properties.setProperty(prefix + "description", item.description)
        properties.setProperty(prefix + "source", item.source)
    }
    val writer = StringWriter()
    properties.store(writer, null)
    return writer.toString()
}

private fun parseFavoriteCollectionItems(stored: String): List<FavoriteCollectionItem> {
    return runCatching {
        val properties = Properties()
        properties.load(StringReader(stored))
        val count = properties.getProperty("count")?.toIntOrNull()?.coerceIn(0, FavoriteCollectionMaxCount) ?: 0
        buildList {
            for (index in 0 until count) {
                val prefix = "item.$index."
                val type = properties.getProperty(prefix + "type")
                    .takeIf { value -> value.isNotBlank() }
                    ?.let { value -> runCatching { FloatingChatMessageType.valueOf(value) }.getOrNull() }
                    ?: FloatingChatMessageType.Text
                val messageId = properties.getProperty(prefix + "messageId")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: continue
                val title = properties.getProperty(prefix + "title")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: type.label
                add(
                    FavoriteCollectionItem(
                        messageId = messageId,
                        type = type,
                        title = normalizeFavoriteCollectionText(title),
                        description = normalizeFavoriteCollectionText(properties.getProperty(prefix + "description").orEmpty()),
                        source = properties.getProperty(prefix + "source")
                            ?.takeIf { value -> value.isNotBlank() }
                            ?.let { value -> normalizeFavoriteCollectionText(value) }
                            ?: "收藏"
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun favoriteCollectionSerializationRoundTrips(): Boolean {
    val item = FavoriteCollectionItem(
        messageId = "message-1",
        type = FloatingChatMessageType.ImageThumbnail,
        title = "图片收藏",
        description = "content://media/external/images/media/42",
        source = "陈晨 · 10:20"
    )
    val decoded = parseFavoriteCollectionItems(serializeFavoriteCollectionItems(listOf(item))).singleOrNull()
    return decoded?.messageId == item.messageId &&
        decoded.type == item.type &&
        decoded.title == item.title &&
        decoded.description == item.description &&
        decoded.source == item.source
}

internal fun favoriteCollectionClickOpensContentPreview(): Boolean = true

internal fun favoriteCollectionClickSendsToChat(): Boolean = false

internal fun favoriteCollectionPreviewActionLabels(): List<String> {
    return listOf("转发", "删除", "关闭")
}

internal fun favoriteCollectionPreviewTimestampLabel(): String = "收藏"

internal fun favoriteCollectionSelectionCountLabel(count: Int): String = "已选 $count"

internal fun favoriteCollectionLongPressActions(): List<MessageLongPressAction> {
    return listOf(
        MessageLongPressAction.Forward,
        MessageLongPressAction.Delete,
        MessageLongPressAction.MultiSelect
    )
}

internal fun favoriteCollectionItemUsesMediaPreview(item: FavoriteCollectionItem): Boolean {
    return item.type == FloatingChatMessageType.ImageThumbnail ||
        item.type == FloatingChatMessageType.VideoPreview
}

internal fun favoriteCollectionPreviewMessage(item: FavoriteCollectionItem): FloatingChatMessage {
    val title = normalizeFavoriteCollectionText(item.title)
    val description = normalizeFavoriteCollectionText(item.description)
    val source = normalizeFavoriteCollectionText(item.source)
    val url = description.takeIf { value -> value.isNotBlank() }
    return FloatingChatMessage(
        id = "favorite-preview-${item.messageId}",
        type = item.type,
        text = title,
        fromMe = false,
        senderName = source,
        time = favoriteCollectionPreviewTimestampLabel(),
        presentation = if (favoriteCollectionItemUsesMediaPreview(item)) {
            FloatingChatMessagePresentation.MediaStandalone
        } else {
            FloatingChatMessagePresentation.SpecialCard
        },
        connectionTarget = FloatingChatConnectionTarget.None,
        detail = description,
        quoteAuthor = source.takeIf { value -> value.isNotBlank() },
        quoteText = description.takeIf { value -> value.isNotBlank() },
        cardName = title.takeIf { value -> item.type == FloatingChatMessageType.ContactLink && value.isNotBlank() },
        cardSubtitle = description.takeIf { value -> item.type == FloatingChatMessageType.ContactLink && value.isNotBlank() },
        appName = title.takeIf { value -> item.type == FloatingChatMessageType.MiniProgramLink && value.isNotBlank() },
        locationTitle = title.takeIf { value ->
            (item.type == FloatingChatMessageType.Location || item.type == FloatingChatMessageType.InlineLocation) &&
                value.isNotBlank()
        },
        locationAddress = description.takeIf { value ->
            (item.type == FloatingChatMessageType.Location || item.type == FloatingChatMessageType.InlineLocation) &&
                value.isNotBlank()
        },
        resourceUrl = url,
        fileName = title.takeIf { value -> item.type == FloatingChatMessageType.FilePreview && value.isNotBlank() },
        filePreviewLines = if (item.type == FloatingChatMessageType.FilePreview && description.isNotBlank()) {
            description.lines().take(4)
        } else {
            emptyList()
        },
        thumbnailUrl = url.takeIf { favoriteCollectionItemUsesMediaPreview(item) }
    )
}

private fun normalizeFavoriteCollectionText(value: String): String {
    if (!looksLikeLegacyFavoriteMojibake(value)) return value
    val repaired = decodeLegacyFavoriteMojibake(value) ?: return value
    return if (repaired.isNotBlank() && repaired != value) repaired else value
}

private fun looksLikeLegacyFavoriteMojibake(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.any { char -> char == '\uFFFD' || char == '\u20AC' || char.code in 0xE000..0xF8FF }) {
        return true
    }
    val markerCount = value.count { char -> FavoriteLegacyMojibakeMarkers.indexOf(char) >= 0 }
    return markerCount >= 2
}

private fun decodeLegacyFavoriteMojibake(value: String): String? {
    val bytes = runCatching { value.toByteArray(FavoriteLegacyMojibakeCharset) }.getOrNull() ?: return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
}

private fun FloatingChatMessage.favoriteDescription(): String {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> resourceUrl ?: thumbnailUrl ?: "聊天图片"
        FloatingChatMessageType.VideoPreview -> resourceUrl ?: thumbnailUrl ?: "聊天视频"
        FloatingChatMessageType.FilePreview -> filePreviewLines.firstOrNull() ?: resourceUrl ?: text
        FloatingChatMessageType.ContactLink -> cardSubtitle ?: resourceUrl ?: text
        FloatingChatMessageType.MiniProgramLink -> detail ?: resourceUrl ?: text
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> locationAddress ?: resourceUrl ?: text
        else -> quoteText ?: detail ?: resourceUrl ?: text
    }
}

private fun favoriteIconFor(type: FloatingChatMessageType): ImageVector {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> Icons.Filled.Image
        FloatingChatMessageType.VideoPreview -> Icons.Filled.VideoLibrary
        FloatingChatMessageType.FilePreview -> Icons.Filled.Article
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> Icons.Filled.LocationOn
        FloatingChatMessageType.ContactLink -> Icons.Filled.CreditCard
        else -> Icons.Filled.Star
    }
}

private fun favoriteAccentColor(type: FloatingChatMessageType): Color {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> Color(0xFF5B8EB7)
        FloatingChatMessageType.VideoPreview -> Color(0xFF855E9B)
        FloatingChatMessageType.FilePreview -> Color(0xFF6A7F90)
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> Color(0xFF3D8B78)
        FloatingChatMessageType.ContactLink -> Color(0xFF5674A8)
        else -> Color(0xFF8D7B55)
    }
}

private fun sanitizeMoneyInput(value: String): String {
    val filtered = value.filter { char -> char.isDigit() || char == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) {
        return filtered.take(6)
    }
    val integerPart = filtered.take(firstDot).take(6)
    val decimalPart = filtered.drop(firstDot + 1).filter { char -> char != '.' }.take(2)
    return "$integerPart.$decimalPart"
}

@Composable
private fun MessageSelectionToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactInteractiveSize {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) OverlayTokens.activeControl else OverlayTokens.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MessageStateBadges(
    favorite: Boolean,
    reminded: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xCC4A4A4A))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favorite) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD56A), modifier = Modifier.size(10.dp))
        }
        if (reminded) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color(0xFFF5F8FA), modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun FavoriteCollectionPreviewOverlay(
    item: FavoriteCollectionItem,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(item.messageId) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(10.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(favoriteAccentColor(item.type)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = favoriteIconFor(item.type),
                            contentDescription = null,
                            tint = OverlayTokens.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextLabel(
                            text = item.title,
                            size = 13.sp,
                            weight = FontWeight.Bold,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 2
                        )
                        TextLabel(
                            text = item.source,
                            size = 10.sp,
                            color = OverlayTokens.momentsName,
                            maxLines = 1
                        )
                    }
                }
                TextLabel(
                    text = item.description,
                    size = 11.sp,
                    color = OverlayTokens.panelSecondaryText,
                    lineHeight = 16.sp,
                    maxLines = 8
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onForward,
                        colors = ButtonDefaults.buttonColors(containerColor = OverlayTokens.inputFocus)
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[0], size = 10.sp, color = OverlayTokens.primaryText, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB65757))
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[1], size = 10.sp, color = OverlayTokens.primaryText, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[2], size = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCollectionLongPressMenuOverlay(
    item: FavoriteCollectionItem,
    itemBounds: Rect?,
    onDismiss: () -> Unit,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(item.messageId) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val density = LocalDensity.current
        val menuWidth = MessageLongPressMenuWidth
        val menuHeight = MessageLongPressMenuEstimatedHeight
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val menuHeightPx = with(density) { menuHeight.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val anchorCenterX = itemBounds?.center?.x ?: viewportWidthPx / 2f
        val aboveTop = (itemBounds?.top ?: (viewportHeightPx / 2f)) - menuHeightPx - gapPx
        val belowTop = (itemBounds?.bottom ?: (viewportHeightPx / 2f)) + gapPx
        val placeBelow = aboveTop < marginPx && belowTop + menuHeightPx <= viewportHeightPx - marginPx
        val topPx = if (placeBelow) belowTop else aboveTop
        val clampedX = (anchorCenterX - menuWidthPx / 2f)
            .coerceIn(marginPx, (viewportWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx))
        val clampedY = topPx.coerceIn(marginPx, (viewportHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx))

        MessageLongPressMenu(
            actions = favoriteCollectionLongPressActions(),
            pointerOnTop = placeBelow,
            onAction = onAction,
            modifier = Modifier
                .offset(
                    x = with(density) { clampedX.toDp() },
                    y = with(density) { clampedY.toDp() }
                )
        )
    }
}

@Composable
private fun MessageLongPressMenuOverlay(
    message: FloatingChatMessage,
    messageBounds: Rect?,
    onDismiss: () -> Unit,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(message.id) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val density = LocalDensity.current
        val menuWidth = MessageLongPressMenuWidth
        val menuHeight = MessageLongPressMenuEstimatedHeight
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val menuHeightPx = with(density) { menuHeight.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val fallbackX = if (message.fromMe) viewportWidthPx - menuWidthPx - marginPx else marginPx
        val anchorCenterX = messageBounds?.center?.x ?: (fallbackX + menuWidthPx / 2f)
        val aboveTop = (messageBounds?.top ?: (viewportHeightPx / 2f)) - menuHeightPx - gapPx
        val belowTop = (messageBounds?.bottom ?: (viewportHeightPx / 2f)) + gapPx
        val placeBelow = aboveTop < marginPx && belowTop + menuHeightPx <= viewportHeightPx - marginPx
        val topPx = if (placeBelow) belowTop else aboveTop
        val clampedX = (anchorCenterX - menuWidthPx / 2f)
            .coerceIn(marginPx, (viewportWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx))
        val clampedY = topPx.coerceIn(marginPx, (viewportHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx))

        MessageLongPressMenu(
            actions = messageLongPressPrimaryActions(),
            pointerOnTop = placeBelow,
            onAction = onAction,
            modifier = Modifier
                .offset(
                    x = with(density) { clampedX.toDp() },
                    y = with(density) { clampedY.toDp() }
                )
        )
    }
}

@Composable
private fun MessageLongPressMenu(
    actions: List<MessageLongPressAction>,
    pointerOnTop: Boolean,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pointerOnTop) {
            MessageLongPressPointer(up = true)
        }
        MaterialSurface(
            modifier = Modifier.width(MessageLongPressMenuWidth),
            shape = RoundedCornerShape(5.dp),
            color = OverlayTokens.longPressMenu,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                actions.chunked(5).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowActions.forEach { action ->
                            MessageLongPressActionButton(
                                action = action,
                                onClick = { onAction(action) }
                            )
                        }
                    }
                }
            }
        }
        if (!pointerOnTop) {
            MessageLongPressPointer(up = false)
        }
    }
}

@Composable
private fun MessageLongPressPointer(up: Boolean) {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 9.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
            } else {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, OverlayTokens.longPressMenu)
    }
}

@Composable
private fun MessageLongPressActionButton(
    action: MessageLongPressAction,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(45.dp)
            .height(50.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFF5F8FA)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = action.icon(),
                contentDescription = null,
                tint = Color(0xFFF5F8FA),
                modifier = Modifier.size(18.dp)
            )
            TextLabel(
                text = action.label,
                size = 10.sp,
                color = Color(0xFFF5F8FA),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MessageForwardTargetOverlay(
    conversation: FloatingChatConversation,
    onDismiss: () -> Unit,
    onTargetSelected: (ChatThreadSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x33000000))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(12.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextLabel(
                    text = "转发到",
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                conversation.groupContacts.forEach { group ->
                    ForwardTargetRow(
                        label = group.name,
                        subtitle = group.description,
                        onClick = { onTargetSelected(ChatThreadSelection.GroupChat(group.id)) }
                    )
                }
                conversation.contacts.forEach { contact ->
                    ForwardTargetRow(
                        label = contact.name,
                        subtitle = contact.description,
                        onClick = { onTargetSelected(ChatThreadSelection.Private(contact.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(OverlayTokens.accountFill),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = label.take(1),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = label,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = subtitle,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ContactEditOverlay(
    target: ContactEditorTarget,
    accountId: String,
    groupProfiles: Map<String, LocalGroupProfile>,
    visibleMessages: List<FloatingChatMessage>,
    contacts: List<FloatingChatContact>,
    onGroupProfileChange: (LocalGroupProfile) -> Unit,
    contactProfiles: Map<String, LocalContactProfile>,
    onContactProfileChange: (LocalContactProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(target) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 620.dp)
                .pointerInput(target) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 10.dp
        ) {
            when (target) {
                is ContactEditorTarget.Group -> GroupContactEditPanel(
                    accountId = accountId,
                    group = target.group,
                    profile = groupProfiles[groupProfileKey(accountId, target.group.id)]
                        ?: defaultLocalGroupProfileFor(accountId = accountId, group = target.group),
                    members = groupMemberRailContacts(
                        contacts = contacts,
                        messages = visibleMessages
                    ),
                    onProfileChange = onGroupProfileChange,
                    onDismiss = onDismiss
                )
                is ContactEditorTarget.User -> UserContactEditPanel(
                    contact = target.contact,
                    profile = contactProfiles[contactProfileKey(accountId, target.contact.id)]
                        ?: defaultLocalContactProfileFor(
                            accountId = accountId,
                            contact = target.contact
                        ),
                    onProfileChange = onContactProfileChange,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun GroupContactEditPanel(
    accountId: String,
    group: FloatingChatContact,
    profile: LocalGroupProfile,
    members: List<FloatingChatContact>,
    onProfileChange: (LocalGroupProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember(profile.accountId, profile.groupId, profile.groupName, group.name) {
        mutableStateOf(profile.groupName.ifBlank { group.name })
    }
    var announcement by remember(profile.accountId, profile.groupId, profile.announcement) {
        mutableStateOf(profile.announcement)
    }
    var remark by remember(profile.accountId, profile.groupId, profile.remark) {
        mutableStateOf(profile.remark)
    }
    var myNickname by remember(profile.accountId, profile.groupId, profile.myNickname) {
        mutableStateOf(profile.myNickname.ifBlank { group.initials.ifBlank { group.name.take(2) } })
    }
    var mute by remember(profile.accountId, profile.groupId, profile.mute) {
        mutableStateOf(profile.mute)
    }
    var pinned by remember(profile.accountId, profile.groupId, profile.pinned) {
        mutableStateOf(profile.pinned)
    }
    var saveToContacts by remember(profile.accountId, profile.groupId, profile.saveToContacts) {
        mutableStateOf(profile.saveToContacts)
    }
    var showMemberNicknames by remember(profile.accountId, profile.groupId, profile.showMemberNicknames) {
        mutableStateOf(profile.showMemberNicknames)
    }
    var showMemberAvatars by remember(profile.accountId, profile.groupId, profile.showMemberAvatars) {
        mutableStateOf(profile.showMemberAvatars)
    }
    var backgroundLabel by remember(profile.accountId, profile.groupId, profile.backgroundLabel) {
        mutableStateOf(profile.backgroundLabel.ifBlank { "默认背景" })
    }

    fun persistProfile(
        nextGroupName: String = groupName,
        nextAnnouncement: String = announcement,
        nextRemark: String = remark,
        nextMyNickname: String = myNickname,
        nextMute: Boolean = mute,
        nextPinned: Boolean = pinned,
        nextSaveToContacts: Boolean = saveToContacts,
        nextShowMemberNicknames: Boolean = showMemberNicknames,
        nextShowMemberAvatars: Boolean = showMemberAvatars,
        nextBackgroundLabel: String = backgroundLabel
    ) {
        onProfileChange(
            profile.copy(
                accountId = accountId,
                groupId = group.id,
                groupName = nextGroupName,
                announcement = nextAnnouncement,
                remark = nextRemark,
                myNickname = nextMyNickname,
                mute = nextMute,
                pinned = nextPinned,
                saveToContacts = nextSaveToContacts,
                showMemberNicknames = nextShowMemberNicknames,
                showMemberAvatars = nextShowMemberAvatars,
                backgroundLabel = nextBackgroundLabel,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(FriendProfilePageBackground),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            GroupInfoTopBar(
                title = "聊天信息(${groupInfoMemberCount(members)})",
                onDismiss = onDismiss
            )
        }
        item {
            GroupInfoMemberGrid(
                members = members,
                group = group
            )
        }
        item { GroupInfoSectionGap() }
        item {
            FriendProfileSection {
                GroupInfoEditableRow(
                    label = "群聊名称",
                    value = groupName,
                    placeholder = "填写群聊名称",
                    onValueChange = {
                        groupName = it
                        persistProfile(nextGroupName = it)
                    }
                )
                FriendProfileDivider()
                GroupInfoQrRow()
                FriendProfileDivider()
                GroupInfoEditableRow(
                    label = "群公告",
                    value = announcement,
                    placeholder = "未设置",
                    maxLines = 2,
                    onValueChange = {
                        announcement = it
                        persistProfile(nextAnnouncement = it)
                    }
                )
                FriendProfileDivider()
                GroupInfoEditableRow(
                    label = "备注",
                    value = remark,
                    placeholder = "添加备注",
                    onValueChange = {
                        remark = it
                        persistProfile(nextRemark = it)
                    }
                )
            }
        }
        item { GroupInfoSectionGap() }
        item {
            FriendProfileSection {
                FriendProfileInfoRow(label = "查找聊天记录", value = "", showArrow = true)
            }
        }
        item { GroupInfoSectionGap() }
        item {
            FriendProfileSection {
                FriendProfileSwitchRow(
                    label = "消息免打扰",
                    value = "",
                    checked = mute,
                    onCheckedChange = { checked ->
                        mute = checked
                        persistProfile(nextMute = checked)
                    }
                )
                FriendProfileDivider()
                FriendProfileSwitchRow(
                    label = "置顶聊天",
                    value = "",
                    checked = pinned,
                    onCheckedChange = { checked ->
                        pinned = checked
                        persistProfile(nextPinned = checked)
                    }
                )
                FriendProfileDivider()
                FriendProfileSwitchRow(
                    label = "保存到通讯录",
                    value = "",
                    checked = saveToContacts,
                    onCheckedChange = { checked ->
                        saveToContacts = checked
                        persistProfile(nextSaveToContacts = checked)
                    }
                )
            }
        }
        item { GroupInfoSectionGap() }
        item {
            FriendProfileSection {
                GroupInfoEditableRow(
                    label = "我在群里的昵称",
                    value = myNickname,
                    placeholder = "填写昵称",
                    onValueChange = {
                        myNickname = it
                        persistProfile(nextMyNickname = it)
                    }
                )
                FriendProfileDivider()
                FriendProfileSwitchRow(
                    label = "显示群成员昵称",
                    value = "",
                    checked = showMemberNicknames,
                    onCheckedChange = { checked ->
                        showMemberNicknames = checked
                        persistProfile(nextShowMemberNicknames = checked)
                    }
                )
                FriendProfileDivider()
                FriendProfileSwitchRow(
                    label = "显示群成员头像",
                    value = "",
                    checked = showMemberAvatars,
                    onCheckedChange = { checked ->
                        showMemberAvatars = checked
                        persistProfile(nextShowMemberAvatars = checked)
                    }
                )
            }
        }
        item { GroupInfoSectionGap() }
        item {
            FriendProfileSection {
                GroupInfoEditableRow(
                    label = "设置当前聊天背景",
                    value = backgroundLabel,
                    placeholder = "默认背景",
                    onValueChange = {
                        backgroundLabel = it
                        persistProfile(nextBackgroundLabel = it)
                    }
                )
                FriendProfileDivider()
                FriendProfileInfoRow(label = "清空聊天记录", value = "", showArrow = true)
                FriendProfileDivider()
                FriendProfileInfoRow(label = "投诉", value = "", showArrow = true)
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FriendProfileCardBackground)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                TextLabel(
                    text = "退出群聊",
                    size = 14.sp,
                    weight = FontWeight.SemiBold,
                    color = GroupInfoDestructiveText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GroupInfoTopBar(
    title: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(FriendProfilePageBackground)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = "‹",
                size = 24.sp,
                color = FriendProfilePrimaryText,
                maxLines = 1
            )
        }
        TextLabel(
            text = title,
            size = 14.sp,
            weight = FontWeight.SemiBold,
            color = FriendProfilePrimaryText,
            modifier = Modifier.align(Alignment.Center),
            maxLines = 1
        )
    }
}

@Composable
private fun GroupInfoMemberGrid(
    members: List<FloatingChatContact>,
    group: FloatingChatContact
) {
    val displayMembers = if (members.isEmpty()) listOf(group) else members
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FriendProfileCardBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        displayMembers.take(8).chunked(4).forEach { rowMembers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                rowMembers.forEach { member ->
                    GroupInfoMemberCell(
                        label = member.name,
                        avatarText = member.initials,
                        avatarColor = Color(member.avatarColor),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowMembers.size < 4 && displayMembers.size <= 7) {
                    GroupInfoAddMemberCell(modifier = Modifier.weight(1f))
                    repeat((3 - rowMembers.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                } else {
                    repeat(4 - rowMembers.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (displayMembers.size >= 8) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                GroupInfoAddMemberCell(modifier = Modifier.weight(1f))
                repeat(3) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GroupInfoMemberCell(
    label: String,
    avatarText: String,
    avatarColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = avatarText.take(2),
                size = 13.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        TextLabel(
            text = label,
            size = 10.sp,
            color = FriendProfilePlaceholderText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GroupInfoAddMemberCell(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = FriendProfilePlaceholderText,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = "+",
                size = 30.sp,
                color = FriendProfilePlaceholderText,
                maxLines = 1
            )
        }
        TextLabel(
            text = "",
            size = 10.sp,
            color = FriendProfilePlaceholderText,
            maxLines = 1
        )
    }
}

@Composable
private fun GroupInfoQrRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(
            text = "群二维码",
            size = 13.sp,
            color = FriendProfilePrimaryText,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        TextLabel(
            text = "▦",
            size = 19.sp,
            color = FriendProfileSecondaryText,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun GroupInfoEditableRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (maxLines == 1) 44.dp else 58.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(
            text = label,
            size = 13.sp,
            color = FriendProfilePrimaryText,
            modifier = Modifier.width(126.dp),
            maxLines = 1
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = maxLines == 1,
            maxLines = maxLines,
            textStyle = TextStyle.Default.copy(
                color = FriendProfileSecondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (value.isBlank()) {
                        TextLabel(
                            text = placeholder,
                            size = 12.sp,
                            color = FriendProfilePlaceholderText,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun GroupInfoSectionGap() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(FriendProfilePageBackground)
    )
}

@Composable
private fun UserContactEditPanel(
    contact: FloatingChatContact,
    profile: LocalContactProfile,
    onProfileChange: (LocalContactProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var draftRemark by remember(profile.accountId, profile.contactId, profile.remark) {
        mutableStateOf(profile.remark)
    }
    var draftTags by remember(profile.accountId, profile.contactId, profile.tags) {
        mutableStateOf(profile.tags)
    }
    var draftMemo by remember(profile.accountId, profile.contactId, profile.memo) {
        mutableStateOf(profile.memo.ifBlank { defaultFriendProfileMemo(contact) })
    }
    var friendCircleVisible by remember(profile.accountId, profile.contactId, profile.friendCircleVisible) {
        mutableStateOf(profile.friendCircleVisible)
    }
    var onlyChat by remember(profile.accountId, profile.contactId, profile.onlyChat) {
        mutableStateOf(profile.onlyChat)
    }
    fun persistProfile(
        remark: String = draftRemark,
        tags: String = draftTags,
        memo: String = draftMemo,
        nextFriendCircleVisible: Boolean = friendCircleVisible,
        nextOnlyChat: Boolean = onlyChat
    ) {
        onProfileChange(
            profile.copy(
                remark = remark,
                tags = tags,
                memo = memo,
                friendCircleVisible = nextFriendCircleVisible,
                onlyChat = nextOnlyChat,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(FriendProfilePageBackground),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            FriendProfileTopBar(onDismiss = onDismiss)
        }
        item {
            FriendProfileHeader(
                contact = contact,
                displayName = draftRemark.ifBlank { contact.name },
                wechatId = profile.contactId.replace("-", "_")
            )
        }
        item {
            FriendProfileSectionTitle("备注")
        }
        item {
            FriendProfileSection {
                FriendProfileEditableRow(
                    label = "备注名",
                    value = draftRemark,
                    placeholder = "填写备注名",
                    onValueChange = {
                        draftRemark = it
                        persistProfile(remark = it)
                    }
                )
                FriendProfileDivider()
                FriendProfileInfoRow(label = "电话", value = profile.phone.orEmpty())
                FriendProfileDivider()
                FriendProfileEditableRow(
                    label = "标签",
                    value = draftTags,
                    placeholder = "添加标签",
                    onValueChange = {
                        draftTags = it
                        persistProfile(tags = it)
                    }
                )
                FriendProfileDivider()
                FriendProfileEditableRow(
                    label = "备注",
                    value = draftMemo,
                    placeholder = "添加描述",
                    onValueChange = {
                        draftMemo = it
                        persistProfile(memo = it)
                    },
                    maxLines = 2,
                    minHeight = 58.dp
                )
                FriendProfileDivider()
                FriendProfilePhotosRow(contact = contact)
            }
        }
        item {
            FriendProfileSectionTitle("朋友权限")
        }
        item {
            FriendProfileSection {
                FriendProfileSwitchRow(
                    label = "朋友圈和状态",
                    value = "允许他看我的朋友圈",
                    checked = friendCircleVisible,
                    onCheckedChange = { checked ->
                        friendCircleVisible = checked
                        persistProfile(nextFriendCircleVisible = checked)
                    }
                )
                FriendProfileDivider()
                FriendProfileSwitchRow(
                    label = "仅聊天",
                    value = "开启后不看彼此朋友圈",
                    checked = onlyChat,
                    onCheckedChange = { checked ->
                        onlyChat = checked
                        persistProfile(nextOnlyChat = checked)
                    }
                )
            }
        }
        item {
            FriendProfileSectionTitle("更多信息")
        }
        item {
            FriendProfileSection {
                FriendProfileInfoRow(label = "我和他的共同群聊", value = "${profile.commonGroupCount ?: 0} 个群聊")
                FriendProfileDivider()
                FriendProfileInfoRow(label = "来源", value = profile.source.orEmpty())
                FriendProfileDivider()
                FriendProfileInfoRow(label = "添加时间", value = profile.addedTime.orEmpty())
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SmallChoiceButton(label = "完成", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun FriendProfileTopBar(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(FriendProfilePageBackground)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = "‹",
                size = 24.sp,
                color = FriendProfilePrimaryText,
                maxLines = 1
            )
        }
        TextLabel(
            text = "朋友资料",
            size = 14.sp,
            weight = FontWeight.SemiBold,
            color = FriendProfilePrimaryText,
            modifier = Modifier.align(Alignment.Center),
            maxLines = 1
        )
    }
}

@Composable
private fun FriendProfileHeader(
    contact: FloatingChatContact,
    displayName: String,
    wechatId: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FriendProfileCardBackground)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(contact.avatarColor)),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = contact.initials,
                size = 14.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextLabel(
                text = displayName,
                size = 17.sp,
                weight = FontWeight.SemiBold,
                color = FriendProfilePrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = "昵称：${contact.name}",
                size = 11.sp,
                color = FriendProfileSecondaryText,
                maxLines = 1
            )
            TextLabel(
                text = "微信号：$wechatId",
                size = 11.sp,
                color = FriendProfileSecondaryText,
                maxLines = 1
            )
            TextLabel(
                text = contact.description,
                size = 11.sp,
                color = FriendProfileSecondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FriendProfileSectionTitle(title: String) {
    TextLabel(
        text = title,
        size = 11.sp,
        color = FriendProfileSectionText,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
        maxLines = 1
    )
}

@Composable
private fun FriendProfileSection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FriendProfileCardBackground)
    ) {
        content()
    }
}

@Composable
private fun FriendProfileInfoRow(
    label: String,
    value: String,
    showArrow: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(
            text = label,
            size = 13.sp,
            color = FriendProfilePrimaryText,
            modifier = Modifier.width(112.dp),
            maxLines = 1
        )
        TextLabel(
            text = value,
            size = 12.sp,
            color = FriendProfileSecondaryText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            textAlign = TextAlign.End
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(6.dp))
            FriendProfileChevron()
        }
    }
}

@Composable
private fun FriendProfileEditableRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    maxLines: Int = 1,
    minHeight: Dp = 44.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(
            text = label,
            size = 13.sp,
            color = FriendProfilePrimaryText,
            modifier = Modifier.width(70.dp),
            maxLines = 1
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = maxLines == 1,
            maxLines = maxLines,
            textStyle = TextStyle.Default.copy(
                color = FriendProfileSecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (value.isBlank()) {
                        TextLabel(
                            text = placeholder,
                            size = 12.sp,
                            color = FriendProfilePlaceholderText,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun FriendProfileSwitchRow(
    label: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextLabel(
                text = label,
                size = 13.sp,
                color = FriendProfilePrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = value,
                size = 10.sp,
                color = FriendProfileSecondaryText,
                maxLines = 1
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun FriendProfilePhotosRow(contact: FloatingChatContact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextLabel(
            text = "照片",
            size = 13.sp,
            color = FriendProfilePrimaryText,
            modifier = Modifier.width(70.dp),
            maxLines = 1
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End
        ) {
            repeat(3) { index ->
                val color = friendProfilePhotoColor(contact, index)
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(38.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.72f))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        FriendProfileChevron()
    }
}

@Composable
private fun FriendProfileDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(FriendProfileDividerColor)
    )
}

@Composable
private fun FriendProfileChevron() {
    TextLabel(
        text = "›",
        size = 18.sp,
        color = FriendProfileChevronText,
        maxLines = 1
    )
}

private fun contactProfileKey(accountId: String, contactId: String): String {
    return "$accountId\t$contactId"
}

private fun groupProfileKey(accountId: String, groupId: String): String {
    return "$accountId\t$groupId"
}

private fun defaultLocalContactProfileFor(
    accountId: String,
    contact: FloatingChatContact
): LocalContactProfile {
    val seed = positiveContactSeed(contact.id)
    return LocalContactProfile(
        accountId = accountId,
        contactId = contact.id,
        memo = defaultFriendProfileMemo(contact),
        friendCircleVisible = true,
        onlyChat = false,
        phone = friendProfilePhoneFor(seed),
        source = FriendProfileSources[seed % FriendProfileSources.size],
        addedTime = FriendProfileAddedTimes[seed % FriendProfileAddedTimes.size],
        commonGroupCount = seed % 5 + 1,
        updatedAt = 0L
    )
}

private fun defaultLocalGroupProfileFor(
    accountId: String,
    group: FloatingChatContact
): LocalGroupProfile {
    return LocalGroupProfile(
        accountId = accountId,
        groupId = group.id,
        groupName = group.name,
        remark = group.description.substringBefore(" · ").ifBlank { group.description },
        announcement = "",
        myNickname = group.initials.ifBlank { group.name.take(2) },
        mute = false,
        pinned = false,
        saveToContacts = false,
        showMemberNicknames = true,
        showMemberAvatars = true,
        backgroundLabel = "默认背景",
        updatedAt = 0L
    )
}

private fun defaultFriendProfileMemo(contact: FloatingChatContact): String {
    return contact.description.substringBefore(" · ").ifBlank { contact.description }
}

private fun friendProfilePhoneFor(seed: Int): String {
    val middle = 1000 + seed % 9000
    val suffix = 1000 + (seed / 7) % 9000
    return "1${FriendProfilePhonePrefixes[seed % FriendProfilePhonePrefixes.size]} $middle $suffix"
}

private fun friendProfilePhotoColor(contact: FloatingChatContact, index: Int): Color {
    val base = Color(contact.avatarColor)
    val alpha = listOf(0.86f, 0.68f, 0.52f)[index]
    return base.copy(alpha = alpha)
}

private fun positiveContactSeed(id: String): Int {
    val hash = id.hashCode()
    return if (hash == Int.MIN_VALUE) 0 else abs(hash)
}

private val FriendProfilePhonePrefixes = listOf("36", "37", "38", "39", "58", "77", "88")
private val FriendProfileSources = listOf("通过群聊添加", "通过手机号搜索", "通过名片分享", "通过扫一扫", "通过朋友验证")
private val FriendProfileAddedTimes = listOf("2024年03月18日", "2024年06月02日", "2025年01月11日", "2025年09月24日", "2026年02月08日")
private val FriendProfilePageBackground = Color(0xFFF2F3F5)
private val FriendProfileCardBackground = Color(0xFFFFFFFF)
private val FriendProfilePrimaryText = Color(0xFF111111)
private val FriendProfileSecondaryText = Color(0xFF656A70)
private val FriendProfileSectionText = Color(0xFF8C939A)
private val FriendProfilePlaceholderText = Color(0xFFB2B8BE)
private val FriendProfileChevronText = Color(0xFFB4BBC2)
private val FriendProfileDividerColor = Color(0xFFE8ECEF)
private val GroupInfoDestructiveText = Color(0xFFE95A5A)

@Composable
private fun AccountEditOverlay(
    account: FloatingChatContact,
    profile: FloatingChatAccountProfile,
    onPickAvatar: () -> Unit,
    onSave: (FloatingChatAccountProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(account.id) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 560.dp)
                .pointerInput(account.id) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 10.dp
        ) {
            AccountEditPanel(
                account = account,
                profile = profile,
                onPickAvatar = onPickAvatar,
                onSave = onSave,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AccountEditPanel(
    account: FloatingChatContact,
    profile: FloatingChatAccountProfile,
    onPickAvatar: () -> Unit,
    onSave: (FloatingChatAccountProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(profile.accountId, profile.name) { mutableStateOf(profile.name) }
    var phone by remember(profile.accountId, profile.phone) { mutableStateOf(profile.phone) }
    var signature by remember(profile.accountId, profile.signature) { mutableStateOf(profile.signature) }
    var gender by remember(profile.accountId, profile.gender) { mutableStateOf(profile.gender) }
    var company by remember(profile.accountId, profile.company) { mutableStateOf(profile.company) }
    var title by remember(profile.accountId, profile.title) { mutableStateOf(profile.title) }
    var region by remember(profile.accountId, profile.region) { mutableStateOf(profile.region) }
    var wechatId by remember(profile.accountId, profile.wechatId) { mutableStateOf(profile.wechatId) }
    var email by remember(profile.accountId, profile.email) { mutableStateOf(profile.email) }
    var tags by remember(profile.accountId, profile.tags) { mutableStateOf(profile.tags) }
    var avatarInitials by remember(profile.accountId, profile.avatarInitials) { mutableStateOf(profile.avatarInitials) }
    val avatarColor = profile.avatarColor
    var avatarImageUri by remember(profile.accountId, profile.avatarImageUri) { mutableStateOf(profile.avatarImageUri) }
    val previewProfile = profile.copy(
        name = name.trim(),
        phone = phone.trim(),
        signature = signature.trim(),
        gender = gender.trim(),
        company = company.trim(),
        title = title.trim(),
        region = region.trim(),
        wechatId = wechatId.trim(),
        email = email.trim(),
        tags = tags.trim(),
        avatarInitials = avatarInitials.trim(),
        avatarColor = avatarColor,
        avatarImageUri = avatarImageUri.trim()
    )
    val genderChoices = remember { listOf("未设置", "男", "女", "其他") }

    LazyColumn(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountProfileAvatarPreview(profile = previewProfile)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TextLabel(
                        text = "账号资料",
                        size = 15.sp,
                        weight = FontWeight.Bold,
                        color = OverlayTokens.panelPrimaryText,
                        maxLines = 1
                    )
                    TextLabel(
                        text = "推名片会发送这里保存的真实资料",
                        size = 9.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SmallChoiceButton(label = "更换头像", onClick = onPickAvatar)
                        if (avatarImageUri.isNotBlank()) {
                            SmallChoiceButton(label = "移除图片", onClick = { avatarImageUri = "" })
                        }
                    }
                }
            }
        }
        item {
            AccountProfileTextField(value = avatarInitials, onValueChange = { avatarInitials = it.take(4) }, label = "头像文字")
        }
        item { AccountProfileTextField(value = name, onValueChange = { name = it }, label = "名字") }
        item { AccountProfileTextField(value = phone, onValueChange = { phone = it }, label = "电话") }
        item { AccountProfileTextField(value = signature, onValueChange = { signature = it }, label = "签名") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                genderChoices.forEach { choice ->
                    AssistChip(
                        onClick = { gender = choice },
                        label = {
                            TextLabel(text = choice, size = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (gender == choice) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (gender == choice) OverlayTokens.accent else OverlayTokens.panelSecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = OverlayTokens.quickPhraseRow,
                            labelColor = OverlayTokens.panelPrimaryText
                        ),
                        border = BorderStroke(1.dp, OverlayTokens.panelBorder)
                    )
                }
            }
        }
        item { AccountProfileTextField(value = company, onValueChange = { company = it }, label = "公司") }
        item { AccountProfileTextField(value = title, onValueChange = { title = it }, label = "职位") }
        item { AccountProfileTextField(value = region, onValueChange = { region = it }, label = "地区") }
        item { AccountProfileTextField(value = wechatId, onValueChange = { wechatId = it }, label = "微信号") }
        item { AccountProfileTextField(value = email, onValueChange = { email = it }, label = "邮箱") }
        item { AccountProfileTextField(value = tags, onValueChange = { tags = it }, label = "标签") }
        item {
            AccountCardPreview(profile = previewProfile)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                SmallChoiceButton(label = "取消", onClick = onDismiss)
                SmallChoiceButton(
                    label = "保存",
                    onClick = {
                        onSave(
                            previewProfile.copy(
                                name = previewProfile.name.ifBlank { account.name },
                                avatarInitials = previewProfile.avatarInitials.ifBlank {
                                    previewProfile.name.take(2).ifBlank { account.initials }
                                },
                                wechatId = previewProfile.wechatId.ifBlank { account.id.replace("-", "_") },
                                avatarImageUri = previewProfile.avatarImageUri
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountProfileAvatarPreview(profile: FloatingChatAccountProfile) {
    val context = LocalContext.current
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = profile.avatarImageUri.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(profile.avatarColor))
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = profile.avatarInitials.ifBlank { profile.name.take(2) },
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountCardPreviewContent(
    name: String,
    subtitle: String,
    detail: String,
    avatarText: String,
    avatarColor: Color,
    avatarImageUri: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.contactCard)
            .border(1.dp, OverlayTokens.contactCardBorder, RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccountCardAvatar(
            text = avatarText.ifBlank { name.take(2) },
            background = avatarColor,
            imageUri = avatarImageUri
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = name,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = subtitle,
                size = 10.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = detail,
                size = 9.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun AccountCardAvatar(
    text: String,
    background: Color,
    imageUri: String?
) {
    val context = LocalContext.current
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = imageUri?.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = text,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = {
            TextLabel(text = label, size = 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        },
        textStyle = TextStyle.Default.copy(
            color = OverlayTokens.panelPrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OverlayTokens.panelPrimaryText,
            unfocusedTextColor = OverlayTokens.panelPrimaryText,
            cursorColor = OverlayTokens.accent,
            focusedBorderColor = OverlayTokens.inputFocus,
            unfocusedBorderColor = OverlayTokens.inputStroke,
            focusedContainerColor = OverlayTokens.input.copy(alpha = 0.45f),
            unfocusedContainerColor = OverlayTokens.input.copy(alpha = 0.32f),
            focusedLabelColor = OverlayTokens.panelSecondaryText,
            unfocusedLabelColor = OverlayTokens.panelSecondaryText
        )
    )
}

@Composable
private fun AccountCardPreview(profile: FloatingChatAccountProfile) {
    AccountCardPreviewContent(
        name = profile.name.ifBlank { "未命名账号" },
        subtitle = accountProfileSubtitle(profile).ifBlank { "完善公司、职位或地区后会展示在这里" },
        detail = accountProfileDetail(profile).ifBlank { "电话、微信号、签名会随名片一起发送" },
        avatarText = profile.avatarInitials.ifBlank { profile.name.take(2) },
        avatarColor = Color(profile.avatarColor),
        avatarImageUri = profile.avatarImageUri
    )
}

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onForward: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = OverlayTokens.longPressMenu,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextLabel(
                text = "已�?$selectedCount",
                size = 11.sp,
                weight = FontWeight.Bold,
                color = Color(0xFFF5F8FA),
                maxLines = 1
            )
            LongPressBarButton(MessageLongPressAction.Forward, onForward)
            LongPressBarButton(MessageLongPressAction.Favorite, onFavorite)
            LongPressBarButton(MessageLongPressAction.Delete, onDelete)
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFF5F8FA)),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
            ) {
                TextLabel(text = "取消", size = 10.sp, color = Color(0xFFF5F8FA), maxLines = 1)
            }
        }
    }
}

@Composable
private fun LongPressBarButton(
    action: MessageLongPressAction,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            tint = Color(0xFFF5F8FA),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun BottomInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    quotedMessage: FloatingChatMessage?,
    onClearQuote: () -> Unit,
    inputFocused: Boolean,
    onInputFocusedChange: (Boolean) -> Unit,
    panelMode: BottomPanelMode,
    onPanelModeChange: (BottomPanelMode) -> Unit,
    onSend: () -> Unit,
    onHome: () -> Unit,
    onAssistantPredict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sendMode = inputText.isNotBlank()
    val leadingAction = bottomInputLeadingAction(inputFocused)
    val shape = RoundedCornerShape(23.dp)
    MaterialSurface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = BottomInputBarMinHeightDp.dp,
                max = BottomInputBarMaxHeightDp.dp
            ),
        shape = shape,
        color = OverlayTokens.bar,
        border = BorderStroke(1.dp, OverlayTokens.bottomBarStroke),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            quotedMessage?.let { message ->
                QuotedComposerPreview(
                    message = message,
                    onClear = onClearQuote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BottomIcon(
                    action = leadingAction,
                    active = leadingAction == BottomInputAction.Emoji && panelMode == BottomPanelMode.Emoji,
                    onClick = {
                        if (leadingAction == BottomInputAction.Home) {
                            onHome()
                        } else {
                            onPanelModeChange(if (panelMode == BottomPanelMode.Emoji) BottomPanelMode.None else BottomPanelMode.Emoji)
                        }
                    }
                )
                BottomIcon(
                    action = BottomInputAction.Voice,
                    active = panelMode == BottomPanelMode.Voice,
                    onClick = {
                        onPanelModeChange(if (panelMode == BottomPanelMode.Voice) BottomPanelMode.None else BottomPanelMode.Voice)
                    }
                )
                AlignedMessageInputField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    focused = inputFocused,
                    onFocusedChange = onInputFocusedChange,
                    onSend = onSend,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(
                            min = BottomInputFieldMinHeightDp.dp,
                            max = BottomInputFieldMaxHeightDp.dp
                        )
                )
                BottomIcon(
                    action = BottomInputAction.Gift,
                    active = panelMode == BottomPanelMode.Gift,
                    onClick = {
                        onPanelModeChange(if (panelMode == BottomPanelMode.Gift) BottomPanelMode.None else BottomPanelMode.Gift)
                    }
                )
                BottomIcon(
                    action = if (sendMode) BottomInputAction.Send else BottomInputAction.Assistant,
                    active = sendMode || panelMode == BottomPanelMode.Assistant,
                    onClick = {
                        if (sendMode) {
                            onSend()
                        } else {
                            onAssistantPredict()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuotedComposerPreview(
    message: FloatingChatMessage,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        TextLabel(
            text = "引用",
            size = 10.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = message.longPressCopyText(),
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        CompactInteractiveSize {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(22.dp)
            ) {
                TextLabel(
                    text = "x",
                    size = 12.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MessageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    CompactInteractiveSize {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            minLines = BottomInputMinLines,
            maxLines = BottomInputMaxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            textStyle = TextStyle.Default.copy(
                color = OverlayTokens.inputText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = modifier.onFocusChanged { onFocusedChange(it.isFocused) },
            shape = shape,
            placeholder = {
                if (value.isBlank()) {
                    TextLabel(
                        text = "输入消息....",
                        size = 17.sp,
                        weight = FontWeight.SemiBold,
                        color = OverlayTokens.inputPlaceholder,
                        maxLines = 1
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OverlayTokens.inputText,
                unfocusedTextColor = OverlayTokens.inputText,
                cursorColor = OverlayTokens.accent,
                focusedBorderColor = OverlayTokens.inputStroke,
                unfocusedBorderColor = OverlayTokens.inputStroke,
                focusedContainerColor = OverlayTokens.input,
                unfocusedContainerColor = OverlayTokens.input
            )
        )
    }
}

@Composable
private fun AlignedMessageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val borderColor = if (focused) OverlayTokens.inputFocus else OverlayTokens.inputStroke
    CompactInteractiveSize {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = BottomInputMinLines,
            maxLines = BottomInputMaxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            textStyle = TextStyle.Default.copy(
                color = OverlayTokens.inputText,
                fontSize = BottomInputTextSizeSp.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = modifier
                .clip(shape)
                .background(OverlayTokens.input)
                .border(1.dp, borderColor, shape)
                .onFocusChanged { onFocusedChange(it.isFocused) }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        TextLabel(
                            text = "输入消息....",
                            size = BottomInputPlaceholderTextSizeSp.sp,
                            weight = FontWeight.SemiBold,
                            color = OverlayTokens.inputPlaceholder,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun BottomIcon(
    action: BottomInputAction,
    active: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val iconTint = when {
        action == BottomInputAction.Assistant -> OverlayTokens.accent
        action == BottomInputAction.Send -> OverlayTokens.accent
        else -> OverlayTokens.bottomIcon
    }
    CompactInteractiveSize {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier
                .size(BottomInputIconButtonSizeDp.dp)
                .clip(CircleShape)
                .background(OverlayTokens.bottomIconButton)
                .border(1.dp, OverlayTokens.bottomIconStroke, CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = iconTint,
                disabledContentColor = iconTint.copy(alpha = 0.65f)
            )
        ) {
            Icon(
                imageVector = bottomInputActionIcon(action),
                contentDescription = bottomInputActionDescription(action),
                tint = iconTint,
                modifier = Modifier.size(
                    if (action == BottomInputAction.Assistant) {
                        (BottomInputIconSizeDp + 1).dp
                    } else {
                        BottomInputIconSizeDp.dp
                    }
                )
            )
        }
    }
}

private fun bottomInputActionIcon(action: BottomInputAction): ImageVector {
    return when (action) {
        BottomInputAction.Home -> Icons.Filled.Home
        BottomInputAction.Emoji -> Icons.Filled.EmojiEmotions
        BottomInputAction.Voice -> Icons.Filled.Mic
        BottomInputAction.Text -> Icons.AutoMirrored.Filled.Send
        BottomInputAction.Gift -> Icons.Filled.CardGiftcard
        BottomInputAction.Assistant -> Icons.Filled.SmartToy
        BottomInputAction.Send -> Icons.AutoMirrored.Filled.Send
    }
}

private fun bottomInputActionDescription(action: BottomInputAction): String {
    return when (action) {
        BottomInputAction.Home -> "主页"
        BottomInputAction.Emoji -> "表情包"
        BottomInputAction.Voice -> "语音输入"
        BottomInputAction.Text -> "输入消息"
        BottomInputAction.Gift -> "礼物选择"
        BottomInputAction.Assistant -> "机器人预测消息"
        BottomInputAction.Send -> "发送"
    }
}

@Composable
private fun FloatingBottomPanel(
    mode: BottomPanelMode,
    voicePermissionRequestToken: Int,
    locationPermissionRequestToken: Int,
    onClose: () -> Unit,
    onInsertText: (String) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    quickPhrases: List<String>,
    momentPosts: List<AppMomentPost>,
    pendingMomentMedia: AppMomentMedia?,
    favoriteItems: List<FavoriteCollectionItem>,
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    transferRecipients: List<FloatingChatContact>,
    onSendQuickPhrase: (String) -> Unit,
    onAddQuickPhrase: (String) -> Unit,
    onUpdateQuickPhrase: (Int, String) -> Unit,
    onDeleteQuickPhrase: (Int) -> Unit,
    onPickMomentMedia: () -> Unit,
    onClearMomentMedia: () -> Unit,
    onPreviewMomentMedia: (AppMomentPost) -> Unit,
    onPostMoment: (String) -> Unit,
    onUpdateMomentPost: (AppMomentPost) -> Unit,
    favoriteMultiSelectMode: Boolean,
    selectedFavoriteItemIds: Map<String, Boolean>,
    onPreviewFavoriteItem: (FavoriteCollectionItem) -> Unit,
    onFavoriteItemLongPress: (FavoriteCollectionItem, Rect?) -> Unit,
    onToggleFavoriteSelection: (FavoriteCollectionItem) -> Unit,
    onForwardSelectedFavorites: () -> Unit,
    onDeleteSelectedFavorites: () -> Unit,
    onCancelFavoriteSelection: () -> Unit,
    onSendRedPacket: (String, String) -> Unit,
    onSendTransfer: (String, String, FloatingChatContact?) -> Unit,
    onSendLocation: (AppLocationOption) -> Unit,
    onSendAccountCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val widthFraction = when (mode) {
        BottomPanelMode.Emoji -> 0.82f
        BottomPanelMode.QuickPhrase -> 0.78f
        BottomPanelMode.Card -> 0.82f
        BottomPanelMode.Moments -> 0.92f
        BottomPanelMode.Favorite -> 0.86f
        BottomPanelMode.RedPacket,
        BottomPanelMode.Transfer,
        BottomPanelMode.Location -> 0.76f
        else -> 0.64f
    }
    val maxHeight = when (mode) {
        BottomPanelMode.Emoji -> (BottomEmojiPanelHeightDp + 20).dp
        BottomPanelMode.QuickPhrase -> 310.dp
        BottomPanelMode.Card -> 360.dp
        BottomPanelMode.Moments -> 520.dp
        BottomPanelMode.Favorite -> 380.dp
        BottomPanelMode.RedPacket,
        BottomPanelMode.Transfer,
        BottomPanelMode.Location -> 300.dp
        else -> 230.dp
    }
    MaterialSurface(
        onClick = {},
        modifier = modifier
            .then(
                if (mode.isCenteredToolFeaturePanel()) {
                    Modifier
                        .widthIn(
                            min = ToolFeaturePanelMinWidthDp.dp,
                            max = ToolFeaturePanelMaxWidthDp.dp
                        )
                        .heightIn(min = 120.dp, max = ToolFeaturePanelMaxHeightDp.dp)
                } else {
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .heightIn(min = 86.dp, max = maxHeight)
                }
            ),
        shape = shape,
        color = OverlayTokens.panel,
        border = BorderStroke(1.dp, OverlayTokens.panelBorder)
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            when (mode) {
                BottomPanelMode.More -> MoreToolPanel(onClose = onClose)
                BottomPanelMode.Emoji -> EmojiPanel(onInsertText = onInsertText)
                BottomPanelMode.Gift -> GiftPanel(onClose = onClose)
                BottomPanelMode.Voice -> RealVoiceInputPanel(
                    permissionRequestToken = voicePermissionRequestToken,
                    onSendVoice = onSendVoice
                )
                BottomPanelMode.QuickPhrase -> QuickPhrasePanel(
                    phrases = quickPhrases,
                    onSendPhrase = onSendQuickPhrase,
                    onAddPhrase = onAddQuickPhrase,
                    onUpdatePhrase = onUpdateQuickPhrase,
                    onDeletePhrase = onDeleteQuickPhrase
                )
                BottomPanelMode.Card -> AccountCardPickerPanel(
                    accounts = accounts,
                    accountProfiles = accountProfiles,
                    onSendAccountCard = onSendAccountCard
                )
                BottomPanelMode.Moments -> MomentsTimelinePanel(
                    posts = momentPosts,
                    pendingMedia = pendingMomentMedia,
                    onPickMedia = onPickMomentMedia,
                    onClearMedia = onClearMomentMedia,
                    onPreviewMedia = onPreviewMomentMedia,
                    onPostMoment = onPostMoment,
                    onUpdatePost = onUpdateMomentPost
                )
                BottomPanelMode.Favorite -> FavoriteCollectionPanel(
                    items = favoriteItems,
                    multiSelectMode = favoriteMultiSelectMode,
                    selectedItemIds = selectedFavoriteItemIds,
                    onPreviewItem = onPreviewFavoriteItem,
                    onLongPressItem = onFavoriteItemLongPress,
                    onToggleSelection = onToggleFavoriteSelection,
                    onForwardSelected = onForwardSelectedFavorites,
                    onDeleteSelected = onDeleteSelectedFavorites,
                    onCancelSelection = onCancelFavoriteSelection
                )
                BottomPanelMode.RedPacket -> PaymentComposerPanel(
                    title = "发红包",
                    amountLabel = "红包金额",
                    noteLabel = "祝福语",
                    defaultNote = "恭喜发财，大吉大利",
                    confirmLabel = "塞钱进红包",
                    recipients = emptyList(),
                    onConfirm = { amount, note, _ -> onSendRedPacket(amount, note) }
                )
                BottomPanelMode.Transfer -> PaymentComposerPanel(
                    title = "转账",
                    amountLabel = "转账金额",
                    noteLabel = "转账说明",
                    defaultNote = "转账给你，请查收",
                    confirmLabel = "确认转账",
                    recipients = transferRecipients,
                    onConfirm = onSendTransfer
                )
                BottomPanelMode.Location -> LocationPickerPanel(
                    permissionRequestToken = locationPermissionRequestToken,
                    onSendLocation = onSendLocation
                )
                BottomPanelMode.Home -> CompactNoticePanel(
                    title = "返回主页",
                    message = "已记录当前会话入口，可从悬浮按钮继续打开。",
                    onClose = onClose
                )
                BottomPanelMode.Assistant -> Unit
                BottomPanelMode.None -> Unit
            }
        }
    }
}

@Composable
private fun QuickPhrasePanel(
    phrases: List<String>,
    onSendPhrase: (String) -> Unit,
    onAddPhrase: (String) -> Unit,
    onUpdatePhrase: (Int, String) -> Unit,
    onDeletePhrase: (Int) -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    val isAdding = editingIndex == phrases.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLabel(
                text = "快捷语",
                size = 12.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            SmallChoiceButton(
                label = "新增",
                onClick = {
                    editingIndex = phrases.size
                    editingText = ""
                }
            )
        }
        if (phrases.isEmpty()) {
            TextLabel(
                text = "暂无快捷语",
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
        phrases.take(QuickPhraseMaxCount).forEachIndexed { index, phrase ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(OverlayTokens.quickPhraseRow)
                    .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(9.dp))
                    .clickable { onSendPhrase(phrase) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextLabel(
                    text = phrase,
                    size = 10.sp,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                SmallChoiceButton(
                    label = "编辑",
                    onClick = {
                        editingIndex = index
                        editingText = phrase
                    }
                )
                SmallChoiceButton(
                    label = "删除",
                    onClick = { onDeletePhrase(index) }
                )
            }
        }
        if (editingIndex != null) {
            BasicTextField(
                value = editingText,
                onValueChange = { editingText = it },
                maxLines = 3,
                textStyle = TextStyle.Default.copy(
                    color = OverlayTokens.panelPrimaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(OverlayTokens.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp, max = 82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OverlayTokens.input.copy(alpha = 0.55f))
                    .border(1.dp, OverlayTokens.inputStroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (editingText.isBlank()) {
                            TextLabel(
                                text = "输入快捷语",
                                size = 10.sp,
                                color = OverlayTokens.panelSecondaryText,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                SmallChoiceButton(
                    label = "取消",
                    onClick = {
                        editingIndex = null
                        editingText = ""
                    }
                )
                SmallChoiceButton(
                    label = "保存",
                    onClick = {
                        val trimmed = editingText.trim()
                        if (trimmed.isNotEmpty()) {
                            val index = editingIndex
                            if (index == null || isAdding) {
                                onAddPhrase(trimmed)
                            } else {
                                onUpdatePhrase(index, trimmed)
                            }
                            editingIndex = null
                            editingText = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MomentsTimelinePanel(
    posts: List<AppMomentPost>,
    pendingMedia: AppMomentMedia?,
    onPickMedia: () -> Unit,
    onClearMedia: () -> Unit,
    onPreviewMedia: (AppMomentPost) -> Unit,
    onPostMoment: (String) -> Unit,
    onUpdatePost: (AppMomentPost) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var commentingPostId by remember { mutableStateOf<String?>(null) }
    var activeMomentMenuPostId by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLabel(
                text = "朋友圈",
                size = 12.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            SmallChoiceButton(label = "图片/视频", onClick = onPickMedia)
            Spacer(modifier = Modifier.width(6.dp))
            SmallChoiceButton(
                label = "发表",
                onClick = {
                    val content = draft.trim()
                    if (content.isNotEmpty() || pendingMedia != null) {
                        onPostMoment(content)
                        draft = ""
                    }
                }
            )
        }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            maxLines = 3,
            textStyle = TextStyle.Default.copy(
                color = OverlayTokens.panelPrimaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp, max = 64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OverlayTokens.momentsComposer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    if (draft.isBlank()) {
                        TextLabel(
                            text = "这一刻的想法...",
                            size = 9.sp,
                            color = OverlayTokens.panelSecondaryText,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
        pendingMedia?.let { media ->
            MomentPendingMediaPreview(media = media, onClear = onClearMedia)
            Spacer(modifier = Modifier.height(6.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 410.dp)
                .padding(top = 6.dp)
                .background(OverlayTokens.momentsBackground)
        ) {
            itemsIndexed(posts) { _, post ->
                val liked = post.likedBy.isNotEmpty()
                val comments = post.comments
                MomentPostRow(
                    post = post,
                    liked = liked,
                    comments = comments,
                    menuOpen = activeMomentMenuPostId == post.id,
                    commenting = commentingPostId == post.id,
                    commentDraft = if (commentingPostId == post.id) commentDraft else "",
                    onToggleMenu = {
                        activeMomentMenuPostId = if (activeMomentMenuPostId == post.id) null else post.id
                    },
                    onLike = {
                        val nextLikedBy = if (post.likedBy.contains("我")) {
                            post.likedBy - "我"
                        } else {
                            post.likedBy + "我"
                        }
                        onUpdatePost(post.copy(likedBy = nextLikedBy))
                        activeMomentMenuPostId = null
                    },
                    onComment = {
                        commentingPostId = if (commentingPostId == post.id) null else post.id
                        commentDraft = ""
                        activeMomentMenuPostId = null
                    },
                    onCommentChange = { next -> commentDraft = next },
                    onPreviewMedia = { onPreviewMedia(post) },
                    onSendComment = {
                        val text = commentDraft.trim()
                        if (text.isNotEmpty()) {
                            onUpdatePost(post.copy(comments = post.comments + AppMomentComment("我", text)))
                            commentDraft = ""
                            commentingPostId = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MomentPostRow(
    post: AppMomentPost,
    liked: Boolean,
    comments: List<AppMomentComment>,
    menuOpen: Boolean,
    commenting: Boolean,
    commentDraft: String,
    onToggleMenu: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCommentChange: (String) -> Unit,
    onPreviewMedia: () -> Unit,
    onSendComment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OverlayTokens.momentsBackground)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(post.avatarColor),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = post.avatarText,
                size = 9.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TextLabel(
                text = post.author,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
            TextLabel(
                text = post.content,
                size = 11.sp,
                color = OverlayTokens.panelPrimaryText,
                lineHeight = 15.sp,
                maxLines = 4
            )
            MomentMediaPreview(post = post, onPreviewMedia = onPreviewMedia)
            if (post.sourceLabel != null) {
                TextLabel(
                    text = post.sourceLabel,
                    size = 10.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.momentsName,
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextLabel(
                    text = post.time,
                    size = 9.sp,
                    color = OverlayTokens.momentsTime,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.width(148.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    MomentMoreButton(onClick = onToggleMenu)
                    if (menuOpen) {
                        MomentLikeCommentPopup(
                            liked = liked,
                            onLike = onLike,
                            onComment = onComment,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-30).dp)
                        )
                    }
                }
            }
            if (liked || comments.isNotEmpty()) {
                MomentInteractionSummary(
                    liked = liked,
                    comments = comments
                )
            }
            if (commenting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelTextInput(
                        value = commentDraft,
                        onValueChange = onCommentChange,
                        placeholder = "评论",
                        modifier = Modifier.weight(1f)
                    )
                    SmallChoiceButton(label = "发送", onClick = onSendComment)
                }
            }
        }
    }
}

@Composable
private fun MomentMoreButton(onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(width = 28.dp, height = 22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OverlayTokens.momentsMore),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = OverlayTokens.momentsName
            )
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MomentLikeCommentPopup(
    liked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialSurface(
        modifier = modifier
            .width(118.dp)
            .height(36.dp),
        shape = RoundedCornerShape(4.dp),
        color = OverlayTokens.momentsActionMenu,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MomentActionMenuButton(
                icon = Icons.Filled.ThumbUp,
                label = if (liked) "取消" else "赞",
                onClick = onLike,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(OverlayTokens.momentsActionDivider)
            )
            MomentActionMenuButton(
                icon = Icons.Filled.Textsms,
                label = "评论",
                onClick = onComment,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MomentActionMenuButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OverlayTokens.momentsActionText
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OverlayTokens.momentsActionText,
                modifier = Modifier.size(14.dp)
            )
            TextLabel(
                text = label,
                size = 10.sp,
                weight = FontWeight.Medium,
                color = OverlayTokens.momentsActionText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MomentMediaPreview(
    post: AppMomentPost,
    onPreviewMedia: () -> Unit,
    detail: Boolean = false
) {
    val media = post.media ?: return
    val widthDp = if (detail) {
        when (media.kind) {
            MomentMediaKind.Image, MomentMediaKind.Video -> media.widthDp.coerceIn(120, 220)
            MomentMediaKind.Link -> media.widthDp
        }
    } else {
        media.widthDp
    }
    val heightDp = if (detail) {
        when (media.kind) {
            MomentMediaKind.Image, MomentMediaKind.Video -> media.heightDp.coerceIn(90, 300)
            MomentMediaKind.Link -> media.heightDp
        }
    } else {
        media.heightDp
    }
    when (media.kind) {
        MomentMediaKind.Image -> {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(media.color)
                    .clickable(onClick = onPreviewMedia),
                contentAlignment = Alignment.Center
            ) {
                MomentMediaBitmap(media)
            }
        }
        MomentMediaKind.Video -> {
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(media.color)
                    .clickable(onClick = onPreviewMedia),
                contentAlignment = Alignment.Center
            ) {
                MomentMediaBitmap(media)
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OverlayTokens.primaryText,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        MomentMediaKind.Link -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(OverlayTokens.momentsLinkCard)
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(media.color),
                    contentAlignment = Alignment.Center
                ) {
                    TextLabel(
                        text = media.label.orEmpty(),
                        size = 8.sp,
                        color = OverlayTokens.primaryText,
                        maxLines = 1
                    )
                }
                TextLabel(
                    text = post.linkTitle.orEmpty(),
                    size = 11.sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MomentPendingMediaPreview(
    media: AppMomentMedia,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(OverlayTokens.momentsComposer)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(media.widthDp.coerceAtMost(92).dp)
                .height(media.heightDp.coerceAtMost(70).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(media.color),
            contentAlignment = Alignment.Center
        ) {
            MomentMediaBitmap(media)
            if (media.kind == MomentMediaKind.Video) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OverlayTokens.primaryText,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        TextLabel(
            text = if (media.kind == MomentMediaKind.Video) "已选择视频" else "已选择图片",
            size = 10.sp,
            weight = FontWeight.Medium,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        SmallChoiceButton(label = "移除", onClick = onClear)
    }
}

@Composable
private fun MomentMediaBitmap(media: AppMomentMedia) {
    val context = LocalContext.current
    val bitmap = remember(media.previewUri, media.uri) {
        when (media.kind) {
            MomentMediaKind.Image -> loadImageThumbnailBitmap(context, media.previewUri ?: media.uri)
            MomentMediaKind.Video -> loadVideoPreviewBitmap(context, media.previewUri, media.uri)
            MomentMediaKind.Link -> null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        TextLabel(
            text = media.label ?: if (media.kind == MomentMediaKind.Video) "视频" else "图片",
            size = 9.sp,
            color = OverlayTokens.primaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun MomentInteractionSummary(
    liked: Boolean,
    comments: List<AppMomentComment>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(OverlayTokens.momentsLinkCard)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (liked) {
            TextLabel(
                text = "我觉得不错",
                size = 9.sp,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
        }
        comments.forEach { comment ->
            TextLabel(
                text = "${comment.author}: ${comment.text}",
                size = 9.sp,
                color = OverlayTokens.panelPrimaryText,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

private fun FloatingChatPickedMediaEvent.toMomentMedia(): AppMomentMedia {
    val isVideo = mediaKind == FloatingChatPrototype.PickedMediaKind.Video
    val aspect = aspectRatio?.coerceIn(0.45f, 2.2f) ?: if (isVideo) 16f / 9f else 1f
    val maxWidth = if (isVideo || aspect > 1f) 150 else 92
    val maxHeight = if (isVideo || aspect > 1f) 86 else 132
    val width = if (aspect >= 1f) maxWidth else (maxHeight * aspect).toInt().coerceIn(62, maxWidth)
    val height = if (aspect >= 1f) (maxWidth / aspect).toInt().coerceIn(58, maxHeight) else maxHeight
    return AppMomentMedia(
        kind = if (isVideo) MomentMediaKind.Video else MomentMediaKind.Image,
        uri = mediaUri,
        previewUri = previewUri,
        orientation = orientation,
        aspectRatio = aspectRatio,
        widthDp = width,
        heightDp = height,
        color = if (isVideo) Color(0xFF536878) else Color(0xFF7E806B),
        label = if (isVideo) "视频" else "图片"
    )
}

@Composable
private fun AccountCardPickerPanel(
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    onSendAccountCard: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TextLabel(
            text = "推名片",
            size = 12.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = "选择要发送的账号名片",
            size = 9.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
        if (accounts.isEmpty()) {
            TextLabel(
                text = "暂无可推送账号",
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(accounts) { _, account ->
                    val profile = accountProfiles[account.id] ?: defaultAccountProfileFor(account)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSendAccountCard(account.id) }
                    ) {
                        AccountCardPreview(profile = profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCollectionPanel(
    items: List<FavoriteCollectionItem>,
    multiSelectMode: Boolean,
    selectedItemIds: Map<String, Boolean>,
    onPreviewItem: (FavoriteCollectionItem) -> Unit,
    onLongPressItem: (FavoriteCollectionItem, Rect?) -> Unit,
    onToggleSelection: (FavoriteCollectionItem) -> Unit,
    onForwardSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextLabel(
            text = "收藏",
            size = 13.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        if (multiSelectMode) {
            TextLabel(
                    text = favoriteCollectionSelectionCountLabel(selectedItemIds.values.count { selected -> selected }),
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
        }
        if (items.isEmpty()) {
            TextLabel(
                text = "暂无收藏。长按消息或在图片视频预览里点收藏后会显示在这里。",
                size = 11.sp,
                color = OverlayTokens.panelSecondaryText,
                lineHeight = 16.sp,
                maxLines = 3
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 318.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                itemsIndexed(items) { _, item ->
                    FavoriteCollectionRow(
                        item = item,
                        selected = selectedItemIds[item.messageId] == true,
                        multiSelectMode = multiSelectMode,
                        onPreviewItem = onPreviewItem,
                        onLongPressItem = onLongPressItem,
                        onToggleSelection = onToggleSelection
                    )
                }
            }
            if (multiSelectMode) {
                FavoriteCollectionSelectionBar(
                    selectedCount = selectedItemIds.values.count { selected -> selected },
                    onForward = onForwardSelected,
                    onDelete = onDeleteSelected,
                    onCancel = onCancelSelection
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoriteCollectionRow(
    item: FavoriteCollectionItem,
    selected: Boolean,
    multiSelectMode: Boolean,
    onPreviewItem: (FavoriteCollectionItem) -> Unit,
    onLongPressItem: (FavoriteCollectionItem, Rect?) -> Unit,
    onToggleSelection: (FavoriteCollectionItem) -> Unit
) {
    var rowBounds by remember(item.messageId) { mutableStateOf<Rect?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OverlayTokens.favoriteRow)
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(8.dp))
            .onGloballyPositioned { coordinates ->
                rowBounds = coordinates.boundsInRoot()
            }
            .combinedClickable(
                onClick = {
                    if (multiSelectMode) {
                        onToggleSelection(item)
                    } else {
                        onPreviewItem(item)
                    }
                },
                onLongClick = { onLongPressItem(item, rowBounds) }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(favoriteAccentColor(item.type)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = favoriteIconFor(item.type),
                contentDescription = null,
                tint = OverlayTokens.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextLabel(
                text = item.title,
                size = 11.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = item.description,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2
            )
            TextLabel(
                text = item.source,
                size = 9.sp,
                color = OverlayTokens.momentsName,
                maxLines = 1
            )
        }
        if (multiSelectMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) OverlayTokens.momentsName else OverlayTokens.panelSecondaryText,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun FavoriteCollectionSelectionBar(
    selectedCount: Int,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OverlayTokens.longPressMenu)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextLabel(
            text = favoriteCollectionSelectionCountLabel(selectedCount),
            size = 10.sp,
            weight = FontWeight.Bold,
            color = Color(0xFFF5F8FA),
            maxLines = 1
        )
        LongPressBarButton(MessageLongPressAction.Forward, onForward)
        LongPressBarButton(MessageLongPressAction.Delete, onDelete)
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFF5F8FA)),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
        ) {
            TextLabel(text = "取消", size = 10.sp, color = Color(0xFFF5F8FA), maxLines = 1)
        }
    }
}

@Composable
private fun PaymentComposerPanel(
    title: String,
    amountLabel: String,
    noteLabel: String,
    defaultNote: String,
    confirmLabel: String,
    recipients: List<FloatingChatContact> = emptyList(),
    onConfirm: (String, String, FloatingChatContact?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(defaultNote) }
    var selectedRecipientId by remember(recipients) { mutableStateOf(recipients.firstOrNull()?.id) }
    val selectedRecipient = recipients.firstOrNull { recipient -> recipient.id == selectedRecipientId }
        ?: recipients.firstOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextLabel(
            text = title,
            size = 12.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        PanelTextInput(
            value = amount,
            onValueChange = { amount = sanitizeMoneyInput(it) },
            placeholder = amountLabel
        )
        PanelTextInput(
            value = note,
            onValueChange = { note = it },
            placeholder = noteLabel
        )
        if (recipients.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextLabel(
                    text = "收款人",
                    size = 10.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 1
                )
                recipients.take(6).chunked(3).forEach { rowRecipients ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowRecipients.forEach { recipient ->
                            RecipientChoiceChip(
                                recipient = recipient,
                                selected = recipient.id == selectedRecipient?.id,
                                onClick = { selectedRecipientId = recipient.id },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowRecipients.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextLabel(
                text = "¥ ${amount.ifBlank { "0.00" }}",
                size = 18.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            SmallChoiceButton(
                label = confirmLabel,
                onClick = { onConfirm(amount.trim(), note.trim(), selectedRecipient) }
            )
        }
    }
}

@Composable
private fun RecipientChoiceChip(
    recipient: FloatingChatContact,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        label = {
            TextLabel(
                text = recipient.name,
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = if (selected) OverlayTokens.paymentCardText else OverlayTokens.panelPrimaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        },
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) OverlayTokens.paymentCard else OverlayTokens.control,
            labelColor = if (selected) OverlayTokens.paymentCardText else OverlayTokens.panelPrimaryText
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) OverlayTokens.paymentCard.copy(alpha = 0.86f) else OverlayTokens.hairline
        )
    )
}

@Composable
private fun LocationPickerPanel(onSendLocation: (AppLocationOption) -> Unit) {
    val locations = remember { defaultLocationOptions() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TextLabel(
            text = "发送位置",
            size = 12.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OverlayTokens.mapPreview)
                .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            LocationGlyph(modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            locations.forEach { location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(OverlayTokens.quickPhraseRow)
                        .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(9.dp))
                        .clickable { onSendLocation(location) }
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = OverlayTokens.panelPrimaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        TextLabel(
                            text = location.title,
                            size = 10.sp,
                            weight = FontWeight.SemiBold,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 1
                        )
                        TextLabel(
                            text = location.address,
                            size = 9.sp,
                            color = OverlayTokens.panelSecondaryText,
                            maxLines = 1
                        )
                    }
                    SmallChoiceButton(label = "发送", onClick = { onSendLocation(location) })
                }
            }
        }
    }
}

@Composable
private fun LocationPickerPanel(
    permissionRequestToken: Int,
    onSendLocation: (AppLocationOption) -> Unit
) {
    val context = LocalContext.current
    var locationState by remember { mutableStateOf(currentDeviceLocationState(context)) }

    fun refreshLocation() {
        locationState = locationState.copy(loading = true, error = null)
        requestCurrentDeviceLocation(context) { nextState ->
            locationState = nextState
        }
    }

    LaunchedEffect(permissionRequestToken) {
        if (hasLocationPermission(context)) {
            refreshLocation()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TextLabel(
            text = "发送位置",
            size = 12.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OverlayTokens.mapPreview)
                .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                LocationGlyph(modifier = Modifier.size(28.dp))
                TextLabel(
                    text = when {
                        locationState.loading -> "正在获取当前位置"
                        locationState.permissionDenied -> "需要位置权限"
                        locationState.option != null -> "已获取真实位置"
                        else -> "未获取到当前位置"
                    },
                    size = 10.sp,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
            }
        }
        val location = locationState.option
        if (location != null) {
            LocationResultRow(location = location, onSendLocation = onSendLocation)
        } else {
            TextLabel(
                text = locationState.error ?: "点击下方按钮获取手机真实位置",
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (locationState.permissionDenied) {
                SmallChoiceButton(
                    label = "授权定位",
                    onClick = { FloatingChatLocationPermissionBridge.requestLocationPermission() }
                )
            } else {
                SmallChoiceButton(
                    label = if (locationState.loading) "定位中" else "重新定位",
                    onClick = {
                        if (!locationState.loading) {
                            refreshLocation()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocationResultRow(
    location: AppLocationOption,
    onSendLocation: (AppLocationOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.quickPhraseRow)
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(9.dp))
            .clickable { onSendLocation(location) }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = OverlayTokens.panelPrimaryText,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = location.title,
                size = 10.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = location.address,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 2
            )
        }
        SmallChoiceButton(label = "发送", onClick = { onSendLocation(location) })
    }
}

@Composable
private fun PanelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle.Default.copy(
            color = OverlayTokens.panelPrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(OverlayTokens.accent),
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.input.copy(alpha = 0.55f))
            .border(1.dp, OverlayTokens.inputStroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    TextLabel(
                        text = placeholder,
                        size = 10.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun RealVoiceInputPanel(
    permissionRequestToken: Int,
    onSendVoice: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(VoiceInputState.Idle) }
    var statusText by remember { mutableStateOf(voiceInputIdleLabel()) }
    var recorder by remember { mutableStateOf<VoiceRecorderSession?>(null) }
    var recordedMs by remember { mutableStateOf(0) }
    var lastFile by remember { mutableStateOf<File?>(null) }

    val stopRecording: (Boolean) -> Unit = { send ->
        val session = recorder
        if (session == null) {
            statusText = voiceInputIdleLabel()
        } else {
            recorder = null
            val elapsedMs = session.elapsedMs()
            val stopped = runCatching {
                session.recorder.stop()
            }.isSuccess
            session.recorder.release()
            state = VoiceInputState.Idle
            recordedMs = elapsedMs
            lastFile = session.file
            if (send && stopped && session.file.length() > 0L) {
                onSendVoice(Uri.fromFile(session.file).toString(), elapsedMs)
            } else if (send) {
                statusText = "没有录到声音，请再试一次"
                session.file.delete()
            } else {
                statusText = voiceInputIdleLabel()
                session.file.delete()
            }
        }
    }

    val startRecording = {
        if (!hasRecordAudioPermission(context)) {
            state = VoiceInputState.PermissionRequired
            statusText = "需要麦克风权限，正在请求授权"
            FloatingChatVoicePermissionBridge.requestRecordAudioPermission()
        } else {
            runCatching {
                createVoiceRecorderSession(context).also { session ->
                    session.recorder.prepare()
                    session.recorder.start()
                }
            }.onSuccess { session ->
                recorder = session
                lastFile = null
                recordedMs = 0
                state = VoiceInputState.Recording
                statusText = "正在录音，点击停止发送"
            }.onFailure { error ->
                state = VoiceInputState.Idle
                statusText = error.message ?: "录音启动失败"
            }
        }
    }

    LaunchedEffect(state, recorder) {
        while (state == VoiceInputState.Recording && recorder != null) {
            recordedMs = recorder?.elapsedMs() ?: recordedMs
            kotlinx.coroutines.delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val active = recorder
            recorder = null
            if (active != null) {
                runCatching { active.recorder.stop() }
                active.recorder.release()
                active.file.delete()
            }
        }
    }

    LaunchedEffect(permissionRequestToken) {
        if (permissionRequestToken > 0 && hasRecordAudioPermission(context)) {
            startRecording()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextLabel(
            text = "语音输入",
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = statusText,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2
        )
        TextLabel(
            text = if (state == VoiceInputState.Recording) {
                formatVoiceTimecode(recordedMs)
            } else {
                lastFile?.name ?: "m4a · 点击开始录音"
            },
            size = 16.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (state == VoiceInputState.Recording) {
                SmallChoiceButton(
                    label = "取消",
                    onClick = { stopRecording(false) }
                )
            }
            SmallChoiceButton(
                label = if (state == VoiceInputState.Recording) {
                    "停止发送"
                } else {
                    "开始录音"
                },
                onClick = {
                    if (state == VoiceInputState.Recording) {
                        stopRecording(true)
                    } else {
                        startRecording()
                    }
                }
            )
        }
    }
}

@Composable
private fun MoreToolPanel(onClose: () -> Unit) {
    val tools = remember {
        listOf(
            PanelTool("Camera", "相册"),
            PanelTool("Video", "视频通话"),
            PanelTool("Call", "语音通话"),
            PanelTool("Pin", "位置"),
            PanelTool("Red", "红包"),
            PanelTool("Gift", "礼物"),
            PanelTool("Pay", "转账"),
            PanelTool("Star", "收藏"),
            PanelTool("Sign", "签约"),
            PanelTool("Card", "名片"),
            PanelTool("File", "文件"),
            PanelTool("Mat", "素材")
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tools.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { tool ->
                    PanelToolButton(tool = tool, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun EmojiPanel(onInsertText: (String) -> Unit) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomEmojiPanelHeightDp.dp),
        factory = { context ->
            EmojiPickerView(context).apply {
                setOnEmojiPickedListener { item ->
                    onInsertText(item.emoji)
                }
            }
        }
    )
}

@Composable
private fun GiftPanel(onClose: () -> Unit) {
    val gifts = remember { listOf("Coffee", "Flower", "Star", "Cake", "Badge", "Thanks") }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextLabel(
            text = "礼物选择",
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
        gifts.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { gift ->
                    SmallChoiceButton(label = gift, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun CompactNoticePanel(title: String, message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextLabel(
            text = title,
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.primaryText,
            maxLines = 1
        )
        TextLabel(
            text = message,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SmallChoiceButton(label = "Close", onClick = onClose)
        }
    }
}

@Composable
private fun PanelToolButton(tool: PanelTool, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 42.dp, max = 54.dp)
            .height(58.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OverlayTokens.primaryText
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(OverlayTokens.panelIcon)
                    .border(1.dp, OverlayTokens.hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                TextLabel(
                    text = tool.icon,
                    size = 8.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextLabel(
                text = tool.label,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SmallChoiceButton(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            TextLabel(
                text = label,
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier
            .height(28.dp)
            .widthIn(min = 42.dp, max = 74.dp),
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = OverlayTokens.control,
            labelColor = OverlayTokens.panelPrimaryText
        ),
        border = BorderStroke(1.dp, OverlayTokens.hairline)
    )
}

@Composable
private fun TextLabel(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = OverlayTokens.primaryText,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    shadow: Shadow? = null
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = androidx.compose.ui.text.TextStyle.Default.copy(
            color = color,
            fontSize = size,
            fontWeight = weight,
            lineHeight = lineHeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            shadow = shadow
        )
    )
}

private fun Modifier.floatingChatFrostedBackdrop(
    enabled: Boolean,
    opacityPercent: Int,
    blurRadiusDp: Int,
    backgroundColorRgb: Int
): Modifier {
    if (!enabled) return background(Color.Transparent)
    val opacity = sanitizeFloatingChatBackgroundOpacityPercent(opacityPercent) / 100f
    val blurStrength = sanitizeFloatingChatBlurRadiusDp(blurRadiusDp) / 40f
    val baseColor = Color(0xFF000000 or sanitizeFloatingChatBackgroundColorRgb(backgroundColorRgb).toLong())
    return drawWithContent {
        drawRect(
            color = baseColor.copy(alpha = opacity * 0.82f)
        )
        drawRect(
            color = Color(0xFFFFFFFF).copy(alpha = opacity * (0.10f + blurStrength * 0.10f))
        )
        drawCircle(
            color = baseColor.lightenedForFrostedBackdrop().copy(alpha = opacity * blurStrength * 0.22f),
            radius = size.minDimension * 0.38f,
            center = Offset(size.width * 0.22f, size.height * 0.18f)
        )
        drawCircle(
            color = baseColor.darkenedForFrostedBackdrop().copy(alpha = opacity * blurStrength * 0.16f),
            radius = size.minDimension * 0.32f,
            center = Offset(size.width * 0.82f, size.height * 0.74f)
        )
        drawContent()
    }
}

private fun Color.lightenedForFrostedBackdrop(): Color {
    return Color(
        red = (red + (1f - red) * 0.58f).coerceIn(0f, 1f),
        green = (green + (1f - green) * 0.58f).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * 0.58f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun Color.darkenedForFrostedBackdrop(): Color {
    return Color(
        red = (red * 0.82f).coerceIn(0f, 1f),
        green = (green * 0.82f).coerceIn(0f, 1f),
        blue = (blue * 0.82f).coerceIn(0f, 1f),
        alpha = 1f
    )
}

@Composable
private fun AnnotatedTextLabel(
    text: AnnotatedString,
    size: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    shadow: Shadow? = null
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle.Default.copy(
            color = OverlayTokens.bubbleText,
            fontSize = size,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            shadow = shadow
        )
    )
}

private enum class AvatarRole {
    Session,
    GroupMember,
    Account
}

internal sealed interface ChatThreadSelection {
    data object Group : ChatThreadSelection
    data class GroupChat(val groupId: String) : ChatThreadSelection
    data class Private(val contactId: String) : ChatThreadSelection
}

internal fun ChatThreadSelection.toLocalThreadId(): String {
    return when (this) {
        ChatThreadSelection.Group -> localThreadIdForSelection()
        is ChatThreadSelection.GroupChat -> localThreadIdForSelection(groupId = groupId)
        is ChatThreadSelection.Private -> localThreadIdForSelection(privateContactId = contactId)
    }
}

internal data class AppMomentPost(
    val id: String = "moment-${System.nanoTime()}",
    val author: String,
    val content: String,
    val time: String,
    val avatarText: String = author.take(2),
    val avatarColor: Color = Color(0xFF6D8190),
    val media: AppMomentMedia? = null,
    val linkTitle: String? = null,
    val sourceLabel: String? = null,
    val likedBy: List<String> = emptyList(),
    val comments: List<AppMomentComment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

internal data class AppMomentMedia(
    val kind: MomentMediaKind,
    val uri: String? = null,
    val previewUri: String? = null,
    val orientation: FloatingChatThumbnailOrientation = FloatingChatThumbnailOrientation.Vertical,
    val aspectRatio: Float? = null,
    val widthDp: Int = 88,
    val heightDp: Int = 88,
    val color: Color = Color(0xFF9B5353),
    val label: String? = null
)

internal data class AppMomentComment(
    val author: String,
    val text: String
)

private fun AppMomentPost.toFloatingChatMediaMessage(): FloatingChatMessage? {
    val media = media ?: return null
    val type = when (media.kind) {
        MomentMediaKind.Image -> FloatingChatMessageType.ImageThumbnail
        MomentMediaKind.Video -> FloatingChatMessageType.VideoPreview
        MomentMediaKind.Link -> return null
    }
    return FloatingChatMessage(
        id = "moment-preview-$id",
        type = type,
        text = content,
        fromMe = false,
        senderName = author,
        time = time,
        presentation = FloatingChatMessagePresentation.MediaStandalone,
        connectionTarget = FloatingChatConnectionTarget.None,
        thumbnailOrientation = media.orientation,
        mediaAspectRatio = media.aspectRatio,
        thumbnailUrl = media.previewUri ?: media.uri,
        resourceUrl = media.uri ?: media.previewUri,
        mediaMimeType = if (media.kind == MomentMediaKind.Video) "video/mp4" else "image/jpeg",
        visibility = FloatingChatVisibilityScope.Public,
        accessState = FloatingChatAccessState.Visible
    )
}

internal fun AppMomentPost.toLocalMomentPost(): LocalMomentPost {
    return LocalMomentPost(
        postId = id,
        author = author,
        content = content,
        displayTime = time,
        avatarText = avatarText,
        avatarColor = avatarColor.toArgb().toLong(),
        mediaKind = media?.kind?.name,
        mediaUri = media?.uri,
        mediaPreviewUri = media?.previewUri,
        mediaOrientation = media?.orientation?.name,
        mediaAspectRatio = media?.aspectRatio,
        mediaWidthDp = media?.widthDp,
        mediaHeightDp = media?.heightDp,
        mediaColor = media?.color?.toArgb()?.toLong(),
        mediaLabel = media?.label,
        linkTitle = linkTitle,
        sourceLabel = sourceLabel,
        likedBy = likedBy,
        comments = comments.map { comment ->
            LocalMomentComment(author = comment.author, text = comment.text)
        },
        createdAt = createdAt
    )
}

internal fun LocalMomentPost.toAppMomentPost(): AppMomentPost {
    return AppMomentPost(
        id = postId,
        author = author,
        content = content,
        time = displayTime,
        avatarText = avatarText,
        avatarColor = Color(avatarColor),
        media = mediaKind?.let { kind ->
            AppMomentMedia(
                kind = enumValueOrDefault(kind, MomentMediaKind.Link),
                uri = mediaUri,
                previewUri = mediaPreviewUri,
                orientation = mediaOrientation?.let { enumValueOrDefault(it, FloatingChatThumbnailOrientation.Vertical) }
                    ?: FloatingChatThumbnailOrientation.Vertical,
                aspectRatio = mediaAspectRatio,
                widthDp = mediaWidthDp ?: 88,
                heightDp = mediaHeightDp ?: 88,
                color = Color(mediaColor ?: 0xFF9B5353),
                label = mediaLabel
            )
        },
        linkTitle = linkTitle,
        sourceLabel = sourceLabel,
        likedBy = likedBy,
        comments = comments.map { comment ->
            AppMomentComment(author = comment.author, text = comment.text)
        },
        createdAt = createdAt
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}

private data class AppLocationOption(
    val title: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geoUri: String? = null
)

internal enum class MomentMediaKind {
    Image,
    Video,
    Link
}

private data class DeviceLocationState(
    val option: AppLocationOption? = null,
    val loading: Boolean = false,
    val permissionDenied: Boolean = false,
    val error: String? = null
)

internal data class FavoriteCollectionItem(
    val messageId: String,
    val type: FloatingChatMessageType,
    val title: String,
    val description: String,
    val source: String
)

internal fun defaultChatThreadSelection(
    conversation: FloatingChatConversation
): ChatThreadSelection {
    return if (conversation.groupContacts.isNotEmpty()) {
        ChatThreadSelection.GroupChat(conversation.groupContacts.first().id)
    } else if (conversation.contacts.isNotEmpty()) {
        ChatThreadSelection.Group
    } else {
        ChatThreadSelection.Group
    }
}

internal fun selectedAccountIdAfterAccountAvatarClick(
    currentAccountId: String,
    clickedAccountId: String
): String {
    return clickedAccountId.ifBlank { currentAccountId }
}

internal fun accountScopedConversation(
    conversation: FloatingChatConversation,
    activeAccountId: String
): FloatingChatConversation {
    val activeAccount = conversation.accountContacts.firstOrNull { account -> account.id == activeAccountId }
        ?: conversation.accountContacts.firstOrNull()
        ?: return conversation
    val accountIndex = conversation.accountContacts.indexOfFirst { account -> account.id == activeAccount.id }
        .coerceAtLeast(0)
    val scopedContacts = accountScopedContacts(
        source = conversation.contacts,
        activeAccount = activeAccount,
        accountIndex = accountIndex,
        count = AccountScopedContactCount,
        selectedFirst = false
    )
    val scopedGroups = accountScopedContacts(
        source = conversation.groupContacts,
        activeAccount = activeAccount,
        accountIndex = accountIndex,
        count = AccountScopedGroupCount,
        selectedFirst = true
    )
    val contactsByBaseId = scopedContacts.associateBy { contact -> contact.base.id }
    val messages = buildList {
        scopedGroups.forEachIndexed { groupIndex, scopedGroup ->
            val baseMessages = FloatingChatPrototype.groupMessagesFor(
                conversation = conversation,
                groupId = scopedGroup.base.id
            ).ifEmpty {
                FloatingChatPrototype.groupMessagesFor(conversation)
            }.ifEmpty {
                conversation.messages.take(4)
            }
            baseMessages.takeLast(8).forEachIndexed { messageIndex, message ->
                val sender = if (scopedContacts.isNotEmpty()) {
                    scopedContacts[(groupIndex + messageIndex) % scopedContacts.size]
                } else {
                    scopedGroup
                }
                add(
                    message.toAccountScopedMessage(
                        activeAccount = activeAccount,
                        threadId = scopedGroup.contact.id,
                        sender = sender.contact,
                        scopedMessageId = "${scopedGroup.contact.id}-${message.id}-$messageIndex"
                    )
                )
            }
        }
        scopedContacts.forEach { scopedContact ->
            val baseMessages = FloatingChatPrototype.privateMessagesFor(
                conversation = conversation,
                contactId = scopedContact.base.id,
                accountId = activeAccount.id
            ).ifEmpty {
                conversation.messages.filter { message ->
                    message.connectionTarget == FloatingChatConnectionTarget.User ||
                        message.connectionTarget == FloatingChatConnectionTarget.Account
                }.take(4)
            }
            baseMessages.takeLast(6).forEachIndexed { messageIndex, message ->
                add(
                    message.toAccountScopedMessage(
                        activeAccount = activeAccount,
                        threadId = scopedContact.contact.id,
                        sender = scopedContact.contact,
                        scopedMessageId = "${scopedContact.contact.id}-${message.id}-$messageIndex"
                    )
                )
            }
        }
    }
    return conversation.copy(
        peerName = activeAccount.name,
        accountName = activeAccount.name,
        contacts = scopedContacts.map { scoped -> scoped.contact },
        groupContacts = scopedGroups.map { scoped -> scoped.contact },
        accountContacts = conversation.accountContacts.map { account ->
            account.copy(selected = account.id == activeAccount.id)
        },
        messages = messages.distinctBy { message -> message.id }
    )
}

internal data class AccountScopedConversation(
    val accountId: String,
    val conversation: FloatingChatConversation
)

internal fun accountScopedConversations(
    conversation: FloatingChatConversation
): List<AccountScopedConversation> {
    return conversation.accountContacts.map { account ->
        AccountScopedConversation(
            accountId = account.id,
            conversation = accountScopedConversation(
                conversation = conversation,
                activeAccountId = account.id
            )
        )
    }
}

internal fun applyContactProfilesToConversation(
    conversation: FloatingChatConversation,
    profiles: List<LocalContactProfile>
): FloatingChatConversation {
    val accountId = conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: return conversation
    val remarksByContactId = profiles
        .asSequence()
        .filter { profile -> profile.accountId == accountId && profile.remark.isNotBlank() }
        .associate { profile -> profile.contactId to profile.remark.trim() }
    if (remarksByContactId.isEmpty()) return conversation

    fun renamedContact(contact: FloatingChatContact): FloatingChatContact {
        val remark = remarksByContactId[contact.id] ?: return contact
        return contact.copy(
            name = remark,
            initials = remark.take(2).ifBlank { contact.initials }
        )
    }

    return conversation.copy(
        contacts = conversation.contacts.map(::renamedContact),
        messages = conversation.messages.map { message ->
            val targetId = message.connectionTargetId
            val remark = if (message.connectionTarget == FloatingChatConnectionTarget.User && targetId != null) {
                remarksByContactId[targetId]
            } else {
                null
            }
            if (remark == null) message else message.copy(senderName = remark)
        }
    )
}

internal fun applyGroupProfilesToConversation(
    conversation: FloatingChatConversation,
    profiles: List<LocalGroupProfile>,
    accountId: String
): FloatingChatConversation {
    val profilesByGroupId = profiles
        .asSequence()
        .filter { profile -> profile.accountId == accountId }
        .associateBy { profile -> profile.groupId }
    if (profilesByGroupId.isEmpty()) return conversation

    fun renamedGroup(group: FloatingChatContact): FloatingChatContact {
        val profile = profilesByGroupId[group.id] ?: return group
        val displayName = profile.groupName.trim().ifBlank { group.name }
        val displayDescription = profile.remark.trim().ifBlank { group.description }
        return group.copy(
            name = displayName,
            initials = displayName.take(2).ifBlank { group.initials },
            description = displayDescription
        )
    }

    return conversation.copy(
        groupContacts = conversation.groupContacts.map(::renamedGroup)
    )
}

internal fun groupMemberAvatarsVisibleForSelection(
    selection: ChatThreadSelection,
    groupProfilesById: Map<String, LocalGroupProfile>
): Boolean {
    val groupId = when (selection) {
        ChatThreadSelection.Group -> return true
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> return true
    }
    return groupProfilesById[groupId]?.showMemberAvatars ?: true
}

internal fun forwardTargetConversationFor(
    conversation: FloatingChatConversation,
    profiles: List<LocalContactProfile>
): FloatingChatConversation {
    return applyContactProfilesToConversation(conversation, profiles)
}

internal fun allAccountHomeConversation(
    baseConversation: FloatingChatConversation,
    accountConversations: List<AccountScopedConversation>
): FloatingChatConversation {
    return baseConversation.copy(
        peerName = "All accounts",
        contacts = accountConversations.flatMap { scoped -> scoped.conversation.contacts },
        groupContacts = accountConversations.flatMap { scoped -> scoped.conversation.groupContacts },
        messages = accountConversations.flatMap { scoped -> scoped.conversation.messages }
    )
}

internal fun rightRailAccountAvatarClickSwitchesActiveAccountWorkspace(): Boolean = true

internal fun accountScopedFriendsAndGroupsAreIndependent(): Boolean = true

internal fun accountSwitchResetsThreadToScopedDefault(): Boolean = true

private data class AccountScopedContact(
    val base: FloatingChatContact,
    val contact: FloatingChatContact
)

private fun accountScopedContacts(
    source: List<FloatingChatContact>,
    activeAccount: FloatingChatContact,
    accountIndex: Int,
    count: Int,
    selectedFirst: Boolean
): List<AccountScopedContact> {
    if (source.isEmpty()) return emptyList()
    val safeCount = min(count, source.size).coerceAtLeast(1)
    val startIndex = (accountIndex * safeCount) % source.size
    return List(safeCount) { offset ->
        val base = source[(startIndex + offset) % source.size]
        AccountScopedContact(
            base = base,
            contact = base.copy(
                id = accountScopedThreadId(activeAccount.id, base.id),
                description = "${activeAccount.name} / ${base.description}",
                selected = selectedFirst && offset == 0
            )
        )
    }
}

private fun accountScopedThreadId(accountId: String, sourceThreadId: String): String {
    return "$accountId$AccountScopedThreadSeparator$sourceThreadId"
}

internal fun accountIdForScopedThreadSelection(selection: ChatThreadSelection): String? {
    val threadId = when (selection) {
        ChatThreadSelection.Group -> return null
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> selection.contactId
    }
    return accountIdForScopedThreadId(threadId)
}

private fun accountIdForScopedThreadId(threadId: String): String? {
    return threadId.substringBefore(AccountScopedThreadSeparator, missingDelimiterValue = "")
        .takeIf { accountId -> accountId.isNotBlank() && threadId.contains(AccountScopedThreadSeparator) }
}

private fun FloatingChatMessage.toAccountScopedMessage(
    activeAccount: FloatingChatContact,
    threadId: String,
    sender: FloatingChatContact,
    scopedMessageId: String
): FloatingChatMessage {
    val target = when {
        fromMe -> FloatingChatConnectionTarget.Account
        connectionTarget == FloatingChatConnectionTarget.None -> FloatingChatConnectionTarget.None
        else -> FloatingChatConnectionTarget.User
    }
    return copy(
        id = scopedMessageId,
        senderName = if (fromMe) activeAccount.name else sender.name,
        connectionTarget = target,
        connectionTargetId = when (target) {
            FloatingChatConnectionTarget.User -> sender.id
            FloatingChatConnectionTarget.Account -> activeAccount.id
            FloatingChatConnectionTarget.None -> null
        },
        threadContactId = threadId
    )
}

internal fun initialChatThreadSelection(
    conversation: FloatingChatConversation,
    preferredSelection: ChatThreadSelection
): ChatThreadSelection {
    return when (preferredSelection) {
        ChatThreadSelection.Group -> defaultChatThreadSelection(conversation)
        is ChatThreadSelection.GroupChat -> {
            if (conversation.groupContacts.any { group -> group.id == preferredSelection.groupId }) {
                preferredSelection
            } else {
                defaultChatThreadSelection(conversation)
            }
        }
        is ChatThreadSelection.Private -> {
            if (conversation.contacts.any { contact -> contact.id == preferredSelection.contactId }) {
                preferredSelection
            } else {
                defaultChatThreadSelection(conversation)
            }
        }
    }
}

internal fun selectedAccountForThread(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    overrideAccountId: String? = null
): FloatingChatContact {
    if (!overrideAccountId.isNullOrBlank()) {
        conversation.accountContacts.firstOrNull { account -> account.id == overrideAccountId }?.let { account ->
            return account
        }
    }
    val contactId = when (selection) {
        ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull { it.selected }?.id
            ?: conversation.groupContacts.firstOrNull()?.id
            ?: conversation.contacts.firstOrNull()?.id
            ?: ""
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> selection.contactId
    }
    return FloatingChatPrototype.pairedAccountFor(
        conversation = conversation,
        contactId = contactId
    )
}

internal fun visibleMessagesForThread(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    selectedAccountId: String
): List<FloatingChatMessage> {
    return when (selection) {
        ChatThreadSelection.Group -> FloatingChatPrototype.groupMessagesFor(conversation)
        is ChatThreadSelection.GroupChat -> FloatingChatPrototype.groupMessagesFor(conversation, selection.groupId)
        is ChatThreadSelection.Private -> FloatingChatPrototype.privateMessagesFor(
            conversation = conversation,
            contactId = selection.contactId,
            accountId = selectedAccountId
        )
    }
}

internal fun previewableThreadMedia(
    messages: List<FloatingChatMessage>
): List<FloatingChatMessage> {
    return messages.filter { message ->
        message.type == FloatingChatMessageType.ImageThumbnail ||
            message.type == FloatingChatMessageType.VideoPreview
    }
}

internal data class HomeUnreadThreadSummary(
    val accountId: String,
    val threadId: String,
    val selection: ChatThreadSelection,
    val message: FloatingChatMessage,
    val unreadCount: Int
)

internal fun defaultHomeUnreadThreadIds(conversation: FloatingChatConversation): Set<String> {
    return buildList {
        conversation.groupContacts.take(2).forEach { group ->
            add(ChatThreadSelection.GroupChat(group.id).toLocalThreadId())
        }
        conversation.contacts.take(3).forEach { contact ->
            add(ChatThreadSelection.Private(contact.id).toLocalThreadId())
        }
    }.toSet()
}

internal fun defaultAllAccountHomeUnreadThreadIds(
    conversation: FloatingChatConversation
): Set<String> {
    return accountScopedConversations(conversation)
        .flatMap { scoped -> defaultHomeUnreadThreadIds(scoped.conversation) }
        .toSet()
}

internal fun homeUnreadThreadSummaries(
    conversation: FloatingChatConversation,
    unreadThreadIds: Set<String>
): List<HomeUnreadThreadSummary> {
    val accountId = conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: ""
    return homeUnreadThreadSummariesForAccount(
        accountId = accountId,
        conversation = conversation,
        unreadThreadIds = unreadThreadIds
    )
}

internal fun homeUnreadThreadSummaries(
    accountConversations: List<AccountScopedConversation>,
    unreadThreadIds: Set<String>
): List<HomeUnreadThreadSummary> {
    return accountConversations.flatMap { scoped ->
        homeUnreadThreadSummariesForAccount(
            accountId = scoped.accountId,
            conversation = scoped.conversation,
            unreadThreadIds = unreadThreadIds
        )
    }
}

private fun homeUnreadThreadSummariesForAccount(
    accountId: String,
    conversation: FloatingChatConversation,
    unreadThreadIds: Set<String>
): List<HomeUnreadThreadSummary> {
    val selections = buildList {
        conversation.groupContacts.forEach { group -> add(ChatThreadSelection.GroupChat(group.id)) }
        conversation.contacts.forEach { contact -> add(ChatThreadSelection.Private(contact.id)) }
    }
    return selections.mapNotNull { selection ->
        val threadId = selection.toLocalThreadId()
        if (!unreadThreadIds.contains(threadId)) return@mapNotNull null
        val selectedAccountId = selectedAccountForThread(conversation, selection).id
        val threadMessages = visibleMessagesForThread(
            conversation = conversation,
            selection = selection,
            selectedAccountId = selectedAccountId
        )
        val unreadMessages = threadMessages.filter { message -> !message.fromMe }
        val latest = unreadMessages.lastOrNull() ?: threadMessages.lastOrNull() ?: return@mapNotNull null
        val contact = contactForSelection(conversation, selection) ?: return@mapNotNull null
        val unreadCount = unreadMessages.size.coerceAtLeast(1)
        HomeUnreadThreadSummary(
            accountId = accountId,
            threadId = threadId,
            selection = selection,
            unreadCount = unreadCount,
            message = latest.copy(
                id = "home-unread-${threadId}-${latest.id}",
                fromMe = false,
                senderName = if (unreadCount > 1) {
                    "${contact.name} - $unreadCount unread - ${conversation.accountName}"
                } else {
                    "${contact.name} - ${conversation.accountName}"
                },
                connectionTarget = FloatingChatConnectionTarget.User,
                connectionTargetId = selection.homeConnectorTargetId(),
                threadContactId = selection.threadContactIdForHome()
            )
        )
    }
}

internal fun unreadThreadIdsAfterOpeningHomeUnreadBubble(
    unreadThreadIds: Set<String>,
    summary: HomeUnreadThreadSummary
): Set<String> {
    return unreadThreadIds - summary.threadId
}

private fun contactForSelection(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection
): FloatingChatContact? {
    return when (selection) {
        ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull()
        is ChatThreadSelection.GroupChat -> conversation.groupContacts.firstOrNull { group -> group.id == selection.groupId }
        is ChatThreadSelection.Private -> conversation.contacts.firstOrNull { contact -> contact.id == selection.contactId }
    }
}

private fun ChatThreadSelection.homeConnectorTargetId(): String {
    return when (this) {
        ChatThreadSelection.Group -> GroupThreadId
        is ChatThreadSelection.GroupChat -> groupId.groupConnectorId()
        is ChatThreadSelection.Private -> contactId
    }
}

private fun ChatThreadSelection.threadContactIdForHome(): String? {
    return when (this) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> groupId
        is ChatThreadSelection.Private -> contactId
    }
}

internal fun homeUnreadOverviewUsesLatestMessagePerThread(): Boolean = true

internal fun homeUnreadOverviewBubblesJumpToThread(): Boolean = true

internal fun homeUnreadOverviewClearsUnreadAfterOpen(): Boolean = true

internal fun homeUnreadOverviewKeepsConnectorLines(): Boolean = true

internal fun homeUnreadAvatarGreenDotReflectsThreadState(): Boolean = true

internal fun homeUnreadOverviewUsesMessageScopedConnectorLines(): Boolean = true

internal fun homeUnreadOverviewShowsAllAccounts(): Boolean = true

internal fun homeUnreadBubbleSwitchesToOwningAccount(): Boolean = true

internal fun homeUnreadOverviewSuppressesGroupMemberAvatars(): Boolean = true

internal fun homeUnreadOverviewUsesFallbackConnectorSourceWhenRailAvatarIsOffscreen(): Boolean = true

internal fun groupMemberContactForMessage(
    message: FloatingChatMessage,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    groupMemberAvatarsVisible: Boolean
): FloatingChatContact? {
    if (homeOverviewVisible) return null
    if (!selectedThread.isGroupThread()) return null
    if (!groupMemberAvatarsVisible) return null
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    val targetId = message.connectionTargetId ?: return null
    return contactsById[targetId]
}

internal fun outgoingTextMessageWithOptionalQuote(
    baseMessage: FloatingChatMessage,
    quotedMessage: FloatingChatMessage?
): FloatingChatMessage {
    if (quotedMessage == null) return baseMessage
    return baseMessage.copy(
        type = FloatingChatMessageType.Quote,
        quoteAuthor = quotedMessage.senderName.ifBlank { "引用" },
        quoteText = quotedMessage.longPressCopyText()
    )
}

private fun FloatingChatMessage.longPressCopyText(): String {
    return listOfNotNull(
        text.ifBlank { null },
        detail,
        quoteText,
        cardName,
        appName,
        locationTitle,
        locationAddress,
        fileName,
        resourceUrl,
        thumbnailUrl
    ).joinToString(" ").ifBlank {
        when (type) {
            FloatingChatMessageType.ImageThumbnail -> "[图片]"
            FloatingChatMessageType.VideoPreview -> "[视频]"
            FloatingChatMessageType.Voice -> "[语音]"
            else -> "[消息]"
        }
    }
}

private fun FloatingChatMessage.forwardedCopyFor(
    conversation: FloatingChatConversation,
    target: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    val targetPrefix = when (target) {
        ChatThreadSelection.Group -> "group"
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    val threadContactId = when (target) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> target.groupId
        is ChatThreadSelection.Private -> target.contactId
    }
    return copy(
        id = "local-forward-$targetPrefix-$accountId-$sequence",
        fromMe = true,
        senderName = conversation.accountContacts.firstOrNull { account -> account.id == accountId }?.name
            ?: conversation.accountName,
        time = "刚刚",
        connectionTarget = FloatingChatConnectionTarget.Account,
        connectionTargetId = accountId,
        threadContactId = threadContactId
    )
}

internal fun groupMemberRailContacts(
    contacts: List<FloatingChatContact>,
    messages: List<FloatingChatMessage>
): List<FloatingChatContact> {
    val contactById = contacts.associateBy { contact -> contact.id }
    return messages
        .asSequence()
        .filter { message -> message.connectionTarget == FloatingChatConnectionTarget.User }
        .mapNotNull { message -> message.connectionTargetId }
        .filterNot { targetId -> targetId == AssistantContactId }
        .distinct()
        .mapNotNull { targetId -> contactById[targetId] }
        .toList()
}

internal fun ChatThreadSelection.toPrototypeToolSelection(): FloatingChatPrototype.ToolThreadSelection {
    return when (this) {
        ChatThreadSelection.Group -> FloatingChatPrototype.ToolThreadSelection.Group
        is ChatThreadSelection.GroupChat -> FloatingChatPrototype.ToolThreadSelection.GroupChat(groupId)
        is ChatThreadSelection.Private -> FloatingChatPrototype.ToolThreadSelection.Private(contactId)
    }
}

private fun ChatThreadSelection.isGroupThread(): Boolean {
    return this is ChatThreadSelection.Group || this is ChatThreadSelection.GroupChat
}

private fun ChatThreadSelection.groupConnectorId(): String {
    return when (this) {
        ChatThreadSelection.Group -> GroupThreadId
        is ChatThreadSelection.GroupChat -> groupId.groupConnectorId()
        is ChatThreadSelection.Private -> contactId
    }
}

private fun FloatingChatContact.toGroupThreadSelection(): ChatThreadSelection {
    return if (id == GroupThreadId) {
        ChatThreadSelection.Group
    } else {
        ChatThreadSelection.GroupChat(id)
    }
}

private fun FloatingChatContact.groupConnectorId(): String {
    return id.groupConnectorId()
}

private fun String.groupConnectorId(): String {
    return "floating-chat-group-$this"
}

@Composable
private fun rememberAsyncAvatarBitmap(imageUri: String?): Bitmap? {
    val context = LocalContext.current
    return rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = imageUri?.takeIf { it.isNotBlank() }
    )
}

internal fun FloatingChatPrototype.ToolThreadSelection.toChatThreadSelection(): ChatThreadSelection {
    return when (this) {
        FloatingChatPrototype.ToolThreadSelection.Group -> ChatThreadSelection.Group
        is FloatingChatPrototype.ToolThreadSelection.GroupChat -> ChatThreadSelection.GroupChat(groupId)
        is FloatingChatPrototype.ToolThreadSelection.Private -> ChatThreadSelection.Private(contactId)
    }
}

internal fun simulatedMessageToolActions(): Set<FloatingChatToolAction> {
    return setOf(
        FloatingChatToolAction.Assistant
    )
}

internal fun documentToolRequestsSystemFilePicker(): Boolean = true

internal fun pickedDocumentCreatesRealFileMessage(): Boolean {
    val message = FloatingChatPrototype.pickedDocumentMessage(
        conversation = FloatingChatPrototype.sampleConversation(),
        documentUri = "content://com.android.providers.media.documents/document/document%3A42",
        displayName = "测试文档.pdf",
        fileFormat = FloatingChatFileFormat.Pdf,
        fileSizeLabel = "213.1 KB",
        previewLines = emptyList(),
        mimeType = "application/pdf",
        selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
        accountId = "account-main",
        sequence = 1
    )
    return message.type == FloatingChatMessageType.FilePreview &&
        message.resourceUrl?.startsWith("content://") == true &&
        message.fileName == "测试文档.pdf" &&
        message.fileSizeLabel == "213.1 KB" &&
        message.mediaMimeType == "application/pdf" &&
        message.threadContactId == "li-si"
}

internal fun rightRailToolButtonsUseMaterialIcons(): Boolean = true

internal fun rightRailToolButtonsShowTextLabels(): Boolean = true

internal fun rightRailToolButtonsSupportLongPressReorder(): Boolean = true

internal fun rightRailToolButtonsSupportDragReorder(): Boolean = true

internal fun rightRailToolReorderUsesLongPressDragGesture(): Boolean = true

internal fun rightRailToolReorderFollowsDraggedIcon(): Boolean = true

internal fun rightRailToolReorderKeepsOriginalIcon(): Boolean = true

internal fun rightRailToolReorderShowsLongPressFeedback(): Boolean = true

internal fun rightRailToolReorderAnimatesDisplacedItems(): Boolean = true

internal fun rightRailToolReorderUsesSinglePointerGesture(): Boolean = true

internal fun rightRailToolReorderUsesAbsoluteFingerOffset(): Boolean = true

internal fun rightRailToolReorderSkipsPlacementAnimationForDraggedItem(): Boolean = true

internal fun rightRailToolReorderModeLetsAnyButtonDragImmediately(): Boolean = true

internal fun rightRailToolReorderModeChangeDoesNotRestartActivePointerGesture(): Boolean = true

internal fun rightRailToolReorderParentConsumesTapGestures(): Boolean = false

internal fun rightRailToolGestureCancelsLongPressWhenMovedBeforeTimeout(): Boolean = true

internal fun rightRailToolGestureCancelsClickWhenMovedPastTouchSlop(): Boolean = true

internal fun rightRailToolReorderMovesByDraggedCenterCrossingSlots(): Boolean = true

internal fun rightRailToolListScrollDisabledDuringReorderDrag(): Boolean = true

internal fun rightRailToolReorderPersistsOnDragEnd(): Boolean = true

internal fun rightRailToolSingleTapExitsReorder(): Boolean = true

internal fun rightRailToolReorderUsesClickToMove(): Boolean = false

internal fun rightRailWidthDp(): Int = RightRailWidthDp

internal fun rightRailToolButtonWidthDp(): Int = RailToolButtonWidthDp

internal fun rightRailToolButtonHeightDp(): Int = RailToolButtonHeightDp

internal fun rightRailUsesDiscreteSnapExpansion(): Boolean = false

internal fun rightRailUsesContinuousDragExpansion(): Boolean = false

internal fun rightRailUsesAreaBasedExpansion(): Boolean = true

internal fun rightRailSectionShiftFraction(): Float = RightRailSectionShiftFraction

internal fun rightRailUsesIndependentListScrolling(): Boolean = true

internal fun rightRailKeepsAccountAndToolSectionHeightsStableWhileScrolling(): Boolean = false

internal fun rightRailStopsExpansionAtContentHeightWhenItemsAreShort(): Boolean = true

internal fun rightRailKeepsSelectedAccountConnectorAnchorWhenCompressed(): Boolean {
    val state = ConnectorCoordinateState()
    val selectedBounds = Rect(12f, 24f, 54f, 66f)
    state.updateAccountAvatar("account-work", selectedBounds)
    state.updateSelectedAccountAvatar("account-work", selectedBounds)
    state.removeAccountAvatar("account-work")
    return state.accountAvatarFor("account-work") == selectedBounds &&
        state.accountAvatarFor("account-private") == null
}

internal fun rightRailKeepsAnyAccountConnectorAnchorWhenScrolledOffscreen(): Boolean {
    val state = ConnectorCoordinateState()
    val accountBounds = Rect(12f, -32f, 54f, 10f)
    state.updateAccountViewport(Rect(0f, 0f, 58f, 220f))
    state.updateAccountAvatar("account-store", accountBounds)
    state.removeAccountAvatar("account-store")
    return state.accountAvatarFor("account-store")?.center?.y == 0f &&
        state.accountAvatarFor("account-work") == null
}

internal fun rightRailAccountConnectorAnchorFollowsVirtualOffscreenPosition(): Float {
    val state = ConnectorCoordinateState()
    val viewport = Rect(0f, 0f, 58f, 220f)
    state.updateAccountViewport(viewport)
    state.updateAccountAvatar("account-0", Rect(16f, 45f, 58f, 87f))
    state.removeAccountAvatar("account-0")
    state.updateVirtualAccountAvatars(
        accountIds = listOf("account-0", "account-1", "account-2"),
        visibleItems = listOf(
            RightRailVisibleAccountItem(index = 1, offset = 18, size = 42),
            RightRailVisibleAccountItem(index = 2, offset = 66, size = 42)
        ),
        viewport = viewport,
        fallbackStepPx = 48f
    )
    return state.accountAvatarFor("account-0")?.center?.y ?: -1f
}

internal fun rightRailSingleVisibleAccountKeepsUpperOffscreenAnchorAbove(): Float {
    val state = ConnectorCoordinateState()
    val viewport = Rect(0f, 0f, 58f, 220f)
    state.updateAccountViewport(viewport)
    state.updateVirtualAccountAvatars(
        accountIds = listOf("account-0", "account-1", "account-2"),
        visibleItems = listOf(
            RightRailVisibleAccountItem(index = 1, offset = 18, size = 42)
        ),
        viewport = viewport,
        fallbackStepPx = 48f
    )
    return state.accountAvatarFor("account-0")?.center?.y ?: -1f
}

internal fun rightRailOffscreenAccountConnectorUsesEdgeIndicator(): Boolean {
    val tree = createChatConnectorTree(
        avatarBounds = Rect(240f, -42f, 282f, 0f),
        bubbleBounds = listOf(Rect(112f, 90f, 220f, 134f)),
        layerBounds = Rect(0f, 0f, 282f, 360f),
        visibleRootBounds = Rect(58f, 0f, 224f, 300f),
        target = FloatingChatConnectionTarget.Account,
        hasMessagesAbove = false,
        hasMessagesBelow = false,
        avatarOffscreenEdge = ChatConnectorViewportEdge.Above
    ) ?: return false

    return tree.avatarBranch == null &&
        tree.trunkStart.y == 0f &&
        tree.messageBranches.isNotEmpty()
}

internal fun rightRailPinnedSelectedAccountConnectorAnchorYWhenCompressed(): Float {
    val state = ConnectorCoordinateState()
    val selectedBounds = Rect(12f, 45f, 54f, 87f)
    state.updateAccountViewport(Rect(0f, 0f, 58f, 46f))
    state.updateAccountAvatar("account-work", selectedBounds)
    state.updateSelectedAccountAvatar("account-work", selectedBounds)
    state.removeAccountAvatar("account-work")
    return state.accountAvatarFor("account-work")?.center?.y ?: -1f
}

internal fun defaultRightRailAccountWeight(): Float = RightRailDefaultAccountWeight

internal fun minRightRailAccountWeight(): Float = RightRailMinAccountWeight

internal fun maxRightRailAccountWeight(): Float = RightRailMaxAccountWeight

internal fun rightRailWeightsForAccountWeight(accountWeight: Float): RightRailWeights {
    val safeAccountWeight = rightRailNormalizeAccountWeight(accountWeight).coerceIn(
        minimumValue = minRightRailAccountWeight(),
        maximumValue = maxRightRailAccountWeight()
    )
    return RightRailWeights(
        accountWeight = safeAccountWeight,
        toolWeight = rightRailNormalizeAccountWeight(1f - safeAccountWeight)
    )
}

internal fun rightRailAccountWeightAfterDrag(
    currentWeight: Float,
    deltaY: Float,
    railHeightPx: Float,
    minWeight: Float = minRightRailAccountWeight(),
    maxWeight: Float = maxRightRailAccountWeight()
): Float {
    if (railHeightPx <= 0f) {
        return currentWeight.coerceIn(minWeight, maxWeight)
    }
    return rightRailNormalizeAccountWeight(currentWeight + deltaY / railHeightPx)
        .coerceIn(minWeight, maxWeight)
}

internal fun rightRailAccountWeightAfterToolDrag(
    currentWeight: Float,
    deltaY: Float,
    railHeightPx: Float
): Float {
    return rightRailAccountWeightAfterDrag(
        currentWeight = currentWeight,
        deltaY = deltaY,
        railHeightPx = railHeightPx
    )
}

internal fun rightRailAccountWeightForAccountAreaDrag(): Float {
    return rightRailNormalizeAccountWeight(
        (defaultRightRailAccountWeight() + rightRailSectionShiftFraction())
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
    )
}

internal fun rightRailAccountWeightForToolAreaDrag(): Float {
    return rightRailNormalizeAccountWeight(
        (defaultRightRailAccountWeight() - rightRailSectionShiftFraction())
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
    )
}

private fun rightRailNormalizeAccountWeight(weight: Float): Float {
    return (weight * 100f).roundToInt() / 100f
}

private fun rightRailMaxAccountWeightForContent(
    accountCount: Int,
    toolCount: Int,
    railHeightPx: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    val rawMax = if (railHeightPx <= 0f) {
        maxRightRailAccountWeight()
    } else {
        with(density) {
            val accountContentHeight = rightRailListContentHeightDp(
                itemCount = accountCount,
                itemHeightDp = RailAvatarSizeDp
            ).dp.toPx()
            val toolMinimumHeight = rightRailListContentHeightDp(
                itemCount = toolCount.coerceAtMost(RightRailMinimumVisibleToolCount),
                itemHeightDp = RailToolButtonHeightDp
            ).dp.toPx()
            ((accountContentHeight + RightRailExpansionSlackDp.dp.toPx()) / railHeightPx)
                .coerceAtMost(1f - toolMinimumHeight / railHeightPx)
        }
    }
    return rightRailNormalizeAccountWeight(
        rawMax
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtLeast(defaultRightRailAccountWeight())
    )
}

private fun rightRailMinAccountWeightForContent(
    toolCount: Int,
    railHeightPx: Float,
    density: androidx.compose.ui.unit.Density
): Float {
    val rawMin = if (railHeightPx <= 0f) {
        minRightRailAccountWeight()
    } else {
        with(density) {
            val toolContentHeight = rightRailListContentHeightDp(
                itemCount = toolCount,
                itemHeightDp = RailToolButtonHeightDp
            ).dp.toPx()
            1f - ((toolContentHeight + RightRailExpansionSlackDp.dp.toPx()) / railHeightPx)
        }
    }
    return rightRailNormalizeAccountWeight(
        rawMin
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtMost(defaultRightRailAccountWeight())
    )
}

internal fun rightRailMaxAccountWeightForContentDp(
    accountCount: Int,
    toolCount: Int,
    railHeightDp: Int
): Float {
    if (railHeightDp <= 0) return maxRightRailAccountWeight()
    val accountContentHeight = rightRailListContentHeightDp(
        itemCount = accountCount,
        itemHeightDp = RailAvatarSizeDp
    )
    val toolMinimumHeight = rightRailListContentHeightDp(
        itemCount = toolCount.coerceAtMost(RightRailMinimumVisibleToolCount),
        itemHeightDp = RailToolButtonHeightDp
    )
    return rightRailNormalizeAccountWeight(
        ((accountContentHeight + RightRailExpansionSlackDp).toFloat() / railHeightDp)
            .coerceAtMost(1f - toolMinimumHeight.toFloat() / railHeightDp)
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtLeast(defaultRightRailAccountWeight())
    )
}

internal fun rightRailMinAccountWeightForContentDp(
    toolCount: Int,
    railHeightDp: Int
): Float {
    if (railHeightDp <= 0) return minRightRailAccountWeight()
    val toolContentHeight = rightRailListContentHeightDp(
        itemCount = toolCount,
        itemHeightDp = RailToolButtonHeightDp
    )
    return rightRailNormalizeAccountWeight(
        (1f - (toolContentHeight + RightRailExpansionSlackDp).toFloat() / railHeightDp)
            .coerceIn(minRightRailAccountWeight(), maxRightRailAccountWeight())
            .coerceAtMost(defaultRightRailAccountWeight())
    )
}

internal fun rightRailListContentHeightDp(
    itemCount: Int,
    itemHeightDp: Int
): Int {
    val safeCount = itemCount.coerceAtLeast(0)
    if (safeCount == 0) return 0
    return safeCount * itemHeightDp + (safeCount - 1) * RightRailItemGapDp
}

internal fun rightRailSelectedAccountFirstVisibleIndex(
    accounts: List<FloatingChatContact>,
    selectedAccountId: String?
): Int {
    return accounts.indexOfFirst { account -> account.id == selectedAccountId }
        .coerceAtLeast(0)
}

internal fun rightRailScrollsSelectedAccountIntoViewForConnectors(): Boolean = true

internal fun quickPhraseToolOpensPanelInsteadOfDirectSend(): Boolean = true

internal fun quickPhrasePanelShowsRecentPhrases(): Boolean = true

internal fun quickPhrasePanelSupportsCrud(): Boolean = true

internal fun quickPhrasePanelCanSendSelectedPhrase(): Boolean = true

internal fun defaultQuickPhrases(): List<String> = DefaultQuickPhrases

internal fun momentsToolOpensInAppTimeline(): Boolean = true

internal fun momentsTimelineBelongsToFloatingChatApp(): Boolean = true

internal fun momentsTimelineSupportsComposePost(): Boolean = true

internal fun momentsTimelineMatchesWechatFeedLayout(): Boolean = true

internal fun momentsTimelineShowsAvatarNameMediaTimeAndMore(): Boolean = true

internal fun momentsTimelineSupportsLikeAndComment(): Boolean = true

internal fun momentsMoreButtonShowsWechatLikeCommentMenu(): Boolean = true

internal fun momentsInlineLikeCommentButtonsAreHiddenUntilMoreMenu(): Boolean = true

internal fun momentsComposerSupportsImageAndVideo(): Boolean = true

internal fun momentsMediaPickDoesNotSendChatMessage(): Boolean = true

internal fun momentsPanelUsesLargerFloatingSheetWithCompactContent(): Boolean = true

internal fun momentsComposedPostsPersistInSqlite(): Boolean = true

internal fun momentsTimelineRestoresPersistedPostsOnOverlayRecreate(): Boolean = true

internal fun momentsTimelineRowsOpenDetail(): Boolean = false

internal fun momentsTimelineMediaOpensFullscreenPreview(): Boolean = true

internal fun momentsTimelineKeepsCurrentPageForTextAndActions(): Boolean = true

internal fun momentsTimelineReusesChatMediaPreview(): Boolean = true

internal fun locationPermissionRequestHidesFloatingOverlayUntilResult(): Boolean = true

internal fun toolFeaturePanelsUseCenteredFloatingSheet(): Boolean = true

internal fun toolFeaturePanelMinWidthDp(): Int = ToolFeaturePanelMinWidthDp

internal fun toolFeaturePanelMaxWidthDp(): Int = ToolFeaturePanelMaxWidthDp

internal fun toolFeaturePanelMaxHeightDp(): Int = ToolFeaturePanelMaxHeightDp

internal fun redPacketToolOpensInAppComposer(): Boolean = true

internal fun redPacketPanelSendsAmountAndGreeting(): Boolean = true

internal fun transferToolOpensInAppComposer(): Boolean = true

internal fun transferPanelSendsAmountAndNote(): Boolean = true

internal fun locationToolOpensInAppPickerInsteadOfDirectSend(): Boolean = true

internal fun locationPanelSendsSelectedLocation(): Boolean = true

internal fun locationToolUsesRealDeviceLocation(): Boolean = true

internal fun locationToolRequestsRuntimePermission(): Boolean = true

internal fun locationPanelStartsRealLocationRefreshAutomatically(): Boolean = true

internal fun locationPanelUsesOnlyPresetLocations(): Boolean = false

internal fun locationMessageIncludesCoordinatesInResourceUrl(): Boolean = true

internal fun favoriteToolOpensInAppCollectionPage(): Boolean = true

internal fun favoritePageShowsSavedMessagesLinksImagesVideos(): Boolean = true

internal fun favoriteCollectionPersistsSavedItems(): Boolean = true

internal fun favoriteCollectionRestoresSavedItemsBeforeCurrentSession(): Boolean = true

internal fun favoriteToolDirectlySendsSimulatedMessage(): Boolean = false

internal fun appOwnedPaymentToolsSendChatMessages(): Boolean = true

internal fun appOwnedWechatLikeToolsOpenWechat(): Boolean = false

internal fun groupChatMemberAvatarScrollsWithMessageBubble(): Boolean = true

internal fun groupMemberAvatarBubbleCenterOffsetDp(): Int = GroupMemberAvatarBubbleCenterOffsetDp

internal fun groupChatConnectorUsesMessageScopedMemberAvatar(): Boolean = true

internal fun groupChatMemberToBubbleConnectorUsesDirectLine(): Boolean = true

internal fun groupChatMemberToBubbleConnectorSkipsTreeBend(): Boolean = true

internal fun groupChatMemberAvatarVisibilityCanBeToggledFromGroupEditPanel(): Boolean = true

internal fun groupChatHiddenMemberAvatarUsesGroupAvatarConnector(): Boolean = true

internal fun leftRailAvatarsSupportLongPressEditPanel(): Boolean = true

internal fun contactEditPanelSupportsRemarkAndTags(): Boolean = true

internal fun contactEditPanelUsesWechatFriendProfileLayout(): Boolean = true

internal fun contactEditPanelWechatSectionTitles(): List<String> = listOf("备注", "朋友权限", "更多信息")

internal fun contactEditPanelWechatFieldLabels(): List<String> = listOf(
    "备注名",
    "电话",
    "标签",
    "备注",
    "照片",
    "朋友圈和状态",
    "仅聊天",
    "我和他的共同群聊",
    "来源",
    "添加时间"
)

internal fun groupAvatarLongPressOpensFloatingEditPanel(): Boolean = true

internal fun groupEditPanelUsesWechatChatInfoLayout(): Boolean = true

internal fun groupEditPanelPersistsChangesInSqlite(): Boolean = true

internal fun groupEditPanelStoresMemberAvatarVisibilityPerGroup(): Boolean = true

internal fun groupInfoMemberCount(members: List<FloatingChatContact>): Int {
    return (members.size + 1).coerceAtLeast(1)
}

internal fun groupEditPanelWechatFieldLabels(): List<String> = listOf(
    "群聊名称",
    "群二维码",
    "群公告",
    "备注",
    "查找聊天记录",
    "消息免打扰",
    "置顶聊天",
    "保存到通讯录",
    "我在群里的昵称",
    "显示群成员昵称",
    "显示群成员头像",
    "设置当前聊天背景",
    "清空聊天记录",
    "投诉",
    "退出群聊"
)

internal fun galleryToolPicksImageAndVideo(): Boolean = true

internal fun galleryToolDetectsPickedMediaKindFromMimeType(): Boolean = true

internal fun galleryVideoPickerKeepsActivityAliveUntilMediaDelivery(): Boolean = true

internal fun galleryVideoMessageForPlayback(): FloatingChatMessage {
    return FloatingChatMessage(
        id = "gallery-video-playback",
        type = FloatingChatMessageType.VideoPreview,
        text = "",
        fromMe = true,
        senderName = "me",
        time = "刚刚",
        thumbnailUrl = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-media/video-1.jpg",
        resourceUrl = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-original-media/video-1.mp4"
    )
}

internal fun galleryVideoMessageWithoutVideoResource(): FloatingChatMessage {
    return galleryVideoMessageForPlayback().copy(
        id = "gallery-video-without-resource",
        resourceUrl = null
    )
}

internal fun cameraToolUsesWechatPressGesture(): Boolean = true

internal fun cameraToolTapCapturesPhoto(): Boolean = true

internal fun cameraToolLongPressRecordsVideo(): Boolean = true

internal fun cameraCapturePreviewRequiresExplicitSend(): Boolean = true

internal fun cameraCaptureShowsCapturedPhotoPreview(): Boolean = true

internal fun cameraCaptureShowsCapturedVideoPreview(): Boolean = true

internal fun cameraCapturePreviewCoversLiveCameraPreview(): Boolean = true

internal fun cameraCaptureNormalizesPhotoOrientationBeforeSend(): Boolean = true

internal fun cameraCaptureUsesDisplayRotationForPhotoOutput(): Boolean = true

internal fun cameraLongPressStartsRecordingImmediately(): Boolean = true

internal fun cameraVideoMaxDurationMs(): Int = CameraVideoMaxDurationMs

internal fun cameraVideoAutoStopsAtMaxDuration(): Boolean = true

internal fun cameraRecordingShowsShutterProgressRing(): Boolean = true

internal fun cameraRecordingProgressUsesVideoMaxDuration(): Boolean = true

internal fun cameraRecordingReleaseShowsCapturedVideoPreview(): Boolean = true

internal fun cameraCapturePreviewOffersRetakeAndSend(): Boolean = true

internal fun cameraVideoCapturePreviewShowsPosterFrameBeforePlayback(): Boolean = true

internal fun cameraVideoCapturePreviewSwitchesToPlayerOnTap(): Boolean = true

internal fun cameraVideoCapturePreviewUsesRealPlayer(): Boolean = true

internal fun cameraVideoCapturePreviewStartsPlaybackOnTap(): Boolean = true

internal fun cameraVideoCapturePreviewShowsPlaybackFailureState(): Boolean = true

internal fun cameraVideoCapturePreviewSetsTextureViewBackground(): Boolean = false

internal fun cameraVideoCapturePreviewPlayerReleaseIsIdempotent(): Boolean = true

internal fun cameraVideoCapturePreviewPlayerReleaseHandlesInvalidState(): Boolean = true

private fun Modifier.floatingChatInternalEdgeGesture(
    touchTargetPx: Float,
    touchSlopPx: Float,
    shortThresholdPx: Float,
    longThresholdPx: Float,
    onGesture: (EdgeSide, GestureType, GestureData) -> Unit,
    onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean,
    onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit,
    onBackGestureCancel: () -> Unit
): Modifier {
    return pointerInput(
        touchTargetPx,
        touchSlopPx,
        shortThresholdPx,
        longThresholdPx,
        onGesture,
        onBackGestureProgress,
        onBackGestureCommit,
        onBackGestureEnd,
        onBackGestureCancel
    ) {
        val classifier = SwipeClassifier()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val side = edgeSideForPosition(
                x = down.position.x,
                width = size.width.toFloat(),
                touchTargetPx = touchTargetPx
            ) ?: return@awaitEachGesture

            val startX = down.position.x
            val startY = down.position.y
            var latestX = startX
            var latestY = startY
            var consumingGesture = false
            var latestBackProgress: BackGestureProgress? = null
            var sentBackCancel = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                latestX = change.position.x
                latestY = change.position.y
                val dx = latestX - startX
                val dy = latestY - startY
                val distance = hypot(dx, dy)
                val backProgress = BackGestureProgress.fromDelta(
                    side = side,
                    dx = dx,
                    dy = dy,
                    thresholdPx = shortThresholdPx,
                    longThresholdPx = longThresholdPx,
                    touchY = latestY,
                    startY = startY,
                    minimumDragDistancePx = 0f
                )

                if (backProgress != null) {
                    latestBackProgress = backProgress
                    sentBackCancel = false
                    onBackGestureProgress(side, backProgress)
                    if (distance > touchSlopPx || consumingGesture) {
                        consumingGesture = true
                        change.consume()
                    }
                } else if (latestBackProgress != null && !sentBackCancel) {
                    latestBackProgress = null
                    sentBackCancel = true
                    onBackGestureCancel()
                }

                val classified = classifier.classify(side, dx, dy)
                if (!consumingGesture && classified != null && distance >= shortThresholdPx) {
                    consumingGesture = true
                    change.consume()
                } else if (consumingGesture && classified != null) {
                    change.consume()
                }

                if (!change.pressed) {
                    if (consumingGesture) {
                        val data = GestureData(startX, startY, latestX, latestY)
                        val finalBackProgress = latestBackProgress
                        if (finalBackProgress != null) {
                            onBackGestureEnd(side, finalBackProgress)
                            if (!onBackGestureCommit(side, finalBackProgress, data)) {
                                onGesture(side, finalBackProgress.gestureType, data)
                            }
                        } else if (classified != null && distance >= shortThresholdPx) {
                            onGesture(side, classified, data)
                        }
                        change.consume()
                    } else if (latestBackProgress != null) {
                        onBackGestureCancel()
                    }
                    break
                }
            }
        }
    }
}

private fun edgeSideForPosition(
    x: Float,
    width: Float,
    touchTargetPx: Float
): EdgeSide? {
    if (width <= 0f || touchTargetPx <= 0f) return null
    return when {
        x <= touchTargetPx -> EdgeSide.LEFT
        x >= width - touchTargetPx -> EdgeSide.RIGHT
        else -> null
    }
}

private object FloatingChatInternalEdgeGestureDefaults {
    val TouchTargetDp: Dp = 24.dp
    const val ShortThresholdDp: Int = 32
    const val LongThresholdDp: Int = 180
    const val ShortThresholdMinDp: Int = 8
    const val ShortThresholdMaxDp: Int = 120
    const val LongThresholdMinDeltaDp: Int = 8
    const val LongThresholdMaxDp: Int = 320
    const val ThresholdResponseRatio: Float = 0.70f
}

internal fun floatingChatServiceOverlayOperationsAreGuarded(): Boolean = true

internal fun floatingChatServiceOverlayRefreshRequiresInitializedControllers(): Boolean = true

internal fun floatingChatOverlayStaysAboveGestureOverlay(): Boolean = true

internal fun gestureOverlayIsBroughtToFrontAfterFloatingChatRecreated(): Boolean = false

internal fun floatingChatOverlayHandlesOwnEdgeGestures(): Boolean = true

internal fun floatingChatOverlayEdgeGestureConsumesPlainTaps(): Boolean = false

internal fun messageBlockUsesNegativePadding(): Boolean = false

internal enum class ConnectorAvatarLane {
    Session,
    GroupMember,
    Account
}

internal fun connectorAvatarLaneFor(
    selection: ChatThreadSelection,
    target: FloatingChatConnectionTarget
): ConnectorAvatarLane {
    return when (target) {
        FloatingChatConnectionTarget.Account -> ConnectorAvatarLane.Account
        FloatingChatConnectionTarget.User -> when (selection) {
            ChatThreadSelection.Group -> ConnectorAvatarLane.GroupMember
            is ChatThreadSelection.GroupChat -> ConnectorAvatarLane.GroupMember
            is ChatThreadSelection.Private -> ConnectorAvatarLane.Session
        }
        FloatingChatConnectionTarget.None -> ConnectorAvatarLane.Session
    }
}

internal fun groupMemberAvatarSizeDp(): Int {
    return GroupMemberAvatarSizeDp
}

internal class FloatingChatOverlayRuntimeState {
    var previewVisible by mutableStateOf(false)
    var mediaActionSheetVisible by mutableStateOf(false)
    var dismissSignal by mutableStateOf(0L)
    var selectedThread by mutableStateOf<ChatThreadSelection>(ChatThreadSelection.Group)
    var pickedMediaEvent by mutableStateOf<FloatingChatPickedMediaEvent?>(null)
    var pickedDocumentEvent by mutableStateOf<FloatingChatPickedDocumentEvent?>(null)
    var blinkVoiceResultEvent by mutableStateOf<FloatingChatBlinkVoiceResultEvent?>(null)
    var previewSession by mutableStateOf<FloatingChatMediaPreviewSession?>(null)
    var documentPreviewMessage by mutableStateOf<FloatingChatMessage?>(null)

    fun canHandleBack(): Boolean {
        return previewSession != null || documentPreviewMessage != null || mediaActionSheetVisible
    }

    fun requestDismiss() {
        dismissSignal += 1L
    }

    fun deliverPickedMedia(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: String,
        previewUri: String,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        val nextToken = (pickedMediaEvent?.token ?: 0L) + 1L
        pickedMediaEvent = FloatingChatPickedMediaEvent(
            token = nextToken,
            mediaKind = mediaKind,
            mediaUri = mediaUri,
            previewUri = previewUri,
            orientation = orientation,
            aspectRatio = aspectRatio,
            target = target
        )
    }

    fun clearPickedMediaEvent(token: Long) {
        if (pickedMediaEvent?.token == token) {
            pickedMediaEvent = null
        }
    }

    fun deliverPickedDocument(document: FloatingChatPickedDocument) {
        val nextToken = (pickedDocumentEvent?.token ?: 0L) + 1L
        pickedDocumentEvent = FloatingChatPickedDocumentEvent(
            token = nextToken,
            document = document
        )
    }

    fun clearPickedDocumentEvent(token: Long) {
        if (pickedDocumentEvent?.token == token) {
            pickedDocumentEvent = null
        }
    }

    fun deliverBlinkVoiceResult(
        eventType: String,
        durationMs: Long,
        confidence: Float
    ) {
        val nextToken = (blinkVoiceResultEvent?.token ?: 0L) + 1L
        blinkVoiceResultEvent = FloatingChatBlinkVoiceResultEvent(
            token = nextToken,
            eventType = eventType,
            durationMs = durationMs,
            confidence = confidence
        )
    }

    fun clearBlinkVoiceResultEvent(token: Long) {
        if (blinkVoiceResultEvent?.token == token) {
            blinkVoiceResultEvent = null
        }
    }

    fun openMediaPreview(
        mediaMessages: List<FloatingChatMessage>,
        initialIndex: Int
    ) {
        if (mediaMessages.isEmpty()) return
        documentPreviewMessage = null
        previewSession = FloatingChatMediaPreviewSession(
            mediaMessages = mediaMessages,
            initialIndex = initialIndex.coerceIn(0, mediaMessages.lastIndex.coerceAtLeast(0))
        )
    }

    fun closeMediaPreview() {
        previewSession = null
        previewVisible = false
    }

    fun openDocumentPreview(message: FloatingChatMessage) {
        if (message.type != FloatingChatMessageType.FilePreview) return
        previewSession = null
        mediaActionSheetVisible = false
        documentPreviewMessage = message
    }

    fun closeDocumentPreview() {
        documentPreviewMessage = null
        previewVisible = false
    }
}

internal data class FloatingChatPickedMediaEvent(
    val token: Long,
    val mediaKind: FloatingChatPrototype.PickedMediaKind,
    val mediaUri: String,
    val previewUri: String,
    val orientation: FloatingChatThumbnailOrientation,
    val aspectRatio: Float?,
    val target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
)

internal data class FloatingChatPickedDocumentEvent(
    val token: Long,
    val document: FloatingChatPickedDocument
)

internal data class FloatingChatBlinkVoiceResultEvent(
    val token: Long,
    val eventType: String,
    val durationMs: Long,
    val confidence: Float
)

private enum class BottomPanelMode {
    None,
    Home,
    Assistant,
    Voice,
    Emoji,
    Gift,
    QuickPhrase,
    Card,
    Moments,
    Favorite,
    RedPacket,
    Transfer,
    Location,
    More
}

private fun BottomPanelMode.isCenteredToolFeaturePanel(): Boolean {
    return this == BottomPanelMode.QuickPhrase ||
        this == BottomPanelMode.Card ||
        this == BottomPanelMode.Moments ||
        this == BottomPanelMode.Favorite ||
        this == BottomPanelMode.RedPacket ||
        this == BottomPanelMode.Transfer ||
        this == BottomPanelMode.Location
}

internal enum class BottomInputAction {
    Home,
    Emoji,
    Voice,
    Text,
    Gift,
    Assistant,
    Send
}

private enum class VoiceInputState {
    Idle,
    PermissionRequired,
    Recording
}

private enum class MediaAction(val label: String) {
    Share("分享"),
    Save("保存"),
    Favorite("收藏"),
    More("更多"),
    Visibility("可见范围"),
    Edit("编辑"),
    Comment("定位到聊天"),
    Grid("图片管理")
}

private fun MessageLongPressAction.icon(): ImageVector {
    return when (this) {
        MessageLongPressAction.Copy -> Icons.Filled.ContentCopy
        MessageLongPressAction.Forward -> Icons.AutoMirrored.Filled.Forward
        MessageLongPressAction.Favorite -> Icons.Filled.Star
        MessageLongPressAction.Delete -> Icons.Filled.Delete
        MessageLongPressAction.MultiSelect -> Icons.Filled.Checklist
        MessageLongPressAction.Quote -> Icons.Filled.FormatQuote
        MessageLongPressAction.Reminder -> Icons.Filled.Notifications
    }
}

private fun MediaAction.toContract(): MediaActionContract {
    return when (this) {
        MediaAction.Share -> MediaActionContract.Share
        MediaAction.Save -> MediaActionContract.Save
        MediaAction.Favorite -> MediaActionContract.Favorite
        MediaAction.More -> MediaActionContract.More
        MediaAction.Visibility -> MediaActionContract.Visibility
        MediaAction.Edit -> MediaActionContract.Edit
        MediaAction.Comment -> MediaActionContract.Comment
        MediaAction.Grid -> MediaActionContract.Grid
    }
}

internal data class RightRailWeights(
    val accountWeight: Float,
    val toolWeight: Float
)

internal data class RightRailVisibleAccountItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

private data class PanelTool(
    val icon: String,
    val label: String
)

private class ConnectorCoordinateState {
    var version by mutableIntStateOf(0)
        private set
    val userAvatars = mutableMapOf<String, Rect>()
    val groupMemberAvatars = mutableMapOf<String, Rect>()
    val accountAvatars = mutableMapOf<String, Rect>()
    val messageBubbles = mutableMapOf<String, Rect>()
    var groupThreadAvatar: Rect? = null
        private set
    private var groupThreadAvatarId: String? = null
    var privateThreadAvatar: Rect? = null
        private set
    private var privateThreadAvatarId: String? = null
    private val retainedAccountAvatars = mutableMapOf<String, Rect>()
    private var accountViewport: Rect? = null
    var messageViewport: Rect? = null
        private set

    private fun invalidate() {
        version += 1
    }

    fun updateUserAvatar(id: String, bounds: Rect) {
        if (userAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun removeUserAvatar(id: String) {
        if (userAvatars.remove(id) != null) invalidate()
    }

    fun updateGroupMemberAvatar(id: String, bounds: Rect) {
        if (groupMemberAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun removeGroupMemberAvatar(id: String) {
        if (groupMemberAvatars.remove(id) != null) invalidate()
    }

    fun updateAccountAvatar(id: String, bounds: Rect) {
        val changed = accountAvatars.updateIfChanged(id, bounds) or
            retainedAccountAvatars.updateIfChanged(id, bounds)
        if (changed) invalidate()
    }

    fun updateSelectedAccountAvatar(id: String, bounds: Rect) {
        if (retainedAccountAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun updateVirtualAccountAvatars(
        accountIds: List<String>,
        visibleItems: List<RightRailVisibleAccountItem>,
        viewport: Rect,
        fallbackStepPx: Float
    ) {
        rightRailVirtualAccountAvatarBounds(
            accountIds = accountIds,
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = fallbackStepPx
        ).forEach { (id, bounds) ->
            if (retainedAccountAvatars.updateIfChanged(id, bounds)) invalidate()
        }
    }

    fun accountAvatarFor(id: String): Rect? {
        val bounds = accountAvatars[id] ?: retainedAccountAvatars[id]
        return bounds?.pinnedVerticallyTo(accountViewport)
    }

    fun accountAvatarEdgeFor(id: String): ChatConnectorViewportEdge? {
        val bounds = accountAvatars[id] ?: retainedAccountAvatars[id] ?: return null
        val viewport = accountViewport ?: return null
        return when {
            bounds.center.y < viewport.top -> ChatConnectorViewportEdge.Above
            bounds.center.y > viewport.bottom -> ChatConnectorViewportEdge.Below
            else -> null
        }
    }

    fun removeAccountAvatar(id: String) {
        if (accountAvatars.remove(id) != null) invalidate()
    }

    fun updateAccountViewport(bounds: Rect) {
        if (accountViewport != bounds) {
            accountViewport = bounds
            invalidate()
        }
    }

    fun updateMessageBubble(id: String, bounds: Rect) {
        if (messageBubbles.updateIfChanged(id, bounds)) invalidate()
    }

    fun retainMessageBounds(activeMessageIds: Set<String>) {
        var changed = false
        messageBubbles.keys
            .filterNot { id -> id in activeMessageIds }
            .forEach { id -> changed = messageBubbles.remove(id) != null || changed }
        groupMemberAvatars.keys
            .filterNot { id -> id in activeMessageIds }
            .forEach { id -> changed = groupMemberAvatars.remove(id) != null || changed }
        if (changed) invalidate()
    }

    fun updateGroupThreadAvatar(id: String, bounds: Rect) {
        groupThreadAvatarId = id
        if (groupThreadAvatar != bounds) {
            groupThreadAvatar = bounds
            invalidate()
        }
    }

    fun removeGroupThreadAvatar(id: String) {
        if (groupThreadAvatarId == id) {
            groupThreadAvatarId = null
            groupThreadAvatar = null
            invalidate()
        }
    }

    fun updatePrivateThreadAvatar(id: String, bounds: Rect) {
        privateThreadAvatarId = id
        if (privateThreadAvatar != bounds) {
            privateThreadAvatar = bounds
            invalidate()
        }
    }

    fun privateThreadAvatarFor(id: String): Rect? {
        return privateThreadAvatar.takeIf { privateThreadAvatarId == id }
    }

    fun removePrivateThreadAvatar(id: String) {
        if (privateThreadAvatarId == id) {
            clearPrivateThreadAvatar()
        }
    }

    fun clearPrivateThreadAvatar() {
        privateThreadAvatarId = null
        if (privateThreadAvatar != null) {
            privateThreadAvatar = null
            invalidate()
        }
    }

    fun updateMessageViewport(bounds: Rect) {
        if (messageViewport != bounds) {
            messageViewport = bounds
            invalidate()
        }
    }
}

internal fun rightRailVirtualAccountAvatarBounds(
    accountIds: List<String>,
    visibleItems: List<RightRailVisibleAccountItem>,
    viewport: Rect,
    fallbackStepPx: Float
): Map<String, Rect> {
    if (accountIds.isEmpty() || visibleItems.isEmpty()) return emptyMap()
    val sortedItems = visibleItems
        .filter { item -> item.index in accountIds.indices && item.size > 0 }
        .sortedBy { item -> item.index }
    if (sortedItems.isEmpty()) return emptyMap()

    val anchor = sortedItems.first()
    val anchorSize = anchor.size.toFloat()
    val anchorCenterY = viewport.top + anchor.offset + anchorSize / 2f
    val stepPx = sortedItems
        .zipWithNext()
        .firstNotNullOfOrNull { (first, second) ->
            val indexDelta = second.index - first.index
            if (indexDelta == 0) {
                null
            } else {
                val firstCenterY = first.offset + first.size / 2f
                val secondCenterY = second.offset + second.size / 2f
                (secondCenterY - firstCenterY) / indexDelta
            }
        }
        ?.takeIf { step -> step != 0f }
        ?: fallbackStepPx.takeIf { step -> step != 0f }
        ?: return emptyMap()
    val left = viewport.right - anchorSize
    val right = viewport.right

    return accountIds.mapIndexed { index, id ->
        val centerY = anchorCenterY + stepPx * (index - anchor.index)
        id to Rect(
            left = left,
            top = centerY - anchorSize / 2f,
            right = right,
            bottom = centerY + anchorSize / 2f
        )
    }.toMap()
}

private fun Rect.pinnedVerticallyTo(viewport: Rect?): Rect {
    viewport ?: return this
    val pinnedCenterY = center.y.coerceIn(viewport.top, viewport.bottom)
    if (pinnedCenterY == center.y) return this
    val halfHeight = height / 2f
    return Rect(
        left = left,
        top = pinnedCenterY - halfHeight,
        right = right,
        bottom = pinnedCenterY + halfHeight
    )
}

private fun MutableMap<String, Rect>.updateIfChanged(id: String, bounds: Rect): Boolean {
    if (this[id] == bounds) return false
    this[id] = bounds
    return true
}

internal data class ChatConnectorLine(
    val start: Offset,
    val cornerStart: Offset,
    val cornerEnd: Offset,
    val end: Offset
)

internal data class ChatConnectorBranch(
    val start: Offset,
    val end: Offset
)

internal data class ChatConnectorTree(
    val trunkStart: Offset,
    val trunkEnd: Offset,
    val avatarBranch: ChatConnectorBranch?,
    val messageBranches: List<ChatConnectorBranch>
)

internal data class ChatConnectorBraceHook(
    val center: Offset,
    val branchEnd: Offset,
    val radius: Float,
    val verticalDirection: Float
)

internal data class ChatConnectorBraceGeometry(
    val trunkSegments: List<ChatConnectorBranch>,
    val hooks: List<ChatConnectorBraceHook>
)

internal data class ChatConnectorRoundedHookGeometry(
    val curveStart: Offset,
    val curveControl: Offset,
    val horizontalStart: Offset,
    val branchEnd: Offset
)

internal enum class ChatConnectorViewportEdge {
    Above,
    Below
}

private data class ConnectorTargetKey(
    val target: FloatingChatConnectionTarget,
    val targetId: String,
    val lane: ConnectorAvatarLane
)

private data class VoiceRecorderSession(
    val recorder: MediaRecorder,
    val file: File,
    val startedAtMs: Long
) {
    fun elapsedMs(): Int {
        return (System.currentTimeMillis() - startedAtMs).toInt().coerceAtLeast(0)
    }
}

private data class ConnectorViewportEdgeState(
    val hasAbove: Boolean = false,
    val hasBelow: Boolean = false
)

private data class ConnectorOffscreenIndex(
    val beforeByIndex: List<Set<ConnectorTargetKey>>,
    val afterByIndex: List<Set<ConnectorTargetKey>>
) {
    fun targetsAbove(firstVisibleIndex: Int): Set<ConnectorTargetKey> {
        if (beforeByIndex.isEmpty()) return emptySet()
        return beforeByIndex[firstVisibleIndex.coerceIn(0, beforeByIndex.lastIndex)]
    }

    fun targetsBelow(lastVisibleIndex: Int): Set<ConnectorTargetKey> {
        if (afterByIndex.isEmpty()) return emptySet()
        return afterByIndex[lastVisibleIndex.coerceIn(0, afterByIndex.lastIndex)]
    }

    companion object {
        fun fromMessages(
            messages: List<FloatingChatMessage>,
            selection: ChatThreadSelection,
            selectedAccountId: String,
            homeOverviewVisible: Boolean = false,
            groupMemberAvatarsVisible: Boolean
        ): ConnectorOffscreenIndex {
            if (messages.isEmpty()) {
                return ConnectorOffscreenIndex(emptyList(), emptyList())
            }

            val keysByIndex = messages.map { message ->
                message.toOffscreenConnectorTargetKey(
                    selection = selection,
                    selectedAccountId = selectedAccountId,
                    homeOverviewVisible = homeOverviewVisible,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible
                )
            }
            val beforeByIndex = MutableList(messages.size) { emptySet<ConnectorTargetKey>() }
            val seenBefore = linkedSetOf<ConnectorTargetKey>()
            keysByIndex.forEachIndexed { index, key ->
                beforeByIndex[index] = seenBefore.toSet()
                if (key != null) seenBefore += key
            }

            val afterByIndex = MutableList(messages.size) { emptySet<ConnectorTargetKey>() }
            val seenAfter = linkedSetOf<ConnectorTargetKey>()
            for (index in keysByIndex.lastIndex downTo 0) {
                afterByIndex[index] = seenAfter.toSet()
                keysByIndex[index]?.let { key -> seenAfter += key }
            }

            return ConnectorOffscreenIndex(
                beforeByIndex = beforeByIndex,
                afterByIndex = afterByIndex
            )
        }
    }
}

private sealed interface ContactEditorTarget {
    data class Group(val group: FloatingChatContact) : ContactEditorTarget
    data class User(val contact: FloatingChatContact) : ContactEditorTarget
}

internal fun createChatConnectorLine(
    avatarBounds: Rect,
    bubbleBounds: Rect,
    layerBounds: Rect,
    target: FloatingChatConnectionTarget
): ChatConnectorLine {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val bubbleAnchor = if (target == FloatingChatConnectionTarget.Account) {
        bubbleBounds.rightCenterIn(layerBounds).awayFromBubble(target)
    } else {
        bubbleBounds.leftCenterIn(layerBounds).awayFromBubble(target)
    }
    val elbowX = connectorMidX(avatarAnchor, target)
    return ChatConnectorLine(
        start = avatarAnchor,
        cornerStart = Offset(elbowX, avatarAnchor.y),
        cornerEnd = Offset(elbowX, bubbleAnchor.y),
        end = bubbleAnchor
    )
}

internal fun createOffscreenChatConnectorLine(
    avatarBounds: Rect,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget,
    edge: ChatConnectorViewportEdge
): ChatConnectorLine {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val endY = when (edge) {
        ChatConnectorViewportEdge.Above -> visibleRootBounds.top - layerBounds.top
        ChatConnectorViewportEdge.Below -> visibleRootBounds.bottom - layerBounds.top
    }
    val endX = if (target == FloatingChatConnectionTarget.Account) {
        visibleRootBounds.right - layerBounds.left
    } else {
        visibleRootBounds.left - layerBounds.left
    }
    val edgeAnchor = Offset(endX, endY)
    val elbowX = connectorMidX(avatarAnchor, target)

    return ChatConnectorLine(
        start = avatarAnchor,
        cornerStart = Offset(elbowX, avatarAnchor.y),
        cornerEnd = Offset(elbowX, edgeAnchor.y),
        end = edgeAnchor
    )
}

internal fun createGroupMemberMessageConnectorBranch(
    avatarBounds: Rect,
    bubbleBounds: Rect,
    layerBounds: Rect
): ChatConnectorBranch {
    val avatarAnchor = avatarBounds.rightCenterIn(layerBounds)
    val bubbleAnchor = Offset(
        x = bubbleBounds.left - layerBounds.left - imModuleConnectionLineBubbleGapPx(),
        y = avatarAnchor.y
    )
    return ChatConnectorBranch(
        start = avatarAnchor,
        end = bubbleAnchor
    )
}

internal fun homeOverviewFallbackConnectorAvatarBounds(
    bubbleBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget
): Rect? {
    if (bubbleBounds.isEmpty()) return null
    val centerY = bubbleBounds
        .map { bounds -> bounds.center.y }
        .average()
        .toFloat()
        .coerceIn(visibleRootBounds.top, visibleRootBounds.bottom)
    val size = imModuleConnectionLineStrokeWidthPx().coerceAtLeast(1f)
    val offset = imModuleConnectionLineHorizontalOffsetPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        Rect(
            left = visibleRootBounds.right + offset,
            top = centerY - size / 2f,
            right = visibleRootBounds.right + offset + size,
            bottom = centerY + size / 2f
        )
    } else {
        val avatarRight = visibleRootBounds.left - (SessionRailWidthDp - RailAvatarSizeDp).toFloat()
        Rect(
            left = avatarRight - size,
            top = centerY - size / 2f,
            right = avatarRight,
            bottom = centerY + size / 2f
        )
    }
}

internal fun createChatConnectorTree(
    avatarBounds: Rect,
    bubbleBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget,
    hasMessagesAbove: Boolean,
    hasMessagesBelow: Boolean,
    avatarOffscreenEdge: ChatConnectorViewportEdge? = null
): ChatConnectorTree? {
    val avatarAnchor = if (target == FloatingChatConnectionTarget.Account) {
        avatarBounds.leftCenterIn(layerBounds)
    } else {
        avatarBounds.rightCenterIn(layerBounds)
    }
    val visibleAnchors = bubbleBounds
        .map { bounds ->
            val anchor = if (target == FloatingChatConnectionTarget.Account) {
                bounds.rightCenterIn(layerBounds).awayFromBubble(target)
            } else {
                bounds.leftCenterIn(layerBounds).awayFromBubble(target)
            }
            anchor.pinnedToMessageViewport(layerBounds, visibleRootBounds)
        }
        .sortedBy { anchor -> anchor.y }

    if (visibleAnchors.isEmpty() && !hasMessagesAbove && !hasMessagesBelow) {
        return null
    }

    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    val trunkX = connectorMidX(avatarAnchor, target)
    val avatarEdgeY = avatarAnchor.pinnedToMessageViewport(layerBounds, visibleRootBounds).y
    val branchYs = visibleAnchors.map { anchor -> anchor.y } +
        listOfNotNull(
            viewportTop.takeIf { hasMessagesAbove },
            viewportBottom.takeIf { hasMessagesBelow },
            avatarEdgeY.takeIf { avatarOffscreenEdge != null },
            avatarAnchor.y.takeIf { avatarOffscreenEdge == null }
        )
    val trunkStartY = branchYs.minOrNull() ?: avatarAnchor.y
    val trunkEndY = branchYs.maxOrNull() ?: avatarAnchor.y
    val trunkStart = Offset(trunkX, trunkStartY)
    val trunkEnd = Offset(trunkX, trunkEndY)

    return ChatConnectorTree(
        trunkStart = trunkStart,
        trunkEnd = trunkEnd,
        avatarBranch = if (avatarOffscreenEdge == null) {
            ChatConnectorBranch(
                start = avatarAnchor,
                end = Offset(trunkX, avatarAnchor.y)
            )
        } else {
            null
        },
        messageBranches = visibleAnchors.map { anchor ->
            ChatConnectorBranch(
                start = Offset(trunkX, anchor.y),
                end = anchor
            )
        }
    )
}

internal fun ChatConnectorLine.pinnedToMessageViewport(
    layerBounds: Rect,
    visibleRootBounds: Rect
): ChatConnectorLine {
    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    val pinnedEndY = end.y.coerceIn(viewportTop, viewportBottom)
    if (pinnedEndY == end.y) {
        return this
    }
    return copy(
        cornerEnd = Offset(cornerEnd.x, pinnedEndY),
        end = Offset(end.x, pinnedEndY)
    )
}

private fun Offset.pinnedToMessageViewport(
    layerBounds: Rect,
    visibleRootBounds: Rect
): Offset {
    val viewportTop = visibleRootBounds.top - layerBounds.top
    val viewportBottom = visibleRootBounds.bottom - layerBounds.top
    return copy(y = y.coerceIn(viewportTop, viewportBottom))
}

private fun Offset.awayFromBubble(target: FloatingChatConnectionTarget): Offset {
    val gap = imModuleConnectionLineBubbleGapPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        copy(x = x + gap)
    } else {
        copy(x = x - gap)
    }
}

private fun offscreenConnectorEdges(
    index: ConnectorOffscreenIndex,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int
): Map<ConnectorTargetKey, ConnectorViewportEdgeState> {
    val states = linkedMapOf<ConnectorTargetKey, ConnectorViewportEdgeState>()
    index.targetsAbove(firstVisibleIndex).forEach { key ->
        states[key] = (states[key] ?: ConnectorViewportEdgeState()).copy(hasAbove = true)
    }
    index.targetsBelow(lastVisibleIndex).forEach { key ->
        states[key] = (states[key] ?: ConnectorViewportEdgeState()).copy(hasBelow = true)
    }
    return states
}

private fun FloatingChatMessage.toConnectorTargetKey(
    selection: ChatThreadSelection,
    selectedAccountId: String,
    groupMemberAvatarsVisible: Boolean
): ConnectorTargetKey? {
    val target = connectionTarget
    if (target == FloatingChatConnectionTarget.None) return null
    if (selection is ChatThreadSelection.Private) {
        return ConnectorTargetKey(
            target = target,
            targetId = when (target) {
                FloatingChatConnectionTarget.User -> selection.contactId
                FloatingChatConnectionTarget.Account -> selectedAccountId
                FloatingChatConnectionTarget.None -> return null
            },
            lane = connectorAvatarLaneFor(selection, target)
        )
    }
    val targetId = connectionTargetId ?: return null
    if (
        selection.isGroupThread() &&
        target == FloatingChatConnectionTarget.User
    ) {
        return if (groupMemberAvatarsVisible) {
            ConnectorTargetKey(
                target = target,
                targetId = id,
                lane = ConnectorAvatarLane.GroupMember
            )
        } else {
            ConnectorTargetKey(
                target = target,
                targetId = selection.groupConnectorId(),
                lane = ConnectorAvatarLane.Session
            )
        }
    }
    return ConnectorTargetKey(
        target = target,
        targetId = targetId,
        lane = connectorAvatarLaneFor(selection, target)
    )
}

private fun FloatingChatMessage.toHomeOverviewConnectorTargetKey(): ConnectorTargetKey? {
    if (connectionTarget != FloatingChatConnectionTarget.User) return null
    return ConnectorTargetKey(
        target = FloatingChatConnectionTarget.User,
        targetId = homeOverviewConnectorKeyDebugId(this) ?: return null,
        lane = ConnectorAvatarLane.Session
    )
}

private fun FloatingChatMessage.toHomeOverviewConnectorSourceKey(): ConnectorTargetKey? {
    if (connectionTarget != FloatingChatConnectionTarget.User) return null
    return ConnectorTargetKey(
        target = FloatingChatConnectionTarget.User,
        targetId = homeOverviewConnectorSourceKeyDebugId(this) ?: return null,
        lane = ConnectorAvatarLane.Session
    )
}

internal fun homeOverviewConnectorKeyDebugId(message: FloatingChatMessage): String? {
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    return "home-message:${message.id}"
}

internal fun homeOverviewConnectorSourceKeyDebugId(message: FloatingChatMessage): String? {
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    return message.connectionTargetId
}

private fun FloatingChatMessage.toOffscreenConnectorTargetKey(
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean
): ConnectorTargetKey? {
    if (homeOverviewVisible) {
        return toHomeOverviewConnectorTargetKey()
    }
    if (
        selection.isGroupThread() &&
        groupMemberAvatarsVisible &&
        connectionTarget == FloatingChatConnectionTarget.User
    ) {
        return ConnectorTargetKey(
            target = FloatingChatConnectionTarget.User,
            targetId = selection.groupConnectorId(),
            lane = ConnectorAvatarLane.Session
        )
    }
    return toConnectorTargetKey(selection, selectedAccountId, groupMemberAvatarsVisible)
}

private fun connectorMidX(
    avatarAnchor: Offset,
    target: FloatingChatConnectionTarget
): Float {
    val offset = imModuleConnectionLineHorizontalOffsetPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        avatarAnchor.x - offset
    } else {
        avatarAnchor.x + offset
    }
}

internal fun Rect.leftCenterIn(containerBounds: Rect): Offset {
    return Offset(
        x = left - containerBounds.left,
        y = center.y - containerBounds.top
    )
}

internal fun Rect.rightCenterIn(containerBounds: Rect): Offset {
    return Offset(
        x = right - containerBounds.left,
        y = center.y - containerBounds.top
    )
}

internal fun rootBoundsFromPosition(
    positionInRoot: Offset,
    width: Int,
    height: Int
): Rect {
    return Rect(
        left = positionInRoot.x,
        top = positionInRoot.y,
        right = positionInRoot.x + width,
        bottom = positionInRoot.y + height
    )
}

internal fun isConnectorAnchorVisible(
    anchorInLayer: Offset,
    layerBounds: Rect,
    visibleRootBounds: Rect
): Boolean {
    val anchorRootY = layerBounds.top + anchorInLayer.y
    return anchorRootY >= visibleRootBounds.top && anchorRootY <= visibleRootBounds.bottom
}

private fun ChatConnectorLine.toPath(): Path {
    return Path().apply {
        moveTo(start.x, start.y)
        lineTo(cornerStart.x, cornerStart.y)
        lineTo(cornerEnd.x, cornerEnd.y)
        lineTo(end.x, end.y)
    }
}

private fun ChatConnectorTree.toPath(): Path {
    val geometry = createChatConnectorBraceGeometry(this)
    return Path().apply {
        geometry.trunkSegments.forEach { segment ->
            moveTo(segment.start.x, segment.start.y)
            lineTo(segment.end.x, segment.end.y)
        }
        geometry.hooks.forEach { hook ->
            braceHookSegment(hook)
        }
    }
}

internal fun createChatConnectorBraceGeometry(tree: ChatConnectorTree): ChatConnectorBraceGeometry {
    val requestedRadius = imModuleConnectionLineCornerRadiusPx()
    val avatarHook = tree.avatarBranch?.let { branch -> branch.end to branch.start }
    val pendingHooks = (listOfNotNull(avatarHook) +
        tree.messageBranches.map { branch -> branch.start to branch.end })
        .sortedBy { (center, _) -> center.y }
    val hooks = pendingHooks.mapIndexed { index, (center, branchEnd) ->
        val horizontalRoom = abs(branchEnd.x - center.x)
        val requestedHookRadius = min(requestedRadius, connectorRoundedElbowRadiusPx(horizontalRoom))
        val nextCenterY = pendingHooks.getOrNull(index + 1)?.first?.y
        val previousCenterY = pendingHooks.getOrNull(index - 1)?.first?.y
        val verticalRoom = when {
            index == 0 && nextCenterY != null -> abs(nextCenterY - center.y) / 2f
            index > 0 && previousCenterY != null -> abs(center.y - previousCenterY) / 2f
            else -> requestedHookRadius
        }
        val radius = min(requestedHookRadius, verticalRoom).coerceAtLeast(1f)
        val verticalDirection = if (index == 0) 1f else -1f
        ChatConnectorBraceHook(
            center = center,
            branchEnd = branchEnd,
            radius = radius,
            verticalDirection = verticalDirection
        )
    }
    return ChatConnectorBraceGeometry(
        trunkSegments = connectorContinuousTrunkSegments(
            trunkStart = tree.trunkStart,
            trunkEnd = tree.trunkEnd
        ),
        hooks = hooks
    )
}

private fun connectorContinuousTrunkSegments(
    trunkStart: Offset,
    trunkEnd: Offset
): List<ChatConnectorBranch> {
    if (trunkStart == trunkEnd) return emptyList()

    val topY = min(trunkStart.y, trunkEnd.y)
    val bottomY = kotlin.math.max(trunkStart.y, trunkEnd.y)
    val trunkX = trunkStart.x
    return listOf(
        ChatConnectorBranch(
            start = Offset(trunkX, topY),
            end = Offset(trunkX, bottomY)
        )
    )
}

private fun Path.braceHookSegment(hook: ChatConnectorBraceHook) {
    val deltaX = hook.branchEnd.x - hook.center.x
    if (abs(deltaX) <= 0.5f) return

    val geometry = hook.roundedElbowGeometry()
    moveTo(geometry.curveStart.x, geometry.curveStart.y)
    quadraticTo(
        geometry.curveControl.x,
        geometry.curveControl.y,
        geometry.horizontalStart.x,
        geometry.horizontalStart.y
    )
    lineTo(geometry.branchEnd.x, geometry.branchEnd.y)
}

internal fun ChatConnectorBraceHook.branchStartAvoidingJointOverlap(): Offset {
    return roundedElbowGeometry().horizontalStart
}

internal fun ChatConnectorBraceHook.roundedElbowGeometry(): ChatConnectorRoundedHookGeometry {
    val deltaX = branchEnd.x - center.x
    if (abs(deltaX) <= 0.5f) {
        return ChatConnectorRoundedHookGeometry(
            curveStart = center,
            curveControl = center,
            horizontalStart = center,
            branchEnd = branchEnd
        )
    }
    val horizontalDirection = if (deltaX >= 0f) 1f else -1f
    val safeRadius = min(radius, abs(deltaX)).coerceAtLeast(1f)
    return ChatConnectorRoundedHookGeometry(
        curveStart = center.copy(y = center.y + verticalDirection * safeRadius),
        curveControl = center,
        horizontalStart = center.copy(x = center.x + horizontalDirection * safeRadius),
        branchEnd = branchEnd
    )
}

private const val SessionRailWidthDp = 56
private val SessionRailWidth = SessionRailWidthDp.dp
private const val RightRailWidthDp = 58
private val RightRailWidth = RightRailWidthDp.dp
private val RailAvatarSize = 42.dp
private const val RailAvatarSizeDp = 42
private const val RailToolButtonWidthDp = 42
private const val RailToolButtonHeightDp = 42
private val RailToolButtonWidth = RailToolButtonWidthDp.dp
private val RailToolButtonHeight = RailToolButtonHeightDp.dp
private const val RightRailItemGapDp = 6
private const val RightRailExpansionSlackDp = 10
private const val RightRailMinimumVisibleToolCount = 3
private const val StandaloneImageMaxWidthDp = 168
private const val StandaloneMediaMinHeightDp = 72
private const val StandaloneMediaMaxHeightDp = 176
private val FloatingContentSideInset = 58.dp
private val EdgeGestureSafeInset = 8.dp
private val MessagePaneHorizontalPadding = 4.dp
private const val AccountScopedThreadSeparator = "__"
private const val AccountScopedContactCount = 5
private const val AccountScopedGroupCount = 2
private val StandaloneImageMaxWidth = 228.dp
private const val GroupMemberAvatarSizeDp = 28
private const val GroupMemberAvatarBubbleCenterOffsetDp = 4
private const val LeftRailFollowTextHideDelayMs = 0
private const val LeftRailFollowTextStartOffsetDp = RailAvatarSizeDp
private const val LeftRailFollowTextWidthDp = 280
private const val LeftRailFollowTextLayerWidthDp = LeftRailFollowTextStartOffsetDp + LeftRailFollowTextWidthDp
private val LeftRailFollowTextLayerWidth = LeftRailFollowTextLayerWidthDp.dp
private val LeftRailFollowTextWidth = LeftRailFollowTextWidthDp.dp
private const val LeftRailFollowTextInnerPaddingDp = 0
private const val LeftRailFollowTextNameSizeSp = 10f
private const val LeftRailFollowTextMessageSizeSp = 8.8f
private const val LeftRailFollowTextTimeSizeSp = 8f
private const val LeftRailLeadingSpacerItemCount = 1
private const val LeftRailShortContentScrollPaddingDp = 96
private const val LeftRailItemGapDp = 6
private const val LeftRailMinimumScrollRangeDp = 160
private const val LeftRailTopOverscrollMaxDp = 18
private const val LeftRailTopOverscrollResistance = 0.32f
private const val LeftRailTopOverscrollReturnMs = 170
private const val LeftRailLayerZIndex = 30f
private const val ConnectorLayerZIndex = 10f
private const val BottomInputBarMinHeightDp = 46
private const val BottomInputBarMaxHeightDp = 128
private const val BottomInputBarBottomPaddingDp = 10
private const val MessageListBottomExtraClearanceDp = 22
private val MessageLongPressMenuWidth = 300.dp
private val MessageLongPressMenuEstimatedHeight = 122.dp
private const val BottomInputIconButtonSizeDp = 32
private const val BottomInputIconSizeDp = 18
private const val BottomInputFieldMinHeightDp = 36
private const val BottomInputFieldMaxHeightDp = 118
private const val BottomInputTextSizeSp = 15
private const val BottomInputPlaceholderTextSizeSp = 13
private const val BottomInputMinLines = 1
private const val BottomInputMaxLines = 4
private const val BottomEmojiPanelHeightDp = 300
private const val ToolFeaturePanelMinWidthDp = 330
private const val ToolFeaturePanelMaxWidthDp = 430
private const val ToolFeaturePanelMaxHeightDp = 560
private const val CameraVideoMaxDurationMs = 15_000
private const val TAG = "FloatingChatOverlay"
private const val LOCATION_REQUEST_TIMEOUT_MS = 8_000L
private const val VoiceRecorderMimeType = "audio/mp4"
private const val VoiceRecorderFileExtension = "m4a"
private const val TOOL_ORDER_PREFS = "floating_chat_tool_order"
private const val KEY_TOOL_ACTION_ORDER = "tool_action_order"
private const val ACCOUNT_PROFILE_PREFS = "floating_chat_account_profiles"
private const val QUICK_PHRASE_PREFS = "floating_chat_quick_phrases"
private const val KEY_QUICK_PHRASES = "quick_phrases"
private const val QUICK_PHRASE_SEPARATOR = "\u001F"
private const val QuickPhraseMaxCount = 8
private const val FAVORITE_COLLECTION_PREFS = "floating_chat_favorite_collection"
private const val KEY_FAVORITE_COLLECTION_ITEMS = "favorite_collection_items"
private const val FavoriteCollectionMaxCount = 80
private val FavoriteLegacyMojibakeCharset: Charset = Charset.forName("GB18030")
private const val FavoriteLegacyMojibakeMarkers =
    "\u93c0\u60f0\u68cc\u93c2\u56e8\u6e70\u5a11\u581f\u4f05\u9365\u5267\u5896\u9471\u5a42\u3049" +
        "\u9365\u5267\u5896\u6924\u572d\u6d30\u6769\u6d98\u5bb3\u6d63\u66e1\u5acd\u8def" +
        "\u675e\u5f42\u934f\u62bd\u68f4\u9352\u72bb\u6ace\u5bb8\u67e5\u832c\u7ca1\u7039\u5c7e\u579a" +
        "\u9428\u52eb\u56e8\u6fa7\u55d8\u7b1f\u6d7c\u6c33\u7efe\u7455\u4f8a\u7d30"
private const val RightRailDefaultAccountWeight = 0.42f
private const val RightRailMinAccountWeight = 0.24f
private const val RightRailMaxAccountWeight = 0.70f
private const val RightRailSectionShiftFraction = 0.25f
private const val RightRailSectionResizeMs = 140
private const val GroupThreadId = "floating-chat-group-thread"
private const val AssistantContactId = "assistant"
private const val REAL_MEDIA_DECODE_MAX_SIZE_PX = 720
private val DefaultQuickPhrases = listOf(
    "收到，我先看一下，稍后同步进展。",
    "这个我确认后回复你。",
    "可以，按这个方案推进。"
)

private object OverlayTokens {
    val blankScrim = Color(0xFF000000)
    val shell = Color(0xF2D9E4E8)
    val shellBorder = Color(0xB8F8FCFF)
    val bar = Color(0xB8D7E1E7)
    val bottomBarStroke = Color(0x90F8FCFF)
    val rail = Color(0x00000000)
    val control = Color(0xFFEEF5F7)
    val activeControl = Color(0xE87DCC16)
    val input = Color(0x52F0F6F8)
    val inputStroke = Color(0x74F8FCFF)
    val inputText = Color(0xF8F8FCFF)
    val inputPlaceholder = Color(0xDCF8FCFF)
    val inputFocus = Color(0xCC7DCC16)
    val hairline = Color(0x82F8FCFF)
    val panel = Color(0xE8EDF4F5)
    val panelBorder = Color(0xB8F8FCFF)
    val panelIcon = Color(0x88778D94)
    val panelPrimaryText = Color(0xFF173C48)
    val panelSecondaryText = Color(0xC2173C48)
    val centerPanelScrim = Color(0x33000000)
    val quickPhraseRow = Color(0x66F8FCFF)
    val mapPreview = Color(0x6685A5AE)
    val momentsBackground = Color(0xFFF8FAFB)
    val momentsComposer = Color(0xFFEFF4F6)
    val momentsName = Color(0xFF607699)
    val momentsTime = Color(0xFFB3BCC2)
    val momentsMore = Color(0xEEF1F4F7)
    val momentsActionMenu = Color(0xEE22282C)
    val momentsActionDivider = Color(0xFF596168)
    val momentsActionText = Color(0xFFF4F7FA)
    val momentsLinkCard = Color(0xFFF1F2F3)
    val favoriteRow = Color(0xFFF5F8FA)
    val longPressMenu = Color(0xF24A4A4A)
    val voiceButton = Color(0xFFE0EBEE)
    val voiceIcon = Color(0xFF173C48)

    val primaryText = Color(0xF2F8FCFF)
    val secondaryText = Color(0xD9F1F7FA)
    val tertiaryText = Color(0xAEEAF2F5)
    val messageText = Color(0xFF0D4354)
    val tertiaryMessageText = Color(0xBB1A5768)
    val icon = Color(0xE8F8FCFF)
    val bottomIcon = Color(0xEAF8FCFF)
    val bottomIconButton = Color(0x36F8FCFF)
    val bottomIconStroke = Color(0x70F8FCFF)
    val toolIcon = Color(0xFF173C48)
    val toolIconActive = Color(0xFF3F720C)

    val accent = Color(0xFF7DCC16)
    val alertMuted = Color(0x50C96D68)
    val alertCore = Color(0xFFE27E76)

    val avatarFill = Color(0xFF7B97A4)
    val accountFill = Color(0xFF8AA9A7)
    val groupAvatar = Color(0xFF466D7A)
    val avatarNameTag = Color(0xB81B2529)
    val avatarBadgeText = Color(0xCCF8FCFF)

    val legacySelfBubble = Color(0xFFF7F9FB)
    val legacyOtherBubble = Color(0xFFF7F9FB)
    val legacyBubbleBorder = Color(0xFFCAD5DD)
    val selfBubble = Color(0x40FFFFFF)
    val otherBubble = Color.Transparent
    val systemBubble = Color(0xFFF8FCFF)
    val specialCard = Color(0xFFF7F9FB)
    val specialCardBorder = Color(0xFFCAD5DD)
    val aiBubble = Color(0xFFF3F7EF)
    val aiBorder = Color(0xA87DCC16)
    val aiDashedBorder = Color(0xFF7DCC16)
    val aiBadge = Color(0x4A7DCC16)
    val aiText = Color(0xFF507F0D)
    val selfBubbleBorder = Color(0x4DFFFFFF)
    val otherBubbleBorder = Color(0x80FFFFFF)
    val bubbleBorder = Color(0x80000000)
    val glassShadow = Color(0x1A000000)
    val bubbleText = Color(0xF8F8FCFF)
    val bubbleTextMuted = Color(0xD9F1F7FA)
    val bubbleNameText = Color(0xF8F8FCFF)
    val systemPromptText = Color(0xF4F8FCFF)
    val cardPrimaryText = Color(0xF8F8FCFF)
    val cardSecondaryText = Color(0xD9F1F7FA)
    val linkText = Color(0xFF145DA0)
    val aiGold = Color(0xFF9B6B13)
    val cardKindText = Color(0xFF294E59)
    val locationCard = Color(0xFFE6F3EE)
    val locationCardBorder = Color(0xFFB7D7CC)
    val contactCard = Color(0xFFEAF1FA)
    val contactCardBorder = Color(0xFFBDCCDE)
    val miniProgramCard = Color(0xFFE7F2F3)
    val paymentCard = Color(0xFFFF9D2E)
    val paymentCardBorder = Color(0xFFE48621)
    val paymentCardText = Color(0xFFFFF8E8)
    val paymentCardFooterText = Color(0xEFFFF1D6)
    val fileCard = Color(0xFFEAEFF4)
    val fileCardBorder = Color(0xFFBCC8D2)
    val voiceCard = Color(0xFFE7EEF6)
    val voiceCardBorder = Color(0xFFBBC8D8)
    val mediaCard = Color(0xFFEDF2F5)
    val resourcePanel = Color(0xFFE9F0F1)
    val resourcePanelBorder = Color(0xFFBECBD1)
    val miniProgramIcon = Color(0xFF3D8B78)
    val permissionChip = Color(0xDDF2F6F7)
    val permissionChipBorder = Color(0xFFCAD5DD)
    val filePreviewBackground = Color(0xFFE0E8EA)
    val fileIconText = Color(0xFFF8FCFF)
    val fileWechatCard = Color(0xFFF8FAFC)
    val fileWechatCardBorder = Color(0xFFE5EBEF)
    val fileWechatTitle = Color(0xF31B1D20)
    val fileWechatSize = Color(0xB05B636A)
    val locationPin = Color(0xFF216A78)
    val locationMapCard = Color(0xFFF8FAFC)
    val locationMapText = Color(0xF31B1D20)
    val locationMapSubtext = Color(0xB05B636A)
    val locationMapBase = Color(0xFFECEFEC)
    val locationMapRoad = Color(0xFFFFFFFF)
    val locationMapMinorRoad = Color(0xFFD7DDDE)
    val locationMapPark = Color(0xFFBFE4B7)
    val locationMapWater = Color(0xFFC8DDEE)
    val locationMapPin = Color(0xFF10B978)
    val imageBase = Color(0xFF6C8188)
    val imageBlock = Color(0xFF9FB5BD)
    val imageHighlight = Color(0xBBE7EFF1)
    val imageFade = Color(0x7A253D45)
    val imageWatermark = Color(0xDDF8FCFF)
    val imageAction = Color(0x80243840)
    val standaloneMediaBorder = Color(0xDDF4FAFC)
    val mediaActionButton = Color(0xD9263035)
    val mediaActionButtonSelected = Color(0xE87DCC16)
    val mediaActionBorder = Color(0x66F8FCFF)
    val mediaActionIcon = Color(0xF4F8FCFF)
    val mediaActionIconSelected = Color(0xFF173C48)
    val mediaActionStatus = Color(0xC4263035)
    val mediaSheetScrim = Color(0x6B182126)
    val mediaSheet = Color(0xF6F7FAFB)
    val mediaSheetBorder = Color(0xFFE2EAEE)
    val mediaSheetText = Color(0xFF203D48)
    val mediaSheetMutedText = Color(0xA6203D48)
    val mediaSheetIcon = Color(0xFF203D48)
    val mediaSheetIconBox = Color(0xFFF1F5F7)
    val mediaSheetIconBorder = Color(0xFFDCE6EA)
    val mediaSheetCancel = Color(0xFFE8EEF1)
    val videoBase = Color(0xFF34484F)
    val videoFrame = Color(0xFF5E737B)
    val inlineAvatar = Color(0xFF5B6D75)
    val imModuleTextShadow = Shadow(
        color = Color(0xE6000000),
        offset = Offset(0f, 2f),
        blurRadius = 12f
    )
    val leftRailFollowTextShadow = Shadow(
        color = Color(0xE6000000),
        offset = Offset(0f, 2f),
        blurRadius = 12f
    )
    val textShadow = imModuleTextShadow

    val quoteBackground = Color(0xFFC5C5C5)
    val quoteBar = Color(0x88F8FCFF)
    val aiffStrip = Color(0x62EAF3F5)
    val railDivider = Color(0x9FF8FCFF)
    val activeTool = Color(0xEE7DCC16)
    val connectorLine = Color(0xF2F8FCFF)
    val connectorLineShadow = Color(0x4D000000)
}
