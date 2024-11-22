package app.logdate.core.data.rewind

import app.logdate.model.Rewind
import jakarta.inject.Inject

class DefaultRewindGenerator @Inject constructor() : RewindGenerator {
    override suspend fun generateRewind(): Rewind {
        TODO("Not yet implemented")
    }
}