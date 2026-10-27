package app.logdate.client.data.fakes

import app.logdate.client.media.storage.MediaClaimFence

class RecordingMediaClaimFence : MediaClaimFence {
    var claims: Int = 0
        private set

    var isInsideClaim: Boolean = false
        private set

    override suspend fun <T> withClaim(block: suspend () -> T): T {
        claims++
        check(!isInsideClaim) { "Media claim fence cannot be entered recursively in this test" }
        isInsideClaim = true
        return try {
            block()
        } finally {
            isInsideClaim = false
        }
    }

    override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = error("The test fence does not support reclamation")
}
