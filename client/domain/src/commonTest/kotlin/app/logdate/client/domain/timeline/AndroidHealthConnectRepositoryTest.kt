package app.logdate.client.domain.timeline

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidHealthConnectRepositoryTest {

    private val mockContext = mockk<Context>()
    private val mockHealthConnectClient = mockk<HealthConnectClient>()
    private val mockPermissionController = mockk<HealthConnectClient.PermissionController>()
    private val mockReadRecordsResponse = mockk<ReadRecordsResponse<SleepSessionRecord>>()
    
    private lateinit var repository: AndroidHealthConnectRepository
    private val testDate = LocalDate(2023, 6, 15)
    private val testTimeZone = TimeZone.of("UTC")
    private val javaZoneId = java.time.ZoneId.of("UTC")

    @BeforeTest
    fun setUp() {
        // Set up static mocks
        mockkStatic(HealthConnectClient::class)
        
        // Set up mocks
        every { HealthConnectClient.getOrCreate(mockContext) } returns mockHealthConnectClient
        every { mockHealthConnectClient.permissionController } returns mockPermissionController
        
        // Set up repository
        repository = AndroidHealthConnectRepository(mockContext)
    }

    @AfterTest
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `isHealthConnectAvailable returns true when SDK is available`() = runTest {
        // Given
        every { HealthConnectClient.sdkStatus(mockContext) } returns HealthConnectClient.SDK_AVAILABLE
        
        // When
        val result = repository.isHealthConnectAvailable()
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `isHealthConnectAvailable returns false when SDK is unavailable`() = runTest {
        // Given
        every { HealthConnectClient.sdkStatus(mockContext) } returns HealthConnectClient.SDK_UNAVAILABLE
        
        // When
        val result = repository.isHealthConnectAvailable()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `isHealthConnectAvailable returns false when exception occurs`() = runTest {
        // Given
        every { HealthConnectClient.sdkStatus(mockContext) } throws RuntimeException("Test exception")
        
        // When
        val result = repository.isHealthConnectAvailable()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `hasSleepPermissions returns true when all permissions granted`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        
        // When
        val result = repository.hasSleepPermissions()
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `hasSleepPermissions returns false when permissions not granted`() = runTest {
        // Given
        coEvery { mockPermissionController.getGrantedPermissions() } returns emptySet()
        
        // When
        val result = repository.hasSleepPermissions()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `hasSleepPermissions returns false when exception occurs`() = runTest {
        // Given
        coEvery { mockPermissionController.getGrantedPermissions() } throws RuntimeException("Test exception")
        
        // When
        val result = repository.hasSleepPermissions()
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `requestSleepPermissions always returns false`() = runTest {
        // When
        val result = repository.requestSleepPermissions()
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `getDayBoundsForDate returns default bounds when HealthConnectClient is null`() = runTest {
        // Given
        every { HealthConnectClient.getOrCreate(mockContext) } throws RuntimeException("Test exception")
        val repository = AndroidHealthConnectRepository(mockContext)
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // Check that the result matches default bounds (5am to midnight)
        val javaDate = testDate.toJavaLocalDate()
        val expectedStart = javaDate.atTime(5, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        val nextDay = javaDate.plusDays(1)
        val expectedEnd = nextDay.atStartOfDay()
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
            
        assertEquals(expectedStart, result.start)
        assertEquals(expectedEnd, result.end)
    }
    
    @Test
    fun `getDayBoundsForDate returns default bounds when permissions not granted`() = runTest {
        // Given
        coEvery { mockPermissionController.getGrantedPermissions() } returns emptySet()
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // Check that the result matches default bounds (5am to midnight)
        val javaDate = testDate.toJavaLocalDate()
        val expectedStart = javaDate.atTime(5, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        val nextDay = javaDate.plusDays(1)
        val expectedEnd = nextDay.atStartOfDay()
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
            
        assertEquals(expectedStart, result.start)
        assertEquals(expectedEnd, result.end)
    }
    
    @Test
    fun `getDayBoundsForDate returns default bounds when no sleep data available`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns emptyList()
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // Check that the result matches default bounds (5am to midnight)
        val javaDate = testDate.toJavaLocalDate()
        val expectedStart = javaDate.atTime(5, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        val nextDay = javaDate.plusDays(1)
        val expectedEnd = nextDay.atStartOfDay()
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
            
        assertEquals(expectedStart, result.start)
        assertEquals(expectedEnd, result.end)
    }
    
    @Test
    fun `getDayBoundsForDate calculates bounds based on sleep data`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        
        // Create sample sleep sessions for past 30 days with consistent wake and sleep times
        val sleepSessions = createSampleSleepSessions()
        
        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns sleepSessions
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // The average wake time should be 7am, but with the early riser adjustment
        // we should get a time closer to 6am for the start
        val javaDate = testDate.toJavaLocalDate()
        
        // Expected start time is earlier than 7am to account for early risers
        val expectedStart = javaDate.atTime(6, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        // Sleep time is consistently 23:00 (11pm)
        val expectedEnd = javaDate.atTime(23, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        // We don't expect exact matches due to averaging logic, but we can check that
        // the bounds are reasonably close to our expectations
        val startHour = result.start.toJavaInstant().atZone(javaZoneId).hour
        val endHour = result.end.toJavaInstant().atZone(javaZoneId).hour
        
        // Start should be close to 6am
        assertTrue(startHour in 5..6)
        
        // End should be close to 11pm
        assertTrue(endHour in 22..23)
    }
    
    @Test
    fun `getDayBoundsForDate handles early risers properly`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        
        // Create sample sleep sessions with some early wake-ups at 4:30am
        val sleepSessions = createSampleSleepSessions()
        val earlyRiserSessions = createEarlyRiserSleepSessions()
        val mixedSessions = sleepSessions + earlyRiserSessions
        
        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns mixedSessions
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // The early riser detection (15th percentile) should influence the start time
        val startHour = result.start.toJavaInstant().atZone(javaZoneId).hour
        val startMinute = result.start.toJavaInstant().atZone(javaZoneId).minute
        
        // Start time should be influenced by early risers but not earlier than 4am
        assertTrue(startHour in 4..5)
    }
    
    @Test
    fun `getDayBoundsForDate enforces 4am minimum start time`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        
        // Create sample sleep sessions with extremely early wake-ups at 2am
        val sleepSessions = createExtremeEarlyRiserSleepSessions()
        
        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns sleepSessions
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // Despite very early wake times, the minimum should be enforced at 4am
        val startHour = result.start.toJavaInstant().atZone(javaZoneId).hour
        
        assertEquals(4, startHour)
    }
    
    @Test
    fun `getDayBoundsForDate handles exceptions when reading sleep data`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions
        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } throws RuntimeException("Test exception")
        
        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)
        
        // Then
        // Should fall back to default bounds (5am to midnight)
        val javaDate = testDate.toJavaLocalDate()
        val expectedStart = javaDate.atTime(5, 0)
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
        
        val nextDay = javaDate.plusDays(1)
        val expectedEnd = nextDay.atStartOfDay()
            .atZone(javaZoneId)
            .toInstant()
            .toKotlinInstant()
            
        assertEquals(expectedStart, result.start)
        assertEquals(expectedEnd, result.end)
    }
    
    // Helper methods to create test data
    private fun createSampleSleepSessions(): List<SleepSessionRecord> {
        val sessions = mutableListOf<SleepSessionRecord>()
        
        // Create 10 sample sleep sessions with wake time at 7am and sleep time at 11pm
        val baseDate = testDate.toJavaLocalDate().minusDays(30)
        
        for (i in 0 until 10) {
            val date = baseDate.plusDays(i.toLong())
            
            // Sleep from 11pm to 7am next day
            val startTime = date.atTime(23, 0)
                .atZone(javaZoneId)
                .toInstant()
            
            val endTime = date.plusDays(1)
                .atTime(7, 0)
                .atZone(javaZoneId)
                .toInstant()
            
            val metadata = mockk<Metadata>()
            
            val session = SleepSessionRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = null,
                endZoneOffset = null,
                stages = emptyList(),
                notes = null,
                metadata = metadata
            )
            
            sessions.add(session)
        }
        
        return sessions
    }
    
    private fun createEarlyRiserSleepSessions(): List<SleepSessionRecord> {
        val sessions = mutableListOf<SleepSessionRecord>()
        
        // Create 3 sample sleep sessions with early wake time at 4:30am
        val baseDate = testDate.toJavaLocalDate().minusDays(15)
        
        for (i in 0 until 3) {
            val date = baseDate.plusDays(i.toLong())
            
            // Sleep from 10:30pm to 4:30am next day
            val startTime = date.atTime(22, 30)
                .atZone(javaZoneId)
                .toInstant()
            
            val endTime = date.plusDays(1)
                .atTime(4, 30)
                .atZone(javaZoneId)
                .toInstant()
            
            val metadata = mockk<Metadata>()
            
            val session = SleepSessionRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = null,
                endZoneOffset = null,
                stages = emptyList(),
                notes = null,
                metadata = metadata
            )
            
            sessions.add(session)
        }
        
        return sessions
    }
    
    private fun createExtremeEarlyRiserSleepSessions(): List<SleepSessionRecord> {
        val sessions = mutableListOf<SleepSessionRecord>()
        
        // Create sample sleep sessions with extremely early wake time at 2am
        val baseDate = testDate.toJavaLocalDate().minusDays(10)
        
        for (i in 0 until 5) {
            val date = baseDate.plusDays(i.toLong())
            
            // Sleep from 10pm to 2am next day
            val startTime = date.atTime(22, 0)
                .atZone(javaZoneId)
                .toInstant()
            
            val endTime = date.plusDays(1)
                .atTime(2, 0)
                .atZone(javaZoneId)
                .toInstant()
            
            val metadata = mockk<Metadata>()
            
            val session = SleepSessionRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = null,
                endZoneOffset = null,
                stages = emptyList(),
                notes = null,
                metadata = metadata
            )
            
            sessions.add(session)
        }
        
        return sessions
    }
}