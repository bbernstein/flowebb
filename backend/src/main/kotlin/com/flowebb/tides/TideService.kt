package com.flowebb.tides

import com.flowebb.tides.station.*
import com.flowebb.tides.calculation.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TideResponse(
    val timestamp: Long,
    val waterLevel: Double,
    val predictedLevel: Double,
    val nearestStation: String,
    val location: String?,
    val stationDistance: Double, // in kilometers
    val type: TideType,
    val calculationMethod: String // "NOAA API" or "Harmonic Calculation"
)

open class TideService(
    private val stationService: StationService,
    private val calculator: TideLevelCalculator = TideLevelCalculator()
) {
    open suspend fun getCurrentTide(latitude: Double, longitude: Double, useCalculation: Boolean = false): TideResponse {
        val station = stationService.findNearestStation(latitude, longitude)
        return getCurrentTideForStation(station, useCalculation)
    }

    open suspend fun getCurrentTideForStation(stationId: String, useCalculation: Boolean = false): TideResponse {
        val station = stationService.getStation(stationId)
        return getCurrentTideForStation(station, useCalculation)
    }

    private suspend fun getCurrentTideForStation(station: Station, useCalculation: Boolean): TideResponse {
        val currentTime = Instant.now().toEpochMilli()

        val tideLevel = if (useCalculation) {
            calculator.getCurrentTideLevel(station, currentTime, forceHarmonicCalculation = true)
        } else {
            calculator.getCurrentTideLevel(station, currentTime)
        }

        return TideResponse(
            timestamp = currentTime,
            waterLevel = tideLevel.waterLevel,
            predictedLevel = tideLevel.predictedLevel,
            nearestStation = station.id,
            location = station.name,
            stationDistance = station.distance,
            type = tideLevel.type,
            calculationMethod = if (useCalculation) "Harmonic Calculation" else "NOAA API"
        )
    }
}
