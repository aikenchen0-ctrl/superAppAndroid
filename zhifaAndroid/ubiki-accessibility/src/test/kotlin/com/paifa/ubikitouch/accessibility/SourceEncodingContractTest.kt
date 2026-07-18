package com.paifa.ubikitouch.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceEncodingContractTest {
    @Test
    fun mainSourceContainsNoKnownMojibakeCharacters() {
        val sourceRoot = sourceRoot()
        val mojibake = Regex("[�鐨鍙閫褰鑱鏈瀹瑜閸濮娴鎺鍋闇娌缁鏆鐢澹寰鍏鏁绱澶宸閺鐠濞娑銆鈥€]")
        val failures = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .mapNotNull { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (mojibake.containsMatchIn(line)) "${file.relativeTo(sourceRoot)}:${index + 1}" else null
                }.takeIf(List<String>::isNotEmpty)
            }
            .flatten()
            .toList()

        assertTrue("Mojibake found in main source:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun sourceRoot(): File {
        val moduleRelative = File("src/main")
        if (moduleRelative.exists()) return moduleRelative
        return File("ubiki-accessibility/src/main")
    }
}
