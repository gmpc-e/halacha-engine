package com.elad.halachatime.core.presets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

// --- Models (use your existing Models.kt if it already defines these) ---
data class HalachicSource(val ref: String, val summary: String)

enum class ZmanMethodKey {
    ALOT_HASHACHAR, NETZ, SHEMA_END, TEFILLAH_END,
    CHATZOT, MINCHA_GEDOLA, MINCHA_KETANA, PLAG, SHKIA, TZEIT
}

data class MethodDefinition(
    val key: ZmanMethodKey,
    val description: String,
    val kosherJavaMethod: String,
    val sources: List<HalachicSource> = emptyList()
)

data class BoardProfile(
    val key: String,
    val displayName: String,
    val localeNotes: Map<String, String> = emptyMap(),
    val methods: List<MethodDefinition>
)
// -----------------------------------------------------------------------

/**
 * Loads presets:
 * 1) built-ins from classpath "/profiles"
 * 2) external dir (System prop "preset.dir" or env "PRESET_DIR"), overriding by key
 * Validates all JSONs against /schemas/board-preset.schema.json (draft-07).
 */
class PresetRegistry(
    private val schemaResourcePath: String = "/schemas/board-preset.schema.json",
    private val builtinsResourceDir: String = "profiles",
    private val externalDir: Path? = resolveExternalDir()
) {
    private val mapper = jacksonObjectMapper()
    private val schema: Schema
    private val profiles: MutableMap<String, BoardProfile> = linkedMapOf()

    companion object {
        private fun resolveExternalDir(): Path? {
            val sys = System.getProperty("preset.dir")?.trim().takeUnless { it.isNullOrEmpty() }
            val env = System.getenv("PRESET_DIR")?.trim().takeUnless { it.isNullOrEmpty() }
            val chosen = sys ?: env
            return chosen?.let { Path.of(it) }
        }
    }

    init {
        // Load schema
        val schemaStream = javaClass.getResourceAsStream(schemaResourcePath)
            ?: error("Schema not found at $schemaResourcePath")
        val schemaJson = JSONObject(JSONTokener(schemaStream))
        schema = SchemaLoader.load(schemaJson)

        // 1) built-ins
        loadBuiltinsFromClasspath(builtinsResourceDir).forEach { bytes ->
            validateThenPut(bytes)
        }

        // 2) external overrides
        externalDir?.takeIf { Files.exists(it) && it.isDirectory() }?.let { dir ->
            Files.walk(dir).use { paths ->
                paths.filter { it.isRegularFile() && it.toString().endsWith(".json") }
                    .forEach { p -> validateThenPut(Files.readAllBytes(p)) }
            }
        }
    }

    fun get(key: String): BoardProfile? = profiles[key]
    fun keys(): Set<String> = profiles.keys


    // --- helpers ---

    private fun validateThenPut(bytes: ByteArray) {
        val obj = JSONObject(JSONTokener(bytes.inputStream()))
        schema.validate(obj) // throws on invalid
        val model: BoardProfile = mapper.readValue(bytes) // throws on bad enum
        profiles[model.key] = model // external overrides built-in
    }

    private fun loadBuiltinsFromClasspath(resourceDir: String): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val resources: java.util.Enumeration<URL> =
            Thread.currentThread().contextClassLoader.getResources(resourceDir)

        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            when (url.protocol) {
                "file" -> {
                    val root = Path.of(url.toURI())
                    if (Files.exists(root) && Files.isDirectory(root)) {
                        Files.walk(root).use { paths ->
                            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                                .forEach { out += Files.readAllBytes(it) }
                        }
                    }
                }
                "jar" -> {
                    val path = url.path
                    val jarPath = path.substringAfter("jar:file:").substringBefore("!")
                    val inside = path.substringAfter("!").removePrefix("/")

                    JarFile(jarPath).use { jf ->
                        jf.entries().asSequence()
                            .filter { entry ->
                                !entry.isDirectory &&
                                        entry.name.startsWith("$inside/") &&
                                        entry.name.lowercase().endsWith(".json")
                            }
                            .forEach { entry ->
                                jf.getInputStream(entry).use { out += it.readAllBytes() }
                            }
                    }
                }
            }
        }
        return out
    }

}
