package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class WeightedLruCacheTest {
    @Test
    fun fileImagesAreSampledBeforeDecode() {
        assertEquals(1, imageDecodeSampleSize(width = 640, height = 480, maxSize = 720))
        assertEquals(2, imageDecodeSampleSize(width = 800, height = 600, maxSize = 720))
        assertEquals(4, imageDecodeSampleSize(width = 1600, height = 1200, maxSize = 720))
        assertEquals(4, imageDecodeSampleSize(width = 1080, height = 2400, maxSize = 720))
    }

    @Test
    fun cachedValueIsReusedWithoutRunningLoaderAgain() {
        val cache = newCache(maxWeight = 8)
        var loadCount = 0

        val first = cache.call("getOrPut", "image", {
            loadCount += 1
            "bitmap"
        })
        val second = cache.call("getOrPut", "image", {
            loadCount += 1
            "replacement"
        })

        assertEquals("bitmap", first)
        assertEquals("bitmap", second)
        assertEquals(1, loadCount)
    }

    @Test
    fun leastRecentlyUsedValuesAreEvictedByWeight() {
        val cache = newCache(maxWeight = 4)
        cache.call("put", "first", "AA")
        cache.call("put", "second", "BB")
        assertEquals("AA", cache.call("get", "first"))

        cache.call("put", "third", "CC")

        assertEquals("AA", cache.call("get", "first"))
        assertNull(cache.call("get", "second"))
        assertEquals("CC", cache.call("get", "third"))
    }

    private fun newCache(maxWeight: Int): Any {
        val cacheClass = runCatching {
            Class.forName("com.paifa.ubikitouch.accessibility.WeightedLruCache")
        }.getOrElse {
            fail("WeightedLruCache has not been implemented")
            error("unreachable")
        }
        val constructor = cacheClass.declaredConstructors.single { it.parameterCount == 2 }
        constructor.isAccessible = true
        val weightOf: (Any) -> Int = { value -> value.toString().length }
        return constructor.newInstance(maxWeight, weightOf)
    }

    private fun Any.call(methodName: String, vararg arguments: Any): Any? {
        val method = javaClass.declaredMethods.single {
            it.name == methodName && it.parameterCount == arguments.size
        }
        method.isAccessible = true
        return method.invoke(this, *arguments)
    }

    private fun imageDecodeSampleSize(width: Int, height: Int, maxSize: Int): Int {
        val method = runCatching {
            Class.forName("com.paifa.ubikitouch.accessibility.FloatingChatOverlayUiKt")
                .getDeclaredMethod(
                    "imageDecodeSampleSize",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
        }.getOrElse {
            fail("file image sampling has not been implemented")
            error("unreachable")
        }
        method.isAccessible = true
        return method.invoke(null, width, height, maxSize) as Int
    }
}
