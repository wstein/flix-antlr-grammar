plugins {
    kotlin("jvm") version "2.2.20"
    antlr
    jacoco
    application
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

val antlrVersion = "4.13.2"
val grammarPackage = "io.github.wstein.flix.antlr"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:$antlrVersion")
    implementation("org.antlr:antlr4-runtime:$antlrVersion")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The `antlr` plugin wires its own configuration into `api`, which drags the
// unrelated ANTLR 2.7.7 runtime onto every consumer's compile classpath.
configurations.api {
    setExtendsFrom(emptyList())
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "$grammarPackage.cli.MainKt"
}

sourceSets.main {
    antlr.srcDir(file("../grammars"))
}

tasks.generateGrammarSource {
    maxHeapSize = "512m"
    // ANTLR derives no package from the source layout, so state it explicitly and
    // emit into the matching directory.
    val outDir = layout.buildDirectory.dir("generated-src/antlr/main/${grammarPackage.replace('.', '/')}")
    outputDirectory = outDir.get().asFile
    arguments = arguments +
        listOf(
            "-visitor",
            "-no-listener",
            "-long-messages",
            "-Werror",
            "-package",
            grammarPackage,
            "-lib",
            outDir.get().asFile.path,
        )
}

// Kotlin sources reference the generated parser, so generation must precede
// both Kotlin compilation and ktlint's source scan.
tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}
tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask>().configureEach {
    dependsOn(tasks.generateGrammarSource, tasks.generateTestGrammarSource)
}

ktlint {
    version = "1.5.0"
    filter {
        // Generated ANTLR output is not ours to style.
        exclude { it.file.path.contains("generated-src") }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude("${grammarPackage.replace('.', '/')}/Flix*.class") }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            // Baseline ratchet: raise as coverage grows, never lower.
            limit {
                counter = "INSTRUCTION"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
