package com.flowebb.tides.station

import com.flowebb.http.HttpClientService
import com.flowebb.tides.calculation.GeoUtils
import com.flowebb.tides.station.cache.StationListCache
import io.ktor.client.call.*
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

    private suspend fun getStationList(): List<NoaaStationMetadata> {
        // Try to get from cache first
        stationListCache.getStationList()?.let { cached ->
            if (stationListCache.isCacheValid()) {
                logger.debug { "Using cached station list with ${cached.size} stations" }
                return cached
            }
        }

        // If not in cache or expired, fetch new data
        logger.debug { "Fetching fresh station list from NOAA API" }
        return fetchStationList().also { stations ->
            logger.debug { "Fetched ${stations.size} stations from NOAA API" }
            stationListCache.saveStationList(stations)
        }
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

        val timeZoneOffset = parseTimeZoneCorrection(stationData.timeZoneCorr)

        return Station(
            id = stationData.stationId,
            name = stationData.stationName,
            state = stationData.state,
            region = stationData.region,
            distance = 0.0,
            latitude = stationData.lat,
            longitude = stationData.lon,
            source = StationSource.NOAA,
            capabilities = setOf(StationType.WATER_LEVEL),
            timeZoneOffset = timeZoneOffset,
            level = stationData.level,
            stationType = stationData.stationType
        )
    }

    override suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int
    ): List<Station> {
        logger.debug { "Finding nearest stations to lat=$latitude, lon=$longitude" }

        return getStationList()
            .map { stationData ->
                val distance = GeoUtils.calculateDistance(
                    latitude,
                    longitude,
                    stationData.lat,
                    stationData.lon
                )
                Station(
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
            .sortedBy { it.distance }
            .take(limit)
    }
}
