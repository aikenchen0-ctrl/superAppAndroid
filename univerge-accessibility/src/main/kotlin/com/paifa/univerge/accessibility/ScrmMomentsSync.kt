package com.paifa.univerge.accessibility

import android.content.Context
import com.paifa.univerge.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.ScrmSyncMomentMessagesRequest
import com.paifa.univerge.accessibility.scrm.ScrmSyncMomentsRequest
import com.paifa.univerge.accessibility.scrm.submitScrmMomentTaskAndAwait
import com.paifa.univerge.accessibility.scrm.ScrmMomentPostAttachment
import com.paifa.univerge.accessibility.scrm.ScrmMomentPostPayload
import com.paifa.univerge.accessibility.scrm.ScrmMomentPublishOptions
import com.paifa.univerge.accessibility.scrm.ScrmMomentVisibilityPayload
import com.paifa.univerge.accessibility.scrm.ScrmPostMomentRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentTaskAwaitOutcome
import com.paifa.univerge.accessibility.scrm.uploadScrmMomentMedia
import com.paifa.univerge.accessibility.scrm.ScrmMomentLikeRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentCommentRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentCommentDeleteRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentDetailRequest
import kotlinx.serialization.json.JsonElement

internal data class ScrmMomentsLoadResult(
    val posts: List<AppMomentPost>,
    val message: String
)

private const val MomentDetailReadLimit = 40
private const val MomentRecentTaskCount = 40

private data class ScrmMomentDetailLoadResult(
    val posts: List<AppMomentPost>,
    val failureMessage: String? = null
)

/**
 * 接口：同步或读取近期朋友圈任务，再按真实 circleId 回读详情。
 * 测试流程：打开朋友圈后点击刷新或同步，确认评论、媒体和点赞等详情来自 SCRM 回读。
 */
internal fun loadScrmMoments(
    context: Context,
    route: ScrmFloatingAccountRoute,
    syncNow: Boolean = true
): ScrmMomentsLoadResult {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    val taskPayloads = mutableListOf<JsonElement?>()
    if (syncNow) {
        val syncMoments = submitScrmMomentTaskAndAwait(session.taskApi) {
            session.momentApi.syncMoments(ScrmSyncMomentsRequest(route.deviceUuid, route.weChatId, startTime = 0L))
        }
        val syncMessages = submitScrmMomentTaskAndAwait(session.taskApi) {
            session.momentApi.syncMomentMessages(
                ScrmSyncMomentMessagesRequest(route.deviceUuid, route.weChatId, onlyComment = false, getAll = true)
            )
        }
        taskPayloads += syncMoments.data
        taskPayloads += syncMessages.data
    } else {
        session.taskApi
            .getRecentTasks(deviceUuid = route.deviceUuid, count = MomentRecentTaskCount)
            .items
            .orEmpty()
            .forEach { taskPayloads += it.data }
    }
    val syncedPosts = taskPayloads
        .flatMap(::scrmMomentPostsFromTaskData)
        .distinctBy { it.id }
        .sortedByDescending { it.createdAt }
    val details = loadScrmMomentDetails(
        route = route,
        posts = syncedPosts,
        requestDetail = { request ->
            submitScrmMomentTaskAndAwait(session.taskApi) {
                session.momentApi.getMomentDetail(request)
            }
        }
    )
    val posts = (details.posts + syncedPosts)
        .distinctBy { it.id }
        .sortedByDescending { it.createdAt }
    val message = if (posts.isEmpty()) {
        if (syncNow) "同步任务已提交，服务端暂无可展示的朋友圈明细" else "暂无已同步的朋友圈记录"
    } else {
        "已读取 ${posts.size} 条真实朋友圈"
    }
    return ScrmMomentsLoadResult(
        posts = posts,
        message = details.failureMessage?.let { "$message；详情回读未完成：$it" } ?: message
    )
}

private fun loadScrmMomentDetails(
    route: ScrmFloatingAccountRoute,
    posts: List<AppMomentPost>,
    requestDetail: (ScrmMomentDetailRequest) -> ScrmMomentTaskAwaitOutcome
): ScrmMomentDetailLoadResult {
    val detailPosts = mutableListOf<AppMomentPost>()
    val circleIds = posts.mapNotNull { it.circleId ?: scrmCircleIdForMomentPostId(it.id) }
        .filter { it > 0L }
        .distinct()
        .take(MomentDetailReadLimit)
    for (circleId in circleIds) {
        try {
            val outcome = requestDetail(
                ScrmMomentDetailRequest(
                    deviceUuid = route.deviceUuid,
                    weChatId = route.weChatId,
                    circleId = circleId,
                    getBigMap = true
                )
            )
            detailPosts += outcome.data.flatMap(::scrmMomentPostsFromTaskData)
        } catch (error: Exception) {
            return ScrmMomentDetailLoadResult(
                posts = detailPosts,
                failureMessage = error.message ?: "详情任务失败"
            )
        }
    }
    return ScrmMomentDetailLoadResult(posts = detailPosts)
}

internal fun publishScrmMoment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    content: String,
    media: AppMomentMedia?,
    clientRequestId: String,
    options: ScrmMomentPublishOptions = ScrmMomentPublishOptions()
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    val uploadedMedia = uploadScrmMomentMedia(context, session.messageApi, media)
    val trimmedContent = content.trim().takeIf { it.isNotBlank() }
    val selectedFriendWxids = options.normalizedSelectedFriendWxids
    val remindWxids = options.normalizedRemindWxids
    val visibleFriends = when (options.visibility) {
        com.paifa.univerge.accessibility.scrm.ScrmMomentVisibility.PartVisible,
        com.paifa.univerge.accessibility.scrm.ScrmMomentVisibility.NotVisible ->
            selectedFriendWxids.takeIf { it.isNotEmpty() }

        else -> null
    }
    return submitScrmMomentTaskAndAwait(session.taskApi, treatMissingRecentTaskAsAccepted = true) {
        session.momentApi.postMoment(
            ScrmPostMomentRequest(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId,
                clientRequestId = clientRequestId,
                content = trimmedContent,
                attachmentType = uploadedMedia?.attachmentType,
                attachments = uploadedMedia?.url?.let(::listOf),
                sendSlow = true,
                notiUsers = remindWxids.takeIf { it.isNotEmpty() },
                visibleType = options.visibility.apiValue,
                friendWxids = if (options.visibility == com.paifa.univerge.accessibility.scrm.ScrmMomentVisibility.PartVisible) {
                    visibleFriends
                } else {
                    null
                },
                invisibleFriendWxids = if (options.visibility == com.paifa.univerge.accessibility.scrm.ScrmMomentVisibility.NotVisible) {
                    visibleFriends
                } else {
                    null
                },
                poi = options.poi,
                payload = ScrmMomentPostPayload(
                    clientRequestId = clientRequestId,
                    weChatId = route.weChatId,
                    content = trimmedContent,
                    attachment = uploadedMedia?.let { ScrmMomentPostAttachment(it.attachmentTypeCode, listOf(it.url)) },
                    sendSlow = true,
                    notiUsers = remindWxids.takeIf { it.isNotEmpty() },
                    visible = ScrmMomentVisibilityPayload(
                        type = options.visibility.payloadType,
                        friends = visibleFriends
                    ),
                    poi = options.poi
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

/** 接口：评论或回复朋友圈，replyCommentId 为 0 表示普通评论。 */
internal fun commentScrmMoment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    circleId: Long,
    text: String,
    toWeChatId: String? = null,
    replyCommentId: Long = 0L
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    return submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.commentMoment(
            ScrmMomentCommentRequest(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId,
                circleId = circleId,
                toWeChatId = toWeChatId,
                content = text,
                replyCommentId = replyCommentId,
                isResend = false
            )
        )
    }
}

/**
 * 接口：删除本人已确认的朋友圈评论。
 * 测试流程：在自己发布的真实评论菜单中点击删除，等待任务完成后评论才从时间线移除。
 */
internal fun deleteScrmMomentComment(
    context: Context,
    route: ScrmFloatingAccountRoute,
    circleId: Long,
    commentId: Long,
    publishTime: Long
): ScrmMomentTaskAwaitOutcome {
    val session = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()
    return submitScrmMomentTaskAndAwait(session.taskApi) {
        session.momentApi.deleteMomentComment(
            ScrmMomentCommentDeleteRequest(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId,
                circleId = circleId,
                commentId = commentId,
                publishTime = publishTime
            )
        )
    }
}
