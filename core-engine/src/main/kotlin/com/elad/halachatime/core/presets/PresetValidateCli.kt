package com.elad.halachatime.core.presets

import org.everit.json.schema.Schema
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * CLI validator for board preset JSON files.
 *
 * Exit codes:
 *  0 = all presets valid
 *  1 = one or more presets invalid
 *  2 = directory not found
 *  3 = schema resource missing
 */
object PresetValidateCli {

    @JvmStatic
    fun main(args: Array<String>) {
        val presetDir = System.getProperty("preset.dir")
            ?: System.getenv("PRESET_DIR")
            ?: Path.of("").toAbsolutePath()
                .resolve("core-engine/src/main/resources/presets")
                .toString()

        val base = Path.of(presetDir)
        if (!Files.exists(base) || !Files.isDirectory(base)) {
            System.err.println("[validate-presets] Directory not found: $presetDir")
            exitProcess(2)
        }

        val schemaV2 = loadSchemaOrNull("schemas/board-preset.schema.v2.json")
        val schemaV1 = loadSchemaOrNull("schemas/board-preset.schema.json")
        if (schemaV2 == null && schemaV1 == null) {
            System.err.println("[validate-presets] Schema resources not found on classpath.")
            exitProcess(3)
        }

        var ok = true
        Files.walk(base).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .sorted()
                .forEach { path ->
                    val jsonText = Files.readString(path)
                    val obj = JSONObject(jsonText)

                    // choose schema: try v2 first; if it fails, try v1; if both fail → invalid
                    val result = validateWithFallback(obj, schemaV2, schemaV1)

                    when (result) {
                        is ValidationResult.Valid -> {
                            val key = obj.optString("key", "<no-key>")
                            val itemsCount = obj.optJSONArray("zmanim")?.length() ?: 0
                            val schemaName = if (result.usedV2) "v2" else "v1"
                            println("[validate-presets] OK ${path.fileName} key='$key', schema=$schemaName, items=$itemsCount")
                        }
                        is ValidationResult.Invalid -> {
                            ok = false
                            System.err.println("[validate-presets] INVALID ${path.fileName}: ${result.messages.size} issue(s)")
                            result.messages.forEach { (ptr, msg) ->
                                System.err.println("  • $ptr: $msg")
                            }
                        }
                    }
                }
        }

        if (!ok) exitProcess(1)
    }

    private fun validateWithFallback(
        obj: JSONObject,
        schemaV2: Schema?,
        schemaV1: Schema?
    ): ValidationResult {
        // Try v2 first if present
        val v2 = schemaV2?.let { tryValidate(obj, it) }
        if (v2 is ValidationResult.Valid) return v2.copy(usedV2 = true)
        // If v2 failed and v1 exists, try v1
        val v1 = schemaV1?.let { tryValidate(obj, it) }
        if (v1 is ValidationResult.Valid) return v1.copy(usedV2 = false)

        // Merge errors if we have both attempts
        val messages = mutableListOf<Pair<String, String>>()
        if (v2 is ValidationResult.Invalid) messages += v2.messages
        if (v1 is ValidationResult.Invalid) messages += v1.messages

        // If neither schema existed, mark invalid with a single message
        if (schemaV2 == null && schemaV1 == null) {
            messages += "" to "No schemas available"
        }

        return ValidationResult.Invalid(messages.ifEmpty { listOf("" to "Failed schema validation") })
    }

    private fun tryValidate(obj: JSONObject, schema: Schema): ValidationResult =
        try {
            schema.validate(obj)
            ValidationResult.Valid()
        } catch (ve: ValidationException) {
            ValidationResult.Invalid(flatten(ve))
        } catch (ex: Exception) {
            ValidationResult.Invalid(listOf("" to (ex.message ?: ex::class.simpleName)) as List<Pair<String, String>>)
        }

    private fun flatten(ve: ValidationException): List<Pair<String, String>> {
        if (ve.causingExceptions.isEmpty()) {
            return listOf(ve.pointerToViolation to (ve.message ?: "validation error"))
        }
        val out = mutableListOf<Pair<String, String>>()
        ve.causingExceptions.forEach { sub -> out += flatten(sub) }
        return out
    }

    private fun loadSchemaOrNull(resourcePath: String): Schema? {
        val stream = PresetValidateCli::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: return null
        stream.use {
            return SchemaLoader.load(JSONObject(JSONTokener(it)))
        }
    }

    private sealed class ValidationResult {
        data class Valid(val usedV2: Boolean = true) : ValidationResult()
        data class Invalid(val messages: List<Pair<String, String>>) : ValidationResult()
    }
}
