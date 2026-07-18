import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(22)
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_22) } }
    sourceSets.jvmMain.dependencies {
        implementation(project(":scene-compose"))
        implementation(project(":renderer-filament"))
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
    }
}

compose.desktop {
    application {
        mainClass = "dev.composescene3d.sample.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
