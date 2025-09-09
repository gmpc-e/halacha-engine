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
