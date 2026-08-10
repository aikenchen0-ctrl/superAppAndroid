package com.paifa.ubikitouch.accessibility

internal class WeightedLruCache<K, V : Any>(
    private val maxWeight: Int,
    private val weightOf: (V) -> Int
) {
    private val entries = LinkedHashMap<K, V>(0, 0.75f, true)
    private var currentWeight = 0

    init {
        require(maxWeight > 0) { "maxWeight must be positive" }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        putLocked(key, value)
    }

    fun getOrPut(key: K, loader: () -> V?): V? {
        get(key)?.let { return it }
        val loaded = loader() ?: return null
        synchronized(this) {
            entries[key]?.let { return it }
            putLocked(key, loaded)
            return loaded
        }
    }

    private fun putLocked(key: K, value: V) {
        entries.remove(key)?.let { previous ->
            currentWeight -= safeWeight(previous)
        }
        val valueWeight = safeWeight(value)
        if (valueWeight > maxWeight) return

        entries[key] = value
        currentWeight += valueWeight
        val iterator = entries.entries.iterator()
        while (currentWeight > maxWeight && iterator.hasNext()) {
            currentWeight -= safeWeight(iterator.next().value)
            iterator.remove()
        }
    }

    private fun safeWeight(value: V): Int = weightOf(value).coerceAtLeast(1)
}
