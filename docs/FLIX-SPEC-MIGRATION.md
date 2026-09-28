# Migrating to Flix v0.77.0 and the current flix-spec

Status: **not started.** Progress is tracked in the [checklist](#checklist) below; the parse
defect it contains is [D15](DEFECTS.md). `conformance/baseline.json` records, under `measuredAt`,
`flixSpecArtifact` 0.75.8 and `flixSpecPinCommit` `40949531b4d42e5eaf2e4b9997537eaf793c24e7`
(Flix v0.75.2). That is two releases behind: v0.76.0 and v0.77.0.

## Terms

flix-spec's vocabulary, as this document uses it. The full definitions are in flix-spec's
`docs/CONFORMANCE.md` and `docs/PROJECTION.md`.

| Term | Meaning |
| --- | --- |
| **lane** | One independent comparison in a conformance report: `oracle_conformance` (tree shape), `recovery_conformance`, `diagnostic_conformance`, `source_invariants`. Each has its own verdict, which may be `not-applicable`. |
| **projection map** | `conformance/projection-map.json`. It maps this grammar's rule names onto flix-spec's canonical `TreeKind`s and declares which of our nodes are transparent (`ignored`, `flatten`). |
| **elide** | A canonical kind skipped during comparison. Upstream now applies most of these itself, via `ast/transparency.json`. |
| **unmapped / stop** | A native node that is neither mapped nor ignored. The comparison stops there, skipping it and its whole subtree. `nodesUnmapped` counts these. |
| **depth** (`depthPercent`) | Nodes actually compared as a share of `nodesExpected`, the canonical tree's size after normalisation. Read it together with `fixturesAgreeing`: a map that maps little agrees with almost everything. |
| **kind / arity divergence** | A compared node whose canonical kind differs, or whose child count differs. |
| **`schemaVersion`** | Per artifact. Reports are at 7 with flix-spec 0.77.0 and unchanged in 0.77.1 (6 before). The projection documents this repository writes are at 2. |

## What changed in Flix

This repository names **three** Flix revisions, and they answer different questions:

| Pin | Where | Answers |
| --- | --- | --- |
| `318bb51` (Flix 0.75.1) | `CLAUDE.md` "Source of truth", `docs/DEFECTS.md` | Which `Lexer.scala`/`Parser2.scala` the grammar was transliterated from |
| `debf7df0fdd63f2b76b7a539ebbff5243070ca6e` | `.github/workflows/ci.yml:56,117`, `fixtures/corpus-baseline.json` | Which corpus the parse-rate gate runs over |
| `40949531b4d42e5eaf2e4b9997537eaf793c24e7` (v0.75.2) | `conformance/baseline.json` `measuredAt` | Which flix-spec expectations the conformance lanes compare against |

Only the conformance pin is at v0.75.2. The upstream reference has moved twice since.

This migration moves all three to v0.77.0 (`4a5b60a31ac03bb762f68b554a0fc2b6f4d982b9`), each in its
own step (see the checklist). Two things to settle while doing it:

- **Two parse-rate figures are in circulation, and they agree.** `fixtures/corpus-baseline.json`
  records `rate: 1.0`. `CLAUDE.md` says 99.85% (687/688). The gate excludes one file that Flix ships
  truncated on purpose (`test/flix/resiliency/ford-fulkerson-prefix.flix`, see `docs/DEFECTS.md`);
  1.0 is the rate without it and 687/688 is the rate with it. State which one is meant wherever the
  number is quoted.
- **The corpus pin could not be found locally.** `debf7df` is absent from the local `flix/flix`
  checkout, which is at v0.76.0, and from `flix-fork`. `actions/checkout` resolves any commit in the
  repository network, fork commits included. So before re-pinning, confirm that the new ref is an
  upstream tag: `v0.77.0` is.

### v0.75.2 → v0.76.0

- Effects accept type parameters. Generic *operations* remain invalid and now report
  `IllegalOperationTypeParams` rather than `IllegalEffectTypeParams`.
- Malformed `match` and `ematch` expressions retain the match node and the scrutinee through
  ordinary recovery instead of collapsing.
- **Vocabulary unchanged**: 191 TreeKinds and 158 TokenKinds, same names, same digests.

### v0.76.0 → v0.77.0

- **`+UsesOrImports.Package`** (TreeKind 191 → 192). `use` now recognises a package path:
  `use flixball::Game.Board` and `use flixball::{Game, Board}`.
- **`+ColonColonTight`** (TokenKind 158 → 159). `::` written **without surrounding whitespace**
  lexes as a distinct token. Tight `::` is the package-path separator, and *both* forms are list
  cons: the parser accepts `ColonColonTight` in cons position. Writing the separator with
  whitespace is now a `Malformed` error.
- Nothing was removed or re-parented. Both releases are additive at the vocabulary level.
- Internally, Flix deleted its `Reader` phase and `shared.Input`. That broke `flix-spec`'s own
  adapter and is fixed there; it does not reach consumers.

> **`ColonColonTight` is the one that bites quietly.** Upstream left `("::", ColonColon)` in the
> lexer's operator table and decides tightness in hand-written dispatch outside every table. Nothing
> that scrapes or reflects over that table sees a change. A rule matching `ColonColon` today simply
> stops matching `a::b`, with no error anywhere.

## What changed in flix-spec

Beyond the pin, flix-spec 0.77.0 changes four things that reach this repository. Sources:
flix-spec's `docs/MIGRATION-v0.77.0.md`, `docs/CONFORMANCE.md` and `docs/PROJECTION.md` §4.1.

**Adopt 0.77.1, not 0.77.0.** It carries the same upstream pin and is additive: same three
vocabularies, same report `schemaVersion` 7, both fixture forms unchanged in shape. What it adds is
described in "What 0.77.1 adds" below, and one of those items bears directly on the compatibility
policy this repository has adopted.

**1. The transparency contract is stated per occurrence, and is much larger.**
It used to admit a kind only if *every* occurrence had at most one child. It now fires per
occurrence — dropped when empty, replaced when singular, kept when branching — which admitted four
kinds every structural consumer was already eliding for itself: `Expr.Expr`, `Pattern.Pattern`,
`QName`, `UsesOrImports.UseOrImportList`. A third rule, `elide-empty`, drops empty `AnnotationList`
and `ModifierList` without splicing their tokens.

Normalisation now removes **2301 of 4484 nodes (51.3%)** at 0.77.1 (2285 of 4449 at 0.77.0; flix-spec `docs/MIGRATION-v0.77.0.md`,
"Validation"), up from 753 of 4398 (17.1%) under the previous contract. Canonical trees are
substantially smaller and every baseline is stale.

Because the rules fire per occurrence, an elided kind is **not always absent**: `QName` survives
wherever a name is qualified (23 occurrences), and `ModifierList` wherever it holds a modifier (12);
both counted over flix-spec 0.77.0's `fixtures/expected`.
Mappings onto those are legitimate, and `validateProjectionMap` now decides that by measuring
`fixtures/expected` rather than inferring it from the rule name.

**2. A fourth lane: `diagnostic_conformance`.**
It compares whether the same units are **rejected**, and whether each carries the same gated
`kind`/`line`. Accept/reject needs no tree, no projection map and no shared vocabulary. A consumer
whose diagnostic names are its own declares `diagnosticMappings` in its projection map; without it
the lane compares accept/reject alone and says so. A consumer that emits no diagnostics at all is
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

### What 0.77.1 adds

- **`ast/annotation.json`** — the 16 annotations the reference defines, digest-pinned. A third
  vocabulary, because the lexer emits a single `TokenKind.Annotation` for all of them and the name
  lives in the token's `text`, where no `TokenKind` digest can see it change. It is a **coverage**
  vocabulary and never a validity check: the token is genuinely open, because Java interop
  annotations lex identically and upstream models that with `Annotation.Error`. Nothing is required
  of this grammar, which already treats an annotation as one token.
- **`ast/retired.json`** — vocabulary the reference has removed, with the tag each went at:
  `Decl.Law`, `KeywordLaw`, `KeywordLawful`, all gone at v0.75.2. This is the file D16 wanted; a
  removal otherwise leaves nothing behind but a digest that stopped matching.
- **The fixture suite is 147**, not 146 — one fixture covers the three annotations Flix's own
  corpus never uses.
- **`validateProjectionMap` now reports deprecated keys.** `elide` and `flattenCanonical` are
  marked deprecated in the schema; the run prints a `NOTE:` naming them. Step 2 does **not** clear
  it — the note fires for as long as the key is present at all. What step 2 does is reduce it to
  one justified key: `CommentList`, the single canonical kind this grammar cannot produce, and the
  one exception to "elide nothing Flix does not". The note now lists removable and remaining
  entries separately, so the residue is visible.

- **A reference defect, FLIX-0002**, found by flix-spec's new advisory `Weeder2` run. It is an
  exception to the compatibility policy and is stated there, below, rather than here. The
  `Weeder2` run itself is advisory only in flix-spec: no lane, no report field, no schema change,
  so there is nothing to consume.

## Compatibility policy: not stricter, not looser

The target is "accepts the same programs as Flix 0.77.0". The grammar and the validation pass,
taken together, should accept and reject exactly what `Parser2` and `Weeder2` do. *Parse the
superset* decides which of the two layers reports an error; it never decides whether an error is
reported.

Flix has no warnings. `errors/Severity.scala` has `Error`, `Info` and `Hint`, and `Info`/`Hint`
come from `CodeHinter`, which only the LSP servers run. From the command line a program either
compiles or it does not. So:

| Construct | What Flix does | What this grammar does |
| --- | --- | --- |
| **Deprecated syntax** | Accepts it silently. The only case is `pub redef` (`Weeder2.scala:339`), and `--Xno-deprecated` turns it into an error | Accept silently. A `--no-deprecated` switch may turn it into an error, and a hint is fine in an editor context. Never put it in the conformance projection, because Flix reports nothing there |
| **Deprecated library API** (`@Deprecated`) | `CodeHint.Deprecated`, `Severity.Info`, shown only in the IDE | Out of scope: a matter of names, not syntax |
| **Removed syntax** | Gone, and its words become free. `law` and `lawful` are ordinary names in 0.77.0 | Reject, and free the words (D16) |
| **Malformed but recognisable** | Keeps the node and reports the error. Spaced `use a :: B` still yields `UsesOrImports.Package`, plus `Malformed` | Parse it, build the same node, and report the same error from the validation pass |
| **Compiler crash** (`InternalCompilerException`) | Neither accepts nor rejects: it aborts. `a ⊆ b` — a math-name operator used infix — parses cleanly and then throws (flix-spec `FLIX-0002`) | **Exception to the rule above.** Follow `Parser2`, accept the program, report nothing, and cite the flix-spec defect ID |

**An `InternalCompilerException` is not a verdict.** "Accepts the same programs as Flix 0.77.0" has
no answer for an input the compiler aborts on, so the policy needs this clause rather than an
implicit reading. `Parser2` is the authority for those inputs: it has a dedicated `BinaryOp.NameMath`
and lists `NameMath` in `FIRST_BINARY_OP`, so `a ⊆ b` is well-formed and this grammar already
accepts it (`FlixParser.g4:367`). `Weeder2`'s operator match omits `NameMath` and throws. Accept,
do not reproduce the crash, and do not treat it as a rejection. Upstream draft and a standalone
reproduction: <https://github.com/wstein/flix-fork/issues/4>,
<https://github.com/wstein/flix-repro-namemath-infix-crash>.

These were observed by running the 0.77.0 release jar through flix-spec's `extract` task. Every
deprecated or removed case above was probed, not inferred.

## What this repository does not need to do

Several upstream changes are already handled here, or do not apply:

- **Effect type parameters (v0.76.0).** `effDeclaration` (`FlixParser.g4:140`) already accepts
  `typeParams?`. `opDeclaration` (`:144`) accepts them as well. That operations are still illegal is
  a weeding error (`IllegalOperationTypeParams`), and parse-the-superset keeps it out of the
  grammar.
- **`match`/`ematch` recovery (v0.76.0).** This is a recovery-shape change. `recovery_conformance`
  is `not-applicable` for this grammar, so nothing here is compared against it.
- **Flix's `Reader` / `shared.Input` removal (v0.77.0).** This was internal to Flix, and flix-spec
  absorbs it.
- **`token-positions` (flix-spec).** It stands down for consumers that emit no tokens, and
  `Projection.kt` emits rule nodes only; see its KDoc. The `CommentList` elide stays for the same
  reason.
- **Mappings onto newly elided kinds.** There are none to review.

## What this repository must do

The two targets are not affected equally. Projection and conformance exist **only on the JVM side**:
`Projection.kt` is run by `:antlr4:projectFixtures` (`antlr4/build.gradle.kts:44`), and
`antlr-ng/src` holds only `cli.ts` and `FlixLexerBase.ts`. Anything in the shared grammars or the
lexer base classes reaches both targets and has to pass both corpus gates.

| Step | `antlr4` (JVM) | `antlr-ng` (TS) |
| --- | --- | --- |
| Pin, elides, labelled names, synthetic `Operator`, diagnostics | yes | no |
| `argumentList` (shared `FlixParser.g4`) | yes | yes: regenerate, corpus gate |
| `COLON_COLON_TIGHT`, package path (shared grammars + both `FlixLexerBase`) | yes | yes: `FlixLexerBase.ts`, corpus gate |

The steps are ordered by value against effort and risk:

1. Measurement changes that touch no code come first.
2. Then the cheapest independent signal: diagnostics.
3. Then the projection work with the largest depth gain.
4. Grammar and lexer changes come last, because they are the only steps that can move the corpus
   rate.

### Checklist

Commit each step separately. The commit message states the metric change, as `CLAUDE.md` requires
for grammar changes.

Commands:

```bash
CORPUS="$(cd ../../flix/flix/main && pwd)"   # a flix/flix checkout at the CI corpus pin

FLIX_SPEC=../flix-spec scripts/flix-spec-conformance.sh              # conformance, all lanes
./gradlew build -Dflix.corpus="$CORPUS"                              # JVM: tests, snapshots, corpus gate
(cd antlr-ng && npm run generate && FLIX_CORPUS="$CORPUS" npm test)  # TS corpus gate
node tools/gen-docs.mjs && git diff --exit-code -- docs/SYNTAX.md docs/RAILROAD.md
```

Starting figures (`conformance/baseline.json`, flix-spec 0.75.8): depth 41%, `fixturesAgreeing`
76/138, `divergences` 81, `nodesUnmapped` 112. The corpus gate is at `rate: 1.0`
(`fixtures/corpus-baseline.json`).

- [ ] **1. Conformance pin.** Update `baseline.json` `measuredAt` and re-measure with no code change.
      Done when: the script runs clean against flix-spec 0.77.0 and the new depth, agreement,
      `nodesExpected` and `divergences` are recorded. Depth is expected to *rise* with no code
      change, and the schema 6 and schema 7 figures are not comparable.
- [ ] **2. Elides.** Keep only `CommentList`, with its reason in `notes`. Done when: re-measured,
      and any rise in `divergences` is attributed to `Expr.Statement`/`Type.Apply` in the
      commit message.
- [ ] **3. Diagnostics.** Done when: `diagnostic_conformance` is no longer `not-applicable`, and
      accept/reject agreement is recorded as a new ratchet in `baseline.json`.
- [ ] **4 + 5. Labelled names, `Operator`, `ArgumentList`.** Land them together. Done when:
      `nodesUnmapped` is well below 112 (the `expr`/`type` share alone is 60), depth rises, and
      `fixturesAgreeing` does not fall. For `argumentList`, the JVM and TS corpus gates must hold and
      the snapshots and generated docs must be regenerated in the same commit.
- [ ] **6a. Corpus pin.** Move `ci.yml:56,117` and `fixtures/corpus-baseline.json` to v0.77.0 with
      no grammar change. Done when: both gates report their rate against the new corpus. A drop
      here is a real v0.77.0 gap to fix in 6b, not a regression.
- [ ] **6b. Tight `::` and package paths.** Tracked as D15 in `docs/DEFECTS.md`. Done when: both
      corpus gates are back at 1.0, the new positive and negative fixtures pass, and `42::Nil`
      still parses.
- [ ] **6c. Free `law` and `lawful`.** Tracked as D16. Done when: both are ordinary names,
      `fixtures/keywords.txt` holds 82 entries, and both corpus gates hold.
- [ ] **7. `CLAUDE.md`.** Done when: the pin, the parse-rate headline and the traps match the new
      state.

Gates, applied to every step:

- **Corpus rate.** It must not drop on either target, except at 6a, which re-baselines on purpose.
- **Ratchets.** `divergences` and `fixturesAgreeing` must not get worse unless the commit message
  says why, per the `baseline.json` note.
- **Rollback.** Revert any step that fails a gate, rather than lowering a baseline to fit it.
  Steps 1–5 touch only `conformance/`, `Projection.kt` and, for `argumentList`, the grammar.
  Reverting the commit is a complete rollback.

### 1. Move the conformance pin and re-measure

In `conformance/baseline.json`, update these fields under `measuredAt`:

- `flixSpecArtifact` → `0.77.1`
- `flixSpecPin` → `v0.77.0`
- `flixSpecPinCommit` → `4a5b60a31ac03bb762f68b554a0fc2b6f4d982b9`
- `fixtures` → `147` (was 138)
- `fixtureRevision` → the value in the new report's `provenance.fixtureRevision`

`scripts/flix-spec-conformance.sh` guards these fields at two different points:

- **Before running**, it refuses on a pin mismatch (`:38-44`).
- **After projecting and comparing**, it fails the run on a fixture-revision mismatch (`:77-85`).
  So a stale `fixtureRevision` costs a full run before the mismatch is reported.

### 2. Reduce `elide` to what the grammar cannot produce

The contract change matters most to a low-depth consumer, and this grammar sits at **41% depth**
(`conformance/baseline.json`, `lanes.oracle_conformance.depthPercent`) because an unmapped node
costs its entire subtree against the denominator. Normalisation now removes 51.4% of the canonical
tree rather than 17.1%, so a large share of what this grammar was failing to reach no longer exists
to be reached. Step 1's re-measurement shows how far the number moves on its own; record it before
changing anything else.

Then cut `elide` in `conformance/projection-map.json` down to one entry. The rule is the
compatibility policy above: compare against the compiler's own tree as flix-spec normalises it, and
elide nothing it does not. flix-spec's `docs/PROJECTION.md` deprecates `elide` apart from
"a canonical kind that consumer genuinely cannot produce".

| Entry | Verdict | Why |
| --- | --- | --- |
| `AnnotationList`, `Expr.Expr`, `ModifierList`, `Pattern.Pattern`, `QName`, `UsesOrImports.UseOrImportList` | delete | Upstream now removes them exactly where Flix's tree carries no structure. A local elide would also hide the occurrences it keeps, such as 23 qualified `QName`s and 12 non-empty `ModifierList`s |
| `Expr.Statement` | delete | Real structure in Flix's tree (14 occurrences in `fixtures/expected`). Eliding it hides divergences the reference would see, so it is mapping work for `statement` instead |
| `Type.Apply` | delete | Real structure (15 occurrences). Map it from `# ApplyType` (`FlixParser.g4:236`) once step 4 emits labels |
| `CommentList` | **keep** | The grammar genuinely cannot produce it: comments go to the `COMMENTS`/`DOC_COMMENTS` channels and never reach the parse tree (`Projection.kt` KDoc). Record that reason in the map's `notes` |

Deleting `Expr.Statement` and `Type.Apply` will *raise* `divergences`. The comparison is not
getting worse; divergences that were hidden become visible. Record the rise and its cause in the
commit message, as the `baseline.json` note requires, and let step 4 bring the number back down.
The map has no mappings onto elided kinds, so nothing else needs review.

### 3. Emit diagnostics

This is the cheapest win in the migration. `recovery_conformance` is `not-applicable` here — ANTLR's
recovery inserts nodes the parse tree does not name — so this repository currently produces one
derived signal. The new lane needs no tree and no map: emitting one diagnostic per ANTLR syntax
error *measures* accept/reject across all 147 fixtures of flix-spec 0.77.1 (the current
baseline measured 138). With `diagnosticMappings` translating ANTLR's error names, kind and line
compare too.

It is cheap because the errors are already collected and then thrown away:

- `Projection.kt:151` writes `"diagnostics": []` unconditionally.
- The `quiet` listener (`Projection.kt:110-120`) already receives every lexer and parser error, with
  line and column, and discards them.

Have the listener collect `{kind, line, col, message}` and write the list, sorted by
`(line, col, kind)` as `schemas/projection.schema.json` requires. `kind` can start as one constant
per recognizer, such as `LexerError` and `ParserError`. That is enough for accept/reject; map to the
reference's names later through `diagnosticMappings`. The projection document's `schemaVersion`
(2) is unaffected, because `diagnostics` is already a required field.

It is independent of every other step: no grammar change, no map change. That is why it comes
before the structural work.

### 4. Emit labelled-alternative names

The real depth problem is not the contract.
`antlr4/src/main/kotlin/io/github/wstein/flix/antlr/cli/Projection.kt:69` uses

```kotlin
val kind = ruleNames[ctx.ruleIndex]
```

`ctx.ruleIndex` is identical across every labelled alternative of a rule, so all 49 labelled
alternatives of `expr` collapse back to the string `expr`. The grammar already carries **62 labelled
alternatives** (`# ApplyExpr`, `# AddExpr`, `# ConsExpr`, `# MatchExpr`, …): 49 on `expr`, 11 on
`type`, 2 on `pattern`.

Those two rules are the top of this repository's `unmapped` list — `expr` 39, `type` 21, **60 of 112
stops** (`build/flix-spec-report.json`, schemaVersion 6, `oracle_conformance.unmapped` and
`nodesUnmapped`). The fix is to emit the labelled alternative's name, but **not** by taking the
class name unconditionally. `ctx::class.simpleName!!.removeSuffix("Context")` also renames every
*unlabelled* rule — `BlockContext` becomes `Block` — and every key in
`conformance/projection-map.json` (`block`, `defDeclaration`, all 41 `mappings`, plus `ignored` and
`flatten`) is the camelCase rule name. Every existing mapping would stop matching at once.

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

### 5. Add the `Operator` and `ArgumentList` nodes

Two missing nodes keep three of the largest canonical kinds unreachable. Do this together with (4),
not after — on its own, (4) turns some kind divergences into arity divergences.

- `Expr.Binary` has canonical arity **3** (lhs, `Operator`, rhs). `Projection.kt` emits only
  `ParserRuleContext` children, and `expr ( PLUS | MINUS ) expr` gives the operator no context, so a
  labelled `AddExpr` renders with 2 children.

  **Do not extract a shared `binaryOp` rule.** `expr` gets its precedence from the *order* of its
  alternatives (`FlixParser.g4:355-395`). A single `expr binaryOp expr` alternative collapses every
  level into one. Putting `binaryOp` into every level instead makes each level accept every
  operator, and the rule becomes ambiguous.

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
  `Expr.InvokeSuperConstructor`, `Expr.InvokeSuperMethod`) before reusing one rule for all five.
  Where the elements differ (`argument` vs `expr`), a shared rule changes what those three sites
  accept unless it is parameterised. Because the rule is new, `fixtures/snapshots/`,
  `docs/SYNTAX.md` and `docs/RAILROAD.md` change with it.

This also settles the open question in `projection-map.json`'s `notes.expr`, which records that
mapping `expr` to `Expr.Binary` *"drops agreement 76 -> 40, which is the signature of a guess that
is often wrong"*. It is not a bad guess — it is a **missing node**.

### 6. Lex tight `::` and parse package paths

`grammars/FlixLexer.g4` must distinguish tight `::` from spaced `::`, and the parser must produce a
node mapping to `UsesOrImports.Package` for `use flixball::Game.Board` and
`use flixball::{Game, Board}`. It will not fall out of the existing rule: `COLON_COLON : '::' ;`
(`FlixLexer.g4:166`) is a plain literal.

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
  `COLON_COLON`, and they must accept `COLON_COLON_TIGHT` too. This is not a superset
  concession: the reference parser itself accepts tight cons with no diagnostic. Otherwise every
  tight cons such as `x::xs` becomes a parse error. This is the same trap as the "bites quietly"
  note above, pointed at this repository.
- **`use a::b` does not parse today at all.** `useClause` (`:26`) is `USE qname …` and `qname`
  (`:291`) joins segments only with `dot`. The package form needs a new alternative that produces a
  node mapped to `UsesOrImports.Package`.
- **The package is a single segment.** A package is one lowercase name plus the tight separator,
  and it prefixes either a qualified name or a braced list. The braced list follows `::` directly,
  with no `.` before `{`:

  ```
  use flixball::Game.Board     Use[use, Package[Ident, ::], QName[Game . Board]]
  use flixball::{Game, Board}  Use[use, Package[Ident, ::], UseMany[{ Game , Board }]]
  use a::b::C                  UnexpectedToken: only one package segment
  use flixball :: Game.Board   Use[use, Package[Ident, ::], QName] + Malformed: "Write '::' without whitespace"
  ```

  A shape that matches: `usePackage : nameLowercase ( COLON_COLON_TIGHT | COLON_COLON ) ;` and
  `useClause : USE usePackage? ( qname ( dot useMany )? | useMany ) ;`, where `useMany` is the
  braced list extracted from the current `useClause`.

  **Accept the spaced separator in the grammar, and report it after parsing.** Flix keeps the
  `UsesOrImports.Package` node for `use flixball :: Game.Board` and attaches a `Malformed`
  diagnostic to it. Following the compatibility policy, this grammar should build the same node and
  report the same error, not throw a syntax error that loses the node.

  There is no validation pass to report it from yet: `cli/Main.kt` reports only ANTLR's own syntax
  errors. The smallest honest home is a post-parse check shared by `Main.kt` and `Projection.kt`: a
  `usePackage` whose separator is `COLON_COLON` yields `{kind: "Malformed", line, col, message}`.
  It is then emitted beside the syntax errors in step 3's `diagnostics` list, so
  `diagnostic_conformance` agrees with Flix on accept/reject for this input, and on kind and line
  too once `diagnosticMappings` is in place. `use a::b::C` has no node to keep (Flix reports
  `UnexpectedToken`), so an ordinary syntax error is the right result there.
- **The corpus barely exercises it.** On the v0.76.0 corpus, 154 files use spaced `::`. Tight `::`
  appears in 8 files, and all but one occurrence is inside a string literal. The one in code is a
  tight *cons*, `List.point(42::Nil)` (`main/test/ca/uwaterloo/flix/library/TestList.flix:537`). It
  is the only corpus line that catches a broken `ConsExpr`, and nothing in the corpus exercises the
  package path. Add fixtures to `fixtures/positive/` and `fixtures/negative/` and lexer tests;
  `FlixLexerTest.kt:216-219` covers only spaced `::`.
- **Tight `:::` has no variant of its own.** `Nil:::Nil` and `Nil ::: Nil` both lex as
  `ColonColonColon`, so `COLON_COLON_COLON` (`FlixLexer.g4:165`) stays as it is.
- **"Tight" means no whitespace on either side,** the same rule `classifyArrow()` applies:
  `42 ::Nil` lexes as spaced `ColonColon`.

All of the above was observed by running the v0.77.0 release jar (sha256 `20007d79…`, the digest
in flix-spec's `pin.json`) on single-construct probes through flix-spec's
`./gradlew :tools:project:extract`, which drives `Lexer` and `Parser2` directly.

### 7. Update `CLAUDE.md`

`CLAUDE.md` is the agent-facing record of the grammar's authority and its traps, and it is stale in
three places once this lands:

- `CLAUDE.md:38`: "Reference: `flix/flix@318bb51` (Flix 0.75.1)". Move it to v0.77.0
  (`4a5b60a`), and re-check the line citations in the "Source of truth" table against that revision.
  `docs/DESIGN-DEBATE.md:5` cites the same pin, but it is a historical record and stays.
- `CLAUDE.md:7`: the parse-rate headline. Restate it against the new corpus pin, and say whether the
  figure includes the excluded truncated file (see "What changed in Flix" above).
- `CLAUDE.md:93` and `:143`: "`->` and `.` are whitespace-sensitive" gains `::`. Add a trap entry in
  the same style, saying that tight `::` is `COLON_COLON_TIGHT` (package separator) and spaced `::`
  is cons, and that the parser accepts both in cons position. The "`:` has no `GenericOperator`
  fallback" entry lists `::` as merely reserved; point it at the new entry.

## Two guards worth adding while you are here

Neither is required by the release. Both close gaps this migration exposed.

### Emit only diagnostics the lexer or `Parser2` would raise

flix-spec's pipeline stops after `Parser2`: `ProjectionExtractor` collects
`lexerErrors ++ parserErrors` and nothing else, and `docs/CONFORMANCE.md` calls `Weeder2` errors
"out of scope by construction, not a gap".

So `diagnostic_conformance` compares against a **parse-phase-only** set. A spaced `::` reported as
`Malformed` is fine, because `Parser2` raises it. But every validation-level check you later write
into the projection output — duplicate modifiers, arity rules, anything `Weeder2` would own — adds a
diagnostic the canonical side does not have, and breaks `kind`/`line` agreement on exactly the
negative fixtures the lane is there to measure.

Tag each check with the phase that owns it: parse-phase diagnostics go into the projection,
validation-only diagnostics go to your CLI and stay out of it.

### Assert the vocabulary digests, not just the pin commit

`law` and `lawful` stopped being keywords at Flix v0.75.2 and went stale here without anyone
noticing, because a commit SHA moving tells you *that* the vocabulary changed, never *what*
changed — and nothing compared the names.

Record `treeKindDigest` and `tokenKindDigest` from `pin.json` alongside the pin you already track,
and fail on a mismatch. It costs two fields and forces a review at the next vocabulary change
instead of after it.

Two cheap follow-ons, now that `ast/retired.json` exists:

- assert that nothing in your keyword or token table matches a `Keyword*` entry in
  `ast/retired.json` — that pins the `law`/`lawful` class of staleness as a regression test;
- remember the digest cannot see an existing kind's *extension* being re-partitioned. It caught
  `ColonColonTight` only because a **new name** appeared. When a name is added, ask what it took
  from; the answer belongs in a fixture.
