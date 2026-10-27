@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.state.EditorBlockPresentation
import app.logdate.shared.model.Journal
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.ink_add
import logdate.client.feature.editor.generated.resources.ink_example_body
import logdate.client.feature.editor.generated.resources.ink_example_title
import logdate.client.feature.editor.generated.resources.ink_journal
import logdate.client.feature.editor.generated.resources.ink_preview_description
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private val InkBlockId = Uuid.parse("00000000-0000-0000-0000-000000000701")
private val TextBlockId = Uuid.parse("00000000-0000-0000-0000-000000000702")

/** The actual editor shell, block sequence, and shared focus morph with prototype-only ink rendering. */
@Composable
fun HandwritingEntryPrototype(
    initialPage: HandwritingPage = HandwritingPage(),
    modifier: Modifier = Modifier,
    initiallyFocused: Boolean = false,
    onPageChanged: (HandwritingPage) -> Unit = {},
    onSavePracticePage: ((HandwritingPage) -> Unit)? = null,
    storageStatus: String? = null,
    initialText: String? = null,
    onTextChanged: (String) -> Unit = {},
    onExit: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(storageStatus) { if (storageStatus != null) snackbar.showSnackbar(storageStatus) }
    val sampleText = stringResource(Res.string.ink_example_title) + "\n\n" + stringResource(Res.string.ink_example_body)
    var text by rememberSaveable { mutableStateOf(initialText ?: sampleText) }
    var history by remember { mutableStateOf(HandwritingHistory(initialPage)) }
    var selected by remember { mutableStateOf(if (initiallyFocused) InkBlockId else null) }
    var inkPresent by rememberSaveable { mutableStateOf(true) }
    var textPresent by rememberSaveable { mutableStateOf(true) }
    var journalExpanded by rememberSaveable { mutableStateOf(false) }
    val journalTitle = stringResource(Res.string.ink_journal)
    val journal = remember { Journal(title = journalTitle) }
    var selectedJournals by remember { mutableStateOf(listOf(journal.id)) }
    val tools = remember { HandwritingDrawingSettings() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var contextAvailable by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val drawing = inkPresent && selected == InkBlockId
    var focusProgress by remember { mutableStateOf(if (initiallyFocused) 1f else 0f) }
    var immersiveTarget by remember { mutableStateOf(initiallyFocused) }
    LaunchedEffect(drawing) {
        if (drawing) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    fun update(next: HandwritingHistory) {
        history = next
        onPageChanged(next.page)
    }
    val marker = remember { TextBlockUiState(id = InkBlockId) }
    val textBlock = remember(text) { TextBlockUiState(id = TextBlockId, content = text) }
    val blocks =
        buildList<EntryBlockUiState> {
            if (textPresent) add(textBlock)
            // This marker never enters the journal repository. Its renderer is supplied only by this debug host.
            if (inkPresent) add(marker)
        }
    val inkPresentation =
        EditorBlockPresentation(
            immersive = true,
            focusTransitionProgress = focusProgress,
            inline = { open ->
                InlineHandwritingBlock(
                    InkBlockId,
                    history.page,
                    tools,
                    focusProgress,
                    description = stringResource(Res.string.ink_preview_description),
                    onFocus = open,
                )
            },
            focused = {
                FocusedHandwritingBlock(
                    InkBlockId,
                    history,
                    tools,
                    context = blocks.filter { it.id != InkBlockId },
                    onEditHistory = { edit -> update(edit(history)) },
                    focusProgress = focusProgress,
                    contextAvailable = contextAvailable,
                )
            },
        )
    val state =
        BlocksUiState(
            blocks = blocks,
            expandedBlockId = selected,
            availableJournals = listOf(journal),
            selectedJournalIds = selectedJournals,
            onBlockFocused = { selected = it },
            onJournalSelectionChanged = { selectedJournals = it },
            onUpdateBlock = { block ->
                if (block is TextBlockUiState && block.id == TextBlockId) {
                    text = block.content
                    onTextChanged(text)
                }
            },
            onDeleteBlock = { id ->
                selected = null
                if (id == InkBlockId) {
                    inkPresent = false
                    update(history.edit(HandwritingPage()))
                }
                if (id == TextBlockId) textPresent = false
                if (!inkPresent && !textPresent) {
                    textPresent = true
                    text = ""
                    onTextChanged(text)
                }
            },
            onCreateBlock = { _, _ -> error("Block creation is disabled for this fixed prototype entry") },
            allowBlockCreation = false,
            blockPresentations = mapOf(InkBlockId to inkPresentation),
        )
    val focus = if (drawing) EditorFocus.Drawing(InkBlockId) else selected?.let { EditorFocus.Editing(it) } ?: EditorFocus.Entry
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        contextAvailable =
                            with(
                                density,
                            ) { it.width.toDp() >= 840.dp }
                    }.testTag("handwriting_editor"),
            ) {
                ImmersiveEditorLayout(
                    isImmersiveBlockActive = immersiveTarget,
                    immersiveExitProgress = if (immersiveTarget) 0f else 1f,
                    animateContextControls = false,
                    focusState =
                        if (immersiveTarget) {
                            EditorFocus.Drawing(InkBlockId)
                        } else if (drawing) {
                            EditorFocus.Entry
                        } else {
                            focus
                        },
                    topBarContent = {
                        NoteEditorToolbar(
                            onBack = { if (selected != null) selected = null else onExit() },
                            onSave = {
                                onTextChanged(if (textPresent) text else "")
                                onSavePracticePage?.invoke(history.page)
                            },
                            onShowDrafts = {},
                            actionsVisible = !immersiveTarget,
                            optionsVisible = false,
                            saveEnabled = onSavePracticePage != null,
                        )
                    },
                    editorContent = {
                        MainEditorContent(
                            state,
                            shouldReturnToPickerOnBack = false,
                            onDismissExpanded = { selected = null },
                            onBlockFocusTransitionProgress = { focusProgress = it },
                            onBlockFocusTargetChanged = { immersiveTarget = it },
                            focusState = focus,
                            listState = listState,
                        )
                    },
                    bottomContent = {
                        Column {
                            EditorBottomContent(
                                listOf(journal),
                                selectedJournals,
                                { selectedJournals = it },
                                journalExpanded,
                                { journalExpanded = it },
                            )
                            if (!inkPresent) {
                                TextButton({
                                    inkPresent = true
                                    selected = InkBlockId
                                }) { Text(stringResource(Res.string.ink_add)) }
                            }
                        }
                    },
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}
