package com.elad.halachatime.core.presets

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class PresetRegistry(
    private val externalDirs: List<Path> = emptyList()
) {
    private val mapper = jacksonObjectMapper().apply {
        findAndRegisterModules()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
    }

    private val schema by lazy {
        val schemaStream = javaClass.getResourceAsStream("/schemas/board-preset.schema.json")
            ?: error("Missing JSON schema on classpath: /schemas/board-preset.schema.json")
        schemaStream.use {
            SchemaLoader.builder()
                .schemaJson(JSONObject(JSONTokener(it)))
                .draftV7Support() // draft-07
                .build()
                .load()
                .build()
        }
    }

    private val byKey: Map<String, BoardPreset> by lazy {
        // 1) Load built-ins from classpath: /profiles/*.json
        val builtins = loadClasspathProfiles("/profiles")

        // 2) Load external overrides/additions from provided directories
        val externals = externalDirs
            .flatMap { dir ->
                if (!Files.exists(dir)) emptyList()
                else Files.walk(dir)
                    .filter { it.isRegularFile() && it.fileName.toString().lowercase().endsWith(".json") }
                    .map { it to Files.newInputStream(it) }
                    .use { seq -> seq.map { (p, s) -> p.fileName.toString() to s }.toList() }
            }
            .flatMap { (name, stream) ->
                stream.use { listOf(readAndValidate(it)) }
            }
            .associateBy { it.key }

        // 3) Merge: external wins on same key; union on new keys
        val merged = HashMap<String, BoardPreset>()
        for (p in builtins) merged[p.key] = p
        for ((k, v) in externals) merged[k] = v
        merged.toMap()
    }

    fun list(): List<BoardPreset> = byKey.values.sortedBy { it.key }
    fun get(key: String): BoardPreset? = byKey[key]

    private fun loadClasspathProfiles(root: String): List<BoardPreset> {
        // Works both from classes directory and from a JAR
        val url = javaClass.getResource(root) ?: return emptyList()
        val protocol = url.protocol
        val out = mutableListOf<BoardPreset>()

        if (protocol == "jar") {
            // Running from a jar
            val path = url.path // e.g. "file:/.../app.jar!/profiles"
            val bang = path.indexOf("!")
            val jarPath = path.substring(5, bang) // strip "file:"
            val inside = path.substring(bang + 2) // strip "!/"
            JarFile(jarPath).use { jf ->
                jf.stream().forEach { entry ->
                    if (!entry.isDirectory && entry.name.startsWith("$inside/") && entry.name.lowercase().endsWith(".json")) {
                        jf.getInputStream(entry).use { out += readAndValidate(it) }
                    }
                }
            }
        } else {
            // Running from classes dir
            val dirUrl = javaClass.getResource("$root/") ?: return emptyList()
            val dirPath = Path.of(dirUrl.toURI())
            if (dirPath.isDirectory()) {
                Files.list(dirPath).use { paths ->
                    paths.filter { it.isRegularFile() && it.fileName.toString().lowercase().endsWith(".json") }
                        .forEach { p -> Files.newInputStream(p).use { out += readAndValidate(it) } }
                }
            }
        }
        return out
    }

    private fun readAndValidate(stream: InputStream): BoardPreset {
        val text = stream.reader(Charsets.UTF_8).readText()
        val json = JSONObject(text)
        schema.validate(json) // throws on invalid
        return mapper.readValue<BoardPreset>(text)
    }

    companion object {
        fun fromDefaultLocations(): PresetRegistry {
            val cwd = Path.of("").toAbsolutePath().normalize()
            val candidates = listOf(
                cwd.resolve("profiles"),
                cwd.resolve("profiles/builtin"),
                cwd.resolve("profiles/community")
            ).filter { Files.exists(it) }
            return PresetRegistry(externalDirs = candidates)
        }
    }
}
