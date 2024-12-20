package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import com.flowebb.tides.station.Station
import io.ktor.client.call.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.*
import java.time.format.DateTimeFormatter
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import kotlin.text.toDouble

open class TideLevelCalculator(
    private val httpClient: HttpClientService = HttpClientService()
) {
    private val logger = KotlinLogging.logger {}
    private val noaaDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneOffset.UTC)

    internal fun getStationZoneId(station: Station): ZoneId {
        return station.timeZoneOffset?.let { ZoneId.ofOffset("", it) }
            ?: ZoneOffset.UTC
    }

    suspend fun calculateLevel(
        station: Station,
        timestamp: Long
    ): Double = withContext(Dispatchers.IO) {
        try {
            getNoaaLevel(station, timestamp)
                .also { logger.debug { "Got NOAA level for ts: $timestamp station ${station.id}: $it" } }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get NOAA level for station ${station.id}" }
            throw e
        }
    }

    private suspend fun getNoaaLevel(station: Station, timestamp: Long): Double = withContext(Dispatchers.IO) {
        val zoneId = getStationZoneId(station)
        val zoneOffset = zoneId.rules.getOffset(Instant.ofEpochMilli(timestamp))
        val instant = Instant.ofEpochMilli(timestamp)
        val stationTime = instant.atZone(zoneId)

        val beginInstant = stationTime.withHour(0).withMinute(0).withSecond(0).toInstant().minus(Duration.ofHours(24))
        val endInstant = beginInstant.plus(Duration.ofDays(1)).plus(Duration.ofHours(24))

        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm")
            .withZone(zoneId)

        val beginDate = dateFormatter.format(beginInstant)
        val endDate = dateFormatter.format(endInstant)

        logger.debug { "Getting NOAA level for station ${station.id} (type: ${station.stationType}) between $beginDate and $endDate" }

        // for subordinate stations (S), use HILO and interpolate
        val predictions = if (station.stationType == "S") {
            getHiloPredictions(station, beginDate, endDate)
        } else {
            getDetailedPredictions(station, beginDate, endDate)
        }

        interpolatePredictions(predictions, timestamp, zoneOffset)
    }

    private suspend fun getDetailedPredictions(
        station: Station,
        beginDate: String,
        endDate: String
    ): List<NoaaPrediction> {
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to beginDate,
                "end_date" to endDate,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "6"
            )
        ) { response ->
            response.body<NoaaResponse>().predictions
        }
    }

    private suspend fun getHiloPredictions(
        station: Station,
        beginDate: String,
        endDate: String
    ): List<NoaaPrediction> {
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to beginDate,
                "end_date" to endDate,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "hilo"
            )
        ) { response ->
            response.body<NoaaResponse>().predictions
        }
    }

    private fun interpolatePredictions(predictions: List<NoaaPrediction>, timestamp: Long, zoneOffset: ZoneOffset): Double {
        val sorted = predictions.sortedBy {
            LocalDateTime.parse(it.t, noaaDateFormatter)
                .toInstant(zoneOffset)
                .toEpochMilli()
        }

        val idx = sorted.binarySearch {
            LocalDateTime.parse(it.t, noaaDateFormatter)
                .toInstant(zoneOffset)
                .toEpochMilli()
                .compareTo(timestamp)
        }

        logger.debug { "Interpolating prediction for timestamp $timestamp in zone offset $zoneOffset idx=$idx" }

        return if (idx >= 0) {
            sorted[idx].v.toDouble()
        } else {
            val insertionPoint = -(idx + 1)
            if (insertionPoint == 0 || insertionPoint >= sorted.size) {
                throw IllegalStateException("No predictions available for the requested time")
            } else {
                val before = sorted[insertionPoint - 1]
                val after = sorted[insertionPoint]

                val t1 = LocalDateTime.parse(before.t, noaaDateFormatter)
                    .toInstant(zoneOffset)
                    .toEpochMilli()
                val t2 = LocalDateTime.parse(after.t, noaaDateFormatter)
                    .toInstant(zoneOffset)
                    .toEpochMilli()
                val v1 = before.v.toDouble()
                val v2 = after.v.toDouble()

                // Linear interpolation
                v1 + (v2 - v1) * (timestamp - t1) / (t2 - t1)
            }
        }
    }

    fun determineTideType(currentLevel: Double, previousLevel: Double): TideType {
        return when {
            currentLevel > previousLevel + 0.1 -> TideType.RISING
            currentLevel < previousLevel - 0.1 -> TideType.FALLING
            currentLevel > 6.0 -> TideType.HIGH
            else -> TideType.LOW
        }
    }

    suspend fun getCurrentTideLevel(
        station: Station,
        timestamp: Long
    ): TideLevel {
        val currentLevel = calculateLevel(station, timestamp)
        val previousLevel = calculateLevel(station, timestamp - 3600000)

        return TideLevel(
            waterLevel = currentLevel,
            predictedLevel = currentLevel,
            type = determineTideType(currentLevel, previousLevel)
        )
    }

    suspend fun findExtremes(
        station: Station,
        startTime: Long,
        endTime: Long
    ): List<TideExtreme> = withContext(Dispatchers.IO) {
        getNoaaExtremes(station, startTime, endTime)
    }

    suspend fun getPredictions(
        station: Station,
        startTime: Long,
        endTime: Long,
        interval: Duration
    ): List<TidePrediction> = withContext(Dispatchers.IO) {
        val zoneId = getStationZoneId(station)

        // For subordinate stations (S), use HILO and interpolate
        if (station.stationType == "S") {
            logger.debug { "Using HILO predictions for subordinate station ${station.id}" }
            // getting HiLo predictions for 24 hours before and 24 hours after the given range
            val startTimeEarlier = startTime - Duration.ofHours(24).toMillis()
            val endTimeLater = endTime + Duration.ofHours(24).toMillis()

            // Get HILO predictions and interpolate
            getNoaaExtremes(station, startTimeEarlier, endTimeLater).let { extremes ->
                interpolateExtremes(extremes, startTimeEarlier, endTimeLater, interval)
            }
        } else {
            // For reference stations (R), use detailed predictions
            logger.debug { "Using detailed predictions for reference station ${station.id}" }
            getNoaaPredictions(station, startTime, endTime, zoneId, interval)
        }
    }

    private fun interpolateExtremes(
        extremes: List<TideExtreme>,
        startTime: Long,
        endTime: Long,
        interval: Duration
    ): List<TidePrediction> {
        if (extremes.isEmpty()) return emptyList()

        val baseTime = extremes.first().timestamp
        val times = extremes.map { (it.timestamp - baseTime).toDouble() / 60000 }.toDoubleArray() // Normalize to minutes and start from zero
        val heights = extremes.map { it.height }.toDoubleArray()

        val splineInterpolator = SplineInterpolator()
        val splineFunction: PolynomialSplineFunction = splineInterpolator.interpolate(times, heights)

        val predictions = mutableListOf<TidePrediction>()
        var currentTime = startTime

        while (currentTime <= endTime) {
            val normalizedTime = (currentTime - baseTime).toDouble() / 60000 // Normalize to minutes and start from zero
            val height = when {
                normalizedTime < times.first() -> heights.first() // Use the first height if before the range
                normalizedTime > times.last() -> heights.last() // Use the last height if after the range
                else -> splineFunction.value(normalizedTime) // Interpolate within the range
            }
            predictions.add(
                TidePrediction(
                    timestamp = currentTime,
                    height = height
                )
            )
            currentTime += interval.toMillis()
        }

        return predictions
    }

    private suspend fun getNoaaPredictions(
        station: Station,
        startTime: Long,
        endTime: Long,
        zoneId: ZoneId,
        interval: Duration = Duration.ofMinutes(6)
    ): List<TidePrediction> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(zoneId)

        val beginDate = dateFormatter.format(Instant.ofEpochMilli(startTime))
        val endDate = dateFormatter.format(Instant.ofEpochMilli(endTime))

        val response = httpClient.get<NoaaResponse>(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to beginDate,
                "end_date" to endDate,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "${interval.toMinutes()}"
            )
        ) { it.body() }

        return response.predictions.map { prediction ->
            TidePrediction(
                timestamp = LocalDateTime.parse(prediction.t, noaaDateFormatter)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                height = prediction.v.toDouble()
            )
        }
    }

    private suspend fun getNoaaExtremes(
        station: Station,
        startTime: Long,
        endTime: Long
    ): List<TideExtreme> {
        val zoneId = getStationZoneId(station)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm")
            .withZone(zoneId)

        val beginDate = dateFormatter.format(Instant.ofEpochMilli(startTime))
        val endDate = dateFormatter.format(Instant.ofEpochMilli(endTime))

        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to beginDate,
                "end_date" to endDate,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "hilo"
            )
        ) { response ->
            response.body<NoaaResponse>().predictions.map { prediction ->
                TideExtreme(
                    type = if (prediction.type == "H") TideType.HIGH else TideType.LOW,
                    timestamp = LocalDateTime.parse(prediction.t, noaaDateFormatter)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    height = prediction.v.toDouble()
                )
            }
        }
    }
}
