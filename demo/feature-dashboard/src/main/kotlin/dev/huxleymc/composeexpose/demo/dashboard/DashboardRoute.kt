package dev.huxleymc.composeexpose.demo.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Entry point for the dashboard feature.
 */
@Composable
fun DashboardRoute(
    userName: String,
    unreadCount: Int = 0,
) {
    DashboardScreen(
        title = "Welcome, $userName",
        metrics = listOf(
            DashboardMetric("Open tasks", unreadCount.toString()),
            DashboardMetric("Build health", "98%"),
            DashboardMetric("Reusable composables", "24"),
        ),
    )
}

@Preview(name = "Dashboard route", group = "dashboard")
@Composable
private fun DashboardRoutePreview() {
    DashboardRoute(userName = "Huxley", unreadCount = 7)
}
