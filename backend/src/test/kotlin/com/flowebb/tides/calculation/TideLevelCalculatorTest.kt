package com.flowebb.tides.calculation

import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationSource
import com.flowebb.tides.station.StationType
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TideLevelCalculatorTest {
    private val calculator = TideLevelCalculator()

    // Create a test station to use
    private val testStation = Station(
        id = "TEST1",
        name = "Test Station",
        state = "WA",
        region = "Test Region",
        distance = 0.0,
        latitude = 47.0,
        longitude = -122.0,
        source = StationSource.NOAA,
        capabilities = setOf(StationType.WATER_LEVEL)
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
    fun `getCurrentTideLevel returns valid TideLevel object`() = runBlocking {
        val tideLevel = calculator.getCurrentTideLevel(testStation, 0L)
        assertNotNull(tideLevel)
        assertTrue(tideLevel.waterLevel >= 0)
        assertEquals(tideLevel.waterLevel, tideLevel.predictedLevel)
        assertNotNull(tideLevel.type)
    }
}
