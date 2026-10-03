package app.logdate.wear.capture

import androidx.annotation.StringRes
import app.logdate.wear.R

/**
 * The five feelings a mood check-in offers, in the order the check-in screen lists them.
 *
 * This is the only table of feelings on the watch: the check-in screen, the Mood complication, the
 * Today's Summary tile and the timeline read a feeling's tag, emoji and name from here.
 *
 * @property tag What a check-in note stores (see [MomentNoteCodec]). Saved notes already use these
 *   tags, so they never change.
 * @property emojiCodePoint The Unicode code point of the feeling's emoji.
 * @property labelRes The feeling's name, shown next to its emoji.
 */
enum class Feeling(
    val tag: String,
    val emojiCodePoint: Int,
    @param:StringRes val labelRes: Int,
) {
    GREAT(tag = "great", emojiCodePoint = 0x1F604, labelRes = R.string.wear_feeling_great),
    GOOD(tag = "good", emojiCodePoint = 0x1F642, labelRes = R.string.wear_feeling_good),
    OK(tag = "ok", emojiCodePoint = 0x1F610, labelRes = R.string.wear_feeling_ok),
    SAD(tag = "sad", emojiCodePoint = 0x1F614, labelRes = R.string.wear_feeling_sad),
    ROUGH(tag = "rough", emojiCodePoint = 0x1F630, labelRes = R.string.wear_feeling_rough),
    ;

    /** The feeling's emoji as text. */
    val emoji: String get() = String(Character.toChars(emojiCodePoint))

    companion object {
        /** The feeling stored under [tag], or null when no feeling uses it. */
        fun fromTag(tag: String): Feeling? = entries.firstOrNull { it.tag == tag }
    }
}
