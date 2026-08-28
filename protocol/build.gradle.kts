import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.jetbrains.rdgen") version "2026.2.1"
}

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    // Explicit: gradle.properties disables the default stdlib dependency for the IDE plugin,
    // but this is a plain JVM project and nothing provides the stdlib at compile time.
    implementation(kotlin("stdlib"))
    implementation("com.jetbrains.rd:rd-gen:2026.2.1")
    // rider-model.jar, published by the root project
    implementation(project(mapOf("path" to ":", "configuration" to "riderModel")))
}

kotlin {
    jvmToolchain(25)
}

val DotnetPluginId: String by rootProject

rdgen {
    val csOutput = File(rootDir, "src/dotnet/$DotnetPluginId/Generated")
    val ktOutput = File(rootDir, "src/rider/main/kotlin/dev/structlayout/generated")

    verbose = true
    packages = "model.rider"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "dev.structlayout.generated"
        directory = "$ktOutput"
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "JetBrains.Rider.Model"
        directory = "$csOutput"
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
