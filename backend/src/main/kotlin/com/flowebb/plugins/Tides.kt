// src/main/kotlin/com/flowebb/plugins/Tides.kt
package com.flowebb.plugins

import com.flowebb.tides.api.configureTides
import io.ktor.server.application.*

fun Application.configureTides() {
    configureTides()
}
