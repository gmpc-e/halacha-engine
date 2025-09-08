plugins { kotlin("jvm") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
dependencies {
    implementation("com.github.KosherJava:zmanim:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.test { useJUnitPlatform() }