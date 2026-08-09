plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.ceclientbridge"
version = "1.1.0"

data class ServerProfile(
    val paperDevBundle: String,
    val javaVersion: Int,
    val targetFamily: String
)

val target = providers.gradleProperty("target")
    .orElse(providers.gradleProperty("bridgeTarget"))
    .orElse("26.x")
    .get()
val craftEngineJar = providers.gradleProperty("craftEngineJar")
    .orElse("libs/craft-engine-paper-plugin-26.7.4.jar")
    .get()
val profiles = mapOf(
    // The Paper bridge is released as one 26.2 server artifact. Legacy coverage is
    // provided by the separate Fabric client artifact, not by another server build.
    "26.x" to ServerProfile("26.2.build.65-beta", 25, "26.x")
)
val profile = profiles[target] ?: throw GradleException(
    "Unsupported or unavailable server target '$target'. Available profiles: ${profiles.keys.joinToString()}")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(profile.javaVersion))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // NMS access (ServerPlayer/RecipeManager/etc.) for the recipe-resync workaround - paper-api alone
    // isn't enough for that, unlike the rest of this plugin which only needs the public Bukkit API.
    paperweight.paperDevBundle(profile.paperDevBundle)
    // CraftEngine：物品/方块/配方公开 API 来源，版本须与服务器一致（26.7-SNAPSHOT），用本地 jar
    compileOnly(files(craftEngineJar))
}

sourceSets {
    main {
        java.srcDir("../protocol/src/main/java")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(profile.javaVersion)
    }
    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(profile.javaVersion)
    }
    register<JavaExec>("bridgeChannelsTest") {
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("com.ceclientbridge.net.BridgeChannelsTest")
    }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("CraftEngineClientBridge-${project.version}-${target}.jar")
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        filteringCharset = "UTF-8"
        expand("version" to project.version, "bridge_target" to profile.targetFamily)
    }
}
