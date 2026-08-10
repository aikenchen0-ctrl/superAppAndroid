package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiState
import com.paifa.ubikitouch.accessibility.floatingchat.tools.LocationCoordinator
import com.paifa.ubikitouch.accessibility.floatingchat.tools.LocationRuntimePort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationCoordinatorTest {
    @Test
    fun refreshUpdatesOptions() = runBlocking {
        val coordinator = LocationCoordinator(object : LocationRuntimePort {
            override suspend fun refresh() = listOf("home")
            override suspend fun send(optionId: String) = Unit
        })
        val next = coordinator.dispatch(LocationUiState(loading = true), LocationUiEvent.RefreshRequested)
        assertEquals(listOf("home"), next.options)
        assertEquals(false, next.loading)
        assertEquals(null, next.error)
    }

    @Test
    fun sendFailureIsVisible() = runBlocking {
        val coordinator = LocationCoordinator(object : LocationRuntimePort {
            override suspend fun refresh() = emptyList<String>()
            override suspend fun send(optionId: String): Unit = error("permission denied")
        })
        val next = coordinator.dispatch(LocationUiState(options = listOf("home")), LocationUiEvent.SendRequested("home"))
        assertEquals("permission denied", next.error)
        assertEquals(listOf("home"), next.options)
    }
}
