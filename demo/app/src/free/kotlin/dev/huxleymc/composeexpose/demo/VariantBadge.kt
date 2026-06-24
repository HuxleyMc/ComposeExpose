package dev.huxleymc.composeexpose.demo

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Labels UI that is only available in the free demo flavor.
 */
@Composable
fun VariantBadge(label: String = "Free") {
    Text(text = "Free: $label")
}

@Preview(name = "Free variant badge", group = "variants")
@Composable
private fun VariantBadgePreview() {
    VariantBadge()
}
