package app.logdate.server.util

import kotlin.time.Instant as KtInstant
import kotlinx.datetime.Instant as KxInstant

internal fun KtInstant.toKotlinxInstant(): KxInstant = KxInstant.fromEpochMilliseconds(toEpochMilliseconds())

internal fun KxInstant.toKotlinInstant(): KtInstant = KtInstant.fromEpochMilliseconds(toEpochMilliseconds())
