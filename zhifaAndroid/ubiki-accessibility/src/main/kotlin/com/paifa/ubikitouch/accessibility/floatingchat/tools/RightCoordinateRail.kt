package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarRole
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactAvatar
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.account.toContact
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorCoordinateState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.RailPinnedAvatarEdge
import com.paifa.ubikitouch.accessibility.floatingchat.chat.RightRailVisibleAccountItem
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rightRailPinnedSelectedAccountEdge
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun RightCoordinateRail(
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
    val railScreenEdgeInsetDp = with(density) { rightRailAvatarScreenEdgeInsetPx().toDp() }
    val toolSlotHeightPx = remember(density) {
        with(density) { (rightRailToolButtonHeightDp().dp + rightRailItemGapDp().dp).toPx() }
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
        animationSpec = tween(durationMillis = rightRailSectionResizeMs()),
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
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { account -> account.id == selectedAccountId }
    }
    val accountVirtualFallbackStepPx = remember(density) {
        with(density) { (rightRailAvatarSizeDp().dp + rightRailItemGapDp().dp).toPx() }
    }
    val pinnedSelectedAccountEdge by remember(
        accountIds,
        selectedAccountId,
        accountListState,
        accountVirtualFallbackStepPx
    ) {
        derivedStateOf {
            val layoutInfo = accountListState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
            if (viewportHeight <= 0f) {
                null
            } else {
                rightRailPinnedSelectedAccountEdge(
                    accountIds = accountIds,
                    selectedAccountId = selectedAccountId,
                    visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                        RightRailVisibleAccountItem(
                            index = item.index,
                            offset = item.offset,
                            size = item.size
                        )
                    },
                    viewportHeightPx = viewportHeight,
                    fallbackStepPx = accountVirtualFallbackStepPx
                )
            }
        }
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
        Box(
            modifier = Modifier
                .weight(railWeights.accountWeight)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                    AccountRailAvatarItem(
                        account = account,
                        profile = accountProfiles[account.id],
                        selectedAccountId = selectedAccountId,
                        connectorState = connectorState,
                        onAccountAvatarClick = onAccountAvatarClick,
                        onAccountAvatarLongClick = onAccountAvatarLongClick,
                        removeBoundsOnDispose = true,
                        modifier = Modifier.padding(end = railScreenEdgeInsetDp)
                    )
                }
            }
            val pinnedEdge = pinnedSelectedAccountEdge
            if (pinnedEdge != null && selectedAccount != null) {
                AccountRailAvatarItem(
                    account = selectedAccount,
                    profile = accountProfiles[selectedAccount.id],
                    selectedAccountId = selectedAccountId,
                    connectorState = connectorState,
                    onAccountAvatarClick = onAccountAvatarClick,
                    onAccountAvatarLongClick = onAccountAvatarLongClick,
                    removeBoundsOnDispose = false,
                    modifier = Modifier
                        .align(pinnedEdge.toRightRailAlignment())
                        .padding(end = railScreenEdgeInsetDp)
                        .zIndex(14f)
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
                Box(modifier = Modifier.padding(end = railScreenEdgeInsetDp)) {
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
}

@Composable
private fun AccountRailAvatarItem(
    account: FloatingChatContact,
    profile: FloatingChatAccountProfile?,
    selectedAccountId: String?,
    connectorState: ConnectorCoordinateState,
    onAccountAvatarClick: (FloatingChatContact) -> Unit,
    onAccountAvatarLongClick: (FloatingChatContact) -> Unit,
    removeBoundsOnDispose: Boolean,
    modifier: Modifier = Modifier
) {
    val displayAccount = profile?.toContact(account) ?: account
    var currentAccountBounds by remember(account.id) { mutableStateOf<Rect?>(null) }
    Box(modifier = modifier) {
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
                if (removeBoundsOnDispose) {
                    connectorState.removeAccountAvatar(account.id)
                }
            }
        )
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
                .size(width = rightRailToolButtonWidthDp().dp, height = rightRailToolButtonHeightDp().dp)
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

private fun RailPinnedAvatarEdge.toRightRailAlignment(): Alignment {
    return when (this) {
        RailPinnedAvatarEdge.Top -> Alignment.TopEnd
        RailPinnedAvatarEdge.Bottom -> Alignment.BottomEnd
    }
}
