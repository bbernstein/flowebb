package com.flowebb.tides.station

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.ZoneOffset

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ZoneOffset::class)
object ZoneOffsetSerializer : KSerializer<ZoneOffset> {
    override fun serialize(encoder: Encoder, value: ZoneOffset) {
        // Store as total seconds to handle non-hour offsets
        encoder.encodeString(value.totalSeconds.toString())
    }

    override fun deserialize(decoder: Decoder): ZoneOffset {
        return ZoneOffset.ofTotalSeconds(decoder.decodeString().toInt())
    }
}

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
    @Serializable(with = ZoneOffsetSerializer::class)
    val timeZoneOffset: ZoneOffset? = null,
    val level: String? = null,
    val stationType: String? = null  // Added stationType field
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
