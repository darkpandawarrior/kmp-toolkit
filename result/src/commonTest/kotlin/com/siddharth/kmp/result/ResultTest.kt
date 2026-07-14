package com.siddharth.kmp.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultTest {

    private val ok: Result<Int, DataError> = Result.Success(2)
    private val err: Result<Int, DataError> = Result.Failure(DataError.Network.NO_INTERNET)

    @Test
    fun map_transformsSuccess_passesFailureThrough() {
        assertEquals(Result.Success(3), ok.map { it + 1 })
        assertEquals(err, err.map { it + 1 })
    }

    @Test
    fun flatMap_chainsSuccess_shortCircuitsFailure() {
        assertEquals(Result.Success(20), ok.flatMap { Result.Success(it * 10) })
        assertEquals(err, err.flatMap { Result.Success(it * 10) })
        val chainedFailure: Result<Int, DataError> = ok.flatMap { Result.Failure(DataError.Local.NOT_FOUND) }
        assertEquals(Result.Failure(DataError.Local.NOT_FOUND), chainedFailure)
    }

    @Test
    fun fold_selectsCorrectBranch() {
        assertEquals("ok:2", ok.fold(onSuccess = { "ok:$it" }, onFailure = { "err:$it" }))
        assertEquals("err:NO_INTERNET", err.fold(onSuccess = { "ok:$it" }, onFailure = { "err:$it" }))
    }

    @Test
    fun mapError_transformsErrorArm_only() {
        assertEquals(ok, ok.mapError { DataError.Local.UNKNOWN })
        assertEquals(Result.Failure(DataError.Local.UNKNOWN), err.mapError { DataError.Local.UNKNOWN })
    }

    @Test
    fun onSuccess_onFailure_fireOnCorrectArm() {
        var successes = 0
        var failures = 0
        ok.onSuccess { successes++ }.onFailure { failures++ }
        err.onSuccess { successes++ }.onFailure { failures++ }
        assertEquals(1, successes)
        assertEquals(1, failures)
    }

    @Test
    fun getOrNull_errorOrNull_extractCorrectArm() {
        assertEquals(2, ok.getOrNull())
        assertNull(ok.errorOrNull())
        assertEquals(DataError.Network.NO_INTERNET, err.errorOrNull())
        assertNull(err.getOrNull())
    }

    @Test
    fun asEmpty_dropsSuccessPayload() {
        assertEquals<EmptyResult<DataError>>(Result.Success(Unit), ok.asEmpty())
        assertEquals<EmptyResult<DataError>>(Result.Failure(DataError.Network.NO_INTERNET), err.asEmpty())
    }
}
