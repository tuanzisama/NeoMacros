import org.gradle.language.jvm.tasks.ProcessResources
import java.util.Properties

// Stonecutter central script: evaluated once per :product:<mc> node. All
// version-specific coordinates are read from versions/<mc>/gradle.properties.
plugins {
    id("net.fabricmc.fabric-loom")
}

val mc = project.name // branch "product": the node's project name is the Minecraft version

val nodeProps = Properties().apply {
    val f = rootProject.file("versions/$mc/gradle.properties")
    require(f.exists()) { "Missing node properties file versions/$mc/gradle.properties" }
    f.inputStream().use { load(it) }
}
fun prop(key: String): String =
    nodeProps.getProperty(key) ?: error("versions/$mc/gradle.properties is missing key: $key")

val modId = providers.gradleProperty("mod_id").get()

// Mode split. Loom auto-detects unobfuscated Minecraft (26.1+): no mappings and
// a plain `jar`. Obfuscated 1.x nodes need layered mappings plus
// modImplementation; those configurations only exist in obfuscated mode, hence
// the string-based add() in both branches.
val unobf = prop("access_widener_namespace") == "official"


base {
    // Gradle appends "-$version", producing e.g. neomacros-26.3-fabric-0.1.0.jar
    archivesName = "$modId-$mc-fabric"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")

    println("NODE-DIAG mc=" + mc + " unobf=" + unobf)
    if (unobf) {
        implementation("net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    } else {
        add("mappings", loom.layered {
            officialMojangMappings()
            // Parchment layers come from parchment_* keys in versions/<mc>/gradle.properties
        })
        add("modImplementation", "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    }

    // Version-independent modules: compiled against here and embedded as
    // jar-in-jar so every node ships the same prebuilt core.
    for (m in listOf(":doc-api", ":ports", ":core", ":engine")) {
        implementation(project(m))
        include(project(m))
    }
}

// Source absorption: adapter (the only version-aware shared sources) and the
// fabric entrypoint compile together with this node's own sources.
sourceSets.main {
    java.srcDir(rootProject.layout.projectDirectory.dir("adapter/src/main/java"))
    java.srcDir(rootProject.layout.projectDirectory.dir("fabric/src/main/java"))
    resources.srcDir(rootProject.layout.projectDirectory.dir("adapter/src/main/resources"))
    resources.srcDir(rootProject.layout.projectDirectory.dir("fabric/src/main/resources"))
}

tasks.withType<ProcessResources>().configureEach {
    val expandProps = mapOf(
        "version" to providers.gradleProperty("version").get(),
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

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Reads the E2E-G results file (written by script-tests/suite.js through the
// Reads the E2E-G results file (written by script-tests/suite.js through the
// storage module) and fails when any case failed or the suite never
// completed. Requires a finished runClient session with
// -Dneomacros.e2e.quitOnDone=true.
tasks.register("verifyGameE2E") {
    group = "verification"
    description = "Asserts the in-game E2E suite results"
    doLast {
        val candidates = listOf(
            rootProject.file("run/config/neomacros/storage/suite.json"),
            rootProject.file("product/versions/$mc/run/config/neomacros/storage/suite.json"),
        )
        val resultsFile = candidates.firstOrNull { it.exists() }
            ?: throw GradleException(
                "E2E-G results file missing; run the in-game suite first (tried $candidates)")
        val text = resultsFile.readText(Charsets.UTF_8)
        if (text.contains("\"pass\": false") || text.contains("\"pass\":false")) {
            throw GradleException("E2E-G suite has failing cases; see $resultsFile")
        }
        if (!text.contains("suite-done")) {
            throw GradleException("E2E-G suite did not complete; see $resultsFile")
        }
        println("GAME E2E PASS: $resultsFile")
    }
}
