package app.logdate.client.data.fakes

import app.logdate.client.database.dao.MediaCaptionDao
import app.logdate.client.database.entities.MediaCaptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

class FakeMediaCaptionDao : MediaCaptionDao {
    private val captions = mutableMapOf<Uuid, MediaCaptionEntity>()
    private val captionsFlow = MutableStateFlow<List<MediaCaptionEntity>>(emptyList())

    override fun observeAll(): Flow<List<MediaCaptionEntity>> = captionsFlow

    override suspend fun getCaption(noteId: Uuid): MediaCaptionEntity? = captions[noteId]

    override suspend fun upsertCaption(entity: MediaCaptionEntity) {
        captions[entity.noteId] = entity
        updateFlow()
    }

    override suspend fun deleteCaption(noteId: Uuid) {
        captions.remove(noteId)
        updateFlow()
    }

    fun clear() {
        captions.clear()
        updateFlow()
    }

    private fun updateFlow() {
        captionsFlow.value = captions.values.toList()
    }
}
