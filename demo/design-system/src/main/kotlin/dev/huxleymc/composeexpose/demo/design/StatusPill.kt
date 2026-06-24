package dev.huxleymc.composeexpose.demo.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class StatusTone {
    Success,
    Warning,
    Critical,
}

/**
 * Renders a compact health or status indicator using semantic success, warning, and critical tones.
 */
@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor: Color =
        when (tone) {
            StatusTone.Success -> colorScheme.primaryContainer
            StatusTone.Warning -> colorScheme.tertiaryContainer
            StatusTone.Critical -> colorScheme.errorContainer
        }
    val contentColor: Color =
        when (tone) {
            StatusTone.Success -> colorScheme.onPrimaryContainer
            StatusTone.Warning -> colorScheme.onTertiaryContainer
            StatusTone.Critical -> colorScheme.onErrorContainer
        }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@DesignSystemPreview
@Composable
private fun StatusPillPreview() {
    DemoTheme {
        StatusPill(label = "Healthy", tone = StatusTone.Success)
    }
}
