plugins {
    kotlin("jvm") version "2.4.10"
    antlr
    jacoco
    application
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
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
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
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

// Emits this grammar's parse of every flix-spec fixture as a canonical projected tree, for
// flix-spec's own comparison to read. Deliberately a writer and not a comparator: the comparison
// lives in flix-spec, and a second port of it is exactly the duplication that repository exists to
// end -- see the note on Projection.kt.
tasks.register<JavaExec>("projectFixtures") {
    description = "Projects flix-spec fixtures into build/flix-spec-projection/ for conformance."
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "$grammarPackage.cli.Projection"
    workingDir = rootProject.projectDir
    argumentProviders.add(
        CommandLineArgumentProvider {
            val spec = providers.environmentVariable("FLIX_SPEC").orNull
            if (spec == null) emptyList() else listOf("--flix-spec", spec)
        },
    )
}

sourceSets.main {
    antlr.setSrcDirs(listOf(file("../grammars")))
}

tasks.generateGrammarSource {
    maxHeapSize = "512m"
    val outDir = layout.buildDirectory.dir("generated-src/antlr/main/${grammarPackage.replace('.', '/')}")
    outputDirectory = outDir.get().asFile
    outDir.get().asFile.mkdirs()
    arguments =
        listOf(
            "-visitor",
            "-no-listener",
            "-long-messages",
            "-Werror",
            "-package",
            grammarPackage,
            "-lib",
            outDir.get().asFile.absolutePath,
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
    // Forward opt-in flags: -Dsnapshots.update=true regenerates CST snapshots,
    // -Dflix.corpus=<dir> points the parse-rate gate at a Flix checkout.
    for (key in listOf("snapshots.update", "flix.corpus")) {
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
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
