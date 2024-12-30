package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TideLevelCalculatorTest {
    private val mockHttpClient = mockk<HttpClientService>()
    private val calculator = TideLevelCalculator(mockHttpClient)

    @Test
    fun `interpolatePredictions correctly interpolates between two predictions`() {
        val predictions =
            listOf(
                TidePrediction(timestamp = 1000L, height = 5.0),
                TidePrediction(timestamp = 2000L, height = 7.0),
            )

        val interpolated = calculator.interpolatePredictions(predictions, 1500L)
        assertEquals(6.0, interpolated) // Should be halfway between 5.0 and 7.0
    }

    @Test
    fun `interpolateExtremes correctly interpolates between extremes`() {
        val extremes =
            listOf(
                TideExtreme(type = TideType.LOW, timestamp = 1000L, height = 2.0),
                TideExtreme(type = TideType.HIGH, timestamp = 2000L, height = 8.0),
                TideExtreme(type = TideType.LOW, timestamp = 3000L, height = 1.0),
            )

        val interpolated = calculator.interpolateExtremes(extremes, 1500L)
        assertEquals(6.21875, interpolated, 0.00001) // Exact value for cubic spline interpolation at t=1500
    }
}
