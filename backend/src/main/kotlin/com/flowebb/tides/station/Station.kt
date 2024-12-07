package com.flowebb.tides.station

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val name: String?,
    val state: String?,
    val region: String?,
    val distance: Double,
    val latitude: Double,
    val longitude: Double,
    val source: StationSource,
    val capabilities: Set<StationType> = setOf(),
    val harmonicConstants: HarmonicConstants? = null  // New property
)

enum class StationSource {
    NOAA,
    UKHO,  // UK Hydrographic Office
    CHS,   // Canadian Hydrographic Service
    // Add more sources as needed
}

enum class StationType {
    WATER_LEVEL,
    TIDAL_CURRENTS,
    WATER_TEMPERATURE,
    AIR_TEMPERATURE,
    WIND
}
