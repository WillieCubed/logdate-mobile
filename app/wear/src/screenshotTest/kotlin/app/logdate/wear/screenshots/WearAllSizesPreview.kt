package app.logdate.wear.screenshots

import androidx.compose.ui.tooling.preview.Preview

/** A small round watch, a large round watch, and a small round watch at the largest font scale checked, 1.24. */
@Preview(name = "Small Round", device = "id:wearos_small_round", showBackground = true)
@Preview(name = "Large Round", device = "id:wearos_large_round", showBackground = true)
@Preview(name = "Large Font", device = "id:wearos_small_round", showBackground = true, fontScale = 1.24f)
annotation class WearAllSizesPreview
