package app.logdate.feature.postcards.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.dao.PostcardDao
import app.logdate.feature.postcards.model.PostcardDocument
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * UI state for the Postcard viewer.
 */
sealed interface PostcardViewerUiState {
    data object Loading : PostcardViewerUiState

    data class Loaded(
        val document: PostcardDocument,
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
) : ViewModel() {
    private val postcardId: Uuid =
        savedStateHandle
            .get<String>("postcardId")
            ?.let { Uuid.parse(it) }
            ?: error("postcardId is required")

    private val json = Json { ignoreUnknownKeys = true }

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
                        PostcardViewerUiState.Loaded(document)
                    } catch (e: Exception) {
                        PostcardViewerUiState.Error("Failed to parse Postcard: ${e.message}")
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PostcardViewerUiState.Loading,
            )
}
