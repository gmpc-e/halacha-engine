package com.elad.halachatime.core.presets

/**
 * Pretty-printers for v2-only presets.
 * Assumes BoardPreset exposes: key, displayName, dayModel?, items: List<BoardItem>, notes?: Map<String,String>
 * And BoardItem exposes: id, label?: Map<String,String>, resolver?: Map<String, Any?>,
 *                        visible?: Boolean, order?: Int, kosherJavaMethod?: String, notes?: Map<String,String>
 */

private fun Map<String, String>?.prettyNotes(): String =
    if (this.isNullOrEmpty()) "—"
    else entries.joinToString(" | ") { (k, v) -> "$k: $v" }

/** Compact echo of a resolver map that can be one of: DEGREES / MINUTES / FORMULA */
private fun Map<String, Any?>?.prettyResolver(): String {
    if (this == null || this.isEmpty()) return "—"
    val type = (this["type"] as? String)?.uppercase() ?: "—"
    return when (type) {
        "DEGREES" -> {
            val kind = (this["kind"] ?: this["method"])?.toString() ?: "—"
            val deg  = (this["degrees"] ?: this["value"])?.toString() ?: "—"
            "DEGREES(kind=$kind, degrees=$deg)"
        }
        "MINUTES" -> {
            val kind     = (this["kind"] ?: this["method"])?.toString() ?: "—"
            val minutes  = (this["minutes"] ?: this["value"])?.toString() ?: "—"
            val baseline = (this["baseline"] ?: this["mode"])?.toString()
            if (baseline.isNullOrBlank()) "MINUTES(kind=$kind, minutes=$minutes)"
            else "MINUTES(kind=$kind, minutes=$minutes, baseline=$baseline)"
        }
        "FORMULA" -> {
            val name = (this["name"] ?: this["value"])?.toString() ?: "—"
            "FORMULA($name)"
        }
        else -> this.toString()
    }
}

/**
 * Pretty-print a single BoardPreset (v2).
 */
fun printPreset(p: BoardPreset): String = buildString {
    appendLine("key: ${p.key}")
    appendLine("name: ${p.displayName}")
    appendLine("dayModel: ${p.dayModel ?: "—"}")
    appendLine("notes: ${p.notes.prettyNotes()}")

    val items = p.items
    if (items.isEmpty()) {
        appendLine("items: —")
        return@buildString
    }

    appendLine("items (${items.size}):")
    items.sortedBy { it.order ?: Int.MAX_VALUE }.forEach { itm ->
        append("  - id="); append(itm.id)
        append(", visible="); append(itm.visible ?: true)
        append(", order="); append(itm.order ?: "—")
        appendLine()

        val lblHe = itm.label?.get("he") ?: "—"
        val lblEn = itm.label?.get("en") ?: "—"
        appendLine("    label.he: $lblHe")
        appendLine("    label.en: $lblEn")

        appendLine("    resolver: ${itm.resolver.prettyResolver()}")
        appendLine("    kosherJavaMethod: ${itm.kosherJavaMethod ?: "—"}")
        val notes = itm.notes?.entries?.joinToString { (k, v) -> "$k: $v" } ?: "—"
        appendLine("    notes: $notes")
    }
}

/** Pretty-print a list of presets (one after another). */
fun printPresets(list: List<BoardPreset>): String =
    list.joinToString(separator = "\n" + "-".repeat(60) + "\n") { printPreset(it) }
