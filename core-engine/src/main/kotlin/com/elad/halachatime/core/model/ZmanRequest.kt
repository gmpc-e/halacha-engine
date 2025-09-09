package com.elad.halachatime.core.model

import java.time.LocalDate

data class ZmanRequest(
    val date: LocalDate,
    val place: Place,
    val presetKey: String = "GRA"  // future: use profile config to choose variants
)
