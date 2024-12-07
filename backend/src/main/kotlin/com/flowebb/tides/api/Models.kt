// src/main/kotlin/com/flowebb/tides/api/Models.kt
package com.flowebb.tides.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String
)

@Serializable
data class TideRequest(
    val latitude: Double,
    val longitude: Double
)
