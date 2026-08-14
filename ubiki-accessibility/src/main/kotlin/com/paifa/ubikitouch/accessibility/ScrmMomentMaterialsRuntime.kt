package com.paifa.ubikitouch.accessibility

import android.content.Context
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialControlRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialCopyRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialCreateRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentCopyFinderMaterialRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentCopyFinderMaterialResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialDetail
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentPostPayload
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager

private fun session(context: Context) = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()

internal fun loadScrmMomentMaterials(context: Context, route: ScrmFloatingAccountRoute): List<ScrmMomentMaterial> =
    session(context).momentApi.getMomentMaterials(scrmMomentMaterialQuery(route))

internal fun scrmMomentMaterialQuery(@Suppress("UNUSED_PARAMETER") route: ScrmFloatingAccountRoute): ScrmMomentMaterialQuery =
    ScrmMomentMaterialQuery(
        tenantId = null,
        skip = 0,
        take = 80
    )

internal fun loadScrmMomentMaterialDetail(context: Context, route: ScrmFloatingAccountRoute, material: ScrmMomentMaterial): ScrmMomentMaterialDetail =
    session(context).momentApi.getMomentMaterialDetail(material.id, material.tenantId)

internal fun createScrmMomentMaterial(context: Context, route: ScrmFloatingAccountRoute, content: String, name: String?, category: String?): ScrmMomentMaterial =
    session(context).momentApi.createMomentMaterial(
        scrmMomentMaterialCreateRequest(
            route = route,
            content = content,
            name = name,
            category = category,
            clientRequestId = "moment-material-${System.currentTimeMillis()}"
        )
    )

internal fun scrmMomentMaterialCreateRequest(
    route: ScrmFloatingAccountRoute,
    content: String,
    name: String?,
    category: String?,
    clientRequestId: String
): ScrmMomentMaterialCreateRequest = ScrmMomentMaterialCreateRequest(
    payload = ScrmMomentPostPayload(weChatId = route.weChatId, content = content),
    clientRequestId = clientRequestId,
    content = content,
    name = name,
    category = category,
    tenantId = null,
    enableImmediately = true
)

internal fun copyScrmMomentMaterial(context: Context, material: ScrmMomentMaterial): ScrmMomentMaterial =
    session(context).momentApi.copyMomentMaterial(material.id, ScrmMomentMaterialCopyRequest("${material.displayName} 副本", true))

/**
 * 视频号直播页面从已同步朋友圈创建素材草稿，不拼接原始 XML，也不在客户端伪造发布结果。
 * 测试流程：使用真实 snsId 打开视频号直播，提交后检查服务端返回的 publishReady 与 missingFields。
 */
internal fun createScrmChannelsLiveMaterial(
    context: Context,
    route: ScrmFloatingAccountRoute,
    snsId: Long,
    materialName: String?
): ScrmMomentCopyFinderMaterialResult = session(context).momentApi.copyMomentToFinderMaterial(
    snsId = snsId,
    request = ScrmMomentCopyFinderMaterialRequest(
        deviceUuid = route.deviceUuid,
        weChatId = route.weChatId,
        snsId = snsId,
        preferredType = "live",
        materialName = materialName?.trim()?.takeIf { it.isNotBlank() },
        enableImmediately = true
    )
)

internal fun archiveScrmMomentMaterial(context: Context, material: ScrmMomentMaterial): ScrmMomentMaterial =
    session(context).momentApi.archiveMomentMaterial(material.id, ScrmMomentMaterialControlRequest("app archive"))
