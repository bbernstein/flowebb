package com.flowebb.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.flowebb.tides.api.BaseHandler
import com.flowebb.tides.TideService
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.station.StationService
import com.flowebb.tides.station.DynamoStationFinder
import com.flowebb.tides.station.StationSource

@Suppress("unused")
class TidesLambda : BaseHandler() {
    private val tideService = TideService(
        stationService = StationService(mapOf(
            StationSource.NOAA to DynamoStationFinder()
        )),
        calculator = TideLevelCalculator()
    )

    override suspend fun handleRequestSuspend(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent {
        val params = input.queryStringParameters ?: mapOf()

        return try {
            val tideInfo = when {
                params["stationId"] != null -> {
                    tideService.getCurrentTideForStation(
                        params["stationId"]!!
                    )
                }
                params["lat"] != null && params["lon"] != null -> {
                    val lat = params["lat"]!!.toDouble()
                    val lon = params["lon"]!!.toDouble()

                    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                        return error("Invalid coordinates")
                    }

                    tideService.getCurrentTide(
                        lat,
                        lon
                    )
                }
                else -> return error("Missing required parameters")
            }

            success(tideInfo)
        } catch (e: Exception) {
            error(e.message ?: "Internal Server Error")
        }
    }
}
