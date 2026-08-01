package io.github.wstein.flix.antlr.cli

import io.github.wstein.flix.antlr.FlixLexer
import io.github.wstein.flix.antlr.FlixParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import java.io.File
import kotlin.system.exitProcess

data class ParseResult(
    val file: File,
    val success: Boolean,
    val errorCount: Int,
    val errors: List<String>,
)

fun parseFile(file: File): ParseResult {
    val errors = mutableListOf<String>()
    val stream = CharStreams.fromPath(file.toPath())
    val lexer = FlixLexer(stream)
    val tokens = CommonTokenStream(lexer)
    val parser = FlixParser(tokens)

    val listener =
        object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?,
            ) {
                errors.add("${file.path}:$line:$charPositionInLine: $msg")
            }
        }

    parser.removeErrorListeners()
    parser.addErrorListener(listener)
    parser.compilationUnit()

    val totalErrors = parser.numberOfSyntaxErrors.coerceAtLeast(errors.size)
    return ParseResult(file, totalErrors == 0, totalErrors, errors)
}

fun runValidation(args: Array<String>): Int {
    if (args.isEmpty()) {
        println("Usage: flix-antlr-validate <file-or-directory-paths>")
        return 1
    }

    val filesToParse = mutableListOf<File>()
    for (arg in args) {
        val file = File(arg)
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile && it.extension == "flix" }.forEach { filesToParse.add(it) }
        } else if (file.exists()) {
            filesToParse.add(file)
        } else {
            println("Warning: file or directory not found: $arg")
        }
    }

    if (filesToParse.isEmpty()) {
        println("No .flix files found to validate.")
        return 1
    }

    println("Validating ${filesToParse.size} Flix source file(s)...")
    var passed = 0
    var failed = 0

    for (file in filesToParse) {
        val result = parseFile(file)
        if (result.success) {
            passed++
        } else {
            failed++
            println("FAIL: ${file.path} (${result.errorCount} error(s))")
            result.errors.forEach { println("  $it") }
        }
    }

    val total = filesToParse.size
    val rate = (passed.toDouble() / total.toDouble()) * 100.0
    println("\nValidation Summary: $passed / $total passed (${String.format("%.2f", rate)}%)")

    return if (failed > 0) 1 else 0
}

fun main(args: Array<String>) {
    val exitCode = runValidation(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
