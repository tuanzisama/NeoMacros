plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// 版本无关模块：固定 Java 17 基线（架构文档 02 §1）
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
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
