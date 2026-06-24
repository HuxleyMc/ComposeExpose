package dev.huxleymc.composeexpose.demo.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Displays one dashboard metric with a compact label and value.
 *
 * Reusable across overview, billing, and account surfaces.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview(name = "Metric card", group = "design-system")
@Composable
private fun MetricCardPreview() {
    DemoTheme {
        MetricCard(label = "Revenue", value = "$12.4k")
    }
}
