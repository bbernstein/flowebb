package com.flowebb.tides.cache

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import kotlinx.serialization.Serializable

@DynamoDbBean
data class TidePredictionRecord(
    @get:DynamoDbPartitionKey
    var stationId: String = "",

    @get:DynamoDbSortKey
    var date: String = "",  // Format: YYYY-MM-DD

    var stationType: String = "",  // "R" for reference, "S" for subordinate

    @get:DynamoDbConvertedBy(PredictionListConverter::class)
    var predictions: List<CachedPrediction> = emptyList(),

    @get:DynamoDbConvertedBy(ExtremeListConverter::class)
    var extremes: List<CachedExtreme> = emptyList(),

    var lastUpdated: Long = 0,

    var ttl: Long = 0
) {
    // Required no-arg constructor for DynamoDB
    constructor() : this(stationId = "", date = "")
}

@Serializable
data class CachedPrediction(
    val timestamp: Long,
    val height: Double
)

@Serializable
data class CachedExtreme(
    val timestamp: Long,
    val height: Double,
    val type: String  // "HIGH" or "LOW"
)
