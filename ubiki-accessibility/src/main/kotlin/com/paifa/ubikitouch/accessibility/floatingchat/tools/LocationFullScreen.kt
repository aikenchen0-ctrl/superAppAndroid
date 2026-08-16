package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.location.Geocoder
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.AppLocationOption
import com.paifa.ubikitouch.accessibility.DeviceLocationState
import com.paifa.ubikitouch.accessibility.FloatingChatLocationPermissionBridge
import com.paifa.ubikitouch.accessibility.currentDeviceLocationState
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.hasLocationPermission
import com.paifa.ubikitouch.accessibility.requestCurrentDeviceLocation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LocationFullScreenTab(val label: String) {
    CurrentLocation("当前位置"),
    Search("搜索地点")
}

/** 根据位置页模式选择与 iOS 对齐的消息类型。 */
internal fun locationFullScreenMessageType(
    isLiveLocation: Boolean
): FloatingChatMessageType =
    if (isLiveLocation) FloatingChatMessageType.LiveLocation else FloatingChatMessageType.Location

/** 根据位置页模式显示普通位置或实时位置标题。 */
internal fun locationFullScreenTitle(
    isLiveLocation: Boolean
): String = if (isLiveLocation) "选择实时位置" else "位置信息"

/**
 * 对应 iOS `presentLocationPicker` 的 Android Material 3 全屏位置选择页。
 * 普通位置和实时位置均通过系统定位选择后写入聊天消息，分别映射为 Location 与 LiveLocation；
 * 本页复用已有悬浮根视图和真实定位权限链路，避免创建 Dialog 或新 Window 引起 BadTokenException。
 * 测试流程：分别点击右侧“位置信息”和“实时位置”，允许定位权限并刷新，发送当前位置后确认
 * 聊天消息的类型、标题、地址和 geoUri；切换两个分页，点击左上返回并确认页面向下滑动关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationFullScreen(
    permissionRequestToken: Int,
    onSendLocation: (AppLocationOption) -> Unit,
    onBack: () -> Unit,
    isLiveLocation: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { LocationFullScreenTab.entries.size })
    var locationState by remember { mutableStateOf(currentDeviceLocationState(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<AppLocationOption>()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isClosing by remember { mutableStateOf(false) }

    fun refreshLocation() {
        locationState = locationState.copy(loading = true, error = null)
        requestCurrentDeviceLocation(context) { nextState -> locationState = nextState }
    }
    fun searchPlaces() {
        val query = searchQuery.trim()
        if (query.isEmpty() || searching) return
        searching = true
        searchError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { searchLocationOptions(context, query) }
            }.onSuccess { locations ->
                searchResults = locations
                searchError = if (locations.isEmpty()) "未找到匹配地点" else null
            }.onFailure { error ->
                searchResults = emptyList()
                searchError = error.message ?: "地点搜索失败"
            }
            searching = false
        }
    }

    LaunchedEffect(permissionRequestToken) {
        if (hasLocationPermission(context)) refreshLocation()
    }

    fun close(afterClose: () -> Unit) {
        if (isClosing) return
        isClosing = true
        afterClose()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = locationFullScreenTitle(isLiveLocation),
            onBack = { close(onBack) }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            LocationFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (LocationFullScreenTab.entries[page]) {
                LocationFullScreenTab.CurrentLocation -> LocationCurrentPage(
                    locationState = locationState,
                    onRefresh = ::refreshLocation,
                    onRequestPermission = FloatingChatLocationPermissionBridge::requestLocationPermission,
                    onSendLocation = { location -> close { onSendLocation(location) } }
                )
                LocationFullScreenTab.Search -> LocationSearchPage(
                    query = searchQuery,
                    searching = searching,
                    results = searchResults,
                    error = searchError,
                    onQueryChange = { searchQuery = it },
                    onSearch = ::searchPlaces,
                    onSendLocation = { location -> close { onSendLocation(location) } }
                )
            }
        }
    }
}

@Composable
private fun LocationCurrentPage(
    locationState: DeviceLocationState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onSendLocation: (AppLocationOption) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "当前位置",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        item {
            val location = locationState.option
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    headlineContent = {
                        Text(
                            text = location?.title ?: locationStateLabel(locationState),
                            fontWeight = FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Text(
                            text = location?.address ?: locationState.error.orEmpty(),
                            fontWeight = FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        if (location != null) {
                            FilledTonalButton(onClick = { onSendLocation(location) }) {
                                Text("发送", fontWeight = FontWeight.Normal)
                            }
                        }
                    }
                )
            }
        }
        item {
            if (locationState.permissionDenied) {
                FilledTonalButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("授权定位", fontWeight = FontWeight.Normal)
                }
            } else {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !locationState.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (locationState.loading) "定位中" else "刷新位置", fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun LocationSearchPage(
    query: String,
    searching: Boolean,
    results: List<AppLocationOption>,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSendLocation: (AppLocationOption) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "搜索地点",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("地点名称") },
                    singleLine = true
                )
                FilledTonalButton(onClick = onSearch, enabled = query.isNotBlank() && !searching) {
                    Text(if (searching) "搜索中" else "搜索", fontWeight = FontWeight.Normal)
                }
            }
        }
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        } else {
            items(results, key = { location -> location.geoUri ?: "${location.latitude}-${location.longitude}-${location.title}" }) { location ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        headlineContent = { Text(location.title, fontWeight = FontWeight.Normal) },
                        supportingContent = { Text(location.address, fontWeight = FontWeight.Normal) },
                        trailingContent = {
                            FilledTonalButton(onClick = { onSendLocation(location) }) {
                                Text("发送", fontWeight = FontWeight.Normal)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun locationStateLabel(state: DeviceLocationState): String = when {
    state.loading -> "正在获取当前位置"
    state.permissionDenied -> "需要定位权限"
    else -> "未获取到当前位置"
}

@Suppress("DEPRECATION")
private fun searchLocationOptions(context: android.content.Context, query: String): List<AppLocationOption> {
    val geocoder = Geocoder(context, Locale.getDefault())
    return geocoder.getFromLocationName(query, 8).orEmpty().mapNotNull { address ->
        val title = address.featureName?.takeIf { value -> value.isNotBlank() }
            ?: address.locality?.takeIf { value -> value.isNotBlank() }
            ?: return@mapNotNull null
        val addressText = address.getAddressLine(0)?.takeIf { value -> value.isNotBlank() } ?: title
        val latitude = address.latitude
        val longitude = address.longitude
        AppLocationOption(
            title = title,
            address = addressText,
            latitude = latitude,
            longitude = longitude,
            geoUri = "geo:$latitude,$longitude?q=$latitude,$longitude"
        )
    }
}
