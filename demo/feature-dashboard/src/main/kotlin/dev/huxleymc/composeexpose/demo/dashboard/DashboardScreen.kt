package dev.huxleymc.composeexpose.demo.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.huxleymc.composeexpose.demo.design.DemoTheme
import dev.huxleymc.composeexpose.demo.design.MetricCard

data class DashboardMetric(
    val label: String,
    val value: String,
)

/**
 * Presents dashboard metrics in a simple vertical layout.
 */
@Composable
fun DashboardScreen(
    title: String,
    metrics: List<DashboardMetric>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        metrics.forEach { metric ->
            MetricCard(label = metric.label, value = metric.value)
        }
    }
}

@Preview(name = "Dashboard screen", group = "dashboard")
@Composable
private fun DashboardScreenPreview() {
    DemoTheme {
        DashboardScreen(
            title = "Welcome, Huxley",
            metrics =
                listOf(
                    DashboardMetric("Open tasks", "7"),
                    DashboardMetric("Build health", "98%"),
                ),
        )
    }
}
