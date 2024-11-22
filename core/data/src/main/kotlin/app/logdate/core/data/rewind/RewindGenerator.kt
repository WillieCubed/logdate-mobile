package app.logdate.core.data.rewind

import app.logdate.model.Rewind

/**
 * A generator for creating new [Rewind]s.
 */
interface RewindGenerator {
    suspend fun generateRewind(): Rewind
}