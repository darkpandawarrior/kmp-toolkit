package com.siddharth.kmp.offlineoutbox

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSHomeDirectory

fun buildOutboxDatabase(): OutboxDatabase =
    Room.databaseBuilder<OutboxDatabase>(
        name = NSHomeDirectory() + "/Documents/offline_outbox.db",
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(MIGRATION_1_2)
        .build()
