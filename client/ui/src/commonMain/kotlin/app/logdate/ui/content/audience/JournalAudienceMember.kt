package app.logdate.ui.content.audience

import app.logdate.shared.model.Audience
import kotlin.time.Instant

data class JournalAudienceMember(
    val member: Audience,
    val dateAdded: Instant,
)
