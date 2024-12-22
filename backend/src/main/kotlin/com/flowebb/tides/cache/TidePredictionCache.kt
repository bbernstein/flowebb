package com.flowebb.tides.cache

import com.flowebb.config.DynamoConfig
import com.flowebb.tides.calculation.TideExtreme
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.calculation.TideType
import mu.KotlinLogging
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days

class TidePredictionCache {
    private val logger = KotlinLogging.logger {}
    private val cacheValidityPeriod = 7.days.inWholeMilliseconds
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val predictionsTable = DynamoConfig.enhancedClient.table(
        "tide-predictions-cache",
        TableSchema.fromBean(TidePredictionRecord::class.java)
    )

    fun getPredictions(
        stationId: String,
        date: LocalDate
    ): TidePredictionRecord? {
        val dateStr = date.format(dateFormatter)
        logger.debug { "Fetching predictions from cache for station $stationId on $dateStr" }

        return try {
            val cached = predictionsTable.getItem(
                Key.builder()
                    .partitionValue(stationId)
                    .sortValue(dateStr)
                    .build()
            )

            if (cached != null && isCacheValid(cached)) {
                logger.debug { "Cache hit for station $stationId on $dateStr" }
                cached
            } else {
                logger.debug { "Cache miss for station $stationId on $dateStr" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching predictions from cache" }
            null
        }
    }

    fun savePredictions(
        stationId: String,
        date: LocalDate,
        predictions: List<TidePrediction>,
        extremes: List<TideExtreme>,
        stationType: String
    ) {
        val dateStr = date.format(dateFormatter)
        logger.debug { "Saving predictions to cache for station $stationId on $dateStr" }

        val now = Instant.now().toEpochMilli()
        val cacheItem = TidePredictionRecord(
            stationId = stationId,
            date = dateStr,
            stationType = stationType,
            predictions = predictions.map {
                CachedPrediction(
                    timestamp = it.timestamp,
                    height = it.height
                )
            },
            extremes = extremes.map {
                CachedExtreme(
                    timestamp = it.timestamp,
                    height = it.height,
                    type = it.type.toString()
                )
            },
            lastUpdated = now,
            ttl = now + cacheValidityPeriod
        )

        try {
            predictionsTable.putItem(cacheItem)
            logger.debug { "Successfully cached predictions for station $stationId on $dateStr" }
        } catch (e: Exception) {
            logger.error(e) { "Error saving predictions to cache" }
            throw e
        }
    }

    private fun isCacheValid(cache: TidePredictionRecord): Boolean {
        val now = Instant.now().toEpochMilli()
        return (now - cache.lastUpdated) < cacheValidityPeriod
    }

    // Make it explicitly public
    fun convertToPredictions(cached: List<CachedPrediction>): List<TidePrediction> {
        return cached.map {
            TidePrediction(
                timestamp = it.timestamp,
                height = it.height
            )
        }
    }

    fun convertToExtremes(cached: List<CachedExtreme>): List<TideExtreme> {
        return cached.map {
            TideExtreme(
                type = TideType.valueOf(it.type),
                timestamp = it.timestamp,
                height = it.height
            )
        }
    }
}
