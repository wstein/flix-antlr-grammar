import { readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join, sep } from "node:path";
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

/**
 * Corpus files that are *meant* not to parse.
 *
 * Kept identical to the JVM target's list in `CorpusCoverageTest.kt`: both targets share one
 * committed baseline, so an exclusion applied to only one of them would make the two rates
 * disagree by construction and the shared floor unmeetable for whichever lacked it.
 */
const INTENTIONALLY_UNPARSEABLE = ["test/flix/resiliency/ford-fulkerson-prefix.flix"];

const isIntentionallyUnparseable = (file: string): boolean =>
    INTENTIONALLY_UNPARSEABLE.some((suffix) => file.split(sep).join("/").endsWith(suffix));

describe("corpus coverage", () => {
    const corpus = corpusDir();

    // Parsing ~700 real-world files takes well over vitest's 5s default -- ~155s on a fast
    // local machine, comfortably over 300s on a CI runner (antlr4ng's pure-JS runtime is
    // slower per file than the JVM ANTLR runtime the antlr4 target uses).
    it.skipIf(!corpus)("corpus parse rate meets baseline", { timeout: 900_000 }, () => {
        const all = walkFlixFiles(corpus!);
        expect(all.length).toBeGreaterThan(0);

        const parses = (f: string): boolean => {
            try {
                return parseFile(f).success;
            } catch {
                return false;
            }
        };

        const excluded = all.filter(isIntentionallyUnparseable);
        const files = all.filter((f) => !isIntentionallyUnparseable(f));

        // An exclusion that stopped being necessary silently shrinks what the gate measures, so
        // a file that now parses is reported rather than quietly counted as a success.
        expect(
            excluded.filter(parses),
            "these files are excluded as intentionally unparseable but now parse; " +
                "remove them from INTENTIONALLY_UNPARSEABLE",
        ).toEqual([]);

        const parsed = files.filter(parses).length;
        const rate = parsed / files.length;
        const baseline = baselineRate();

        console.log(
            `corpus: ${parsed} / ${files.length} parsed (${(rate * 100).toFixed(2)}%), ` +
                `baseline ${(baseline * 100).toFixed(2)}%, ` +
                `${excluded.length} excluded as intentionally unparseable`,
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
