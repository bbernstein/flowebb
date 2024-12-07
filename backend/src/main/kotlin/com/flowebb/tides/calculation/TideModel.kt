package com.flowebb.tides.calculation

import kotlinx.serialization.Serializable

@Serializable
enum class TideType {
    RISING,
    FALLING,
    HIGH,
    LOW
}

data class TideLevel(
    val waterLevel: Double,    // Current water level in feet relative to MLLW
    val predictedLevel: Double, // Predicted water level
    val type: TideType        // Current tide state
)

@Serializable
data class NoaaResponse(
    val predictions: List<NoaaPrediction>
)

@Serializable
data class NoaaPrediction(
    val t: String,  // Time of prediction
    val v: String,  // Predicted water level (as string from NOAA)
    val type: String? = null // Type of prediction (H for high, L for low), optional
)
