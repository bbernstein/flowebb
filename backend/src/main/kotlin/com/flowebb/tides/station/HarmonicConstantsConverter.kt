package com.flowebb.tides.station

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class HarmonicConstantsConverter : AttributeConverter<HarmonicConstants> {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun transformFrom(input: HarmonicConstants): AttributeValue {
        return AttributeValue.builder()
            .s(json.encodeToString(input))
            .build()
    }

    override fun transformTo(input: AttributeValue): HarmonicConstants {
        return json.decodeFromString<HarmonicConstants>(input.s())
    }

    override fun type() = EnhancedType.of(HarmonicConstants::class.java)

    override fun attributeValueType() = AttributeValueType.S
}

@DynamoDbBean
data class StationCacheItem(
    @get:DynamoDbPartitionKey
    var stationId: String = "",
    var name: String? = null,
    var state: String? = null,
    var region: String? = null,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var source: String = "",
    var capabilities: Set<String> = setOf(),
    @get:DynamoDbConvertedBy(HarmonicConstantsConverter::class)
    var harmonicConstants: HarmonicConstants? = null,
    var lastUpdated: Long = 0,
    var ttl: Long = 0
)

class StationListConverter : AttributeConverter<List<NoaaStationMetadata>> {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun transformFrom(input: List<NoaaStationMetadata>): AttributeValue {
        return AttributeValue.builder()
            .s(json.encodeToString(input))
            .build()
    }

    override fun transformTo(input: AttributeValue): List<NoaaStationMetadata> {
        return json.decodeFromString<List<NoaaStationMetadata>>(input.s())
    }

    override fun type(): EnhancedType<List<NoaaStationMetadata>> {
        return EnhancedType.listOf(EnhancedType.of(NoaaStationMetadata::class.java))
    }

    override fun attributeValueType() = AttributeValueType.S
}
