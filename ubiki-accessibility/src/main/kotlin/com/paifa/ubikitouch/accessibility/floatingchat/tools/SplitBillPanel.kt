package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.scrm.PaymentAmount
import com.paifa.ubikitouch.accessibility.scrm.SplitBillDraft
import com.paifa.ubikitouch.core.model.FloatingChatContact

/** Mirrors iOS group collection selection and per-person summary without issuing a payment request. */
@Composable
internal fun SplitBillPanel(members: List<FloatingChatContact>) {
    var amount by remember { mutableStateOf("") }
    var selectedIds by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }
    var submitted by remember { mutableStateOf(false) }
    var paymentPreviewRequested by remember { mutableStateOf(false) }
    val selected = members.filter { it.id in selectedIds }
    val draft = runCatching { SplitBillDraft(PaymentAmount.parseFen(amount), selected.size) }.getOrNull()

    if (submitted && draft != null) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextLabel("群收款", 16.sp, color = Color(0xFF222222), maxLines = 1)
            TextLabel("¥${PaymentAmount.formatYuan(draft.totalFen)}", 28.sp, color = Color(0xFF222222), maxLines = 1)
            TextLabel("共 ${selected.size} 人 · 人均 ¥${PaymentAmount.formatYuan(draft.perPersonFen)}", 12.sp, color = Color(0xFF777777), maxLines = 1)
            selected.forEachIndexed { index, member ->
                val paid = index % 2 == 0 && index != 0
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextLabel(member.name, 13.sp, color = Color(0xFF333333), maxLines = 1)
                    TextLabel(if (paid) "已支付" else "待支付", 11.sp, color = if (paid) Color(0xFF1AAD19) else Color(0xFF888888), maxLines = 1)
                }
            }
            TextLabel(
                if (paymentPreviewRequested) "付款请求已组装" else "付款（仅预览）",
                13.sp,
                color = if (paymentPreviewRequested) Color(0xFF777777) else Color(0xFF1AAD19),
                modifier = Modifier.clickable(enabled = !paymentPreviewRequested) { paymentPreviewRequested = true },
                maxLines = 1
            )
            TextLabel("当前接口文档未提供独立 AA 收款写接口，未发送。", 10.sp, color = Color(0xFF888888), maxLines = 2)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextLabel("群收款", 16.sp, color = Color(0xFF222222), maxLines = 1)
        TextLabel("已选 ${selected.size} 人 · 人均 ${draft?.let { "¥${PaymentAmount.formatYuan(it.perPersonFen)}" } ?: "待填写"}", 12.sp, color = Color(0xFF777777), maxLines = 1)
        BasicTextField(
            value = amount,
            onValueChange = { amount = it; submitted = false },
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
            singleLine = true,
            decorationBox = { inner ->
                if (amount.isBlank()) TextLabel("总金额", 13.sp, color = Color(0xFF888888), maxLines = 1)
                inner()
            }
        )
        TextLabel(
            if (selectedIds.size == members.size) "取消全选" else "全选",
            12.sp,
            color = Color(0xFF1AAD19),
            modifier = Modifier.clickable {
                selectedIds = if (selectedIds.size == members.size) emptySet() else members.map { it.id }.toSet()
                submitted = false
            },
            maxLines = 1
        )
        members.forEach { member ->
            val selectedMember = member.id in selectedIds
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedIds = if (selectedMember) selectedIds - member.id else selectedIds + member.id
                    submitted = false
                }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextLabel(member.name, 13.sp, color = Color(0xFF333333), maxLines = 1)
                TextLabel(if (selectedMember) "已选择" else "未选择", 11.sp, color = if (selectedMember) Color(0xFF1AAD19) else Color(0xFF888888), maxLines = 1)
            }
        }
        TextLabel(
            "发起收款",
            13.sp,
            color = Color(0xFF1AAD19),
            modifier = Modifier.clickable(enabled = draft != null) { submitted = true },
            maxLines = 2
        )
    }
}
