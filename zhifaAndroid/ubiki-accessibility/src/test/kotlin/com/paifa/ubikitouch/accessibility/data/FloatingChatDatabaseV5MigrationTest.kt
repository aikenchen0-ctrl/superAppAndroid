package com.paifa.ubikitouch.accessibility.data

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatDatabaseV5MigrationTest {
    @Test
    fun `v4 to v5 migration preserves local data and applies remote defaults`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            createLegacyV4Schema(connection)
            insertLegacyV4Data(connection)

            FloatingChatDatabaseContract.migrationStatements(4, 5).forEach { statement ->
                connection.createStatement().use { it.execute(statement) }
            }

            assertEquals(1, count(connection, "chat_threads"))
            assertEquals(1, count(connection, "chat_messages"))
            assertEquals(1, count(connection, "chat_files"))
            assertEquals(1, count(connection, "chat_message_files"))
            assertEquals(1, count(connection, "moment_posts"))
            assertEquals(1, count(connection, "contact_profiles"))
            assertEquals(1, count(connection, "group_profiles"))
            assertEquals("LOCAL_ONLY", scalarString(connection, "SELECT send_state FROM chat_messages"))
            assertNull(scalarLongOrNull(connection, "SELECT remote_task_id FROM chat_messages"))
            assertTrue(tableExists(connection, "scrm_accounts"))
            assertTrue(tableExists(connection, "scrm_outbox"))
            assertTrue(tableExists(connection, "scrm_tasks"))
        }
    }

    @Test
    fun `v5 client request id index rejects duplicate outgoing commands`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createLegacyV4Schema(connection)
            FloatingChatDatabaseContract.migrationStatements(4, 5).forEach { statement ->
                connection.createStatement().use { it.execute(statement) }
            }
            insertThread(connection, "private:one")
            insertMinimalMessage(connection, "message-1", "private:one", "request-1")

            val result = runCatching {
                insertMinimalMessage(connection, "message-2", "private:one", "request-1")
            }

            assertTrue(result.exceptionOrNull() is SQLException)
        }
    }

    private fun createLegacyV4Schema(connection: Connection) {
        val statements = listOf(
            """
                CREATE TABLE chat_threads (
                    thread_id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    title TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent(),
            """
                CREATE TABLE chat_messages (
                    message_id TEXT PRIMARY KEY,
                    thread_id TEXT NOT NULL REFERENCES chat_threads(thread_id) ON DELETE CASCADE,
                    sender_name TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    body TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    is_from_me INTEGER NOT NULL DEFAULT 0,
                    kind TEXT NOT NULL,
                    presentation TEXT NOT NULL,
                    connection_target TEXT NOT NULL
                )
            """.trimIndent(),
            """
                CREATE TABLE chat_files (
                    file_id TEXT PRIMARY KEY,
                    content_key TEXT NOT NULL UNIQUE,
                    uri TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(),
            """
                CREATE TABLE chat_message_files (
                    message_id TEXT NOT NULL REFERENCES chat_messages(message_id) ON DELETE CASCADE,
                    file_id TEXT NOT NULL REFERENCES chat_files(file_id) ON DELETE RESTRICT,
                    role TEXT NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(message_id, file_id, role, position)
                )
            """.trimIndent(),
            "CREATE TABLE moment_posts (post_id TEXT PRIMARY KEY, content TEXT NOT NULL)",
            "CREATE TABLE contact_profiles (account_id TEXT NOT NULL, contact_id TEXT NOT NULL, PRIMARY KEY(account_id, contact_id))",
            "CREATE TABLE group_profiles (account_id TEXT NOT NULL, group_id TEXT NOT NULL, PRIMARY KEY(account_id, group_id))"
        )
        statements.forEach { statement ->
            connection.createStatement().use { it.execute(statement) }
        }
    }

    private fun insertLegacyV4Data(connection: Connection) {
        insertThread(connection, "private:legacy")
        insertMinimalMessage(connection, "message-legacy", "private:legacy", null)
        connection.prepareStatement(
            "INSERT INTO chat_files(file_id, content_key, uri, created_at) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, "file-legacy")
            statement.setString(2, "sha256:legacy")
            statement.setString(3, "file:///legacy.txt")
            statement.setLong(4, 1L)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO chat_message_files(message_id, file_id, role, position) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, "message-legacy")
            statement.setString(2, "file-legacy")
            statement.setString(3, "file")
            statement.setInt(4, 0)
            statement.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO moment_posts(post_id, content) VALUES (?, ?)").use {
            it.setString(1, "moment-legacy")
            it.setString(2, "legacy moment")
            it.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO contact_profiles(account_id, contact_id) VALUES (?, ?)"
        ).use {
            it.setString(1, "account-legacy")
            it.setString(2, "contact-legacy")
            it.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO group_profiles(account_id, group_id) VALUES (?, ?)"
        ).use {
            it.setString(1, "account-legacy")
            it.setString(2, "group-legacy")
            it.executeUpdate()
        }
    }

    private fun insertThread(connection: Connection, threadId: String) {
        connection.prepareStatement(
            "INSERT INTO chat_threads(thread_id, kind, created_at, updated_at) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, threadId)
            statement.setString(2, "private")
            statement.setLong(3, 1L)
            statement.setLong(4, 1L)
            statement.executeUpdate()
        }
    }

    private fun insertMinimalMessage(
        connection: Connection,
        messageId: String,
        threadId: String,
        clientRequestId: String?
    ) {
        val sql = if (clientRequestId == null) {
            """
                INSERT INTO chat_messages(
                    message_id, thread_id, sender_name, message_type, body, created_at,
                    is_from_me, kind, presentation, connection_target
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else {
            """
                INSERT INTO chat_messages(
                    message_id, thread_id, sender_name, message_type, body, created_at,
                    is_from_me, kind, presentation, connection_target, client_request_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, messageId)
            statement.setString(2, threadId)
            statement.setString(3, "Tester")
            statement.setString(4, "Text")
            statement.setString(5, "hello")
            statement.setLong(6, 1L)
            statement.setInt(7, 1)
            statement.setString(8, "Normal")
            statement.setString(9, "Bubble")
            statement.setString(10, "Account")
            if (clientRequestId != null) statement.setString(11, clientRequestId)
            statement.executeUpdate()
        }
    }

    private fun count(connection: Connection, table: String): Int {
        return connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun tableExists(connection: Connection, table: String): Boolean {
        return connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun scalarString(connection: Connection, query: String): String {
        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun scalarLongOrNull(connection: Connection, query: String): Long? {
        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use { result ->
                result.next()
                result.getLong(1).takeUnless { result.wasNull() }
            }
        }
    }
}
