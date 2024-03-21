package app.logdate.mobile.ui.common

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.logdate.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(title: String = stringResource(id = R.string.app_name)) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    )
}