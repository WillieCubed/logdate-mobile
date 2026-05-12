@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.rewind.ui.past

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.rewind.generated.resources.*
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

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
                title = { Text(stringResource(Res.string.past_rewind)) },
                navigationIcon = {
                    IconButton(onClick = { onGoBack() }) {
                        Icon(painter = PlatformIcons.back(), contentDescription = stringResource(UiRes.string.common_back))
                    }
                },
            )
        },
    ) {
        LazyColumn(
            contentPadding = it,
        ) {
            // TODO: Show past rewinds
            item {
                Spacer(
                    Modifier.windowInsetsBottomHeight(
                        WindowInsets.systemBars,
                    ),
                )
            }
        }
    }
}

fun PastRewindCard() {
}
