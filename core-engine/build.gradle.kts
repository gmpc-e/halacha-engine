plugins {
    kotlin("jvm") // inherits version (2.0.21) from root
}

kotlin {
    jvmToolchain(17)
}

// No repositories block here — settings.gradle.kts owns it.

dependencies {
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // JSON Schema validation
    implementation("com.github.erosb:everit-json-schema:1.14.6")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}
