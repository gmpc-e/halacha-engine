package com.elad.halachatime.core.presets

import com.fasterxml.jackson.annotation.JsonAlias

data class BoardPreset(
    val key: String,
    val displayName: String,
    val dayModel: String? = null,

    // v2 canonical field is "items". We also accept "zmanim" in JSON.
    @JsonAlias("zmanim")
    val items: List<BoardItem> = emptyList(),

    val options: Map<String, Any?>? = null,
    val notes: Map<String, String>? = null
)

data class BoardItem(
    val id: String,
    val label: Map<String, String>? = null,
    val resolver: Map<String, Any?>? = null,
    val visible: Boolean? = null,
    val order: Int? = null,
    val kosherJavaMethod: String? = null,
    val notes: Map<String, String>? = null
)
