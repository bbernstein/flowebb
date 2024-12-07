package com.flowebb.tides.api

import com.flowebb.tides.TideService
import com.flowebb.tides.station.StationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.Route

fun Route.tideRoutes(tideService: TideService, stationService: StationService) {
    route("/api") {
        get("/tides") {
            val latitude = call.parameters["lat"]?.toDoubleOrNull()
            val longitude = call.parameters["lon"]?.toDoubleOrNull()
            val stationId = call.parameters["stationId"]
            val useCalculation = call.parameters["useCalculation"]?.toBoolean() ?: false

            try {
                val tideInfo = when {
                    // If stationId is provided, use it directly
                    stationId != null -> {
                        tideService.getCurrentTideForStation(stationId, useCalculation)
                    }
                    // If lat/lon provided, find nearest station
                    latitude != null && longitude != null -> {
                        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Latitude must be between -90 and 90, longitude between -180 and 180")
                            )
                            return@get
                        }
                        tideService.getCurrentTide(latitude, longitude, useCalculation)
                    }
                    // If neither provided, return error
                    else -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Must provide either stationId or latitude/longitude parameters")
                        )
                        return@get
                    }
                }

                call.respond(HttpStatusCode.OK, tideInfo)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Error calculating tide: ${e.message}")
                )
            }
        }

        get("/stations") {
            val latitude = call.parameters["lat"]?.toDoubleOrNull()
            val longitude = call.parameters["lon"]?.toDoubleOrNull()
            val stationId = call.parameters["stationId"]

            try {
                val stations = when {
                    stationId != null -> {
                        listOf(stationService.getStation(stationId))
                    }
                    latitude != null && longitude != null -> {
                        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Latitude must be between -90 and 90, longitude between -180 and 180")
                            )
                            return@get
                        }
                        stationService.findNearestStations(latitude, longitude, 10)
                    }
                    else -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Must provide either stationId or latitude/longitude parameters")
                        )
                        return@get
                    }
                }
                call.respond(HttpStatusCode.OK, stations)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Error finding stations: ${e.message}")
                )
            }
        }
    }
}
