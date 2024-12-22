package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import com.flowebb.tides.cache.TidePredictionCache
import com.flowebb.tides.cache.TidePredictionRecord
import com.flowebb.tides.station.Station
import io.ktor.client.call.*
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import java.time.*
import java.time.format.DateTimeFormatter

class TideLevelCalculator(
    private val httpClient: HttpClientService = HttpClientService(),
    private val cache: TidePredictionCache = TidePredictionCache()
) {
    private val noaaDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneOffset.UTC)

    fun getStationZoneId(station: Station): ZoneId {
        return station.timeZoneOffset?.let { ZoneId.ofOffset("", it) }
            ?: ZoneOffset.UTC
    }

    suspend fun getCachedDayData(
        station: Station,
        dates: List<LocalDate>,
        zoneId: ZoneId
    ): List<TidePredictionRecord> {
        return dates.map { date ->
            cache.getPredictions(station.id, date) ?: run {
                // If not in cache, fetch from NOAA
                if (station.stationType == "S") {
                    val extremes = fetchNoaaExtremes(station, date, zoneId)
                    cache.savePredictions(station.id, date, emptyList(), extremes, "S")
                } else {
                    val predictions = fetchNoaaPredictions(station, date, zoneId)
                    val extremes = fetchNoaaExtremes(station, date, zoneId)
                    cache.savePredictions(station.id, date, predictions, extremes, "R")
                }
                cache.getPredictions(station.id, date)!!
            }
        }
    }

    internal fun interpolateExtremes(
        extremes: List<TideExtreme>,
        timestamp: Long
    ): Double {
        if (extremes.isEmpty()) throw IllegalStateException("No extremes available for interpolation")

        val sortedExtremes = extremes.sortedBy { it.timestamp }
        val baseTime = sortedExtremes.first().timestamp

        val times = sortedExtremes.map { (it.timestamp - baseTime).toDouble() / 60000 }.toDoubleArray()
        val heights = sortedExtremes.map { it.height }.toDoubleArray()

        val splineInterpolator = SplineInterpolator()
        val splineFunction: PolynomialSplineFunction = splineInterpolator.interpolate(times, heights)

        val normalizedTime = (timestamp - baseTime).toDouble() / 60000
        return when {
            normalizedTime < times.first() -> heights.first()
            normalizedTime > times.last() -> heights.last()
            else -> splineFunction.value(normalizedTime)
        }
    }

    internal fun interpolatePredictions(
        predictions: List<TidePrediction>,
        timestamp: Long
    ): Double {
        val sorted = predictions.sortedBy { it.timestamp }

        val idx = sorted.binarySearch { it.timestamp.compareTo(timestamp) }

        return if (idx >= 0) {
            sorted[idx].height
        } else {
            val insertionPoint = -(idx + 1)
            if (insertionPoint == 0 || insertionPoint >= sorted.size) {
                throw IllegalStateException("No predictions available for the requested time")
            } else {
                val before = sorted[insertionPoint - 1]
                val after = sorted[insertionPoint]

                // Linear interpolation
                val ratio = (timestamp - before.timestamp).toDouble() / (after.timestamp - before.timestamp)
                before.height + (after.height - before.height) * ratio
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

    private suspend fun fetchNoaaPredictions(
        station: Station,
        date: LocalDate,
        zoneId: ZoneId
    ): List<TidePrediction> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to dateStr,
                "end_date" to dateStr,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "6"
            )
        ) { response ->
            response.body<NoaaResponse>().predictions.map { prediction ->
                TidePrediction(
                    timestamp = LocalDateTime.parse(prediction.t, noaaDateFormatter)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    height = prediction.v.toDouble()
                )
            }
        }
    }

    private suspend fun fetchNoaaExtremes(
        station: Station,
        date: LocalDate,
        zoneId: ZoneId
    ): List<TideExtreme> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams = mapOf(
                "station" to station.id,
                "begin_date" to dateStr,
                "end_date" to dateStr,
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
