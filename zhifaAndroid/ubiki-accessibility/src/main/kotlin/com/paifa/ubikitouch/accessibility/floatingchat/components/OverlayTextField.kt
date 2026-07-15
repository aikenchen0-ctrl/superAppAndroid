package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayTextFieldEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayTextFieldProps
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatThemeValues

@Composable
internal fun OverlayTextField(
    props: OverlayTextFieldProps,
    onEvent: (OverlayTextFieldEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = props.value,
        onValueChange = { onEvent(OverlayTextFieldEvent.ValueChanged(it)) },
        enabled = props.enabled,
        isError = props.errorMessage != null,
        singleLine = true,
        placeholder = {
            Text(
                text = props.placeholder,
                style = FloatingChatThemeValues.typography.body
            )
        },
        supportingText = props.errorMessage?.let { message ->
            { Text(message, style = FloatingChatThemeValues.typography.status) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
            onSend = { onEvent(OverlayTextFieldEvent.Submitted) }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FloatingChatThemeValues.dimensions.controlHeight)
    )
}
