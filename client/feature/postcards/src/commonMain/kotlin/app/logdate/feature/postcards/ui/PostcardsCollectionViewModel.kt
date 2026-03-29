package app.logdate.feature.postcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.dao.PostcardDao
import app.logdate.client.database.entities.PostcardEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Postcards collection screen.
 *
 * Provides an observable list of all saved Postcards, sorted by most recently modified.
 */
class PostcardsCollectionViewModel(
    postcardDao: PostcardDao,
) : ViewModel() {
    val postcards: StateFlow<List<PostcardEntity>> =
        postcardDao
            .getAllPostcards()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}
