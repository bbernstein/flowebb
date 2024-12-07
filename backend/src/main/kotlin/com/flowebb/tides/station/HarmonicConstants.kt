package com.flowebb.tides.station

import kotlinx.serialization.Serializable

@Serializable
data class HarmonicConstituent(
    val name: String,
    val speed: Double,  // Angular speed in degrees per hour
    val amplitude: Double, // Amplitude in feet
    val phase: Double    // Phase in degrees
)

@Serializable
data class HarmonicConstants(
    val stationId: String,
    val meanSeaLevel: Double,
    val constituents: List<HarmonicConstituent>
)
