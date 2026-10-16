package app.logdate.client.data.account

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativeRestoreAccountPolicyTest {
    private val owner = Uuid.random().toString()

    @Test
    fun `a bound transferred journal accepts only its existing account`() {
        val policy = NativeRestoreAccountPolicy(owner, "true", hasLocalData = true)
        assertTrue(policy.canAttempt)
        assertTrue(policy.accepts(owner))
        assertFalse(policy.accepts(Uuid.random().toString()))
    }

    @Test
    fun `an empty installation can recover credentials without backed up app data`() {
        val policy = NativeRestoreAccountPolicy(null, null, hasLocalData = false)
        assertTrue(policy.canAttempt)
        assertTrue(policy.accepts(owner))
        assertFalse(policy.accepts("corrupt-account"))
    }

    @Test
    fun `offline drafts and unreadable ownership defer to foreground consent`() {
        assertFalse(NativeRestoreAccountPolicy(owner, null, true).canAttempt)
        assertFalse(NativeRestoreAccountPolicy(null, null, true).canAttempt)
        assertFalse(NativeRestoreAccountPolicy("corrupt-owner", "true", false).canAttempt)
        assertFalse(NativeRestoreAccountPolicy(null, "true", false).canAttempt)
        assertFalse(NativeRestoreAccountPolicy(owner, "corrupt-binding", false).canAttempt)
    }
}
