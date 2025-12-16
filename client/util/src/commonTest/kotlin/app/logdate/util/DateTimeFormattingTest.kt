package app.logdate.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Tests for the date and time formatting utilities.
 * 
 * These tests verify that all formatting functions work correctly across platforms,
 * handling different locales, time zones, and edge cases appropriately.
 */
class DateTimeFormattingTest {
    
    /**
     * Tests [formatDateLocalized] for correct localized date formatting.
     * 
     * Verifies that:
     * - The formatted string is not empty or null
     * - The year appears in the formatted output
     * - The month appears either as a number or name
     * 
     * Since actual formatting depends on platform locale settings,
     * we can't test for exact output format.
     */
    @Test
    fun testFormatDateLocalized() {
        val testDate = LocalDate(2025, 6, 5)
        val formattedDate = formatDateLocalized(testDate)
        
        assertNotNull(formattedDate)
        assertTrue(formattedDate.isNotEmpty(), "Formatted date should not be empty")
        
        // The date should contain the year
        assertTrue(formattedDate.contains("2025"), "Formatted date should contain the year 2025")
        
        // The date should contain the month (either as number or name)
        assertTrue(formattedDate.contains("6") || 
                  formattedDate.lowercase().contains("june") ||
                  formattedDate.lowercase().contains("jun"), 
                 "Formatted date should contain the month (June/Jun or 6)")
    }
    
    /**
     * Tests the [Instant.asTime] extension property.
     * 
     * Verifies that:
     * - The formatted time string is not empty or null
     * - It contains a time separator (usually ":")
     * 
     * The exact formatting depends on platform locale settings.
     */
    @Test
    fun testInstantAsTime() {
        val now = Clock.System.now()
        val timeString = now.asTime
        
        assertNotNull(timeString)
        assertTrue(timeString.isNotEmpty(), "Time string should not be empty")
        
        // Should contain either colon (for 12-hour format) or some time separator
        assertTrue(timeString.contains(":"), "Time string should contain a time separator")
    }
    
    /**
     * Tests [LocalDate.now] extension function.
     * 
     * Verifies that the date returned by LocalDate.now() matches
     * the current system date in terms of year, month, and day.
     */
    @Test
    fun testLocalDateNow() {
        val today = LocalDate.now()
        val systemNow = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        
        assertEquals(systemNow.year, today.year, "Year should match system time")
        assertEquals(systemNow.monthNumber, today.monthNumber, "Month should match system time")
        assertEquals(systemNow.dayOfMonth, today.dayOfMonth, "Day should match system time")
    }
    
    /**
     * Tests [LocalDateTime.weekOfYear] extension property.
     * 
     * Verifies week number calculations follow ISO 8601 standard:
     * - First week containing Thursday, January 2, 2025 is week 1
     * - Mid-year date (June 15, 2025) is correctly identified as week 24
     * - End-of-year date (December 31, 2025) is correctly handled
     * 
     * The ISO 8601 standard defines the first week as containing the first Thursday
     * of the year, and December 31 can be either in the last week of the current
     * year or the first week of the next year.
     */
    @Test
    fun testWeekOfYear() {
        // Test first week of the year (ISO week date standard)
        val firstWeek = LocalDateTime(2025, 1, 2, 0, 0)
        assertEquals(1, firstWeek.weekOfYear, "January 2, 2025 should be week 1")
        
        // Test middle of the year
        // 2025-06-15 is a Sunday in the 24th week of 2025 per ISO 8601
        val midYear = LocalDateTime(2025, 6, 15, 0, 0)
        assertEquals(24, midYear.weekOfYear, "June 15, 2025 should be week 24 (per ISO 8601)") 
        
        // Test end of year - for 2025, December 31 is a Wednesday
        // According to the current implementation, this is week 1 of 2026
        // This matches ISO 8601 which states: if the week containing January 1 
        // has four or more days in the new year, then it is week 1; otherwise, it is the last week
        // of the previous year, and the next week is week 1.
        val endYear = LocalDateTime(2025, 12, 31, 0, 0)
        
        // Looking at the current implementation, we expect week 1 since our calculation puts it in week 1
        assertEquals(1, endYear.weekOfYear, "Dec 31, 2025 should be week 1 of 2026 (per current implementation)")
    }
    
    /**
     * Tests [Instant.daysUntilNow] extension property.
     * 
     * Verifies that:
     * - A timestamp from yesterday returns 1 day
     * - The current timestamp returns 0 days
     */
    @Test
    fun testDaysUntilNow() {
        // Test with a date from yesterday
        val yesterday = Clock.System.now().minus(1.days)
        assertEquals(1, yesterday.daysUntilNow, "Yesterday should be 1 day until now")
        
        // Test with current time
        val justNow = Clock.System.now()
        assertEquals(0, justNow.daysUntilNow, "Current time should be 0 days until now")
    }
    
    /**
     * Tests [Instant.weeksAgo] extension function.
     * 
     * Verifies that:
     * - A timestamp from 3 weeks ago returns 3 weeks
     * - A timestamp from less than a week ago returns 0 weeks
     * 
     * This confirms the function rounds down to the nearest week.
     */
    @Test
    fun testWeeksAgo() {
        // Test with a date from 3 weeks ago
        val threeWeeksAgo = Clock.System.now().minus(21.days)
        assertEquals(3, threeWeeksAgo.weeksAgo(), "Three weeks ago should return 3")
        
        // Test with a date from 6 days ago (less than a week)
        val sixDaysAgo = Clock.System.now().minus(6.days)
        assertEquals(0, sixDaysAgo.weeksAgo(), "Six days ago should return 0 weeks")
    }
    
    /**
     * Tests [LocalDate.toReadableDateShort] extension function.
     * 
     * Verifies that:
     * - The formatted date contains the month name
     * - The formatted date contains the day number
     * - The year is only included if it's different from the current year
     * 
     * This ensures consistent human-readable date formatting.
     */
    @Test
    fun testToReadableDateShort() {
        val date = LocalDate(2025, 3, 15)
        val result = date.toReadableDateShort()
        
        assertTrue(result.contains("March"), "Should contain month name")
        assertTrue(result.contains("15"), "Should contain day number")
        
        // Current year shouldn't include the year number
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        if (date.year == currentYear) {
            assertTrue(!result.contains("2025"), "Current year dates shouldn't show year")
        } else {
            assertTrue(result.contains("2025"), "Non-current year dates should show year")
        }
    }
}