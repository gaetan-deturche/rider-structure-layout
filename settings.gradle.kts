pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/maven-central")
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
        id("org.jetbrains.intellij.platform") version "2.16.0"
        // rdgen's version is declared at the application site (protocol/build.gradle.kts): the
        // resolutionStrategy remap below reads requested.version, which is null for versions
        // declared here.
    }
    resolutionStrategy {
        eachPlugin {
            // rdgen's plugin id does not follow the '{id}:{id}.gradle.plugin' Maven convention.
            if (requested.id.id == "com.jetbrains.rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}

rootProject.name = "struct-layout"

include(":protocol")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}
