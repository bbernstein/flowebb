package com.flowebb.tides.station

import com.flowebb.config.DynamoConfig
import com.flowebb.http.HttpClientService
import com.flowebb.tides.calculation.GeoUtils
import com.flowebb.tides.station.cache.StationListCache
import io.ktor.client.call.*
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
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
    private val httpClient: HttpClientService = HttpClientService(),
    private val stationListCache: StationListCache = StationListCache()
) : StationFinder {
    private val logger = KotlinLogging.logger {}

    private val cacheValidityPeriod = 24.hours.inWholeMilliseconds

    private val harmonicConstantsTable = DynamoConfig.enhancedClient.table(
        "harmonic-constants-cache",
        TableSchema.fromBean(HarmonicConstantsCache::class.java)
    )

    private val harmonicCacheValidityPeriod = 7.days.inWholeMilliseconds

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
        limit: Int,
        requireHarmonicConstants: Boolean
    ): List<Station> {
        logger.debug {
            "Finding nearest stations to lat=$latitude, lon=$longitude " +
                    "with harmonicConstants=${requireHarmonicConstants}"
        }

        val allStations = getStationList()
        logger.debug { "Retrieved ${allStations.size} total stations for processing" }

        // Calculate and sort distances for all stations
        val stationsWithDistances = allStations
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

        val result = mutableListOf<Station>()
        var processedCount = 0
        val batchSize = 10  // Process stations in batches

        // Keep fetching stations until we have enough or run out
        while (result.size < limit && processedCount < stationsWithDistances.size) {
            val batch = stationsWithDistances
                .subList(
                    processedCount,
                    minOf(processedCount + batchSize, stationsWithDistances.size)
                )

            logger.debug {
                "Processing batch of ${batch.size} stations " +
                        "(${result.size}/${limit} stations found so far)"
            }

            // Process this batch of stations
            for ((stationData, distance) in batch) {
                val harmonicConstants = if (requireHarmonicConstants) {
                    fetchHarmonicConstants(stationData.stationId)
                } else null

                // Skip if we need harmonic constants but they're missing or empty
                if (requireHarmonicConstants &&
                    (harmonicConstants == null || harmonicConstants.constituents.isEmpty())
                ) {
                    logger.debug {
                        "Skipping station ${stationData.stationId} - " +
                                "no harmonic constants available"
                    }
                    continue
                }

                result.add(
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
                        harmonicConstants = harmonicConstants
                    )
                )

                if (result.size >= limit) {
                    break
                }
            }

            processedCount += batch.size
        }

        logger.debug {
            "Found ${result.size} suitable stations after processing " +
                    "$processedCount total stations"
        }

        return result
    }

    private suspend fun fetchHarmonicConstants(stationId: String): HarmonicConstants? {
        return try {
            logger.debug { "Fetching harmonic constants for station $stationId" }
            httpClient.get(
                url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/$stationId/harcon.json"
            ) { response ->
                response.body<NoaaHarmonicResponse>().let { noaaResponse ->
                    // Only create HarmonicConstants if there are actual constituents
                    if (noaaResponse.HarmonicConstituents.isEmpty()) {
                        logger.debug { "Station $stationId has no harmonic constituents" }
                        null
                    } else {
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
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch harmonic constants for station $stationId" }
            null
        }
    }
}
