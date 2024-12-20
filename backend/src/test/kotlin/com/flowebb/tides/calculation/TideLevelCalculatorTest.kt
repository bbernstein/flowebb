package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationSource
import com.flowebb.tides.station.StationType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TideLevelCalculatorTest {
    private val mockHttpClient = mockk<HttpClientService>()
    private val calculator = TideLevelCalculator(mockHttpClient)

    private val testStation = Station(
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

    @Test
    fun `determineTideType correctly identifies rising tide`() {
        val type = calculator.determineTideType(5.0, 4.0)
        assertEquals(TideType.RISING, type)
    }

    @Test
    fun `determineTideType correctly identifies falling tide`() {
        val type = calculator.determineTideType(4.0, 5.0)
        assertEquals(TideType.FALLING, type)
    }

    @Test
    fun getCurrentTideLevel() {
        runTest {
            coEvery {
                mockHttpClient.get<List<NoaaPrediction>>(
                    url = match { it.contains("tidesandcurrents.noaa.gov") },
                    headers = any(),
                    queryParams = any(),
                    transform = any()
                )
            } returns listOf(
                NoaaPrediction(
                    t = "2024-12-15 12:00",
                    v = "5.0",
                    type = null
                ),
                NoaaPrediction(
                    t = "2024-12-15 18:15",
                    v = "7.5",
                    type = null
                )
            )
            val testTime = Instant.parse("2024-12-15T15:00:00Z").toEpochMilli()
            val tideLevel = calculator.getCurrentTideLevel(testStation, testTime)

            assertNotNull(tideLevel)
            assertTrue(tideLevel.waterLevel >= 0)
            assertEquals(tideLevel.waterLevel, tideLevel.predictedLevel)
            assertNotNull(tideLevel.type)
        }
    }
}
