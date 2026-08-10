package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.tools.LocationRuntimeAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationRuntimeAdapterTest {
    @Test
    fun delegatesRefreshAndSend() = runBlocking {
        val sent = mutableListOf<String>()
        val adapter = LocationRuntimeAdapter(
            refreshOperation = { listOf("office") },
            sendOperation = { sent += it }
        )
        assertEquals(listOf("office"), adapter.refresh())
        adapter.send("office")
        assertEquals(listOf("office"), sent)
    }
}
