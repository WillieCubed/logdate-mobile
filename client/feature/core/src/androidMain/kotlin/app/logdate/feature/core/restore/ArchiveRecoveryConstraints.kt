package app.logdate.feature.core.restore

import androidx.work.Constraints

internal val archiveRecoveryConstraints: Constraints = Constraints.Builder().setRequiresStorageNotLow(true).build()
