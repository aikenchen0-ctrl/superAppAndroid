package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal data class RightRailToolCatalogItem(
    val label: String,
    val icon: ImageVector,
    val action: FloatingChatToolAction? = null,
    val opensBackgroundRemoval: Boolean = false,
    val opensFriendManagement: Boolean = false,
    val opensContactRelations: Boolean = false,
    val opensLeftSidebar: Boolean = false,
    val opensLiveLocation: Boolean = false,
    val opensOfficialArticle: Boolean = false,
    val opensMusic: Boolean = false,
    val opensSplitBill: Boolean = false,
    val opensGroupInfo: Boolean = false,
    val isBubbleAppearanceToggle: Boolean = false
)

internal val rightRailToolCatalog = listOf(
    item("群信息", Icons.Filled.Contacts, opensGroupInfo = true), item("眨眼测试", Icons.Filled.Visibility, FloatingChatToolAction.Blink),
    item("语音助手", Icons.Filled.Mic, FloatingChatToolAction.AiVoice), item("遥感套索", Icons.Filled.LocationOn),
    item("智能抠图", Icons.Filled.Image, opensBackgroundRemoval = true), item("UI组件", Icons.Filled.AutoAwesome), item("OpenAPI", Icons.Filled.SmartToy, FloatingChatToolAction.Command),
    item("侧边特效", Icons.Filled.AutoAwesome, FloatingChatToolAction.SideEffect), item("账号设备", Icons.Filled.PhoneIphone, FloatingChatToolAction.Device), item("好友管理", Icons.Filled.Contacts, FloatingChatToolAction.Contacts),
    item("同步群聊", Icons.Filled.Contacts), item("侧边特效", Icons.Filled.AutoAwesome, FloatingChatToolAction.SideEffect), item("3D气泡", Icons.Filled.BubbleChart),
    item("AI自动回复", Icons.Filled.SmartToy, FloatingChatToolAction.Assistant), item("朋友圈", Icons.Filled.Collections, FloatingChatToolAction.Moments),
    item("素材库", Icons.Filled.Collections, FloatingChatToolAction.MomentMaterials), item("微信小程序", Icons.Filled.Article),
    item("视屏号发布", Icons.Filled.VideoLibrary, FloatingChatToolAction.Finder), item("客户档案", Icons.Filled.AccountBox),
    item("通讯录", Icons.Filled.Contacts, FloatingChatToolAction.Contacts), item("申请审核", Icons.Filled.Article), item("隐藏用户", Icons.Filled.AccountBox),
    item("左侧全部", Icons.Filled.Collections), item("携带名字", Icons.Filled.AccountBox), item("快捷语", Icons.Filled.Mic, FloatingChatToolAction.QuickPhrase),
    item("拍摄照片", Icons.Filled.CameraAlt, FloatingChatToolAction.Camera), item("图片", Icons.Filled.Image, FloatingChatToolAction.Gallery),
    item("视频", Icons.Filled.VideoLibrary, FloatingChatToolAction.Gallery), item("视频号视频", Icons.Filled.VideoLibrary), item("视频号直播", Icons.Filled.PlayCircle),
    item("位置信息", Icons.Filled.LocationOn, FloatingChatToolAction.Location), item("实时位置", Icons.Filled.LocationOn), item("推名片", Icons.Filled.AccountBox, FloatingChatToolAction.Card),
    item("群邀请卡", Icons.Filled.Contacts, FloatingChatToolAction.GroupInvite), item("网页链接", Icons.Filled.Article), item("公众号文章", Icons.Filled.Article), item("小程序卡片", Icons.Filled.Article),
    item("音乐分享", Icons.Filled.PlayCircle), item("收藏分享", Icons.Filled.Collections, FloatingChatToolAction.Favorite), item("多选", Icons.Filled.Collections),
    item("接龙消息", Icons.Filled.Article, FloatingChatToolAction.Relay), item("文件/文档", Icons.Filled.Folder, FloatingChatToolAction.Files), item("语音消息", Icons.Filled.Mic, FloatingChatToolAction.Voice),
    item("语音通话", Icons.Filled.Call), item("视频通话", Icons.Filled.VideoLibrary), item("红包", Icons.Filled.AccountBox, FloatingChatToolAction.RedPacket),
    item("转账", Icons.Filled.AccountBox, FloatingChatToolAction.Transfer), item("AA收款", Icons.Filled.AccountBox, opensSplitBill = true), item("微信卡券", Icons.Filled.CardGiftcard, FloatingChatToolAction.Wallet)
).mapIndexed { index, item ->
    when (index) {
        5 -> item.copy(action = FloatingChatToolAction.UiComponents)
        16 -> item.copy(action = FloatingChatToolAction.MiniProgram)
        9 -> item.copy(opensFriendManagement = true)
        19 -> item.copy(opensContactRelations = true)
        22 -> item.copy(opensLeftSidebar = true)
        18 -> item.copy(action = FloatingChatToolAction.Notes)
        20 -> item.copy(action = FloatingChatToolAction.ReviewRequests)
        21 -> item.copy(action = FloatingChatToolAction.HiddenUsers)
        23 -> item.copy(action = FloatingChatToolAction.SendName)
        27 -> item.copy(action = FloatingChatToolAction.Video)
        28 -> item.copy(action = FloatingChatToolAction.ChannelsVideo)
        29 -> item.copy(action = FloatingChatToolAction.ChannelsLive)
        34 -> item.copy(action = FloatingChatToolAction.WebLink)
        35 -> item.copy(opensOfficialArticle = true)
        37 -> item.copy(opensMusic = true)
        43 -> item.copy(action = FloatingChatToolAction.VoiceCall)
        44 -> item.copy(action = FloatingChatToolAction.VideoCall)
        36 -> item.copy(action = FloatingChatToolAction.MiniProgram)
        31 -> item.copy(opensLiveLocation = true)
        else -> item
    }
}

private fun item(
    label: String,
    icon: ImageVector,
    action: FloatingChatToolAction? = null,
    opensBackgroundRemoval: Boolean = false,
    opensLiveLocation: Boolean = false,
    opensOfficialArticle: Boolean = false,
    opensMusic: Boolean = false,
    opensSplitBill: Boolean = false,
    opensGroupInfo: Boolean = false,
    isBubbleAppearanceToggle: Boolean = icon == Icons.Filled.BubbleChart
) = RightRailToolCatalogItem(
    label = label,
    icon = icon,
    action = action,
    opensBackgroundRemoval = opensBackgroundRemoval,
    opensLiveLocation = opensLiveLocation,
    opensOfficialArticle = opensOfficialArticle,
    opensMusic = opensMusic,
    opensSplitBill = opensSplitBill,
    opensGroupInfo = opensGroupInfo,
    isBubbleAppearanceToggle = isBubbleAppearanceToggle
)
