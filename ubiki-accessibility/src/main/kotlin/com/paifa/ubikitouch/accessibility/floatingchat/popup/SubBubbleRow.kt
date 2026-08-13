package com.paifa.ubikitouch.accessibility.floatingchat.popup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SubBubbleRow(
    count: Int,
    expandToRight: Boolean,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable (index: Int) -> Unit
) {
    Layout(
        modifier = modifier,
        content = { repeat(count) { content(it) } }
    ) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = (placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0))
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.maxOfOrNull { it.height }?.coerceIn(constraints.minHeight, constraints.maxHeight)
            ?: constraints.minHeight
        layout(width, height) {
            var x = if (expandToRight) 0 else width
            placeables.forEach { placeable ->
                if (expandToRight) {
                    placeable.place(x, 0)
                    x += placeable.width + gap
                } else {
                    x -= placeable.width
                    placeable.place(x, 0)
                    x -= gap
                }
            }
        }
    }
}
