package com.elad.halachatime.core.presets

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.file.Files
import java.nio.file.Path

class PresetRegistry(
    private val externalDirs: List<Path> = emptyList()
) {
    private val mapper = jacksonObjectMapper().apply {
        findAndRegisterModules()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val schema by lazy {
        javaClass.classLoader.getResourceAsStream("schemas/board-preset.schema.json").use { stream ->
            requireNotNull(stream) { "Schema resource not found: schemas/board-preset.schema.json" }
            val raw = JSONObject(JSONTokener(stream))
            SchemaLoader.load(raw)
        }
    }

    private val builtins: MutableMap<String, BoardPreset> = mutableMapOf(
        "GRA" to BoardPreset(
            key = "GRA",
            displayName = "Vilna Gaon (GRA)",
            sofZmanReference = SofZmanRef.GRA,
            alosMode = OffsetMode(OffsetType.DEGREES, 16.1),
            tzeitMode = OffsetMode(OffsetType.DEGREES, 7.083),
            misheyakirMinutes = 35
        ),
        "MGA" to BoardPreset(
            key = "MGA",
            displayName = "Magen Avraham (MGA)",
            sofZmanReference = SofZmanRef.MGA,
            alosMode = OffsetMode(OffsetType.FIXED_MINUTES, 72.0),
            tzeitMode = OffsetMode(OffsetType.FIXED_MINUTES, 72.0),
            misheyakirMinutes = 35
        )
    )

    private val merged: MutableMap<String, BoardPreset> by lazy {
        val map = builtins.toMutableMap()
        loadExternal().forEach { ext -> map[ext.key] = ext }
        map
    }

    fun listKeys(): List<String> = merged.keys.sorted()
    fun get(key: String): BoardPreset? = merged[key]
    fun getOrDefault(key: String, defaultKey: String = "GRA"): BoardPreset =
        merged[key] ?: merged[defaultKey] ?: error("Default preset '$defaultKey' not found")

    private fun loadExternal(): List<BoardPreset> {
        if (externalDirs.isEmpty()) return emptyList()
        val results = mutableListOf<BoardPreset>()
        externalDirs.forEach { dir ->
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return@forEach
            Files.walk(dir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                    .forEach { path ->
                        val json = Files.readString(path)
                        validate(json, path)
                        val preset: BoardPreset = mapper.readValue(json)
                        results += preset
                    }
            }
        }
        return results
    }

    private fun validate(json: String, origin: Path) {
        try {
            val obj = JSONObject(json)
            schema.validate(obj)
        } catch (e: Exception) {
            throw IllegalArgumentException("Preset JSON failed schema validation at $origin: ${e.message}", e)
        }
    }

    companion object {
        fun fromDefaultLocations(): PresetRegistry {
            val cwd = Path.of("").toAbsolutePath().normalize()
            val profilesDir = cwd.resolve("profiles")
            val candidates = listOf(
                profilesDir,
                profilesDir.resolve("builtin"),
                profilesDir.resolve("community"),
            ).filter { Files.exists(it) }
            return PresetRegistry(externalDirs = candidates)
        }
    }
}
