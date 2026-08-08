buildscript {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
    dependencies {
        classpath("net.fabricmc:fabric-loom:1.15.5")
    }
}

plugins {
    java
}

apply(plugin = "fabric-loom")

val minecraftVersion = "1.21.6"
val loaderVersion = "0.17.2"
val fabricApiVersion = "0.127.1+1.21.6"
val yarnVersion = "1.21.6+build.1"
val minecraftDependency = ">=1.21.6 <1.22"
val targetFamily = "1.21.x"

val modVersion: String by project
val mavenGroup: String by project
val modId: String by project
group = mavenGroup
version = modVersion

repositories {
    maven("https://api.modrinth.com/maven")
}

// Loom's remapper only needs the Java libraries. Native LWJGL/JTracy artifacts are runtime inputs
// for launching the game and are not required to rewrite this mod; excluding them keeps remapJar
// reproducible when Mojang's native repository is unavailable.
configurations.named("minecraftRuntimeLibraries") {
    exclude(group = "org.lwjgl")
    exclude(group = "com.mojang", module = "jtracy")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    add("mappings", "net.fabricmc:yarn:$yarnVersion:v2")
    add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    add("modCompileOnly", "maven.modrinth:jei:vPkfuKVX")
    add("modCompileOnly", "maven.modrinth:jade:AMBKaYce")
}

sourceSets {
    main {
        java.srcDir("../protocol/src/main/java")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf(
            "mod_id" to modId,
            "mod_version" to modVersion,
            "minecraft_version" to minecraftVersion,
            "minecraft_dependency" to minecraftDependency,
            "bridge_target" to targetFamily,
            "java_version" to 21,
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
        archiveBaseName.set("$modId-1.21.x")
        archiveClassifier.set("")
    }
    named<org.gradle.jvm.tasks.Jar>("remapJar") {
        archiveFileName.set("$modId-1.21.x-$modVersion.jar")
    }
}
