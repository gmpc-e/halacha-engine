plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
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

    // ✅ REMOVE (was on main classpath and not needed)
    // implementation("junit:junit:4.12")

    // Tests (JUnit5)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

// ---- CLI: validate presets on build/check ----
tasks.register<JavaExec>("validatePresets") {
    group = "verification"
    description = "Validate board preset JSONs against the v2 schema"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.elad.halachatime.core.presets.PresetValidateCli")
    jvmArgs(
        "-Dpreset.dir=" + (
                System.getProperty("preset.dir")
                    ?: System.getenv("PRESET_DIR")
                    ?: "${project.projectDir}/src/main/resources/presets"
                )
    )
}

tasks.named("check") { dependsOn("validatePresets") }
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true   // <— show println() output
    }
}