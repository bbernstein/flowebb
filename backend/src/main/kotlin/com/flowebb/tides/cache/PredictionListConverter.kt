package com.flowebb.tides.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class PredictionListConverter : AttributeConverter<List<CachedPrediction>> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override fun transformFrom(input: List<CachedPrediction>): AttributeValue {
        return AttributeValue.builder()
            .s(json.encodeToString(input))
            .build()
    }

    override fun transformTo(input: AttributeValue): List<CachedPrediction> {
        return if (input.s() != null) {
            json.decodeFromString(input.s())
        } else {
            emptyList()
        }
    }

    override fun attributeValueType(): AttributeValueType = AttributeValueType.S

    override fun type(): EnhancedType<List<CachedPrediction>> =
        EnhancedType.listOf(CachedPrediction::class.java)
}

class ExtremeListConverter : AttributeConverter<List<CachedExtreme>> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override fun transformFrom(input: List<CachedExtreme>): AttributeValue {
        return AttributeValue.builder()
            .s(json.encodeToString(input))
            .build()
    }

    override fun transformTo(input: AttributeValue): List<CachedExtreme> {
        return if (input.s() != null) {
            json.decodeFromString(input.s())
        } else {
            emptyList()
        }
    }

    override fun attributeValueType(): AttributeValueType = AttributeValueType.S

    override fun type(): EnhancedType<List<CachedExtreme>> =
        EnhancedType.listOf(CachedExtreme::class.java)
}
