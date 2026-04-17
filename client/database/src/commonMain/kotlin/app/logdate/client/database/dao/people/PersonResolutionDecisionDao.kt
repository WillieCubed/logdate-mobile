package app.logdate.client.database.dao.people

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.people.PersonResolutionDecisionEntity

@Dao
interface PersonResolutionDecisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: PersonResolutionDecisionEntity)

    @Query("SELECT * FROM person_resolution_decisions")
    suspend fun getAll(): List<PersonResolutionDecisionEntity>

    @Query("SELECT * FROM person_resolution_decisions WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): PersonResolutionDecisionEntity?
}
