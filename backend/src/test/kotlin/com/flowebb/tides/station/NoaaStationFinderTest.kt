package com.flowebb.tides.station

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.*
import kotlin.time.Duration.Companion.days

class NoaaStationFinderTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var mockEngine: MockEngine
    private lateinit var client: HttpClient

    private val testStationsJson = """
    {
        "stationList": [
            {
                "name": "SEATTLE",
                "state": "WA",
                "region": "Puget Sound",
                "timeZoneCorr": "-8",
                "stationName": "Seattle",
                "commonName": "Seattle",
                "distance": 0.5,
                "stationFullName": "SEATTLE, PUGET SOUND",
                "etidesStnName": "SEATTLE, PUGET SOUND",
                "stationId": "9447130",
                "lat": 47.60263888888889,
                "lon": -122.33916666666667,
                "stationType": "R"
            },
            {
                "name": "TACOMA",
                "state": "WA",
                "region": "Puget Sound",
                "timeZoneCorr": "-8",
                "stationName": "Tacoma",
                "commonName": "Tacoma",
                "distance": 1.0,
                "stationFullName": "TACOMA, PUGET SOUND",
                "etidesStnName": "TACOMA, PUGET SOUND",
                "stationId": "9447819",
                "lat": 47.2690,
                "lon": -122.4138,
                "stationType": "R"
            }
        ]
    }
""".trimIndent()

    private val testHarmonicJson = """
        {
            "HarmonicConstituents": [
                {
                    "name": "M2",
                    "speed": 28.984104,
                    "amplitude": 4.128,
                    "phase_GMT": 159.5
                },
                {
                    "name": "S2",
                    "speed": 30.0,
                    "amplitude": 1.089,
                    "phase_GMT": 183.3
                }
            ]
        }
    """.trimIndent()

    // Query coordinates close to Seattle
    private val testLat = 47.6062
    private val testLon = -122.3321

    @BeforeEach
    fun setup() {
        cacheFile = tempDir.resolve("test-cache.json").toFile()
        if (cacheFile.exists()) {
            cacheFile.delete()
        }

        mockEngine = MockEngine { request ->
            println("Mock engine received request to: ${request.url}")
            val response = when {
                request.url.toString().contains("/harcon.json") -> testHarmonicJson
                else -> testStationsJson
            }
            println("Responding with: $response")
            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                    encodeDefaults = true
                })
            }
            expectSuccess = false
        }
    }

    @Test
    fun `uses cached data when available and not expired`() = runBlocking {
        // First, create a cache file with known data
        val cacheContent = """
    {
        "timestamp": ${System.currentTimeMillis()},
        "stationList": [
            {
                "name": "Cache Test Station", 
                "state": "WA",
                "region": "Test Region",
                "timeZoneCorr": "-8",
                "stationName": "Cache Test Station",
                "commonName": "Cache Test Station", 
                "distance": 0.5,
                "stationFullName": "Cache Test Station, Test Region",
                "etidesStnName": "Cache Test Station, Test Region",
                "stationId": "TEST123",
                "lat": 47.0,
                "lon": -122.0,
                "stationType": "R"
            }
        ]
    }
""".trimIndent()
        cacheFile.writeText(cacheContent)

        val finder = NoaaStationFinder(client, cacheFile)
        val station = finder.findNearestStations(testLat, testLon, 1).first()

        assertEquals("TEST123", station.id)
        assertEquals("Cache Test Station", station.name)
    }

    @Test
    fun `ignores expired cache and fetches new data`() = runBlocking {
        // Create an expired cache file
        val cacheContent = """
            {
                "timestamp": ${System.currentTimeMillis() - 8.days.inWholeMilliseconds},
                "stationList": [
                    {
                        "stationId": "OLD123",
                        "name": "Old Cache Station",
                        "lat": 47.0,
                        "lon": -122.0,
                        "state": "WA"
                    }
                ]
            }
        """.trimIndent()
        cacheFile.writeText(cacheContent)

        val finder = NoaaStationFinder(client, cacheFile)
        val station = finder.findNearestStations(testLat, testLon, 1).first()

        assertEquals("9447130", station.id)
        assertEquals("Seattle", station.name)
    }

    @Test
    fun `creates new cache file when fetching data`() = runBlocking {
        assertFalse(cacheFile.exists(), "Cache file should not exist before test")

        val finder = NoaaStationFinder(client, cacheFile)
        finder.findNearestStations(47.0, -122.0, 1)

        assertTrue(cacheFile.exists(), "Cache file should be created after fetching data")
        val cacheContent = cacheFile.readText()
        assertTrue(cacheContent.contains("9447130"), "Cache should contain fetched station data")
    }

    @Test
    fun findNearestReturnsClosestStation() = runBlocking {
        val finder = NoaaStationFinder(client, cacheFile)
        val station = finder.findNearestStations(47.6062, -122.3321, 1).first()

        assertEquals("9447130", station.id)
        assertEquals("Seattle", station.name)
        assertEquals("WA", station.state)
        assertTrue(abs(47.60263888888889 - station.latitude) < 0.0001)
        assertTrue(abs(-122.33916666666667 - station.longitude) < 0.0001)
        assertEquals(StationSource.NOAA, station.source)
    }

    @Test
    fun findNearestStationsReturnsOrderedList() = runBlocking {
        val finder = NoaaStationFinder(client, cacheFile)
        val stations = finder.findNearestStations(47.6062, -122.3321, 2)

        assertEquals(2, stations.size)
        assertEquals("9447130", stations[0].id)
        assertEquals("9447819", stations[1].id)
        assertTrue(stations[0].distance < stations[1].distance)
    }
}
