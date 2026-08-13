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
    val opensCouponWallet: Boolean = false
)

internal val rightRailToolCatalog = listOf(
    item("群信息", Icons.Filled.Contacts), item("眨眼测试", Icons.Filled.Visibility, FloatingChatToolAction.Blink),
    item("语音助手", Icons.Filled.Mic, FloatingChatToolAction.AiVoice), item("遥感套索", Icons.Filled.LocationOn),
    item("智能抠图", Icons.Filled.Image), item("UI组件", Icons.Filled.AutoAwesome), item("OpenAI", Icons.Filled.SmartToy),
    item("侧边特效", Icons.Filled.AutoAwesome), item("账号设置", Icons.Filled.AccountBox), item("好友管理", Icons.Filled.Contacts, FloatingChatToolAction.Contacts),
    item("同步群聊", Icons.Filled.Contacts), item("侧边特效", Icons.Filled.AutoAwesome), item("3D气泡", Icons.Filled.BubbleChart),
    item("AI自动回复", Icons.Filled.SmartToy, FloatingChatToolAction.Assistant), item("朋友圈", Icons.Filled.Collections, FloatingChatToolAction.Moments),
    item("素材库", Icons.Filled.Collections, FloatingChatToolAction.MomentMaterials), item("微信小程序", Icons.Filled.Article),
    item("视屏号发布", Icons.Filled.VideoLibrary, FloatingChatToolAction.Finder), item("客户档案", Icons.Filled.AccountBox),
    item("通讯录", Icons.Filled.Contacts, FloatingChatToolAction.Contacts), item("申请审核", Icons.Filled.Article), item("隐藏用户", Icons.Filled.AccountBox),
    item("左侧全部", Icons.Filled.Collections), item("携带名字", Icons.Filled.AccountBox), item("快捷语", Icons.Filled.Mic, FloatingChatToolAction.QuickPhrase),
    item("拍摄照片", Icons.Filled.CameraAlt, FloatingChatToolAction.Camera), item("图片", Icons.Filled.Image, FloatingChatToolAction.Gallery),
    item("视频", Icons.Filled.VideoLibrary, FloatingChatToolAction.Gallery), item("视频号视频", Icons.Filled.VideoLibrary), item("视频号直播", Icons.Filled.PlayCircle),
    item("位置信息", Icons.Filled.LocationOn, FloatingChatToolAction.Location), item("实时位置", Icons.Filled.LocationOn), item("推名片", Icons.Filled.AccountBox, FloatingChatToolAction.Card),
    item("群邀请卡", Icons.Filled.Contacts), item("网页链接", Icons.Filled.Article), item("公众号文章", Icons.Filled.Article), item("小程序卡片", Icons.Filled.Article),
    item("音乐分享", Icons.Filled.PlayCircle), item("收藏分享", Icons.Filled.Collections, FloatingChatToolAction.Favorite), item("多选", Icons.Filled.Collections),
    item("接龙消息", Icons.Filled.Article), item("文件/文档", Icons.Filled.Folder, FloatingChatToolAction.Files), item("语音消息", Icons.Filled.Mic, FloatingChatToolAction.Voice),
    item("语音通话", Icons.Filled.Call), item("视频通话", Icons.Filled.VideoLibrary), item("红包", Icons.Filled.AccountBox, FloatingChatToolAction.RedPacket),
    item("转账", Icons.Filled.AccountBox, FloatingChatToolAction.Transfer), item("AA收款", Icons.Filled.AccountBox), item("微信卡券", Icons.Filled.CardGiftcard, opensCouponWallet = true)
)

private fun item(
    label: String,
    icon: ImageVector,
    action: FloatingChatToolAction? = null,
    opensCouponWallet: Boolean = false
) = RightRailToolCatalogItem(label = label, icon = icon, action = action, opensCouponWallet = opensCouponWallet)
