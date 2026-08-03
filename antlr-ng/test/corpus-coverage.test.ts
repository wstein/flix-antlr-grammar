import { readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseFile, walkFlixFiles } from "../src/cli.js";

/**
 * Parse-rate gate over a real Flix checkout, mirroring
 * antlr4/src/test/kotlin/.../CorpusCoverageTest.kt for the antlr-ng (TypeScript) target.
 *
 * Both targets generate from the same canonical grammars/ (docs/DEFECTS.md D11), so the same
 * committed baseline applies to both -- a divergence between the two rates would mean the
 * TypeScript-flavoured preprocessing in tools/gen-antlr-ng.mjs changed grammar behaviour, not
 * just syntax, which is itself worth catching.
 *
 * The corpus location comes from FLIX_CORPUS, falling back to a sibling flix/flix checkout.
 * When no corpus is present the test is skipped rather than passed, so a missing corpus can
 * never be mistaken for coverage.
 */
function corpusDir(): string | null {
    const configured = process.env.FLIX_CORPUS;
    const candidates = [configured, join(homedir(), "github.com", "flix", "flix", "main")].filter(
        (c): c is string => Boolean(c),
    );
    for (const c of candidates) {
        try {
            if (statSync(c).isDirectory()) return c;
        } catch {
            // does not exist -- try the next candidate
        }
    }
    return null;
}

function baselineRate(): number {
    const text = readFileSync(join(import.meta.dirname, "..", "..", "fixtures", "corpus-baseline.json"), "utf8");
    const match = /"rate"\s*:\s*([0-9.]+)/.exec(text);
    if (!match) throw new Error('corpus-baseline.json is missing a numeric "rate" field');
    return Number(match[1]);
}

describe("corpus coverage", () => {
    const corpus = corpusDir();

    // Parsing ~700 real-world files takes well over vitest's 5s default -- ~155s on a fast
    // local machine, comfortably over 300s on a CI runner (antlr4ng's pure-JS runtime is
    // slower per file than the JVM ANTLR runtime the antlr4 target uses).
    it.skipIf(!corpus)("corpus parse rate meets baseline", { timeout: 900_000 }, () => {
        const files = walkFlixFiles(corpus!);
        expect(files.length).toBeGreaterThan(0);

        const parsed = files.filter((f) => {
            try {
                return parseFile(f).success;
            } catch {
                return false;
            }
        }).length;
        const rate = parsed / files.length;
        const baseline = baselineRate();

        console.log(
            `corpus: ${parsed} / ${files.length} parsed (${(rate * 100).toFixed(2)}%), ` +
                `baseline ${(baseline * 100).toFixed(2)}%`,
        );

        // Tolerance absorbs corpus churn only, not grammar regressions.
        expect(
            rate,
            `Corpus parse rate regressed: ${rate.toFixed(4)} < baseline ${baseline.toFixed(4)}. ` +
                "Fix the grammar rather than lowering fixtures/corpus-baseline.json.",
        ).toBeGreaterThanOrEqual(baseline - 0.005);

        if (rate > baseline + 0.005) {
            console.log(
                `Parse rate improved to ${rate.toFixed(4)}; raise "rate" in fixtures/corpus-baseline.json to lock it in.`,
            );
        }
    });
});
