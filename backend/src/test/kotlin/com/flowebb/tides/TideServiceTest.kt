package com.flowebb.tides

import com.flowebb.tides.calculation.*
import com.flowebb.tides.station.*
import com.flowebb.tides.cache.*
import java.time.ZoneId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.ZoneOffset
import kotlin.test.*

class TideServiceTest {
    private val mockStation = Station(
        id = "TEST1",
        name = "Test Station",
        state = "WA",
        region = "Puget Sound",
        distance = 10.5,
        latitude = 47.0,
        longitude = -122.0,
        source = StationSource.NOAA,
        capabilities = setOf(StationType.WATER_LEVEL),
        timeZoneOffset = ZoneOffset.ofHours(-8),
        stationType = "R"
    )

    private val mockStationService = mockk<StationService>()
    private val mockCalculator = mockk<TideLevelCalculator>()
    private lateinit var service: TideService

    @BeforeTest
    fun setup() {
        service = TideService(mockStationService, mockCalculator)

        // Setup mock behavior for StationService
        coEvery {
            mockStationService.findNearestStations(
                latitude = any(),
                longitude = any(),
                limit = any(),
                preferredSource = any()
            )
        } returns listOf(mockStation)

        coEvery {
            mockStationService.getStation(any())
        } returns mockStation

        // Setup mock behavior for TideLevelCalculator
        coEvery {
            mockCalculator.getStationZoneId(any())
        } returns ZoneId.of("America/Los_Angeles")

        coEvery {
            mockCalculator.getCachedDayData(any(), any(), any())
        } returns listOf(
            TidePredictionRecord(
                stationId = "TEST1",
                date = "2024-12-15",
                stationType = "R",
                predictions = listOf(
                    CachedPrediction(
                        timestamp = 1734567890000,
                        height = 5.0
                    )
                ),
                extremes = listOf(
                    CachedExtreme(
                        timestamp = 1734567890000,
                        height = 8.0,
                        type = "HIGH"
                    )
                )
            )
        )

        coEvery {
            mockCalculator.interpolatePredictions(any(), any())
        } returns 5.0

        coEvery {
            mockCalculator.interpolateExtremes(any(), any())
        } returns 8.0

        coEvery {
            mockCalculator.determineTideType(any(), any())
        } returns TideType.RISING
    }

    @Test
    fun `getCurrentTide returns valid ExtendedTideResponse`() = runBlocking {
        val result = service.getCurrentTide(47.0, -122.0)

        assertEquals("TEST1", result.nearestStation)
        assertEquals("Test Station", result.location)
        assertEquals(10.5, result.stationDistance)
        assertEquals("NOAA API", result.calculationMethod)
        assertEquals(TideType.RISING, result.tideType)
        assertNotNull(result.predictions)
        assertNotNull(result.extremes)
        assertEquals(-8 * 3600, result.timeZoneOffsetSeconds)
    }

    @Test
    fun `getCurrentTideForStation returns valid ExtendedTideResponse`() = runBlocking {
        val result = service.getCurrentTideForStation("TEST1")

        assertEquals("TEST1", result.nearestStation)
        assertEquals("Test Station", result.location)
        assertEquals("NOAA API", result.calculationMethod)
        assertEquals(TideType.RISING, result.tideType)
        assertNotNull(result.predictions)
        assertNotNull(result.extremes)
        assertEquals(-8 * 3600, result.timeZoneOffsetSeconds)
    }
}
