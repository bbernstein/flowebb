// src/main/kotlin/com/flowebb/tides/calculation/HarmonicConstants.kt
package com.flowebb.tides.calculation

object HarmonicConstants {
    // These will be used later for actual tide calculations
    const val LUNAR_DAY = 24.84 // hours
    const val SOLAR_DAY = 24.0  // hours
    const val LUNAR_MONTH = 29.53059 // days

    // Main tidal constituents (to be used later)
    const val M2 = 12.42 // Principal lunar semidiurnal
    const val S2 = 12.00 // Principal solar semidiurnal
    const val N2 = 12.66 // Larger lunar elliptic semidiurnal
    const val K1 = 23.93 // Lunar diurnal
    const val O1 = 25.82 // Principal lunar diurnal
    const val M4 = 6.21  // Shallow water overtides of principal lunar
}
