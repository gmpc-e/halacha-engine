plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("io.ktor.plugin") version "3.0.1" apply false
}

allprojects {
    group = "com.elad.halachatime"
    version = "0.1.0"
}