plugins {
    kotlin("jvm") version "2.4.10"
}

val libraryGroup = providers.environmentVariable("COMPOSE_SCENE_3D_GROUP")
    .orElse("io.github.aleksandrkdev")
val libraryVersion = providers.environmentVariable("COMPOSE_SCENE_3D_VERSION")
    .orElse("0.1.0-alpha02")

dependencies {
    implementation("${libraryGroup.get()}:scene-core:${libraryVersion.get()}")
    implementation("${libraryGroup.get()}:scene-compose:${libraryVersion.get()}")
    implementation("${libraryGroup.get()}:renderer-filament:${libraryVersion.get()}")
}

kotlin {
    jvmToolchain(22)
}
