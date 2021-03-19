rootProject.name = "wire-grpc"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.squareup.wire") {
                // For some reason, Gradle does a lookup on the wrong coordinates:
                // 'com.squareup.wire:com.squareup.wire.gradle.plugin' instead of the one below.
                useModule("com.squareup.wire:wire-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}