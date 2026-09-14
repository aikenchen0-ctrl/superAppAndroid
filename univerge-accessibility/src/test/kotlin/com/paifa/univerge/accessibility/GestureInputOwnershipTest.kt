package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureInputOwnershipTest {
    @Test
    fun nativeSuccessOwnsOnlyTheReportedRegionAndSuppressesItsOverlayFallback() {
        val left = GestureRegionKey.Side(EdgeSide.LEFT, zoneId = 0)
        val bottom = GestureRegionKey.Bottom
        val leftRect = GestureInputRect(left = 0, top = 200, right = 24, bottom = 900)
        val bottomRect = GestureInputRect(left = 180, top = 2376, right = 900, bottom = 2400)

        val report = GestureInputOwnershipReport.resolve(
            requestedRegions = listOf(
                GestureInputRegion(left, leftRect),
                GestureInputRegion(bottom, bottomRect)
            ),
            nativeResults = listOf(
                NativeInputRegistration.success(left, leftRect),
                NativeInputRegistration.success(bottom, bottomRect)
            )
        )

        assertEquals(GestureInputOwner.NATIVE, report.ownerFor(left))
        assertEquals(GestureInputOwner.NATIVE, report.ownerFor(bottom))
        assertTrue(report.overlayRegions.isEmpty())
        assertEquals(leftRect, report.recordFor(left)?.rect)
        assertEquals(bottomRect, report.recordFor(bottom)?.rect)
    }

    @Test
    fun nativeFailureCreatesOverlayOnlyForTheMissingRegion() {
        val left = GestureRegionKey.Side(EdgeSide.LEFT, zoneId = 0)
        val right = GestureRegionKey.Side(EdgeSide.RIGHT, zoneId = 1)
        val bottom = GestureRegionKey.Bottom
        val rightRect = GestureInputRect(left = 1056, top = 300, right = 1080, bottom = 1000)

        val report = GestureInputOwnershipReport.resolve(
            requestedRegions = listOf(
                GestureInputRegion(left),
                GestureInputRegion(right, rightRect),
                GestureInputRegion(bottom)
            ),
            nativeResults = listOf(
                NativeInputRegistration.success(left),
                NativeInputRegistration.failure(right, "touch controller rejected region"),
                NativeInputRegistration.success(bottom)
            )
        )

        assertEquals(GestureInputOwner.NATIVE, report.ownerFor(left))
        assertEquals(GestureInputOwner.OVERLAY, report.ownerFor(right))
        assertEquals(GestureInputOwner.NATIVE, report.ownerFor(bottom))
        assertEquals(listOf(right), report.overlayRegions)
        assertEquals("touch controller rejected region", report.recordFor(right)?.degradationReason)
        assertEquals(rightRect, report.recordFor(right)?.rect)
    }

    @Test
    fun bottomIsASeparatePhysicalRegionAndDuplicateKeysAreRejected() {
        val bottom = GestureRegionKey.Bottom
        val report = GestureInputOwnershipReport.resolve(
            requestedRegions = listOf(GestureInputRegion(bottom)),
            nativeResults = listOf(NativeInputRegistration.success(bottom))
        )

        assertEquals(GestureInputOwner.NATIVE, report.ownerFor(bottom))
        assertTrue(report.recordFor(bottom)?.region is GestureRegionKey.Bottom)

        assertIllegalArgument {
            GestureInputOwnershipReport.resolve(
                requestedRegions = listOf(GestureInputRegion(bottom), GestureInputRegion(bottom)),
                nativeResults = emptyList()
            )
        }
        assertIllegalArgument {
            GestureInputOwnershipReport.resolve(
                requestedRegions = listOf(GestureInputRegion(bottom)),
                nativeResults = listOf(
                    NativeInputRegistration.success(bottom),
                    NativeInputRegistration.failure(bottom, "second result")
                )
            )
        }
    }

    @Test
    fun centralRegionCannotBeAssignedAFullScreenTouchOwner() {
        assertIllegalArgument {
            GestureInputOwnershipReport(
                records = listOf(
                    GestureInputOwnership(
                        region = GestureRegionKey.Center,
                        owner = GestureInputOwner.FULL_SCREEN_TOUCH,
                        rect = null,
                        degradationReason = "legacy full-screen helper"
                    )
                )
            )
        }
    }

    @Test
    fun reportCopiesRecordsAndRetainsOptionalRectangleAndReason() {
        val region = GestureRegionKey.Side(EdgeSide.RIGHT, zoneId = 3)
        val records = mutableListOf(
            GestureInputOwnership(
                region = region,
                owner = GestureInputOwner.OVERLAY,
                rect = GestureInputRect(1056, 0, 1080, 600),
                degradationReason = "native unavailable on this API"
            )
        )

        val report = GestureInputOwnershipReport(records)
        records += GestureInputOwnership(
            region = GestureRegionKey.Bottom,
            owner = GestureInputOwner.NONE,
            rect = null,
            degradationReason = "overlay add failed"
        )

        assertEquals(1, report.records.size)
        assertEquals(GestureInputOwner.OVERLAY, report.records.single().owner)
        assertNotNull(report.records.single().rect)
        assertEquals("native unavailable on this API", report.records.single().degradationReason)
        assertFalse(report.hasFullScreenTouchOwner)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (report.records as MutableList<GestureInputOwnership>).add(
                GestureInputOwnership(
                    region = GestureRegionKey.Bottom,
                    owner = GestureInputOwner.NONE,
                    degradationReason = "not part of this snapshot"
                )
            )
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
