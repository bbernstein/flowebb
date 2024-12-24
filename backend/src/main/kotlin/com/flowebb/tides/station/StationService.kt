package com.flowebb.tides.station

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging

open class StationService(
    private val finders: Map<StationSource, StationFinder>
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun getStation(stationId: String): Station = coroutineScope {
        logger.debug { "Looking up station: $stationId" }

        // Create async tasks for all finders
        val results = finders.map { (source, finder) ->
            async {
                try {
                    logger.debug { "Attempting to find station with $source finder" }
                    Result.success(finder.findStation(stationId))
                } catch (e: Exception) {
                    logger.error(e) { "Failed to find station with $source finder" }
                    logger.debug { "Stack trace: $e" }
                    Result.failure(e)
                }
            }
        }

        // Wait for first successful result or all failures
        results.awaitAll()
            .firstOrNull { it.isSuccess }
            ?.getOrNull()
            ?: throw Exception("Station not found: $stationId")
    }

    open suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int = 5,
        preferredSource: StationSource? = null
    ): List<Station> = coroutineScope {
        logger.debug {
            "Finding nearest stations: lat=$latitude, lon=$longitude, limit=$limit, " +
                    "preferred=$preferredSource"
        }

        // If preferred source is specified, try it first
        if (preferredSource != null) {
            finders[preferredSource]?.let { finder ->
                try {
                    logger.debug { "Attempting to find stations with preferred source: $preferredSource" }
                    return@coroutineScope finder.findNearestStations(
                        latitude,
                        longitude,
                        limit
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to find stations with preferred source" }
                }
            }
        }

        // Try all available sources in parallel
        val results = finders.map { (source, finder) ->
            async {
                try {
                    logger.debug { "Attempting to find stations with source: $source" }
                    Result.success(
                        finder.findNearestStations(
                            latitude,
                            longitude,
                            limit
                        )
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to find stations with $source finder" }
                    Result.failure(e)
                }
            }
        }

        // Return first successful result or throw if all fail
        results.awaitAll()
            .firstOrNull { it.isSuccess }
            ?.getOrNull()
            ?: throw Exception("No stations found in any available source")
    }
}
