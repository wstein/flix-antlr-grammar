# flix-antlr-grammar

[![CI](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE.md)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.13.2-c92a2a.svg)](https://www.antlr.org)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.1-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org)
[![Flix](https://img.shields.io/badge/Flix-0.75.1-blueviolet.svg)](https://flix.dev)
[![Corpus](https://img.shields.io/badge/corpus%20parse%20rate-90.9%25-brightgreen.svg)](fixtures/corpus-baseline.json)

An [ANTLR4](https://www.antlr.org) grammar for the [Flix](https://flix.dev) programming
language, derived from the reference compiler's `Lexer.scala` and `Parser2.scala` rather than
from prose documentation.

## Status

**Corpus parse rate: 90.90% (629 / 692 files.)** The rate is enforced by a ratcheting gate;
see [Verification](#verification).

| Area | State |
| --- | --- |
| Build, CI, Dependabot, release | done |
| Lexer: 84 keywords, operator runs, holes, interpolation modes | done |
| Parser: declarations, types, expressions, patterns | done |
| Parser: Datalog, fixpoint, effect handlers | done |
| Validation CLI and corpus gate | done |
| Java interop, anonymous classes, remaining edge cases | in progress |
| Syntax reference and railroad diagrams | not started |

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
docs/              design debates, defect log
```

Only the JVM target generates from `grammars/` today. The antlr-ng target ships the
hand-written runtime support and is type-checked in CI, but cannot yet generate from the
shared grammars: the embedded actions are Java syntax and antlr-ng's TypeScript target ignores
`options { superClass }`. See [docs/DEFECTS.md](docs/DEFECTS.md) D11.

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

The corpus gate needs a Flix checkout, resolved from `-Dflix.corpus=<dir>`, the `FLIX_CORPUS`
environment variable, or a sibling `flix-fork` checkout. Without one it **skips** rather than
passes, so an absent corpus can never be mistaken for coverage. It therefore does not run in
CI today; run it locally before proposing a grammar change:

```bash
./gradlew :antlr4:test -Dflix.corpus=/path/to/flix/main
./gradlew :antlr4:test -Dsnapshots.update=true   # after an intended tree-shape change
```

Raise the baseline when the rate improves; never lower it.

## Source of truth

All syntax decisions trace to the reference compiler
(fork [`wstein/flix-fork@debf7df`](https://github.com/wstein/flix-fork), Flix 0.75.1):

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
