@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import logdate.client.feature.core.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Screen shown after successful account creation.
 *
 * This screen confirms that the account has been created successfully
 * and provides a button to proceed to the next step.
 *
 * @param onFinish Callback when the user finishes the account setup process.
 */
@Composable
fun AccountCreationCompletionScreen(onFinish: () -> Unit) {
    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.account_created),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    stringResource(Res.string.your_logdate_cloud_account_has_been_successfully_created) +
                        "You can now sync your data across all your devices.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.get_started))
            }
        }
    }
}
