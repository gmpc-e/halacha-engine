package com.elad.halachatime.core.tools

import com.elad.halachatime.core.presets.PresetRegistry

fun main(args: Array<String>) {
    val registry = PresetRegistry.fromDefaultLocations()
    if (args.isEmpty()) {
        println("Available presets:")
        registry.listKeys().forEach { key ->
            val p = registry.get(key)!!
            println("- $key : ${p.displayName}  (alos=${p.alosMode.type}:${p.alosMode.value}, tzeit=${p.tzeitMode.type}:${p.tzeitMode.value})")
        }
        println("\nTry: ./gradlew :core-engine:run --args='OR_HACHAIM'")
        return
    }

    val key = args.first()
    val preset = registry.getOrDefault(key)
    println("Resolved preset [$key] -> ${preset.key} : ${preset.displayName}")
    println("  sofZmanReference: ${preset.sofZmanReference}")
    println("  alosMode: ${preset.alosMode}")
    println("  tzeitMode: ${preset.tzeitMode}")
    println("  misheyakirMinutes: ${preset.misheyakirMinutes}")
    println("  notes(en): ${preset.localeNotes["en"]}")
    println("  notes(he): ${preset.localeNotes["he"]}")
}
