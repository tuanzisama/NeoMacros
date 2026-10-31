plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// Version-independent module pinned to the Java 17 baseline: compiled once and
// embedded into every product node. // The E2E-A harness (runtime + stub adapter + test bridge) lives here.

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    api(project(":core"))
    api(project(":engine"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// CI-facing alias: the application-level E2E suite is the regular test task.
tasks.register("verifyE2EA") {
    group = "verification"
    description = "Runs the application-level E2E scenario suite"
    dependsOn(tasks.test)
}
