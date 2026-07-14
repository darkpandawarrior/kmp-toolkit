package com.siddharth.kmp.offlineoutbox

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

// A Room @Database is closed — a library cannot splice its @Dao/@Entity into a host app's own
// @Database. So this module ships its OWN tiny database (one entity) and its own per-platform
// builder (own .db file); a consumer runs this ALONGSIDE its own database, never inside it.
@Database(
    entities = [SubmitDraftEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(OutboxDatabaseConstructor::class)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun submitDraftDao(): SubmitDraftDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object OutboxDatabaseConstructor : RoomDatabaseConstructor<OutboxDatabase>
