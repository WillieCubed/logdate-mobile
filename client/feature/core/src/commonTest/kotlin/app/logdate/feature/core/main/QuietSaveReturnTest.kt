package app.logdate.feature.core.main

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class QuietSaveReturnTest {
    @Test
    fun `acknowledgement is consumed when shown even if the visit ends early`() =
        runTest {
            val host = SnackbarHostState()
            var consumed = 0
            val visit = launch { showQuietSaveReturn(host, "Saved", "Open draft") { consumed++ } }
            runCurrent()
            assertNotNull(host.currentSnackbarData)
            assertEquals(1, consumed)
            visit.cancel()
            runCurrent()
            assertEquals(1, consumed)
        }

    @Test
    fun `consumption leaves the draft action available`() =
        runTest {
            val host = SnackbarHostState()
            var consumed = 0
            var result: SnackbarResult? = null
            launch { result = showQuietSaveReturn(host, "Saved", "Open draft") { consumed++ } }
            runCurrent()
            assertEquals(1, consumed)
            host.currentSnackbarData!!.performAction()
            runCurrent()
            assertEquals(SnackbarResult.ActionPerformed, result)
            assertEquals(1, consumed)
        }

    @Test
    fun `a queued message is not consumed before presentation`() =
        runTest {
            val host = SnackbarHostState()
            val other = launch { host.showSnackbar("Another message") }
            runCurrent()
            var consumed = 0
            val queued = launch { showQuietSaveReturn(host, "Saved", "Open draft") { consumed++ } }
            runCurrent()
            assertEquals(0, consumed)
            queued.cancel()
            other.cancel()
        }
}
