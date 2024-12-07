package com.flowebb.tides.calculation

import com.flowebb.tides.station.Station
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.*

open class TideLevelCalculator(
    private val client: HttpClient? = null,
    private val useNoaaApi: Boolean = false
) {
    open suspend fun calculateLevel(station: Station, timestamp: Long): Double = withContext(Dispatchers.IO) {
        if (useNoaaApi && client != null) {
            try {
                getNoaaLevel(station.id, timestamp)
            } catch (e: Exception) {
                calculateHarmonicLevel(station, timestamp)
            }
        } else {
            calculateHarmonicLevel(station, timestamp)
        }
    }

    private suspend fun getNoaaLevel(stationId: String, timestamp: Long): Double = withContext(Dispatchers.IO) {
        val beginInstant = Instant.ofEpochMilli(timestamp - 6 * 3600000) // 6 hours before
        val endInstant = Instant.ofEpochMilli(timestamp + 6 * 3600000)   // 6 hours after

        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm")
            .withZone(ZoneOffset.UTC)

        val beginDate = dateFormatter.format(beginInstant)
        val endDate = dateFormatter.format(endInstant)

        val response = client!!.get("https://api.tidesandcurrents.noaa.gov/api/prod/datagetter") {
            parameter("station", stationId)
            parameter("begin_date", beginDate)
            parameter("end_date", endDate)
            parameter("product", "predictions")
            parameter("datum", "MLLW")
            parameter("units", "english")
            parameter("time_zone", "gmt")
            parameter("format", "json")
        }

        val noaaResponse = response.body<NoaaResponse>()
        interpolatePredictions(noaaResponse.predictions, timestamp)
    }

    private fun interpolatePredictions(predictions: List<NoaaPrediction>, timestamp: Long): Double {
        val sorted = predictions.sortedBy { Instant.parse(it.t).toEpochMilli() }
        val idx = sorted.binarySearch {
            Instant.parse(it.t).toEpochMilli().compareTo(timestamp)
        }

        return if (idx >= 0) {
            sorted[idx].v.toDouble()
        } else {
            val insertionPoint = -(idx + 1)
            if (insertionPoint == 0 || insertionPoint >= sorted.size) {
                sorted.firstOrNull()?.v?.toDouble() ?: calculateHarmonicLevel(null, timestamp)
            } else {
                val before = sorted[insertionPoint - 1]
                val after = sorted[insertionPoint]

                val t1 = Instant.parse(before.t).toEpochMilli()
                val t2 = Instant.parse(after.t).toEpochMilli()
                val v1 = before.v.toDouble()
                val v2 = after.v.toDouble()

                // Linear interpolation
                v1 + (v2 - v1) * (timestamp - t1) / (t2 - t1)
            }
        }
    }

    private fun calculateHarmonicLevel(station: Station?, timestamp: Long): Double {
        if (station?.harmonicConstants != null) {
            val hours = timestamp / 3600000.0

            // Calculate the contribution of each constituent
            val constituentsSum = station.harmonicConstants.constituents.sumOf { constituent ->
                val speedRadians = Math.toRadians(constituent.speed)
                val phaseRadians = Math.toRadians(constituent.phase)
                constituent.amplitude * cos(speedRadians * hours + phaseRadians)
            }

            return station.harmonicConstants.meanSeaLevel + constituentsSum
        }

        return defaultHarmonicCalculation(timestamp)
    }

    private fun defaultHarmonicCalculation(timestamp: Long): Double {
        val hours = timestamp / 3600000.0

        val m2Speed = 2 * PI / HarmonicConstants.M2
        val s2Speed = 2 * PI / HarmonicConstants.S2
        val n2Speed = 2 * PI / HarmonicConstants.N2
        val k1Speed = 2 * PI / HarmonicConstants.K1
        val o1Speed = 2 * PI / HarmonicConstants.O1

        val m2 = 2.0 * cos(m2Speed * hours)
        val s2 = 1.0 * cos(s2Speed * hours + PI / 4)
        val n2 = 0.5 * cos(n2Speed * hours + PI / 3)
        val k1 = 0.5 * cos(k1Speed * hours + PI / 6)
        val o1 = 0.3 * cos(o1Speed * hours + PI / 2)

        return 4.0 + m2 + s2 + n2 + k1 + o1
    }

    fun determineTideType(currentLevel: Double, previousLevel: Double): TideType {
        return when {
            currentLevel > previousLevel + 0.1 -> TideType.RISING
            currentLevel < previousLevel - 0.1 -> TideType.FALLING
            currentLevel > 6.0 -> TideType.HIGH
            else -> TideType.LOW
        }
    }

    open suspend fun getCurrentTideLevel(
        station: Station,
        timestamp: Long,
        forceHarmonicCalculation: Boolean = false
    ): TideLevel {
        val currentLevel = if (forceHarmonicCalculation) {
            calculateHarmonicLevel(station, timestamp)
        } else {
            calculateLevel(station, timestamp)
        }

        val previousLevel = if (forceHarmonicCalculation) {
            calculateHarmonicLevel(station, timestamp - 3600000)
        } else {
            calculateLevel(station, timestamp - 3600000)
        }

        return TideLevel(
            waterLevel = currentLevel,
            predictedLevel = currentLevel,
            type = determineTideType(currentLevel, previousLevel)
        )
    }
}
