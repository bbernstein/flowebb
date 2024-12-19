package com.flowebb.tides.station

import mu.KotlinLogging

open class StationService(
    private val finders: Map<StationSource, StationFinder>
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun getStation(stationId: String): Station {
        logger.debug { "Looking up station: $stationId" }

        for ((source, finder) in finders) {
            try {
                logger.debug { "Attempting to find station with $source finder" }
                return finder.findStation(stationId)
                    .also { logger.debug { "Found station: $it" } }
            } catch (e: Exception) {
                logger.error(e) { "Failed to find station with $source finder" }
                // Log the full stack trace
                logger.debug { "Stack trace: $e" }
            }
        }
        throw Exception("Station not found: $stationId")
    }

    open suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int = 5,
        preferredSource: StationSource? = null
    ): List<Station> {
        logger.debug {
            "Finding nearest stations: lat=$latitude, lon=$longitude, limit=$limit, " +
                    "preferred=$preferredSource"
        }

        // If preferred source is specified, try it first
        if (preferredSource != null) {
            finders[preferredSource]?.let { finder ->
                try {
                    logger.debug { "Attempting to find stations with preferred source: $preferredSource" }
                    return finder.findNearestStations(
                        latitude,
                        longitude,
                        limit
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to find stations with preferred source" }
                }
            }
        }

        // Try all available sources and return the first success
        for ((source, finder) in finders) {
            try {
                logger.debug { "Attempting to find stations with source: $source" }
                return finder.findNearestStations(
                    latitude,
                    longitude,
                    limit
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to find stations with $source finder" }
                continue
            }
        }

        throw Exception("No stations found in any available source")
    }
}
