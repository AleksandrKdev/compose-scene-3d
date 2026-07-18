import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

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
val projectUrl = "https://github.com/AleksandrKdev/compose-scene-3d"

subprojects {
    if (name in publishedModules) {
        pluginManager.apply("maven-publish")
        pluginManager.apply("signing")

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

        extensions.configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}
