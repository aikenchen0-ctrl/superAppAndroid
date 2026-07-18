package com.paifa.ubikitouch.accessibility.floatingchat.account

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import java.util.EnumMap

@Composable
internal fun AccountEditOverlay(
    account: FloatingChatContact,
    profile: FloatingChatAccountProfile,
    onPickAvatar: () -> Unit,
    onSave: (FloatingChatAccountProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(account.id) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 560.dp)
                .pointerInput(account.id) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 10.dp
        ) {
            AccountEditPanel(
                account = account,
                profile = profile,
                onPickAvatar = onPickAvatar,
                onSave = onSave,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AccountEditPanel(
    account: FloatingChatContact,
    profile: FloatingChatAccountProfile,
    onPickAvatar: () -> Unit,
    onSave: (FloatingChatAccountProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(profile.accountId, profile.name) { mutableStateOf(profile.name) }
    var phone by remember(profile.accountId, profile.phone) { mutableStateOf(profile.phone) }
    var signature by remember(profile.accountId, profile.signature) { mutableStateOf(profile.signature) }
    var gender by remember(profile.accountId, profile.gender) { mutableStateOf(profile.gender) }
    var company by remember(profile.accountId, profile.company) { mutableStateOf(profile.company) }
    var title by remember(profile.accountId, profile.title) { mutableStateOf(profile.title) }
    var region by remember(profile.accountId, profile.region) { mutableStateOf(profile.region) }
    var wechatId by remember(profile.accountId, profile.wechatId) { mutableStateOf(profile.wechatId) }
    var email by remember(profile.accountId, profile.email) { mutableStateOf(profile.email) }
    var tags by remember(profile.accountId, profile.tags) { mutableStateOf(profile.tags) }
    var avatarInitials by remember(profile.accountId, profile.avatarInitials) { mutableStateOf(profile.avatarInitials) }
    val avatarColor = profile.avatarColor
    var avatarImageUri by remember(profile.accountId, profile.avatarImageUri) { mutableStateOf(profile.avatarImageUri) }
    var showQr by remember(profile.accountId) { mutableStateOf(false) }
    val previewProfile = profile.copy(
        name = name.trim(),
        phone = phone.trim(),
        signature = signature.trim(),
        gender = gender.trim(),
        company = company.trim(),
        title = title.trim(),
        region = region.trim(),
        wechatId = wechatId.trim(),
        email = email.trim(),
        tags = tags.trim(),
        avatarInitials = avatarInitials.trim(),
        avatarColor = avatarColor,
        avatarImageUri = avatarImageUri.trim()
    )
    val genderChoices = remember { listOf("未设置", "男", "女", "其他") }

    LazyColumn(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountProfileAvatarPreview(profile = previewProfile)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TextLabel(
                        text = "璐﹀彿璧勬枡",
                        size = 15.sp,
                        weight = FontWeight.Bold,
                        color = OverlayTokens.panelPrimaryText,
                        maxLines = 1
                    )
                    TextLabel(
                        text = "推名片会发送这里保存的真实资料",
                        size = 9.sp,
                        color = OverlayTokens.panelSecondaryText,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AccountProfileChoiceButton(label = "更换头像", onClick = onPickAvatar)
                        AccountProfileChoiceButton(label = "二维码", onClick = { showQr = true })
                        if (avatarImageUri.isNotBlank()) {
                            AccountProfileChoiceButton(label = "绉婚櫎鍥剧墖", onClick = { avatarImageUri = "" })
                        }
                    }
                }
            }
        }
        item {
            AccountProfileTextField(value = avatarInitials, onValueChange = { avatarInitials = it.take(4) }, label = "头像文字")
        }
        item { AccountProfileTextField(value = name, onValueChange = { name = it }, label = "鍚嶅瓧") }
        item { AccountProfileTextField(value = phone, onValueChange = { phone = it }, label = "电话") }
        item { AccountProfileTextField(value = signature, onValueChange = { signature = it }, label = "绛惧悕") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                genderChoices.forEach { choice ->
                    AssistChip(
                        onClick = { gender = choice },
                        label = {
                            TextLabel(text = choice, size = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (gender == choice) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (gender == choice) OverlayTokens.accent else OverlayTokens.panelSecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = OverlayTokens.quickPhraseRow,
                            labelColor = OverlayTokens.panelPrimaryText
                        ),
                        border = BorderStroke(1.dp, OverlayTokens.panelBorder)
                    )
                }
            }
        }
        item { AccountProfileTextField(value = company, onValueChange = { company = it }, label = "公司") }
        item { AccountProfileTextField(value = title, onValueChange = { title = it }, label = "ְλ") }
        item { AccountProfileTextField(value = region, onValueChange = { region = it }, label = "鍦板尯") }
        item { AccountProfileTextField(value = wechatId, onValueChange = { wechatId = it }, label = "微信号") }
        item { AccountProfileTextField(value = email, onValueChange = { email = it }, label = "閭") }
        item { AccountProfileTextField(value = tags, onValueChange = { tags = it }, label = "鏍囩") }
        item {
            AccountCardPreview(profile = previewProfile)
        }
        if (showQr) {
            item {
                AccountProfileQrPreviewCard(
                    profile = previewProfile,
                    onDismiss = { showQr = false }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                AccountProfileChoiceButton(label = "取消", onClick = onDismiss)
                AccountProfileChoiceButton(
                    label = "淇濆瓨",
                    onClick = {
                        onSave(
                            previewProfile.copy(
                                name = previewProfile.name.ifBlank { account.name },
                                avatarInitials = previewProfile.avatarInitials.ifBlank {
                                    previewProfile.name.take(2).ifBlank { account.initials }
                                },
                                wechatId = previewProfile.wechatId.ifBlank { account.id.replace("-", "_") },
                                avatarImageUri = previewProfile.avatarImageUri
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountProfileAvatarPreview(profile: FloatingChatAccountProfile) {
    val context = LocalContext.current
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = profile.avatarImageUri.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(profile.avatarColor))
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = profile.avatarInitials.ifBlank { profile.name.take(2) },
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountProfileQrPreviewCard(
    profile: FloatingChatAccountProfile,
    onDismiss: () -> Unit
) {
    val payload = accountProfileQrPayload(profile)
    val qrBitmap = remember(payload) { createAccountQrBitmap(payload, 480) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextLabel(
                text = "微信二维码",
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            AccountProfileChoiceButton(label = "关闭", onClick = onDismiss)
        }
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFB))
                .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                TextLabel(
                    text = "二维码生成失败",
                    size = 10.sp,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 1
                )
            }
        }
        TextLabel(
            text = payload,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
    }
}

private fun createAccountQrBitmap(payload: String, sizePx: Int): Bitmap? {
    val normalized = payload.trim().takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1)
        }
        val matrix = QRCodeWriter().encode(
            normalized,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val dark = 0xFF111820.toInt()
        val light = 0xFFF8FAFB.toInt()
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] = if (matrix[x, y]) dark else light
            }
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }.getOrNull()
}

@Composable
internal fun AccountCardPreviewContent(
    name: String,
    subtitle: String,
    detail: String,
    avatarText: String,
    avatarColor: Color,
    avatarImageUri: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.contactCard)
            .border(1.dp, OverlayTokens.contactCardBorder, RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccountCardAvatar(
            text = avatarText.ifBlank { name.take(2) },
            background = avatarColor,
            imageUri = avatarImageUri
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = name,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = subtitle,
                size = 10.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = detail,
                size = 9.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun AccountCardAvatar(
    text: String,
    background: Color,
    imageUri: String?
) {
    val context = LocalContext.current
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = imageUri?.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, OverlayTokens.panelBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = text,
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = {
            TextLabel(text = label, size = 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        },
        textStyle = TextStyle.Default.copy(
            color = OverlayTokens.panelPrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OverlayTokens.panelPrimaryText,
            unfocusedTextColor = OverlayTokens.panelPrimaryText,
            cursorColor = OverlayTokens.accent,
            focusedBorderColor = OverlayTokens.inputFocus,
            unfocusedBorderColor = OverlayTokens.inputStroke,
            focusedContainerColor = OverlayTokens.input.copy(alpha = 0.45f),
            unfocusedContainerColor = OverlayTokens.input.copy(alpha = 0.32f),
            focusedLabelColor = OverlayTokens.panelSecondaryText,
            unfocusedLabelColor = OverlayTokens.panelSecondaryText
        )
    )
}

@Composable
private fun AccountCardPreview(profile: FloatingChatAccountProfile) {
    AccountCardPreviewContent(
        name = profile.name.ifBlank { "未命名账号" },
        subtitle = accountProfileSubtitle(profile).ifBlank { "完善公司、职位或地区后会展示在这里" },
        detail = accountProfileDetail(profile).ifBlank { "电话、微信号、签名会随名片一起发送" },
        avatarText = profile.avatarInitials.ifBlank { profile.name.take(2) },
        avatarColor = Color(profile.avatarColor),
        avatarImageUri = profile.avatarImageUri
    )
}

@Composable
internal fun AccountCardPickerPanel(
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    onSendAccountCard: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        TextLabel(
            text = "推荐名片",
            size = 12.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = "选择要发送的账号名片",
            size = 9.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
        if (accounts.isEmpty()) {
            TextLabel(
                text = "暂无可推荐账号",
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(accounts) { _, account ->
                    val profile = accountProfiles[account.id] ?: defaultAccountProfileFor(account)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSendAccountCard(account.id) }
                    ) {
                        AccountCardPreview(profile = profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProfileChoiceButton(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            TextLabel(
                text = label,
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier
            .height(28.dp)
            .widthIn(min = 42.dp, max = 74.dp),
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = OverlayTokens.control,
            labelColor = OverlayTokens.panelPrimaryText
        ),
        border = BorderStroke(1.dp, OverlayTokens.hairline)
    )
}
