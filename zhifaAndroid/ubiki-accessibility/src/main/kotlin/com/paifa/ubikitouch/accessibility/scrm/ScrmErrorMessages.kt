package com.paifa.ubikitouch.accessibility.scrm

internal fun ScrmException.toUserMessage(): String {
    return when (this) {
        is ScrmAuthenticationException -> "API Key 无效或已停用"
        is ScrmPermissionException -> "当前 API Key 没有访问权限"
        is ScrmRateLimitException -> "请求过于频繁，请稍后重试"
        is ScrmServerException -> "SCRM 服务暂时不可用（HTTP $statusCode）"
        is ScrmRequestException -> message ?: "SCRM 请求参数无效"
        is ScrmTimeoutException -> "连接 SCRM 服务超时"
        is ScrmNetworkException -> "无法连接 SCRM 服务，请检查网络和地址"
        is ScrmInvalidResponseException -> "SCRM 返回的数据格式无效"
        is ScrmLocalMediaException -> message ?: "本地媒体文件不可读取"
        is ScrmConfigurationException,
        is ScrmCredentialStorageException,
        is ScrmCredentialCorruptedException -> message ?: "SCRM 配置不可用"
    }
}
