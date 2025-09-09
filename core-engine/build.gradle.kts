plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // KosherJava Zmanim
    implementation("com.kosherjava:zmanim:2.5.0")

    // JSON Schema validation
    implementation("com.github.erosb:everit-json-schema:1.14.6")
    implementation("org.json:json:20240303")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Validate preset JSONs against the schema before check/build.
 *
 * Usage:
 *   ./gradlew :core-engine:validatePresets \
 *     -Dpreset.dir=core-engine/src/main/resources/presets
 *
 * Or export PRESET_DIR and omit -D:
 *   export PRESET_DIR=core-engine/src/main/resources/presets
 *   ./gradlew :core-engine:validatePresets
 */
tasks.register<JavaExec>("validatePresets") {
    group = "verification"
    description = "Validate board preset JSONs against the schema"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.elad.halachatime.core.presets.PresetValidateCli")

    // Prefer -Dpreset.dir, then PRESET_DIR, then default to module resources
    val defaultDir = file("src/main/resources/presets").absolutePath
    val dirFromSysProp = System.getProperty("preset.dir")
    val dirFromEnv = System.getenv("PRESET_DIR")
    val effectiveDir = dirFromSysProp ?: dirFromEnv ?: defaultDir

    jvmArgs("-Dpreset.dir=$effectiveDir")
}

// Gate the lifecycle tasks on validation
tasks.named("check") { dependsOn("validatePresets") }
tasks.named("build") { dependsOn("validatePresets") }
