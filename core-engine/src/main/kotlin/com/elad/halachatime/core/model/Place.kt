package com.elad.halachatime.core.model

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double = 0.0,
    val timeZoneId: String
)
