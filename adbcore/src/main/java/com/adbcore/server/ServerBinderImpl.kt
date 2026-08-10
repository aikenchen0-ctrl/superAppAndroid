package com.adbcore.server

import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

internal class ServerBinderImpl : IAdbCoreService.Stub() {

    private val scriptLoopManager = ScriptLoopManager()

    override fun getPid(): Int = Process.myPid()

    override fun exec(command: String?, timeoutMs: Long): String {
        if (command.isNullOrEmpty()) return ""
        return runCatching { runShell(command, timeoutMs) }
            .onFailure { Log.e(TAG, "exec failed: $command", it) }
            .getOrElse { "[adbcore exec error] ${it.javaClass.simpleName}: ${it.message ?: ""}" }
    }

    override fun startScript(taskId: String?, script: String?, intervalMs: Long): Boolean {
        if (taskId.isNullOrEmpty() || script.isNullOrEmpty()) return false
        Log.i(TAG, "startScript taskId=$taskId intervalMs=$intervalMs")
        return scriptLoopManager.start(taskId, script, intervalMs)
    }

    override fun stopScript(taskId: String?): Boolean {
        if (taskId.isNullOrEmpty()) return false
        Log.i(TAG, "stopScript taskId=$taskId")
        return scriptLoopManager.stop(taskId)
    }

    override fun stopAllScripts() {
        Log.i(TAG, "stopAllScripts")
        scriptLoopManager.stopAll()
    }

    override fun runningScriptIds(): Array<String> = scriptLoopManager.runningIds()

    private fun runShell(command: String, timeoutMs: Long): String {
        val proc = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))

        // Read on a worker thread so we can enforce a timeout
        val readerThread = Thread({
            try {
                reader.forEachLine { line ->
                    if (output.length < MAX_OUTPUT_BYTES) {
                        output.append(line).append('\n')
                    }
                }
            } catch (_: Throwable) {
            }
        }, "adbcore-exec-reader")
        readerThread.isDaemon = true
        readerThread.start()

        val finished = if (timeoutMs > 0) {
            proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            proc.waitFor(); true
        }

        if (!finished) {
            proc.destroyForcibly()
            output.append("[adbcore exec timeout after ${timeoutMs}ms]\n")
        }

        readerThread.join(500)
        if (output.length >= MAX_OUTPUT_BYTES) {
            output.append("[truncated at $MAX_OUTPUT_BYTES bytes]\n")
        }
        return output.toString()
    }

    companion object {
        private const val TAG = "AdbCoreServer"
        private const val MAX_OUTPUT_BYTES = 1024 * 1024  // 1 MB cap to protect server memory
    }
}
