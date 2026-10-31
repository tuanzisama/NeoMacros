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
            }
        }
    }

    plugins {
        // 1.19+ detects unobfuscated Minecraft (26.1+) on its own; do not downgrade to 1.17,
        // which requires the fabric.loom.disableObfuscation flag and registers a different
        // configuration set. See docs/02-build-system.md for the rationale.
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
            // Milestone nodes (docs/01-overview.md §8). The full matrix:
            // 26.x unobf on Java 25, 1.x obf on Java 17/21 (Mojmap+Parchment).
            // fabric_minecraft_version_range pins each node 1:1.
            // The 26.x line is active with cross-version adapter branches
            // (T-106). The 1.x nodes have verified fabric API versions in
            // versions/<mc>/gradle.properties but stay inactive until their
            // obf-mode loom setup and Java 17/21 toolchains are wired.
            versions("26.1.2", "26.2", "26.3")
        }
    }
}
