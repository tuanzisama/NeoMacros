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
        // 1.19-SNAPSHOT：官方 26.3 示例同版本，自动处理无混淆（无需 disableObfuscation 手动开关）
        id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT" apply false
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "neomacros"

// ── 版本无关模块（一次编译，Java 17 基线；架构文档 02 §1）──────────────────
include("doc-api", "ports", "core", "engine", "e2e")

// ── 版本相关产品分支（Stonecutter 虚拟版本节点：:product:<mc>）─────────────
include("product")

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        branch("product") {
            // 里程碑节点（架构文档 01 §8 / 02 §2）。T-000 仅挂 26.3；
            // 其余 8 节点在 T-106 补全（同时建 versions/<mc>/gradle.properties）。
            versions("26.3")
            // 1.x 节点加入时无需改此处脚本：product/build.gradle.kts 按
            // versions/<mc>/gradle.properties 的 access_widener_namespace 决定是否加映射层。
        }
    }
}
