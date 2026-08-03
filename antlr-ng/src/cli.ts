import {
    type ATNSimulator,
    BaseErrorListener,
    CharStream,
    CommonTokenStream,
    type RecognitionException,
    type Recognizer,
    type Token,
} from "antlr4ng";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join } from "node:path";
import { FlixLexer } from "./generated/FlixLexer.js";
import { FlixParser } from "./generated/FlixParser.js";

export interface ParseResult {
    file: string;
    success: boolean;
    errorCount: number;
    errors: string[];
}

export function parseFile(file: string): ParseResult {
    const errors: string[] = [];
    const stream = CharStream.fromString(readFileSync(file, "utf8"));
    const lexer = new FlixLexer(stream);
    const tokens = new CommonTokenStream(lexer);
    const parser = new FlixParser(tokens);

    // The lexer needs the listener too. Without it an unrecognized character is reported to
    // the console and dropped, and a file that lost source text still counted as validated --
    // mirrors antlr4/src/main/kotlin/.../cli/Main.kt's parseFile, which found this the hard way.
    const listener = new (class extends BaseErrorListener {
        override syntaxError<S extends Token, T extends ATNSimulator>(
            _recognizer: Recognizer<T>,
            _offendingSymbol: S | null,
            line: number,
            column: number,
            msg: string,
            _e: RecognitionException | null,
        ): void {
            errors.push(`${file}:${line}:${column}: ${msg}`);
        }
    })();

    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    parser.compilationUnit();

    const totalErrors = Math.max(errors.length, parser.numberOfSyntaxErrors);
    return { file, success: totalErrors === 0, errorCount: totalErrors, errors };
}

export function walkFlixFiles(dir: string): string[] {
    const out: string[] = [];
    for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        const stat = statSync(full);
        if (stat.isDirectory()) {
            out.push(...walkFlixFiles(full));
        } else if (stat.isFile() && extname(full) === ".flix") {
            out.push(full);
        }
    }
    return out;
}
