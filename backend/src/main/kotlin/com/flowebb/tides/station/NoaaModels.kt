package com.flowebb.tides.station

import kotlinx.serialization.Serializable

@Serializable
data class NoaaStationMetadata(
    val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val state: String? = null,
    val type: String? = null, // This maps to stationType
    val distance: Double? = null,
    val stationName: String? = null,
    val region: String? = null,
    val commonName: String? = null,
    val stationFullName: String? = null,
    val etidesStnName: String? = null,
    val timeZoneCorr: String? = null,
    val refStationId: String? = null,
    val stationType: String? = null,
    val parentGeoGroupId: String? = null,
    val seq: String? = null,
    val geoGroupId: String? = null,
    val geoGroupName: String? = null,
    val level: String? = null,
    val geoGroupType: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class NoaaStationsResponse(
    val stationList: List<NoaaStationMetadata>,
)
