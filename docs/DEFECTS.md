# Known defects

Findings from measuring the grammar against the real Flix corpus
(`wstein/flix-fork@debf7df`, 692 `.flix` files). Ordered by impact.

Measured parse rate at the time of writing: **10.26% (71 / 692)**.

The build was green and all 38 unit tests passed while the grammar rejected nine out of ten
real Flix files. The tests only ever exercised hand-written snippets, so they could not detect
this. See D4.

---

## D1 — 22 real keywords are missing from the lexer

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

## D2 — 24 phantom keywords that do not exist in Flix

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

## D3 — Holes are not tokenized at all

`???` (`HoleAnonymous`), `?name` (`HoleNamed`) and `name?` (`HoleVariable`) have no lexer
rules and no parser alternative. `???` is Flix's standard "unimplemented" marker and appears
throughout the corpus and the standard library; `def f(): Bool = ???` does not parse.

**Fix**: add the three token rules (`Lexer.scala:153`, `:470-482`, `:509-513`) and an
expression alternative.

## D4 — The corpus gate did not test a corpus

`CorpusCoverageTest.testCorpusParseRate` wrote two hand-written snippets into a temporary
directory and parsed those. It never touched the corpus, so the parse rate it reported was
meaningless and a 10% grammar showed as green.

`testRuleReachability` likewise did not measure reachability: it walked one snippet's tree and
asserted the walker visited more than ten nodes, which is true for any non-empty parse.

**Status**: fixed. The test now parses a real corpus and enforces a ratcheting baseline
committed to `fixtures/corpus-baseline.json`. It skips, rather than passes, when no corpus is
available, so an absent corpus can never read as success.

## D5 — Enum bodies and case separators are too strict

`enum E0` with no body is rejected, and `enum E { case A case B }` (no comma between cases) is
rejected. `Parser2.scala:1214` defines `FIRST_ENUM_CASE = { CommentDoc, 'case', ',' }` and
deliberately accepts `case A, B, C`, `case A, case B,` and `case A case B` alike. The enum
body is also optional.

## D6 — Stray input falls through to the Datalog constraint rule

Errors in declaration position resync into the Datalog constraint rule, producing
`expecting {DOT, DOT_WS, '(', ':-'}` on ordinary declarations. The diagnostics are actively
misleading. This is an alternative-ordering problem in `compilationUnit`.

---

## Remediation order

D1, D2 and D3 are lexical and mechanical, and together should move the parse rate the most.
Re-measure after each — the corpus gate now makes that a single command. D5 and D6 are parser
changes that only become visible once the lexer is correct.

Do not raise the baseline by hand. Let the gate ratchet it.
