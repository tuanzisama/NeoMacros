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
            // Milestone nodes (docs/02-build-system.md). Adding a 1.x node needs
            // no script change: product/build.gradle.kts branches on
            // access_widener_namespace from versions/<mc>/gradle.properties.
            versions("26.3")
        }
    }
}
