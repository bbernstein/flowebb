package com.flowebb.tides.station

import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.s3.S3Client
import io.mockk.mockk
import io.mockk.every
import io.mockk.coEvery
import com.flowebb.config.DynamoConfig
import com.flowebb.http.HttpClientService
import com.flowebb.tides.station.cache.StationListCache

class NoaaStationFinderTest {
    private lateinit var mockHttpClient: HttpClientService
    private lateinit var mockDynamoClient: DynamoDbEnhancedClient
    private lateinit var mockS3Client: S3Client
    private lateinit var mockTable: DynamoDbTable<StationListPartition>
    private lateinit var finder: NoaaStationFinder

    @BeforeEach
    fun setup() {
        mockDynamoClient = mockk(relaxed = true)
        mockS3Client = mockk(relaxed = true)
        mockTable = mockk<DynamoDbTable<StationListPartition>>()
        mockHttpClient = mockk()

        DynamoConfig.setTestClient(mockDynamoClient)

        // Create StationListCache with mock S3 client
        val stationListCache = StationListCache(
            isLocalDevelopment = true, // Force local development mode for tests
            s3Client = mockS3Client
        )

        every {
            mockDynamoClient.table(
                any<String>(),
                any<TableSchema<StationListPartition>>()
            )
        } returns mockTable

        every {
            mockTable.getItem(any<Key>())
        } returns null

        every {
            mockTable.putItem(any<StationListPartition>())
        } returns mockk()

        // Mock HTTP client responses
        coEvery {
            mockHttpClient.get(
                url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/tidepredstations.json",
                headers = any(),
                queryParams = any(),
                transform = any<suspend (HttpResponse) -> List<NoaaStationMetadata>>()
            )
        } returns listOf(
            NoaaStationMetadata(
                stationId = "9447130",
                name = "SEATTLE",
                stationName = "Seattle",
                state = "WA",
                region = "Puget Sound",
                lat = 47.60263888888889,
                lon = -122.33916666666667,
                distance = 0.5
            ),
            NoaaStationMetadata(
                stationId = "9447819",
                name = "TACOMA",
                stationName = "Tacoma",
                state = "WA",
                region = "Puget Sound",
                lat = 47.2690,
                lon = -122.4138,
                distance = 1.0
            )
        )

        coEvery {
            mockHttpClient.get(
                url = match { it.contains("/harcon.json") },
                headers = any(),
                queryParams = any(),
                transform = any<suspend (HttpResponse) -> HarmonicConstants>()
            )
        } returns HarmonicConstants(
            stationId = "9447130",
            meanSeaLevel = 0.0,
            constituents = listOf(
                HarmonicConstituent(name = "M2", speed = 28.984104, amplitude = 4.128, phase = 159.5),
                HarmonicConstituent(name = "S2", speed = 30.0, amplitude = 1.089, phase = 183.3)
            )
        )

        finder = NoaaStationFinder(mockHttpClient, stationListCache)
    }

    @AfterEach
    fun tearDown() {
        DynamoConfig.resetTestClient()
    }

    @Test
    fun findNearestReturnsClosestStation() = runBlocking {
        val station = finder.findNearestStations(47.6062, -122.3321, 1).first()

        assertEquals("9447130", station.id)
        assertEquals("Seattle", station.name)
        assertEquals("WA", station.state)
        assertTrue(kotlin.math.abs(47.60263888888889 - station.latitude) < 0.0001)
        assertTrue(kotlin.math.abs(-122.33916666666667 - station.longitude) < 0.0001)
        assertEquals(StationSource.NOAA, station.source)
    }

    @Test
    fun findNearestStationsReturnsOrderedList() = runBlocking {
        val stations = finder.findNearestStations(47.6062, -122.3321, 2)

        assertEquals(2, stations.size)
        assertEquals("9447130", stations[0].id)
        assertEquals("9447819", stations[1].id)
        assertTrue(stations[0].distance < stations[1].distance)
    }

    @Test
    fun findStationReturnsCorrectStation() = runBlocking {
        val station = finder.findStation("9447130")

        assertEquals("9447130", station.id)
        assertEquals("Seattle", station.name)
        assertEquals("WA", station.state)

        val harmonicConstants = station.harmonicConstants
        assertNotNull(harmonicConstants, "Harmonic constants should not be null")
        assertEquals(2, harmonicConstants.constituents.size)
    }}
