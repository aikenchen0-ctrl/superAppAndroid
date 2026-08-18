package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.launch

internal enum class CouponWalletCategory(val label: String, val icon: ImageVector) {
    Membership("会员卡", Icons.Filled.Badge),
    Transport("交通卡", Icons.Filled.DirectionsBus),
    Voucher("券和礼品卡", Icons.Filled.CardGiftcard),
    Ticket("票证", Icons.Filled.ConfirmationNumber)
}

internal data class CouponWalletItem(
    val category: CouponWalletCategory,
    val title: String,
    val subtitle: String,
    val badgeText: String? = null,
    val code: String = "8866 1024 2026"
)

private val couponWalletItems = listOf(
    CouponWalletItem(CouponWalletCategory.Membership, "麦当劳会员卡", "积分 0 | 余额 ¥0.00", "附近可用"),
    CouponWalletItem(CouponWalletCategory.Membership, "天虹集团会员卡", "积分 236"),
    CouponWalletItem(CouponWalletCategory.Membership, "赵一鸣零食会员卡", "积分 0"),
    CouponWalletItem(CouponWalletCategory.Membership, "蛙好厨炭烧牛蛙会员卡", "积分 0"),
    CouponWalletItem(CouponWalletCategory.Membership, "拼多多会员卡", "积分 0"),
    CouponWalletItem(CouponWalletCategory.Membership, "美宜佳会员卡", "会员服务"),
    CouponWalletItem(CouponWalletCategory.Membership, "阿迪达斯会员卡", "会员服务"),
    CouponWalletItem(CouponWalletCategory.Transport, "深圳通交通卡", "余额 ¥36.20", "可刷码"),
    CouponWalletItem(CouponWalletCategory.Voucher, "饮品兑换券", "满 30 减 10 · 来福士 B1", "今日可用"),
    CouponWalletItem(CouponWalletCategory.Ticket, "发票", "共 3 张"),
    CouponWalletItem(CouponWalletCategory.Ticket, "人力资源和社会保障部", "电子社保卡"),
    CouponWalletItem(CouponWalletCategory.Ticket, "医保电子凭证", "国家医疗保障局监制"),
    CouponWalletItem(CouponWalletCategory.Ticket, "深圳信息职业技术学院", "学生卡")
)

/**
 * 对应 iOS CouponWalletViewController 和 CouponDetailViewController 的 Android M3 全屏卡包。
 *
 * iOS 卡包未对接远端卡券接口，因此本页仅展示与详情操作本地卡包状态，不伪造网络请求。
 * 测试流程：点击右侧“微信卡券”，切换四个分类标签并滚动卡券列表；点击条目进入详情，
 * 再使用左上返回关闭页面，确认页面由实体自下向上进入、自上向下退出且未创建 Dialog/Window。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CouponWalletFullScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { CouponWalletCategory.entries.size }
    var selectedItem by remember { mutableStateOf<CouponWalletItem?>(null) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        if (selectedItem != null) {
            selectedItem = null
        } else if (!isClosing) {
            isClosing = true
            onBack()
        }
    }

    BackHandler {
        navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = if (selectedItem == null) "卡包" else "卡券详情",
            onBack = ::navigateBack
        )
        val detailItem = selectedItem
        if (detailItem != null) {
            CouponWalletDetailPage(
                item = detailItem,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                CouponWalletCategory.entries.forEachIndexed { index, category ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(category.label, fontWeight = FontWeight.Normal) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                CouponWalletListPage(
                    category = CouponWalletCategory.entries[page],
                    items = couponWalletItems,
                    onItemClick = { selectedItem = it }
                )
            }
        }
    }
}

@Composable
private fun CouponWalletListPage(
    category: CouponWalletCategory,
    items: List<CouponWalletItem>,
    onItemClick: (CouponWalletItem) -> Unit
) {
    val categoryItems = remember(category, items) { items.filter { it.category == category } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = category.label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }
        items(categoryItems, key = { "${it.category.name}:${it.title}" }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) },
                colors = CardDefaults.cardColors(containerColor = couponContainerColor(item.category))
            ) {
                ListItem(
                    leadingContent = {
                        Icon(item.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = {
                        Text(item.title, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(item.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        item.badgeText?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                )
            }
        }
    }
}

@Composable
private fun CouponWalletDetailPage(item: CouponWalletItem, modifier: Modifier = Modifier) {
    var receivedStatus by remember(item) { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = couponContainerColor(item.category))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(item.category.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
                    Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.badgeText?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    Text("券码 ${item.code}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    Text("有效期：2026.07.06 - 2026.12.31", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(onClick = { receivedStatus = "已在卡包中" }, modifier = Modifier.fillMaxWidth()) {
                Text("已在卡包中", fontWeight = FontWeight.Normal)
            }
        }
        receivedStatus?.let { status ->
            item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun couponContainerColor(category: CouponWalletCategory) = when (category) {
    CouponWalletCategory.Membership -> MaterialTheme.colorScheme.secondaryContainer
    CouponWalletCategory.Transport -> MaterialTheme.colorScheme.tertiaryContainer
    CouponWalletCategory.Voucher -> MaterialTheme.colorScheme.primaryContainer
    CouponWalletCategory.Ticket -> MaterialTheme.colorScheme.surfaceVariant
}
