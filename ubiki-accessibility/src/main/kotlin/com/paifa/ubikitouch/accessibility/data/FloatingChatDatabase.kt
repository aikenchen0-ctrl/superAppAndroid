package com.paifa.ubikitouch.accessibility.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class FloatingChatDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    FloatingChatDatabaseContract.databaseName,
    null,
    FloatingChatDatabaseContract.databaseVersion
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        FloatingChatDatabaseContract.createStatements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        FloatingChatDatabaseContract.migrationStatements(oldVersion, newVersion).forEach(db::execSQL)
    }
}
