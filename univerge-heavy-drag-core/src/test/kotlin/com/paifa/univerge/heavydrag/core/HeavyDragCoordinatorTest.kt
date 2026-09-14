package com.paifa.univerge.heavydrag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyDragCoordinatorTest {
    @Test
    fun lightTapCommitsClickWithoutStartingOrMovingDrag() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification(
                    gestureId = gestureId,
                    eventType = HeavyTouchEventType.PRESS,
                    strength = HeavyTouchStrength.LIGHT,
                    confidence = 0.99f,
                    eventTimeMillis = 8L
                )
            )
        )
        coordinator.onPointerMove(0, HeavyPoint(6f, 5f), 12L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(6f, 5f), 20L, gestureId)

        assertEquals(listOf("click"), log)
        assertEquals(HeavyDragState.IDLE, coordinator.state)
        assertNull(coordinator.activeSession)
    }

    @Test
    fun heavyDragStartsOnlyForMatchingCurrentGestureId() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator(
            policy = HeavyDragPolicy(minHeavyConfidence = 0.8f)
        )
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = gestureId + 99L,
                    confidence = 1f,
                    eventTimeMillis = 10L
                )
            )
        )
        assertEquals(HeavyDragState.PRESSED_PENDING, coordinator.state)
        assertTrue(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = gestureId,
                    confidence = 0.9f,
                    eventTimeMillis = 12L
                )
            )
        )
        coordinator.onPointerMove(0, HeavyPoint(15f, 5f), 20L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(15f, 5f), 30L, gestureId)

        assertEquals(listOf("start", "move", "end"), log)
    }

    @Test
    fun lowConfidenceHeavyResultLeavesWindowOpenForAQualifiedResult() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator(HeavyDragPolicy(minHeavyConfidence = 0.8f))
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertFalse(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 0.4f, 10L)))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 0.9f, 20L)))
        coordinator.onPointerUp(0, HeavyPoint(5f, 5f), 30L, gestureId)

        assertEquals(listOf("start", "end"), log)
    }

    @Test
    fun classificationFromFinishedGestureCannotStartTheNextGesture() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val firstGestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        coordinator.onPointerUp(0, HeavyPoint(5f, 5f), 20L, firstGestureId)
        val secondGestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 30L))

        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = firstGestureId,
                    confidence = 1f,
                    eventTimeMillis = 35L
                )
            )
        )
        coordinator.onPointerUp(0, HeavyPoint(5f, 5f), 40L, secondGestureId)

        assertEquals(listOf("click", "click"), log)
        assertEquals(HeavyDragState.IDLE, coordinator.state)
    }

    @Test
    fun heavyResultAfterClassificationWindowExpiresIsIgnored() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator(
            policy = HeavyDragPolicy(classificationTimeoutMillis = 100L)
        )
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        coordinator.onPointerMove(0, HeavyPoint(6f, 5f), 150L, gestureId)
        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = gestureId,
                    confidence = 1f,
                    eventTimeMillis = 150L
                )
            )
        )
        coordinator.onPointerUp(0, HeavyPoint(6f, 5f), 160L, gestureId)

        assertEquals(listOf("click"), log)
    }

    @Test
    fun oldTouchTimestampCannotResurrectAClassificationAfterWindowExpires() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator(
            policy = HeavyDragPolicy(classificationTimeoutMillis = 100L)
        )
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        coordinator.onPointerMove(0, HeavyPoint(6f, 5f), 200L, gestureId)
        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = gestureId,
                    confidence = 1f,
                    eventTimeMillis = 50L
                )
            )
        )
        coordinator.onPointerUp(0, HeavyPoint(6f, 5f), 210L, gestureId)

        assertEquals(listOf("click"), log)
    }

    @Test
    fun classificationTimestampBeforeDownIsIgnored() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 100L))
        assertFalse(
            coordinator.onClassification(
                HeavyTouchClassification.heavyPress(
                    gestureId = gestureId,
                    confidence = 1f,
                    eventTimeMillis = 50L
                )
            )
        )
        coordinator.onPointerUp(0, HeavyPoint(5f, 5f), 120L, gestureId)

        assertEquals(listOf("click"), log)
    }

    @Test
    fun multiPointerCancelsSessionAndRepeatedTerminationIsIdempotent() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(8f, 5f), 20L, gestureId, pointerCount = 2)
        coordinator.onPointerCancel(0, 30L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(8f, 5f), 40L, gestureId)

        assertEquals(listOf("start", "cancel"), log)
        assertEquals(HeavyDragState.IDLE, coordinator.state)
        assertNull(coordinator.activeSession)
    }

    @Test
    fun cancelWithMultiplePointersAlsoTerminatesTheActiveSession() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        coordinator.onPointerCancel(0, 20L, gestureId, pointerCount = 2)

        assertEquals(listOf("cancel"), log)
        assertNull(coordinator.activeSession)
        assertEquals(HeavyDragState.IDLE, coordinator.state)
    }

    @Test
    fun multiPointerMoveReportsConsumedCancellationDispatch() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        val dispatch = coordinator.onPointerMove(
            pointerId = 0,
            position = HeavyPoint(6f, 5f),
            timestampMillis = 20L,
            gestureId = gestureId,
            pointerCount = 2
        )

        assertTrue(dispatch.consumed)
        assertEquals(HeavyDragCancelReason.MULTI_POINTER, dispatch.cancelReason)
        assertEquals(listOf("cancel"), log)
    }

    @Test
    fun dropZoneEmitsEnterOverExitInOrderWhenPointerLeaves() {
        val events = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(mutableListOf()))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "zone",
                bounds = HeavyRect(20f, 0f, 40f, 20f),
                mode = HeavyTargetMode.DROP_ZONE,
                onEnter = { events += "enter" },
                onOver = { events += "over" },
                onExit = { events += "exit" },
                onDrop = { events += "drop" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(25f, 5f), 20L, gestureId)
        coordinator.onPointerMove(0, HeavyPoint(28f, 5f), 30L, gestureId)
        coordinator.onPointerMove(0, HeavyPoint(5f, 5f), 40L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(5f, 5f), 50L, gestureId)

        assertEquals(listOf("enter", "over", "exit"), events)
    }

    @Test
    fun dropZoneCommitsDropInsteadOfExitWhenReleasedInside() {
        val events = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(mutableListOf()))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "zone",
                bounds = HeavyRect(20f, 0f, 40f, 20f),
                mode = HeavyTargetMode.DROP_ZONE,
                onEnter = { events += "enter" },
                onOver = { events += "over" },
                onExit = { events += "exit" },
                onDrop = { events += "drop" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(25f, 5f), 20L, gestureId)
        coordinator.onPointerMove(0, HeavyPoint(28f, 5f), 30L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(28f, 5f), 40L, gestureId)

        assertEquals(listOf("enter", "over", "drop"), events)
    }

    @Test
    fun overlapTargetUsesTwentyPercentEnterAndTenPercentExitHysteresis() {
        val events = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(mutableListOf()))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "overlap",
                bounds = HeavyRect(8f, 0f, 18f, 10f),
                mode = HeavyTargetMode.OVERLAP,
                overlapEnterThreshold = 0.20f,
                overlapExitThreshold = 0.10f,
                onOverlapEnter = { events += "enter" },
                onOverlap = { events += "over" },
                onOverlapExit = { events += "exit" },
                onOverlapCommit = { events += "commit" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(4.5f, 5f), 20L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(4.5f, 5f), 30L, gestureId)

        assertEquals(listOf("enter", "over", "commit"), events)
    }

    @Test
    fun overlapTargetExitsOnlyAfterRatioFallsBelowTenPercent() {
        val events = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(mutableListOf()))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "overlap",
                bounds = HeavyRect(8f, 0f, 18f, 10f),
                mode = HeavyTargetMode.OVERLAP,
                onOverlapEnter = { events += "enter" },
                onOverlap = { events += "over" },
                onOverlapExit = { events += "exit" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(4f, 5f), 20L, gestureId)
        coordinator.onPointerMove(0, HeavyPoint(3.5f, 5f), 30L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(3.5f, 5f), 40L, gestureId)

        assertEquals(listOf("enter", "over", "exit"), events)
    }

    @Test
    fun oneSessionKeepsOneSourceAndSecondPointerDownCancelsIt() {
        val firstLog = mutableListOf<String>()
        val secondLog = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(
            source(firstLog, id = "first", priority = 1)
        )
        coordinator.registerSource(
            source(secondLog, id = "second", priority = 2)
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertEquals("second", coordinator.activeSession?.sourceId)
        coordinator.onPointerDown(1, HeavyPoint(5f, 5f), 5L, pointerCount = 2)

        assertEquals(listOf("cancel"), secondLog)
        assertTrue(firstLog.isEmpty())
        assertNull(coordinator.activeSession)
        assertEquals(HeavyDragState.IDLE, coordinator.state)
        assertNotNull(gestureId)
    }

    @Test
    fun cancellationFromDragStartDoesNotEmitTargetsAfterTermination() {
        val events = mutableListOf<String>()
        lateinit var coordinator: HeavyDragCoordinator
        val source = HeavyDragSource(
            id = "source",
            bounds = HeavyRect(0f, 0f, 10f, 10f),
            onDragStart = {
                events += "start"
                coordinator.cancel(HeavyDragCancelReason.ACTION_CANCEL, 10L)
            },
            onDragCancel = { events += "cancel" }
        )
        coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source)
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "zone",
                bounds = HeavyRect(0f, 0f, 20f, 20f),
                mode = HeavyTargetMode.DROP_ZONE,
                onEnter = { events += "enter" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))

        assertEquals(listOf("start", "cancel"), events)
        assertNull(coordinator.activeSession)
    }

    @Test
    fun disposingActiveSourceRegistrationCancelsThatSessionExactlyOnce() {
        val log = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        val registration = coordinator.registerSource(source(log))

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        registration.dispose()
        assertNull(coordinator.activeSession)
        registration.dispose()
        coordinator.onPointerCancel(0, 20L, gestureId)

        assertEquals(listOf("start", "cancel"), log)
        assertNull(coordinator.activeSession)
        assertTrue(registration.isDisposed)
    }

    @Test
    fun targetCallbackCancellingDuringMovePreventsPostTerminationDragMove() {
        val events = mutableListOf<String>()
        lateinit var coordinator: HeavyDragCoordinator
        coordinator = HeavyDragCoordinator()
        coordinator.registerSource(
            HeavyDragSource(
                id = "source",
                bounds = HeavyRect(0f, 0f, 10f, 10f),
                onDragStart = { events += "start" },
                onDrag = { events += "move" },
                onDragCancel = { events += "cancel" }
            )
        )
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "zone",
                bounds = HeavyRect(20f, 0f, 40f, 20f),
                onEnter = { events += "enter" },
                onOver = {
                    events += "over"
                    coordinator.cancel(HeavyDragCancelReason.ACTION_CANCEL, 30L)
                }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        coordinator.onPointerMove(0, HeavyPoint(25f, 5f), 20L, gestureId)
        coordinator.onPointerMove(0, HeavyPoint(26f, 5f), 30L, gestureId)

        assertEquals(listOf("start", "enter", "move", "over", "cancel"), events)
        assertNull(coordinator.activeSession)
    }

    @Test
    fun activeOverlapCallbacksUseTheLatestRegisteredTargetBounds() {
        val events = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(mutableListOf()))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "overlap",
                bounds = HeavyRect(8f, 0f, 18f, 10f),
                mode = HeavyTargetMode.OVERLAP,
                onOverlapEnter = { event -> events += "enter:${event.targetBounds.left}" },
                onOverlap = { event -> events += "over:${event.targetBounds.left}" }
            )
        )

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(5f, 5f), 0L))
        assertTrue(coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 10L)))
        assertTrue(coordinator.updateTargetBounds("overlap", HeavyRect(7f, 0f, 17f, 10f)))
        coordinator.onPointerMove(0, HeavyPoint(5f, 5f), 20L, gestureId)

        assertEquals(listOf("enter:8.0", "over:7.0"), events)
    }

    @Test
    fun zeroAreaSourceDoesNotCapturePointerAtItsSingleCoordinate() {
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(
            HeavyDragSource(
                id = "empty",
                bounds = HeavyRect(0f, 0f, 0f, 0f)
            )
        )

        assertNull(coordinator.onPointerDown(0, HeavyPoint(0f, 0f), 0L))
        assertEquals(HeavyDragState.IDLE, coordinator.state)
    }

    @Test
    fun rectanglesComputeContainmentAndIntersectionWithoutAndroidTypes() {
        val source = HeavyRect(0f, 0f, 10f, 10f)
        val target = HeavyRect(8f, 8f, 18f, 18f)

        assertTrue(source.contains(HeavyPoint(0f, 0f)))
        assertFalse(source.contains(HeavyPoint(10.1f, 5f)))
        assertEquals(4f, source.intersectionArea(target), 0.0001f)
        assertEquals(0.04f, source.overlapRatio(target), 0.0001f)
    }

    @Test
    fun registeredSourceAndTargetBoundsCanBeUpdatedWithoutReplacingCallbacks() {
        val sourceLog = mutableListOf<String>()
        val targetLog = mutableListOf<String>()
        val coordinator = HeavyDragCoordinator()
        coordinator.registerSource(source(sourceLog, bounds = HeavyRect(0f, 0f, 1f, 1f)))
        coordinator.registerTarget(
            HeavyDragTarget(
                id = "target",
                bounds = HeavyRect(20f, 0f, 30f, 10f),
                onEnter = { targetLog += "enter" }
            )
        )

        assertTrue(coordinator.updateSourceBounds("source", HeavyRect(10f, 0f, 20f, 10f)))
        assertTrue(coordinator.updateTargetBounds("target", HeavyRect(0f, 0f, 10f, 10f)))
        assertEquals(HeavyRect(10f, 0f, 20f, 10f), coordinator.sources.get("source")?.bounds)
        assertEquals(HeavyRect(0f, 0f, 10f, 10f), coordinator.targets.get("target")?.bounds)

        val gestureId = requireNotNull(coordinator.onPointerDown(0, HeavyPoint(12f, 5f), 0L))
        coordinator.onClassification(HeavyTouchClassification.heavyPress(gestureId, 1f, 1L))
        coordinator.onPointerMove(0, HeavyPoint(2f, 5f), 2L, gestureId)
        assertEquals(listOf("enter"), targetLog)
        assertEquals(listOf("start", "move"), sourceLog)
    }

    private fun source(
        log: MutableList<String>,
        id: String = "source",
        priority: Int = 0,
        bounds: HeavyRect = HeavyRect(0f, 0f, 10f, 10f)
    ): HeavyDragSource {
        return HeavyDragSource(
            id = id,
            payload = "payload-$id",
            bounds = bounds,
            priority = priority,
            onClick = { log += "click" },
            onDragStart = { log += "start" },
            onDrag = { log += "move" },
            onDragEnd = { log += "end" },
            onDragCancel = { log += "cancel" }
        )
    }
}
