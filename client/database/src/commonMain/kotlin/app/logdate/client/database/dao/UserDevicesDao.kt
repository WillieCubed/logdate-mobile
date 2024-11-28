package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.logdate.client.database.entities.UserDeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * A DAO for interacting with [UserDeviceEntity]s.
 */
@Dao
interface UserDevicesDao {

    /**
     * Retrieves all [UserDeviceEntity]s.
     */
    @Query("SELECT * FROM user_devices")
    fun getAllDevices(): Flow<List<UserDeviceEntity>>

    /**
     * Retrieves a [UserDeviceEntity] by its UID.
     */
    @Query("SELECT * FROM user_devices WHERE uid = :uid")
    suspend fun getDevice(uid: String): UserDeviceEntity

    /**
     * Inserts the given [device] into the database.
     */
    @Insert
    suspend fun addDevice(device: UserDeviceEntity)

    /**
     * Removes the [UserDeviceEntity] with the given UID.
     */
    @Query("DELETE FROM user_devices WHERE uid = :uid")
    suspend fun removeDevice(uid: String)

    /**
     * Updates the given [device] in the database.
     */
    @Update
    suspend fun updateDevice(device: UserDeviceEntity)
}