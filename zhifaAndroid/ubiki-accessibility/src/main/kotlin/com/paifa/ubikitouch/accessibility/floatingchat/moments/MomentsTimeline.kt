package com.paifa.ubikitouch.accessibility.floatingchat.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiState

private val TimelineBackground = Color(0xFFF5F5F5)
private val CardBackground = Color.White
private val PrimaryText = Color(0xFF222222)
private val SecondaryText = Color(0xFF888888)

@Composable
internal fun MomentsTimeline(
    state: MomentsUiState,
    onEvent: (MomentsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(TimelineBackground),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                TextLabel("朋友圈", 16.sp, color = PrimaryText, modifier = Modifier.weight(1f), maxLines = 1)
                TextLabel("刷新", 12.sp, color = SecondaryText, modifier = Modifier.clickable { onEvent(MomentsUiEvent.RefreshRequested) }, maxLines = 1)
            }
        }
        if (state.error != null) {
            item { TextLabel(state.error, 12.sp, color = Color(0xFFE45858), modifier = Modifier.padding(16.dp), maxLines = 2) }
        }
        items(state.posts, key = { it.id }) { post ->
            Column(modifier = Modifier.fillMaxWidth().background(CardBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextLabel(post.authorName, 14.sp, color = PrimaryText, maxLines = 1)
                TextLabel(post.text, 14.sp, color = PrimaryText, maxLines = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TextLabel(if (post.liked) "已赞" else "赞", 12.sp, color = SecondaryText, modifier = Modifier.clickable { onEvent(MomentsUiEvent.LikeRequested(post.id)) }, maxLines = 1)
                    TextLabel("评论 ${post.commentCount}", 12.sp, color = SecondaryText, modifier = Modifier.clickable { onEvent(MomentsUiEvent.CommentRequested(post.id)) }, maxLines = 1)
                }
            }
        }
    }
}
