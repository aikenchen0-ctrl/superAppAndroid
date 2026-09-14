package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.EdgeSide
import java.util.Collections

/** A physical input region that can be owned by exactly one gesture backend. */
sealed interface GestureRegionKey {
    data class Side(val side: EdgeSide, val zoneId: Int) : GestureRegionKey {
        init {
            require(zoneId >= 0) { "zoneId must be non-negative" }
        }
    }

    data object Bottom : GestureRegionKey

    /** The foreground app owns this area; the gesture server must never own it. */
    data object Center : GestureRegionKey
}

/** A platform-neutral rectangle used by the ownership report. */
data class GestureInputRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    init {
        require(left >= 0) { "rect.left must be non-negative" }
        require(top >= 0) { "rect.top must be non-negative" }
        require(right > left) { "rect.right must be greater than rect.left" }
        require(bottom > top) { "rect.bottom must be greater than rect.top" }
    }
}

/** The only owners allowed for a real gesture region. */
enum class GestureInputOwner {
    NATIVE,
    OVERLAY,
    NONE,

    /** Retained as a diagnostic value so legacy callers can be rejected explicitly. */
    FULL_SCREEN_TOUCH
}

/** A requested region and its preferred trigger rectangle. */
data class GestureInputRegion(
    val key: GestureRegionKey,
    val rect: GestureInputRect? = null
)

/** Result of attempting to register one physical region with the native backend. */
data class NativeInputRegistration(
    val region: GestureRegionKey,
    val available: Boolean,
    val rect: GestureInputRect? = null,
    val failureReason: String? = null
) {
    init {
        require(failureReason == null || failureReason.isNotBlank()) {
            "failureReason must be null or non-blank"
        }
        if (available) {
            require(failureReason == null) {
                "a successful native registration cannot have a failure reason"
            }
        }
    }

    companion object {
        fun success(
            region: GestureRegionKey,
            rect: GestureInputRect? = null
        ): NativeInputRegistration {
            return NativeInputRegistration(
                region = region,
                available = true,
                rect = rect
            )
        }

        fun failure(
            region: GestureRegionKey,
            reason: String = DEFAULT_NATIVE_FAILURE_REASON,
            rect: GestureInputRect? = null
        ): NativeInputRegistration {
            return NativeInputRegistration(
                region = region,
                available = false,
                rect = rect,
                failureReason = reason
            )
        }
    }
}

/** Immutable ownership record for one side zone or the bottom bar. */
data class GestureInputOwnership(
    val region: GestureRegionKey,
    val owner: GestureInputOwner,
    val rect: GestureInputRect? = null,
    val degradationReason: String? = null
) {
    init {
        require(degradationReason == null || degradationReason.isNotBlank()) {
            "degradationReason must be null or non-blank"
        }
        require(owner != GestureInputOwner.FULL_SCREEN_TOUCH) {
            "full-screen touch ownership is prohibited"
        }
        if (region == GestureRegionKey.Center) {
            require(owner == GestureInputOwner.NONE) {
                "the center region belongs to the foreground app"
            }
        }
    }

    val regionKey: GestureRegionKey
        get() = region

    val bounds: GestureInputRect?
        get() = rect

    val fallbackReason: String?
        get() = degradationReason
}

/**
 * Immutable snapshot of input ownership. Construction rejects duplicate physical regions,
 * so a native owner and an overlay owner cannot silently coexist for the same key.
 */
class GestureInputOwnershipReport(records: Iterable<GestureInputOwnership>) {
    val records: List<GestureInputOwnership> = immutableList(records)

    init {
        require(this.records.map(GestureInputOwnership::region).toSet().size == this.records.size) {
            "each physical gesture region must have at most one input owner"
        }
    }

    val nativeRegions: List<GestureRegionKey>
        get() = immutableList(records.filter { it.owner == GestureInputOwner.NATIVE }.map { it.region })

    val overlayRegions: List<GestureRegionKey>
        get() = immutableList(records.filter { it.owner == GestureInputOwner.OVERLAY }.map { it.region })

    val hasFullScreenTouchOwner: Boolean
        get() = records.any { it.owner == GestureInputOwner.FULL_SCREEN_TOUCH }

    fun recordFor(region: GestureRegionKey): GestureInputOwnership? {
        return records.firstOrNull { it.region == region }
    }

    fun ownerFor(region: GestureRegionKey): GestureInputOwner? {
        return recordFor(region)?.owner
    }

    override fun equals(other: Any?): Boolean {
        return other is GestureInputOwnershipReport && records == other.records
    }

    override fun hashCode(): Int = records.hashCode()

    override fun toString(): String = "GestureInputOwnershipReport(records=$records)"

    companion object {
        /**
         * Resolves one owner for every requested region. Native success wins; a failed or
         * missing native registration falls back only for that region.
         */
        fun resolve(
            requestedRegions: Iterable<GestureInputRegion>,
            nativeResults: Iterable<NativeInputRegistration>
        ): GestureInputOwnershipReport {
            val requested = requestedRegions.toList()
            val requestedByKey = requested.associateBy { it.key }
            require(requestedByKey.size == requested.size) {
                "each physical gesture region must be requested once"
            }
            require(requested.none { it.key == GestureRegionKey.Center }) {
                "the center region cannot be owned by the gesture server"
            }

            val registrations = nativeResults.toList()
            val registrationsByKey = registrations.associateBy { it.region }
            require(registrationsByKey.size == registrations.size) {
                "native registration results must contain one result per region"
            }
            require(registrations.all { it.region in requestedByKey }) {
                "native registration result is not part of the requested regions"
            }

            val resolved = requested.map { requestedRegion ->
                val registration = registrationsByKey[requestedRegion.key]
                val rect = requestedRegion.rect ?: registration?.rect
                if (registration?.available == true) {
                    GestureInputOwnership(
                        region = requestedRegion.key,
                        owner = GestureInputOwner.NATIVE,
                        rect = rect
                    )
                } else {
                    GestureInputOwnership(
                        region = requestedRegion.key,
                        owner = GestureInputOwner.OVERLAY,
                        rect = rect,
                        degradationReason = registration?.failureReason
                            ?: DEFAULT_MISSING_NATIVE_RESULT_REASON
                    )
                }
            }
            return GestureInputOwnershipReport(resolved)
        }
    }
}

private const val DEFAULT_NATIVE_FAILURE_REASON = "native input registration failed"
private const val DEFAULT_MISSING_NATIVE_RESULT_REASON = "native input registration was not reported"

internal fun NativeTouchInterceptRect.toGestureInputRect(): GestureInputRect =
    GestureInputRect(left = left, top = top, right = right, bottom = bottom)

internal fun NativeBottomGestureInterceptRect.toGestureInputRect(): GestureInputRect =
    GestureInputRect(left = left, top = top, right = right, bottom = bottom)

private fun <T> immutableList(values: Iterable<T>): List<T> {
    return Collections.unmodifiableList(values.toList())
}
