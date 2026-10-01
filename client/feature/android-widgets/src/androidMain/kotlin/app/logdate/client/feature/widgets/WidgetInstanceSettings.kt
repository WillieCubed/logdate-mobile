package app.logdate.client.feature.widgets

import android.content.Context
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType

enum class PhotoWidgetShape { SYSTEM, SOFT, CIRCLE }

/** Launcher widget IDs are independent, so choices must be stored per placed widget. */
class WidgetInstanceSettings(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)

    fun recallMode(
        id: Int,
        fallback: RecallMode,
    ): RecallMode = preferences.getString("$id.recallMode", null)?.let { runCatching { RecallMode.valueOf(it) }.getOrNull() } ?: fallback

    fun contentTypes(
        id: Int,
        fallback: Set<WidgetContentType>,
    ): Set<WidgetContentType> =
        preferences
            .getString("$id.contentTypes", null)
            ?.split(',')
            ?.mapNotNull { runCatching { WidgetContentType.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf(Set<WidgetContentType>::isNotEmpty) ?: fallback

    fun chosenNoteId(id: Int): String? = preferences.getString("$id.chosenNoteId", null)

    fun usePhotoPrompt(id: Int): Boolean = preferences.getBoolean("$id.usePhotoPrompt", true)

    fun hasPhotoPromptChoice(id: Int): Boolean = preferences.contains("$id.usePhotoPrompt")

    fun photoShape(id: Int): PhotoWidgetShape =
        preferences
            .getString("$id.photoShape", null)
            ?.let { runCatching { PhotoWidgetShape.valueOf(it) }.getOrNull() }
            ?: PhotoWidgetShape.SYSTEM

    fun ensureRecallDefaults(
        id: Int,
        mode: RecallMode,
        types: Set<WidgetContentType>,
    ) {
        if (preferences.contains("$id.recallMode")) return
        saveRecall(id, mode, types)
    }

    fun saveRecall(
        id: Int,
        mode: RecallMode,
        types: Set<WidgetContentType>,
    ) {
        preferences
            .edit()
            .putString("$id.recallMode", mode.name)
            .putString("$id.contentTypes", types.joinToString(",") { it.name })
            .apply()
    }

    fun saveChosenNote(
        id: Int,
        noteId: String,
    ) {
        preferences.edit().putString("$id.chosenNoteId", noteId).apply()
    }

    fun saveUsePhotoPrompt(
        id: Int,
        enabled: Boolean,
    ) {
        preferences.edit().putBoolean("$id.usePhotoPrompt", enabled).apply()
    }

    fun savePhotoShape(
        id: Int,
        shape: PhotoWidgetShape,
    ) {
        preferences.edit().putString("$id.photoShape", shape.name).apply()
    }

    fun remap(
        oldId: Int,
        newId: Int,
    ) = remap(intArrayOf(oldId), intArrayOf(newId))

    fun remap(
        oldIds: IntArray,
        newIds: IntArray,
    ) {
        val moves = oldIds.zip(newIds).filter { (oldId, newId) -> oldId != newId }
        if (moves.isEmpty()) return
        val snapshot = preferences.all.toMap()
        val editor = preferences.edit()
        moves.forEach { (oldId, _) ->
            val oldPrefix = "$oldId."
            snapshot.keys.filter { it.startsWith(oldPrefix) }.forEach { editor.remove(it) }
        }
        moves.forEach { (oldId, newId) ->
            val oldPrefix = "$oldId."
            snapshot.forEach { (key, value) ->
                if (key.startsWith(oldPrefix)) {
                    val newKey = "$newId.${key.removePrefix(oldPrefix)}"
                    when (value) {
                        is String -> editor.putString(newKey, value)
                        is Boolean -> editor.putBoolean(newKey, value)
                    }
                }
            }
        }
        editor.apply()
    }

    fun remove(id: Int) {
        preferences
            .edit()
            .remove("$id.recallMode")
            .remove("$id.contentTypes")
            .remove("$id.chosenNoteId")
            .remove("$id.usePhotoPrompt")
            .remove("$id.photoShape")
            .apply()
    }
}
