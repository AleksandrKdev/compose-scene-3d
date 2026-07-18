import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

allprojects {
    group = "dev.composescene3d"
    version = "0.1.0-alpha01"
}

val publishedModules = setOf("scene-core", "scene-compose", "renderer-filament")

subprojects {
    if (name in publishedModules) {
        pluginManager.apply("maven-publish")

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "localAlpha"
                    url = rootProject.uri(
                        rootProject.layout.buildDirectory.dir("maven-alpha").get().asFile
                    )
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("ComposeScene3D ${project.name}")
                    description.set(
                        "Retained-mode 3D scene APIs for Kotlin and Compose Multiplatform"
                    )
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                }
            }
        }
    }
}
