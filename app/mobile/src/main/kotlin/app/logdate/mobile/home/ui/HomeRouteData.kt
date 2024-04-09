package app.logdate.mobile.home.ui

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
sealed class HomeRouteData(
    val id: String,
    val label: String,
    val unselectedIcon: @RawValue ImageVector = Icons.Outlined.FavoriteBorder,
    val selectedIcon: @RawValue ImageVector = Icons.Filled.Favorite,
) : Parcelable {
    data object Timeline :
        HomeRouteData("timeline", "Timeline", Icons.Outlined.Timeline, Icons.Filled.Timeline)

    data object Rewind :
        HomeRouteData("rewind", "Rewind", Icons.Outlined.History, Icons.Filled.History)

    data object Journals :
        HomeRouteData("journals", "Journals", Icons.Outlined.Book, Icons.Filled.Book)

    data object Library : HomeRouteData(
        "library",
        "Library",
        Icons.AutoMirrored.Outlined.LibraryBooks,
        Icons.AutoMirrored.Filled.LibraryBooks,
    )

    companion object {
        // Use lazy because this will be null otherwise at static initialization
        val ALL by lazy {
            listOf(
                Timeline, Rewind, Journals,
                // TODO: Re-enable library once available
                // Library
            )
        }
    }
}