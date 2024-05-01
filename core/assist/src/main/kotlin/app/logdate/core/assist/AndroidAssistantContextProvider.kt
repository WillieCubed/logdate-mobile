package app.logdate.core.assist

import android.content.ClipData
import org.json.JSONObject
import javax.inject.Inject

/**
 * The default LogDate assistant context provider for Android.
 */
class AndroidAssistantContextProvider @Inject constructor() : AssistantContextProvider {

    override val jsonData: String
        get() = generateScreenData()

    override val clipData: ClipData?
        get() = currentClipData

    private var currentScreen: ScreenData = ScreenData.Other

    private var currentClipData: ClipData? = null

    /**
     * Updates the current screen.
     *
     * This method should be called whenever the user navigates to a new screen. Whenever the screen
     * changes, the [jsonData] describing the screen for the assistant updates in addition to any
     * [clipData] that may be relevant to the current screen.
     */
    fun updateCurrentScreen(screen: ScreenData) {
        currentScreen = screen
        when (screen) {
            is ScreenData.Journal -> {
            }

            is ScreenData.MediaItem -> {
            }

            is ScreenData.TimelineDetails -> {
            }

            else -> {
                // Do something
            }

        }
    }

    /**
     * Returns a JSON-LD object representing the current screen.
     */
    private fun generateScreenData(): String {
        return when (val screen = currentScreen) {
            is ScreenData.Journal -> {
                JSONObject()
                    .put("@type", "")
                    .put("name", screen.title)
                    .apply {
                        screen.publicUrl?.let { put("url", it) }
                    }
                    .toString()
            }

            is ScreenData.UserProfile -> {
                JSONObject()
                    .put("@type", "")
                    .put("name", screen.screenName)
                    .put("givenName", screen.firstName)
                    .put("familyName", screen.lastName)
                    .put("alternateName", screen.handle)
                    .put("image", screen.profileImageUrl)
                    .apply {
                        screen.publicUrl?.let { put("url", it) }
                    }
                    .toString()
            }

            else -> {
                JSONObject().toString()
            }
        }

//        return when (screen) {
//            "home" -> """
//                {
//                    "@context": "http://schema.org",
//                    "@type": "WebPage",
//                    "name": "Logdate",
//                    "description": "Logdate is a personal journaling app that helps you keep track of your thoughts, feelings, and experiences.",
//                    "url": "https://logdate.app",
//                    "keywords": "journal, diary, personal, thoughts, feelings, experiences",
//                    "inLanguage": "en",
//                    "isPartOf": {
//                        "@type": "WebSite",
//                        "name": "Logdate",
//                        "url": "https://logdate.app"
//                    }
//                }
//            """.trimIndent()
//            "settings" -> """
//                {
//                    "@context": "http://schema.org",
//                    "@type": "WebPage",
//                    "name": "Logdate Settings",
//                    "description": "Logdate is a personal journaling app that helps you keep track of your thoughts, feelings, and experiences.",
//                    "url": "https://logdate.app/settings",
//                    "keywords": "journal, diary, personal, thoughts, feelings, experiences",
//                    "inLanguage": "en",
//                    "isPartOf": {
//                        "@type": "WebSite",
//                        "name": "Logdate",
//                        "url": "https://logdate.app"
//                    }
//                }
//            """.trimIndent()
//            else -> ""
//        }
    }
}

sealed interface ScreenData {
    data class TimelineDetails(
        val selectedId: String? = null,
    ) : ScreenData

    data class Journal(
        val uid: String,
        val title: String,
        val publicUrl: String? = null,
    ) : ScreenData

    data class UserProfile(
        val uid: String,
        val screenName: String,
        val handle: String,
        val firstName: String,
        val lastName: String,
        val profileImageUrl: String,
        val publicUrl: String? = null,
    ) : ScreenData

    data class MediaItem(
        val uid: String,
        val mediaUri: String,
        val publicUrl: String? = null,
    ) : ScreenData

    data object Other : ScreenData
}