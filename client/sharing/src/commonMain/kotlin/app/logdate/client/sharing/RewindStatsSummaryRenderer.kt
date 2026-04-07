package app.logdate.client.sharing

/**
 * One stats summary destined for a single shareable card.
 *
 * Field naming follows what the renderer draws, not what the data layer holds: every string
 * is already localized by the time it reaches this object so the renderer never has to know
 * about resources or compose context.
 *
 * @property title The rewind's user-facing title (a synthesized line, never a template).
 * @property subtitle A short context line under the title — typically the date range.
 * @property counters Numeric stats drawn as big-number blocks (entries, photos, people…).
 * @property highlights Short prose lines drawn beneath the counters (leading theme, where).
 * @property accentSeed Per-card hue seed so consecutive summaries don't look identical.
 */
data class RewindStatsSummary(
    val title: String,
    val subtitle: String,
    val counters: List<Counter>,
    val highlights: List<Highlight>,
    val accentSeed: Int = 0,
) {
    data class Counter(
        val label: String,
        val count: Int,
    )

    data class Highlight(
        val heading: String,
        val value: String,
    )
}

/**
 * Renders a [RewindStatsSummary] as a styled bitmap that can be attached to a share intent.
 *
 * Mirrors the [RewindQuoteCardRenderer] pattern: implementations are platform-specific, the
 * result is a content URI string the platform's share sheet can attach as media, and null
 * means rendering is unavailable on this platform (or failed). Callers should fall back to
 * the plain-text share path on null.
 */
interface RewindStatsSummaryRenderer {
    suspend fun render(summary: RewindStatsSummary): String?
}

/**
 * No-op renderer for platforms that don't yet have a stats summary renderer. Always returns
 * null so callers fall back to the plain-text share path.
 */
object NoOpRewindStatsSummaryRenderer : RewindStatsSummaryRenderer {
    override suspend fun render(summary: RewindStatsSummary): String? = null
}
