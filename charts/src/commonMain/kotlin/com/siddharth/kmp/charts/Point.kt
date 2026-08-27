package com.siddharth.kmp.charts

import androidx.compose.runtime.Immutable

/**
 * One sample on a time series. [x] is normally an epoch value and [y] the measured quantity;
 * neither is interpreted here, so the same chart renders spend-over-time, transaction volume or
 * an application funnel without a per-domain wrapper.
 */
@Immutable
data class Point(
    val x: Double,
    val y: Double,
)
