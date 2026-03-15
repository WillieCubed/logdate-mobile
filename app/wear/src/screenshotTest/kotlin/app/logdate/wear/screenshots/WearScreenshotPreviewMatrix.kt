package app.logdate.wear.screenshots

import androidx.compose.ui.tooling.preview.Preview

/**
 * Standard Wear OS preview matrix covering both watch sizes.
 *
 * Generates 2 baseline variants per preview:
 * 1. Small round watch (192dp, typical 1.3" display)
 * 2. Large round watch (227dp, typical 1.5" display)
 */
@Preview(
    name = "Small Round",
    device = "id:wearos_small_round",
    showBackground = true,
)
@Preview(
    name = "Large Round",
    device = "id:wearos_large_round",
    showBackground = true,
)
annotation class WearScreenshotPreviewMatrix
