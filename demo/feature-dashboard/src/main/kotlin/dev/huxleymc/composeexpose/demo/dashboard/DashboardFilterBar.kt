package dev.huxleymc.composeexpose.demo.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.huxleymc.composeexpose.demo.design.DemoTheme

data class DashboardFilter(
    val id: String,
    val label: String,
)

/**
 * Presents selectable dashboard filters as chips and reports the selected filter.
 */
@Composable
fun DashboardFilterBar(
    filters: List<DashboardFilter>,
    selectedFilterId: String,
    onFilterSelected: (DashboardFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter.id == selectedFilterId,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

@Preview(name = "Dashboard filters", group = "dashboard")
@Composable
private fun DashboardFilterBarPreview() {
    DemoTheme {
        DashboardFilterBar(
            filters =
                listOf(
                    DashboardFilter("open", "Open"),
                    DashboardFilter("blocked", "Blocked"),
                    DashboardFilter("closed", "Closed"),
                ),
            selectedFilterId = "open",
            onFilterSelected = {},
        )
    }
}
