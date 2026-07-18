package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.content.Context
import android.content.SharedPreferences

internal fun loadQuickPhrases(context: Context): List<String> {
    val stored = quickPhrasePrefs(context).getString(KEY_QUICK_PHRASES, null)
        ?: return DefaultQuickPhrases
    return normalizeQuickPhrases(
        stored
            .split(QUICK_PHRASE_SEPARATOR)
            .map { phrase -> phrase.trim() }
    )
}

internal fun saveQuickPhrases(context: Context, phrases: List<String>) {
    quickPhrasePrefs(context)
        .edit()
        .putString(KEY_QUICK_PHRASES, normalizeQuickPhrases(phrases).joinToString(QUICK_PHRASE_SEPARATOR))
        .apply()
}

internal fun normalizeQuickPhrases(phrases: List<String>): List<String> {
    return phrases
        .asSequence()
        .map { phrase -> phrase.trim() }
        .filter { phrase -> phrase.isNotEmpty() }
        .distinct()
        .take(QuickPhraseMaxCount)
        .toList()
}

private fun quickPhrasePrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(QUICK_PHRASE_PREFS, Context.MODE_PRIVATE)
}

internal fun quickPhraseToolOpensPanelInsteadOfDirectSend(): Boolean = true

internal fun quickPhrasePanelShowsRecentPhrases(): Boolean = true

internal fun quickPhrasePanelSupportsCrud(): Boolean = true

internal fun quickPhrasePanelCanSendSelectedPhrase(): Boolean = true

internal fun defaultQuickPhrases(): List<String> = DefaultQuickPhrases

private const val QUICK_PHRASE_PREFS = "floating_chat_quick_phrases"
private const val KEY_QUICK_PHRASES = "quick_phrases"
private const val QUICK_PHRASE_SEPARATOR = "\u001F"
private const val QuickPhraseMaxCount = 8
private val DefaultQuickPhrases = listOf(
    "收到，我先看一下，稍后同步进展。",
    "这个我确认后回复你。",
    "可以，按这个方案推进。"
)
