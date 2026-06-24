package dev.huxleymc.composeexpose.demo.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.huxleymc.composeexpose.demo.design.MetricCard

/**
 * Arranges dashboard metrics into a configurable number of columns.
 */
@Composable
fun AdaptiveMetricsGrid(
    metrics: List<DashboardMetric>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        metrics.chunked(safeColumns).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowMetrics.forEach { metric ->
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = metric.label,
                        value = metric.value,
                    )
                }
                repeat(safeColumns - rowMetrics.size) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}
