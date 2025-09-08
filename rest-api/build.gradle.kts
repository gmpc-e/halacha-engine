plugins {
    application
    kotlin("jvm")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

application {
    // Entry point for `./gradlew :rest-api:run`
    mainClass.set("com.elad.halacha.rest.ServerKt")
}

dependencies {
    implementation(project(":core-engine"))

    // Ktor server (no Ktor Gradle plugin needed)
    implementation("io.ktor:ktor-server-core-jvm:3.0.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-jackson:3.0.1")

    testImplementation(kotlin("test"))
}
