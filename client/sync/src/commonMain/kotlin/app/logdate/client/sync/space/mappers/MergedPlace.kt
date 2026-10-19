package app.logdate.client.sync.space.mappers

import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.toCentimeters
import app.logdate.shared.model.location.SemanticPlace
import kotlin.time.Instant

/**
 * A place as its `app.logdate.place.place` record holds it: the place the person saved
 * (`user_places`) and the place in their location history ([SemanticPlace]) that share an id,
 * combined by [of].
 *
 * Every place has a name and a center, because the record requires both and so does every place
 * this device stores. A record without them isn't a place this device can read.
 *
 * @property radiusMeters How far from the center counts as being there, at the record's precision
 *   of one centimeter. Only saved places have one.
 * @property savedAt When the person saved the place; null when it only came from location history.
 * @property updatedAt When the saved place last changed.
 */
data class MergedPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double? = null,
    val description: String? = null,
    val locality: String? = null,
    val externalId: String? = null,
    val userConfirmed: Boolean = false,
    val savedAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        if (savedAt != null && !userConfirmed) throw SpaceRecordMappingException("Place: a saved place is always confirmed")
    }

    /**
     * The saved place, or null when the person never saved this one. A record from another app can
     * name a saved place without a radius; it reads with the app's usual radius.
     */
    fun savedPlace(): UserPlaceEntity? {
        val saved = savedAt ?: return null
        return UserPlaceEntity(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
            description = description,
            createdAt = saved.toEpochMilliseconds(),
            updatedAt = (updatedAt ?: saved).toEpochMilliseconds(),
        )
    }

    /** The place as location history sees it. Every place has this view, saved or not. */
    fun historyPlace(): SemanticPlace = SemanticPlace(id, name, latitude, longitude, locality, externalId, userConfirmed)

    companion object {
        /** The radius the app gives a saved place when none was chosen. */
        const val DEFAULT_RADIUS_METERS: Double = 100.0

        /**
         * Combines the saved place and the history place that share an id, as the lexicon defines:
         * every field from both, and where both have a name or coordinates the saved place's win,
         * because renaming or moving a saved place changes it in place. Label corrections decide the
         * name shown; they aren't part of the place.
         */
        fun of(
            saved: UserPlaceEntity?,
            history: SemanticPlace?,
        ): MergedPlace {
            require(saved != null || history != null) { "A place needs a saved place, a history place or both" }
            require(saved == null || history == null || saved.id == history.id) {
                "A saved place and a history place with another id can't be combined"
            }
            val id = saved?.id ?: requireNotNull(history).id
            return MergedPlace(
                id = id,
                name = saved?.name ?: requireNotNull(history).name,
                latitude = saved?.latitude ?: requireNotNull(history).latitude,
                longitude = saved?.longitude ?: requireNotNull(history).longitude,
                radiusMeters = saved?.let { quantizedRadius("Place $id", it.radiusMeters) },
                description = saved?.description,
                locality = history?.locality,
                externalId = history?.externalId,
                userConfirmed = saved != null || history?.userConfirmed == true,
                savedAt = saved?.createdAt?.let(Instant::fromEpochMilliseconds),
                updatedAt = saved?.updatedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

        /** [meters] at the precision the record keeps: whole centimeters, and never below zero. */
        fun quantizedRadius(
            owner: String,
            meters: Double,
        ): Double = meters.toCentimeters("$owner radius") / CENTIMETERS_PER_METER

        internal const val CENTIMETERS_PER_METER = 100.0
    }
}
