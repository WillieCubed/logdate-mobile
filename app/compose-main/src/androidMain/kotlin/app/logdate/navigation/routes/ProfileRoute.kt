package app.logdate.navigation.routes

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation route for the profile screen.
 * 
 * This screen allows users to view and edit their profile information including
 * display name, username, bio, birthday, and other account details.
 */
@Serializable
data object ProfileRoute : NavKey