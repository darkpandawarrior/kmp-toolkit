package com.siddharth.kmp.offlineoutbox

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// A Room @Database is closed — a library cannot splice its @Dao/@Entity into a host app's own
// @Database. So this module ships its OWN tiny database and its own per-platform builder (own .db
// file); a consumer runs this ALONGSIDE its own database, never inside it. Two independent outbox
// shapes live here: SubmitDraftEntity (keyed draft store) and OpOutboxEntity (FIFO operation log).
@Database(
    entities = [SubmitDraftEntity::class, OpOutboxEntity::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(OutboxDatabaseConstructor::class)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun submitDraftDao(): SubmitDraftDao

    abstract fun opOutboxDao(): OpOutboxDao
}

// v2 adds the op_outbox table (the operation-log outbox shape). Additive — leaves submit_drafts
// untouched. Registered on every platform builder via addMigrations; never fallbackToDestructive.
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `op_outbox` (" +
                    "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`id` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, " +
                    "`attempts` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`lastError` TEXT, " +
                    "`createdAtMs` INTEGER NOT NULL)",
            )
        }
    }

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object OutboxDatabaseConstructor : RoomDatabaseConstructor<OutboxDatabase> {
    override fun initialize(): OutboxDatabase
}
