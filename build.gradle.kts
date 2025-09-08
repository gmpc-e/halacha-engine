// build.gradle.kts (root)
plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false  // ← required for Compose with Kotlin 2.x
    id("com.android.application") version "8.7.2" apply false                // AGP 8.7.x works with Gradle 9

}
allprojects {
    group = "com.elad.halachatime"
    version = "0.1.0"
}
