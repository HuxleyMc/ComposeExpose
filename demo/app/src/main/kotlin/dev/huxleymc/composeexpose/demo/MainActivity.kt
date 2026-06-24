package dev.huxleymc.composeexpose.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.huxleymc.composeexpose.demo.dashboard.DashboardRoute
import dev.huxleymc.composeexpose.demo.design.DemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoApp()
        }
    }
}

/**
 * Root app composable that wires the demo theme to the dashboard route.
 */
@Composable
fun DemoApp() {
    DemoTheme {
        DashboardRoute(
            userName = "Huxley",
            unreadCount = 7,
        )
    }
}

@Preview(name = "Demo app", group = "app")
@Composable
private fun DemoAppPreview() {
    DemoApp()
}
