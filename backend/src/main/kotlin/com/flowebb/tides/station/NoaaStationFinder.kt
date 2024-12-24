package com.flowebb.tides.station

import com.flowebb.http.HttpClientService
import com.flowebb.tides.calculation.GeoUtils
import com.flowebb.tides.station.cache.StationListCache
import io.ktor.client.call.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.hours

@Suppress("unused")
class NoaaStationFinder(
    private val httpClient: HttpClientService = HttpClientService(),
    private val stationListCache: StationListCache = StationListCache()
) : StationFinder {
    private val logger = KotlinLogging.logger {}
    private val cacheValidityPeriod = 24.hours.inWholeMilliseconds

    private fun parseTimeZoneCorrection(timeZoneCorr: String?): ZoneOffset? {
        return timeZoneCorr?.let { correction ->
            try {
                // NOAA format is like "5" or "-5"
                val hours = correction.toInt()
                ZoneOffset.ofHours(hours)
            } catch (e: Exception) {
                logger.warn { "Failed to parse timezone correction: $correction" }
                null
            }
        }
    }

    private suspend fun getStationList(): List<NoaaStationMetadata> = coroutineScope {
        // Try to get from cache first
        stationListCache.getStationList()?.let { cached ->
            if (stationListCache.isCacheValid()) {
                logger.debug { "Using cached station list with ${cached.size} stations" }
                return@coroutineScope cached
            }
        }

        // If not in cache or expired, fetch new data
        logger.debug { "Fetching fresh station list from NOAA API" }
        // Create async task for fetching
        val fetchTask = async { fetchStationList() }

        val stations = fetchTask.await()
        logger.debug { "Fetched ${stations.size} stations from NOAA API" }

        // Cache the results asynchronously
        async { stationListCache.saveStationList(stations) }

        stations
    }

    private suspend fun fetchStationList(): List<NoaaStationMetadata> {
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/tidepredstations.json"
        ) { response ->
            response.body<NoaaStationsResponse>().stationList
        }
    }

    override suspend fun findStation(stationId: String): Station {
        logger.debug { "Fetching station data for ID: $stationId" }

        val stationData = getStationList()
            .find { it.stationId == stationId }
            ?: throw Exception("Station not found: $stationId")

        return convertToStation(stationData, 0.0)
    }

    override suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int
    ): List<Station> = coroutineScope {
        logger.debug { "Finding nearest stations to lat=$latitude, lon=$longitude" }

        val stationList = getStationList()

        // Process stations in parallel chunks for distance calculations
        val chunkSize = 100
        val stations = stationList.chunked(chunkSize).map { chunk ->
            async {
                chunk.map { stationData ->
                    val distance = GeoUtils.calculateDistance(
                        latitude,
                        longitude,
                        stationData.lat,
                        stationData.lon
                    )
                    convertToStation(stationData, distance)
                }
            }
        }.awaitAll()
            .flatten()
            .sortedBy { it.distance }
            .take(limit)

        stations
    }

    private fun convertToStation(stationData: NoaaStationMetadata, distance: Double): Station {
        return Station(
            id = stationData.stationId,
            name = stationData.stationName,
            state = stationData.state,
            region = stationData.region,
            distance = distance,
            latitude = stationData.lat,
            longitude = stationData.lon,
            source = StationSource.NOAA,
            capabilities = setOf(StationType.WATER_LEVEL),
            timeZoneOffset = parseTimeZoneCorrection(stationData.timeZoneCorr),
            level = stationData.level,
            stationType = stationData.stationType
        )
    }
}
