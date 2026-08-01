plugins {
    application
}

application {
    mainClass = "io.github.wstein.flix.antlr.cli.MainKt"
}

tasks.named("run") {
    dependsOn(":antlr4:run")
}

tasks.named("check") {
    dependsOn(":antlr4:check")
}

tasks.named("build") {
    dependsOn(":antlr4:build")
}

tasks.named("test") {
    dependsOn(":antlr4:test")
}

tasks.register("ktlintFormat") {
    dependsOn(":antlr4:ktlintFormat")
}

tasks.register("ktlintCheck") {
    dependsOn(":antlr4:ktlintCheck")
}

tasks.register("generateGrammarSource") {
    dependsOn(":antlr4:generateGrammarSource")
}
