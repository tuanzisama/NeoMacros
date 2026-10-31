import org.gradle.language.jvm.tasks.ProcessResources
import java.util.Properties

// Stonecutter centralScript：对每个 :product:<mc> 虚拟节点生效。
// 版本相关坐标只读 versions/<mc>/gradle.properties（架构文档 02 §6）。
plugins {
    id("net.fabricmc.fabric-loom")
}

val mc = project.name // 分支 product 的版本子项目名即 MC 版本，如 "26.3"

val nodeProps = Properties().apply {
    val f = rootProject.file("versions/$mc/gradle.properties")
    require(f.exists()) { "缺少版本节点属性文件 versions/$mc/gradle.properties" }
    f.inputStream().use { load(it) }
}
fun prop(key: String): String =
    nodeProps.getProperty(key) ?: error("versions/$mc/gradle.properties 缺少键: $key")

val modId = providers.gradleProperty("mod_id").get()
val modVersion = providers.gradleProperty("version").get()

// ── 模式分叉（架构文档 02 §3）───────────────────────────────────────────────
// loom 1.19+ 自动识别无混淆版本（26.1+）：不需要 mappings，产物走 jar。
// 1.x 混淆节点（T-106 接入）走 else 分支：Mojmap+Parchment + modImplementation。
// 注意：modImplementation/mappings configuration 只在混淆模式注册，故统一用 add() 字符串形式。
val unobf = prop("access_widener_namespace") == "official"

base {
    // Gradle 自动追加 "-$version"，产物形如 neomacros-26.3-fabric-0.1.0.jar
    archivesName = "$modId-$mc-fabric"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")

    if (unobf) {
        // 26.x 无混淆（官方 fabric-example-mod@26.3 同款姿势）
        implementation("net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    } else {
        // 1.x 混淆模式（预留：T-106 接入 1.x 节点时启用，parchment 属性届时加入 versions/<mc>）
        add("mappings", loom.layered {
            officialMojangMappings()
        })
        add("modImplementation", "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    }

    // 版本无关模块：编译依赖 + jar-in-jar 嵌入（架构文档 02 §4“嵌入而非依赖”）
    for (m in listOf(":doc-api", ":ports", ":core", ":engine")) {
        implementation(project(m))
        include(project(m))
    }
}

// 源码吸收：adapter（版本相关源码，唯一允许 import MC/Fabric 的共享源码）与 fabric 入口
sourceSets.main {
    java.srcDir(rootProject.layout.projectDirectory.dir("adapter/src/main/java"))
    java.srcDir(rootProject.layout.projectDirectory.dir("fabric/src/main/java"))
    resources.srcDir(rootProject.layout.projectDirectory.dir("adapter/src/main/resources"))
    resources.srcDir(rootProject.layout.projectDirectory.dir("fabric/src/main/resources"))
}

tasks.withType<ProcessResources>().configureEach {
    val expandProps = mapOf(
        "version" to modVersion,
        "mod_id" to modId,
        "mc_range" to prop("fabric_minecraft_version_range"),
        "loader_min" to prop("fabric_loader_version"),
    )
    inputs.properties(expandProps)
    filesMatching("fabric.mod.json") {
        expand(expandProps)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
