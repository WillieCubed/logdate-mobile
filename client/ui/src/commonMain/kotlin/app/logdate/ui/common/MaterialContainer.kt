package app.logdate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A Material You-themed container that automatically applies spacing between its children,
 * gives each child a surface background, and rounds the entire container.
 *
 * This component follows Material Design 3 guidelines and provides consistent spacing
 * and surface treatment for grouped content.
 *
 * @param modifier Modifier to be applied to the container
 * @param shape The shape of the container. Defaults to medium rounded corners
 * @param containerColor The background color of the main container
 * @param contentPadding Padding applied to the entire container content
 * @param itemSpacing Vertical spacing between items in the container
 * @param itemShape Shape applied to each child surface
 * @param itemColor Background color for each child surface
 * @param itemPadding Padding applied to each child surface
 * @param horizontalAlignment Horizontal alignment of items within the container
 * @param content The content to be displayed, each top-level composable will be wrapped in a Surface
 */
@Composable
fun MaterialContainer(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    itemSpacing: Dp = Spacing.xs,
    itemShape: Shape = MaterialTheme.shapes.extraSmall,
    itemPadding: PaddingValues = PaddingValues(Spacing.md),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable MaterialContainerScope.() -> Unit,
) {
    // Create a modified color scheme where the surface color is overridden to use surfaceContainerHigh
    val currentColorScheme = MaterialTheme.colorScheme
    val modifiedColorScheme = currentColorScheme.copy(
        surface = currentColorScheme.surfaceContainerHigh
    )
    
    MaterialTheme(
        colorScheme = modifiedColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                horizontalAlignment = horizontalAlignment,
            ) {
                val scope = MaterialContainerScopeImpl(
                    itemShape = itemShape,
                    itemColor = containerColor,
                    itemPadding = itemPadding
                )
                scope.content()
            }
        }
    }
}

/**
 * Scope for MaterialContainer content that provides utility functions for creating surfaced items.
 */
interface MaterialContainerScope {
    /**
     * Creates a surface-wrapped item with the container's default styling.
     *
     * @param modifier Modifier to be applied to the surface
     * @param color Override the default item color
     * @param shape Override the default item shape
     * @param padding Override the default item padding
     * @param content The content to be displayed within the surface
     */
    @Composable
    fun SurfaceItem(
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        shape: Shape? = null,
        padding: PaddingValues? = null,
        content: @Composable () -> Unit,
    )

    /**
     * Creates an unsurfaced item that doesn't have a background surface.
     * Useful for items that already have their own surface treatment or for dividers.
     *
     * @param modifier Modifier to be applied to the content
     * @param content The content to be displayed without surface wrapping
     */
    @Composable
    fun UnsurfacedItem(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    )
}

private class MaterialContainerScopeImpl(
    private val itemShape: Shape,
    private val itemColor: Color,
    private val itemPadding: PaddingValues,
) : MaterialContainerScope {

    @Composable
    override fun SurfaceItem(
        modifier: Modifier,
        color: Color,
        shape: Shape?,
        padding: PaddingValues?,
        content: @Composable () -> Unit,
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape ?: itemShape,
            color = if (color != Color.Unspecified) color else itemColor,
        ) {
            content()
        }
    }

    @Composable
    override fun UnsurfacedItem(
        modifier: Modifier,
        content: @Composable () -> Unit,
    ) {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Simplified MaterialContainer for cases where you just want automatic spacing
 * and surface treatment without custom item configuration.
 *
 * @param modifier Modifier to be applied to the container
 * @param shape The shape of the container
 * @param containerColor The background color of the main container
 * @param contentPadding Padding applied to the entire container content
 * @param itemSpacing Vertical spacing between items
 * @param content Column content where each top-level composable becomes a surfaced item
 */
@Composable
fun SimpleMaterialContainer(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    itemSpacing: Dp = Spacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun MaterialContainerPreview() {
    LogDateTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Text(
                text = "MaterialContainer Examples",
                style = MaterialTheme.typography.headlineMedium
            )

            MaterialContainer {
                SurfaceItem {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Column {
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Navigate to home screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SurfaceItem {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Column {
                            Text(
                                text = "Profile",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Manage your profile settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            MaterialContainer {
                SurfaceItem {
                    Text(
                        text = "Section 1",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                UnsurfacedItem {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                SurfaceItem {
                    Text(
                        text = "Section 2",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            SimpleMaterialContainer {
                Text(
                    text = "Simple container item 1",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Simple container item 2",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}