package com.adbcore.server

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages multiple long-running script loops inside the server process.
 *
 * Each loop is an independent thread that, on every iteration:
 *   1. forks `sh -c <script>`
 *   2. blocks until the child exits
 *   3. sleeps `intervalMs`
 *   4. checks the cancellation flag and either repeats or exits
 *
 * Output (stdout merged with stderr) is forwarded to logcat under
 * the tag "AdbCoreScript:<taskId>" line by line.
 */
internal class ScriptLoopManager {

    private val jobs = ConcurrentHashMap<String, ScriptLoopJob>()

    fun start(taskId: String, script: String, intervalMs: Long): Boolean {
        if (taskId.isEmpty()) return false
        val newJob = ScriptLoopJob(taskId, script, intervalMs)
        val previous = jobs.putIfAbsent(taskId, newJob)
        if (previous != null) {
            // Same id already running — keep the existing one untouched.
            return false
        }
        newJob.start()
        return true
    }

    fun stop(taskId: String): Boolean {
        val job = jobs.remove(taskId) ?: return false
        job.cancel()
        return true
    }

    fun stopAll() {
        val all = jobs.values.toList()
        jobs.clear()
        all.forEach { it.cancel() }
    }

    fun runningIds(): Array<String> = jobs.keys.toTypedArray()
}

private class ScriptLoopJob(
    taskId: String,
    private val script: String,
    private val intervalMs: Long
) {

    private val running = AtomicBoolean(true)
    private val tag = "AdbCoreScript:$taskId"
    private var currentProcess: Process? = null

    private val thread = Thread({ runLoop() }, "adbcore-loop-$taskId").apply {
        isDaemon = true
    }

    fun start() {
        thread.start()
    }

    fun cancel() {
        running.set(false)
        currentProcess?.destroy()
        thread.interrupt()
    }

    private fun runLoop() {
        Log.i(tag, "loop started (intervalMs=$intervalMs)")
        while (running.get()) {
            try {
                val proc = ProcessBuilder("sh", "-c", script)
                    .redirectErrorStream(true)
                    .start()
                currentProcess = proc

                BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                    lines.forEach { Log.i(tag, it) }
                }

                val exit = proc.waitFor()
                Log.i(tag, "iteration done (exit=$exit)")
                currentProcess = null
            } catch (_: InterruptedException) {
                Log.i(tag, "interrupted")
                break
            } catch (t: Throwable) {
                Log.e(tag, "iteration failed", t)
            }

            if (!running.get()) break
            if (intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        Log.i(tag, "loop stopped")
    }
}
