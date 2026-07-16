package com.paifa.ubikitouch.accessibility.floatingchat.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackDecisionTest {
    @Test
    fun requestsPlaybackBeforeAPlayerExists() {
        assertEquals(
            VideoPlaybackDecision.RequestPlayback,
            decideVideoPlayback(false, failed = false, playerReady = false, playing = false, completed = false)
        )
    }

    @Test
    fun retriesAfterPreparationFailure() {
        assertEquals(
            VideoPlaybackDecision.Retry,
            decideVideoPlayback(true, failed = true, playerReady = false, playing = false, completed = false)
        )
    }

    @Test
    fun ignoresTapsUntilPlayerIsPrepared() {
        assertEquals(
            VideoPlaybackDecision.Ignore,
            decideVideoPlayback(true, failed = false, playerReady = false, playing = false, completed = false)
        )
    }

    @Test
    fun pausesPlayingVideoAndRestartsCompletedVideo() {
        assertEquals(
            VideoPlaybackDecision.Pause,
            decideVideoPlayback(true, failed = false, playerReady = true, playing = true, completed = false)
        )
        assertEquals(
            VideoPlaybackDecision.Restart,
            decideVideoPlayback(true, failed = false, playerReady = true, playing = false, completed = true)
        )
    }
}
