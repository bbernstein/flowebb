package com.flowebb.tides.station

import com.flowebb.config.DynamoConfig
import software.amazon.awssdk.enhanced.dynamodb.*
import java.time.Instant
import mu.KotlinLogging
import kotlin.time.Duration.Companion.days
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class HarmonicConstantsCache(
    @get:DynamoDbPartitionKey
    var stationId: String = "",
    @get:DynamoDbConvertedBy(HarmonicConstantsConverter::class)
    var harmonicConstants: HarmonicConstants? = null,
    var lastUpdated: Long = 0,
    var ttl: Long = 0
)

class DynamoStationFinder : StationFinder {
    private val logger = KotlinLogging.logger {}
    private val noaaFinder = NoaaStationFinder()
    private val cacheValidityPeriod = 7.days.inWholeMilliseconds

    private val stationsTable = DynamoConfig.enhancedClient.table(
        "stations-cache",
        TableSchema.fromBean(StationCacheItem::class.java)
    )

    override suspend fun findStation(stationId: String): Station {
        logger.debug { "Looking up station: $stationId" }

        // Try to get from cache first
        val cachedStation = stationsTable.getItem(
            Key.builder().partitionValue(stationId).build()
        )

        if (cachedStation != null &&
            (Instant.now().toEpochMilli() - cachedStation.lastUpdated) < cacheValidityPeriod
        ) {
            logger.debug { "Found cached station: $stationId" }
            return cachedStation.toStation()
        }

        // If not in cache or expired, fetch new data
        logger.debug { "Cache miss for station: $stationId, fetching from NOAA" }
        val station = noaaFinder.findStation(stationId)

        // Cache the result
        stationsTable.putItem(station.toCacheItem())

        return station
    }

    override suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int
    ): List<Station> {
        logger.debug { "Finding nearest stations to lat=$latitude, lon=$longitude" }

        // For coordinate-based searches, always use NOAA finder to get fresh results
        // This ensures we always get the correct nearest stations
        return noaaFinder.findNearestStations(latitude, longitude, limit).also {
            // Cache the individual stations for future lookups
            logger.debug { "Caching ${it.size} stations in $stationsTable" }
            it.forEach { station ->
                stationsTable.putItem(station.toCacheItem())
            }
            logger.debug { "Cached ${it.size} stations from nearest stations search" }
        }
    }

    private fun StationCacheItem.toStation(): Station {
        return Station(
            id = stationId,
            name = name,
            state = state,
            region = region,
            distance = 0.0, // This will be calculated when needed
            latitude = latitude,
            longitude = longitude,
            source = StationSource.valueOf(source),
            capabilities = capabilities.map { StationType.valueOf(it) }.toSet(),
            harmonicConstants = harmonicConstants
        )
    }

    private fun Station.toCacheItem(): StationCacheItem {
        val now = Instant.now().toEpochMilli()
        return StationCacheItem(
            stationId = id,
            name = name,
            state = state,
            region = region,
            latitude = latitude,
            longitude = longitude,
            source = source.name,
            capabilities = capabilities.map { it.name }.toSet(),
            harmonicConstants = harmonicConstants,
            lastUpdated = now,
            ttl = now + cacheValidityPeriod
        )
    }
}
