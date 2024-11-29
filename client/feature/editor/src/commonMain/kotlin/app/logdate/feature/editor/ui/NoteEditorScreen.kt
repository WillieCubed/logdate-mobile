@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalSharedTransitionApi::class
)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EditorUiState
import app.logdate.feature.editor.ui.editor.EntryBlockData
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.rememberEditorState
import app.logdate.ui.LocalSharedTransitionScope
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EntryEditorScreen(
    onNavigateBack: () -> Unit,
    onEntrySaved: (uid: String) -> Unit, // TODO: Convert to UUID
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState = rememberEditorState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is EditorUiState.Success -> {
//                editorState.blocks = (uiState as EditorUiState.Success).blocks
            }

            is EditorUiState.Error -> {}
            EditorUiState.Loading -> {}
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                onNavigateBack = onNavigateBack,
                onSave = { viewModel.saveEntry() }
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (uiState) {
                is EditorUiState.Loading -> LoadingIndicator()
                is EditorUiState.Error -> ErrorView(
                    message = (uiState as EditorUiState.Error).message
                )

                is EditorUiState.Success -> EditorContent(
                    editorState = editorState,
                )
            }

            if (editorState.isAddingNewBlock) {
                BlockTypePicker(
                    onTypeSelected = { blockType ->
                        val newBlock = viewModel.createNewBlock(blockType)
                        editorState.addBlock(newBlock)
                        editorState.finishAddingBlock()
                        editorState.expandBlock(newBlock.id)
                    },
                    onDismiss = { editorState.finishAddingBlock() }
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    editorState: EditorState,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope =
        LocalSharedTransitionScope.current ?: error("No shared transition scope found")
    // TODO: Use AnimatedVisibilityScope of screen itself
//    val animatedVisibilityScope =
//        LocalNavAnimatedVisibilityScope.current ?: error("No animated visibility scope found")

    with(sharedTransitionScope) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                editorState.blocks,
                key = { it.id }
            ) { block ->
                AnimatedContent(
                    targetState = editorState.expandedBlockId,
                    label = "Animated block",
                ) {
                    EntryBlockItem(
                        block = block,
                        isExpanded = block.id == editorState.expandedBlockId,
                        onExpandClick = { editorState.expandBlock(block.id) },
                        onCollapseClick = {
//                    editorState.expandBlock(null)
                        },
                        onUpdateBlock = { editorState.updateBlock(it) },
                        modifier = Modifier.sharedElement(
                            state = rememberSharedContentState(key = block.id),
                            animatedVisibilityScope = this,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryBlockItem(
    block: EntryBlockData,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onUpdateBlock: (EntryBlockData) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { if (!isExpanded) onExpandClick() }
    ) {
//        when (block) {
//            is TextBlockData -> TextBlockContent(
//                block = block,
//                isExpanded = isExpanded,
//                onCollapseClick = onCollapseClick,
//                onUpdateBlock = onUpdateBlock,
//            )
//
//            is ImageBlockData -> ImageBlockContent(
//                block = block,
//                isExpanded = isExpanded,
//                onCollapseClick = onCollapseClick,
//                onUpdateBlock = onUpdateBlock,
//            )
//            // Add other block type implementations
//        }
    }
}

@Composable
private fun BlockTypePicker(
    onTypeSelected: (BlockType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Block") },
        text = {
            Column {
                BlockType.entries.forEach { blockType ->
                    TextButton(
                        onClick = { onTypeSelected(blockType) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(blockType.name.lowercase().replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase() else it.toString()
                        })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message)
    }
}
