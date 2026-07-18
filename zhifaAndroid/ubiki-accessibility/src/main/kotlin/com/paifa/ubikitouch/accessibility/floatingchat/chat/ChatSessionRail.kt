package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarRole
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactAvatar
import com.paifa.ubikitouch.accessibility.floatingchat.chat.GroupThreadId
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.components.avatarPressModifier
import com.paifa.ubikitouch.accessibility.floatingchat.components.avatarTextTagsVisible
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupConnectorId
import com.paifa.ubikitouch.accessibility.floatingchat.account.leftRailSelectedAvatarHighlightStrokeDp
import com.paifa.ubikitouch.accessibility.floatingchat.components.rememberAsyncAvatarBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toGroupThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun ChatSessionRail(
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
    val railItems = remember(visibleGroups, contacts, conversation.messages, selectedAccountId) {
        sessionRailItemsByLatestChatTime(
            groups = visibleGroups,
            contacts = contacts,
            conversation = conversation,
            selectedAccountId = selectedAccountId
        )
    }
    val contactsById = remember(railItems) {
        railItems.associate { item -> item.contact.id to item.contact }
    }
    val sessionConnectorIds = remember(railItems) {
        railItems.map { item ->
            when (item) {
                is SessionRailItem.Group -> item.contact.groupConnectorId()
                is SessionRailItem.Contact -> item.contact.id
            }
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = leftRailInitialFirstVisibleItemIndex()
    )
    var showFollowText by remember { mutableStateOf(false) }
    val avatarBoundsByContactId = remember { mutableMapOf<String, Rect>() }
    var avatarBoundsVersion by remember { mutableIntStateOf(0) }
    fun updateAvatarBounds(id: String, bounds: Rect) {
        if (avatarBoundsByContactId.updateBoundsIfChanged(id, bounds)) {
            avatarBoundsVersion += 1
        }
    }
    fun removeAvatarBounds(id: String) {
        if (avatarBoundsByContactId.remove(id) != null) {
            avatarBoundsVersion += 1
        }
    }
    val density = LocalDensity.current
    val railScreenEdgeInsetDp = with(density) { leftRailAvatarScreenEdgeInsetPx().toDp() }
    var topOverscrollPx by remember { mutableStateOf(0f) }
    val maxTopOverscrollPx = remember(density) {
        with(density) { leftRailTopOverscrollMaxDp().dp.toPx() }
    }
    var railRootTopPx by remember { mutableStateOf(0f) }
    var sessionViewportBounds by remember { mutableStateOf<Rect?>(null) }
    val sessionVirtualFallbackStepPx = remember(density) {
        with(density) { (leftRailAvatarSizeDp() + leftRailItemGapDp()).dp.toPx() }
    }
    val followInfos by remember(conversation, selectedAccountId, contactsById, avatarBoundsVersion) {
        derivedStateOf {
            leftRailVisibleFollowInfos(
                conversation = conversation,
                selectedAccountId = selectedAccountId,
                contactsById = contactsById,
                visibleAvatarBounds = avatarBoundsByContactId,
                railRootTopPx = railRootTopPx
            )
        }
    }
    val selectedPrivateContactId = (selectedThread as? ChatThreadSelection.Private)?.contactId
    val selectedSessionConnectorId = selectedThread.groupConnectorId()
    val selectedRailItem = remember(railItems, selectedThread) {
        railItems.firstOrNull { item ->
            when (item) {
                is SessionRailItem.Group -> selectedThread == item.contact.toGroupThreadSelection()
                is SessionRailItem.Contact -> selectedThread is ChatThreadSelection.Private &&
                    selectedThread.contactId == item.contact.id
            }
        }
    }
    val selectedPrivateAvatarBounds by remember(selectedPrivateContactId, avatarBoundsVersion) {
        derivedStateOf {
            selectedPrivateContactId?.let { contactId -> avatarBoundsByContactId[contactId] }
        }
    }
    val pinnedSelectedAvatarEdge by remember(
        sessionConnectorIds,
        selectedSessionConnectorId,
        listState,
        sessionVirtualFallbackStepPx
    ) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
            if (viewportHeight <= 0f) {
                null
            } else {
                leftRailPinnedSelectedAvatarEdge(
                    sessionIds = sessionConnectorIds,
                    selectedSessionId = selectedSessionConnectorId,
                    visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                        val sessionIndex = item.index - leftRailLeadingSpacerItemCount()
                        if (sessionIndex < 0) {
                            null
                        } else {
                            LeftRailVisibleSessionItem(
                                index = sessionIndex,
                                offset = item.offset,
                                size = item.size
                            )
                        }
                    },
                    viewportHeightPx = viewportHeight,
                    fallbackStepPx = sessionVirtualFallbackStepPx
                )
            }
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
    LaunchedEffect(sessionConnectorIds, listState, sessionViewportBounds, sessionVirtualFallbackStepPx) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val sessionIndex = item.index - leftRailLeadingSpacerItemCount()
                if (sessionIndex < 0) {
                    null
                } else {
                    LeftRailVisibleSessionItem(
                        index = sessionIndex,
                        offset = item.offset,
                        size = item.size
                    )
                }
            }
        }.collectLatest { visibleItems ->
            val viewport = sessionViewportBounds ?: return@collectLatest
            connectorState.updateVirtualUserAvatars(
                sessionIds = sessionConnectorIds,
                visibleItems = visibleItems,
                viewport = viewport,
                fallbackStepPx = sessionVirtualFallbackStepPx
            )
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
                    topOverscrollPx = (topOverscrollPx + available.y * leftRailTopOverscrollResistance())
                        .coerceIn(0f, maxTopOverscrollPx)
                    return Offset.Zero
                }
                if (!pullingDown && topOverscrollPx > 0f) {
                    val consumed = minOf(-available.y, topOverscrollPx)
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
                        animationSpec = tween(durationMillis = leftRailTopOverscrollReturnMs())
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
                .width(leftRailTouchableWidthDp().dp)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    sessionViewportBounds = bounds
                    connectorState.updateUserViewport(bounds)
                }
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
                SessionRailAvatarItem(
                    item = item,
                    contactsCount = contacts.size,
                    selectedThread = selectedThread,
                    selectedPrivateContactId = selectedPrivateContactId,
                    unreadThreadIds = unreadThreadIds,
                    avatarBoundsByContactId = avatarBoundsByContactId,
                    updateAvatarBounds = ::updateAvatarBounds,
                    removeAvatarBounds = ::removeAvatarBounds,
                    connectorState = connectorState,
                    onThreadSelected = onThreadSelected,
                    onGroupAvatarLongClick = onGroupAvatarLongClick,
                    onContactAvatarLongClick = onContactAvatarLongClick,
                    removeBoundsOnDispose = true,
                    modifier = Modifier.padding(start = railScreenEdgeInsetDp)
                )
            }
        }
        val pinnedEdge = pinnedSelectedAvatarEdge
        if (pinnedEdge != null && selectedRailItem != null) {
            SessionRailAvatarItem(
                item = selectedRailItem,
                contactsCount = contacts.size,
                selectedThread = selectedThread,
                selectedPrivateContactId = selectedPrivateContactId,
                unreadThreadIds = unreadThreadIds,
                avatarBoundsByContactId = avatarBoundsByContactId,
                updateAvatarBounds = ::updateAvatarBounds,
                removeAvatarBounds = ::removeAvatarBounds,
                connectorState = connectorState,
                onThreadSelected = onThreadSelected,
                onGroupAvatarLongClick = onGroupAvatarLongClick,
                onContactAvatarLongClick = onContactAvatarLongClick,
                removeBoundsOnDispose = false,
                modifier = Modifier
                    .align(pinnedEdge.toLeftRailAlignment())
                    .padding(start = railScreenEdgeInsetDp)
                    .zIndex(14f)
            )
        }
        LeftRailFollowTextOverlay(
            infos = followInfos,
            visible = showFollowText && followInfos.isNotEmpty(),
            modifier = Modifier
                .offset(x = leftRailFollowTextStartOffsetDp().dp + railScreenEdgeInsetDp)
                .fillMaxHeight()
                .requiredWidth(leftRailFollowTextWidthDp().dp)
                .zIndex(12f)
        )
    }
}

@Composable
private fun SessionRailAvatarItem(
    item: SessionRailItem,
    contactsCount: Int,
    selectedThread: ChatThreadSelection,
    selectedPrivateContactId: String?,
    unreadThreadIds: Set<String>,
    avatarBoundsByContactId: Map<String, Rect>,
    updateAvatarBounds: (String, Rect) -> Unit,
    removeAvatarBounds: (String) -> Unit,
    connectorState: ConnectorCoordinateState,
    onThreadSelected: (ChatThreadSelection) -> Unit,
    onGroupAvatarLongClick: (FloatingChatContact) -> Unit,
    onContactAvatarLongClick: (FloatingChatContact) -> Unit,
    removeBoundsOnDispose: Boolean,
    modifier: Modifier = Modifier
) {
    when (item) {
        is SessionRailItem.Group -> {
            val group = item.contact
            val selection = group.toGroupThreadSelection()
            Box(modifier = modifier) {
                GroupChatAvatar(
                    selected = selectedThread == selection,
                    unread = unreadThreadIds.contains(selection.toLocalThreadId()),
                    memberCount = contactsCount,
                    label = group.initials,
                    color = Color(group.avatarColor),
                    memberAvatarUris = groupChatAvatarDisplayImageUris(group),
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
                        if (removeBoundsOnDispose) {
                            removeAvatarBounds(group.id)
                            val connectorId = group.groupConnectorId()
                            connectorState.removeUserAvatar(connectorId)
                            connectorState.removeGroupThreadAvatar(connectorId)
                        }
                    }
                )
            }
        }
        is SessionRailItem.Contact -> {
            val contact = item.contact
            var currentAvatarBounds by remember(contact.id) { mutableStateOf<Rect?>(null) }
            Box(modifier = modifier) {
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
                        if (removeBoundsOnDispose) {
                            removeAvatarBounds(contact.id)
                            connectorState.removeUserAvatar(contact.id)
                        }
                    }
                )
            }
        }
    }
}

private fun RailPinnedAvatarEdge.toLeftRailAlignment(): Alignment {
    return when (this) {
        RailPinnedAvatarEdge.Top -> Alignment.TopStart
        RailPinnedAvatarEdge.Bottom -> Alignment.BottomStart
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
                    .offset(x = leftRailFollowTextInnerPaddingDp().dp, y = yOffset)
                    .widthIn(
                        max = (leftRailFollowTextWidthDp() - leftRailFollowTextInnerPaddingDp() * 2).dp
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                TextLabel(
                    text = info.name,
                    size = leftRailFollowTextNameSizeSp().sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.primaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.leftRailFollowTextShadow
                )
                TextLabel(
                    text = info.lastMessage,
                    size = leftRailFollowTextMessageSizeSp().sp,
                    weight = FontWeight.Medium,
                    color = OverlayTokens.secondaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.leftRailFollowTextShadow
                )
                TextLabel(
                    text = info.lastTime,
                    size = leftRailFollowTextTimeSizeSp().sp,
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
private fun GroupChatAvatar(
    selected: Boolean,
    unread: Boolean,
    memberCount: Int,
    label: String = "群",
    color: Color = OverlayTokens.groupAvatar,
    memberAvatarUris: List<String> = emptyList(),
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
            .size(leftRailAvatarSizeDp().dp)
            .then(avatarPressModifier(onClick = onClick, onLongClick = onLongClick))
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
        border = BorderStroke(
            width = if (selected) leftRailSelectedAvatarHighlightStrokeDp().dp else 1.dp,
            color = if (selected) OverlayTokens.accent else OverlayTokens.hairline
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (memberAvatarUris.isNotEmpty()) {
                GroupChatAvatarGrid(
                    memberAvatarUris = memberAvatarUris,
                    fallbackColor = color,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
            }
            if (avatarTextTagsVisible()) {
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
private fun GroupChatAvatarGrid(
    memberAvatarUris: List<String>,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val displayUris = memberAvatarUris.take(groupChatAvatarGridMaxMembers())
    if (displayUris.size == 1) {
        val avatarBitmap = rememberAsyncAvatarBitmap(displayUris.single())
        Box(
            modifier = modifier
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(fallbackColor.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    } else {
        val rows = displayUris.chunked(3)
        Column(
            modifier = modifier.padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    row.forEach { uri ->
                        val avatarBitmap = rememberAsyncAvatarBitmap(uri)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(fallbackColor.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
            repeat(3 - rows.size) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

private fun MutableMap<String, Rect>.updateBoundsIfChanged(id: String, bounds: Rect): Boolean {
    if (this[id] == bounds) return false
    this[id] = bounds
    return true
}

internal sealed class SessionRailItem(open val contact: FloatingChatContact) {
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

internal fun sessionRailItemKeys(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>
): List<String> {
    return sessionRailItems(
        groups = groups,
        contacts = contacts
    ).map { item -> item.key }
}

internal fun sessionRailItemKeysByLatestChatTime(
    conversation: FloatingChatConversation,
    selectedAccountId: String
): List<String> {
    return sessionRailItemsByLatestChatTime(
        groups = conversation.groupContacts,
        contacts = conversation.contacts,
        conversation = conversation,
        selectedAccountId = selectedAccountId
    ).map { item -> item.key }
}

internal fun sessionRailItemsByLatestChatTime(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    conversation: FloatingChatConversation,
    selectedAccountId: String
): List<SessionRailItem> {
    val items = sessionRailItems(groups, contacts)
    if (!leftRailSortsSessionsByLatestChatTime()) return items

    val latestMessageIndexes = latestMessageIndexesBySessionRailKey(
        conversation = conversation,
        groups = groups,
        contacts = contacts,
        selectedAccountId = selectedAccountId
    )
    return items.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SessionRailItem>> { indexed ->
                latestMessageIndexes[indexed.value.key] ?: Int.MIN_VALUE
            }.thenBy { indexed -> indexed.index }
        )
        .map { indexed -> indexed.value }
}

private fun sessionRailItems(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>
): List<SessionRailItem> {
    return buildList {
        dedupeSessionRailContacts(groups).forEach { group -> add(SessionRailItem.Group(group)) }
        dedupeSessionRailContacts(contacts).forEach { contact -> add(SessionRailItem.Contact(contact)) }
    }
}

private fun dedupeSessionRailContacts(contacts: List<FloatingChatContact>): List<FloatingChatContact> {
    val mergedById = linkedMapOf<String, FloatingChatContact>()
    contacts.forEach { contact ->
        val existing = mergedById[contact.id]
        mergedById[contact.id] = if (existing == null) {
            contact
        } else {
            mergeRailContact(existing, contact)
        }
    }
    return mergedById.values.toList()
}

private fun latestMessageIndexesBySessionRailKey(
    conversation: FloatingChatConversation,
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    selectedAccountId: String
): Map<String, Int> {
    val groupIds = groups.asSequence().map { group -> group.id }.toHashSet()
    val contactIds = contacts.asSequence().map { contact -> contact.id }.toHashSet()
    val indexes = mutableMapOf<String, Int>()
    conversation.messages.forEachIndexed { index, message ->
        val key = sessionRailKeyForMessage(
            message = message,
            groupIds = groupIds,
            contactIds = contactIds,
            selectedAccountId = selectedAccountId
        ) ?: return@forEachIndexed
        indexes[key] = index
    }
    return indexes
}

private fun sessionRailKeyForMessage(
    message: FloatingChatMessage,
    groupIds: Set<String>,
    contactIds: Set<String>,
    selectedAccountId: String
): String? {
    val explicitThreadId = message.threadContactId?.takeIf { threadId -> threadId.isNotBlank() }
    return when {
        explicitThreadId != null && explicitThreadId in groupIds -> "group-$explicitThreadId"
        explicitThreadId != null && explicitThreadId in contactIds -> SessionRailItem.Contact.keyFor(explicitThreadId)
        message.connectionTarget == FloatingChatConnectionTarget.User &&
            message.connectionTargetId in groupIds -> "group-${message.connectionTargetId}"
        message.connectionTarget == FloatingChatConnectionTarget.User &&
            message.connectionTargetId in contactIds -> SessionRailItem.Contact.keyFor(requireNotNull(message.connectionTargetId))
        message.connectionTarget == FloatingChatConnectionTarget.Account &&
            message.connectionTargetId == selectedAccountId -> null
        else -> null
    }
}

private fun mergeRailContact(
    existing: FloatingChatContact,
    candidate: FloatingChatContact
): FloatingChatContact {
    return existing.copy(
        name = existing.name.ifBlank { candidate.name },
        initials = existing.initials.ifBlank { candidate.initials },
        description = existing.description.ifBlank { candidate.description },
        selected = existing.selected || candidate.selected,
        online = existing.online || candidate.online,
        avatarUrl = existing.avatarUrl ?: candidate.avatarUrl,
        groupMemberAvatarUrls = existing.groupMemberAvatarUrls.ifEmpty { candidate.groupMemberAvatarUrls }
    )
}
