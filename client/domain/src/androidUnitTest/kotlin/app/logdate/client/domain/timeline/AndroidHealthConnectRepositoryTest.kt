package app.logdate.client.domain.timeline

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidHealthConnectRepositoryTest {

    private val mockContext = mockk<Context>()
    private val mockHealthConnectClient = mockk<HealthConnectClient>()
    private val mockPermissionController = mockk<PermissionController>()
    private val mockReadRecordsResponse = mockk<ReadRecordsResponse<SleepSessionRecord>>()

    private lateinit var repository: AndroidHealthConnectRepository
    private val testDate = LocalDate(2023, 6, 15)
    private val testTimeZone = TimeZone.of("UTC")
    private val javaZoneId = java.time.ZoneId.of("UTC")

    private var sdkStatusProvider: (Context) -> Int = { HealthConnectClient.SDK_AVAILABLE }
    private var healthConnectClientProvider: (Context) -> HealthConnectClient? = { mockHealthConnectClient }

    @BeforeTest
    fun setUp() {
        every { mockHealthConnectClient.permissionController } returns mockPermissionController
        repository = buildRepository()
    }

    @AfterTest
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `isHealthConnectAvailable returns true when SDK is available`() = runTest {
        // Given
        sdkStatusProvider = { HealthConnectClient.SDK_AVAILABLE }
        repository = buildRepository()

        // When
        val result = repository.isHealthConnectAvailable()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isHealthConnectAvailable returns false when SDK is unavailable`() = runTest {
        // Given
        sdkStatusProvider = { HealthConnectClient.SDK_UNAVAILABLE }
        repository = buildRepository()

        // When
        val result = repository.isHealthConnectAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isHealthConnectAvailable returns false when exception occurs`() = runTest {
        // Given
        sdkStatusProvider = { throw RuntimeException("Test exception") }
        repository = buildRepository()

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
        coEvery { mockPermissionController.getGrantedPermissions() } returns emptySet<String>()

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
        healthConnectClientProvider = { null }
        repository = buildRepository()

        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)

        // Then
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
        coEvery { mockPermissionController.getGrantedPermissions() } returns emptySet<String>()

        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)

        // Then
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

        val sleepSessions = createSampleSleepSessions()

        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns sleepSessions

        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)

        // Then
        val startHour = result.start.toJavaInstant().atZone(javaZoneId).hour
        val endHour = result.end.toJavaInstant().atZone(javaZoneId).hour

        assertTrue(startHour in 5..6)
        assertTrue(endHour in 22..23)
    }

    @Test
    fun `getDayBoundsForDate handles early risers properly`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions

        val sleepSessions = createSampleSleepSessions()
        val earlyRiserSessions = createEarlyRiserSleepSessions()
        val mixedSessions = sleepSessions + earlyRiserSessions

        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns mixedSessions

        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)

        // Then
        val startHour = result.start.toJavaInstant().atZone(javaZoneId).hour
        assertTrue(startHour in 4..5)
    }

    @Test
    fun `getDayBoundsForDate enforces 4am minimum start time`() = runTest {
        // Given
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val grantedPermissions = setOf(sleepPermission)
        coEvery { mockPermissionController.getGrantedPermissions() } returns grantedPermissions

        val sleepSessions = createExtremeEarlyRiserSleepSessions()

        coEvery { mockHealthConnectClient.readRecords(any<ReadRecordsRequest<SleepSessionRecord>>()) } returns mockReadRecordsResponse
        every { mockReadRecordsResponse.records } returns sleepSessions

        // When
        val result = repository.getDayBoundsForDate(testDate, testTimeZone)

        // Then
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

    private fun buildRepository(): AndroidHealthConnectRepository {
        return AndroidHealthConnectRepository(
            context = mockContext,
            healthConnectClientProvider = { ctx -> healthConnectClientProvider(ctx) },
            sdkStatusProvider = { ctx -> sdkStatusProvider(ctx) }
        )
    }

    private fun createSampleSleepSessions(): List<SleepSessionRecord> {
        val sessions = mutableListOf<SleepSessionRecord>()
        val baseDate = testDate.toJavaLocalDate().minusDays(30)

        for (i in 0 until 10) {
            val date = baseDate.plusDays(i.toLong())

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
        val baseDate = testDate.toJavaLocalDate().minusDays(15)

        for (i in 0 until 3) {
            val date = baseDate.plusDays(i.toLong())

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
        val baseDate = testDate.toJavaLocalDate().minusDays(10)

        for (i in 0 until 5) {
            val date = baseDate.plusDays(i.toLong())

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
