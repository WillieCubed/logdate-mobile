package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun QuotaUsageBlock(
    quotaUsage: StorageQuotaUi,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Header (always visible)
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Total Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${quotaUsage.usedGB}/${quotaUsage.totalGB} GB",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                
                LinearProgressIndicator(
                    progress = { quotaUsage.usagePercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Detailed breakdown box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Progress bar with segments
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                var currentOffset = 0f
                                quotaUsage.categories.forEach { category ->
                                    val categoryProgress = category.sizeInMB / (quotaUsage.totalGB * 1000)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(currentOffset + categoryProgress)
                                            .height(24.dp)
                                            .background(category.color)
                                    )
                                    currentOffset += categoryProgress
                                }
                            }
                            
                            Text(
                                text = "${quotaUsage.usedGB}/${quotaUsage.totalGB} GB",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Category breakdown
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        quotaUsage.categories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                color = category.color,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = if (category.sizeInMB >= 1000) {
                                        "${String.format("%.1f", category.sizeInMB / 1000f)} GB"
                                    } else {
                                        "${category.sizeInMB} MB"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun QuotaUsageBlockPreview() {
    val mockQuotaUsage = StorageQuotaUi(
        totalBytes = 100L * 1024L * 1024L * 1024L, // 100GB
        usedBytes = 12L * 1024L * 1024L * 1024L + 300L * 1024L * 1024L, // 12.3GB
        usagePercentage = 0.123f,
        categories = listOf(
            StorageCategory(
                name = "IMAGE_NOTES",
                usedBytes = 255L * 1024L * 1024L, // 255MB
                usagePercentage = 0.02f,
                color = Color(0xFF2196F3), // Blue
                formattedUsed = "255 MB"
            ),
            StorageCategory(
                name = "VIDEO_NOTES", 
                usedBytes = 12L * 1024L * 1024L * 1024L, // 12GB
                usagePercentage = 0.92f,
                color = Color(0xFFFF9800), // Orange
                formattedUsed = "12 GB"
            ),
            StorageCategory(
                name = "VOICE_NOTES",
                usedBytes = 255L * 1024L * 1024L, // 255MB
                usagePercentage = 0.02f,
                color = Color(0xFFF44336), // Red
                formattedUsed = "255 MB"
            )
        ),
        formattedTotal = "100 GB",
        formattedUsed = "12.3 GB",
        isOverQuota = false
    )
    
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quota Usage Block",
            style = MaterialTheme.typography.headlineMedium
        )
        QuotaUsageBlock(quotaUsage = mockQuotaUsage)
    }
}