# Known defects

Findings from measuring the grammar against the real Flix corpus
(`flix/flix@318bb51`, 692 `.flix` files). Ordered by impact.

Measured parse rate: **90.90% (629 / 692)**, up from 10.26% when this log was opened.

D1-D6, D9 and D10 are resolved. D7 is open. The entry that made the rest possible is D4: the
build was green and all 38 unit tests passed while the grammar rejected nine out of ten real
Flix files, because the tests only ever exercised hand-written snippets.

Do not raise the corpus baseline by hand; let the gate ratchet it.

---

## D1 — 22 real keywords are missing from the lexer — RESOLVED

`grammars/FlixLexer.g4` defines 86 keywords, but they are not the 84 keywords Flix actually
has. Absent, despite being in `Lexer.scala:49-139`:

```
Static  Univ  checked_cast  checked_ecast  ematch  forA  forM  foreach  handler
lawful  rvadd  rvand  rvnot  rvsub  static  super  throw  trait  unchecked_cast
unsafe  xor  xvar
```

`trait`, `throw`, `foreach`, `handler`, `super`, `unsafe`, `Static` and `Univ` are core
constructs. Their absence alone accounts for a large share of the failures.

**Fix**: regenerate the keyword table from `Lexer.scala:49-139`. The authoritative list of 84
is reproduced in `CLAUDE.md`.

## D2 — 24 phantom keywords that do not exist in Flix — RESOLVED

These are defined as keywords but are ordinary identifiers in Flix 0.75.1. Any real program
using one as a name fails to parse:

```
assert  bool  built_in  chan  char  class  deref  do  float32  float64  for  get
in  int8  int16  int32  int64  namespace  op  open  override  ref  set  string
without
```

`namespace` and `class` are from a Flix version that predates `mod` and `trait`. `ref`,
`deref`, `do` and `without` were removed from the language. `bool`, `char`, `string`, `int32`
and friends were never keywords — Flix's primitive types are the *uppercase* names `Bool`,
`Char`, `String`, `Int32`, which resolve as ordinary type names.

This is the same defect class `flix-textmate` catalogues as its "phantom keywords" finding.

**Fix**: delete these rules and every parser reference to them.

## D3 — Holes are not tokenized at all — RESOLVED

`???` (`HoleAnonymous`), `?name` (`HoleNamed`) and `name?` (`HoleVariable`) have no lexer
rules and no parser alternative. `???` is Flix's standard "unimplemented" marker and appears
throughout the corpus and the standard library; `def f(): Bool = ???` does not parse.

**Fix**: add the three token rules (`Lexer.scala:153`, `:470-482`, `:509-513`) and an
expression alternative.

## D4 — The corpus gate did not test a corpus — RESOLVED

`CorpusCoverageTest.testCorpusParseRate` wrote two hand-written snippets into a temporary
directory and parsed those. It never touched the corpus, so the parse rate it reported was
meaningless and a 10% grammar showed as green.

`testRuleReachability` likewise did not measure reachability: it walked one snippet's tree and
asserted the walker visited more than ten nodes, which is true for any non-empty parse.

**Status**: fixed. The test now parses a real corpus and enforces a ratcheting baseline
committed to `fixtures/corpus-baseline.json`. It skips, rather than passes, when no corpus is
available, so an absent corpus can never read as success.

## D5 — Enum bodies and case separators are too strict — RESOLVED

`enum E0` with no body is rejected, and `enum E { case A case B }` (no comma between cases) is
rejected. `Parser2.scala:1214` defines `FIRST_ENUM_CASE = { CommentDoc, 'case', ',' }` and
deliberately accepts `case A, B, C`, `case A, case B,` and `case A case B` alike. The enum
body is also optional.

## D6 — Stray input falls through to the Datalog constraint rule — RESOLVED

Errors in declaration position resync into the Datalog constraint rule, producing
`expecting {DOT, DOT_WS, '(', ':-'}` on ordinary declarations. The diagnostics are actively
misleading. This is an alternative-ordering problem in `compilationUnit`.

## D7 — Java interop and remaining edge cases

The 63 files that still fail cluster around Java interop (anonymous classes with method
bodies, `[Object]`-style type arguments), a handful of dot-position cases, and named
arguments in some call positions. These are genuine grammar gaps rather than defects in the
derivation, and each needs its own reading of `Parser2.scala`.

## D8 — The corpus gate does not run in CI — RESOLVED (documented)

The gate needs a Flix checkout and skips without one, so CI cannot currently enforce the
ratchet. Contributors must run it locally; the README states this. Wiring a pinned corpus
checkout into CI would close the gap and is tracked as a non-blocking follow-up.

## D9 — `/` listed as a reserved operator in the antlr-ng target — RESOLVED

`antlr-ng/src/FlixLexerBase.ts` mapped `/` to `SLASH` in its reserved-operator table. Since
`/` is excluded from the user-operator character set, it can never appear in an operator run,
so the entry was unreachable and misleading. Removed to mirror the Java implementation.

## D10 — CI matrix was a no-op — RESOLVED

The build job pinned a `java:21-bookworm` container while running a 21/25 JDK matrix, so both
legs built on the image's JDK 21 and the JDK 25 leg proved nothing. The container is gone and
`actions/setup-java` now supplies the matrix JDK.

## D11 — The antlr-ng target cannot consume the shared grammar

`grammars/` is described as canonical for both targets, but only the JVM target can generate
from it today. Two independent obstacles:

1. **Embedded actions are Java syntax.** The lexer's actions and predicates call
   `isNameCharFollow()`, `enterBrace()` and `_input.LA(1)` unqualified. TypeScript requires
   `this.` on every one, and Java forbids nothing — so no single spelling satisfies both.
   ANTLR has no target-portable action syntax.
2. **antlr-ng ignores `options { superClass }` for TypeScript.** The generated lexer extends
   nothing, so every helper call is unresolved and `override` is rejected
   (`TS4112`).

Generation itself succeeds and is kept as `npm run generate:experimental`; its output is not
committed and does not type-check. CI type-checks only the hand-written `FlixLexerBase.ts`.

Closing this needs a small pre-processing step that emits a TypeScript-adapted copy of the
grammars — qualifying action calls with `this.` and injecting the superclass import — rather
than pointing antlr-ng at `grammars/` directly.

Note also that `antlr4ng-cli` never had a 3.x release, so the declared `^3.0.0` could not
install at all; the target had therefore never been built. `FlixLexerBase.ts` did not compile
either, using the Java field `_input` instead of antlr4ng's `inputStream`, calling `reset()` on
a plain array, and reading `charIndex`. All three are fixed.

## D12 — U+FFFF cannot be lexed

ANTLR's Java target reserves U+FFFF in its serialized ATN, so no lexer rule can match it —
adding it explicitly to `STRING_CONTENT` changes nothing. When it appears in a source file the
lexer cannot leave `STRING_MODE`, and every character after it is silently dropped.

One corpus file is affected (`TestJson.flix`, which tests JSON escaping of the Unicode
non-characters). The token-tiling property excludes files containing U+FFFF and reports the
count, rather than weakening the property for every other character. Nothing else in the corpus
loses a single character.

---

## How these were found

Every defect above came from one measurement — parsing a real Flix checkout — rather than from
review. None was visible in a passing test suite. When adding a construct, re-measure; when a
number does not move, the construct was not the problem.
