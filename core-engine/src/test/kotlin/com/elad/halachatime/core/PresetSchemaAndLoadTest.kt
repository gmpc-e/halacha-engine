package com.elad.halachatime.core

import com.elad.halachatime.core.presets.BoardPreset
import com.elad.halachatime.core.presets.PresetRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/* ---------- reflection compat helpers (unchanged) ---------- */

private fun listProfilesCompat(reg: PresetRegistry): List<BoardPreset> {
    val f = PresetRegistry::class.declaredFunctions.firstOrNull {
        it.name in setOf("listProfiles", "list", "all") && it.parameters.size == 1
    } ?: return emptyList()
    f.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return runCatching { f.call(reg) as List<BoardPreset> }.getOrElse { emptyList() }
}

private fun getProfileCompat(reg: PresetRegistry, key: String): BoardPreset? {
    val f = PresetRegistry::class.declaredFunctions.firstOrNull {
        it.name in setOf("getProfile", "get") && it.parameters.size == 2
    } ?: return null
    f.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return runCatching { f.call(reg, key) as BoardPreset? }.getOrNull()
}

/* ---------- fixtures dir ---------- */

private fun fixturesDir(): Path {
    val url = requireNotNull(
        Thread.currentThread().contextClassLoader.getResource("presets-fixtures")
    ) { "presets-fixtures/ not found on test classpath" }
    return Path.of(url.toURI())
}

/* ---------- tests ---------- */

class PresetSchemaAndLoadTest {

    @Test
    fun `registry loads only valid v2 presets from fixtures dir`() {
        val dir = fixturesDir()
        println("[TEST] Using fixtures dir: $dir (exists=${dir.exists()})")

        // Print the JSON fixture files we’re about to validate/load
        val jsonFiles = Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .map { it.fileName.toString() }
                .toList()
        }
        println("[TEST] Fixture JSON files: $jsonFiles")

        val reg = PresetRegistry(listOf(dir))
        val profiles = listProfilesCompat(reg)
        val keys = profiles.map { it.key }
        println("[TEST] Loaded keys: $keys")

        assertTrue(keys.contains("UNIT_GOOD"), "Expected UNIT_GOOD to load")
        assertFalse(keys.contains("UNIT_BAD"), "UNIT_BAD should be rejected by schema")

        val good = getProfileCompat(reg, "UNIT_GOOD")
        assertNotNull(good, "UNIT_GOOD should be retrievable")
        assertTrue(good!!.items.isNotEmpty(), "UNIT_GOOD should have items")
        println("[TEST] UNIT_GOOD -> items=${good.items.size}")
    }

    @Test
    fun `getProfile returns the exact preset with items`() {
        val dir = fixturesDir()
        val reg = PresetRegistry(listOf(dir))
        val p = getProfileCompat(reg, "UNIT_GOOD")
        println("[TEST] UNIT_GOOD -> ${p?.let { "items=${it.items.size}" } ?: "null"}")
        assertNotNull(p)
        assertTrue(p!!.items.isNotEmpty())
    }
}