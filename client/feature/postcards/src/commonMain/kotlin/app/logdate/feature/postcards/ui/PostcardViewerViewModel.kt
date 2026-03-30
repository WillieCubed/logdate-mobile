package app.logdate.feature.postcards.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.dao.PostcardDao
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.stickers.ui.StickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * UI state for the Postcard viewer.
 */
sealed interface PostcardViewerUiState {
    data object Loading : PostcardViewerUiState

    data class Loaded(
        val document: PostcardDocument,
        val stickerUriMap: Map<Uuid, String> = emptyMap(),
    ) : PostcardViewerUiState

    data class Error(
        val message: String,
    ) : PostcardViewerUiState
}

/**
 * ViewModel for the Postcard viewer screen.
 *
 * Loads a [PostcardDocument] from the database by ID and exposes it as
 * observable UI state.
 */
class PostcardViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val postcardDao: PostcardDao,
    private val stickerRepository: StickerRepository,
) : ViewModel() {
    private val postcardId: Uuid =
        savedStateHandle
            .get<String>("postcardId")
            ?.let { Uuid.parse(it) }
            ?: error("postcardId is required")

    private val json = Json { ignoreUnknownKeys = true }

    private val resolvedStickerUris = MutableStateFlow<Map<Uuid, String>>(emptyMap())

    val uiState: StateFlow<PostcardViewerUiState> =
        postcardDao
            .getPostcard(postcardId)
            .map { entity ->
                if (entity == null) {
                    PostcardViewerUiState.Error("Postcard not found")
                } else {
                    try {
                        val document =
                            json.decodeFromString(
                                PostcardDocument.serializer(),
                                entity.documentJson,
                            )
                        resolveStickerUris(document)
                        PostcardViewerUiState.Loaded(document, resolvedStickerUris.value)
                    } catch (e: Exception) {
                        PostcardViewerUiState.Error("Failed to parse Postcard: ${e.message}")
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PostcardViewerUiState.Loading,
            )

    private fun resolveStickerUris(document: PostcardDocument) {
        val stickerRefs =
            document.elements
                .filterIsInstance<CanvasElement.Sticker>()
                .map { it.stickerRef }
        if (stickerRefs.isEmpty()) return

        viewModelScope.launch {
            val uriMap = mutableMapOf<Uuid, String>()
            for (ref in stickerRefs) {
                val entity = stickerRepository.getSticker(ref)
                if (entity != null) {
                    uriMap[ref] = entity.imageUri
                }
            }
            resolvedStickerUris.value = uriMap
        }
    }
}
