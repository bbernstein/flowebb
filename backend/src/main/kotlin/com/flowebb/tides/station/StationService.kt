package com.flowebb.tides.station

open class StationService(
    private val finders: Map<StationSource, StationFinder>
) {
    open suspend fun getStation(stationId: String): Station {
        // Try each finder until we find the station
        for ((_, finder) in finders) {
            try {
                return finder.findStation(stationId)
            } catch (e: Exception) {
                continue
            }
        }
        throw Exception("Station not found: $stationId")
    }

    open suspend fun findNearestStation(
        latitude: Double,
        longitude: Double,
        preferredSource: StationSource? = null
    ): Station {
        val stations = findNearestStations(latitude, longitude, 1, preferredSource)
        return stations.firstOrNull() ?: throw Exception("No station found")
    }

    open suspend fun findNearestStations(
        latitude: Double,
        longitude: Double,
        limit: Int = 5,
        preferredSource: StationSource? = null
    ): List<Station> {
        // If preferred source is specified, try it first
        if (preferredSource != null) {
            finders[preferredSource]?.let { finder ->
                try {
                    return finder.findNearestStations(latitude, longitude, limit)
                } catch (e: Exception) {
                    // Log error but continue to try other sources
                }
            }
        }

        // Try all available sources and return the first success
        for ((_, finder) in finders) {
            try {
                return finder.findNearestStations(latitude, longitude, limit)
            } catch (e: Exception) {
                // Log error but continue to next source
                continue
            }
        }

        throw Exception("No stations found in any available source")
    }
}
