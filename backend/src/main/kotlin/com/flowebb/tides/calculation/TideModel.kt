package com.flowebb.tides.calculation

import kotlinx.serialization.Serializable

@Serializable
enum class TideType {
    RISING,
    FALLING,
    HIGH,
    LOW,
}

@Serializable
data class TideExtreme(
    val type: TideType,
    val timestamp: Long,
    val height: Double,
)

@Serializable
data class TidePrediction(
    val timestamp: Long,
    val height: Double,
)

@Serializable
data class NoaaResponse(
    val predictions: List<NoaaPrediction>,
)

@Serializable
data class NoaaPrediction(
    val t: String, // Time of prediction
    val v: String, // Predicted water level (as string from NOAA)
    val type: String? = null, // Type of prediction (H for high, L for low), optional
)
