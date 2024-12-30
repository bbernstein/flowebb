package com.flowebb.tides

import com.flowebb.tides.cache.CachedExtreme
import com.flowebb.tides.cache.CachedPrediction
import com.flowebb.tides.cache.TidePredictionCache
import com.flowebb.tides.cache.TidePredictionRecord
import com.flowebb.tides.calculation.TideExtreme
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.calculation.TideType
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationService
import com.flowebb.tides.station.StationSource
import com.flowebb.tides.station.StationType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TideServiceTest : DynamoTestBase() {
    private val mockStation =
        Station(
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
            stationType = "R",
        )

    private val mockStationService = mockk<StationService>()
    private val mockCalculator = mockk<TideLevelCalculator>()
    private val mockCache = mockk<TidePredictionCache>()
    private lateinit var service: TideService

    @BeforeTest
    fun setup() {
        service = TideService(mockStationService, mockCalculator, mockCache)

        // Setup mock behavior for StationService
        coEvery {
            mockStationService.findNearestStations(
                latitude = any(),
                longitude = any(),
                limit = any(),
                preferredSource = any(),
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
        } returns
            listOf(
                TidePredictionRecord(
                    stationId = "TEST1",
                    date = "2024-12-15",
                    stationType = "R",
                    predictions =
                    listOf(
                        CachedPrediction(
                            timestamp = 1734567890000,
                            height = 5.0,
                        ),
                    ),
                    extremes =
                    listOf(
                        CachedExtreme(
                            timestamp = 1734567890000,
                            height = 8.0,
                            type = "HIGH",
                        ),
                    ),
                ),
            )

        coEvery {
            mockCalculator.interpolatePredictions(any(), any())
        } returns 5.0

        coEvery {
            mockCalculator.interpolateExtremes(any(), any())
        } returns 8.0

        // Setup mock behavior for TidePredictionCache
        every {
            mockCache.convertToPredictions(any())
        } returns
            listOf(
                TidePrediction(
                    timestamp = 1734567890000,
                    height = 5.0,
                ),
            )

        every {
            mockCache.convertToExtremes(any())
        } returns
            listOf(
                TideExtreme(
                    type = TideType.HIGH,
                    timestamp = 1734567890000,
                    height = 8.0,
                ),
            )
    }

    @Test
    fun `getCurrentTide returns valid ExtendedTideResponse`() =
        runBlocking {
            val result = service.getCurrentTide(47.0, -122.0, null, null)

            assertEquals("TEST1", result.nearestStation)
            assertEquals("Test Station", result.location)
            assertEquals(10.5, result.stationDistance)
            assertEquals("NOAA API", result.calculationMethod)
            assertNotNull(result.predictions)
            assertNotNull(result.extremes)
            assertEquals(-8 * 3600, result.timeZoneOffsetSeconds)
        }

    @Test
    fun `getCurrentTideForStation returns valid ExtendedTideResponse`() =
        runBlocking {
            val result = service.getCurrentTideForStation("TEST1", null, null)

            assertEquals("TEST1", result.nearestStation)
            assertEquals("Test Station", result.location)
            assertEquals("NOAA API", result.calculationMethod)
            assertNotNull(result.predictions)
            assertNotNull(result.extremes)
            assertEquals(-8 * 3600, result.timeZoneOffsetSeconds)
        }
}
