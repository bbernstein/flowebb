package com.flowebb.tides

import com.flowebb.tides.api.ExtendedTideResponse
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationService
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

open class TideService(
    private val stationService: StationService,
    private val calculator: TideLevelCalculator = TideLevelCalculator()
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun getCurrentTide(
        latitude: Double,
        longitude: Double
    ): ExtendedTideResponse {
        logger.debug { "Getting current tide for lat=$latitude, lon=$longitude" }
        val stations = stationService.findNearestStations(latitude, longitude, 1)
        if (stations.isEmpty()) {
            throw Exception("No stations found near the specified coordinates")
        }
        return getCurrentTideForStation(stations.first())
    }

    open suspend fun getCurrentTideForStation(
        stationId: String
    ): ExtendedTideResponse {
        logger.debug { "Getting current tide for station $stationId" }
        try {
            val station = stationService.getStation(stationId)
            logger.debug { "Found station: ${station.id}, stationType:${station.stationType}" }
            return getCurrentTideForStation(station)
        } catch (e: Exception) {
            logger.error(e) { "Error getting tide for station $stationId" }
            throw e
        }
    }

    private suspend fun getCurrentTideForStation(station: Station): ExtendedTideResponse {
        val currentTime = Instant.now()
        val stationZone = calculator.getStationZoneId(station)
        val startOfDay = currentTime.atZone(stationZone)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
        val startOfDayMillis = startOfDay.toEpochMilli()
        val endOfDayMillis = endOfDay.toEpochMilli()

        logger.debug { "Getting tides for station ${station.id} (type: ${station.stationType}) between $startOfDay and $endOfDay ($startOfDayMillis - $endOfDayMillis)" }

        val currentLevel = calculator.getCurrentTideLevel(station, currentTime.toEpochMilli())
        val extremes = calculator.findExtremes(station, startOfDayMillis, endOfDayMillis)
        val predictions = calculator.getPredictions(station, startOfDayMillis, endOfDayMillis, Duration.ofMinutes(6))

        return ExtendedTideResponse(
            timestamp = currentTime.toEpochMilli(),
            waterLevel = currentLevel.waterLevel,
            predictedLevel = currentLevel.predictedLevel,
            nearestStation = station.id,
            location = station.name,
            latitude = station.latitude,
            longitude = station.longitude,
            stationDistance = station.distance,
            tideType = currentLevel.type,
            calculationMethod = "NOAA API",
            extremes = extremes,
            predictions = predictions,
            timeZoneOffsetSeconds = station.timeZoneOffset?.totalSeconds
        )
    }
}
