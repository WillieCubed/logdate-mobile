package app.logdate.client.sharing

/**
 * One quote of the user's own words destined for a styled card.
 *
 * @property text The exact text content from the rewind panel — the user's own writing.
 * @property dateLabel Optional formatted date string ("March 15, 2025"). When present, drawn
 *   quietly beneath the quote so the receiver knows when the moment happened.
 * @property accentSeed Used to derive a per-card hue so different moments in the same week
 *   render in different colors. Pass the panel's `sourceId.hashCode()` or similar; defaults to
 *   a neutral seed when callers don't have a stable identifier.
 */
data class RewindQuote(
    val text: String,
    val dateLabel: String? = null,
    val accentSeed: Int = 0,
)

/**
 * Renders a [RewindQuote] as a styled bitmap that can be attached to a share intent.
 *
 * This is the visual companion to the plain-text share path: when the user shares a text-only
 * rewind moment (a journal note, narrative context, transition), apps that prefer images
 * (Instagram Stories, Notes, Photos) get a beautifully laid-out card of the user's own words
 * instead of having nothing visual to attach.
 *
 * Implementations are platform-specific. The result is a content URI string the platform's
 * share sheet can attach as media, or null when rendering is unavailable on this platform
 * (or fails). Callers should fall back to the plain-text share path on null.
 */
interface RewindQuoteCardRenderer {
    suspend fun render(quote: RewindQuote): String?
}

/**
 * No-op renderer for platforms that don't yet have a quote card renderer. Always returns null
 * so callers fall back to the plain-text share path.
 */
object NoOpRewindQuoteCardRenderer : RewindQuoteCardRenderer {
    override suspend fun render(quote: RewindQuote): String? = null
}
