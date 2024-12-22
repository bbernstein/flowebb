package com.flowebb.tides

import com.flowebb.tides.api.ExtendedTideResponse
import com.flowebb.tides.cache.TidePredictionCache
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationService
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

open class TideService(
    private val stationService: StationService,
    private val calculator: TideLevelCalculator = TideLevelCalculator(),
    private val cache: TidePredictionCache = TidePredictionCache()
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
        val currentDate = currentTime.atZone(stationZone).toLocalDate()

        // Get yesterday, today, and tomorrow's data
        val dates = listOf(
            currentDate.minusDays(1),
            currentDate,
            currentDate.plusDays(1)
        )

        val startOfDay = currentTime.atZone(stationZone)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
        val startOfDayMillis = startOfDay.toEpochMilli()
        val endOfDayMillis = endOfDay.toEpochMilli()

        logger.debug { "Getting tides for station ${station.id} (type: ${station.stationType}) between $startOfDay and $endOfDay" }

        // Get three days of cached data
        val cachedDataList = calculator.getCachedDayData(station, dates, stationZone)

        // Combine all predictions and extremes
        val allPredictions = cachedDataList.flatMap { cache.convertToPredictions(it.predictions) }
            .sortedBy { it.timestamp }
        val allExtremes = cachedDataList.flatMap { cache.convertToExtremes(it.extremes) }
            .sortedBy { it.timestamp }

        // Use the combined data for calculations
        val currentLevel = if (station.stationType == "S") {
            calculator.interpolateExtremes(allExtremes, currentTime.toEpochMilli())
        } else {
            calculator.interpolatePredictions(allPredictions, currentTime.toEpochMilli())
        }

        val currentType = calculator.determineTideType(currentLevel,
            if (station.stationType == "S") {
                calculator.interpolateExtremes(allExtremes, currentTime.toEpochMilli() - 360000)
            } else {
                calculator.interpolatePredictions(allPredictions, currentTime.toEpochMilli() - 360000)
            }
        )

        // Generate predictions at 6-minute intervals
        val predictions = mutableListOf<TidePrediction>()
        var t = startOfDayMillis
        while (t <= endOfDayMillis) {
            val height = if (station.stationType == "S") {
                calculator.interpolateExtremes(allExtremes, t)
            } else {
                calculator.interpolatePredictions(allPredictions, t)
            }
            predictions.add(TidePrediction(t, height))
            t += Duration.ofMinutes(6).toMillis()
        }

        // Get today's extremes only
        val todayExtremes = allExtremes.filter { extreme ->
            val extremeTime = Instant.ofEpochMilli(extreme.timestamp)
                .atZone(stationZone)
                .toLocalDate()
            extremeTime == currentDate
        }

        return ExtendedTideResponse(
            timestamp = currentTime.toEpochMilli(),
            waterLevel = currentLevel,
            predictedLevel = currentLevel,
            nearestStation = station.id,
            location = station.name,
            latitude = station.latitude,
            longitude = station.longitude,
            stationDistance = station.distance,
            tideType = currentType,
            calculationMethod = "NOAA API",
            extremes = todayExtremes,
            predictions = predictions,
            timeZoneOffsetSeconds = station.timeZoneOffset?.totalSeconds
        )
    }
}
