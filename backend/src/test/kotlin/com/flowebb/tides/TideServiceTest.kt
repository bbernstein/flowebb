package com.flowebb.tides

import com.flowebb.tides.calculation.*
import com.flowebb.tides.station.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
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
            mockCalculator.getCurrentTideLevel(any(), any())
        } returns TideLevel(
            waterLevel = 5.0,
            predictedLevel = 5.0,
            type = TideType.RISING
        )

        coEvery {
            mockCalculator.findExtremes(any(), any(), any())
        } returns listOf(
            TideExtreme(
                type = TideType.HIGH,
                timestamp = 1234567890000,
                height = 8.0
            )
        )

        coEvery {
            mockCalculator.getPredictions(any(), any(), any(), any())
        } returns listOf(
            TidePrediction(
                timestamp = 1234567890000,
                height = 5.0
            )
        )

        every {
            mockCalculator.getStationZoneId(any())
        } returns ZoneId.of("America/Los_Angeles")
    }

    @Test
    fun `getCurrentTide returns valid ExtendedTideResponse`() = runBlocking {
        val result = service.getCurrentTide(47.0, -122.0)

        assertEquals("TEST1", result.nearestStation)
        assertEquals("Test Station", result.location)
        assertEquals(10.5, result.stationDistance)
        assertEquals("NOAA API", result.calculationMethod)

        // Validate predictions
        assertTrue(result.predictions.isNotEmpty())
        assertEquals(5.0, result.predictions.first().height)

        // Validate extremes
        assertTrue(result.extremes.isNotEmpty())
        assertEquals(TideType.HIGH, result.extremes.first().type)
    }

    @Test
    fun `getCurrentTideForStation returns valid ExtendedTideResponse`() = runBlocking {
        val result = service.getCurrentTideForStation("TEST1")

        assertEquals("TEST1", result.nearestStation)
        assertEquals("Test Station", result.location)
        assertEquals("NOAA API", result.calculationMethod)
    }
}
