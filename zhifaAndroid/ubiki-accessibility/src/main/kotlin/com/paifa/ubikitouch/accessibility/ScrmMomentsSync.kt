package com.paifa.ubikitouch.accessibility

import android.content.Context
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncMomentMessagesRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncMomentsRequest
import com.paifa.ubikitouch.accessibility.scrm.submitScrmMomentTaskAndAwait

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
