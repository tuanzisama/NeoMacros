plugins {
    `java-library`
    jacoco
}

repositories {
    mavenCentral()
}

// Version-independent module pinned to the Java 17 baseline: compiled once and
// embedded into every product node.
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
    api(project(":ports"))
    api(project(":doc-api"))
    api(libs.gson)
    // YAML manifest parsing (neomacro.yml); embedded into products via the
    // node include wiring so the version stays pinned here.
    implementation(libs.snakeyaml)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Shared dependency-guard rules from the ports test fixtures.
    testImplementation(testFixtures(project(":ports")))
    testImplementation(libs.archunit.junit5)
}

// Coverage gate for the event framework (docs/09 T-020): the framework
// package must stay at or above 85% line coverage.
tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "BUNDLE"
            includes = listOf("ink.tuanzi.neomacros.core.event.*")
            // Payload records are data holders (equals/hashCode boilerplate);
            // their behavioral coverage comes from the docs pipeline fixtures
            // and E2E scenarios, so they stay out of the framework gate.
            excludes = listOf("ink.tuanzi.neomacros.core.event.def.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
