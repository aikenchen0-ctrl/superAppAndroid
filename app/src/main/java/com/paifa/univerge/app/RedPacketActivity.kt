package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.FloatingChatRedPacketBridge

class RedPacketActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val session = FloatingChatRedPacketBridge.currentSession()
            RedPacketScreen(
                recipientNames = session?.recipientNames.orEmpty(),
                onBack = ::finish
            )
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) FloatingChatRedPacketBridge.notifyClosed()
        super.onDestroy()
    }
}

private val StatusBarHeight = 30.dp
private val RedPacketRed = Color(0xFFFA5151)
private val RedPacketDisabled = Color(0xFFE5E5E5)
private val RedPacketText = Color(0xFF191919)

@Composable
private fun RedPacketScreen(recipientNames: List<String>, onBack: () -> Unit) {
    var amount by remember { mutableStateOf(value = "66") }
    var blessing by remember { mutableStateOf("恭喜发财，大吉大利") }
    val names = recipientNames.take(3)
    val maxCount = names.size.coerceAtLeast(1)
    var packetCount by remember(maxCount) { mutableStateOf(1) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
        Spacer(Modifier.height(StatusBarHeight))
        RedPacketToolbar(onBack)
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 28.dp)) {
            Text(
                text = "发给${names.joinToString("、").ifBlank { "联系人" }}",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            RedPacketInput(amount, "金额", onValueChanged = { amount = it })
            Spacer(Modifier.height(14.dp))
            RedPacketInput(blessing, "备注", onValueChanged = { blessing = it })
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("红包个数", color = RedPacketText, fontSize = 15.sp)
                Spacer(Modifier.width(12.dp))
                Text("$packetCount/$maxCount", color = Color(0xFF666666), fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { packetCount-- },
                    enabled = packetCount > 1
                ) { Icon(Icons.Filled.Remove, "减少红包个数", tint = if (packetCount > 1) RedPacketText else Color(0xFFBDBDBD)) }
                IconButton(
                    onClick = { packetCount++ },
                    enabled = packetCount < maxCount
                ) { Icon(Icons.Filled.Add, "增加红包个数", tint = if (packetCount < maxCount) RedPacketText else Color(0xFFBDBDBD)) }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPacketRed, contentColor = Color.White)
            ) { Text("塞钱进红包", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun RedPacketInput(value: String, hint: String, onValueChanged: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(horizontal = 14.dp, vertical = 15.dp),
        singleLine = true,
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) Text(hint, color = Color(0xFF999999), fontSize = 15.sp)
                inner()
            }
        }
    )
}

@Composable
private fun RedPacketToolbar(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).background(Color.White)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = RedPacketText)
        }
        Text("红包", modifier = Modifier.align(Alignment.Center), color = RedPacketText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
