package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * ViewModel for the memory selection screen during onboarding.
 */
class MemorySelectionViewModel(
    private val mediaManager: MediaManager,
    private val aiClient: GenerativeAIChatClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemorySelectionUiState())
    val uiState: StateFlow<MemorySelectionUiState> = _uiState.asStateFlow()

    private val selectedMemoryIds = mutableSetOf<String>()
    private var currentPage = 0
    private val pageSize = 20

    init {
        loadInitialMemories()
    }

    /**
     * Loads initial memories and AI-curated content.
     */
    private fun loadInitialMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Load recent memories (last 6 months)
                val sixMonthsAgo = Clock.System.now().minus(6, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
                val recentMemories = mediaManager.queryMediaByDate(
                    start = sixMonthsAgo,
                    end = Clock.System.now()
                ).first()

                // Load first page of all memories
                val initialMemories = recentMemories.take(pageSize)
                
                // Get AI-curated memories with high emotional salience
                val aiCuratedMemories = getCuratedMemories(recentMemories)

                _uiState.update {
                    it.copy(
                        allMemories = initialMemories,
                        aiCuratedMemories = aiCuratedMemories,
                        isLoading = false,
                        hasMoreMemories = recentMemories.size > pageSize
                    )
                }
                
                currentPage = 1
                
                Napier.d(
                    tag = "MemorySelectionViewModel",
                    message = "Loaded ${initialMemories.size} memories, ${aiCuratedMemories.size} AI-curated"
                )
            } catch (e: Exception) {
                Napier.e(
                    tag = "MemorySelectionViewModel",
                    message = "Failed to load memories",
                    throwable = e
                )
                _uiState.update {
                    it.copy(isLoading = false, hasMoreMemories = false)
                }
            }
        }
    }

    /**
     * Uses AI to identify memories with high emotional salience.
     */
    private suspend fun getCuratedMemories(allMemories: List<MediaObject>): List<MediaObject> {
        return try {
            // For now, use a simple heuristic - in real implementation would use AI analysis
            // Select diverse content types and recent important-looking memories
            val candidates = allMemories.take(20) // Analyze first 20 for performance
            
            // Simple heuristic: prefer larger files (likely higher quality) and diverse types
            val images = candidates.filterIsInstance<MediaObject.Image>()
                .sortedByDescending { it.size }
                .take(4)
            
            val videos = candidates.filterIsInstance<MediaObject.Video>()
                .sortedByDescending { it.duration }
                .take(2)
            
            (images + videos).shuffled().take(6)
        } catch (e: Exception) {
            Napier.e(
                tag = "MemorySelectionViewModel",
                message = "Failed to get AI-curated memories",
                throwable = e
            )
            // Fallback to simple selection
            allMemories.take(6)
        }
    }

    /**
     * Loads more memories for infinite scroll.
     */
    fun loadMoreMemories() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMemories) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            try {
                val sixMonthsAgo = Clock.System.now().minus(6, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
                val allRecentMemories = mediaManager.queryMediaByDate(
                    start = sixMonthsAgo,
                    end = Clock.System.now()
                ).first()

                val startIndex = currentPage * pageSize
                val endIndex = (startIndex + pageSize).coerceAtMost(allRecentMemories.size)
                
                if (startIndex < allRecentMemories.size) {
                    val newMemories = allRecentMemories.subList(startIndex, endIndex)
                    
                    _uiState.update {
                        it.copy(
                            allMemories = it.allMemories + newMemories,
                            isLoadingMore = false,
                            hasMoreMemories = endIndex < allRecentMemories.size
                        )
                    }
                    
                    currentPage++
                    
                    Napier.d(
                        tag = "MemorySelectionViewModel",
                        message = "Loaded ${newMemories.size} more memories, page $currentPage"
                    )
                } else {
                    _uiState.update {
                        it.copy(isLoadingMore = false, hasMoreMemories = false)
                    }
                }
            } catch (e: Exception) {
                Napier.e(
                    tag = "MemorySelectionViewModel",
                    message = "Failed to load more memories",
                    throwable = e
                )
                _uiState.update {
                    it.copy(isLoadingMore = false)
                }
            }
        }
    }

    /**
     * Toggles selection state of a memory.
     */
    fun toggleMemorySelection(memoryUri: String) {
        if (selectedMemoryIds.contains(memoryUri)) {
            selectedMemoryIds.remove(memoryUri)
        } else {
            selectedMemoryIds.add(memoryUri)
        }
        
        _uiState.update {
            it.copy(selectedMemoryIds = selectedMemoryIds.toSet())
        }
        
        Napier.d(
            tag = "MemorySelectionViewModel",
            message = "Toggled selection for $memoryUri, now have ${selectedMemoryIds.size} selected"
        )
    }

    /**
     * Gets the currently selected memories.
     */
    fun getSelectedMemories(): List<MediaObject> {
        val currentState = _uiState.value
        val allMemories = currentState.allMemories + currentState.aiCuratedMemories
        return allMemories.filter { it.uri in selectedMemoryIds }.distinctBy { it.uri }
    }

    /**
     * Processes the selected memories for import.
     */
    fun processSelectedMemories() {
        viewModelScope.launch {
            val selectedMemories = getSelectedMemories()
            
            Napier.i(
                tag = "MemorySelectionViewModel",
                message = "Processing ${selectedMemories.size} selected memories for import"
            )
            
            try {
                // Add selected memories to the default collection
                selectedMemories.forEach { memory ->
                    mediaManager.addToDefaultCollection(memory.uri)
                }
                
                Napier.i(
                    tag = "MemorySelectionViewModel",
                    message = "Successfully imported ${selectedMemories.size} memories"
                )
            } catch (e: Exception) {
                Napier.e(
                    tag = "MemorySelectionViewModel",
                    message = "Failed to import selected memories",
                    throwable = e
                )
            }
        }
    }
}