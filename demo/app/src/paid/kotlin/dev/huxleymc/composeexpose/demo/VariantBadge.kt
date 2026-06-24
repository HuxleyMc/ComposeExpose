package dev.huxleymc.composeexpose.demo

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Labels UI that is only available in the paid demo flavor.
 */
@Composable
fun VariantBadge(label: String = "Paid") {
    Text(text = "Paid: $label")
}

@Preview(name = "Paid variant badge", group = "variants")
@Composable
private fun VariantBadgePreview() {
    VariantBadge()
}
