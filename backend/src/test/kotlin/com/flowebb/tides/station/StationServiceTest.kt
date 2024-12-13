package com.flowebb.tides.station

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class StationServiceTest {
    private class MockStationFinder(private val station: Station?) : StationFinder {
        override suspend fun findNearestStations(latitude: Double, longitude: Double, limit: Int): List<Station> {
            return if (station != null) {
                listOf(station)
            } else {
                throw Exception("No station found")
            }
        }

        override suspend fun findStation(stationId: String): Station {
            return station ?: throw Exception("Station not found")
        }
    }

    @Test
    fun `findNearestStations returns station from preferred source`() = runBlocking {
        val mockStation = Station(
            id = "TEST1",
            name = "Test Station",
            latitude = 47.0,
            longitude = -122.0,
            source = StationSource.NOAA,
            state = "WA",
            region = "Test Region",
            distance = 0.5,
            capabilities = setOf(StationType.WATER_LEVEL)
        )

        val service = StationService(
            mapOf(StationSource.NOAA to MockStationFinder(mockStation))
        )

        val result = service.findNearestStations(47.0, -122.0, 1, StationSource.NOAA)
        assertEquals(mockStation, result.first())
    }

    @Test
    fun `findNearestStations tries all sources when preferred source fails`() = runBlocking {
        val mockStation = Station(
            id = "TEST2",
            name = "Test Station",
            latitude = 47.0,
            longitude = -122.0,
            source = StationSource.UKHO,
            state = "AB",
            region = "Test Region",
            distance = 1.2,
            capabilities = setOf(StationType.WATER_LEVEL)
        )

        val service = StationService(
            mapOf(
                StationSource.NOAA to MockStationFinder(null),
                StationSource.UKHO to MockStationFinder(mockStation)
            )
        )

        val result = service.findNearestStations(47.0, -122.0, 1, StationSource.NOAA)
        assertEquals(mockStation, result.first())
    }
}
