plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.13"
}

data class ClientProfile(
    val targetFamily: String,
    val minecraftVersion: String,
    val loaderVersion: String,
    val fabricApiVersion: String,
    val minecraftDependency: String,
    val javaVersion: Int,
    val jeiVersion: String,
    val jadeVersion: String,
    val fabricLoaderJar: String,
    val jeiJar: String,
    val jadeJar: String
)

val profiles = mapOf(
    // One 26.x artifact built against the requested 26.2 API.
    "26.x" to ClientProfile(
        "26.x", "26.2", "0.19.3", "0.155.0+26.2", ">=26.2 <27", 25,
        "zE4WIFwL", "JB4B8a9g",
        "libs/fabric-loader-0.19.3.jar",
        "libs/jei-26.2-fabric-30.16.0.131.jar",
        "libs/Jade-mc26.2-Fabric-26.2.10.jar"
    )
)

val target = providers.gradleProperty("target")
    .orElse(providers.gradleProperty("bridgeTarget"))
    .orElse("26.x")
    .get()
val profile = profiles[target] ?: throw GradleException(
    "Unsupported client target '$target'. Available profiles: ${profiles.keys.joinToString()}")

val minecraftVersion = providers.gradleProperty("minecraftVersion").orElse(profile.minecraftVersion).get()
val loaderVersion = providers.gradleProperty("loaderVersion").orElse(profile.loaderVersion).get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").orElse(profile.fabricApiVersion).get()
val minecraftDependency = providers.gradleProperty("minecraftDependency").orElse(profile.minecraftDependency).get()
val jeiVersion = providers.gradleProperty("jeiVersion").orElse(profile.jeiVersion).get()
val jadeVersion = providers.gradleProperty("jadeVersion").orElse(profile.jadeVersion).get()

fun resolveDependencyJar(propertyName: String, defaultPath: String): java.io.File {
    val path = providers.gradleProperty(propertyName).orElse(defaultPath).get()
    val jar = file(path)
    if (!jar.isFile) {
        throw GradleException("Missing $propertyName for target '$target': ${jar.absolutePath}")
    }
    return jar
}

val jeiJar = resolveDependencyJar("jeiJar", profile.jeiJar)
val jadeJar = resolveDependencyJar("jadeJar", profile.jadeJar)
val fabricLoaderJar = resolveDependencyJar("fabricLoaderJar", profile.fabricLoaderJar)
val fabricLoaderClassesDir = layout.buildDirectory.dir("fabric-loader-classes").get().asFile
val extractFabricLoaderClasses = tasks.register<Sync>("extractFabricLoaderClasses") {
    from(zipTree(fabricLoaderJar)) {
        include("**/*.class")
        includeEmptyDirs = false
    }
    into(fabricLoaderClassesDir)
}
val modVersion: String by project
val mavenGroup: String by project
val modId: String by project

group = mavenGroup
version = modVersion

repositories {
    // Loom resolves "mod" dependencies (JEI/Jade) through the project's own repositories, not just
    // settings.gradle.kts's dependencyResolutionManagement - declare it here too.
    maven("https://api.modrinth.com/maven")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(profile.javaVersion))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    compileOnly(files(fabricLoaderClassesDir))
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly(files(jeiJar))
    compileOnly(files(jadeJar))
}

sourceSets {
    main {
        java.srcDir("../protocol/src/main/java")
    }
}

tasks {
    compileJava {
        dependsOn(extractFabricLoaderClasses)
        options.encoding = "UTF-8"
        options.release.set(profile.javaVersion)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf(
            "mod_id" to modId,
            "mod_version" to modVersion,
            "minecraft_version" to minecraftVersion,
            "minecraft_dependency" to minecraftDependency,
            "bridge_target" to profile.targetFamily,
            "java_version" to profile.javaVersion,
            "loader_version" to loaderVersion
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
        filesMatching("bridge-target.properties") {
            expand(props)
        }
    }
    jar {
        archiveBaseName.set("$modId-$target")
    }
}
