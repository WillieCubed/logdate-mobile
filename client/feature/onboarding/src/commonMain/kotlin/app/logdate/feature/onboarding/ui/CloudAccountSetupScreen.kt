package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.billing.model.LogDateBackupPlanOption
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CloudAccountSetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    useCompactLayout: Boolean = false,
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = koinViewModel(),
) {
    var selectedOption by remember { mutableStateOf<CloudSetupOption?>(null) }
    
    CloudAccountSetupContent(
        useCompactLayout = useCompactLayout,
        selectedOption = selectedOption,
        onBack = onBack,
        onOptionSelected = { selectedOption = it },
        onContinue = onContinue,
        onSkip = onSkip,
        onPlanSelected = onboardingViewModel::selectPlan,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudAccountSetupContent(
    useCompactLayout: Boolean,
    selectedOption: CloudSetupOption?,
    onBack: () -> Unit,
    onOptionSelected: (CloudSetupOption) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    when (selectedOption) {
        CloudSetupOption.CREATE_ACCOUNT -> {
            // TODO: Implement PasskeyAccountCreationScreen when core module is available
            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text("Create Account") },
                        navigationIcon = {
                            IconButton(onClick = { onOptionSelected(CloudSetupOption.SETUP_SYNC) }) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Account creation will be implemented here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Button(onClick = onContinue) {
                        Text("Continue (Mock)")
                    }
                }
            }
        }
        CloudSetupOption.SIGN_IN -> {
            // TODO: Implement PasskeyAuthenticationScreen when core module is available
            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text("Sign In") },
                        navigationIcon = {
                            IconButton(onClick = { onOptionSelected(CloudSetupOption.SETUP_SYNC) }) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Sign in will be implemented here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Button(onClick = onContinue) {
                        Text("Continue (Mock)")
                    }
                }
            }
        }
        else -> {
            AdaptiveLayout(
                useCompactLayout = useCompactLayout,
                modifier = modifier,
                supplementalContent = {
                    Scaffold(
                        topBar = {
                            LargeTopAppBar(
                                title = { Text("Backup & Sync") },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(
                                            Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors().copy(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) { contentPadding ->
                        Column(
                            modifier = Modifier
                                .padding(contentPadding)
                                .padding(Spacing.lg)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            InfoSection()
                        }
                    }
                },
                mainContent = {
                    if (useCompactLayout) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                LargeTopAppBar(
                                    title = { Text("Backup & Sync") },
                                    navigationIcon = {
                                        IconButton(onClick = onBack) {
                                            Icon(
                                                Icons.AutoMirrored.Default.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    },
                                    scrollBehavior = scrollBehavior,
                                )
                            },
                        ) { contentPadding ->
                            MainContent(
                                selectedOption = selectedOption,
                                onOptionSelected = onOptionSelected,
                                onContinue = onContinue,
                                onSkip = onSkip,
                                onPlanSelected = onPlanSelected,
                                modifier = Modifier.padding(contentPadding)
                            )
                        }
                    } else {
                        MainContent(
                            selectedOption = selectedOption,
                            onOptionSelected = onOptionSelected,
                            onContinue = onContinue,
                            onSkip = onSkip,
                            onPlanSelected = onPlanSelected
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun InfoSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Card {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "LogDate Cloud",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Keep your memories safe and accessible across all your devices. LogDate Cloud syncs your entries, photos, and settings securely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Card {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Secure with Passkeys",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "No passwords to remember. Use your device's biometric authentication (Face ID, Touch ID, or fingerprint) for secure, convenient access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    selectedOption: CloudSetupOption?,
    onOptionSelected: (CloudSetupOption) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            InfoSection()
        }
        
        item {
            Text(
                text = "Choose how you'd like to proceed:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }
        
        // Account setup options
        item {
            CloudSetupOptionCard(
                icon = Icons.Default.PersonAdd,
                title = "Create LogDate Cloud Account",
                description = "Set up a new account with secure passkey authentication and choose your storage plan.",
                isSelected = selectedOption == CloudSetupOption.CREATE_ACCOUNT,
                onClick = { onOptionSelected(CloudSetupOption.CREATE_ACCOUNT) }
            )
        }
        
        item {
            CloudSetupOptionCard(
                icon = Icons.Default.AccountCircle,
                title = "Sign In to Existing Account",
                description = "Already have a LogDate Cloud account? Sign in with your passkey to continue.",
                isSelected = selectedOption == CloudSetupOption.SIGN_IN,
                onClick = { onOptionSelected(CloudSetupOption.SIGN_IN) }
            )
        }
        
        item {
            CloudSetupOptionCard(
                icon = Icons.Default.CloudOff,
                title = "Continue Without Cloud Sync",
                description = "Keep using LogDate locally on this device only. You can set up cloud sync later in Settings.",
                isSelected = selectedOption == CloudSetupOption.SKIP,
                onClick = { onOptionSelected(CloudSetupOption.SKIP) }
            )
        }
        
        // Storage plans (shown when create account is selected)
        if (selectedOption == CloudSetupOption.CREATE_ACCOUNT) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                Text(
                    text = "Choose Your Storage Plan:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            item {
                PlanOptionCard(
                    option = LogDateBackupPlanOption.BASIC,
                    title = "Basic (Free)",
                    description = "Up to 10 GB of storage for text, photos, and videos. High-quality compression (1080p).",
                    price = "Free",
                    isRecommended = true,
                    onPlanSelected = onPlanSelected
                )
            }
            
            item {
                PlanOptionCard(
                    option = LogDateBackupPlanOption.STANDARD,
                    title = "Premium",
                    description = "Up to 2 TB of storage with original quality photos and videos. Priority support.",
                    price = "$12/month",
                    isRecommended = false,
                    onPlanSelected = onPlanSelected
                )
            }
        }
        
        // Action buttons
        item {
            Spacer(modifier = Modifier.height(Spacing.lg))
            
            when (selectedOption) {
                CloudSetupOption.CREATE_ACCOUNT -> {
                    Button(
                        onClick = { onOptionSelected(CloudSetupOption.CREATE_ACCOUNT) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Create Account")
                    }
                }
                CloudSetupOption.SIGN_IN -> {
                    Button(
                        onClick = { onOptionSelected(CloudSetupOption.SIGN_IN) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Sign In")
                    }
                }
                CloudSetupOption.SKIP -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue Without Cloud Sync")
                        }
                        Text(
                            text = "You can always set up cloud sync later in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                CloudSetupOption.SETUP_SYNC -> {
                    Text(
                        text = "Setting up sync...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                null -> {
                    Text(
                        text = "Select an option above to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSetupOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder(enabled = true).copy(
                brush = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ).brush
            )
        } else null
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    option: LogDateBackupPlanOption,
    title: String,
    description: String,
    price: String,
    isRecommended: Boolean,
    onPlanSelected: (LogDateBackupPlanOption) -> Unit
) {
    Card(
        onClick = { onPlanSelected(option) }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isRecommended) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            }
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = price,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

enum class CloudSetupOption {
    SETUP_SYNC,
    CREATE_ACCOUNT,
    SIGN_IN,
    SKIP
}

@Preview
@Composable
private fun CloudAccountSetupScreenPreview() {
    MaterialTheme {
        CloudAccountSetupContent(
            useCompactLayout = true,
            selectedOption = CloudSetupOption.CREATE_ACCOUNT,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {}
        )
    }
}