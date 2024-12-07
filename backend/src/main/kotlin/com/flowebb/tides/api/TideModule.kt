package com.flowebb.tides.api

import com.flowebb.tides.TideService
import com.flowebb.tides.calculation.TideLevelCalculator
import com.flowebb.tides.station.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureTides() {
    // Initialize HTTP client for NOAA API calls
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Initialize station finders
    val noaaFinder = NoaaStationFinder()

    // Create station service with available finders
    val stationService = StationService(
        mapOf(
            StationSource.NOAA to noaaFinder
        )
    )

    // Create tide calculator with NOAA API support
    val calculator = TideLevelCalculator(client, useNoaaApi = true)

    // Create tide service
    val tideService = TideService(stationService, calculator)

    // Configure routes
    routing {
        tideRoutes(tideService, stationService)
    }
}
