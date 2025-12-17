pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jitpack diperlukan untuk CardStackView
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "C_OtoMatch"
include(":app")