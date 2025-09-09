package com.elad.halachatime.core.presets

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.everit.json.schema.Schema
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension

class PresetRegistry(
    private val externalDirs: List<Path> = emptyList()
) {
    private val mapper = jacksonObjectMapper().apply {
        findAndRegisterModules()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // v2 schema only
    private val schema: Schema by lazy {
        val name = "schemas/board-preset.schema.v2.json"
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("Schema resource not found on classpath: $name")
        stream.use { SchemaLoader.load(JSONObject(JSONTokener(it))) }
    }

    private data class Loaded(val fileId: String, val preset: BoardPreset)

    private val merged: Map<String, BoardPreset> by lazy {
        val found = mutableListOf<Loaded>()
        found += loadFromClasspathDir("presets")
        found += loadFromExternalDirs()

        if (found.isEmpty()) {
            System.err.println("[PresetRegistry] WARNING: no valid v2 presets found")
        }

        val out = linkedMapOf<String, BoardPreset>()
        for (x in found) {
            val k = x.preset.key
            if (k.isBlank()) {
                System.err.println("[PresetRegistry] SKIP ${x.fileId}: missing 'key'")
                continue
            }
            out[k] = x.preset
        }
        out
    }

    fun listProfiles(): List<BoardPreset> = merged.values.sortedBy { it.displayName }
    fun getProfile(key: String): BoardPreset? = merged[key]

    // ---------- loaders ----------

    private fun loadFromExternalDirs(): List<Loaded> {
        if (externalDirs.isEmpty()) return emptyList()
        val out = mutableListOf<Loaded>()
        externalDirs.forEach { dir ->
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return@forEach
            Files.walk(dir).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.extension == "json" }
                    .forEach { path ->
                        val fileId = "fs:${path.fileName}"
                        runCatching {
                            val json = Files.readString(path)
                            validateOrThrow(json, fileId)
                            val preset: BoardPreset = mapper.readValue(json)
                            out += Loaded(fileId, preset)
                            println("[PresetRegistry] OK ${path.fileName} key='${preset.key}', items=${preset.items.size}")
                        }.onFailure { ex ->
                            System.err.println("[PresetRegistry] SKIP invalid preset: ${path.fileName} → ${ex.message}")
                        }
                    }
            }
        }
        return out
    }

    private fun loadFromClasspathDir(dir: String): List<Loaded> {
        val cl = javaClass.classLoader
        val roots: List<URL> = cl.getResources(dir).toList()
        val out = mutableListOf<Loaded>()
        for (root in roots) {
            when (root.protocol) {
                "file" -> {
                    val p = Path.of(root.toURI())
                    if (Files.isDirectory(p)) {
                        Files.list(p).use { stream ->
                            stream.filter { Files.isRegularFile(it) && it.extension == "json" }
                                .forEach { path ->
                                    val fileId = "cp:${path.fileName}"
                                    runCatching {
                                        val json = Files.readString(path)
                                        validateOrThrow(json, fileId)
                                        val preset: BoardPreset = mapper.readValue(json)
                                        out += Loaded(fileId, preset)
                                        println("[PresetRegistry] OK ${path.fileName} key='${preset.key}', items=${preset.items.size}")
                                    }.onFailure { ex ->
                                        System.err.println("[PresetRegistry] SKIP invalid preset: ${path.fileName} → ${ex.message}")
                                    }
                                }
                        }
                    }
                }
                "jar" -> {
                    val spec = root.file // e.g. file:/.../app.jar!/presets
                    val bang = spec.indexOf("!")
                    if (bang > 0) {
                        val jarUrl = spec.substring(0, bang).removePrefix("file:")
                        JarFile(jarUrl).use { jar ->
                            val entries = jar.entries().toList()
                                .filter { !it.isDirectory && it.name.startsWith("$dir/") && it.name.endsWith(".json") }
                            for (e in entries) {
                                val fileId = "cp:${e.name.substringAfterLast('/')}"
                                runCatching {
                                    val json = jar.getInputStream(e).use(InputStream::readBytes).toString(Charsets.UTF_8)
                                    validateOrThrow(json, fileId)
                                    val preset: BoardPreset = mapper.readValue(json)
                                    out += Loaded(fileId, preset)
                                    println("[PresetRegistry] OK $fileId key='${preset.key}', items=${preset.items.size}")
                                }.onFailure { ex ->
                                    System.err.println("[PresetRegistry] SKIP invalid preset: $fileId → ${ex.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun validateOrThrow(json: String, fileId: String) {
        try {
            schema.validate(JSONObject(json))
        } catch (ve: ValidationException) {
            val subs = if (ve.causingExceptions.isEmpty()) listOf(ve) else ve.causingExceptions
            val msg = buildString {
                append("INVALID $fileId: ${subs.size} schema violations found")
                subs.take(12).forEach { sub ->
                    append("\n  • ${sub.pointerToViolation.ifBlank { "#"} }: ${sub.message}")
                }
            }
            throw IllegalArgumentException(msg)
        }
    }

    companion object {
        fun fromDefaultLocations(): PresetRegistry {
            val cwd = Path.of("").toAbsolutePath().normalize()
            val presetDirProp = System.getProperty("preset.dir")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            val presetDirEnv  = System.getenv("PRESET_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            val profilesDir   = cwd.resolve("profiles") // legacy optional

            val candidates = listOfNotNull(presetDirProp, presetDirEnv, profilesDir)
                .filter { Files.exists(it) && Files.isDirectory(it) }

            // NOTE: This registry always loads classpath /presets as well.
            return PresetRegistry(candidates)
        }
    }
}
