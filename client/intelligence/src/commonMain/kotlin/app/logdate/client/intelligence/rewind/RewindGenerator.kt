package app.logdate.client.intelligence.rewind

import app.logdate.shared.model.Rewind

/**
 * A generator for creating new [Rewind]s.
 */
interface RewindGenerator {
    suspend fun generateRewind(): Rewind
}