package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.content.Context
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode

internal class QuickPhraseActions(
    private val context: Context,
    private val quickPhrases: () -> List<String>,
    private val onQuickPhrasesChanged: (List<String>) -> Unit,
    private val onSendText: (String) -> Unit,
    private val onBottomPanelModeChanged: (BottomPanelMode) -> Unit
) {
    fun updateQuickPhrases(nextPhrases: List<String>) {
        val normalized = normalizeQuickPhrases(nextPhrases)
        onQuickPhrasesChanged(normalized)
        saveQuickPhrases(context, normalized)
    }

    fun sendQuickPhrase(phrase: String) {
        val text = phrase.trim()
        if (text.isNotEmpty()) {
            onSendText(text)
            updateQuickPhrases(listOf(text) + quickPhrases().filterNot { it == text })
            onBottomPanelModeChanged(BottomPanelMode.None)
        }
    }

    fun addQuickPhrase(phrase: String) {
        updateQuickPhrases(listOf(phrase) + quickPhrases())
    }

    fun updateQuickPhrase(index: Int, phrase: String) {
        updateQuickPhrases(
            quickPhrases().mapIndexed { phraseIndex, existing ->
                if (phraseIndex == index) phrase else existing
            }
        )
    }

    fun deleteQuickPhrase(index: Int) {
        updateQuickPhrases(quickPhrases().filterIndexed { phraseIndex, _ -> phraseIndex != index })
    }
}
