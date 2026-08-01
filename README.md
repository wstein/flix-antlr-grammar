# flix-antlr-grammar

[![CI](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml/badge.svg)](https://github.com/wstein/flix-antlr-grammar/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE.md)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.13.2-c92a2a.svg)](https://www.antlr.org)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-9.6-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org)
[![Flix](https://img.shields.io/badge/Flix-0.75.1-blueviolet.svg)](https://flix.dev)

An [ANTLR4](https://www.antlr.org) grammar for the [Flix](https://flix.dev) programming
language, derived directly from the reference compiler's `Lexer.scala` and `Parser2.scala`
rather than from prose documentation.

## Status

Work in progress. Phases land incrementally; see [docs/DESIGN-DEBATE.md](docs/DESIGN-DEBATE.md)
for the architectural decisions and the reasoning behind them.

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Build, CI, project scaffolding | done |
| 2 | `FlixLexer.g4` | pending |
| 3 | Declarations, types, uses/imports | pending |
| 4 | Expressions and patterns | pending |
| 5 | Datalog, fixpoint, effect handlers | pending |
| 6 | Validation CLI and corpus coverage gate | pending |
| 7 | Syntax reference and railroad diagrams | pending |

## Design in one paragraph

The grammar is **permissive by design**. Flix's own pipeline parses a deliberate superset of
the language and rejects the illegal remainder in a later phase (`Weeder2`), because a parser
that accepts more recovers better in an editor and produces far better diagnostics than a
grammar that encodes every rule as a syntax error. This grammar follows the parser, not the
weeder: `pub pub sealed def` parses, and reporting it is a validation concern. Rule names
mirror the compiler's `SyntaxTree.TreeKind` inventory so the parse tree lines up with the
compiler's own concrete syntax tree.

## Source of truth

All syntax decisions trace to a specific place in the reference compiler
(fork [`wstein/flix-fork@debf7df`](https://github.com/wstein/flix-fork), Flix 0.75.1):

| File | Authority for |
| --- | --- |
| `phase/Lexer.scala` | Tokenization, keyword and operator spellings |
| `ast/TokenKind.scala` | The exhaustive token inventory and FIRST-set tables |
| `phase/Parser2.scala` | Grammar shape, operator precedence, ambiguity resolution |
| `ast/SyntaxTree.scala` | Node names (`TreeKind`), used as the rule names here |
| `phase/Weeder2.scala` | What stays *out* of the grammar and is validated later |

Two sibling projects informed the design and act as cross-checks:
[tree-sitter-flix](https://github.com/wstein/tree-sitter-flix) (an LR grammar whose declared
conflicts map the hard spots) and [flix-textmate](https://github.com/wstein/flix-textmate)
(whose lexicon is machine-extracted from the same compiler).

## Build

Requires JDK 21 or newer. The Gradle wrapper is checked in.

```bash
./gradlew build          # generate, compile, test, verify coverage
./gradlew ktlintFormat   # apply the formatter
./gradlew ktlintCheck    # verify formatting and lint
./gradlew test           # tests only
```

## Layout

```
src/main/antlr/          FlixLexer.g4, FlixParser.g4 — the grammar
src/main/kotlin/         validation CLI and parse-tree helpers
src/test/kotlin/         JUnit 5 tests
docs/                    design debate, syntax reference
```

## Contributing

Every change must keep `./gradlew build` green, which includes the formatter, the linter, the
test suite, and the coverage floor. Grammar changes should cite the reference-compiler line
they follow.

## License

[Apache-2.0](LICENSE.md)
