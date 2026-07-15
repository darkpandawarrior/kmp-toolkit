package com.siddharth.kmp.offlineoutbox

import androidx.room.Entity
import androidx.room.PrimaryKey

// Append-only operation-log row. `seq` autoincrements to give a strict insertion (FIFO) order that is
// independent of wall-clock ties; `id` is a caller-opaque UUID used as the server idempotency key.
@Entity(tableName = "op_outbox")
data class OpOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val type: String,
    val payload: String,
    val attempts: Int,
    val status: String,
    val lastError: String?,
    val createdAtMs: Long,
)
