package app.logdate.ui.navigation

import androidx.navigation3.runtime.NavEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityPresentationTest {
    @Test
    fun unregisteredEntriesOwnTheirActivity() {
        assertEquals(ActivityPresentation.Replacement, NavEntry("key") {}.activityPresentation())
    }

    @Test
    fun registrationsPreserveTheirDeclaredContext() {
        val detail = ActivityPresentation.Detail("journals", childContext = "journal")
        val entry = NavEntry("key", metadata = detail.asMetadata()) {}
        assertEquals(detail, entry.activityPresentation())
    }
}
