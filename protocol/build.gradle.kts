plugins {
    `java-library`
}

group = "com.ceclientbridge"
version = "2.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    named<Test>("test") {
        enabled = false
    }
    register<JavaExec>("protocolTest") {
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("com.ceclientbridge.protocol.BridgeProtocolTest")
    }
    register<JavaExec>("handshakeTest") {
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("com.ceclientbridge.protocol.BridgeHandshakeTest")
    }
    jar {
        archiveBaseName.set("cebridge-protocol")
    }
}
