import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    // Versions come from settings.gradle.kts (pluginManagement).
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

val pluginGroup: String by project
val pluginVersion: String by project
val platformVersion: String by project
val DotnetPluginId: String by project
val pluginNameValue = project.property("pluginName") as String
val BuildConfiguration: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        rider(platformVersion) {
            // The Maven distribution (unlike the installer) ships lib/rd/rider-model.jar,
            // which the :protocol project needs to compile the model DSL against.
            useInstaller = false
        }
        jetbrainsRuntime()

        // Rider APIs (project.solution) and the rd model/framework live in bundled modules that are
        // NOT on the compile classpath by default.
        bundledModule("intellij.rider")
        bundledModule("intellij.rider.rdclient.dotnet")
        bundledModule("intellij.rd.ide.model.generated")
        bundledModule("intellij.rd.platform")
        bundledModule("intellij.libraries.rd.framework")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = project.property("pluginName") as String
        ideaVersion {
            sinceBuild = project.property("pluginSinceBuild") as String
            untilBuild = project.property("pluginUntilBuild") as String
        }
    }
}

kotlin {
    jvmToolchain(25)
}

sourceSets {
    main {
        kotlin.srcDir("src/rider/main/kotlin")
        resources.srcDir("src/rider/main/resources")
    }
}

// Exposes rider-model.jar to :protocol so rdgen can extend the Rider model.
val riderModel: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(riderModel.name, provider {
        intellijPlatform.platformPath.resolve("lib/rd/rider-model.jar").toFile().also {
            check(it.exists()) { "rider-model.jar not found at $it" }
        }
    }) {
        builtBy(tasks.named("initializeIntellijPlatformPlugin"))
    }
}

val dotnetSolution = layout.projectDirectory
    .file("src/dotnet/$DotnetPluginId/$DotnetPluginId.csproj")

val buildBackend by tasks.registering(Exec::class) {
    description = "Builds the ReSharper backend half of the plugin."
    group = "build"
    workingDir = layout.projectDirectory.asFile
    dependsOn(tasks.named("initializeIntellijPlatformPlugin"))

    // The C++ backend assemblies are not in the JetBrains.Rider.SDK NuGet package, but they ARE in the
    // Rider distribution the Gradle plugin downloads. Pointing the csproj there keeps CI working with no
    // Rider installed, and makes local builds independent of which Rider version is on the machine.
    doFirst {
        val host = intellijPlatform.platformPath.resolve("lib/ReSharperHost")
        check(host.toFile().isDirectory) { "ReSharperHost not found in the platform at $host" }
        commandLine("dotnet", "build", dotnetSolution.asFile.absolutePath,
            "-c", BuildConfiguration, "--nologo", "-p:RiderHostPath=$host")
    }
}

// The backend DLL has to land in the plugin's dotnet/ dir: that is where the ReSharper host looks
// for the .NET half of a Rider plugin. prepareSandbox's output is what buildPlugin zips, so this
// single copy covers both runIde and the distribution (adding it to buildPlugin too = duplicate entry).
tasks.withType<PrepareSandboxTask> {
    dependsOn(buildBackend)

    from("src/dotnet/$DotnetPluginId/bin/$BuildConfiguration") {
        include("$DotnetPluginId.dll")
        include("$DotnetPluginId.pdb")
        into("$pluginNameValue/dotnet")
    }
}
