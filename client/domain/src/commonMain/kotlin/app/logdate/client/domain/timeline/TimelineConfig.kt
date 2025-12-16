package app.logdate.client.domain.timeline

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Configuration for timeline day conceptualization and pagination.
 *
 * This class defines how timeline entries are grouped into "conceptual days" based on
 * natural activity patterns rather than strict calendar days.
 */
data class TimelineConfig(
    /**
     * Default number of days to load in a single pagination request.
     */
    val defaultPageSize: Int = 7,
    
    /**
     * The minimum gap between activities to consider them part of different conceptual days.
     * Default is 6 hours - if there's a 6-hour gap in activity, we consider it a new day.
     */
    val dayBreakThreshold: Duration = 6.hours,
    
    /**
     * Maximum allowed duration for a single conceptual day.
     * This prevents a conceptual day from spanning too many calendar days.
     */
    val maxDayDuration: Duration = 36.hours,
    
    /**
     * Whether to show days with no entries in the timeline.
     */
    val showEmptyDays: Boolean = false,
    
    /**
     * Default timezone to use for date calculations if not specified.
     */
    val defaultTimeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    /**
     * Groups entries into conceptual days based on activity patterns.
     * 
     * @param entries The chronologically sorted list of entries
     * @param timeZone The timezone to use for date calculations
     * @return A list of grouped entries by conceptual day
     */
    fun groupEntriesIntoConceptualDays(
        entries: List<JournalEntry>,
        timeZone: TimeZone = defaultTimeZone
    ): List<ConceptualDay> {
        if (entries.isEmpty()) return emptyList()
        
        val sortedEntries = entries.sortedBy { it.timestamp }
        val conceptualDays = mutableListOf<ConceptualDay>()
        
        var currentDayEntries = mutableListOf(sortedEntries.first())
        var dayStart = sortedEntries.first().timestamp
        
        for (i in 1 until sortedEntries.size) {
            val entry = sortedEntries[i]
            val previousEntry = sortedEntries[i-1]
            val gap = entry.timestamp - previousEntry.timestamp
            val currentDuration = entry.timestamp - dayStart
            
            // Start a new conceptual day if:
            // 1. There's a significant gap in activity, or
            // 2. The current conceptual day has exceeded the maximum duration
            val isNewDay = gap >= dayBreakThreshold || currentDuration >= maxDayDuration
            
            if (isNewDay) {
                // Create a conceptual day from the current group
                val baseDate = dayStart.toLocalDateTime(timeZone).date
                conceptualDays.add(
                    ConceptualDay(
                        entries = currentDayEntries.toList(),
                        baseDate = baseDate,
                        start = dayStart,
                        end = previousEntry.timestamp
                    )
                )
                
                // Start a new group
                currentDayEntries = mutableListOf(entry)
                dayStart = entry.timestamp
            } else {
                currentDayEntries.add(entry)
            }
        }
        
        // Add the last group
        if (currentDayEntries.isNotEmpty()) {
            val baseDate = dayStart.toLocalDateTime(timeZone).date
            conceptualDays.add(
                ConceptualDay(
                    entries = currentDayEntries,
                    baseDate = baseDate,
                    start = dayStart,
                    end = currentDayEntries.last().timestamp
                )
            )
        }
        
        return conceptualDays
    }
}

/**
 * Represents a logical day within the timeline, which may span calendar day boundaries
 * based on natural activity patterns.
 */
data class ConceptualDay(
    /**
     * Entries that belong to this conceptual day.
     */
    val entries: List<JournalEntry>,
    
    /**
     * The calendar date that serves as the primary reference for this conceptual day.
     * Usually the date of the first entry in the conceptual day.
     */
    val baseDate: LocalDate,
    
    /**
     * The timestamp of the first entry in this conceptual day.
     */
    val start: Instant,
    
    /**
     * The timestamp of the last entry in this conceptual day.
     */
    val end: Instant
)

/**
 * Interface representing an entry in the journal with a timestamp.
 */
interface JournalEntry {
    val timestamp: Instant
}