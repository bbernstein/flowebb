package com.flowebb.tides.api

import com.flowebb.tides.calculation.TideExtreme
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.calculation.TideType
import com.flowebb.tides.station.Station
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ApiResponse {
    @SerialName("responseType") // Add this to specify the discriminator
    val responseType: String
}

@Serializable
@SerialName("stations")
data class StationsResponse(
    override val responseType: String = "stations",
    val stations: List<Station>,
) : ApiResponse

@Serializable
data class ExtendedTideResponse(
    override val responseType: String = "tide",
    val timestamp: Long,
    val waterLevel: Double?,
    val predictedLevel: Double?,
    val nearestStation: String,
    val location: String?,
    val latitude: Double,
    val longitude: Double,
    val stationDistance: Double,
    val tideType: TideType?, // Changed from type to tideType
    val calculationMethod: String,
    val extremes: List<TideExtreme>,
    val predictions: List<TidePrediction>,
    val timeZoneOffsetSeconds: Int? = null,
) : ApiResponse

@Serializable
@SerialName("error")
data class ErrorResponse(
    override val responseType: String = "error",
    val error: String,
) : ApiResponse
