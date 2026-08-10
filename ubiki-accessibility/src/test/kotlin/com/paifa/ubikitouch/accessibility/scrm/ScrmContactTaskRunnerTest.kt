package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmContactTaskRunnerTest {
    @Test
    fun contactSyncWaitsForFinalTaskSuccessBeforeReloadingContacts() {
        val taskApi = FakeTaskApi(
            ScrmTaskResult(
                taskId = 42L,
                success = true,
                status = "processing",
                resultUnknown = false,
                receivedAt = "2026-07-12T10:00:00Z",
                rawHidden = true
            ),
            ScrmTaskResult(
                taskId = 42L,
                success = true,
                status = "success",
                resultUnknown = false,
                message = "contacts synced",
                data = Json.parseToJsonElement("""{"friendId":"wxid_found"}"""),
                receivedAt = "2026-07-12T10:00:02Z",
                rawHidden = true
            )
        )
        val runner = ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 3,
            sleepMillis = {}
        )

        val outcome = runner.submitAndAwait(
            reloadContactsOnSuccess = true
        ) {
            ScrmTaskSubmissionResult(taskId = 42L, success = true)
        }

        assertEquals(42L, outcome.taskId)
        assertEquals(true, outcome.shouldReloadContacts)
        assertEquals("contacts synced", outcome.message)
        assertEquals(
            "wxid_found",
            outcome.data?.jsonObject?.get("friendId")?.jsonPrimitive?.content
        )
        assertEquals(2, taskApi.requestedTaskIds.size)
    }

    @Test
    fun failedContactSyncTaskExposesFinalTaskMessage() {
        val taskApi = FakeTaskApi(
            ScrmTaskResult(
                taskId = 43L,
                success = false,
                status = "failed",
                resultUnknown = false,
                message = "device offline",
                receivedAt = "2026-07-12T10:00:00Z",
                rawHidden = true
            )
        )
        val runner = ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        val error = runCatching {
            runner.submitAndAwait(reloadContactsOnSuccess = true) {
                ScrmTaskSubmissionResult(taskId = 43L, success = true)
            }
        }.exceptionOrNull()

        assertTrue(error is ScrmRequestException)
        assertEquals("device offline", error?.message)
    }

    @Test
    fun rejectedContactTaskStopsBeforePolling() {
        val taskApi = FakeTaskApi()
        val runner = ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 1,
            sleepMillis = {}
        )

        val error = runCatching {
            runner.submitAndAwait(reloadContactsOnSuccess = true) {
                ScrmTaskSubmissionResult(
                    taskId = 44L,
                    success = false,
                    message = "permission denied"
                )
            }
        }.exceptionOrNull()

        assertTrue(error is ScrmRequestException)
        assertEquals("permission denied", error?.message)
        assertEquals(emptyList<Long>(), taskApi.requestedTaskIds)
    }

    @Test
    fun missingRecentTaskResultStaysPendingInsteadOfError() {
        val taskApi = MissingTaskApi()
        val runner = ScrmContactTaskRunner(
            taskApi = taskApi,
            pollDelayMillis = 1L,
            maxPollAttempts = 2,
            sleepMillis = {}
        )

        val outcome = runner.submitAndAwait(reloadContactsOnSuccess = false) {
            ScrmTaskSubmissionResult(taskId = 45L, success = true, message = "queued")
        }

        assertEquals(45L, outcome.taskId)
        assertEquals(false, outcome.completed)
        assertEquals(false, outcome.shouldReloadContacts)
        assertTrue(outcome.message.contains("45"))
        assertEquals(listOf(45L, 45L), taskApi.requestedTaskIds)
    }

    private class FakeTaskApi(
        private vararg val results: ScrmTaskResult
    ) : ScrmTaskApi {
        val requestedTaskIds = mutableListOf<Long>()
        private var index = 0

        override fun getTask(taskId: Long): ScrmTaskResult {
            requestedTaskIds += taskId
            return results[index.coerceAtMost(results.lastIndex)].also {
                index += 1
            }
        }

        override fun getRecentTasks(
            deviceUuid: String?,
            count: Int
        ): ScrmRecentTaskResults {
            error("not used")
        }
    }

    private class MissingTaskApi : ScrmTaskApi {
        val requestedTaskIds = mutableListOf<Long>()

        override fun getTask(taskId: Long): ScrmTaskResult {
            requestedTaskIds += taskId
            throw ScrmRequestException(
                statusCode = 404,
                message = "未找到该 taskId 的近期结果，可能 Android 尚未回包"
            )
        }

        override fun getRecentTasks(
            deviceUuid: String?,
            count: Int
        ): ScrmRecentTaskResults {
            return ScrmRecentTaskResults(count = count)
        }
    }
}
