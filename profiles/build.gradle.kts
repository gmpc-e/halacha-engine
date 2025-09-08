plugins { kotlin("jvm") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
dependencies {
    implementation(project(":core-engine"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
}