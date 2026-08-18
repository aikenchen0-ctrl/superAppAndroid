package com.paifa.univerge.accessibility.data

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatDatabaseV8MigrationTest {
    /**
     * 测试流程：从已存朋友圈缓存升级应用，确认链接地址列被无损加入且旧动态不被删除。
     */
    @Test
    fun v7ToV8MigrationPersistsMomentLinkUrlsWithoutDeletingPosts() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE moment_posts(post_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
                statement.execute("INSERT INTO moment_posts(post_id, created_at) VALUES ('legacy-moment', 1)")
            }

            val migration = FloatingChatDatabaseContract.migrationStatements(7, 8)
            migration.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

            assertTrue(migration.any { it.contains("ALTER TABLE moment_posts ADD COLUMN link_url TEXT") })
            assertFalse(migration.any { it.trimStart().startsWith("DELETE ", ignoreCase = true) })
            assertEquals(1, connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM moment_posts").use { result ->
                    result.next()
                    result.getInt(1)
                }
            })
        }
    }
}
