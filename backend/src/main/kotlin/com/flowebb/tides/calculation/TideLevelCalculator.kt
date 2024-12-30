package com.flowebb.tides.calculation

import com.flowebb.http.HttpClientService
import com.flowebb.tides.cache.CachedExtreme
import com.flowebb.tides.cache.CachedPrediction
import com.flowebb.tides.cache.TidePredictionCache
import com.flowebb.tides.cache.TidePredictionRecord
import com.flowebb.tides.station.Station
import io.ktor.client.call.body
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TideLevelCalculator(
    private val httpClient: HttpClientService = HttpClientService(),
    private val cache: TidePredictionCache = TidePredictionCache(),
) {
    private val noaaDateFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC)

    fun getStationZoneId(station: Station): ZoneId =
        station.timeZoneOffset?.let { ZoneId.ofOffset("", it) }
            ?: ZoneOffset.UTC

    suspend fun getCachedDayData(
        station: Station,
        dates: List<LocalDate>,
        zoneId: ZoneId,
    ): List<TidePredictionRecord> =
        coroutineScope {
            val results =
                dates
                    .map { date ->
                        async {
                            val cachedData = cache.getPredictions(station.id, date)
                            if (cachedData != null) {
                                cachedData
                            } else {
                                val fetchedData =
                                    if (station.stationType == "S") {
                                        val extremes = fetchNoaaExtremes(station, date, zoneId)
                                        TidePredictionRecord(
                                            stationId = station.id,
                                            date = date.format(DateTimeFormatter.ISO_DATE),
                                            stationType = "S",
                                            predictions = emptyList(),
                                            extremes =
                                            extremes.map {
                                                CachedExtreme(it.timestamp, it.height, it.type.toString())
                                            },
                                            lastUpdated = System.currentTimeMillis(),
                                            ttl = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000, // 7 days
                                        )
                                    } else {
                                        val predictions = fetchNoaaPredictions(station, date, zoneId)
                                        val extremes = fetchNoaaExtremes(station, date, zoneId)
                                        TidePredictionRecord(
                                            stationId = station.id,
                                            date = date.format(DateTimeFormatter.ISO_DATE),
                                            stationType = "R",
                                            predictions =
                                            predictions.map {
                                                CachedPrediction(it.timestamp, it.height)
                                            },
                                            extremes =
                                            extremes.map {
                                                CachedExtreme(it.timestamp, it.height, it.type.toString())
                                            },
                                            lastUpdated = System.currentTimeMillis(),
                                            ttl = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000, // 7 days
                                        )
                                    }
                                cache.savePredictions(fetchedData)
                                fetchedData
                            }
                        }
                    }.awaitAll()

            results
        }

//    suspend fun getCachedDayData(
//        station: Station,
//        dates: List<LocalDate>,
//        zoneId: ZoneId
//    ): List<TidePredictionRecord> = coroutineScope {
//        // First, try to get all dates from cache
//        val cachedData = dates.associateWith { date ->
//            async {
//                cache.getPredictions(station.id, date)
//            }
//        }.mapValues { it.value.await() }
//
//        // For dates not in cache, fetch them in parallel
//        val missingDates = cachedData.filterValues { it == null }.keys.toList()
//        val fetchedData = if (missingDates.isNotEmpty()) {
//            // Fetch all missing data in parallel
//            val fetchResults = missingDates.map { date ->
//                async {
//                    if (station.stationType == "S") {
//                        val extremes = fetchNoaaExtremes(station, date, zoneId)
//                        date to TidePredictionRecord(
//                            stationId = station.id,
//                            date = date.format(DateTimeFormatter.ISO_DATE),
//                            stationType = "S",
//                            predictions = emptyList(),
//                            extremes = extremes.map {
//                                CachedExtreme(it.timestamp, it.height, it.type.toString())
//                            },
//                            lastUpdated = System.currentTimeMillis(),
//                            ttl = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000 // 7 days
//                        )
//                    } else {
//                        val predictions = fetchNoaaPredictions(station, date, zoneId)
//                        val extremes = fetchNoaaExtremes(station, date, zoneId)
//                        date to TidePredictionRecord(
//                            stationId = station.id,
//                            date = date.format(DateTimeFormatter.ISO_DATE),
//                            stationType = "R",
//                            predictions = predictions.map {
//                                CachedPrediction(it.timestamp, it.height)
//                            },
//                            extremes = extremes.map {
//                                CachedExtreme(it.timestamp, it.height, it.type.toString())
//                            },
//                            lastUpdated = System.currentTimeMillis(),
//                            ttl = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000 // 7 days
//                        )
//                    }
//                }
//            }.awaitAll().toMap()
//
//            // Save all fetched records in a batch
//            cache.savePredictionsBatch(fetchResults.values.toList())
//
//            fetchResults.values.toList()
//        } else {
//            emptyList()
//        }
//
//        // Combine cached and freshly fetched data
//        dates.map { date ->
//            cachedData[date] ?: fetchedData.find {
//                it.date == date.format(DateTimeFormatter.ISO_DATE)
//            } ?: throw IllegalStateException("Failed to get data for date: $date")
//        }
//    }

    internal fun interpolateExtremes(
        extremes: List<TideExtreme>,
        timestamp: Long,
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
        timestamp: Long,
    ): Double {
        val sorted = predictions.sortedBy { it.timestamp }

        val idx = sorted.binarySearch { it.timestamp.compareTo(timestamp) }

        return if (idx >= 0) {
            sorted[idx].height
        } else {
            val insertionPoint = -(idx + 1)
            if (insertionPoint == 0 || insertionPoint >= sorted.size) {
                throw IllegalStateException("No predictions available for the requested time $timestamp")
            } else {
                val before = sorted[insertionPoint - 1]
                val after = sorted[insertionPoint]

                // Linear interpolation
                val ratio = (timestamp - before.timestamp).toDouble() / (after.timestamp - before.timestamp)
                before.height + (after.height - before.height) * ratio
            }
        }
    }

    private suspend fun fetchNoaaPredictions(
        station: Station,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TidePrediction> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams =
            mapOf(
                "station" to station.id,
                "begin_date" to dateStr,
                "end_date" to dateStr,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "6",
            ),
        ) { response ->
            response.body<NoaaResponse>().predictions.map { prediction ->
                TidePrediction(
                    timestamp =
                    LocalDateTime
                        .parse(prediction.t, noaaDateFormatter)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    height = prediction.v.toDouble(),
                )
            }
        }
    }

    private suspend fun fetchNoaaExtremes(
        station: Station,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TideExtreme> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        return httpClient.get(
            url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter",
            queryParams =
            mapOf(
                "station" to station.id,
                "begin_date" to dateStr,
                "end_date" to dateStr,
                "product" to "predictions",
                "datum" to "MLLW",
                "units" to "english",
                "time_zone" to "lst",
                "format" to "json",
                "interval" to "hilo",
            ),
        ) { response ->
            response.body<NoaaResponse>().predictions.map { prediction ->
                TideExtreme(
                    type = if (prediction.type == "H") TideType.HIGH else TideType.LOW,
                    timestamp =
                    LocalDateTime
                        .parse(prediction.t, noaaDateFormatter)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    height = prediction.v.toDouble(),
                )
            }
        }
    }
}
