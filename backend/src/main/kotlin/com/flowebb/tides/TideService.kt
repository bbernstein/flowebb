package com.flowebb.tides

import com.flowebb.http.HttpClientService
import com.flowebb.tides.station.*
import com.flowebb.tides.calculation.*
import com.flowebb.tides.api.TideResponse
import java.time.Instant
import mu.KotlinLogging

open class TideService(
    private val stationService: StationService,
    private val calculator: TideLevelCalculator = TideLevelCalculator(
        httpClient = HttpClientService(),
        useNoaaApi = true
    )
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun getCurrentTide(latitude: Double, longitude: Double, useCalculation: Boolean = false): TideResponse {
        logger.debug { "Getting current tide for lat=$latitude, lon=$longitude" }
        // Use findNearestStations with limit 1 instead of the old findNearestStation
        val stations = stationService.findNearestStations(latitude, longitude, 1)
        if (stations.isEmpty()) {
            throw Exception("No stations found near the specified coordinates")
        }
        return getCurrentTideForStation(stations.first(), useCalculation)
    }

    open suspend fun getCurrentTideForStation(stationId: String, useCalculation: Boolean = false): TideResponse {
        logger.debug { "Getting current tide for station $stationId" }
        try {
            val station = stationService.getStation(stationId)
            logger.debug { "Found station: ${station.id}" }
            return getCurrentTideForStation(station, useCalculation)
        } catch (e: Exception) {
            logger.error(e) { "Error getting tide for station $stationId" }
            throw e
        }
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
            type = tideLevel.type,  // This still uses type since it's coming from TideLevel
            calculationMethod = if (useCalculation) "Harmonic Calculation" else "NOAA API"
        )
    }
}
