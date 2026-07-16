package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.CompactInteractiveSize

@Composable
internal fun FloatingChatVideoTimelineSlider(value: Float, maxValue: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, modifier: Modifier = Modifier) {
    CompactInteractiveSize {
        val safeMax = maxValue.coerceAtLeast(1f)
        Slider(value = value.coerceIn(0f, safeMax), onValueChange = onValueChange, valueRange = 0f..safeMax, onValueChangeFinished = onValueChangeFinished, modifier = modifier.height(20.dp), colors = SliderDefaults.colors(thumbColor = Color(0xFFF5F8FA), activeTrackColor = Color(0xFFF5F8FA), inactiveTrackColor = Color(0x5CF5F8FA)))
    }
}

@Composable
internal fun FloatingChatMiniVideoControlButton(playing: Boolean, onClick: () -> Unit) {
    CompactInteractiveSize {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(24.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x24F5F8FA), contentColor = Color.White)) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
        }
    }
}
