package app.logdate.client.sharing

/**
 * Visual payload for one non-quote Rewind panel headed to a styled share card.
 *
 * Quote panels are handled by [RewindQuoteCardRenderer]; this renderer covers the rest
 * — image headers, top-list cards, personality cards, narrative-context openings. The
 * card layout is left to the platform implementation; only the data crossing the share
 * boundary lives here.
 *
 * @property kind The structural panel kind. The renderer chooses a layout per kind.
 * @property title Short headline ("Your week was 70% travel", "Top 3 people").
 * @property body Optional secondary text. Lists are passed pre-joined by the caller.
 * @property periodLabel Optional period string ("Week of March 15, 2025"). Drawn quietly
 *   so the recipient knows when the moment happened.
 * @property accentSeed Per-card hue seed so different panels in the same Rewind render
 *   in distinct colors. Use the panel's `sourceId.hashCode()` or similar.
 * @property backgroundImageUri Optional file / content URI to use as the card backdrop
 *   for image-type panels. Renderers without backdrop support draw the [title] solo.
 */
data class RewindPanel(
    val kind: RewindPanelKind,
    val title: String,
    val body: String? = null,
    val periodLabel: String? = null,
    val accentSeed: Int = 0,
    val backgroundImageUri: String? = null,
)

/**
 * Subset of [app.logdate.shared.model.RewindContent] panel kinds this renderer handles.
 *
 * Quote panels are not represented — they go through [RewindQuoteCardRenderer] instead.
 */
enum class RewindPanelKind {
    IMAGE,
    TOP_LIST,
    PERSONALITY,
    NARRATIVE_CONTEXT,
}

/**
 * Renders a [RewindPanel] as a styled bitmap for the share sheet.
 *
 * The result is a content URI string the platform's share sheet can attach as media, or
 * null when rendering is unavailable on this platform (or fails). Callers should fall
 * back to plain-text sharing on null.
 */
interface RewindPanelCardRenderer {
    suspend fun render(panel: RewindPanel): String?
}

/**
 * No-op renderer for platforms that don't have a real implementation yet. Always returns
 * null so callers fall back to plain-text sharing.
 */
object NoOpRewindPanelCardRenderer : RewindPanelCardRenderer {
    override suspend fun render(panel: RewindPanel): String? = null
}
