package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class MomentPostUiState(
    val id: String,
    val authorName: String,
    val text: String,
    val liked: Boolean = false,
    val commentCount: Int = 0
)

data class MomentsUiState(
    val posts: List<MomentPostUiState> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

sealed interface MomentsUiEvent {
    data object BackRequested : MomentsUiEvent
    data object RefreshRequested : MomentsUiEvent
    data object ComposeRequested : MomentsUiEvent
    data class LikeRequested(val postId: String) : MomentsUiEvent
    data class CommentRequested(val postId: String, val text: String = "") : MomentsUiEvent
}

sealed interface MomentsAction {
    data object Back : MomentsAction
    data object Refresh : MomentsAction
    data object Compose : MomentsAction
    data class Like(val postId: String) : MomentsAction
    data class Comment(val postId: String) : MomentsAction
}

fun momentsAction(event: MomentsUiEvent): MomentsAction = when (event) {
    MomentsUiEvent.BackRequested -> MomentsAction.Back
    MomentsUiEvent.RefreshRequested -> MomentsAction.Refresh
    MomentsUiEvent.ComposeRequested -> MomentsAction.Compose
    is MomentsUiEvent.LikeRequested -> MomentsAction.Like(event.postId)
    is MomentsUiEvent.CommentRequested -> MomentsAction.Comment(event.postId)
}

fun reduceMoments(state: MomentsUiState, event: MomentsUiEvent): MomentsUiState = when (event) {
    is MomentsUiEvent.LikeRequested -> state.copy(posts = state.posts.map { post ->
        if (post.id == event.postId) post.copy(liked = !post.liked) else post
    })
    else -> state
}
