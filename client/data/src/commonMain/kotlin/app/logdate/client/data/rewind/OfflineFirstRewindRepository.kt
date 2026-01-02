package app.logdate.client.data.rewind

import app.logdate.client.database.dao.rewind.CachedRewindDao
import app.logdate.client.database.entities.rewind.RewindEntity
import app.logdate.client.database.entities.rewind.RewindImageContentEntity
import app.logdate.client.database.entities.rewind.RewindTextContentEntity
import app.logdate.client.database.entities.rewind.RewindVideoContentEntity
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * A [RewindRepository] that uses a local database as the single source of truth.
 * 
 * This implementation handles the storage and retrieval of rewinds, providing
 * a consistent interface for the rest of the application. It uses a Room DAO
 * for persistence and handles the mapping between database entities and domain models.
 */
class OfflineFirstRewindRepository(
    private val cachedRewindDao: CachedRewindDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RewindRepository {

    private val contentJson = Json { ignoreUnknownKeys = true }
    
    /**
     * Retrieves all rewinds that have been generated.
     * 
     * @return Flow emitting a list of all available rewinds
     */
    override fun getAllRewinds(): Flow<List<Rewind>> {
        return cachedRewindDao.getAllRewinds()
            .map { rewindEntities ->
                rewindEntities.map { it.toDomainModel() }
            }
    }

    /**
     * Retrieves a rewind by its unique identifier.
     * 
     * @param uid The unique identifier of the rewind to retrieve
     * @return Flow emitting the rewind if found
     */
    override fun getRewind(uid: Uuid): Flow<Rewind> {
        return cachedRewindDao.getRewindById(uid)
            .map { rewindEntity -> 
                rewindEntity.toDomainModel() 
            }
    }

    /**
     * Retrieves a rewind for a given time period.
     * 
     * @param start The start of the time period
     * @param end The end of the time period
     * @return Flow emitting the rewind for the given period, or null if none exists
     */
    override fun getRewindBetween(
        start: Instant,
        end: Instant,
    ): Flow<Rewind?> {
        return cachedRewindDao.getRewindForPeriod(start, end)
            .map { rewindEntity -> 
                rewindEntity?.toDomainModel() 
            }
    }

    /**
     * Checks if a rewind is available for the given time period.
     * 
     * @param start The start of the time period
     * @param end The end of the time period
     * @return True if a rewind exists for the period, false otherwise
     */
    override suspend fun isRewindAvailable(
        start: Instant,
        end: Instant,
    ): Boolean = withContext(ioDispatcher) {
        cachedRewindDao.rewindExistsForPeriod(start, end)
    }

    /**
     * Creates a new rewind for the given time period.
     * 
     * This method is deprecated and will be removed in a future version.
     * Use the GenerateBasicRewindUseCase instead, which handles the entire
     * rewind generation process.
     * 
     * @param start The start of the time period
     * @param end The end of the time period
     * @return The created rewind
     */
    @Deprecated("Use GenerateBasicRewindUseCase instead")
    override suspend fun createRewind(start: Instant, end: Instant): Rewind {
        throw UnsupportedOperationException(
            "Direct rewind creation is no longer supported. Use GenerateBasicRewindUseCase instead."
        )
    }
    
    /**
     * Saves a rewind to the repository.
     * 
     * @param rewind The rewind to save
     */
    override suspend fun saveRewind(rewind: Rewind): Unit = withContext(ioDispatcher) {
        try {
            val rewindEntity = rewind.toEntity()
            cachedRewindDao.insertRewind(rewindEntity)
            
            // Separate and convert content by type
            val (textEntities, imageEntities, videoEntities) = rewind.content.toEntities(rewind.uid)
            
            // Save content by type
            cachedRewindDao.insertRewindContent(
                textContent = textEntities,
                imageContent = imageEntities,
                videoContent = videoEntities
            )
            
            Napier.d("Saved rewind: ${rewind.uid} with ${rewind.content.size} content items " +
                    "(${textEntities.size} text, ${imageEntities.size} images, ${videoEntities.size} videos)")
        } catch (e: Exception) {
            Napier.e("Failed to save rewind", e)
            throw e
        }
    }
    
    /**
     * Converts a RewindEntity to a domain Rewind model.
     */
    private suspend fun RewindEntity.toDomainModel(): Rewind {
        val dbContent = cachedRewindDao.getContentForRewind(uid)
        val content = mutableListOf<RewindContent>()
        
        // Convert text content
        dbContent.textContent.map { it.toDomainModel() }.let { content.addAll(it) }
        
        // Convert image content
        dbContent.imageContent.map { it.toDomainModel() }.let { content.addAll(it) }
        
        // Convert video content
        dbContent.videoContent.map { it.toDomainModel() }.let { content.addAll(it) }
        
        // Sort by timestamp to maintain chronological order
        val sortedContent = content.sortedBy { it.timestamp }
        
        return Rewind(
            uid = uid,
            startDate = startDate,
            endDate = endDate,
            generationDate = generationDate,
            label = label,
            title = title,
            content = sortedContent
        )
    }
    
    /**
     * Converts a domain Rewind model to a RewindEntity.
     */
    private fun Rewind.toEntity(): RewindEntity {
        return RewindEntity(
            uid = uid,
            startDate = startDate,
            endDate = endDate,
            generationDate = generationDate,
            label = label,
            title = title
        )
    }
    
    /**
     * Converts a RewindTextContentEntity to a domain RewindContent entry.
     */
    private fun RewindTextContentEntity.toDomainModel(): RewindContent {
        return when {
            content.startsWith(NARRATIVE_PREFIX) -> {
                runCatching {
                    contentJson.decodeFromString<NarrativeContextPayload>(
                        content.removePrefix(NARRATIVE_PREFIX)
                    )
                }.map { payload ->
                    RewindContent.NarrativeContext(
                        timestamp = timestamp,
                        sourceId = sourceId,
                        contextText = payload.contextText,
                        backgroundImage = payload.backgroundImage
                    )
                }.getOrElse {
                    Napier.w("Failed to decode narrative context payload for rewind text content", it)
                    RewindContent.TextNote(
                        timestamp = timestamp,
                        sourceId = sourceId,
                        content = content
                    )
                }
            }
            content.startsWith(TRANSITION_PREFIX) -> {
                runCatching {
                    contentJson.decodeFromString<TransitionPayload>(
                        content.removePrefix(TRANSITION_PREFIX)
                    )
                }.map { payload ->
                    RewindContent.Transition(
                        timestamp = timestamp,
                        sourceId = sourceId,
                        transitionText = payload.transitionText
                    )
                }.getOrElse {
                    Napier.w("Failed to decode transition payload for rewind text content", it)
                    RewindContent.TextNote(
                        timestamp = timestamp,
                        sourceId = sourceId,
                        content = content
                    )
                }
            }
            else -> RewindContent.TextNote(
                timestamp = timestamp,
                sourceId = sourceId,
                content = content
            )
        }
    }
    
    /**
     * Converts a RewindImageContentEntity to a domain Image.
     */
    private fun RewindImageContentEntity.toDomainModel(): RewindContent.Image {
        return RewindContent.Image(
            timestamp = timestamp,
            sourceId = sourceId,
            uri = uri,
            caption = caption
        )
    }
    
    /**
     * Converts a RewindVideoContentEntity to a domain Video.
     */
    private fun RewindVideoContentEntity.toDomainModel(): RewindContent.Video {
        return RewindContent.Video(
            timestamp = timestamp,
            sourceId = sourceId,
            uri = uri,
            caption = caption,
            duration = Duration.parse(duration)
        )
    }
    
    /**
     * Separates RewindContent items by type and converts to appropriate entities.
     */
    private fun List<RewindContent>.toEntities(rewindId: Uuid): Triple<
        List<RewindTextContentEntity>,
        List<RewindImageContentEntity>,
        List<RewindVideoContentEntity>
    > {
        val textEntities = mutableListOf<RewindTextContentEntity>()
        val imageEntities = mutableListOf<RewindImageContentEntity>()
        val videoEntities = mutableListOf<RewindVideoContentEntity>()
        
        forEach { content ->
            when (content) {
                is RewindContent.TextNote -> textEntities.add(content.toTextEntity(rewindId))
                is RewindContent.Image -> imageEntities.add(content.toImageEntity(rewindId))
                is RewindContent.Video -> videoEntities.add(content.toVideoEntity(rewindId))
                is RewindContent.NarrativeContext -> textEntities.add(content.toNarrativeEntity(rewindId))
                is RewindContent.Transition -> textEntities.add(content.toTransitionEntity(rewindId))
            }
        }
        
        return Triple(textEntities, imageEntities, videoEntities)
    }
    
    /**
     * Converts a TextNote to a RewindTextContentEntity.
     */
    private fun RewindContent.TextNote.toTextEntity(rewindId: Uuid): RewindTextContentEntity {
        return RewindTextContentEntity(
            id = sourceId,
            rewindId = rewindId,
            sourceId = sourceId,
            timestamp = timestamp,
            content = content
        )
    }

    private fun RewindContent.NarrativeContext.toNarrativeEntity(rewindId: Uuid): RewindTextContentEntity {
        val payload = NarrativeContextPayload(contextText = contextText, backgroundImage = backgroundImage)
        return RewindTextContentEntity(
            id = sourceId,
            rewindId = rewindId,
            sourceId = sourceId,
            timestamp = timestamp,
            content = NARRATIVE_PREFIX + contentJson.encodeToString(payload)
        )
    }

    private fun RewindContent.Transition.toTransitionEntity(rewindId: Uuid): RewindTextContentEntity {
        val payload = TransitionPayload(transitionText = transitionText)
        return RewindTextContentEntity(
            id = sourceId,
            rewindId = rewindId,
            sourceId = sourceId,
            timestamp = timestamp,
            content = TRANSITION_PREFIX + contentJson.encodeToString(payload)
        )
    }
    
    /**
     * Converts an Image to a RewindImageContentEntity.
     */
    private fun RewindContent.Image.toImageEntity(rewindId: Uuid): RewindImageContentEntity {
        return RewindImageContentEntity(
            id = sourceId,
            rewindId = rewindId,
            sourceId = sourceId,
            timestamp = timestamp,
            uri = uri,
            caption = caption
        )
    }
    
    /**
     * Converts a Video to a RewindVideoContentEntity.
     */
    private fun RewindContent.Video.toVideoEntity(rewindId: Uuid): RewindVideoContentEntity {
        return RewindVideoContentEntity(
            id = sourceId,
            rewindId = rewindId,
            sourceId = sourceId,
            timestamp = timestamp,
            uri = uri,
            caption = caption,
            duration = duration.toString()
        )
    }

    @Serializable
    private data class NarrativeContextPayload(
        val contextText: String,
        val backgroundImage: String?
    )

    @Serializable
    private data class TransitionPayload(
        val transitionText: String
    )

    private companion object {
        private const val NARRATIVE_PREFIX = "LD_NARRATIVE:"
        private const val TRANSITION_PREFIX = "LD_TRANSITION:"
    }
}
