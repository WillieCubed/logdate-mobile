package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_history")
data class LocationHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "altitude")
    val altitude: Double,
    
    @ColumnInfo(name = "accuracy")
    val accuracy: Float? = null,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "place_name")
    val placeName: String? = null,
    
    @ColumnInfo(name = "address")
    val address: String? = null,
    
    @ColumnInfo(name = "place_id")
    val placeId: String? = null,
    
    @ColumnInfo(name = "source")
    val source: String, // "gps", "network", "fused"
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)