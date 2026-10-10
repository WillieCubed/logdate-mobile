package app.logdate.client.appfunctions

import app.logdate.client.text.oneLineExcerpt

/** How many entries one assistant search returns at most. */
const val ASSISTANT_SEARCH_LIMIT = 10

/** The longest excerpt of an entry an assistant receives. */
const val ASSISTANT_EXCERPT_LENGTH = 280

/**
 * Why an assistant may not read the journal right now. Each message tells the assistant what to say
 * to the person, so a refusal is never a dead end.
 */
enum class AssistantReadRefusal(
    val message: String,
) {
    /** The person has not let Android search surfaces see their entries. */
    SystemSearchOff(
        "LogDate only lets assistants read journals and entries when the person turns on " +
            "system search visibility in LogDate Settings > Privacy.",
    ),

    /** LogDate's app lock is on and the person has not unlocked it in this session. */
    Locked("LogDate is locked. The person needs to open LogDate and unlock it first."),
}

/**
 * Whether an assistant may read the journal: its entries or its journals' titles. Reading needs the
 * same consent as Android search (system search visibility) and an unlocked app; writing a new entry
 * needs neither, because it reveals nothing.
 *
 * @return why reading is refused, or null when it is allowed.
 */
fun assistantReadRefusal(
    systemSearchVisible: Boolean,
    unlocked: Boolean,
): AssistantReadRefusal? =
    when {
        !systemSearchVisible -> AssistantReadRefusal.SystemSearchOff
        !unlocked -> AssistantReadRefusal.Locked
        else -> null
    }

/**
 * Whether LogDate counts as unlocked for an assistant. With the app lock on, only an unlock in this
 * process counts: a process Android starts just to answer an assistant has never asked.
 */
fun assistantUnlocked(
    appLockOn: Boolean,
    authenticated: Boolean,
): Boolean = !appLockOn || authenticated

/** An entry's text as an assistant receives it: on one line, shortened with an ellipsis when long. */
fun assistantExcerpt(
    content: String,
    maxLength: Int = ASSISTANT_EXCERPT_LENGTH,
): String = oneLineExcerpt(content, maxLength)
