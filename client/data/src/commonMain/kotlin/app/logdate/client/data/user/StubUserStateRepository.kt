package app.logdate.client.data.user

import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

object StubUserStateRepository : UserStateRepository {

    override val userData: Flow<UserData>
        get() = flowOf(UserData())

    override suspend fun setBirthday(birthday: Instant) {
        // no-op
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        // no-op
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {
        // no-op
    }

    override suspend fun addFavoriteNote(vararg noteId: String) {
        // no-op
    }
}