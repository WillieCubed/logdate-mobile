package app.logdate.feature.rewind.ui.past

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PastRewindsRoute(onGoBack: () -> Unit) {
    PastRewindsScreen(onGoBack = onGoBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastRewindsScreen(onGoBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past rewind") },
                navigationIcon = {
                    IconButton(onClick = { onGoBack() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) {
        LazyColumn(
            contentPadding = it
        ) {
            // TODO: Show past rewinds
            item {
                Spacer(
                    Modifier.windowInsetsBottomHeight(
                        WindowInsets.systemBars
                    )
                )
            }
        }
    }
}

fun PastRewindCard() {

}