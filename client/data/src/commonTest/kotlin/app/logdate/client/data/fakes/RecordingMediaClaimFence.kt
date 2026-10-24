package app.logdate.client.data.fakes

import app.logdate.client.media.storage.MediaClaimFence

class RecordingMediaClaimFence : MediaClaimFence {
    var claims: Int = 0
        private set

    override suspend fun <T> withClaim(block: suspend () -> T): T {
        claims++
        return block()
    }

    override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = error("The test fence does not support reclamation")
}
