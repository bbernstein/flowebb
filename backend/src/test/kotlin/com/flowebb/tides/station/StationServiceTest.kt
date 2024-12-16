// src/test/kotlin/com/flowebb/tides/station/StationServiceTest.kt
package com.flowebb.tides.station

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class StationServiceTest {
    private class MockStationFinder(
        private val stations: List<Station>,
        private val throwError: Boolean = false
    ) : StationFinder {
        override suspend fun findNearestStations(
            latitude: Double,
            longitude: Double,
            limit: Int,
            requireHarmonicConstants: Boolean
        ): List<Station> {
            if (throwError) throw Exception("Mock error")

            var filtered = stations
            if (requireHarmonicConstants) {
                filtered = stations.filter { it.harmonicConstants != null }
            }
            return filtered.take(limit)
        }

        override suspend fun findStation(stationId: String): Station {
            return stations.find { it.id == stationId } ?: throw Exception("Station not found")
        }
    }

    private val testStationsNoHarmonics = listOf(
        Station(
            id = "TEST1",
            name = "Test Station 1",
            latitude = 47.0,
            longitude = -122.0,
            source = StationSource.NOAA,
            state = "WA",
            region = "Test Region",
            distance = 0.5,
            capabilities = setOf(StationType.WATER_LEVEL),
            harmonicConstants = null
        ),
        Station(
            id = "TEST2",
            name = "Test Station 2",
            latitude = 47.1,
            longitude = -122.1,
            source = StationSource.NOAA,
            state = "WA",
            region = "Test Region",
            distance = 1.0,
            capabilities = setOf(StationType.WATER_LEVEL),
            harmonicConstants = null
        )
    )

    private val testStationsWithHarmonics = listOf(
        Station(
            id = "TEST3",
            name = "Test Station 3",
            latitude = 47.2,
            longitude = -122.2,
            source = StationSource.NOAA,
            state = "WA",
            region = "Test Region",
            distance = 1.5,
            capabilities = setOf(StationType.WATER_LEVEL),
            harmonicConstants = HarmonicConstants(
                stationId = "TEST3",
                meanSeaLevel = 0.0,
                constituents = listOf(
                    HarmonicConstituent("M2", 28.984104, 4.128, 159.5)
                )
            )
        ),
        Station(
            id = "TEST4",
            name = "Test Station 4",
            latitude = 47.3,
            longitude = -122.3,
            source = StationSource.NOAA,
            state = "WA",
            region = "Test Region",
            distance = 2.0,
            capabilities = setOf(StationType.WATER_LEVEL),
            harmonicConstants = HarmonicConstants(
                stationId = "TEST4",
                meanSeaLevel = 0.0,
                constituents = listOf(
                    HarmonicConstituent("M2", 28.984104, 4.128, 159.5)
                )
            )
        )
    )

    @Test
    fun `findNearestStations returns all stations when not requiring harmonic constants`() = runBlocking {
        val mixedStations = testStationsNoHarmonics + testStationsWithHarmonics
        val service = StationService(
            mapOf(StationSource.NOAA to MockStationFinder(mixedStations))
        )

        val result = service.findNearestStations(47.0, -122.0, 4)
        assertEquals(4, result.size)
        assertTrue(result.any { it.harmonicConstants == null })
        assertTrue(result.any { it.harmonicConstants != null })
    }

    @Test
    fun `findNearestStations returns only stations with harmonic constants when required`() = runBlocking {
        val mixedStations = testStationsNoHarmonics + testStationsWithHarmonics
        val service = StationService(
            mapOf(StationSource.NOAA to MockStationFinder(mixedStations))
        )

        val result = service.findNearestStations(
            47.0, -122.0, 4,
            requireHarmonicConstants = true
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.harmonicConstants != null })
    }

    @Test
    fun `findNearestStations returns empty list when requiring harmonic constants and none available`() = runBlocking {
        val service = StationService(
            mapOf(StationSource.NOAA to MockStationFinder(testStationsNoHarmonics))
        )

        val result = service.findNearestStations(
            47.0, -122.0, 4,
            requireHarmonicConstants = true
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findNearestStations tries all sources when preferred source fails`() = runBlocking {
        val service = StationService(
            mapOf(
                StationSource.NOAA to MockStationFinder(emptyList(), throwError = true),
                StationSource.UKHO to MockStationFinder(testStationsWithHarmonics)
            )
        )

        val result = service.findNearestStations(
            47.0, -122.0, 2,
            preferredSource = StationSource.NOAA,
            requireHarmonicConstants = true
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.harmonicConstants != null })
    }
}
