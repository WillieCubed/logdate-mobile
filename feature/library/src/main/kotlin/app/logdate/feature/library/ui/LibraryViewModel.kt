package app.logdate.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.LibraryContentRepository
import app.logdate.feature.library.ui.LibraryUiState.Success
import app.logdate.model.LibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: LibraryContentRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = repository
        .allItemsObserved
        .map<List<LibraryItem>, LibraryUiState> { items ->
            Success(
                items
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LibraryUiState.Loading
        )
}
