pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }

        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
                includeGroupAndSubgroups("fabric-loom")
                includeGroup("fabric-loom")
            }
        }
    }

    plugins {
        // Both Loom plugin ids registered apply false; the beforeProject hook
        // below picks one per node. The legacy id (net.fabricmc.fabric-loom)
        // hard-forces disableObfuscation=true — the unobfuscated toolchain the
        // 26.x nodes need. The modern id (fabric-loom) honors the
        // fabric.loom.disableObfuscation=false root flag — the mapped
        // (obfuscated) toolchain for 1.x nodes whose jars still carry
        // obfuscated names and intermediary-named fabric-api modules.
        id("fabric-loom") version "1.19-SNAPSHOT" apply false
        id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT" apply false
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "neomacros"

// Version-independent modules: compiled once against the Java 17 baseline and
// embedded into every product node.
include("doc-api", "ports", "core", "engine", "e2e")

// Version-dependent product branch; Stonecutter creates one virtual node per
// entry (:product:<mc>), all driven by product/build.gradle.kts.
include("product")

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        branch("product") {
            // Milestone nodes (docs/01-overview.md §8). The 1.x obf node uses
            // the modern fabric-loom plugin id (honors the root
            // fabric.loom.disableObfuscation=false flag for the mapped
            // toolchain); the 26.x unobf nodes use the legacy plugin id via
            // unobf.gradle.kts (which loom hard-forces into unobf mode).
            versions("1.21.6")
            versions("26.1.2", "26.2", "26.3").buildscript("unobf.gradle.kts")
        }
    }
}

