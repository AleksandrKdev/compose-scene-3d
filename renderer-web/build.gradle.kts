import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("com.vanniktech.maven.publish")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":scene-core"))
            api(project(":scene-compose"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
