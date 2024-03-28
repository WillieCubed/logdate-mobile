package app.logdate.core.data.user

import app.logdate.core.datastore.model.UserData
import kotlinx.coroutines.flow.Flow

interface UserStateRepository {
    val userData: Flow<UserData>

    suspend fun setIsOnboardingComplete(isComplete: Boolean)

    suspend fun addFavoriteNote(vararg noteId: String)
}