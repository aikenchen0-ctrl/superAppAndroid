package com.paifa.ubikitouch.accessibility.scrm

internal fun scrmContactsPanelRouteForSelectedAccount(
    selectedAccountId: String?,
    fallbackDeviceUuid: String?,
    fallbackWeChatId: String?
): ScrmFloatingAccountRoute? {
    selectedAccountId
        ?.takeIf { it.isNotBlank() }
        ?.let(::scrmFloatingAccountRouteForContactId)
        ?.let { return it }

    val deviceUuid = fallbackDeviceUuid?.takeIf { it.isNotBlank() } ?: return null
    val weChatId = fallbackWeChatId?.takeIf { it.isNotBlank() } ?: return null
    return ScrmFloatingAccountRoute(deviceUuid = deviceUuid, weChatId = weChatId)
}

internal fun scrmRouteCurrentDeviceMismatchMessage(
    route: ScrmFloatingAccountRoute,
    devices: List<ScrmDevice>
): String? {
    val currentDevice = devices.firstOrNull { device -> device.uuid == route.deviceUuid }
        ?: return null
    val currentWeChatId = currentDevice.weChatId?.takeIf { it.isNotBlank() }
        ?: return null
    if (currentWeChatId == route.weChatId) return null
    return "选择账号与设备当前微信账号不一致：当前设备 ${route.deviceUuid} 正在登录 $currentWeChatId，不能使用 ${route.weChatId} 执行该操作。请切换到对应微信账号后刷新。"
}

internal fun Throwable.toScrmContactsPanelMessage(): String {
    return when (this) {
        is ScrmException -> toUserMessage()
        is IllegalArgumentException -> message ?: "联系人操作参数无效"
        else -> message ?: "联系人操作失败"
    }
}
