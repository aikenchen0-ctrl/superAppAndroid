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
    data object ConfirmRequested : PaymentUiEvent
}

data class LocationUiState(
    val options: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

sealed interface LocationUiEvent {
    data object RefreshRequested : LocationUiEvent
    data class SendRequested(val optionId: String) : LocationUiEvent
}
