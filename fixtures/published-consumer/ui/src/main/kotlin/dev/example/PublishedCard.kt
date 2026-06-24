package dev.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Reusable card in the published-consumer fixture.
 */
@Composable
fun PublishedCard(title: String) {
}

@Preview(name = "Published card", group = "fixture")
@Composable
private fun PublishedCardPreview() {
    PublishedCard(title = "Published")
}
