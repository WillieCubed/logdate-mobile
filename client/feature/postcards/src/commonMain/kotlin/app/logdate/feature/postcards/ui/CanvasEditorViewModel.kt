package app.logdate.feature.postcards.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.dao.PostcardDao
import app.logdate.client.database.entities.PostcardEntity
import app.logdate.feature.postcards.model.CanvasBackground
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.PostcardDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The currently active editing tool.
 */
enum class CanvasTool {
    SELECT,
    INK,
    SHAPE,
    TEXT,
    STICKER,
}

/**
 * State of the content shelf at the bottom of the editor.
 */
sealed interface ShelfMode {
    data object Photos : ShelfMode

    data object Stickers : ShelfMode

    data object Browse : ShelfMode
}

/**
 * Mutable state of the canvas editor.
 */
data class CanvasEditorState(
    val document: PostcardDocument,
    val selectedElementId: Uuid? = null,
    val activeTool: CanvasTool = CanvasTool.SELECT,
    val shelfMode: ShelfMode = ShelfMode.Photos,
    val shelfPhotos: List<ShelfPhoto> = emptyList(),
    val isSaving: Boolean = false,
    val isNewPostcard: Boolean = true,
)

/**
 * A photo available on the shelf for placement onto the canvas.
 */
data class ShelfPhoto(
    val mediaUri: String,
    val momentRef: Uuid,
)

/**
 * ViewModel for the canvas editor screen.
 *
 * Manages the document state, undo/redo history, tool selection, and shelf content.
 */
class CanvasEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val postcardDao: PostcardDao,
) : ViewModel() {
    private val postcardId: Uuid? =
        savedStateHandle
            .get<String>("postcardId")
            ?.let { Uuid.parse(it) }

    private val sourceMomentRef: Uuid? =
        savedStateHandle
            .get<String>("sourceMomentRef")
            ?.let { Uuid.parse(it) }

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<CanvasEditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<PostcardDocument>()
    private val redoStack = ArrayDeque<PostcardDocument>()

    init {
        if (postcardId != null) {
            loadExistingPostcard(postcardId)
        }
    }

    private fun createInitialState(): CanvasEditorState {
        val now = Clock.System.now()
        val document =
            PostcardDocument(
                id = postcardId ?: Uuid.random(),
                title = "",
                createdAt = now,
                modifiedAt = now,
                sourceMomentRef = sourceMomentRef,
                background = CanvasBackground.SolidColor("#FFFFFF"),
            )
        return CanvasEditorState(
            document = document,
            isNewPostcard = postcardId == null,
        )
    }

    private fun loadExistingPostcard(id: Uuid) {
        viewModelScope.launch {
            val entity = postcardDao.getPostcardOneShot(id) ?: return@launch
            try {
                val document =
                    json.decodeFromString(
                        PostcardDocument.serializer(),
                        entity.documentJson,
                    )
                _state.update { it.copy(document = document, isNewPostcard = false) }
            } catch (_: Exception) {
                // Failed to parse — keep the blank document
            }
        }
    }

    // --- Element operations ---

    /**
     * Adds a photo element from the shelf onto the canvas at the given position.
     */
    fun addPhotoElement(
        shelfPhoto: ShelfPhoto,
        x: Float,
        y: Float,
    ) {
        pushUndo()
        val element =
            CanvasElement.Photo(
                id = Uuid.random(),
                momentRef = shelfPhoto.momentRef,
                mediaUri = shelfPhoto.mediaUri,
                transform = ElementTransform(x = x, y = y),
                zIndex = nextZIndex(),
            )
        updateDocument { doc ->
            doc.copy(elements = doc.elements + element)
        }
    }

    /**
     * Selects an element by ID, or deselects if null.
     */
    fun selectElement(elementId: Uuid?) {
        _state.update { it.copy(selectedElementId = elementId) }
    }

    /**
     * Pushes undo state once at the start of a drag gesture.
     * Call this before the first [moveElement] in a drag sequence.
     */
    fun beginDrag() {
        pushUndo()
    }

    /**
     * Moves an element by the given delta.
     * Does not push undo — call [beginDrag] once at the start of the gesture.
     */
    fun moveElement(
        elementId: Uuid,
        dx: Float,
        dy: Float,
    ) {
        updateDocument { doc ->
            doc.copy(
                elements =
                    doc.elements.map { el ->
                        if (el.id == elementId) {
                            el.withTransform(
                                el.transform.copy(
                                    x = el.transform.x + dx,
                                    y = el.transform.y + dy,
                                ),
                            )
                        } else {
                            el
                        }
                    },
            )
        }
    }

    /**
     * Signals the end of a drag gesture. No-op currently — undo was pushed in [beginDrag].
     */
    fun endDrag() {
        // Intentionally empty. Undo snapshot was captured in beginDrag().
    }

    /**
     * Scales and rotates the selected element.
     */
    fun transformElement(
        elementId: Uuid,
        scaleDelta: Float,
        rotationDelta: Float,
    ) {
        pushUndo()
        updateDocument { doc ->
            doc.copy(
                elements =
                    doc.elements.map { el ->
                        if (el.id == elementId) {
                            el.withTransform(
                                el.transform.copy(
                                    scaleX = (el.transform.scaleX * scaleDelta).coerceIn(0.1f, 10f),
                                    scaleY = (el.transform.scaleY * scaleDelta).coerceIn(0.1f, 10f),
                                    rotation = el.transform.rotation + rotationDelta,
                                ),
                            )
                        } else {
                            el
                        }
                    },
            )
        }
    }

    /**
     * Removes the selected element from the canvas.
     */
    fun deleteSelectedElement() {
        val selectedId = _state.value.selectedElementId ?: return
        pushUndo()
        updateDocument { doc ->
            doc.copy(elements = doc.elements.filter { it.id != selectedId })
        }
        selectElement(null)
    }

    // --- Ink creation ---

    /**
     * Adds a completed ink stroke as a new element.
     */
    fun addInkStroke(
        points: List<app.logdate.feature.postcards.model.InkPoint>,
        tool: app.logdate.feature.postcards.model.InkTool,
        color: String,
        strokeWidth: Float,
    ) {
        pushUndo()
        val element =
            CanvasElement.Ink(
                id = Uuid.random(),
                tool = tool,
                color = color,
                strokeWidth = strokeWidth,
                points = points,
                zIndex = nextZIndex(),
            )
        updateDocument { doc -> doc.copy(elements = doc.elements + element) }
    }

    // --- Shape creation ---

    /**
     * Adds a shape element from a completed drag gesture.
     */
    fun addShape(
        shapeKind: app.logdate.feature.postcards.model.ShapeKind,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: String,
        strokeWidth: Float,
    ) {
        pushUndo()
        val element =
            CanvasElement.Shape(
                id = Uuid.random(),
                shapeKind = shapeKind,
                color = color,
                strokeWidth = strokeWidth,
                width = width,
                height = height,
                transform = ElementTransform(x = x, y = y),
                zIndex = nextZIndex(),
            )
        updateDocument { doc -> doc.copy(elements = doc.elements + element) }
    }

    // --- Text creation ---

    /**
     * Adds a text element at the given position.
     */
    fun addTextElement(
        content: String,
        fontFamily: String,
        color: String,
        fontSize: Float,
        x: Float,
        y: Float,
    ) {
        pushUndo()
        val element =
            CanvasElement.Text(
                id = Uuid.random(),
                content = content,
                fontFamily = fontFamily,
                color = color,
                fontSize = fontSize,
                transform = ElementTransform(x = x, y = y),
                zIndex = nextZIndex(),
            )
        updateDocument { doc -> doc.copy(elements = doc.elements + element) }
    }

    // --- Tool selection ---

    fun setActiveTool(tool: CanvasTool) {
        _state.update { it.copy(activeTool = tool) }
    }

    // --- Shelf ---

    fun setShelfMode(mode: ShelfMode) {
        _state.update { it.copy(shelfMode = mode) }
    }

    /**
     * Loads photos from a moment onto the shelf.
     */
    fun loadShelfPhotos(photos: List<ShelfPhoto>) {
        _state.update { it.copy(shelfPhotos = photos) }
    }

    // --- Background ---

    fun setBackground(background: CanvasBackground) {
        pushUndo()
        updateDocument { doc -> doc.copy(background = background) }
    }

    // --- Undo / Redo ---

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.document)
        _state.update { it.copy(document = previous) }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.document)
        _state.update { it.copy(document = next) }
    }

    private fun pushUndo() {
        undoStack.addLast(_state.value.document)
        redoStack.clear()
        // Limit undo history
        if (undoStack.size > MAX_UNDO_HISTORY) {
            undoStack.removeFirst()
        }
    }

    // --- Persistence ---

    /**
     * Saves the current Postcard to the database.
     */
    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val doc =
                _state.value.document.copy(
                    modifiedAt = Clock.System.now(),
                )
            val entity =
                PostcardEntity(
                    id = doc.id,
                    title = doc.title.ifEmpty { "Untitled" },
                    createdAt = doc.createdAt,
                    modifiedAt = doc.modifiedAt,
                    sourceMomentRef = doc.sourceMomentRef,
                    documentJson = json.encodeToString(PostcardDocument.serializer(), doc),
                )
            if (_state.value.isNewPostcard) {
                postcardDao.insert(entity)
            } else {
                postcardDao.update(entity)
            }
            _state.update { it.copy(document = doc, isSaving = false, isNewPostcard = false) }
        }
    }

    fun setTitle(title: String) {
        updateDocument { doc -> doc.copy(title = title) }
    }

    // --- Helpers ---

    private fun updateDocument(transform: (PostcardDocument) -> PostcardDocument) {
        _state.update { it.copy(document = transform(it.document)) }
    }

    private fun nextZIndex(): Int {
        val maxZ =
            _state.value.document.elements
                .maxOfOrNull { it.zIndex } ?: -1
        return maxZ + 1
    }

    companion object {
        private const val MAX_UNDO_HISTORY = 50
    }
}

/**
 * Returns a copy of this element with a new transform.
 */
private fun CanvasElement.withTransform(newTransform: ElementTransform): CanvasElement =
    when (this) {
        is CanvasElement.Photo -> copy(transform = newTransform)
        is CanvasElement.Text -> copy(transform = newTransform)
        is CanvasElement.Ink -> copy(transform = newTransform)
        is CanvasElement.Shape -> copy(transform = newTransform)
        is CanvasElement.Sticker -> copy(transform = newTransform)
    }
