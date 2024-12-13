package com.flowebb.tides

import com.flowebb.tides.calculation.*
import com.flowebb.tides.station.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TideServiceTest {
    private class MockStationService : StationService(emptyMap()) {
        override suspend fun findNearestStations(
            latitude: Double,
            longitude: Double,
            limit: Int,
            preferredSource: StationSource?
        ): List<Station> {
            return listOf(Station(
                id = "TEST1",
                name = "Test Station",
                state = "WA",
                region = "Puget Sound",
                distance = 10.5,
                latitude = latitude,
                longitude = longitude,
                source = StationSource.NOAA,
                capabilities = setOf(StationType.WATER_LEVEL)
            ))
        }

        override suspend fun getStation(stationId: String): Station {
            return Station(
                id = stationId,
                name = "Test Station",
                state = "WA",
                region = "Puget Sound",
                distance = 10.5,
                latitude = 47.0,
                longitude = -122.0,
                source = StationSource.NOAA,
                capabilities = setOf(StationType.WATER_LEVEL)
            )
        }
    }

    private class MockTideLevelCalculator : TideLevelCalculator() {
        override suspend fun getCurrentTideLevel(
            station: Station,
            timestamp: Long,
            forceHarmonicCalculation: Boolean
        ): TideLevel {
            return TideLevel(
                waterLevel = 5.0,
                predictedLevel = 5.0,
                type = TideType.RISING
            )
        }
    }

    @Test
    fun `getCurrentTide returns valid TideResponse`() = runBlocking {
        val service = TideService(
            stationService = MockStationService(),
            calculator = MockTideLevelCalculator()
        )

        val result = service.getCurrentTide(47.0, -122.0)

        assertEquals("TEST1", result.nearestStation)
        assertEquals("Test Station", result.location)
        assertEquals(5.0, result.waterLevel)
        assertEquals(5.0, result.predictedLevel)
        assertEquals(TideType.RISING, result.type)
        assertEquals(10.5, result.stationDistance)
        assertEquals("NOAA API", result.calculationMethod)
    }
}
