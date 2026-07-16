package com.paifa.ubikitouch.accessibility.floatingchat.media

internal enum class VideoPlaybackDecision {
    RequestPlayback,
    Retry,
    Ignore,
    Pause,
    Restart,
    Play
}

internal fun decideVideoPlayback(
    playbackRequested: Boolean,
    failed: Boolean,
    playerReady: Boolean,
    playing: Boolean,
    completed: Boolean
): VideoPlaybackDecision {
    if (!playbackRequested) return VideoPlaybackDecision.RequestPlayback
    if (failed) return VideoPlaybackDecision.Retry
    if (!playerReady) return VideoPlaybackDecision.Ignore
    if (playing) return VideoPlaybackDecision.Pause
    if (completed) return VideoPlaybackDecision.Restart
    return VideoPlaybackDecision.Play
}
