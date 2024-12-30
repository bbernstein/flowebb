package com.flowebb.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.flowebb.tides.api.BaseHandler
import com.flowebb.tides.api.StationsResponse
import com.flowebb.tides.station.DynamoStationFinder
import com.flowebb.tides.station.StationService
import com.flowebb.tides.station.StationSource
import mu.KotlinLogging

@Suppress("unused")
class StationsLambda : BaseHandler() {
    private val logger = KotlinLogging.logger {}

    private val stationService = StationService(
        mapOf(
            StationSource.NOAA to DynamoStationFinder(),
        ),
    )

    override suspend fun handleRequestSuspend(
        input: APIGatewayProxyRequestEvent,
        context: Context,
    ): APIGatewayProxyResponseEvent {
        logger.info { "Received request with parameters: ${input.queryStringParameters}" }

        val params = input.queryStringParameters ?: mapOf()
        val requireHarmonicConstants = params["requireHarmonicConstants"]?.toBoolean() ?: false

        return try {
            val stations = when {
                params["stationId"] != null -> {
                    logger.info { "Looking up station by ID: ${params["stationId"]}" }
                    listOf(stationService.getStation(params["stationId"]!!))
                }
                params["lat"] != null && params["lon"] != null -> {
                    val lat = params["lat"]!!.toDouble()
                    val lon = params["lon"]!!.toDouble()

                    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                        logger.error { "Invalid coordinates: lat=$lat, lon=$lon" }
                        return error("Invalid coordinates")
                    }

                    logger.info { "Finding nearest stations for lat=$lat, lon=$lon, requireHarmonicConstants=$requireHarmonicConstants" }
                    stationService.findNearestStations(
                        latitude = lat,
                        longitude = lon,
                        preferredSource = null,
                    )
                }
                else -> {
                    logger.error { "Missing required parameters" }
                    return error("Missing required parameters")
                }
            }

            logger.info { "Found ${stations.size} stations" }
            success(StationsResponse(stations = stations))
        } catch (e: Exception) {
            logger.error(e) { "Error processing request" }
            error("Error processing request: ${e.message}")
        }
    }
}
