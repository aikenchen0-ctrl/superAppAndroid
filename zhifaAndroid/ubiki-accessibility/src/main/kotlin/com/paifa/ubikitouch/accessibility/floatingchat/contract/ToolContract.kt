package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class PaymentUiState(
    val title: String,
    val amountLabel: String,
    val noteLabel: String,
    val defaultNote: String,
    val confirmLabel: String,
    val amount: String = "",
    val note: String = ""
    , val recipients: List<ContactSummary> = emptyList()
    , val selectedRecipientId: String? = null
)

sealed interface PaymentUiEvent {
    data class AmountChanged(val value: String) : PaymentUiEvent
    data class NoteChanged(val value: String) : PaymentUiEvent
    data class RecipientSelected(val id: String) : PaymentUiEvent
    data class ConfirmRequested(val amount: String, val note: String, val recipientId: String?) : PaymentUiEvent
}

data class LocationUiState(
    val options: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class AiUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val temperature: String = "0.7",
    val maxTokens: String = "1024",
    val saving: Boolean = false,
    val testing: Boolean = false,
    val error: String? = null
)

sealed interface AiUiEvent {
    data class BaseUrlChanged(val value: String) : AiUiEvent
    data class ApiKeyChanged(val value: String) : AiUiEvent
    data class ModelChanged(val value: String) : AiUiEvent
    data class PromptChanged(val value: String) : AiUiEvent
    data class TemperatureChanged(val value: String) : AiUiEvent
    data class MaxTokensChanged(val value: String) : AiUiEvent
    data object SaveRequested : AiUiEvent
    data object TestRequested : AiUiEvent
    data object CloseRequested : AiUiEvent
}

data class QuickPhraseUiState(val phrases: List<String> = emptyList(), val editingIndex: Int? = null, val draft: String = "")

sealed interface QuickPhraseUiEvent {
    data object AddRequested : QuickPhraseUiEvent
    data class EditRequested(val index: Int) : QuickPhraseUiEvent
    data class DraftChanged(val value: String) : QuickPhraseUiEvent
    data object SaveRequested : QuickPhraseUiEvent
    data class DeleteRequested(val index: Int) : QuickPhraseUiEvent
    data class SendRequested(val index: Int) : QuickPhraseUiEvent
}

sealed interface LocationUiEvent {
    data object RefreshRequested : LocationUiEvent
    data class SendRequested(val optionId: String) : LocationUiEvent
}
