package com.siddharth.kmp.offlineoutbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OpOutboxDao {
    @Insert
    suspend fun insert(entity: OpOutboxEntity)

    /** PENDING ops, oldest-first — the replay drain order. */
    @Query("SELECT * FROM op_outbox WHERE status = 'PENDING' ORDER BY seq ASC")
    suspend fun pending(): List<OpOutboxEntity>

    /** Live (non-dead) ops, oldest-first — the observable queue for UI. */
    @Query("SELECT * FROM op_outbox WHERE status != 'DEAD' ORDER BY seq ASC")
    fun observeLive(): Flow<List<OpOutboxEntity>>

    @Query("SELECT * FROM op_outbox WHERE status = 'DEAD' ORDER BY seq ASC")
    suspend fun dead(): List<OpOutboxEntity>

    @Query("DELETE FROM op_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE op_outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(
        id: String,
        error: String?,
    )

    @Query("UPDATE op_outbox SET status = 'DEAD', attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markDead(
        id: String,
        error: String?,
    )

    @Query("UPDATE op_outbox SET status = 'PENDING', attempts = 0, lastError = NULL WHERE id = :id")
    suspend fun requeue(id: String)
}
