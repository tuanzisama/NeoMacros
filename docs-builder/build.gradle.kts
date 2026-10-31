plugins {
    `java-library`
}

repositories {
    // Resolves the doc-api annotation jar by coordinates (published via
    // ./gradlew :doc-api:publishToMavenLocal in the parent build): the
    // documentation generator consumes the same annotations the runtime
    // binding builder consumes, from a single source of truth.
    mavenLocal()
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    implementation("ink.tuanzi.neomacros:doc-api:0.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Path to a TypeScript compiler entry (the bin/tsc JS file); the TS
    // compile test runs it through node, e.g.
    // ./gradlew -p docs-builder check -PtscPath=C:/path/to/typescript/bin/tsc
    systemProperty("tscPath", providers.gradleProperty("tscPath").orElse("").get())
    // Fixture sources and snapshot baselines are read at test runtime, outside
    // any source set; declare them as inputs or edits skip the test task.
    inputs.dir(layout.projectDirectory.dir("src/test/fixtures"))
    inputs.dir(layout.projectDirectory.dir("src/test/snapshots"))
}
