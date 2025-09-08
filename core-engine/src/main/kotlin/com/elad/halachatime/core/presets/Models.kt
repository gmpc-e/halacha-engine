package com.elad.halachatime.core.presets

enum class SofZmanRef { GRA, MGA, BAAL_HA_TANYA, ATERET_TORAH, CUSTOM }
enum class OffsetType { FIXED_MINUTES, DEGREES, ZMANI_MINUTES, THREE_STARS, NONE }

data class OffsetMode(
    val type: OffsetType,
    val value: Double? = null
)

data class BoardPreset(
    val key: String,
    val displayName: String,
    val sofZmanReference: SofZmanRef,
    val alosMode: OffsetMode = OffsetMode(OffsetType.NONE),
    val tzeitMode: OffsetMode = OffsetMode(OffsetType.NONE),
    val misheyakirMinutes: Int? = null,
    val localeNotes: Map<String, String> = emptyMap(),
    val notes: String? = null
)
