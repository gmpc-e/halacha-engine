plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-engine"))

    // Ktor server
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-jackson:2.3.12")



    // Jackson JavaTime module (fixes JavaTimeModule unresolved)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // ✅ REMOVE Compose Desktop (this was causing resolution errors)
    // implementation("androidx.compose.ui:ui-desktop:1.7.0")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("io.ktor:ktor-server-test-host:2.3.12")
}

application {
    mainClass.set("com.elad.halacha.rest.ServerKt")
}

tasks.test {
    useJUnitPlatform()
}
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true   // <— show println() output
    }
}