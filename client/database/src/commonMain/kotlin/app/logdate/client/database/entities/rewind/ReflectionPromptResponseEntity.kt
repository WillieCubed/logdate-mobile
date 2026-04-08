package app.logdate.client.database.entities.rewind

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One typed response a user wrote to a noticing prompt inside a rewind.
 *
 * Keyed on `(rewindId, promptKey)` so the user can have at most one reply per prompt
 * — saving twice replaces the previous text. Cascade-deletes with the parent rewind so
 * a deleted rewind leaves no orphan responses behind.
 *
 * The [observation] and [invitation] are denormalized from the prompt at write time.
 * The next synthesis pass might invent slightly different prompts; without this copy,
 * a "your past replies" view would have nothing to anchor an old response to.
 */
@Entity(
    tableName = "rewind_prompt_responses",
    primaryKeys = ["rewindId", "promptKey"],
    foreignKeys = [
        ForeignKey(
            entity = RewindEntity::class,
            parentColumns = [RewindConstants.COLUMN_UID],
            childColumns = ["rewindId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("rewindId"),
    ],
)
data class ReflectionPromptResponseEntity(
    val rewindId: Uuid,
    val promptKey: String,
    val observation: String,
    val invitation: String,
    val responseText: String,
    val created: Instant,
    val lastUpdated: Instant,
)
