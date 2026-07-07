package com.paifa.ubikitouch.accessibility.data

internal object FloatingChatDatabaseContract {
    const val databaseName: String = "floating_chat.db"
    const val databaseVersion: Int = 4

    const val tableThreads: String = "chat_threads"
    const val tableMessages: String = "chat_messages"
    const val tableFiles: String = "chat_files"
    const val tableMessageFiles: String = "chat_message_files"
    const val tableMomentPosts: String = "moment_posts"
    const val tableContactProfiles: String = "contact_profiles"
    const val tableGroupProfiles: String = "group_profiles"

    val createStatements: List<String> = listOf(
        """
            CREATE TABLE IF NOT EXISTS $tableThreads (
                thread_id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                title TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableMessages (
                message_id TEXT PRIMARY KEY,
                thread_id TEXT NOT NULL REFERENCES $tableThreads(thread_id) ON DELETE CASCADE,
                sender_id TEXT,
                sender_name TEXT NOT NULL,
                message_type TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                display_time TEXT,
                is_from_me INTEGER NOT NULL DEFAULT 0,
                kind TEXT NOT NULL,
                presentation TEXT NOT NULL,
                connection_target TEXT NOT NULL,
                connection_target_id TEXT,
                detail TEXT,
                quote_author TEXT,
                quote_text TEXT,
                card_kind TEXT,
                card_name TEXT,
                card_subtitle TEXT,
                app_name TEXT,
                location_title TEXT,
                location_address TEXT,
                resource_url TEXT,
                file_name TEXT,
                file_format TEXT,
                file_size_label TEXT,
                file_preview_lines TEXT,
                visibility TEXT,
                access_state TEXT,
                thumbnail_orientation TEXT,
                media_aspect_ratio REAL,
                thumbnail_url TEXT,
                media_duration_ms INTEGER,
                media_mime_type TEXT,
                inline_tokens TEXT,
                metadata_json TEXT
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableFiles (
                file_id TEXT PRIMARY KEY,
                content_key TEXT NOT NULL UNIQUE,
                uri TEXT NOT NULL,
                preview_uri TEXT,
                mime_type TEXT,
                display_name TEXT,
                size_bytes INTEGER,
                duration_ms INTEGER,
                width INTEGER,
                height INTEGER,
                created_at INTEGER NOT NULL,
                metadata_json TEXT
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableMessageFiles (
                message_id TEXT NOT NULL REFERENCES $tableMessages(message_id) ON DELETE CASCADE,
                file_id TEXT NOT NULL REFERENCES $tableFiles(file_id) ON DELETE RESTRICT,
                role TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(message_id, file_id, role, position)
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableMomentPosts (
                post_id TEXT PRIMARY KEY,
                author TEXT NOT NULL,
                content TEXT NOT NULL,
                display_time TEXT NOT NULL,
                avatar_text TEXT NOT NULL,
                avatar_color INTEGER NOT NULL,
                media_kind TEXT,
                media_uri TEXT,
                media_preview_uri TEXT,
                media_orientation TEXT,
                media_aspect_ratio REAL,
                media_width_dp INTEGER,
                media_height_dp INTEGER,
                media_color INTEGER,
                media_label TEXT,
                link_title TEXT,
                source_label TEXT,
                liked_by TEXT,
                comments_json TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableContactProfiles (
                account_id TEXT NOT NULL,
                contact_id TEXT NOT NULL,
                remark TEXT,
                tags TEXT,
                memo TEXT,
                friend_circle_visible INTEGER NOT NULL DEFAULT 1,
                only_chat INTEGER NOT NULL DEFAULT 0,
                phone TEXT,
                source TEXT,
                added_time TEXT,
                common_group_count INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(account_id, contact_id)
            )
        """.trimIndent(),
        """
            CREATE TABLE IF NOT EXISTS $tableGroupProfiles (
                account_id TEXT NOT NULL,
                group_id TEXT NOT NULL,
                group_name TEXT,
                remark TEXT,
                announcement TEXT,
                my_nickname TEXT,
                mute INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                save_to_contacts INTEGER NOT NULL DEFAULT 0,
                show_member_nicknames INTEGER NOT NULL DEFAULT 1,
                show_member_avatars INTEGER NOT NULL DEFAULT 1,
                background_label TEXT,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(account_id, group_id)
            )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_created ON $tableMessages(thread_id, created_at)",
        "CREATE INDEX IF NOT EXISTS idx_chat_message_files_file ON $tableMessageFiles(file_id)",
        "CREATE INDEX IF NOT EXISTS idx_moment_posts_created ON $tableMomentPosts(created_at DESC)",
        "CREATE INDEX IF NOT EXISTS idx_contact_profiles_contact ON $tableContactProfiles(contact_id)",
        "CREATE INDEX IF NOT EXISTS idx_group_profiles_group ON $tableGroupProfiles(group_id)"
    )

    fun migrationStatements(oldVersion: Int, newVersion: Int): List<String> {
        require(oldVersion <= newVersion) {
            "Cannot downgrade floating chat database from $oldVersion to $newVersion"
        }
        if (oldVersion == newVersion) return emptyList()
        return buildList {
            if (oldVersion < 2 && newVersion >= 2) {
                add(createStatements.first { statement -> statement.contains("CREATE TABLE IF NOT EXISTS $tableMomentPosts") })
                add("CREATE INDEX IF NOT EXISTS idx_moment_posts_created ON $tableMomentPosts(created_at DESC)")
            }
            if (oldVersion < 3 && newVersion >= 3) {
                add(createStatements.first { statement -> statement.contains("CREATE TABLE IF NOT EXISTS $tableContactProfiles") })
                add("CREATE INDEX IF NOT EXISTS idx_contact_profiles_contact ON $tableContactProfiles(contact_id)")
            }
            if (oldVersion < 4 && newVersion >= 4) {
                add(createStatements.first { statement -> statement.contains("CREATE TABLE IF NOT EXISTS $tableGroupProfiles") })
                add("CREATE INDEX IF NOT EXISTS idx_group_profiles_group ON $tableGroupProfiles(group_id)")
            }
        }
    }
}
