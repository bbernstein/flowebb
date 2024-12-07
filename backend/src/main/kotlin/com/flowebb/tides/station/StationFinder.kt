// src/main/kotlin/com/flowebb/tides/station/StationFinder.kt
package com.flowebb.tides.station

interface StationFinder {
    suspend fun findNearestStations(latitude: Double, longitude: Double, limit: Int = 5): List<Station>
    suspend fun findStation(stationId: String): Station
}
