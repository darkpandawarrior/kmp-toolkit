package com.siddharth.kmp.offlineoutbox

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

fun buildOutboxDatabase(): OutboxDatabase =
    Room.databaseBuilder<OutboxDatabase>(
        name = File(System.getProperty("user.home"), ".offline-outbox/offline_outbox.db")
            .also { it.parentFile.mkdirs() }.path,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(MIGRATION_1_2)
        .build()
