package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.formatVideoTimecode
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun FloatingChatVideoPlayerControlBar(currentPositionMs: Int, durationMs: Int, playing: Boolean, onTogglePlayback: () -> Unit, sliderValue: Float, onSliderValueChange: (Float) -> Unit, onSliderChangeFinished: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x7A0E1418)).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FloatingChatMiniVideoControlButton(playing, onTogglePlayback)
        Spacer(Modifier.width(6.dp))
        TextLabel(formatVideoTimecode(currentPositionMs), 8.sp, color = Color(0xFFF5F8FA), weight = FontWeight.Bold, maxLines = 1)
        FloatingChatVideoTimelineSlider(sliderValue, durationMs.toFloat(), onSliderValueChange, onSliderChangeFinished, Modifier.weight(1f).padding(horizontal = 4.dp))
        TextLabel(formatVideoTimecode(durationMs), 8.sp, color = Color(0xFFF5F8FA), weight = FontWeight.Bold, maxLines = 1)
    }
}
