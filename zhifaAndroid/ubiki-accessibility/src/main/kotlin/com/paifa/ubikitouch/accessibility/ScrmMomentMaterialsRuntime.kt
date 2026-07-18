package com.paifa.ubikitouch.accessibility

import android.content.Context
import com.paifa.ubikitouch.accessibility.floatingchat.moments.momentMaterialTenantIdForRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterial
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialControlRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialCopyRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialCreateRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialDetail
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentMaterialQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmMomentPostPayload
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager

private fun session(context: Context) = ScrmSettingsManager(context.applicationContext).loadSelectedSessionOrBootstrap()

internal fun loadScrmMomentMaterials(context: Context, route: ScrmFloatingAccountRoute): List<ScrmMomentMaterial> =
    session(context).momentApi.getMomentMaterials(ScrmMomentMaterialQuery(momentMaterialTenantIdForRoute(route), 80))

internal fun loadScrmMomentMaterialDetail(context: Context, route: ScrmFloatingAccountRoute, material: ScrmMomentMaterial): ScrmMomentMaterialDetail =
    session(context).momentApi.getMomentMaterialDetail(material.id, material.tenantId ?: momentMaterialTenantIdForRoute(route))

internal fun createScrmMomentMaterial(context: Context, route: ScrmFloatingAccountRoute, content: String, name: String?, category: String?): ScrmMomentMaterial =
    session(context).momentApi.createMomentMaterial(
        ScrmMomentMaterialCreateRequest(
            payload = ScrmMomentPostPayload(weChatId = route.weChatId, content = content),
            clientRequestId = "moment-material-${System.currentTimeMillis()}",
            content = content,
            name = name,
            category = category,
            tenantId = momentMaterialTenantIdForRoute(route),
            enableImmediately = true
        )
    )

internal fun copyScrmMomentMaterial(context: Context, material: ScrmMomentMaterial): ScrmMomentMaterial =
    session(context).momentApi.copyMomentMaterial(material.id, ScrmMomentMaterialCopyRequest("${material.displayName} 副本", true))

internal fun archiveScrmMomentMaterial(context: Context, material: ScrmMomentMaterial): ScrmMomentMaterial =
    session(context).momentApi.archiveMomentMaterial(material.id, ScrmMomentMaterialControlRequest("app archive"))
