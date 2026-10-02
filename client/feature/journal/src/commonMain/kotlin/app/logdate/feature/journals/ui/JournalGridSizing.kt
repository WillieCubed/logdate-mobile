package app.logdate.feature.journals.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun journalGridMinimumCoverWidth(panelWidth: Dp): Dp = (panelWidth * .23f).coerceIn(132.dp, 260.dp)
