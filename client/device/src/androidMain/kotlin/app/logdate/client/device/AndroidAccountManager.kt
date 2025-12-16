package app.logdate.client.device

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation for managing LogDate Cloud accounts in the system AccountManager
 */
class AndroidAccountManager(
    context: Context
) : PlatformAccountManager {
    
    companion object {
        const val ACCOUNT_TYPE = "app.logdate.account"
        const val TOKEN_TYPE_ACCESS = "access_token"
        const val TOKEN_TYPE_REFRESH = "refresh_token"
        
        // UserData keys
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_BIO = "bio"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_UPDATED_AT = "updated_at"
    }
    
    private val accountManager = AccountManager.get(context)
    
    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val systemAccount = Account(account.username, ACCOUNT_TYPE)
            
            // Prepare user data
            val userData = Bundle().apply {
                putString(KEY_USER_ID, account.id.toString())
                putString(KEY_DISPLAY_NAME, account.displayName)
                putString(KEY_BIO, account.bio)
                putString(KEY_BACKEND_URL, backendUrl)
                putString(KEY_CREATED_AT, account.createdAt.toString())
                putString(KEY_UPDATED_AT, account.updatedAt.toString())
            }
            
            // Add account to system
            val success = accountManager.addAccountExplicitly(systemAccount, null, userData)
            
            if (success) {
                // Store tokens securely
                accountManager.setAuthToken(systemAccount, TOKEN_TYPE_ACCESS, accessToken)
                accountManager.setAuthToken(systemAccount, TOKEN_TYPE_REFRESH, refreshToken)
                
                Napier.i("Successfully added LogDate account to Android AccountManager: ${account.username}")
                Result.success(Unit)
            } else {
                Napier.w("Failed to add account to Android AccountManager")
                Result.failure(Exception("Failed to add account to system"))
            }
            
        } catch (e: Exception) {
            Napier.e("Error adding account to Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val systemAccount = findSystemAccount(account.username)
                ?: return@withContext Result.failure(Exception("Account not found"))
            
            // Update user data
            accountManager.setUserData(systemAccount, KEY_DISPLAY_NAME, account.displayName)
            accountManager.setUserData(systemAccount, KEY_BIO, account.bio)
            accountManager.setUserData(systemAccount, KEY_BACKEND_URL, backendUrl)
            accountManager.setUserData(systemAccount, KEY_UPDATED_AT, account.updatedAt.toString())
            
            Napier.i("Successfully updated LogDate account in Android AccountManager: ${account.username}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Napier.e("Error updating account in Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateTokens(
        username: String,
        accessToken: String,
        refreshToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val systemAccount = findSystemAccount(username)
                ?: return@withContext Result.failure(Exception("Account not found"))
            
            // Update tokens
            accountManager.setAuthToken(systemAccount, TOKEN_TYPE_ACCESS, accessToken)
            accountManager.setAuthToken(systemAccount, TOKEN_TYPE_REFRESH, refreshToken)
            
            Napier.i("Successfully updated tokens for account: $username")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Napier.e("Error updating tokens in Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeAccount(username: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val systemAccount = findSystemAccount(username)
                ?: return@withContext Result.failure(Exception("Account not found"))
            
            // Remove account from system
            @Suppress("DEPRECATION")
            val future = accountManager.removeAccount(systemAccount, null, null)
            val result = future.result
            
            val success = (result as? Bundle)?.getBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) ?: false
            if (success) {
                Napier.i("Successfully removed LogDate account from Android AccountManager: $username")
                Result.success(Unit)
            } else {
                Napier.w("Failed to remove account from Android AccountManager")
                Result.failure(Exception("Failed to remove account"))
            }
            
        } catch (e: Exception) {
            Napier.e("Error removing account from Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            
            val accountInfos = accounts.map { account ->
                PlatformAccountInfo(
                    username = account.name,
                    displayName = accountManager.getUserData(account, KEY_DISPLAY_NAME) ?: account.name,
                    userId = accountManager.getUserData(account, KEY_USER_ID),
                    backendUrl = accountManager.getUserData(account, KEY_BACKEND_URL)
                )
            }
            
            Napier.i("Retrieved ${accountInfos.size} LogDate accounts from Android AccountManager")
            Result.success(accountInfos)
            
        } catch (e: Exception) {
            Napier.e("Error retrieving accounts from Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getTokens(username: String): Result<TokenPair?> = withContext(Dispatchers.IO) {
        try {
            val systemAccount = findSystemAccount(username)
                ?: return@withContext Result.success(null)
            
            val accessToken = accountManager.peekAuthToken(systemAccount, TOKEN_TYPE_ACCESS)
            val refreshToken = accountManager.peekAuthToken(systemAccount, TOKEN_TYPE_REFRESH)
            
            if (accessToken != null && refreshToken != null) {
                Result.success(TokenPair(accessToken, refreshToken))
            } else {
                Result.success(null)
            }
            
        } catch (e: Exception) {
            Napier.e("Error retrieving tokens from Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clearAllTokens(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            
            accounts.forEach { account ->
                accountManager.invalidateAuthToken(ACCOUNT_TYPE, 
                    accountManager.peekAuthToken(account, TOKEN_TYPE_ACCESS))
                accountManager.invalidateAuthToken(ACCOUNT_TYPE, 
                    accountManager.peekAuthToken(account, TOKEN_TYPE_REFRESH))
            }
            
            Napier.i("Successfully cleared all tokens from Android AccountManager")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Napier.e("Error clearing tokens from Android AccountManager", e)
            Result.failure(e)
        }
    }
    
    private fun findSystemAccount(username: String): Account? {
        return accountManager.getAccountsByType(ACCOUNT_TYPE)
            .find { it.name == username }
    }
}