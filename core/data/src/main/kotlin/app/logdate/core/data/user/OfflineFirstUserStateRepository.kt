package app.logdate.core.data.user

import app.logdate.core.datastore.LogdatePreferencesDataSource
import app.logdate.core.datastore.model.UserData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OfflineFirstUserStateRepository @Inject constructor(
    private val localDataSource: LogdatePreferencesDataSource,
) : UserStateRepository {
    override val userData: Flow<UserData>
        get() = localDataSource.userData

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {
        localDataSource.setShouldHideOnboarding(isComplete)
    }

    override suspend fun addFavoriteNote(vararg noteId: String) {
        TODO("Not yet implemented")
    }
}