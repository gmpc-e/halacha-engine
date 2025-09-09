pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        // Keep google() for Android modules; server modules just use mavenCentral()
        google()
        mavenCentral()
    }
}
rootProject.name = "halacha-engine"
include(":core-engine", ":profiles", ":rest-api", ":android-demo")
