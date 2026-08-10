package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestUiState

@Composable
internal fun FriendRequestScreen(
    state: FriendRequestUiState,
    onEvent: (ContactsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FriendRequestRowBackground)
            .padding(bottom = 12.dp)
    ) {
        FriendRequestTopBar(onBack = { onEvent(ContactsUiEvent.BackRequested) })
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            FriendRequestList(
                requests = state.requests,
                enabled = state.enabled,
                modifier = Modifier.fillMaxWidth(),
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun FriendRequestTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(FriendRequestHeaderBackground)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = FriendRequestPrimaryText,
                modifier = Modifier.size(21.dp)
            )
        }
        TextLabel(
            text = "新的朋友",
            size = 16.sp,
            weight = FontWeight.Bold,
            color = FriendRequestPrimaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun FriendRequestList(
    requests: List<FriendRequestSummary>,
    enabled: Boolean,
    modifier: Modifier,
    onEvent: (ContactsUiEvent) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextLabel(
            text = "好友申请",
            size = 11.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp, max = 210.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (requests.isEmpty()) {
                item {
                    TextLabel(
                        text = "暂无待处理申请",
                        size = 10.sp,
                        color = OverlayTokens.panelSecondaryText,
                        modifier = Modifier.padding(vertical = 10.dp),
                        maxLines = 1
                    )
                }
            }
            itemsIndexed(
                items = requests,
                key = { _, request -> request.id }
            ) { _, request ->
                FriendRequestRow(
                    request = request,
                    enabled = enabled,
                    onEvent = onEvent
                )
            }
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestSummary,
    enabled: Boolean,
    onEvent: (ContactsUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextLabel(
            text = request.displayName,
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = request.message.orEmpty(),
            size = 9.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2,
            lineHeight = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FriendRequestActionButton(
                label = "通过",
                enabled = enabled,
                accent = true,
                onClick = { onEvent(ContactsUiEvent.AcceptRequest(request.id)) }
            )
            FriendRequestActionButton(
                label = "拒绝",
                enabled = enabled,
                onClick = { onEvent(ContactsUiEvent.RejectRequest(request.id)) }
            )
        }
    }
}

@Composable
private fun FriendRequestActionButton(
    label: String,
    enabled: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) OverlayTokens.accent else OverlayTokens.resourcePanel,
            contentColor = if (accent) Color.White else OverlayTokens.panelPrimaryText,
            disabledContainerColor = OverlayTokens.resourcePanel.copy(alpha = 0.55f),
            disabledContentColor = OverlayTokens.panelSecondaryText
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    ) {
        TextLabel(
            text = label,
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = if (enabled && accent) Color.White else OverlayTokens.panelPrimaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private val FriendRequestHeaderBackground = Color(0xFFF1F1F1)
private val FriendRequestRowBackground = Color(0xFFFCFCFC)
private val FriendRequestPrimaryText = Color(0xFF202020)
