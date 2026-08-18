package com.paifa.univerge.accessibility.data

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingChatMomentMetadataPersistenceTest {
    private lateinit var context: Application
    private val closeables = mutableListOf<AutoCloseable>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    @After
    fun tearDown() {
        closeables.asReversed().forEach(AutoCloseable::close)
        closeables.clear()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    /**
     * 测试流程：写入含真实朋友圈和评论标识的缓存，关闭存储后重新打开，
     * 确认回复评论和删除本人评论所需参数不会因悬浮聊天重建而丢失。
     */
    @Test(timeout = 60_000L)
    fun momentRemoteIdentifiersSurviveSqliteStoreRecreation() {
        val expected = LocalMomentPost(
            postId = "scrm-moment:998",
            accountId = "wxid_owner",
            author = "Alice",
            authorWxId = "wxid_alice",
            content = "带真实标识的动态",
            displayTime = "刚刚",
            avatarText = "Al",
            avatarColor = 0xFF123456,
            comments = listOf(
                LocalMomentComment(
                    author = "Bob",
                    text = "回复内容",
                    id = 77L,
                    authorWxId = "wxid_bob",
                    replyTo = "Alice",
                    replyCommentId = 66L
                )
            ),
            createdAt = 1_725_000_000_000L,
            circleId = 998L,
            publishTime = 1_725_000_000L
        )

        FloatingChatMessageStore(context).also(closeables::add).upsertMomentPost(expected)
        val reopenedStore = FloatingChatMessageStore(context).also(closeables::add)

        assertEquals(listOf(expected), reopenedStore.momentPosts())
    }
}
