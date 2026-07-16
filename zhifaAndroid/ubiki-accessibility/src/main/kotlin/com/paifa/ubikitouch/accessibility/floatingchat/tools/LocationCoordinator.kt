package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiState

internal interface LocationRuntimePort {
    suspend fun refresh(): List<String>
    suspend fun send(optionId: String)
}

internal class LocationCoordinator(private val runtime: LocationRuntimePort) {
    suspend fun dispatch(state: LocationUiState, event: LocationUiEvent): LocationUiState = when (event) {
        LocationUiEvent.RefreshRequested -> runCatching {
            state.copy(options = runtime.refresh(), loading = false, error = null)
        }.getOrElse { state.copy(loading = false, error = it.message ?: "定位刷新失败") }
        is LocationUiEvent.SendRequested -> runCatching {
            runtime.send(event.optionId)
            state.copy(loading = false, error = null)
        }.getOrElse { state.copy(loading = false, error = it.message ?: "定位发送失败") }
    }
}
