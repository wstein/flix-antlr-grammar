# Migrating to Flix v0.77.0 and the current flix-spec

Status: **not started.** `conformance/baseline.json` records `flixSpecArtifact` 0.75.8 and
`flixSpecPinCommit` `40949531...` (Flix v0.75.2), two releases behind.

## What changed in Flix

This repository is pinned to Flix **v0.75.2** (`40949531`). The reference has moved twice since.

### v0.75.2 → v0.76.0

- Effects accept type parameters. Generic *operations* remain invalid and now report
  `IllegalOperationTypeParams` rather than `IllegalEffectTypeParams`.
- Malformed `match` and `ematch` expressions retain the match node and the scrutinee through
  ordinary recovery instead of collapsing.
- **Vocabulary unchanged**: 191 TreeKinds and 158 TokenKinds, same names, same digests.

### v0.76.0 → v0.77.0

- **`+UsesOrImports.Package`** (TreeKind 191 → 192). `use` now recognises a package path:
  `use flixball::Game.Board` and `use flixball::{Game, Board}`.
- **`+ColonColonTight`** (TokenKind 158 → 159). `::` written **without surrounding whitespace** lexes
  as a distinct token. Tight `::` is the package-path separator; spaced `::` remains list cons.
  Writing the separator with whitespace is now a `Malformed` error.
- Nothing was removed or re-parented. Both releases are additive at the vocabulary level.
- Internally, Flix deleted its `Reader` phase and `shared.Input`. That broke `flix-spec`'s own
  adapter and is fixed there; it does not reach consumers.

> **`ColonColonTight` is the one that bites quietly.** Upstream left `("::", ColonColon)` in the
> lexer's operator table and decides tightness in hand-written dispatch outside every table. Nothing
> that scrapes or reflects over that table sees a change. A rule matching `ColonColon` today simply
> stops matching `a::b`, with no error anywhere.

## What changed in flix-spec

Beyond the pin, the release you are moving to changes four things that affect consumers.

**1. The transparency contract is stated per occurrence, and is much larger.**
It used to admit a kind only if *every* occurrence had at most one child. It now fires per
occurrence — dropped when empty, replaced when singular, kept when branching — which admitted four
kinds every structural consumer was already eliding for itself: `Expr.Expr`, `Pattern.Pattern`,
`QName`, `UsesOrImports.UseOrImportList`. A third rule, `elide-empty`, drops empty `AnnotationList`
and `ModifierList` without splicing their tokens.

Normalisation now removes **2285 of 4449 nodes (51.4%)**, up from 753 of 4398 (17.1%). Canonical
trees are substantially smaller and every baseline is stale.

Because the rules fire per occurrence, an elided kind is **not always absent**: `QName` survives
wherever a name is qualified (23 occurrences), and `ModifierList` wherever it holds a modifier (12).
Mappings onto those are legitimate, and `validateProjectionMap` now decides that by measuring
`fixtures/expected` rather than inferring it from the rule name.

**2. A fourth lane: `diagnostic_conformance`.**
It compares whether the same units are **rejected**, and whether each carries the same gated
`kind`/`line`. Accept/reject needs no tree, no projection map and no shared vocabulary. If your
diagnostic names are your own, declare `diagnosticMappings` in your projection map; without it the
lane compares accept/reject alone and says so. A consumer that emits no diagnostics at all is
`not-applicable`, not failed.

**3. Depth is published and can be gated.**
Reports now carry `nodesExpected` and `depthPercent` beside `nodesCompared`, and the CLI accepts
`--depth-floor` / `--recovery-depth-floor`. Report `schemaVersion` is **7**. A version-6 report's
depth was computed against the walk rather than the expectation — it read *highest* for the maps
that skipped most — so old and new depth figures are not comparable.

**4. `source_invariants` gained `token-positions`.**
Token `start`/`end` were schema-required and read by nothing. The lane now checks that each token's
text is what its source holds at those offsets, that tokens advance in order, and that what lies
between them is only whitespace or the `$` escape. It stands down for consumers that emit no tokens.

New projection-map keys, both optional: `dropWhenEmpty` (the consumer-side counterpart of
`elide-empty`) and `diagnosticMappings`.

## What this repository must do

### 1. Move the pin

`conformance/baseline.json` — `flixSpecArtifact` and `flixSpecPinCommit` to
`4a5b60a31ac03bb762f68b554a0fc2b6f4d982b9`. `scripts/flix-spec-conformance.sh` refuses to run on a
pin or fixture-revision mismatch, so it will stop you first.

### 2. The contract change helps you more than anyone

This grammar sits at **41% depth** — by far the lowest of the structural consumers — because an
unmapped node costs its entire subtree against the denominator. Normalisation now removes 51.4% of
the canonical tree rather than 17.1%, so a large share of what you were failing to reach no longer
exists to be reached. Re-measure before doing any other work: the number will move on its own.

Then delete the six now-redundant `elide` entries from `conformance/projection-map.json`:

```
AnnotationList  Expr.Expr  ModifierList  Pattern.Pattern  QName  UsesOrImports.UseOrImportList
```

`CommentList`, `Expr.Statement` and `Type.Apply` stay. You have no mappings onto elided kinds, so
nothing there needs review.

### 3. The real depth problem is one line, and it is not the contract

`antlr4/src/main/kotlin/io/github/wstein/flix/antlr/cli/Projection.kt:69` uses

```kotlin
val kind = ruleNames[ctx.ruleIndex]
```

`ctx.ruleIndex` is identical across every labelled alternative of a rule, so all 49 labelled
alternatives of `expr` collapse back to the string `expr`. The grammar already carries **62 labelled
alternatives** (`# ApplyExpr`, `# AddExpr`, `# ConsExpr`, `# MatchExpr`, …): 49 on `expr`, 11 on
`type`.

Those two rules are the top of your own `unmapped` list — `expr` 39, `type` 21, **60 of 112 stops**.
The fix is to emit the labelled alternative's name, but **not** by taking the class name unconditionally.
`ctx::class.simpleName!!.removeSuffix("Context")` also renames every *unlabelled* rule —
`BlockContext` becomes `Block` — and every key in `conformance/projection-map.json` (`block`,
`defDeclaration`, all 41 `mappings`, plus `ignored` and `flatten`) is the camelCase rule name. Every
existing mapping would stop matching at once.

Use the class name only when it is a strict subclass of the rule's own context class, which is
exactly what a labelled alternative is:

```kotlin
val kind =
    if (ctx.javaClass.superclass != ParserRuleContext::class.java) {
        ctx.javaClass.simpleName.removeSuffix("Context") // # AddExpr -> AddExpr
    } else {
        ruleNames[ctx.ruleIndex] // block -> block
    }
```

ANTLR labels all of a rule's alternatives or none of them, so after this change `expr`, `type` and
`pattern` are never emitted again. Their `ignored` entries go dead and should be replaced by entries
for the labels themselves: each label is either mapped or declared `ignored`.

### 4. Two missing nodes keep three of the largest canonical kinds unreachable

Do this together with (3), not after — on its own, (3) turns some kind divergences into arity
divergences.

- `Expr.Binary` has canonical arity **3** (lhs, `Operator`, rhs). `Projection.kt` emits only
  `ParserRuleContext` children, and `expr ( PLUS | MINUS ) expr` gives the operator no context, so a
  labelled `AddExpr` renders with 2 children.

  **Do not extract a shared `binaryOp` rule.** `expr` gets its precedence from the *order* of its
  alternatives (`FlixParser.g4:355-395`). A single `expr binaryOp expr` alternative collapses every
  level into one. Putting `binaryOp` into every level instead makes each level accept every operator,
  and the rule becomes ambiguous.

  Synthesise the node in the projection instead. When a labelled binary context has a
  `TerminalNode` between two `ExprContext` children, `Projection.kt` emits `{"kind":"Operator"}`
  with that token's span. That leaves the grammar untouched: the corpus rate cannot move, and
  `fixtures/snapshots/`, `docs/SYNTAX.md` and `docs/RAILROAD.md` do not change. The cost is a small,
  explicit special case in the projection code rather than a declarative map entry. The binary
  labels are `UserOpExpr`, `AngledPlusExpr`, `MultExpr`, `AddExpr`, `ConsExpr`, `CompareExpr`,
  `EqualityExpr`, `AndExpr` and `OrExpr`. Two of them need a decision of their own:
  - `UserOpExpr`'s `nameMath` operator is already a rule child, mapped to `Ident`. It has to become
    the `Operator` rather than sit beside it.
  - `InfixCallExpr` (`` a `f` b ``) has no single operator token. Check its shape in flix-spec's
    `fixtures/expected` before mapping it.

  If the grammar route is ever preferred, it needs one operator rule per precedence level (`multOp`,
  `addOp`, `consOp`, …). `expr multOp expr` is still a binary alternative for ANTLR's left-recursion
  rewrite, so precedence survives.
- `Expr.Apply` has canonical arity **2** (callee, `ArgumentList`) against
  `expr LPAREN ( argument ( COMMA argument )* )? RPAREN` (`FlixParser.g4:357`). Extract an
  `argumentList` rule and map it to `ArgumentList`. This one is a grammar change, but a safe one:
  the argument list is not left-recursive, so precedence is unaffected.

  The list is written inline in **five** places, not one, and the extraction should cover all of
  them:

  | Alternative | Line | Elements |
  | --- | --- | --- |
  | `ApplyExpr` | 357 | `argument` |
  | `FieldOrMethodExpr` | 359 | `argument` |
  | `ExtTagExpr` | 390 | `expr` |
  | `NewExpr` | 405 | `expr` |
  | `SuperExpr` | 406 | `expr` |

  Check each canonical parent in `fixtures/expected` (`Expr.ExtTag`, `Expr.InvokeConstructor`,
  `Expr.InvokeSuperConstructor`, `Expr.InvokeSuperMethod`) before reusing one rule for all five. Where
  the elements differ (`argument` vs `expr`), a shared rule changes what those three sites accept
  unless it is parameterised. Because the rule is new, `fixtures/snapshots/`, `docs/SYNTAX.md` and
  `docs/RAILROAD.md` change with it.

This also settles the open question in your own `notes.expr`, which records that mapping `expr` to
`Expr.Binary` *"drops agreement 76 → 40, which is the signature of a guess that is often wrong"*.
It is not a bad guess — it is a **missing node**.

### 5. `::` and the package path

`grammars/FlixLexer.g4` must distinguish tight `::` from spaced `::`, and the parser must produce a
node mapping to `UsesOrImports.Package` for `use flixball::Game.Board` and `use flixball::{Game, Board}`.
It will not fall out of the existing rule: `COLON_COLON : '::' ;` (`FlixLexer.g4:166`) is a plain
literal.

**Reuse the existing machinery for whitespace-sensitive tokens rather than a predicate.** `->` is
already split this way:

- `ARROW : '->' { classifyArrow(); }` (`FlixLexer.g4:188`) re-types the token as `ARROW_WS` or
  `ARROW_TIGHT`.
- Those two are virtual tokens in the `tokens {}` block (`FlixLexer.g4:7-13`).
- The classification uses `isWhitespaceBefore()`/`isWhitespaceAfter()`, in
  `FlixLexerBase.java:86-87,108-130` and its mirror `antlr-ng/src/FlixLexerBase.ts:38-42,110-120`.

Add `COLON_COLON_TIGHT` to `tokens {}` and a `classifyColonColon()` to **both** base classes.

Pitfalls:

- **Cons must keep working.** `ConsExpr` (`FlixParser.g4:373`) and `ConsPattern` (`:591`) match
  `COLON_COLON`. Following *parse the superset*, they should accept `COLON_COLON_TIGHT` too and leave
  the spacing error to validation. Otherwise every `x::xs` in the corpus becomes a parse error. This
  is the same trap as the "bites quietly" note above, pointed at this repository.
- **`use a::b` does not parse today at all.** `useClause` (`:26`) is `USE qname …` and `qname`
  (`:291`) joins segments only with `dot`. The package form needs a new alternative that produces a
  node mapped to `UsesOrImports.Package`.
- **The corpus barely exercises it.** On the v0.76.0 corpus, 154 files use spaced `::`. Tight `::`
  appears in 8 files, and all but one occurrence is inside a string literal. The one in code is a
  tight *cons*, `List.point(42::Nil)` (`main/test/ca/uwaterloo/flix/library/TestList.flix:537`). It
  is the only corpus line that catches a broken `ConsExpr`, and nothing in the corpus exercises the
  package path. Add fixtures to `fixtures/positive/` and `fixtures/negative/` and lexer tests;
  `FlixLexerTest.kt:216-219` covers only spaced `::`.
- **Open question: tight `:::`.** Decide from `Lexer.scala` at `4a5b60a` whether it gets a tight
  variant as well. `COLON_COLON_COLON` sits at `FlixLexer.g4:165`.

### 6. The diagnostic lane is your cheapest win

`recovery_conformance` is `not-applicable` here — ANTLR's recovery inserts nodes the parse tree does
not name — so this repository currently produces one derived signal. The new lane needs no tree and
no map: emitting one diagnostic per ANTLR syntax error gives accept/reject agreement across all 146
fixtures. With `diagnosticMappings` translating ANTLR's error names, kind and line compare too.
