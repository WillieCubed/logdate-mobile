@file:Suppress("ktlint:standard:filename")

package app.logdate.client

import android.app.HandoffActivityData
import android.app.HandoffActivityParams
import android.content.ComponentName
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EditorState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** The launch extra naming the draft that an editor handed off from another device continues. */
internal const val EXTRA_HANDOFF_DRAFT_ID = "app.logdate.extra.HANDOFF_DRAFT_ID"

/** What handing an editor window to another device opens there. */
internal sealed interface EditorHandoff {
    /** Nothing is written yet, so the other device opens an empty editor. */
    data object NewEntry : EditorHandoff

    /** The other device opens draft [id] once backup has brought it there. */
    data class Draft(
        val id: Uuid,
    ) : EditorHandoff
}

/**
 * What handing off an editor in [state] continues on the other device, or null while written text
 * has no draft yet. Drafts carry an entry between devices, and autosave creates the first one a
 * couple of seconds after typing starts.
 */
internal fun editorHandoffFor(state: EditorState): EditorHandoff? {
    (state.draftState as? DraftState.Active)?.let { return EditorHandoff.Draft(it.id) }
    return if (state.hasContent()) null else EditorHandoff.NewEntry
}

/** The draft id an editor was launched to continue, when Android Handoff started it. */
internal fun handoffDraftId(extra: String?): Uuid? = extra?.let { runCatching { Uuid.parse(it) }.getOrNull() }

/**
 * Offers this editor window to the person's other Android devices while [FeatureFlag.EDITOR_HANDOFF]
 * is on (Android 17 and later). Without the app installed there, nothing is offered: the web
 * fallback would need logdate.app to open drafts.
 */
internal fun EditorActivity.followEditorHandoffFlag(flags: FeatureFlagStore) {
    if (Build.VERSION.SDK_INT < 37) return
    val params = HandoffActivityParams.Builder().setAllowHandoffWithoutPackageInstalled(false).build()
    lifecycleScope.launch {
        flags.observe(FeatureFlag.EDITOR_HANDOFF).distinctUntilChanged().collect { enabled ->
            setHandoffEnabled(enabled, params)
        }
    }
}

/** The data Android passes to the other device to open [handoff] in LogDate's editor there. */
@RequiresApi(37)
internal fun EditorActivity.editorHandoffData(handoff: EditorHandoff): HandoffActivityData {
    val extras = PersistableBundle()
    if (handoff is EditorHandoff.Draft) extras.putString(EXTRA_HANDOFF_DRAFT_ID, handoff.id.toString())
    return HandoffActivityData
        .Builder(ComponentName(this, EditorActivity::class.java))
        .setExtras(extras)
        .build()
}
