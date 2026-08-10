package com.paifa.ubikitouch.accessibility.floatingchat.tools

internal class LocationRuntimeAdapter(
    private val refreshOperation: suspend () -> List<String>,
    private val sendOperation: suspend (String) -> Unit
) : LocationRuntimePort {
    override suspend fun refresh(): List<String> = refreshOperation()

    override suspend fun send(optionId: String) = sendOperation(optionId)
}
