package com.adbcore

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists script-loop tasks so they can be re-registered with a fresh server
 * after device boot or unexpected server death.
 *
 * Tasks where [ survivesAppDeath ] is false are NOT persisted — they are
 * implicitly tied to the current host-app process lifetime.
 */
internal class TaskStore(context: Context) {

    data class Task(
        val taskId: String,
        val script: String,
        val intervalMs: Long,
        val survivesAppDeath: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", taskId)
            .put("s", script)
            .put("i", intervalMs)
            .put("p", survivesAppDeath)

        companion object {
            fun fromJson(o: JSONObject) = Task(
                taskId = o.getString("id"),
                script = o.getString("s"),
                intervalMs = o.getLong("i"),
                survivesAppDeath = o.optBoolean("p", true)
            )
        }
    }

    private val prefs = KeyStorage.getPrefs(context)

    @Synchronized
    fun put(task: Task) {
        if (!task.survivesAppDeath) return  // ephemeral tasks don't get persisted
        val map = readAll().toMutableMap()
        map[task.taskId] = task
        writeAll(map.values.toList())
    }

    @Synchronized
    fun remove(taskId: String) {
        val map = readAll().toMutableMap()
        if (map.remove(taskId) != null) writeAll(map.values.toList())
    }

    @Synchronized
    fun all(): List<Task> = readAll().values.toList()

    @Synchronized
    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private fun readAll(): Map<String, Task> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
                .associateBy { it.taskId }
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs.edit { putString(KEY, arr.toString()) }
    }

    companion object {
        private const val KEY = "script_tasks"
    }
}
