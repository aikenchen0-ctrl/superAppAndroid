package com.paifa.ubikitouch.accessibility.floatingchat.media

import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation

private const val StandaloneImageMaxWidthDp = 168
private const val StandaloneMediaMinHeightDp = 72
private const val StandaloneMediaMaxHeightDp = 176

internal fun standaloneMediaHeightDp(
    orientation: FloatingChatThumbnailOrientation?,
    mediaAspectRatio: Float? = null
): Int {
    val widthDp = StandaloneImageMaxWidthDp.toFloat()
    val aspectRatio = mediaAspectRatio ?: when (orientation) {
        FloatingChatThumbnailOrientation.Vertical -> 0.68f
        FloatingChatThumbnailOrientation.Horizontal,
        null -> 16f / 9f
    }
    val height = (widthDp / aspectRatio).toInt()
    return height.coerceIn(104, 236)
}

internal fun standaloneMediaShowsInlineActions(): Boolean = false

internal fun messageBubbleShowsAccessChips(): Boolean = false

internal fun mediaPreviewShowsActions(): Boolean = true

internal fun mediaPreviewUsesScrim(): Boolean = true

internal fun mediaPreviewUsesCardShadow(): Boolean = false

internal fun standaloneMediaListShowsAccessChips(): Boolean = false

internal fun standaloneMediaListShowsWatermark(): Boolean = false

internal fun standaloneMediaListUsesVerticalCropScrim(): Boolean = false

internal fun standaloneMediaListUsesAspectFit(): Boolean = true

internal fun standaloneMediaListUsesUniformSquareShape(): Boolean = false

internal fun standaloneMediaUsesCombinedClickForPreviewAndLongPress(): Boolean = true

internal fun standaloneMediaLongPressOpensMessageMenu(): Boolean = true

internal fun standaloneMediaClickTogglesSelectionInMultiSelect(): Boolean = true

internal fun mediaPreviewShowsAccessChips(): Boolean = true

internal fun mediaPreviewShowsWatermark(): Boolean = false

internal fun mediaPreviewUsesVerticalCropScrim(): Boolean = false

internal fun mediaPreviewUsesAspectFit(): Boolean = true

internal fun mediaPreviewUsesCropScaling(): Boolean = false

internal fun mediaPreviewMaxWidthFraction(): Float = 1f

internal fun mediaPreviewMaxHeightFraction(): Float = 1f

internal fun mediaPreviewSupportsZoom(): Boolean = true

internal fun mediaPreviewSingleTapDismisses(): Boolean = true

internal fun mediaPreviewSupportsSwipeBetweenThreadMedia(): Boolean = true

internal fun mediaPreviewImageTransformLetsPagerHandleSingleFingerSwipe(): Boolean = true

internal fun mediaPreviewDismissGestureUsesVerticalOnlyDrag(): Boolean = true

internal fun mediaPreviewSupportsDragToDismiss(): Boolean = true

internal fun mediaPreviewUsesShrinkDismissAnimation(): Boolean = true

internal fun mediaPreviewBackNavigationCloses(): Boolean = true

internal fun mediaPreviewUsesBlackBackdrop(): Boolean = true

internal fun mediaPreviewHidesFloatingChatButton(): Boolean = false

internal fun mediaPreviewCoversSystemBars(): Boolean = true

internal fun mediaPreviewRequestsImmersiveSystemBars(): Boolean = true

internal fun mediaPreviewOverlayAvoidsDecorInsets(): Boolean = true

internal fun mediaPreviewUsesDedicatedFullscreenActivity(): Boolean = false

internal fun mediaPreviewKeepsAccessibilityOverlayHidden(): Boolean = false

internal fun mediaPreviewImmersiveUsesDecorViewInsetsController(): Boolean = true

internal fun mediaPreviewRunsInsideFloatingOverlay(): Boolean = true

internal fun mediaPreviewRestoresOverlayWithoutRecreate(): Boolean = true

internal fun mediaPickerDoesNotCollapseFloatingChatToButton(): Boolean = true

internal fun mediaPickerRestoresExpandedOverlayAfterResult(): Boolean = true

internal fun mediaPickerHidesWholeFloatingChatWindow(): Boolean = true

internal fun mediaPickerKeepsFloatingChatWindowPersistent(): Boolean = true

internal fun cameraToolKeepsFloatingChatWindowPersistent(): Boolean = true

internal fun mediaPickerShowsLightweightTransitionSurface(): Boolean = false

internal fun mediaPickerRestoresOverlayBeforeDeliveringPickedMedia(): Boolean = true

internal fun mediaPickerProcessesPickedMediaOffMainThread(): Boolean = true

internal fun mediaPreviewIgnoresExistingDismissSignalOnOpen(): Boolean = true

internal fun mediaPreviewMinimumZoom(): Float = 1f

internal fun mediaPreviewMaximumZoom(): Float = 4f

internal fun mediaPreviewVideoUsesNativePlayer(): Boolean = true

internal fun mediaPreviewVideoUsesAspectFit(): Boolean = true

internal fun mediaPreviewVideoUsesOriginalAspectFrame(): Boolean = true

internal fun mediaPreviewVideoPlayButtonUsesMaterialIcon(): Boolean = true

internal fun mediaPreviewVideoPauseButtonUsesMaterialIcon(): Boolean = true

internal fun mediaPreviewVideoUsesImageZoomGestures(): Boolean = false

internal fun mediaPreviewVideoAutoPlays(): Boolean = false

internal fun mediaPreviewVideoShowsProgressBar(): Boolean = true

internal fun mediaPreviewVideoShowsTimecodes(): Boolean = true

internal fun mediaPreviewVideoShowsInlinePlayPause(): Boolean = true

internal fun mediaPreviewVideoControlsAvoidActionButtons(): Boolean = true

internal fun mediaPreviewVideoControlBottomPaddingDp(): Int = 12

internal fun mediaPreviewVideoControlsFloatOverVideoFrame(): Boolean = true

internal fun mediaPreviewVideoPlayerReleaseIsIdempotent(): Boolean = true

internal fun mediaPreviewVideoPlayerReleaseHandlesInvalidState(): Boolean = true

internal fun chatListVideoUsesInlinePlayer(): Boolean = false

internal fun chatListVideoUsesAspectFit(): Boolean = true

internal fun chatListVideoAutoPlays(): Boolean = false

internal fun mediaPreviewActionContracts(): Set<MediaActionContract> {
    return setOf(
        MediaActionContract.AnalyzeImage,
        MediaActionContract.FindObject,
        MediaActionContract.Share,
        MediaActionContract.Save,
        MediaActionContract.Favorite,
        MediaActionContract.More
    )
}

internal fun standaloneMediaListMaxWidthDp(): Int = StandaloneImageMaxWidthDp

internal fun standaloneMediaListMinHeightDp(): Int = StandaloneMediaMinHeightDp

internal fun standaloneMediaListMaxHeightDp(): Int = StandaloneMediaMaxHeightDp
