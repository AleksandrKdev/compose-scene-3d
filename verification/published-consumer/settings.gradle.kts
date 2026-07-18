pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "ComposeScene3D"
            url = uri(
                providers.environmentVariable("COMPOSE_SCENE_3D_REPOSITORY_URL")
                    .orElse("https://maven.pkg.github.com/AleksandrKdev/compose-scene-3d")
                    .get()
            )
            val actor = providers.environmentVariable("GITHUB_ACTOR").orNull
            val token = providers.environmentVariable("GITHUB_TOKEN").orNull
            if (!actor.isNullOrBlank() && !token.isNullOrBlank()) {
                credentials {
                    username = actor
                    password = token
                }
            }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "compose-scene-3d-published-consumer"
