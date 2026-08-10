package com.paifa.ubikitouch.accessibility

import android.content.Context
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncMomentMessagesRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncMomentsRequest
import com.paifa.ubikitouch.accessibility.scrm.submitScrmMomentTaskAndAwait
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentPostAttachment
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentPostPayload
import com.paifa.ubikitouch.accessibility.scrm.ScrmPostMomentRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentTaskAwaitOutcome
import com.paifa.ubikitouch.accessibility.scrm.uploadScrmMomentMedia
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentLikeRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentCommentRequest

internal data class ScrmMomentsLoadResult(
    val posts: List<AppMomentPost>,
    val message: String
)

internal fun loadScrmMoments(context: Context, route: ScrmFloatingAccountRoute): ScrmMomentsLoadResult {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    val syncMoments = submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.syncMoments(ScrmSyncMomentsRequest(route.deviceUuid, route.weChatId, startTime = 0L))
    }
    val syncMessages = submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.syncMomentMessages(
            ScrmSyncMomentMessagesRequest(route.deviceUuid, route.weChatId, onlyComment = false, getAll = true)
        )
    }
    val posts = (syncMoments.data + syncMessages.data)
        .flatMap(::scrmMomentPostsFromTaskData)
        .distinctBy { it.id }
        .sortedByDescending { it.createdAt }
    return ScrmMomentsLoadResult(posts, if (posts.isEmpty()) "同步任务已提交，服务端暂无可展示的朋友圈明细" else "已同步 ${posts.size} 条真实朋友圈")
}

internal fun publishScrmMoment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    content: String,
    media: AppMomentMedia?,
    clientRequestId: String
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    val uploadedMedia = uploadScrmMomentMedia(context, session.messageApi, media)
    val trimmedContent = content.trim().takeIf { it.isNotBlank() }
    return submitScrmMomentTaskAndAwait(session.taskApi, treatMissingRecentTaskAsAccepted = true) {
        session.momentApi.postMoment(
            ScrmPostMomentRequest(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId,
                clientRequestId = clientRequestId,
                content = trimmedContent,
                attachmentType = uploadedMedia?.attachmentType,
                attachments = uploadedMedia?.url?.let(::listOf),
                payload = ScrmMomentPostPayload(
                    clientRequestId = clientRequestId,
                    weChatId = route.weChatId,
                    content = trimmedContent,
                    attachment = uploadedMedia?.let { ScrmMomentPostAttachment(it.attachmentTypeCode, listOf(it.url)) }
                )
            )
        )
    }
}

internal fun likeScrmMoment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    circleId: Long,
    cancel: Boolean
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    return submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.likeMoment(ScrmMomentLikeRequest(route.deviceUuid, route.weChatId, circleId, cancel))
    }
}

internal fun commentScrmMoment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    circleId: Long,
    text: String
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    return submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.commentMoment(
            ScrmMomentCommentRequest(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId,
                circleId = circleId,
                content = text,
                replyCommentId = 0L,
                isResend = false
            )
        )
    }
}
