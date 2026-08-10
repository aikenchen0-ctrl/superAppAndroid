package com.paifa.ubikitouch.accessibility.data

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatDatabaseV6MigrationTest {
    @Test
    fun `v5 to v6 migration adds account scope without deleting moments`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE moment_posts(post_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
                statement.execute("INSERT INTO moment_posts(post_id, created_at) VALUES ('legacy-moment', 1)")
            }

            val migration = FloatingChatDatabaseContract.migrationStatements(5, 6)
            migration.forEach { sql -> connection.createStatement().use { it.execute(sql) } }

            assertTrue(migration.any { it.contains("ADD COLUMN account_id") })
            assertTrue(migration.none { it.trimStart().startsWith("DELETE ", ignoreCase = true) })
            assertEquals(1, connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM moment_posts").use { result ->
                    result.next()
                    result.getInt(1)
                }
            })
            assertEquals("", connection.createStatement().use { statement ->
                statement.executeQuery("SELECT account_id FROM moment_posts WHERE post_id = 'legacy-moment'").use { result ->
                    result.next()
                    result.getString(1)
                }
            })
        }
    }
}
