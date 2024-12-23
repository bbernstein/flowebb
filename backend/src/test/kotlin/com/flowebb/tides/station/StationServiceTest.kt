package com.flowebb.tides.station

import com.flowebb.tides.DynamoTestBase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import java.time.ZoneOffset
import kotlin.test.*

class StationServiceTest : DynamoTestBase() {
    private val mockStation = Station(
        id = "TEST1",
        name = "Test Station",
        state = "WA",
        region = "Test Region",
        distance = 0.0,
        latitude = 47.0,
        longitude = -122.0,
        source = StationSource.NOAA,
        capabilities = setOf(StationType.WATER_LEVEL),
        timeZoneOffset = ZoneOffset.ofHours(-8),
        stationType = "R"
    )

    private val noaaFinder = mockk<StationFinder>()
    private val backupFinder = mockk<StationFinder>()
    private lateinit var service: StationService

    @BeforeEach
    fun setup() {
        service = StationService(
            mapOf(
                StationSource.NOAA to noaaFinder,
                StationSource.UKHO to backupFinder
            )
        )
    }

    @Test
    fun `findNearestStations tries all sources when preferred source fails`() = runBlocking {
        // Setup NOAA finder to throw an error
        coEvery {
            noaaFinder.findNearestStations(any(), any(), any())
        } throws Exception("NOAA API unavailable")

        // Setup backup finder to return successfully
        coEvery {
            backupFinder.findNearestStations(any(), any(), any())
        } returns listOf(mockStation)

        val service = StationService(
            mapOf(
                StationSource.NOAA to noaaFinder,
                StationSource.UKHO to backupFinder
            )
        )

        val result = service.findNearestStations(
            47.0, -122.0, 2,
            preferredSource = StationSource.NOAA
        )

        assertEquals(1, result.size)
        assertEquals("TEST1", result.first().id)
    }

    @Test
    fun `findNearestStations returns stations from preferred source when available`() = runBlocking {
        coEvery {
            noaaFinder.findNearestStations(any(), any(), any())
        } returns listOf(mockStation)

        val service = StationService(
            mapOf(
                StationSource.NOAA to noaaFinder,
                StationSource.UKHO to backupFinder
            )
        )

        val result = service.findNearestStations(
            47.0, -122.0, 2,
            preferredSource = StationSource.NOAA
        )

        assertEquals(1, result.size)
        assertEquals("TEST1", result.first().id)
        assertEquals(StationSource.NOAA, result.first().source)
    }

    @Test
    fun `findNearestStations throws exception when no sources available`() {
        val service = StationService(emptyMap())
        runBlocking {
            assertFailsWith<Exception> {
                service.findNearestStations(47.0, -122.0, 2)
            }
        }    }

    @Test
    fun `findNearestStations throws exception when all sources fail`() {
        coEvery {
            noaaFinder.findNearestStations(any(), any(), any())
        } throws Exception("NOAA API unavailable")

        coEvery {
            backupFinder.findNearestStations(any(), any(), any())
        } throws Exception("Backup source unavailable")

        val service = StationService(
            mapOf(
                StationSource.NOAA to noaaFinder,
                StationSource.UKHO to backupFinder
            )
        )

        runBlocking {
            assertFailsWith<Exception> {
                service.findNearestStations(47.0, -122.0, 2)
            }
        }
    }

    @Test
    fun `getStation returns station from first available source`() = runBlocking {
        coEvery {
            noaaFinder.findStation(any())
        } returns mockStation

        val service = StationService(
            mapOf(
                StationSource.NOAA to noaaFinder,
                StationSource.UKHO to backupFinder
            )
        )

        val result = service.getStation("TEST1")
        assertEquals("TEST1", result.id)
        assertEquals(StationSource.NOAA, result.source)
    }
}
