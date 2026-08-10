package com.paifa.ubikitouch.accessibility.floatingchat

import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatLightColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatThemeContractTest {
    @Test
    fun lightThemePreservesExistingOverlaySemanticColors() {
        assertEquals(Color(0xF2F8FCFF), FloatingChatLightColors.primaryText)
        assertEquals(Color(0xD9F1F7FA), FloatingChatLightColors.secondaryText)
        assertEquals(Color(0xFF7DCC16), FloatingChatLightColors.accent)
        assertEquals(Color(0xFFE27E76), FloatingChatLightColors.error)
        assertEquals(Color(0x82F8FCFF), FloatingChatLightColors.outline)
    }

    @Test
    fun themeDefinesSemanticColorsDimensionsAndTypography() {
        val colors = sourceFile("theme/FloatingChatColors.kt")
        val dimensions = sourceFile("theme/FloatingChatDimensions.kt")
        val typography = sourceFile("theme/FloatingChatTypography.kt")
        val theme = sourceFile("theme/FloatingChatTheme.kt")

        listOf(colors, dimensions, typography, theme).forEach { source ->
            assertTrue("Missing theme source: ${source.path}", source.isFile)
        }
        assertTrue(colors.readText().contains("data class FloatingChatColors"))
        assertTrue(dimensions.readText().contains("data class FloatingChatDimensions"))
        assertTrue(typography.readText().contains("data class FloatingChatTypography"))
        assertTrue(theme.readText().contains("fun FloatingChatTheme("))
    }

    @Test
    fun semanticStatusColorsCoverEveryTone() {
        val colors = sourceFile("theme/FloatingChatColors.kt")

        assertTrue("Missing colors source", colors.isFile)
        val text = colors.readText()
        listOf("Neutral", "Info", "Success", "Warning", "Error").forEach { tone ->
            assertTrue("Missing status tone mapping: $tone", text.contains("StatusTone.$tone"))
        }
    }

    @Test
    fun componentContractsStayPlatformIndependent() {
        val source = sourceFile("contract/ComponentContracts.kt")

        assertTrue("Missing component contracts", source.isFile)
        val text = source.readText()
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))
        assertTrue(text.contains("data class OverlayButtonProps"))
        assertTrue(text.contains("sealed interface OverlayButtonEvent"))
        assertTrue(text.contains("data class OverlayTextFieldProps"))
    }

    @Test
    fun commonComponentsExposePropsAndEvents() {
        val expected = mapOf(
            "components/OverlayButton.kt" to "OverlayButtonProps",
            "components/OverlayAvatar.kt" to "OverlayAvatarProps",
            "components/OverlayTextField.kt" to "OverlayTextFieldProps",
            "components/OverlayStatus.kt" to "OverlayStatusProps"
        )

        expected.forEach { (path, contractType) ->
            val source = sourceFile(path)
            assertTrue("Missing component source: ${source.path}", source.isFile)
            val text = source.readText()
            assertTrue(text.contains(contractType))
            assertFalse(text.contains("AccessibilityService"))
            assertFalse(text.contains("WindowManager"))
        }
    }

    @Test
    fun overlayTokensLiveInThemePackageAndDelegateCommonSemanticsToTheme() {
        val tokens = sourceFile("theme/OverlayTokens.kt")
        assertTrue("Missing extracted overlay tokens", tokens.isFile)
        val source = tokens.readText()

        assertTrue(source.contains("FloatingChatLightColors.primaryText"))
        assertTrue(source.contains("FloatingChatLightColors.secondaryText"))
        assertTrue(source.contains("FloatingChatLightColors.accent"))
        assertTrue(source.contains("FloatingChatLightColors.error"))
        assertTrue(source.contains("FloatingChatLightColors.outline"))

        val legacy = legacyOverlaySource()
        assertFalse(legacy.contains("internal object OverlayTokens"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat",
            relativePath
        )
    }

    private fun legacyOverlaySource(): String {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        )
        val source = if (moduleRelative.exists()) {
            moduleRelative
        } else {
            File(
                "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
            )
        }
        assertTrue("Missing legacy overlay source", source.isFile)
        return source.readText()
    }
}
