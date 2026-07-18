package com.paifa.ubikitouch.accessibility.floatingchat.account

import android.content.Context
import android.content.SharedPreferences
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import java.util.Properties
internal data class FloatingChatAccountProfile(
    val accountId: String,
    val name: String,
    val phone: String,
    val signature: String,
    val gender: String,
    val company: String,
    val title: String,
    val region: String,
    val wechatId: String,
    val email: String,
    val tags: String,
    val avatarInitials: String,
    val avatarColor: Long,
    val avatarImageUri: String = ""
)

internal fun defaultAccountProfileFor(account: FloatingChatContact): FloatingChatAccountProfile {
    return FloatingChatAccountProfile(
        accountId = account.id,
        name = account.name,
        phone = "",
        signature = account.description,
        gender = "未设置",
        company = account.description.substringAfter("路", "").trim().ifBlank { "浮窗聊天" },
        title = account.description.substringBefore("路").trim().ifBlank { "璐﹀彿" },
        region = "",
        wechatId = account.id.replace("-", "_"),
        email = "",
        tags = "",
        avatarInitials = account.initials,
        avatarColor = account.avatarColor,
        avatarImageUri = account.avatarUrl.orEmpty()
    )
}

internal fun FloatingChatAccountProfile.toContact(fallback: FloatingChatContact): FloatingChatContact {
    return fallback.copy(
        name = name.ifBlank { fallback.name },
        initials = avatarInitials.ifBlank { name.take(2).ifBlank { fallback.initials } },
        description = accountProfileSubtitle(this).ifBlank { fallback.description },
        avatarColor = avatarColor,
        avatarUrl = avatarImageUri.takeIf { it.isNotBlank() } ?: fallback.avatarUrl
    )
}

internal fun accountProfileEditorFieldKeys(): List<String> {
    return listOf(
        "avatarImage",
        "name",
        "phone",
        "signature",
        "gender",
        "company",
        "title",
        "region",
        "wechatId",
        "email",
        "tags"
    )
}

internal fun rightRailAccountAvatarSupportsLongPressEdit(): Boolean = true

internal fun rightRailSelectedAccountAvatarUsesHighlightRing(): Boolean = true

internal fun rightRailSelectedAccountAvatarHighlightStrokeDp(): Int = railSelectedAvatarHighlightStrokeDp()

internal fun leftRailSelectedAvatarUsesAccountHighlightRing(): Boolean = true

internal fun leftRailSelectedAvatarHighlightStrokeDp(): Int = railSelectedAvatarHighlightStrokeDp()

internal fun railSelectedAvatarHighlightStrokeDp(): Int = 3

internal fun rightRailAccountAvatarClickSelectsSendingAccount(): Boolean = true

internal fun accountProfileEditorSupportsImageAvatarUpload(): Boolean = true

internal fun accountProfileEditorHidesAvatarColorPalette(): Boolean = true

internal fun accountProfileEditorPersistsChanges(): Boolean = true

internal fun accountProfileEditorSupportsWechatQrCode(): Boolean = true

internal fun accountProfileQrPayload(profile: FloatingChatAccountProfile): String {
    return profile.wechatId.trim().ifBlank { profile.accountId }
}

internal fun cardToolSendsEditedAccountProfileCard(): Boolean = true

internal fun cardToolUsesSelectedAccountInsteadOfThreadDefault(): Boolean = true

internal fun cardToolOpensAccountPickerInsteadOfDirectSend(): Boolean = true

internal fun cardToolAccountPickerShowsAllAccounts(): Boolean = true

internal fun contactLinkMessageReusesAccountCardPreview(): Boolean = true

internal fun accountProfileMessageForToolAction(
    action: FloatingChatToolAction,
    profile: FloatingChatAccountProfile,
    baseMessage: FloatingChatMessage
): FloatingChatMessage {
    return if (action == FloatingChatToolAction.Card) {
        accountProfileCardMessage(profile, baseMessage)
    } else {
        baseMessage
    }
}

internal fun accountProfileCardMessage(
    profile: FloatingChatAccountProfile,
    baseMessage: FloatingChatMessage
): FloatingChatMessage {
    val safeName = profile.name.trim().ifBlank { "未命名账号" }
    return baseMessage.copy(
        type = FloatingChatMessageType.ContactLink,
        text = "推名片：$safeName",
        presentation = FloatingChatMessagePresentation.SpecialCard,
        cardKind = FloatingChatContactCardKind.Personal,
        cardName = safeName,
        cardSubtitle = accountProfileSubtitle(profile),
        detail = accountProfileDetail(profile),
        thumbnailUrl = profile.avatarImageUri.takeIf { it.isNotBlank() },
        resourceUrl = "https://aiff.app/cards/${profile.accountId}-${profile.wechatId.ifBlank { safeName }.toCardSlug()}"
    )
}

internal fun accountProfileSubtitle(profile: FloatingChatAccountProfile): String {
    return listOf(profile.title, profile.company, profile.region)
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .joinToString(" · ")
}

internal fun accountProfileDetail(profile: FloatingChatAccountProfile): String {
    return listOfNotNull(
        profile.phone.trim().takeIf { it.isNotEmpty() }?.let { "电话 $it" },
        profile.wechatId.trim().takeIf { it.isNotEmpty() }?.let { "微信 $it" },
        profile.email.trim().takeIf { it.isNotEmpty() }?.let { "邮箱 $it" },
        profile.gender.trim().takeIf { it.isNotEmpty() && it != "未设置" }?.let { "性别 $it" },
        profile.signature.trim().takeIf { it.isNotEmpty() }?.let { "签名 $it" },
        profile.tags.trim().takeIf { it.isNotEmpty() }?.let { "标签 $it" }
    ).joinToString(" · ")
}

private fun String.toCardSlug(): String {
    return lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "profile" }
}

internal fun loadAccountProfile(
    context: Context,
    account: FloatingChatContact
): FloatingChatAccountProfile {
    val fallback = defaultAccountProfileFor(account)
    val stored = accountProfilePrefs(context).getString(account.id, null) ?: return fallback
    return runCatching {
        val properties = Properties().apply { load(StringReader(stored)) }
        FloatingChatAccountProfile(
            accountId = account.id,
            name = properties.getProperty("name", fallback.name),
            phone = properties.getProperty("phone", fallback.phone),
            signature = properties.getProperty("signature", fallback.signature),
            gender = properties.getProperty("gender", fallback.gender),
            company = properties.getProperty("company", fallback.company),
            title = properties.getProperty("title", fallback.title),
            region = properties.getProperty("region", fallback.region),
            wechatId = properties.getProperty("wechatId", fallback.wechatId),
            email = properties.getProperty("email", fallback.email),
            tags = properties.getProperty("tags", fallback.tags),
            avatarInitials = properties.getProperty("avatarInitials", fallback.avatarInitials),
            avatarColor = properties.getProperty("avatarColor", fallback.avatarColor.toString()).toLongOrNull()
                ?: fallback.avatarColor,
            avatarImageUri = properties.getProperty("avatarImageUri", fallback.avatarImageUri)
                .ifBlank { fallback.avatarImageUri }
        )
    }.getOrElse {
        fallback
    }
}

internal fun saveAccountProfile(
    context: Context,
    profile: FloatingChatAccountProfile
) {
    val properties = Properties().apply {
        setProperty("name", profile.name)
        setProperty("phone", profile.phone)
        setProperty("signature", profile.signature)
        setProperty("gender", profile.gender)
        setProperty("company", profile.company)
        setProperty("title", profile.title)
        setProperty("region", profile.region)
        setProperty("wechatId", profile.wechatId)
        setProperty("email", profile.email)
        setProperty("tags", profile.tags)
        setProperty("avatarInitials", profile.avatarInitials)
        setProperty("avatarColor", profile.avatarColor.toString())
        setProperty("avatarImageUri", profile.avatarImageUri)
    }
    val writer = StringWriter()
    properties.store(writer, null)
    accountProfilePrefs(context)
        .edit()
        .putString(profile.accountId, writer.toString())
        .apply()
}

private fun accountProfilePrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(ACCOUNT_PROFILE_PREFS, Context.MODE_PRIVATE)
}

private const val ACCOUNT_PROFILE_PREFS = "floating_chat_account_profiles"
