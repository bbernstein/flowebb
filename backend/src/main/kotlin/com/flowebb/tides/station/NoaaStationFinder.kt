package com.flowebb.tides.station

import com.flowebb.config.DynamoConfig
import com.flowebb.http.HttpClientService
import com.flowebb.tides.calculation.GeoUtils
import io.ktor.client.call.*
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import software.amazon.awssdk.enhanced.dynamodb.Key
import java.time.Instant
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import mu.KotlinLogging

@DynamoDbBean
data class StationListPartition(
    @get:DynamoDbPartitionKey
    var listId: String = "NOAA_STATION_LIST",
    @get:DynamoDbSortKey
    var partitionId: Int = 0,
    @get:DynamoDbConvertedBy(StationListConverter::class)
    var stations: List<NoaaStationMetadata> = listOf(),
    var totalPartitions: Int = 1,
    var lastUpdated: Long = 0,
    var ttl: Long = 0
)

@Suppress("unused")
class NoaaStationFinder(
    private val httpClient: HttpClientService = HttpClientService()
) : StationFinder {
    private val logger = KotlinLogging.logger {}
    private val PARTITION_SIZE = 100 // Number of stations per partition

    private val stationListTable = DynamoConfig.enhancedClient.table(
        "station-list-cache",
        TableSchema.fromBean(StationListPartition::class.java)
    )

    private val cacheValidityPeriod = 24.hours.inWholeMilliseconds

    private val harmonicConstantsTable = DynamoConfig.enhancedClient.table(
        "harmonic-constants-cache",
        TableSchema.fromBean(HarmonicConstantsCache::class.java)
    )

    private val harmonicCacheValidityPeriod = 7.days.inWholeMilliseconds

    private suspend fun getStationList(): List<NoaaStationMetadata> {
        // Try to get first partition to check if cache is valid
        val firstPartition = stationListTable.getItem(
            Key.builder()
                .partitionValue("NOAA_STATION_LIST")
                .sortValue(0)
                .build()
        )

        if (firstPartition != null &&
            (Instant.now().toEpochMilli() - firstPartition.lastUpdated) < cacheValidityPeriod
        ) {
            logger.debug { "Found valid first partition with ${firstPartition.stations.size} stations" }
            logger.debug { "Total partitions: ${firstPartition.totalPartitions}" }

            val allStations = getAllPartitions(firstPartition.totalPartitions)
            logger.debug { "Retrieved ${allStations.size} total stations from cache" }
            return allStations
        }

        // If not in cache or expired, fetch new data
        logger.debug { "Fetching fresh station list from NOAA API" }
        val stations = fetchStationList()
        logger.debug { "Fetched ${stations.size} stations from NOAA API" }

        // Calculate number of partitions needed
        val totalPartitions = (stations.size + PARTITION_SIZE - 1) / PARTITION_SIZE
        logger.debug { "Splitting stations into $totalPartitions partitions" }

        // Store in partitions
        val now = Instant.now().toEpochMilli()
        stations.chunked(PARTITION_SIZE).forEachIndexed { index, partition ->
            logger.debug { "Storing partition $index with ${partition.size} stations" }
            stationListTable.putItem(
                StationListPartition(
                    listId = "NOAA_STATION_LIST",
                    partitionId = index,
                    stations = partition,
                    totalPartitions = totalPartitions,
                    lastUpdated = now,
                    ttl = now + cacheValidityPeriod
                )
            )
        }

        return stations
    }

    private fun getAllPartitions(totalPartitions: Int): List<NoaaStationMetadata> {
        logger.debug { "Retrieving all $totalPartitions partitions" }
        val allStations = (0 until totalPartitions)
            .map { partitionId ->
                val partition = stationListTable.getItem(
                    Key.builder()
                        .partitionValue("NOAA_STATION_LIST")
                        .sortValue(partitionId)
                        .build()
                )
                logger.debug { "Retrieved partition $partitionId with ${partition?.stations?.size ?: 0} stations" }
                partition?.stations ?: emptyList()
            }
            .flatten()

        logger.debug { "Combined all partitions into ${allStations.size} total stations" }
        return allStations
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

        val harmonicConstants = fetchHarmonicConstants(stationId)

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
            harmonicConstants = harmonicConstants
        )
    }

    override suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int
    ): List<Station> {
        logger.debug { "Finding nearest stations to lat=$latitude, lon=$longitude" }

        // Always get the full station list first
        val allStations = getStationList()
        logger.debug { "Retrieved ${allStations.size} total stations for processing" }

        // Calculate distances and find nearest stations
        val nearestStations = allStations
            .map { stationData ->
                val distance = GeoUtils.calculateDistance(
                    latitude,
                    longitude,
                    stationData.lat,
                    stationData.lon
                )
                stationData to distance
            }
            .sortedBy { it.second }
            .take(limit)

        logger.debug { "Found ${nearestStations.size} nearest stations" }
        logger.debug { "Nearest station distances: ${nearestStations.map { it.second }}" }

        // Convert to Station objects and return
        return nearestStations.map { (stationData, distance) ->
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
                harmonicConstants = fetchHarmonicConstants(stationData.stationId)
            )
        }
    }

    private suspend fun fetchHarmonicConstants(stationId: String): HarmonicConstants? {
        // Try to get from cache first
        val cachedConstants = harmonicConstantsTable.getItem(
            Key.builder().partitionValue(stationId).build()
        )

        if (cachedConstants != null &&
            (Instant.now().toEpochMilli() - cachedConstants.lastUpdated) < harmonicCacheValidityPeriod
        ) {
            logger.debug { "Found cached harmonic constants for station $stationId" }
            return cachedConstants.harmonicConstants
        }

        // If not in cache or expired, fetch from API
        return try {
            logger.debug { "Fetching harmonic constants from NOAA API for station $stationId" }
            httpClient.get(
                url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/$stationId/harcon.json"
            ) { response ->
                response.body<NoaaHarmonicResponse>().let { noaaResponse ->
                    HarmonicConstants(
                        stationId = stationId,
                        meanSeaLevel = noaaResponse.HarmonicConstituents
                            .find { it.name == "Z0" }?.amplitude ?: 0.0,
                        constituents = noaaResponse.HarmonicConstituents
                            .filter { it.name != "Z0" }
                            .map { constituent ->
                                HarmonicConstituent(
                                    name = constituent.name,
                                    speed = constituent.speed,
                                    amplitude = constituent.amplitude,
                                    phase = constituent.phase_GMT
                                )
                            }
                    )
                }
            }.also { constants ->
                // Cache the result
                val now = Instant.now().toEpochMilli()
                harmonicConstantsTable.putItem(
                    HarmonicConstantsCache(
                        stationId = stationId,
                        harmonicConstants = constants,
                        lastUpdated = now,
                        ttl = now + harmonicCacheValidityPeriod
                    )
                )
                logger.debug { "Cached harmonic constants for station $stationId" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch harmonic constants for station $stationId" }
            null
        }
    }
}
