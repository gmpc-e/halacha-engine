package com.elad.halachatime.core.presets

sealed interface Resolver {
    data class Degrees(val value: Double, val method: DegreeMethod): Resolver
    data class Minutes(val minutes: Int, val mode: MinutesMode): Resolver
    data class Formula(val value: String): Resolver

    enum class DegreeMethod { ALOS, MISHEYAKIR, TZAIS_GEONIM }
    enum class MinutesMode { ALOS_BEFORE_SUNRISE_SEA, TZAIS_AFTER_SUNSET_SEA }
}
