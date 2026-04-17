package app.logdate.client.repository.knowledge

import app.logdate.shared.model.PersonLink
import app.logdate.shared.model.PersonLinkTargetType
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface PersonLinkRepository {
    fun observeLinksForPerson(personId: Uuid): Flow<List<PersonLink>>

    fun observeLinksForTarget(
        targetType: PersonLinkTargetType,
        targetId: Uuid,
    ): Flow<List<PersonLink>>
}
