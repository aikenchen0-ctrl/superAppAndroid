package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.FloatingChatCouponWalletBridge

class CouponWalletActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CouponWalletScreen(onBack = ::close)
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            FloatingChatCouponWalletBridge.notifyClosed()
        }
        super.onDestroy()
    }

    private fun close() {
        finish()
    }
}

private val StatusBarHeight = 30.dp
private val WalletPrimaryColor = Color(0xFF191919)
private val WalletSecondaryColor = Color(0xFF888888)
private val WalletDividerColor = Color(0xFFEFEFEF)

@Composable
private fun CouponWalletScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(StatusBarHeight))
        WalletToolbar(onBack = onBack)
        WalletEntry(label = "交通卡", icon = Icons.Filled.DirectionsBus)
        WalletEntry(label = "券和礼品卡", icon = Icons.Filled.CardGiftcard)
        WalletEntry(label = "票证", icon = Icons.Filled.ConfirmationNumber)
        WalletEntry(label = "会员卡", icon = Icons.Filled.Badge)
        Text(
            text = "最近使用",
            modifier = Modifier.padding(start = 20.dp, top = 28.dp),
            color = WalletPrimaryColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WalletToolbar(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = WalletPrimaryColor
            )
        }
        Text(
            text = "卡包",
            modifier = Modifier.align(Alignment.Center),
            color = WalletPrimaryColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = {},
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = WalletPrimaryColor
            )
        }
    }
}

@Composable
private fun WalletEntry(label: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = {})
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = WalletPrimaryColor, modifier = Modifier.size(23.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = WalletPrimaryColor, fontSize = 16.sp)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "$label 详情",
            tint = WalletSecondaryColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
