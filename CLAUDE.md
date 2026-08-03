# flix-antlr-grammar — project notes

An ANTLR4 grammar for Flix, transliterated from the reference compiler.

## Implementation status

**Measured corpus parse rate: 99.85% (687 / 688), both targets.** Run the gate before and after
every grammar change and state the delta in the commit message.

- [x] Build, CI, Dependabot, release workflow.
- [x] Lexer: 84 keywords pinned by `fixtures/keywords.txt`, operator runs, holes,
      interpolation modes, whitespace-sensitive `->` and `.`.
- [x] Parser: declarations, types, expressions, patterns, Datalog, effect handlers.
- [x] Validation CLI and a ratcheting corpus gate, on both the `antlr4` (JVM) and `antlr-ng`
      (TypeScript) targets -- see `antlr-ng/test/corpus-coverage.test.ts` and docs/DEFECTS.md
      D11.
- [x] Java interop edge cases (D7).
- [ ] Syntax reference and railroad diagrams.

Do not mark work complete on the strength of unit tests alone — the corpus gate is the
acceptance criterion. See [docs/DEFECTS.md](docs/DEFECTS.md).

## Project layout & Customer separation

- `grammars/`: Shared canonical ANTLR4 grammars (`FlixLexer.g4`, `FlixParser.g4`) consumed by both customers.
- `antlr4/`: JVM target / Java customer (`FlixLexerBase.java`, Gradle build, CLI runner, Kotlin unit tests).
- `antlr-ng/`: `antlr-ng` TypeScript / structural target (`FlixLexerBase.ts`, `package.json`, `tsconfig.json`).
- `fixtures/`: Structured grammar test suite:
  - `fixtures/positive/`: Valid Flix code snippets.
  - `fixtures/negative/`: Syntax error test snippets.
  - `fixtures/snapshots/`: Serialized CST S-expression snapshots (`.snap`) verifying structural parser consistency.

## Source of truth

Reference: `flix/flix@318bb51` (Flix 0.75.1). Paths below are relative to
`main/src/ca/uwaterloo/flix/language/`.

| File | Authority for |
| --- | --- |
| `phase/Lexer.scala` | Tokenization. **Follow the lexer, not the docs** — several docstrings are wrong (noted below). |
| `ast/TokenKind.scala` | Token inventory, `isFirstIn*` FIRST-set tables, `isRecoverIn*` recovery sets. |
| `phase/Parser2.scala` | Grammar shape, precedence, every ambiguity resolution. Hand-written recursive descent, so it transliterates to LL(*) almost directly. |
| `ast/SyntaxTree.scala` | `TreeKind` — use these as ANTLR rule names. |
| `phase/Weeder2.scala` | What to leave permissive. |

Cross-checks: `wstein/tree-sitter-flix` (its `conflicts:` array maps the hard spots) and
`wstein/flix-textmate` (lexicon machine-extracted from the same compiler).

## Governing policy

**Parse the superset; validate later.** Flix's parser deliberately accepts more than the
language allows and rejects the rest in `Weeder2`. This grammar follows the parser. Duplicate
modifiers, illegal modifier/declaration combinations, non-linear patterns, empty
interpolations, unknown annotations, and bad literals all *parse* and are reported by a
separate validation pass. Encoding them as syntax errors would wreck error recovery and
produce token-set-dump diagnostics.

Corollary: do **not** enumerate annotation names or intrinsic names in the grammar. `@Name`
and `%%NAME%%` are single tokens.

## Documented traps

These cost real time to discover. Do not re-derive them.

### Both precedence docstrings are inverted

`Parser2.Op.precedence` (`Parser2.scala:1750`) and `Type.TYPE_OP_PRECEDENCE`
(`Parser2.scala:3492`) both say "lower is higher precedence". They are **wrong**.
`rightBindsTighter` compares `right.precedence > left.precedence`, so a **larger number binds
tighter**. Verify against `1 + 2 * 3`.

This bit twice. ANTLR assigns precedence by alternative order with the **earliest binding
tightest**, so a left-recursive rule must list the table in reverse. The type rule was written
with the arrow first, making the loosest type operator the tightest; fixing it moved the corpus
rate from 67% to 91%.

### User-defined operators bind tighter than every built-in arithmetic operator

`GenericOperator` and `NameMath` sit at level 11; `+`/`-` at 6, `*`/`/` at 7. So `a |> b + c`
parses as `(a |> b) + c`. Unary prefixes are *not* tightest: `discard` is 9, below the
backtick infix at 10 and user operators at 11.

### Operator lexing is "maximal run, then exact match" — not longest-prefix

Scan the maximal run of `[+\-*<>=!&|^$]`. If the **whole run** is exactly a reserved spelling,
emit that token; otherwise emit `GenericOperator`. ANTLR's default longest-match would split
`<--` into `<-` and `-`; Flix makes it one `GenericOperator`. Note the run set excludes
`/`, `~`, `?`, `#`, `%`, and `:`.

### `->` and `.` are whitespace-sensitive

- `a->b` (no whitespace either side) is `ArrowThinRTight` — struct field access.
  `a -> b`, `a ->b`, `a-> b` are all `ArrowThinRWhitespace` — the function/type arrow.
  Out-of-bounds counts as whitespace. `Lexer.scala:349-361`.
- `.` followed by whitespace is `DotWhiteSpace` — the Datalog constraint terminator.
  `.` *preceded* by whitespace is an error (`FreeDot`). Otherwise `Dot`. `Lexer.scala:315-323`.
  There is no `..` or `...` token.

### `!` and `$` are name characters

`isNameChar = letter | digit | _ | ! | $`. So `let!` is one identifier, not `let` + `!`, and
`Map$Entry` is one `NameUppercase`. Keyword matching therefore needs a `!isNameChar` tail
guard, never a plain word boundary.

`$name` drops the `$` from the token span and forces `NameLowercase` — it escapes a keyword.

### `_` is a prefix dispatcher, not a name character at position 0

`_foo` → name; `_⊆` → `NameMath`; `_+` → `GenericOperator` (text includes the `_`); `_1` →
`Underscore` + int; bare `_` → `Underscore`.

### Only one Unicode range exists

`isMathNameChar` is **U+2200–U+22FF only** (the docstring above it says U+2190, and is wrong).
Characters in range join into a single `NameMath` identifier, which doubles as a binary
operator. Everything else non-ASCII is a lex error — there is no `→`, no `λ`, no `¬`.

### `Array#`, `List#`, `Map#`, `Set#`, `Vector#`, `choose*` are single keyword tokens

The `#`/`*` is part of the spelling, and they carry a `!isNameChar` tail guard: `Array#x` must
fall back to `Array`, `#`, `x`.

### String interpolation emits fused delimiter tokens

In `"a${x}b${y}c"` the token between the two interpolations is **one** token with text `}b${`,
typed `LiteralStringInterpolationL`, distinguished from a nested opener by its leading `}`.
Nesting is unbounded and needs a mode stack *plus* a per-level brace-depth counter.

### Things that do not exist in this version

`sig`, `let*`, `letrec`, `\x -> e` lambdas, or-patterns, as-patterns, `ref`/`deref`/`:=`,
`##` Java interop, `try … with`, `do op(…)`, `without`, `~>`, `..`, binary literals, `0X`,
`0b`, uppercase `E` exponents, `%{` debug strings, `##` as a token, and comparison chaining.
`Pure` and `IO` are ordinary uppercase names, not keywords. `**`, `|>`, and `>>` are
`GenericOperator`, not distinct tokens.

Three `TreeKind`s are dead — `Type.Function`, `Expr.InstanceOf`, `TypeParameter`. Do not
create rules for them.

### `:` has no `GenericOperator` fallback

`:`, `:-`, `::`, `:::` are reserved, but `:` is not a user-operator character, so `:=` and
`::=` are **lex errors**, one per `:`. Bug-compatibility here is optional.

## Known hard spots (from tree-sitter-flix's conflict list)

Ranked by how much trouble they caused an LR grammar. ANTLR's ALL(*) handles most of these
natively, but alternative **order** matters because ANTLR resolves true ambiguity silently by
first-alternative-wins.

1. **Pattern vs expression** — 8 of 22 conflicts. Literals, `(`, `{`, and bare identifiers are
   valid in both positions and the disambiguator (`=>`, `=`, `<-`) is arbitrarily far right.
2. **Parameter vs variable vs qualified name** — nothing decides until a `->` or `:` appears.
3. **`(` overload** — unit, unit-lambda, operator section, lambda params, parenthesized
   expression, ascription, tuple. `Parser2.scala:2033-2096` uses a hand-rolled scanner.
4. **`{` overload** — block, record literal, record operation, record pattern, record type,
   effect set, and every declaration body. `{}` is an empty *effect set* in type position.
   `isBlockExpr` (`Parser2.scala:1636`) decides with two tokens of lookahead.
5. **Datalog is the cost center.** `query`/`solve`/`psolve`/`inject`/`pquery` each take a
   greedy comma-separated expression list, which makes `,` globally ambiguous. In tree-sitter
   this took generation from 16s to 7–11 minutes. In ANTLR the cost lands at *runtime* as
   full-context prediction instead. Share one `fixpointExpressions` rule; measure with
   `PredictionMode.LL_EXACT_AMBIG_DETECTION`.

## Verification

A real-corpus parse-rate gate is what actually drives a grammar to correctness; hand-written
tests miss breadth. tree-sitter-flix went 77% → 100% on an 890-file corpus that way, and a
single omission — that Flix definitions may be *named* by a user-defined operator, as in
`def >>` — accounted for every standard-library failure at the 89% mark.

Also check rule **reachability**, not just that the corpus parses. In tree-sitter-flix,
`invoke_method` matched 0 times across 890 files because `apply_expression(get_field(...))`
shadowed it — a silently dead rule that a parse-rate gate cannot detect.

## Commands

```bash
./gradlew build          # generate, compile, test, verify coverage floor
./gradlew ktlintFormat   # formatter — run before every commit
./gradlew ktlintCheck    # lint
```

Commit per phase with conventional-commit messages.
