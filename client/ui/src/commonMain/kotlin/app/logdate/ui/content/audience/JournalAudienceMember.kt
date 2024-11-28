package app.logdate.ui.content.audience

import app.logdate.shared.model.Audience
import kotlinx.datetime.Instant

data class JournalAudienceMember(
    val member: Audience,
    val dateAdded: Instant,
)