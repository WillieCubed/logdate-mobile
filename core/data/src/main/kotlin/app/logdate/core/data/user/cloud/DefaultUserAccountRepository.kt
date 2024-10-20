package app.logdate.core.data.user.cloud

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DefaultUserAccountRepository @Inject constructor() : RemoteUserAccountRepository {
    override val currentUser: Flow<UserAccount?>
        get() = TODO("Not yet implemented")

    override suspend fun signInWithEmail(email: String, password: String): Result<UserAccount> {
        TODO("Not yet implemented")
    }

    override suspend fun signInWithPasskey(
        primaryIdentifier: String,
        passkey: String,
    ): Result<UserAccount> {
        TODO("Not yet implemented")
    }

    override suspend fun signOut(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createAccount(
        primaryIdentifier: String,
        password: String,
        displayName: String,
        photoUrl: String?,
    ): Result<UserAccount> {
        TODO("Not yet implemented")
    }

    override suspend fun updateAccount(
        displayName: String?,
        primaryIdentifier: String?,
        photoUrl: String?,
    ): Result<UserAccount> {
        TODO("Not yet implemented")
    }

    override suspend fun createPasskey(primaryIdentifier: String, passkey: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun updatePasskey(
        primaryIdentifier: String,
        oldPasskey: String,
        newPasskey: String,
    ): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun deletePasskey(primaryIdentifier: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAccount(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchAccountDetails(): Result<UserAccount> {
        TODO("Not yet implemented")
    }
}