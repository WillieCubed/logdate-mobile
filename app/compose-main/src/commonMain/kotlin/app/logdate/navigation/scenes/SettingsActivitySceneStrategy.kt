package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.logdate.feature.core.settings.navigation.SETTINGS_CATEGORIES_METADATA_KEY
import app.logdate.feature.core.settings.navigation.SettingsActivityCategories
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.navigation.ActivityPresentation
import app.logdate.ui.navigation.activityPresentation

class SettingsActivitySceneStrategy<T : NavKey>(
    private val supportsDualPane: () -> Boolean,
    private val foldableSplitLayout: () -> FoldableSplitLayout = { FoldableSplitLayout.None },
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val detail = entries.lastOrNull() ?: return null
        val presentation = detail.activityPresentation() as? ActivityPresentation.SettingsDetail ?: return null
        val categories = detail.metadata[SETTINGS_CATEGORIES_METADATA_KEY] as? SettingsActivityCategories ?: return null
        val activityEntries =
            entries.takeLastWhile {
                it.activityPresentation() is ActivityPresentation.SettingsDetail ||
                    it.activityPresentation() == ActivityPresentation.SettingsRoot
            }
        val root = activityEntries.lastOrNull { it.activityPresentation() == ActivityPresentation.SettingsRoot }
        return SettingsActivityScene(
            key = Pair("SettingsActivityScene", detail.contentKey),
            previousEntries = entries.dropLast(1),
            entries = listOfNotNull(root, detail),
            detail = detail,
            selectedSection = presentation.section,
            categories = categories,
            supportsDualPane = supportsDualPane(),
            foldableSplitLayout = foldableSplitLayout(),
        )
    }
}
