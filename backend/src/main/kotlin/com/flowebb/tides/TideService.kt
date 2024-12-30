package com.flowebb.tides

import com.flowebb.tides.api.ExtendedTideResponse
import com.flowebb.tides.cache.TidePredictionCache
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.calculation.TidePrediction
import com.flowebb.tides.calculation.TideType
import com.flowebb.tides.station.Station
import com.flowebb.tides.station.StationService
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

open class TideService(
    private val stationService: StationService,
    private val calculator: TideLevelCalculator = TideLevelCalculator(),
    private val cache: TidePredictionCache = TidePredictionCache(),
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun getCurrentTide(
        latitude: Double,
        longitude: Double,
        startDateTimeStr: String?,
        endDateTimeStr: String?,
    ): ExtendedTideResponse {
        logger.debug { "Getting current tide for lat=$latitude, lon=$longitude" }
        val stations = stationService.findNearestStations(latitude, longitude, 1)
        if (stations.isEmpty()) {
            throw Exception("No stations found near the specified coordinates")
        }
        return getCurrentTideForStation(stations.first(), startDateTimeStr, endDateTimeStr)
    }

    open suspend fun getCurrentTideForStation(
        stationId: String,
        startDateTimeStr: String?,
        endDateTimeStr: String?,
    ): ExtendedTideResponse {
        logger.debug { "Getting current tide for station $stationId" }
        try {
            val station = stationService.getStation(stationId)
            logger.debug { "Found station: ${station.id}, stationType:${station.stationType}" }
            return getCurrentTideForStation(station, startDateTimeStr, endDateTimeStr)
        } catch (e: Exception) {
            logger.error(e) { "Error getting tide for station $stationId" }
            throw e
        }
    }

    private suspend fun getCurrentTideForStation(
        station: Station,
        startDateTimeStr: String?,
        endDateTimeStr: String?,
    ): ExtendedTideResponse {
        val stationZone = calculator.getStationZoneId(station)
        val startDateTime =
            startDateTimeStr?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).atZone(stationZone).toInstant()
            } ?: Instant
                .now()
                .atZone(stationZone)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()

        val endDateTime =
            endDateTimeStr?.let {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).atZone(stationZone).toInstant()
            } ?: startDateTime.plus(1, ChronoUnit.DAYS)

        if (Duration.between(startDateTime, endDateTime).toDays() > 5) {
            throw IllegalArgumentException("Date range cannot exceed 5 days")
        }

        val useExtremes = station.stationType == "S"
        val currentTime = Instant.now()

        // if using extremes to calculated tides, go back 1 day before starttime so we can interpolate the start of the day
        val startTime =
            if (useExtremes) {
                startDateTime.minus(1, ChronoUnit.DAYS)
            } else {
                startDateTime
            }

        val endTime = endDateTime.plus(1, ChronoUnit.DAYS)

        // get list of dates from startDateTime to endDateTime
        // which may be any number of days in the past or future
        // as type List<LocalDate>
        val dates =
            (0..Duration.between(startTime, endTime).toDays()).map {
                startTime.atZone(stationZone).plusDays(it).toLocalDate()
            }

        val startOfDay = startDateTime.atZone(stationZone).truncatedTo(ChronoUnit.DAYS).toInstant()
        val endOfDay =
            endDateTime
                .atZone(stationZone)
                .plusDays(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
        val startOfDayMillis = startOfDay.toEpochMilli()
        val endOfDayMillis = endOfDay.toEpochMilli()

        // Get three days of cached data
        val cachedDataList = calculator.getCachedDayData(station, dates, stationZone)

        // Combine all predictions and extremes
        val allPredictions =
            cachedDataList
                .flatMap { cache.convertToPredictions(it.predictions) }
                .sortedBy { it.timestamp }
        val allExtremes =
            cachedDataList
                .flatMap { cache.convertToExtremes(it.extremes) }
                .sortedBy { it.timestamp }

        // these are for calculating the current tide only
        val rangeIncludesNow = currentTime.toEpochMilli() in startOfDayMillis..endOfDayMillis
        var currentType: TideType? = null
        var currentLevel: Double? = null
        var previousLevel: Double? = null

        // Generate predictions at 6-minute intervals
        val predictions = mutableListOf<TidePrediction>()
        var t = startOfDayMillis
        while (t <= endOfDayMillis) {
            val height =
                if (useExtremes) {
                    calculator.interpolateExtremes(allExtremes, t)
                } else {
                    calculator.interpolatePredictions(allPredictions, t)
                }

            // Only calculate current tide if it's in the date range
            if (rangeIncludesNow) {
                if (t < currentTime.toEpochMilli()) {
                    previousLevel = height
                } else {
                    if (currentLevel == null) {
                        currentLevel = height
                        if (previousLevel != null) {
                            currentType = if (currentLevel > previousLevel) TideType.RISING else TideType.FALLING
                        }
                    }
                }
            }

            // include current prediction in the result
            val prediction = TidePrediction(t, height)
            predictions.add(prediction)
            t += Duration.ofMinutes(6).toMillis()
        }

        val resultPredictions =
            predictions.filter {
                it.timestamp >= startDateTime.toEpochMilli() && it.timestamp <= endDateTime.toEpochMilli()
            }

        val resultExtremes =
            allExtremes.filter {
                it.timestamp >= startDateTime.toEpochMilli() && it.timestamp <= endDateTime.toEpochMilli()
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
            extremes = resultExtremes,
            predictions = resultPredictions,
            timeZoneOffsetSeconds = station.timeZoneOffset?.totalSeconds,
        )
    }
}
