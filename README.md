# flix-antlr-grammar

[![CI](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE.md)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.13.2-c92a2a.svg)](https://www.antlr.org)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.1-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org)
[![Flix](https://img.shields.io/badge/Flix-0.75.1-blueviolet.svg)](https://flix.dev)
[![Corpus](https://img.shields.io/badge/corpus%20parse%20rate-96.22%25-brightgreen.svg)](fixtures/corpus-baseline.json)

An [ANTLR4](https://www.antlr.org) grammar for the [Flix](https://flix.dev) programming
language, derived from the reference compiler's `Lexer.scala` and `Parser2.scala` rather than
from prose documentation.

## Status

**Corpus parse rate: 96.22% (662 / 688 files), the same on both targets.** The rate is enforced
by a ratcheting gate; see [Verification](#verification).

| Area | State |
| --- | --- |
| Build, CI, Dependabot, release | done |
| Lexer: 84 keywords, operator runs, holes, interpolation modes | done |
| Parser: declarations, types, expressions, patterns | done |
| Parser: Datalog, fixpoint, effect handlers | done |
| Validation CLI and corpus gate, JVM and TypeScript targets | done |
| Java interop, anonymous classes, remaining edge cases | in progress |
| Syntax reference and railroad diagrams | not started |

[docs/SYNTAX.md](docs/SYNTAX.md) is a syntax reference generated from the grammars, and
[docs/TREEKIND-MAP.md](docs/TREEKIND-MAP.md) maps every rule to the compiler's `TreeKind`.
[docs/RAILROAD.md](docs/RAILROAD.md) renders the key rules as Mermaid railroad diagrams.
Regenerate both with `node tools/gen-docs.mjs` after changing a grammar.

Design decisions and the reasoning behind them are recorded in
[docs/DESIGN-DEBATE.md](docs/DESIGN-DEBATE.md) and
[docs/REMEDIATION-DEBATE.md](docs/REMEDIATION-DEBATE.md). Open defects are tracked in
[docs/DEFECTS.md](docs/DEFECTS.md).

## Design in one paragraph

The grammar is **permissive by design**. Flix's own pipeline parses a deliberate superset of
the language and rejects the illegal remainder in a later phase (`Weeder2`), because a parser
that accepts more recovers better in an editor and produces far better diagnostics than a
grammar that encodes every rule as a syntax error. This grammar follows the parser, not the
weeder: `pub pub sealed def` parses, and reporting it is a validation concern.

## Layout

```
grammars/          FlixLexer.g4 and FlixParser.g4 — the shared, canonical grammars
antlr4/            JVM target: FlixLexerBase.java, validation CLI, JUnit 5 tests
antlr-ng/          TypeScript target: FlixLexerBase.ts, generated via antlr4ng
fixtures/          positive and negative sources, CST snapshots, keyword and corpus pins
docs/              design debates, defect log, generated syntax and railroad reference
tools/             Node documentation generators
```

Both targets generate from `grammars/`: the embedded actions are Java syntax and antlr-ng's
TypeScript target ignores `options { superClass }`, so `antlr-ng`'s `npm run generate` stages a
TypeScript-adapted copy first rather than pointing antlr-ng at the grammars directly. See
[docs/DEFECTS.md](docs/DEFECTS.md) D11.

## Build

Requires JDK 21 or newer; the Gradle wrapper is checked in and pinned by checksum.

```bash
./gradlew build            # generate, compile, test, verify coverage
./gradlew ktlintFormat     # apply the formatter
./gradlew ktlintCheck      # verify formatting and lint

cd antlr-ng && npm install && npm run build
```

## Verification

Hand-written tests cannot establish that a grammar covers a language. An earlier version of
this project had a green build, 38 passing tests, and a grammar that rejected nine out of ten
real Flix files, because the "corpus" test parsed two inline snippets in a temporary
directory. The gates that now exist:

| Gate | What it catches |
| --- | --- |
| Corpus parse rate, ratcheted by `fixtures/corpus-baseline.json` | Broad coverage regressions |
| `fixtures/keywords.txt` set equality, checked both ways | Keyword table drift, in either direction |
| CST snapshots in `fixtures/snapshots/` | Silent changes in tree shape |
| Negative fixtures | Over-permissiveness |

The corpus gate needs a Flix checkout, resolved from `-Dflix.corpus=<dir>` (`FLIX_CORPUS` for
the antlr-ng target), or a sibling `flix/flix` checkout. Without one it **skips** rather than
passes, so an absent corpus can never be mistaken for coverage. CI checks out a pinned corpus
and runs the gate for both targets; run it locally before proposing a grammar change:

```bash
./gradlew :antlr4:test -Dflix.corpus=/path/to/flix/main
./gradlew :antlr4:test -Dsnapshots.update=true   # after an intended tree-shape change

cd antlr-ng && npm run generate && FLIX_CORPUS=/path/to/flix/main npm test
```

Both targets are generated from the same `grammars/`, so they are expected to report the same
rate; a divergence between them means the antlr-ng target's TypeScript-adaptation step
(`tools/gen-antlr-ng.mjs`) changed grammar behaviour, not just syntax. Raise the baseline when
the rate improves; never lower it.

## Source of truth

All syntax decisions trace to the reference compiler
(fork [`flix/flix@318bb51`](https://github.com/flix/flix), Flix 0.75.1):

| File | Authority for |
| --- | --- |
| `phase/Lexer.scala` | Tokenization, keyword and operator spellings |
| `ast/TokenKind.scala` | Token inventory and FIRST-set tables |
| `phase/Parser2.scala` | Grammar shape, operator precedence, ambiguity resolution |
| `ast/SyntaxTree.scala` | Node names (`TreeKind`) |
| `phase/Weeder2.scala` | What stays *out* of the grammar and is validated later |

Two sibling projects act as cross-checks:
[tree-sitter-flix](https://github.com/wstein/tree-sitter-flix), whose declared LR conflicts map
the hard spots, and [flix-textmate](https://github.com/wstein/flix-textmate), whose lexicon is
machine-extracted from the same compiler.

Note that both precedence docstrings in `Parser2.scala` are inverted: the code compares
`right.precedence > left.precedence`, so a larger number binds tighter. Transcribing from the
comment produces a table that is backwards in both the expression and type grammars, which is
exactly what happened here once. See [CLAUDE.md](CLAUDE.md).

## Contributing

Every change must keep `./gradlew build` green, which includes the formatter, the linter, the
test suite and the coverage floor. Grammar changes should cite the reference-compiler line they
follow and state their measured effect on the corpus parse rate.

## License

[Apache-2.0](LICENSE.md)
