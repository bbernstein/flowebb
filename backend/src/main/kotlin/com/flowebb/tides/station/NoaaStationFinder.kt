package com.flowebb.tides.station

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.days

@Serializable
private data class NoaaStationMetadata(
    val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val state: String? = null,
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
    val abbrev: String? = null
)

@Serializable
private data class NoaaStationsResponse(
    val stationList: List<NoaaStationMetadata>
)

@Serializable
private data class CacheEntry(
    val timestamp: Long,
    val stationList: List<NoaaStationMetadata>
)

@Serializable
private data class NoaaHarmonicResponse(
    val HarmonicConstituents: List<NoaaConstituent>
)

@Serializable
private data class NoaaConstituent(
    val name: String,
    val speed: Double,
    val amplitude: Double,
    val phase_GMT: Double
)

@Serializable
private data class HarmonicCache(
    val timestamp: Long,
    val harmonicData: Map<String, HarmonicConstants>
)

class NoaaStationFinder(
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true  // Add this line
                isLenient = true
                prettyPrint = true
                encodeDefaults = true
            })
        }
    },
    private val stationsCacheFile: File = File("tide-stations-cache.json"),
    private val harmonicsCacheFile: File = File("tide-harmonics-cache.json")
) : StationFinder {
    private val json = Json {
        ignoreUnknownKeys = true  // Add this line
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val stationCacheValidityPeriod = 7.days.inWholeMilliseconds
    private val harmonicCacheValidityPeriod = 365.days.inWholeMilliseconds
    private var harmonicCache: Map<String, HarmonicConstants> = loadHarmonicCache()

    private suspend fun fetchHarmonicConstants(stationId: String): HarmonicConstants? {
        return try {
            val response =
                client.get("https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/$stationId/harcon.json")
            val harmonicData = response.body<NoaaHarmonicResponse>()

            HarmonicConstants(
                stationId = stationId,
                meanSeaLevel = harmonicData.HarmonicConstituents
                    .find { it.name == "Z0" }?.amplitude ?: 0.0,
                constituents = harmonicData.HarmonicConstituents
                    .filter { it.name != "Z0" }
                    .map { constituent ->
                        HarmonicConstituent(
                            name = constituent.name,
                            speed = constituent.speed,
                            amplitude = constituent.amplitude,
                            phase = constituent.phase_GMT
                        )
                    }
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun findStation(stationId: String): Station {
        val stations = fetchStationList()
        val stationData = stations.find { it.stationId == stationId }
            ?: throw Exception("Station not found: $stationId")

        val harmonicConstants = fetchHarmonicConstants(stationId)

        println("Found station: ${stationData.stationName} constants: $harmonicConstants")
        return Station(
            id = stationData.stationId,
            name = stationData.stationName,
            state = stationData.state,
            region = null,
            distance = 0.0, // No distance since we're looking up directly
            latitude = stationData.lat,
            longitude = stationData.lon,
            source = StationSource.NOAA,
            capabilities = setOf(StationType.WATER_LEVEL),
            harmonicConstants = harmonicConstants
        )
    }

    private fun loadHarmonicCache(): Map<String, HarmonicConstants> {
        if (!harmonicsCacheFile.exists()) return emptyMap()

        return try {
            val cacheEntry = json.decodeFromString<HarmonicCache>(harmonicsCacheFile.readText())
            val age = Instant.now().toEpochMilli() - cacheEntry.timestamp

            if (age < harmonicCacheValidityPeriod) {
                cacheEntry.harmonicData
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            // If there's any error reading the cache, return empty map
            emptyMap()
        }
    }

    private suspend fun getHarmonicConstants(stationId: String): HarmonicConstants? {
        harmonicCache[stationId]?.let { return it }

        val constants = fetchHarmonicConstants(stationId)
        if (constants != null) {
            harmonicCache = harmonicCache + (stationId to constants)
            saveHarmonicCache()
        }
        return constants
    }

    private fun saveHarmonicCache() {
        val cacheEntry = HarmonicCache(
            timestamp = Instant.now().toEpochMilli(),
            harmonicData = harmonicCache
        )
        harmonicsCacheFile.writeText(json.encodeToString(cacheEntry))
    }

    override suspend fun findNearestStations(latitude: Double, longitude: Double, limit: Int): List<Station> {
        val stations = fetchStationList()

        val candidateStations = stations
            .map { station ->
                Pair(station, calculateDistance(latitude, longitude, station.lat, station.lon))
            }
            .sortedBy { it.second }
            .take(limit)

        return candidateStations
            .mapNotNull { (stationData, distance) ->
                val harmonicConstants = getHarmonicConstants(stationData.stationId)
                if (harmonicConstants?.constituents?.isNotEmpty() == true) {
                    Station(
                        id = stationData.stationId,
                        name = stationData.stationName,
                        state = stationData.state,
                        region = null,
                        distance = distance,
                        latitude = stationData.lat,
                        longitude = stationData.lon,
                        source = StationSource.NOAA,
                        capabilities = setOf(StationType.WATER_LEVEL),
                        harmonicConstants = harmonicConstants
                    )
                } else {
                    null
                }
            }
    }

    private suspend fun fetchStationList(): List<NoaaStationMetadata> {
        if (stationsCacheFile.exists()) {
            val cacheEntry = json.decodeFromString<CacheEntry>(stationsCacheFile.readText())
            val age = Instant.now().toEpochMilli() - cacheEntry.timestamp

            if (age < stationCacheValidityPeriod) {
                return cacheEntry.stationList
            }
        }

        val response = client.get("https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/tidepredstations.json")
        val stationsData = response.body<NoaaStationsResponse>()

        val cacheEntry = CacheEntry(
            timestamp = Instant.now().toEpochMilli(),
            stationList = stationsData.stationList,
        )
        stationsCacheFile.writeText(json.encodeToString<CacheEntry>(cacheEntry))

        return stationsData.stationList
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in kilometers

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c
    }
}
