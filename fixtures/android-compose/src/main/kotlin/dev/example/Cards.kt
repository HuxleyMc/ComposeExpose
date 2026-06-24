package dev.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Phone", group = "device", widthDp = 390, heightDp = 844)
@Preview(name = "Tablet", group = "device", widthDp = 840, heightDp = 1180)
annotation class DevicePreviews

/**
 * Shows the primary account card.
 *
 * Supports compact and expanded states.
 */
@DevicePreviews
@Composable
internal fun AccountCard(
    title: String,
    balance: Long = 0L,
    onClick: () -> Unit,
) {
}

@Composable
private fun NoDocChip(selected: Boolean) {
}
