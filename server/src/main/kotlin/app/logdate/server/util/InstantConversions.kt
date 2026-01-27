package app.logdate.server.util

import kotlinx.datetime.Instant as KxInstant
import kotlin.time.Instant as KtInstant

internal fun KtInstant.toKotlinxInstant(): KxInstant =
    KxInstant.fromEpochMilliseconds(toEpochMilliseconds())

internal fun KxInstant.toKotlinxInstant(): KxInstant = this

internal fun KxInstant.toKotlinInstant(): KtInstant =
    KtInstant.fromEpochMilliseconds(toEpochMilliseconds())
