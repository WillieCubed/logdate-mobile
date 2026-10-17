package app.logdate.client.sharing

import kotlinx.datetime.LocalDate
import logdate.client.sharing.generated.resources.Res
import logdate.client.sharing.generated.resources.share_memory_text
import logdate.client.sharing.generated.resources.share_memory_text_with_summary
import org.jetbrains.compose.resources.getString

/**
 * The text shared with a memory from [date], in the current locale: the [summary] when there is one,
 * otherwise an invitation to look at the memory in LogDate.
 */
internal suspend fun memoryDayShareText(
    date: LocalDate,
    summary: String,
): String =
    if (summary.isNotBlank()) {
        getString(Res.string.share_memory_text_with_summary, date.toString(), summary)
    } else {
        getString(Res.string.share_memory_text, date.toString())
    }
