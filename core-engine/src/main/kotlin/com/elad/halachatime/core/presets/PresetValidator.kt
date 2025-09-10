package com.elad.halachatime.core.presets

import org.everit.json.schema.Schema
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ValidationIssue(val pointer: String, val message: String)

data class ValidationReport(
    val schema: String = "v2",
    val valid: Boolean,
    val detectedKey: String?,
    val itemCount: Int,
    val warnings: List<ValidationIssue> = emptyList(),
    val errors: List<ValidationIssue> = emptyList(),
    val hint: String? = null
)

object PresetValidator {
    private val schema: Schema by lazy {
        val name = "schemas/board-preset.schema.v2.json"
        val stream = requireNotNull(PresetValidator::class.java.classLoader.getResourceAsStream(name)) {
            "Schema resource not found on classpath: $name"
        }
        stream.use { SchemaLoader.load(JSONObject(JSONTokener(it))) }
    }

    fun validateString(json: String): ValidationReport {
        val root = JSONObject(json)
        val key = root.optString("key", null)
        val items = root.optJSONArray("items") ?: root.optJSONArray("zmanim")
        val itemCount = items?.length() ?: 0

        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        // 1) Schema validation (primary)
        try {
            schema.validate(root)
        } catch (ve: ValidationException) {
            val leafs = if (ve.causingExceptions.isEmpty()) listOf(ve) else ve.causingExceptions
            errors += leafs.map { ValidationIssue(it.pointerToViolation, it.message.orEmpty()) }
        }

        // 2) Light semantic checks (non-fatal warnings)
        if (items != null) {
            warnings += findDuplicateIds(items)
            if (items.length() == 0) {
                warnings += ValidationIssue("#/items", "Preset has no items — engine will not display any zmanim.")
            }
        } else {
            warnings += ValidationIssue("#", "Missing 'items' array — v2 presets must provide 'items'.")
        }

        // Optionally: range sanity for degrees/minutes (only warn; schema already constrained)
        // (Keep minimal: can be extended later)

        val valid = errors.isEmpty()

        val hint = when {
            !valid && errors.any { it.message.contains("required key") } ->
                "Add the missing required field(s). Check property names and nesting per v2 schema."
            !valid && errors.any { it.message.contains("not a valid enum") } ->
                "Use only allowed enum values (ids, resolver.type/mode). See v2 schema enums."
            !valid && errors.any { it.pointer.startsWith("#/items/") } ->
                "Fix item resolver object to match one allowed subschema (MINUTES/DEGREES/METHOD/etc.)."
            else -> null
        }

        return ValidationReport(
            valid = valid,
            detectedKey = key,
            itemCount = itemCount,
            warnings = warnings,
            errors = errors,
            hint = hint
        )
    }

    private fun findDuplicateIds(items: JSONArray): List<ValidationIssue> {
        val seen = mutableMapOf<String, Int>()
        val dups = mutableListOf<ValidationIssue>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            if (id.isNotBlank()) {
                val prev = seen.putIfAbsent(id, i)
                if (prev != null) {
                    dups += ValidationIssue("#/items/$i/id", "Duplicate id '$id' (first at index $prev)")
                }
            }
        }
        return dups
    }
}