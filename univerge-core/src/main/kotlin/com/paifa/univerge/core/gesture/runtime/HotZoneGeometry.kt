package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import java.util.Collections

/** A configured vertical segment. The zone id is stable within one side. */
data class HotZoneSegment(
    val startDp: Float,
    val lengthDp: Float,
    val thicknessDp: Float = 24f,
    val enabled: Boolean = true,
    val zoneId: Int = 0,
    /** Horizontal inset from the physical edge used by native and overlay hit testing. */
    val edgeInsetDp: Float = 0f
)

/** A clipped trigger rectangle. Px values are filled when a density is supplied. */
data class HotZoneRect(
    val startDp: Float,
    val endDp: Float,
    val thicknessDp: Float,
    val zoneId: Int = 0,
    val startPx: Float = startDp,
    val endPx: Float = endDp,
    val thicknessPx: Float = thicknessDp
) {
    val lengthDp: Float get() = endDp - startDp
    val lengthPx: Float get() = endPx - startPx
}

/** A merged interval used for system exclusion budgeting, not input hit testing. */
data class HotZoneInterval(
    val startDp: Float,
    val endDp: Float,
    val startPx: Float = startDp,
    val endPx: Float = endDp
) {
    val lengthDp: Float get() = endDp - startDp
    val lengthPx: Float get() = endPx - startPx
}

enum class ExclusionBackend {
    DETERMINISTIC_EXCLUSION,
    BEST_EFFORT_NATIVE,
    OVERLAY_FALLBACK
}

enum class ExclusionBudgetState {
    WITHIN_BUDGET,
    OVER_BUDGET
}

data class SideExclusionReport(
    val unionLengthDp: Float,
    val over200Dp: Boolean,
    val unionLengthPx: Float = unionLengthDp,
    val over200Px: Boolean = unionLengthPx > 200f,
    val mergedIntervals: List<HotZoneInterval> = emptyList(),
    val triggerRects: List<HotZoneRect> = emptyList(),
    val withinBudget: Boolean = !over200Dp,
    val budgetLengthDp: Float = 200f,
    val budgetLengthPx: Float = 200f,
    val backend: ExclusionBackend = if (withinBudget) {
        ExclusionBackend.DETERMINISTIC_EXCLUSION
    } else {
        ExclusionBackend.BEST_EFFORT_NATIVE
    },
    val degradationReason: String? = if (withinBudget) null else {
        "Merged exclusion length exceeds the 200dp platform budget"
    },
    val budgetState: ExclusionBudgetState = if (withinBudget) {
        ExclusionBudgetState.WITHIN_BUDGET
    } else {
        ExclusionBudgetState.OVER_BUDGET
    }
) {
    val isWithinBudget: Boolean get() = withinBudget
    val overBudget: Boolean get() = !withinBudget
    val budgetExceeded: Boolean get() = !withinBudget
    val intervals: List<HotZoneInterval> get() = mergedIntervals
    val mergedIntervalsDp: List<HotZoneInterval> get() = mergedIntervals
    val mergedIntervalsPx: List<HotZoneInterval> get() = mergedIntervals
    val backendName: String get() = backend.name
}

class ExclusionReport internal constructor(values: Map<EdgeSide, SideExclusionReport>) {
    private val reports: Map<EdgeSide, SideExclusionReport> =
        Collections.unmodifiableMap(values.toMap())

    val sides: Map<EdgeSide, SideExclusionReport> get() = reports

    fun forSide(side: EdgeSide): SideExclusionReport =
        reports[side] ?: SideExclusionReport(0f, false)

    operator fun get(side: EdgeSide): SideExclusionReport = forSide(side)
}

object HotZoneGeometry {
    const val MAX_EXCLUSION_LENGTH_DP = 200f

    /** Legacy API: returns merged vertical intervals for exclusion budgeting. */
    fun normalize(segments: List<HotZoneSegment>, screenLengthDp: Float): List<HotZoneRect> {
        val clipped = clipSegments(segments, screenLengthDp, density = 1f)
        return mergeRects(clipped)
    }

    /** Returns one clipped rectangle per enabled segment for input hit testing. */
    fun triggerRects(
        segments: List<HotZoneSegment>,
        screenLengthDp: Float,
        density: Float = 1f
    ): List<HotZoneRect> {
        requireValidDensity(density)
        return clipSegments(segments, screenLengthDp, density)
    }

    fun unionLengthDp(segments: List<HotZoneSegment>, screenLengthDp: Float): Float =
        mergeIntervals(clipSegments(segments, screenLengthDp, density = 1f))
            .sumOf { it.lengthDp.toDouble() }
            .toFloat()

    /** Builds backend-facing interval, pixel and budget information in one pass. */
    fun report(
        segmentsBySide: Map<EdgeSide, List<HotZoneSegment>>,
        screenLengthDp: Float,
        density: Float = 1f
    ): ExclusionReport {
        requireValidDensity(density)
        val reports = EdgeSide.entries.associateWith { side ->
            val triggerRects = clipSegments(segmentsBySide[side].orEmpty(), screenLengthDp, density)
            val mergedIntervals = mergeIntervals(triggerRects)
            val unionLengthDp = mergedIntervals.sumOf { it.lengthDp.toDouble() }.toFloat()
            val unionLengthPx = mergedIntervals.sumOf { it.lengthPx.toDouble() }.toFloat()
            val withinBudget = unionLengthDp <= MAX_EXCLUSION_LENGTH_DP
            SideExclusionReport(
                unionLengthDp = unionLengthDp,
                over200Dp = !withinBudget,
                unionLengthPx = unionLengthPx,
                over200Px = unionLengthPx > MAX_EXCLUSION_LENGTH_DP * density,
                mergedIntervals = mergedIntervals,
                triggerRects = triggerRects,
                withinBudget = withinBudget,
                budgetLengthDp = MAX_EXCLUSION_LENGTH_DP,
                budgetLengthPx = MAX_EXCLUSION_LENGTH_DP * density,
                backend = if (withinBudget) ExclusionBackend.DETERMINISTIC_EXCLUSION else ExclusionBackend.BEST_EFFORT_NATIVE,
                degradationReason = if (withinBudget) null else {
                    "Merged exclusion length exceeds the 200dp platform budget"
                },
                budgetState = if (withinBudget) ExclusionBudgetState.WITHIN_BUDGET else ExclusionBudgetState.OVER_BUDGET
            )
        }
        return ExclusionReport(reports)
    }

    private fun clipSegments(
        segments: List<HotZoneSegment>,
        screenLengthDp: Float,
        density: Float
    ): List<HotZoneRect> {
        val limit = screenLengthDp.takeIf { it.isFinite() && it > 0f } ?: 0f
        return segments.asSequence()
            .filter { segment ->
                segment.enabled && segment.lengthDp > 0f && segment.startDp.isFinite() &&
                    segment.lengthDp.isFinite() && segment.thicknessDp.isFinite()
            }
            .mapNotNull { segment ->
                val start = segment.startDp.coerceIn(0f, limit)
                val rawEnd = segment.startDp + segment.lengthDp
                val end = if (rawEnd.isFinite()) rawEnd.coerceIn(0f, limit) else limit
                if (end <= start) null else {
                    val thicknessDp = segment.thicknessDp.coerceAtLeast(0f)
                    HotZoneRect(
                        startDp = start,
                        endDp = end,
                        thicknessDp = thicknessDp,
                        zoneId = segment.zoneId,
                        startPx = start * density,
                        endPx = end * density,
                        thicknessPx = thicknessDp * density
                    )
                }
            }
            .sortedWith(compareBy<HotZoneRect> { it.startDp }.thenBy { it.zoneId })
            .toList()
    }

    private fun mergeRects(rects: List<HotZoneRect>): List<HotZoneRect> {
        if (rects.isEmpty()) return emptyList()
        val merged = ArrayList<HotZoneRect>(rects.size)
        for (current in rects) {
            val previous = merged.lastOrNull()
            if (previous == null || current.startDp > previous.endDp) {
                merged += current
            } else {
                merged[merged.lastIndex] = HotZoneRect(
                    startDp = previous.startDp,
                    endDp = maxOf(previous.endDp, current.endDp),
                    thicknessDp = maxOf(previous.thicknessDp, current.thicknessDp),
                    zoneId = previous.zoneId,
                    startPx = previous.startPx,
                    endPx = maxOf(previous.endPx, current.endPx),
                    thicknessPx = maxOf(previous.thicknessPx, current.thicknessPx)
                )
            }
        }
        return merged
    }

    private fun mergeIntervals(rects: List<HotZoneRect>): List<HotZoneInterval> {
        if (rects.isEmpty()) return emptyList()
        val merged = ArrayList<HotZoneInterval>(rects.size)
        for (rect in rects) {
            val previous = merged.lastOrNull()
            if (previous == null || rect.startDp > previous.endDp) {
                merged += HotZoneInterval(rect.startDp, rect.endDp, rect.startPx, rect.endPx)
            } else {
                merged[merged.lastIndex] = HotZoneInterval(
                    startDp = previous.startDp,
                    endDp = maxOf(previous.endDp, rect.endDp),
                    startPx = previous.startPx,
                    endPx = maxOf(previous.endPx, rect.endPx)
                )
            }
        }
        return merged
    }

    private fun requireValidDensity(density: Float) {
        require(density.isFinite() && density > 0f) { "density must be a finite positive value" }
    }
}
