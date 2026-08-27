package com.siddharth.kmp.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import kotlinx.collections.immutable.ImmutableList

/**
 * A single line chart over [data].
 *
 * This is the whole public surface of `:charts` on purpose. Vico is configurable to a fault, and
 * an app that reaches past this wrapper into `CartesianChartHost` directly re-couples itself to a
 * pre-1.0 API — the exact coupling this module exists to absorb. One chart configured properly
 * beats five half-configured ones; widen this only when a real screen needs something it cannot
 * express.
 *
 * Empty [data] renders an empty chart rather than throwing: a series with no points yet is a
 * normal state on a first run, not an error.
 */
@Composable
fun TimeSeriesChart(
    data: ImmutableList<Point>,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }

    // Vico's producer is push-based and its transaction is a suspend call, so the series is fed
    // from an effect keyed on the data rather than during composition.
    LaunchedEffect(data) {
        producer.runTransaction {
            if (data.isNotEmpty()) {
                lineSeries { series(data.map { it.x }, data.map { it.y }) }
            }
        }
    }

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
        modelProducer = producer,
        modifier = modifier,
    )
}
