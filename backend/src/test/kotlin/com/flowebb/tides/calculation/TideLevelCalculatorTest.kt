package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationSource
import com.flowebb.tides.station.StationType
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TideLevelCalculatorTest {
    private val mockHttpClient = mockk<HttpClientService>()
    private val calculator = TideLevelCalculator(mockHttpClient)

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
    fun `determineTideType correctly identifies high tide`() {
        val type = calculator.determineTideType(7.0, 7.0)
        assertEquals(TideType.HIGH, type)
    }

    @Test
    fun `determineTideType correctly identifies low tide`() {
        val type = calculator.determineTideType(2.0, 2.0)
        assertEquals(TideType.LOW, type)
    }

    @Test
    fun `interpolatePredictions correctly interpolates between two predictions`() {
        val predictions = listOf(
            TidePrediction(timestamp = 1000L, height = 5.0),
            TidePrediction(timestamp = 2000L, height = 7.0)
        )

        val interpolated = calculator.interpolatePredictions(predictions, 1500L)
        assertEquals(6.0, interpolated) // Should be halfway between 5.0 and 7.0
    }

    @Test
    fun `interpolateExtremes correctly interpolates between extremes`() {
        val extremes = listOf(
            TideExtreme(type = TideType.LOW, timestamp = 1000L, height = 2.0),
            TideExtreme(type = TideType.HIGH, timestamp = 2000L, height = 8.0),
            TideExtreme(type = TideType.LOW, timestamp = 3000L, height = 1.0)
        )

        val interpolated = calculator.interpolateExtremes(extremes, 1500L)
        assertEquals(6.21875, interpolated, 0.00001) // Exact value for cubic spline interpolation at t=1500
    }
}
