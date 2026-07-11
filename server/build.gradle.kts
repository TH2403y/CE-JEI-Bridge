plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.ceclientbridge"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // NMS access (ServerPlayer/RecipeManager/etc.) for the recipe-resync workaround - paper-api alone
    // isn't enough for that, unlike the rest of this plugin which only needs the public Bukkit API.
    paperweight.paperDevBundle("26.1.2.build.74-stable")
    // CraftEngine：物品/方块/配方公开 API 来源，版本须与服务器一致（26.7-SNAPSHOT），用本地 jar
    compileOnly(files("libs/craft-engine-paper-plugin-26.7-SNAPSHOT.jar"))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        filteringCharset = "UTF-8"
        expand("version" to project.version)
    }
}
