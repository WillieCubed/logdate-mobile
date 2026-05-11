package app.logdate.ui.platform

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

actual object PlatformIcons {
    @Composable actual fun back(): Painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack)

    @Composable actual fun close(): Painter = rememberVectorPainter(Icons.Filled.Close)

    @Composable actual fun edit(): Painter = rememberVectorPainter(Icons.Filled.Edit)

    @Composable actual fun search(): Painter = rememberVectorPainter(Icons.Filled.Search)

    @Composable actual fun settings(): Painter = rememberVectorPainter(Icons.Filled.Settings)

    @Composable actual fun add(): Painter = rememberVectorPainter(Icons.Filled.Add)

    @Composable actual fun more(): Painter = rememberVectorPainter(Icons.Filled.MoreVert)

    @Composable actual fun history(): Painter = rememberVectorPainter(Icons.Filled.History)

    @Composable actual fun brush(): Painter = rememberVectorPainter(Icons.Filled.Brush)

    @Composable actual fun delete(): Painter = rememberVectorPainter(Icons.Filled.Delete)

    @Composable actual fun share(): Painter = rememberVectorPainter(Icons.Filled.Share)

    @Composable actual fun check(): Painter = rememberVectorPainter(Icons.Filled.Check)

    @Composable actual fun info(): Painter = rememberVectorPainter(Icons.Filled.Info)

    @Composable actual fun play(): Painter = rememberVectorPainter(Icons.Filled.PlayArrow)

    @Composable actual fun pause(): Painter = rememberVectorPainter(Icons.Filled.Pause)

    @Composable actual fun newEntry(): Painter = rememberVectorPainter(Icons.Filled.EditNote)

    @Composable actual fun rewind(): Painter = rememberVectorPainter(Icons.Filled.Replay)

    @Composable actual fun journal(): Painter = rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)

    @Composable actual fun library(): Painter = rememberVectorPainter(Icons.Filled.Image)

    @Composable actual fun timeline(): Painter = rememberVectorPainter(Icons.Filled.Today)

    @Composable actual fun location(): Painter = rememberVectorPainter(Icons.Filled.LocationOn)
}
