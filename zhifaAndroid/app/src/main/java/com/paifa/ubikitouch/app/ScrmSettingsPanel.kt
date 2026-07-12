package com.paifa.ubikitouch.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountOption
import com.paifa.ubikitouch.accessibility.scrm.ScrmConnectionTestResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DefaultScrmServerBaseUrl = "http://112.74.164.233:42718"

private enum class ScrmSettingsOperation {
    Loading,
    Saving,
    Testing,
    Clearing,
    SelectingAccount
}

@Composable
internal fun ScrmSettingsPanel(
    manager: ScrmSettingsManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<ScrmSettingsSummary?>(null) }
    var baseUrl by rememberSaveable { mutableStateOf(DefaultScrmServerBaseUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var revealApiKey by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<ScrmSettingsOperation?>(ScrmSettingsOperation.Loading) }
    var connectionResult by remember { mutableStateOf<ScrmConnectionTestResult?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationFailed by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    fun runOperation(
        nextOperation: ScrmSettingsOperation,
        block: suspend () -> Unit
    ) {
        if (operation != null) return
        operation = nextOperation
        operationMessage = null
        operationFailed = false
        scope.launch {
            try {
                block()
            } finally {
                operation = null
            }
        }
    }

    LaunchedEffect(manager) {
        val result = runCatching {
            withContext(Dispatchers.IO) { manager.loadSummary() }
        }
        result.onSuccess { loaded ->
            summary = loaded
            if (loaded.baseUrl.isNotBlank()) baseUrl = loaded.baseUrl
        }.onFailure { error ->
            operationFailed = true
            operationMessage = safeScrmErrorMessage(
                prefix = "读取配置失败",
                error = error,
                secretInput = apiKeyInput
            )
        }
        operation = null
    }

    val busy = operation != null
    val configured = summary?.isConfigured == true
    val canUseSavedKey = configured && apiKeyInput.isBlank()
    val canSubmit = baseUrl.isNotBlank() && (apiKeyInput.isNotBlank() || canUseSavedKey) && !busy

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.scrm_settings_title),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    connectionResult = null
                },
                enabled = !busy,
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.scrm_base_url)) }
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    connectionResult = null
                },
                enabled = !busy,
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.scrm_api_key)) },
                visualTransformation = if (revealApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { revealApiKey = !revealApiKey },
                        enabled = apiKeyInput.isNotEmpty() && !busy
                    ) {
                        Icon(
                            imageVector = if (revealApiKey) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = stringResource(
                                id = if (revealApiKey) {
                                    R.string.scrm_hide_api_key
                                } else {
                                    R.string.scrm_show_api_key
                                }
                            )
                        )
                    }
                },
                supportingText = {
                    Text(text = scrmApiKeySupportingText(summary))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit,
                    onClick = {
                        runOperation(ScrmSettingsOperation.Saving) {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    manager.save(baseUrl, apiKeyInput)
                                }
                            }
                            result.onSuccess { saved ->
                                summary = saved
                                baseUrl = saved.baseUrl
                                apiKeyInput = ""
                                operationMessage = "配置已安全保存"
                            }.onFailure { error ->
                                operationFailed = true
                                operationMessage = safeScrmErrorMessage(
                                    prefix = "保存失败",
                                    error = error,
                                    secretInput = apiKeyInput
                                )
                            }
                        }
                    }
                ) {
                    OperationProgress(
                        visible = operation == ScrmSettingsOperation.Saving
                    )
                    Text(text = stringResource(id = R.string.scrm_save))
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit,
                    onClick = {
                        runOperation(ScrmSettingsOperation.Testing) {
                            val tested = withContext(Dispatchers.IO) {
                                manager.testConnection(baseUrl, apiKeyInput)
                            }
                            connectionResult = tested
                            operationFailed = tested is ScrmConnectionTestResult.Failure
                            operationMessage = scrmConnectionStatusText(tested)
                        }
                    }
                ) {
                    OperationProgress(
                        visible = operation == ScrmSettingsOperation.Testing
                    )
                    Text(text = stringResource(id = R.string.scrm_test_connection))
                }
            }

            operationMessage?.let { message ->
                Text(
                    text = message,
                    color = if (operationFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            (connectionResult as? ScrmConnectionTestResult.Success)?.let { success ->
                if (success.accounts.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(id = R.string.scrm_account_selection),
                        style = MaterialTheme.typography.labelLarge
                    )
                    success.accounts.forEach { account ->
                        val selected = summary?.selectedDeviceUuid == account.deviceUuid &&
                            summary?.selectedWeChatId == account.weChatId
                        ScrmAccountRow(
                            account = account,
                            selected = selected,
                            enabled = configured && account.deviceUuid != null && !busy,
                            onSelect = {
                                val deviceUuid = account.deviceUuid ?: return@ScrmAccountRow
                                runOperation(ScrmSettingsOperation.SelectingAccount) {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            manager.selectAccount(deviceUuid, account.weChatId)
                                        }
                                    }
                                    result.onSuccess { selectedSummary ->
                                        summary = selectedSummary
                                        operationMessage = "已选择账号：${account.nickname}"
                                    }.onFailure { error ->
                                        operationFailed = true
                                        operationMessage = safeScrmErrorMessage(
                                            prefix = "选择账号失败",
                                            error = error,
                                            secretInput = apiKeyInput
                                        )
                                    }
                                }
                            }
                        )
                    }
                    if (!configured) {
                        Text(
                            text = stringResource(id = R.string.scrm_save_before_select),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (success.capabilityGroups.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(id = R.string.scrm_capability_status),
                        style = MaterialTheme.typography.labelLarge
                    )
                    success.capabilityGroups.forEach { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = group.groupName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.scrm_capability_group_counts,
                                    group.readyCount,
                                    group.blockedCount,
                                    group.pausedCount,
                                    group.unknownRuntimeCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                success.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (configured) {
                TextButton(
                    enabled = !busy,
                    onClick = { showClearConfirmation = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.scrm_clear))
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(text = stringResource(id = R.string.scrm_clear_confirm_title)) },
            text = { Text(text = stringResource(id = R.string.scrm_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        runOperation(ScrmSettingsOperation.Clearing) {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { manager.clear() }
                            }
                            result.onSuccess { cleared ->
                                summary = cleared
                                apiKeyInput = ""
                                connectionResult = null
                                operationMessage = "配置已清除"
                            }.onFailure { error ->
                                operationFailed = true
                                operationMessage = safeScrmErrorMessage(
                                    prefix = "清除失败",
                                    error = error,
                                    secretInput = apiKeyInput
                                )
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(id = R.string.scrm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun OperationProgress(visible: Boolean) {
    if (!visible) return
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp
    )
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun ScrmAccountRow(
    account: ScrmAccountOption,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.nickname, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    id = if (account.isDeviceOnline) {
                        R.string.scrm_device_online
                    } else {
                        R.string.scrm_device_offline
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun scrmApiKeySupportingText(summary: ScrmSettingsSummary?): String {
    return summary?.maskedApiKey?.let { "已保存：$it" } ?: "尚未保存"
}

internal fun scrmConnectionStatusText(result: ScrmConnectionTestResult): String {
    return when (result) {
        is ScrmConnectionTestResult.Failure -> result.message
        is ScrmConnectionTestResult.Success -> buildString {
            append("连接成功：")
            append(result.deviceCount)
            append(" 台设备（")
            append(result.onlineDeviceCount)
            append(" 台在线），")
            append(result.accounts.size)
            append(" 个微信账号")
            result.readyCapabilityCount?.let { ready ->
                append("，")
                append(ready)
                append(" 项可测试")
            }
            result.blockedCapabilityCount?.let { blocked ->
                append("，")
                append(blocked)
                append(" 项阻塞")
            }
            result.pausedCapabilityCount?.takeIf { it > 0 }?.let { paused ->
                append("，")
                append(paused)
                append(" 项暂停")
            }
            result.unknownRuntimeCapabilityCount?.takeIf { it > 0 }?.let { unknown ->
                append("，")
                append(unknown)
                append(" 项运行时未知")
            }
        }
    }
}

private fun safeScrmErrorMessage(
    prefix: String,
    error: Throwable,
    secretInput: String
): String {
    val detail = error.message
        ?.replace(secretInput.takeIf { it.isNotBlank() } ?: "\u0000", "****")
        ?.takeIf { it.isNotBlank() }
    return if (detail == null) prefix else "$prefix：$detail"
}
