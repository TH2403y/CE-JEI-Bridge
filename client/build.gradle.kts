plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.13"
}

val minecraftVersion: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
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
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// MC 26.1+ ships unobfuscated official-name jars; Loom no longer remaps Minecraft or mods.
// Regular Gradle `implementation`/`compileOnly` are used instead of `modImplementation`/`modCompileOnly`,
// and the output is the plain `jar` task instead of `remapJar`.
dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // JEI / Jade: compile-time only. Both are soft dependencies - our JEI/Jade plugin classes are only
    // ever classloaded by JEI/Jade themselves via their own lazy plugin-discovery entrypoints, so the
    // game must still launch fine with neither, one, or both installed.
    compileOnly("maven.modrinth:jei:Ip50avzR")
    compileOnly("maven.modrinth:jade:Bw2a8uFN")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf(
            "mod_id" to modId,
            "mod_version" to modVersion,
            "minecraft_version" to minecraftVersion
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }
    jar {
        archiveBaseName.set(modId)
    }
}
