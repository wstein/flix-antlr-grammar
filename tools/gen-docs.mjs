#!/usr/bin/env node
/**
 * Generate docs/SYNTAX.md and docs/RAILROAD.md from the canonical grammars.
 *
 * Both are derived rather than hand-written so they cannot drift from what the
 * parser actually accepts. Regenerate after any change to grammars/*.g4:
 *
 *   node tools/gen-docs.mjs
 */
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const grammars = join(root, "grammars");

/** A rule runs from its name to the first semicolon ending a line. */
const RULE = /^([A-Za-z_][A-Za-z0-9_]*)\s*\n?\s*:(.*?);[ \t]*$/gms;

/** Actions, predicates and labels are implementation, not syntax. */
function stripActions(body) {
    return body
        .replace(/\{[^{}]*\}\?/g, "")
        .replace(/\{[^{}]*\}/g, "")
        .replace(/\s*#\s*[A-Za-z_][A-Za-z0-9_]*/g, "")
        .replace(/->\s*(channel|type|skip|pushMode|popMode)\([^)]*\)/g, "")
        .replace(/->\s*popMode/g, "")
        .replace(/\/\/[^\n]*/g, "")
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean)
        .join("\n");
}

function rules(file) {
    const text = readFileSync(join(grammars, file), "utf8");
    const out = [];
    for (const m of text.matchAll(RULE)) {
        const body = stripActions(m[2]);
        if (body) out.push([m[1], body]);
    }
    return out;
}

const alternatives = (body) => body.split("\n|").map((a) => a.replace(/^:\s*/, "").trim());

function renderAntlr(name, body) {
    const alts = alternatives(body);
    const rest = alts.slice(1).map((a) => `\n    | ${a}`).join("");
    return "```antlr\n" + `${name}\n    : ${alts[0]}${rest}\n    ;` + "\n```";
}

/** ANTLR rule bodies are already close to W3C EBNF; only the separator differs. */
function renderEbnf(name, body) {
    const alts = alternatives(body).map((a) => a.replace(/\s+/g, " "));
    return `${name} ::= ${alts.join("\n    | ")} ;`;
}

const parserRules = rules("FlixParser.g4");
const lexerRules = rules("FlixLexer.g4");
const isKeyword = (b) => /^'[a-zA-Z][a-zA-Z0-9_#*]*'$/.test(b.trim());

// ---------------------------------------------------------------- SYNTAX.md
const syntax = [
    "# Flix syntax reference",
    "",
    "**Generated from `grammars/FlixLexer.g4` and `grammars/FlixParser.g4`.**",
    "Do not edit by hand; run `node tools/gen-docs.mjs`.",
    "",
    "This is the grammar as implemented, which is deliberately a *superset* of legal Flix.",
    "Constraints such as duplicate modifiers, non-linear patterns and unknown annotation names",
    "parse here and are rejected by a later validation pass, mirroring how the reference",
    "compiler separates `Parser2` from `Weeder2`.",
    "",
    `Parser rules: ${parserRules.length} · lexer rules: ${lexerRules.length}`,
    "",
    "## Parser rules",
    "",
];
for (const [n, b] of parserRules) syntax.push(`### \`${n}\``, "", renderAntlr(n, b), "");
syntax.push("## Lexer rules", "", "Keywords are omitted; see `fixtures/keywords.txt` for the 84.", "");
for (const [n, b] of lexerRules) {
    if (isKeyword(b)) continue;
    syntax.push(`### \`${n}\``, "", renderAntlr(n, b), "");
}
writeFileSync(join(root, "docs", "SYNTAX.md"), syntax.join("\n").trimEnd() + "\n");

// -------------------------------------------------------------- RAILROAD.md
// A curated subset: rendering all 80-odd rules produces a document nobody reads,
// and the interesting shapes are the ones with real alternation.
const FEATURED = [
    "compilationUnit", "declaration", "defDeclaration", "enumDeclaration", "traitDeclaration",
    "effDeclaration", "type", "primaryType", "expr", "pattern", "primaryPattern",
    "datalogConstraint", "predicateBody", "fixpointExpr", "matchRule", "forFragment",
];
const byName = new Map(parserRules);
const rail = [
    "# Railroad diagrams",
    "",
    "**Generated from `grammars/FlixParser.g4`.** Do not edit by hand; run `node tools/gen-docs.mjs`.",
    "",
    "Rendered by [Mermaid](https://mermaid.js.org) 11.16 or newer, which added railroad diagrams.",
    "GitHub renders these inline; older Mermaid versions will show the block as an error.",
    "",
    "A curated subset of the rules whose shape is worth seeing. The complete grammar is in",
    "[SYNTAX.md](SYNTAX.md).",
    "",
];
for (const name of FEATURED) {
    const body = byName.get(name);
    if (!body) continue;
    rail.push(`## \`${name}\``, "", "```mermaid", "railroad-ebnf-beta", renderEbnf(name, body), "```", "");
}
writeFileSync(join(root, "docs", "RAILROAD.md"), rail.join("\n").trimEnd() + "\n");

console.log(
    `SYNTAX.md: ${parserRules.length} parser rules, ` +
    `${lexerRules.filter(([, b]) => !isKeyword(b)).length} non-keyword lexer rules\n` +
    `RAILROAD.md: ${FEATURED.filter((n) => byName.has(n)).length} diagrams`,
);
