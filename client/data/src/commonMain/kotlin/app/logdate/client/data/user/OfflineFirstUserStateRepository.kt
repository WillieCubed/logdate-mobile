package app.logdate.client.data.user

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class OfflineFirstUserStateRepository(
    private val localDataSource: LogdatePreferencesDataSource,
) : UserStateRepository {
    override val userData: Flow<UserData>
        get() = localDataSource.userData

    override suspend fun setBirthday(birthday: Instant) {
        localDataSource.setBirthdate(birthday)
    }

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        localDataSource.setShouldHideOnboarding(isComplete)
    }

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {
        localDataSource.setShouldShowBiometric(isEnabled)
    }

    override suspend fun addFavoriteNote(vararg noteId: String) {
        TODO("Not yet implemented")
    }
}