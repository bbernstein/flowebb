package com.flowebb.tides.api

import com.flowebb.tides.calculation.TideType
import com.flowebb.tides.station.Station
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ApiResponse {
    @SerialName("responseType")  // Add this to specify the discriminator
    val responseType: String
}

@Serializable
@SerialName("tide")  // Add this to specify the concrete type
data class TideResponse(
    override val responseType: String = "tide",  // Add this field
    val timestamp: Long,
    val waterLevel: Double,
    val predictedLevel: Double,
    val nearestStation: String,
    val location: String?,
    val stationDistance: Double,
    @SerialName("tideType")  // Rename the property to avoid conflict
    val type: TideType,
    val calculationMethod: String
) : ApiResponse

@Serializable
@SerialName("stations")
data class StationsResponse(
    override val responseType: String = "stations",
    val stations: List<Station>
) : ApiResponse

@Serializable
@SerialName("error")
data class ErrorResponse(
    override val responseType: String = "error",
    val error: String
) : ApiResponse
