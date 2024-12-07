package com.flowebb.tides.api

import com.flowebb.tides.*
import com.flowebb.tides.calculation.TideType
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationService
import com.flowebb.tides.station.StationSource
import com.flowebb.tides.station.StationType
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RoutesTest {
    // Create mock station service first
    private val mockStationService = object : StationService(emptyMap()) {
        override suspend fun findNearestStations(
            latitude: Double,
            longitude: Double,
            limit: Int,
            preferredSource: StationSource?
        ): List<Station> = listOf(
            Station(
                id = "TEST1",
                name = "Test Station 1",
                state = "WA",
                region = "Test Region",
                distance = 0.5,
                latitude = latitude,
                longitude = longitude,
                source = StationSource.NOAA,
                capabilities = setOf(StationType.WATER_LEVEL)
            ),
            Station(
                id = "TEST2",
                name = "Test Station 2",
                state = "WA",
                region = "Test Region",
                distance = 1.0,
                latitude = latitude + 0.1,
                longitude = longitude - 0.1,
                source = StationSource.NOAA,
                capabilities = setOf(StationType.WATER_LEVEL)
            )
        )

        override suspend fun getStation(stationId: String): Station {
            return Station(
                id = stationId,
                name = "Test Station",
                state = "WA",
                region = "Test Region",
                distance = 0.5,
                latitude = 47.0,
                longitude = -122.0,
                source = StationSource.NOAA,
                capabilities = setOf(StationType.WATER_LEVEL)
            )
        }
    }

    // Create mock tide service that takes the station service as a parameter
    private class MockTideService(stationService: StationService) : TideService(stationService) {
        override suspend fun getCurrentTide(
            latitude: Double,
            longitude: Double,
            useCalculation: Boolean
        ): TideResponse {
            return TideResponse(
                timestamp = 1637964000000,
                waterLevel = 5.0,
                predictedLevel = 5.0,
                nearestStation = "TEST1",
                location = "Test Location",
                stationDistance = 0.5,
                type = TideType.RISING,
                calculationMethod = if (useCalculation) "Harmonic Calculation" else "NOAA API"
            )
        }

        override suspend fun getCurrentTideForStation(
            stationId: String,
            useCalculation: Boolean
        ): TideResponse {
            return TideResponse(
                timestamp = 1637964000000,
                waterLevel = 5.0,
                predictedLevel = 5.0,
                nearestStation = stationId,
                location = "Test Location",
                stationDistance = 0.5,
                type = TideType.RISING,
                calculationMethod = if (useCalculation) "Harmonic Calculation" else "NOAA API"
            )
        }
    }

    @Test
    fun `valid request returns 200 OK with tide data`() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                tideRoutes(MockTideService(mockStationService), mockStationService)
            }
        }

        val response = client.get("/api/tides?lat=47.6062&lon=-122.3321")
        assertEquals(HttpStatusCode.OK, response.status)

        val jsonResponse = Json.decodeFromString<TideResponse>(response.bodyAsText())
        assertEquals("TEST1", jsonResponse.nearestStation)
        assertEquals(5.0, jsonResponse.waterLevel)
        assertEquals(TideType.RISING, jsonResponse.type)
    }

    @Test
    fun `invalid latitude returns 400 Bad Request`() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                tideRoutes(MockTideService(mockStationService), mockStationService)
            }
        }

        val response = client.get("/api/tides?lat=91&lon=-122.3321")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `missing parameters returns 400 Bad Request`() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                tideRoutes(MockTideService(mockStationService), mockStationService)
            }
        }

        val response = client.get("/api/tides")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `stations endpoint returns nearest stations`() = testApplication {
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                tideRoutes(MockTideService(mockStationService), mockStationService)
            }
        }

        val response = client.get("/api/stations?lat=47.6062&lon=-122.3321")
        assertEquals(HttpStatusCode.OK, response.status)

        val stations = Json.decodeFromString<List<Station>>(response.bodyAsText())
        assertTrue(stations.isNotEmpty())
        assertTrue(stations.size <= 10)

        // Verify stations are ordered by distance
        for (i in 0 until stations.size - 1) {
            assertTrue(stations[i].distance <= stations[i + 1].distance)
        }
    }
}
