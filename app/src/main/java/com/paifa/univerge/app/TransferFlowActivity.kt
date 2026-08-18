package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import com.paifa.univerge.accessibility.FloatingChatTransferBridge
import com.paifa.univerge.accessibility.FloatingChatTransferRecipient

class TransferFlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val session = FloatingChatTransferBridge.currentSession()
            if (session == null) {
                EmptyTransferScreen(onClose = ::close)
            } else {
                TransferFlowScreen(
                    recipients = session.recipients,
                    startsWithRecipientSelection = session.requiresRecipientSelection,
                    onClose = ::close
                )
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) FloatingChatTransferBridge.notifyClosed()
        super.onDestroy()
    }

    private fun close() = finish()
}

private val StatusBarHeight = 30.dp
private val TransferTextColor = Color(0xFF191919)
private val TransferGreen = Color(0xFF07C160)
private val TransferDisabled = Color(0xFFE5E5E5)
private val TransferYellow = Color(0xFFF6C343)

@Composable
private fun TransferFlowScreen(
    recipients: List<FloatingChatTransferRecipient>,
    startsWithRecipientSelection: Boolean,
    onClose: () -> Unit
) {
    var recipient by remember(recipients, startsWithRecipientSelection) {
        mutableStateOf(if (startsWithRecipientSelection) null else recipients.firstOrNull())
    }
    var selectingRecipient by remember(startsWithRecipientSelection) { mutableStateOf(startsWithRecipientSelection) }
    BackHandler {
        if (selectingRecipient) onClose() else selectingRecipient = startsWithRecipientSelection
    }
    if (selectingRecipient) {
        RecipientSelectionScreen(
            recipients = recipients,
            selectedRecipient = recipient,
            onRecipientSelected = { recipient = it },
            onNext = { selectingRecipient = false },
            onBack = onClose
        )
    } else {
        TransferScreen(recipient = recipient, onBack = { if (startsWithRecipientSelection) selectingRecipient = true else onClose() })
    }
}

@Composable
private fun RecipientSelectionScreen(
    recipients: List<FloatingChatTransferRecipient>,
    selectedRecipient: FloatingChatTransferRecipient?,
    onRecipientSelected: (FloatingChatTransferRecipient) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Spacer(Modifier.height(StatusBarHeight))
        TransferToolbar(title = "选择收款人", onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recipients, key = { it.id }) { contact ->
                RecipientRow(contact, selected = contact.id == selectedRecipient?.id, onClick = { onRecipientSelected(contact) })
            }
        }
        Button(
            onClick = onNext,
            enabled = selectedRecipient != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(48.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TransferGreen,
                disabledContainerColor = TransferDisabled,
                contentColor = Color.White,
                disabledContentColor = Color(0xFFAAAAAA)
            )
        ) { Text("下一步", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun RecipientRow(contact: FloatingChatTransferRecipient, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF89A3B8), modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(contact.name, color = TransferTextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(contact.weChatId.ifBlank { contact.id }, color = Color(0xFF888888), fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Filled.Check, "已选择", tint = TransferGreen, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TransferScreen(recipient: FloatingChatTransferRecipient?, onBack: () -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(value = "88") }
    var note by remember { mutableStateOf("") }
    var paymentPassword by remember { mutableStateOf("") }
    var operationStatus by remember { mutableStateOf<String?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
        Spacer(Modifier.height(StatusBarHeight))
        TransferToolbar(title = "转账", onBack = onBack)
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 28.dp)) {
            Text("转账给${recipient?.name.orEmpty()}", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            TransferInput(value = amount, hint = "金额", onValueChanged = { amount = it })
            Spacer(Modifier.height(14.dp))
            TransferInput(value = note, hint = "备注", onValueChanged = { note = it })
            Spacer(Modifier.height(14.dp))
            TransferInput(value = paymentPassword, hint = "支付密码（6位）", onValueChanged = {
                paymentPassword = it.filter(Char::isDigit).take(6)
            })
            operationStatus?.let { status ->
                Spacer(Modifier.height(10.dp))
                Text(status, color = if (status.startsWith("转账提交失败")) Color(0xFFD93025) else Color(0xFF666666), fontSize = 13.sp)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    operationInProgress = true
                    operationStatus = "正在提交转账…"
                    FloatingChatTransferBridge.submit(
                        context = context,
                        recipient = recipient,
                        amount = amount,
                        note = note,
                        paymentPassword = paymentPassword
                    ) { result ->
                        operationInProgress = false
                        operationStatus = result
                    }
                },
                enabled = !operationInProgress && recipient != null && paymentPassword.length == 6,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TransferYellow,
                    disabledContainerColor = Color(0xFFE5E5E5),
                    contentColor = TransferTextColor,
                    disabledContentColor = Color(0xFF999999)
                )
            ) { Text(if (operationInProgress) "提交中…" else "转账", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun TransferInput(value: String, hint: String, onValueChanged: (String) -> Unit) {
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
private fun TransferToolbar(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).background(Color.White)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TransferTextColor)
        }
        Text(title, modifier = Modifier.align(Alignment.Center), color = TransferTextColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyTransferScreen(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Spacer(Modifier.height(StatusBarHeight))
        TransferToolbar(title = "转账", onBack = onClose)
    }
}
