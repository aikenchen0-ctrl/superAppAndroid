package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.UniVergeAccessibilityService
import com.paifa.univerge.accessibility.UniVergePreferences

class WeChatChatAppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = UniVergePreferences(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WeChatChatAppearanceScreen(preferences = preferences, onBack = ::finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeChatChatAppearanceScreen(
    preferences: UniVergePreferences,
    onBack: () -> Unit
) {
    var frostedBackgroundEnabled by remember {
        mutableStateOf(preferences.floatingChatFrostedBackgroundEnabled)
    }
    var backgroundOpacityPercent by remember {
        mutableIntStateOf(preferences.floatingChatBackgroundOpacityPercent)
    }
    var blurRadiusDp by remember { mutableIntStateOf(preferences.floatingChatBlurRadiusDp) }
    var backgroundColorRgb by remember { mutableIntStateOf(preferences.floatingChatBackgroundColorRgb) }

    fun refreshOverlay() {
        UniVergeAccessibilityService.instance?.requestOverlayRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微信聊天外观") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                FloatingChatAppearancePanel(
                    frostedBackgroundEnabled = frostedBackgroundEnabled,
                    onFrostedBackgroundEnabledChange = { enabled ->
                        frostedBackgroundEnabled = enabled
                        preferences.floatingChatFrostedBackgroundEnabled = enabled
                        refreshOverlay()
                    },
                    backgroundOpacityPercent = backgroundOpacityPercent,
                    onBackgroundOpacityPercentChange = { opacity ->
                        preferences.floatingChatBackgroundOpacityPercent = opacity
                        backgroundOpacityPercent = preferences.floatingChatBackgroundOpacityPercent
                        refreshOverlay()
                    },
                    blurRadiusDp = blurRadiusDp,
                    onBlurRadiusDpChange = { blurRadius ->
                        preferences.floatingChatBlurRadiusDp = blurRadius
                        blurRadiusDp = preferences.floatingChatBlurRadiusDp
                        refreshOverlay()
                    },
                    backgroundColorRgb = backgroundColorRgb,
                    onBackgroundColorRgbChange = { colorRgb ->
                        preferences.floatingChatBackgroundColorRgb = colorRgb
                        backgroundColorRgb = preferences.floatingChatBackgroundColorRgb
                        refreshOverlay()
                    }
                )
            }
        }
    }
}
