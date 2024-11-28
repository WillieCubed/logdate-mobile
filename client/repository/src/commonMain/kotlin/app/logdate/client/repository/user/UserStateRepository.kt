package app.logdate.client.repository.user

import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface UserStateRepository {
    val userData: Flow<UserData>

    suspend fun setBirthday(birthday: Instant)

    suspend fun setIsOnboardingComplete(isComplete: Boolean)

    suspend fun setBiometricEnabled(isEnabled: Boolean)

    suspend fun addFavoriteNote(vararg noteId: String)

}