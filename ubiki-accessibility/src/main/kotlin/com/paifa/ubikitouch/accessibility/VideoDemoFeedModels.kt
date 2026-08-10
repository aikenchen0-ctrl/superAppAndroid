package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.EdgeSide
import kotlin.math.abs

/** The three local feed modes exposed by the demo overlay. */
enum class VideoDemoTab {
    Recommended,
    Following,
    Nearby
}

enum class VideoDemoPageDirection {
    Previous,
    Next
}

data class VideoDemoItem(
    val id: String,
    val author: String,
    val caption: String,
    val location: String,
    val videoUrl: String,
    val backgroundHex: String,
    val accentHex: String,
    val accentLabel: String
)

data class VideoDemoSwipeLayout(
    val currentTranslationY: Float,
    val adjacentTranslationY: Float
)

object VideoDemoCatalog {
    val tabs: List<VideoDemoTab> = listOf(
        VideoDemoTab.Recommended,
        VideoDemoTab.Following,
        VideoDemoTab.Nearby
    )

    private val itemsByTab: Map<VideoDemoTab, List<VideoDemoItem>> = mapOf(
        VideoDemoTab.Recommended to listOf(
            VideoDemoItem(
                id = "recommended-aurora",
                author = "@northlight",
                caption = "A quiet morning above the clouds.",
                location = "Yunnan, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_BLAZES,
                backgroundHex = "#0C1D30",
                accentHex = "#61D4FF",
                accentLabel = "AURORA"
            ),
            VideoDemoItem(
                id = "recommended-street",
                author = "@cityframes",
                caption = "Neon reflections after the rain.",
                location = "Shanghai, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_ESCAPES,
                backgroundHex = "#251329",
                accentHex = "#FF6488",
                accentLabel = "NIGHT WALK"
            ),
            VideoDemoItem(
                id = "recommended-tide",
                author = "@slowtide",
                caption = "Let the tide set the pace today.",
                location = "Xiamen, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_FUN,
                backgroundHex = "#0B3033",
                accentHex = "#62E6C5",
                accentLabel = "SLOW TIDE"
            )
        ),
        VideoDemoTab.Following to listOf(
            VideoDemoItem(
                id = "following-ceramics",
                author = "@atelier_m",
                caption = "Handmade textures in warm light.",
                location = "Jingdezhen, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_BLAZES,
                backgroundHex = "#302015",
                accentHex = "#FFB45E",
                accentLabel = "ATELIER"
            ),
            VideoDemoItem(
                id = "following-garden",
                author = "@greenroom",
                caption = "A tiny garden, a very big afternoon.",
                location = "Hangzhou, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_ESCAPES,
                backgroundHex = "#123024",
                accentHex = "#9DE36C",
                accentLabel = "GREEN ROOM"
            ),
            VideoDemoItem(
                id = "following-records",
                author = "@turntable",
                caption = "One more record before sunset.",
                location = "Chengdu, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_FUN,
                backgroundHex = "#241B39",
                accentHex = "#B49BFF",
                accentLabel = "SIDE B"
            )
        ),
        VideoDemoTab.Nearby to listOf(
            VideoDemoItem(
                id = "nearby-market",
                author = "@morningmarket",
                caption = "Fresh colors from the neighborhood market.",
                location = "Shenzhen, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_ESCAPES,
                backgroundHex = "#332313",
                accentHex = "#FFD166",
                accentLabel = "LOCAL"
            ),
            VideoDemoItem(
                id = "nearby-court",
                author = "@afterwork",
                caption = "The last game of the day starts here.",
                location = "Guangzhou, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_FUN,
                backgroundHex = "#172C40",
                accentHex = "#6CA8FF",
                accentLabel = "PLAY"
            ),
            VideoDemoItem(
                id = "nearby-lanterns",
                author = "@eveningwalk",
                caption = "Lanterns come on one by one.",
                location = "Suzhou, China",
                videoUrl = VIDEO_URL_FOR_BIGGER_BLAZES,
                backgroundHex = "#351B1A",
                accentHex = "#FF8A65",
                accentLabel = "TONIGHT"
            )
        )
    )

    fun itemsFor(tab: VideoDemoTab): List<VideoDemoItem> = itemsByTab[tab].orEmpty()
}

fun videoDemoPageIndex(
    currentIndex: Int,
    direction: VideoDemoPageDirection,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    val safeIndex = Math.floorMod(currentIndex, itemCount)
    return when (direction) {
        VideoDemoPageDirection.Previous -> Math.floorMod(safeIndex - 1, itemCount)
        VideoDemoPageDirection.Next -> Math.floorMod(safeIndex + 1, itemCount)
    }
}

fun videoDemoSwipeLayout(
    direction: VideoDemoPageDirection,
    dragOffsetY: Float,
    viewportHeightPx: Float
): VideoDemoSwipeLayout {
    val height = viewportHeightPx.coerceAtLeast(0f)
    val adjacentOffset = when (direction) {
        VideoDemoPageDirection.Previous -> -height
        VideoDemoPageDirection.Next -> height
    }
    return VideoDemoSwipeLayout(
        currentTranslationY = dragOffsetY,
        adjacentTranslationY = adjacentOffset + dragOffsetY
    )
}

fun videoDemoShouldCommitSwipe(
    dragOffsetY: Float,
    viewportHeightPx: Float
): Boolean {
    val height = viewportHeightPx.coerceAtLeast(0f)
    return height > 0f &&
        kotlin.math.abs(dragOffsetY) >= height * VIDEO_DEMO_SWIPE_COMMIT_FRACTION
}

fun toggleVideoDemoId(ids: Set<String>, id: String): Set<String> {
    if (id.isBlank()) return ids
    return if (id in ids) ids - id else ids + id
}

fun isMeteorPreviewGesture(
    side: EdgeSide,
    dx: Float,
    dy: Float,
    minDistancePx: Float
): Boolean {
    val threshold = minDistancePx.coerceAtLeast(1f)
    return abs(dy) >= threshold &&
        abs(dy) >= abs(dx) * METEOR_VERTICALITY_RATIO
}

fun meteorPreviewProgress(
    dx: Float,
    dy: Float,
    minDistancePx: Float
): Float {
    val threshold = minDistancePx.coerceAtLeast(1f)
    return (abs(dy) / threshold).coerceIn(0f, 1f)
}

private const val METEOR_VERTICALITY_RATIO = 1.35f
const val VIDEO_DEMO_SWIPE_COMMIT_FRACTION = 0.22f

const val VIDEO_URL_FOR_BIGGER_BLAZES =
    "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
const val VIDEO_URL_FOR_BIGGER_ESCAPES =
    "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
const val VIDEO_URL_FOR_BIGGER_FUN =
    "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
