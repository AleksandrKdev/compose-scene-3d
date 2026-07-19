pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compose-scene-3d"

include(":scene-core")
include(":scene-compose")
include(":renderer-filament")
include(":renderer-web")
include(":renderer-testkit")
include(":samples:android-app")
include(":samples:desktop-app")
include(":samples:ios-shared")
include(":samples:web-app")
