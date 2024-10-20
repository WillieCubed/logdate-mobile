package app.logdate.core.data.user

import app.logdate.core.datastore.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface UserStateRepository {
    val userData: Flow<UserData>

    suspend fun setBirthday(birthday: Instant)

    suspend fun setIsOnboardingComplete(isComplete: Boolean)

    suspend fun setBiometricEnabled(isEnabled: Boolean)

    suspend fun addFavoriteNote(vararg noteId: String)

}