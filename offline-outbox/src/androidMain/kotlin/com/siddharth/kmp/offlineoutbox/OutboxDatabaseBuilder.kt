package com.siddharth.kmp.offlineoutbox

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun buildOutboxDatabase(context: Context): OutboxDatabase =
    Room.databaseBuilder<OutboxDatabase>(
        context = context.applicationContext,
        name = "offline_outbox.db",
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
