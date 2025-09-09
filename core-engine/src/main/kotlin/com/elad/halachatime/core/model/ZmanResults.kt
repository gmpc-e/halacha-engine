package com.elad.halachatime.core.model

import java.time.Instant

enum class ZmanMethodKey {
    ALOT_HASHACHAR, MISHEYAKIR, NETZ, SHEMA_END, TEFILLAH_END, CHATZOT,
    MINCHA_GEDOLA, MINCHA_KETANA, PLAG, SHKIA, TZEIT
}

/**
 * Results:
 * - results: UTC instants
 * - resultsLocal: ISO strings in Place.timeZoneId
 * - methodMap: upstream ComplexZmanimCalendar method used
 */
data class ZmanResults(
    val preset: String,
    val place: Place,
    val date: String,                     // yyyy-MM-dd
    val results: Map<ZmanMethodKey, Instant?>,
    val resultsLocal: Map<ZmanMethodKey, String?>,
    val methodMap: Map<ZmanMethodKey, String>,
    val meta: Map<String, Any?> = emptyMap()
)
