package com.paifa.ubikitouch.accessibility.floatingchat.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalFloatingChatColors = staticCompositionLocalOf { FloatingChatLightColors }
internal val LocalFloatingChatDimensions = staticCompositionLocalOf { FloatingChatDimensions() }
internal val LocalFloatingChatTypography = staticCompositionLocalOf { FloatingChatTypography() }

internal object FloatingChatThemeValues {
    val colors: FloatingChatColors
        @Composable get() = LocalFloatingChatColors.current
    val dimensions: FloatingChatDimensions
        @Composable get() = LocalFloatingChatDimensions.current
    val typography: FloatingChatTypography
        @Composable get() = LocalFloatingChatTypography.current
}

@Composable
internal fun FloatingChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalFloatingChatColors provides if (darkTheme) FloatingChatDarkColors else FloatingChatLightColors,
        LocalFloatingChatDimensions provides FloatingChatDimensions(),
        LocalFloatingChatTypography provides FloatingChatTypography(),
        content = content
    )
}
