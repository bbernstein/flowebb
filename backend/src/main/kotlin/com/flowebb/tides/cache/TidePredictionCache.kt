package com.flowebb.tides.cache

import com.flowebb.config.DynamoConfig
import com.flowebb.tides.calculation.TideExtreme
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.calculation.TideType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.days

class TidePredictionCache(
    enhancedClient: DynamoDbEnhancedClient = DynamoConfig.enhancedClient
) {
    private val logger = KotlinLogging.logger {}
    private val cacheValidityPeriod = 7.days.inWholeMilliseconds
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val predictionsTable = enhancedClient.table(
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

    suspend fun savePredictions(item: TidePredictionRecord) = coroutineScope {
        try {
            predictionsTable.putItem(item)
            logger.debug { "Successfully cached predictions for station ${item.stationId} on ${item.date}" }
        } catch (e: Exception) {
            logger.error(e) { "Error saving predictions to cache for station ${item.stationId} on ${item.date}" }
            throw e
        }
    }
    suspend fun savePredictionsBatch(items: List<TidePredictionRecord>) = coroutineScope {
        logger.debug { "Saving batch of ${items.size} predictions to cache" }

        // Process items in parallel chunks to avoid overwhelming DynamoDB
        val chunkSize = 25
        val results = items.chunked(chunkSize).map { chunk ->
            async {
                chunk.forEach { item ->
                    try {
                        savePredictions(item)
                    } catch (e: Exception) {
                        logger.error(e) { "Error saving predictions to cache for station ${item.stationId} on ${item.date}" }
                        throw e
                    }
                }
            }
        }

        // Wait for all chunks to complete
        results.awaitAll()
    }

    private fun isCacheValid(cache: TidePredictionRecord): Boolean {
        val now = Instant.now().toEpochMilli()
        return (now - cache.lastUpdated) < cacheValidityPeriod
    }

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
