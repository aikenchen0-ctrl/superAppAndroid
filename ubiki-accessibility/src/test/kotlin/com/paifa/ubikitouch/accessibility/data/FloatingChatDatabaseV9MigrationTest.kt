package com.paifa.ubikitouch.accessibility.data

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatDatabaseV9MigrationTest {
    /**
     * 测试流程：使用已存储 v8 朋友圈缓存启动新版应用，确认真实操作标识列无损加入，
     * 旧动态仍然存在，随后可安全恢复评论、回复和删除本人评论的接口参数。
     */
    @Test
    fun v8ToV9MigrationPreservesMomentPostsAndAddsRemoteIdentifierColumns() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE moment_posts(post_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
                statement.execute("INSERT INTO moment_posts(post_id, created_at) VALUES ('legacy-moment', 1)")
            }

            val migration = FloatingChatDatabaseContract.migrationStatements(8, 9)
            migration.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

            assertTrue(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN author_wxid TEXT") })
            assertTrue(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN circle_id INTEGER") })
            assertTrue(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN publish_time INTEGER") })
            assertFalse(migration.any { it.trimStart().startsWith("DELETE ", ignoreCase = true) })
            assertEquals(1, connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM moment_posts").use { result ->
                    result.next()
                    result.getInt(1)
                }
            })
        }
    }

    @Test
    fun v1ToV9MigrationDoesNotAddColumnsAlreadyPresentInTheCreatedMomentTable() {
        val migration = FloatingChatDatabaseContract.migrationStatements(1, 9)

        assertTrue(migration.any { it.contains("idx_moment_posts_account_created") })
        assertFalse(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN account_id TEXT") })
        assertFalse(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN link_url TEXT") })
        assertFalse(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN author_wxid TEXT") })
        assertFalse(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN circle_id INTEGER") })
        assertFalse(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN publish_time INTEGER") })
    }
}
