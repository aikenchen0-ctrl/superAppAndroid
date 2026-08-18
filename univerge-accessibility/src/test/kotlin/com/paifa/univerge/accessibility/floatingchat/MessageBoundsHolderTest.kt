package com.paifa.univerge.accessibility.floatingchat

import androidx.compose.ui.geometry.Rect
import com.paifa.univerge.accessibility.floatingchat.message.MessageBoundsHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBoundsHolderTest {
    @Test
    fun unchangedMeasurementDoesNotNotifyConnector() {
        val holder = MessageBoundsHolder()
        val bounds = Rect(1f, 2f, 100f, 48f)
        var notifications = 0

        assertTrue(holder.update(bounds) { notifications += 1 })
        assertFalse(holder.update(bounds) { notifications += 1 })

        assertEquals(1, notifications)
        assertEquals(bounds, holder.value)
    }
}
