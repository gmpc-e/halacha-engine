plugins {
    id("io.ktor.plugin")
    kotlin("jvm")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
application { mainClass.set("com.elad.halacha.rest.ServerKt") }
dependencies {
    implementation(project(":core-engine"))
    implementation("io.ktor:ktor-server-core-jvm:3.0.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-jackson:3.0.1")
    testImplementation(kotlin("test"))
}