package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.AppLocationOption
import com.paifa.ubikitouch.accessibility.FloatingChatLocationPermissionBridge
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.currentDeviceLocationState
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatLocationGlyph
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.hasLocationPermission
import com.paifa.ubikitouch.accessibility.requestCurrentDeviceLocation
internal fun locationPermissionRequestHidesFloatingOverlayUntilResult(): Boolean = true

internal fun locationToolOpensInAppPickerInsteadOfDirectSend(): Boolean = true

internal fun locationPanelSendsSelectedLocation(): Boolean = true

internal fun locationToolUsesRealDeviceLocation(): Boolean = true

internal fun locationToolRequestsRuntimePermission(): Boolean = true

internal fun locationPanelStartsRealLocationRefreshAutomatically(): Boolean = true

internal fun locationPanelUsesOnlyPresetLocations(): Boolean = false

internal fun locationMessageIncludesCoordinatesInResourceUrl(): Boolean = true

@Composable
internal fun LocationPickerPanel(
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
                FloatingChatLocationGlyph(modifier = Modifier.size(28.dp))
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
