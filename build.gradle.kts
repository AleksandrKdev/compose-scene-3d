import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

allprojects {
    group = "io.github.aleksandrkdev"
    version = "0.1.0-alpha03-SNAPSHOT"
}

val publishedModules = setOf("scene-core", "scene-compose", "renderer-filament")
val projectUrl = "https://github.com/AleksandrKdev/compose-scene-3d"

subprojects {
    if (name in publishedModules) {
        pluginManager.apply("maven-publish")

        pluginManager.withPlugin("com.vanniktech.maven.publish") {
            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(automaticRelease = true)
                if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                    signAllPublications()
                }
            }
        }

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "localAlpha"
                    url = rootProject.uri(
                        rootProject.layout.buildDirectory.dir("maven-alpha").get().asFile
                    )
                }
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/AleksandrKdev/compose-scene-3d")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("ComposeScene3D ${project.name}")
                    description.set(
                        "Retained-mode 3D scene APIs for Kotlin and Compose Multiplatform"
                    )
                    url.set(projectUrl)
                    developers {
                        developer {
                            id.set("AleksandrKdev")
                            name.set("AleksandrKdev")
                            url.set("https://github.com/AleksandrKdev")
                        }
                    }
                    scm {
                        url.set(projectUrl)
                        connection.set("scm:git:$projectUrl.git")
                        developerConnection.set("scm:git:ssh://git@github.com/AleksandrKdev/compose-scene-3d.git")
                    }
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
