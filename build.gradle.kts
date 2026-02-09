plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "ch.riesennet.reforge"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("253.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    test {
        useJUnitPlatform()
    }

    runIde {
        jvmArgs = listOf("-Xmx2g", "-Djava.awt.headless=true")
    }
}
