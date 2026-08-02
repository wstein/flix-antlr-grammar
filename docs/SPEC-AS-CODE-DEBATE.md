# Debate: is this grammar a specification?

Prompted by `tmp/Formal-specifications-as-code.md`, a research note arguing that executable
formal specifications are "mathematically precise", "low ambiguity", and admit "automated
formal verification" — and observing that Flix has no standalone executable spec.

The question put to the room: **does `grammars/*.g4` already function as an executable formal
specification of Flix syntax, and if not, what would make it one?**

Same ground rules as `DESIGN-DEBATE.md`. Every claim carries a citation into the reference
compiler (`flix/flix@318bb51`, Flix 0.75.1) or into a measurement taken during this session.
Proposals rated **1–5** (5 = adopt, 1 = reject).

## Participants

| | Role | Bias declared up front |
| --- | --- | --- |
| **Dana** | Compiler engineer, reads `Parser2.scala` daily | Fidelity to the reference implementation, including its warts |
| **Rafael** | IDE tooling, consumes this from the JetBrains plugin | Error recovery and latency beat theoretical purity |
| **Ingrid** | Parsing theory, ANTLR internals | ALL(*) prediction cost is a real budget |
| **Mo** | Maintainer of `tree-sitter-flix` | Has already paid for most of these mistakes once |
| **Priya** | Release and QA | If it is not gated in CI it will regress by Friday |
| **Sven** | Formal methods; has shipped a Maude semantics | Wants a real spec; suspicious of "the parser is the spec" |

## Measurements taken during this session

All figures from `flix/flix@318bb51`, 873 `.flix` files, using the committed grammar. (The CI
gate walks a 692-file subtree of the same commit; rates agree to within 0.05 pp.)

Provenance, since it became a topic: `318bb51` is on `origin/master` of
**github.com/flix/flix**, and its tree SHA is `294b9ac53cd0d74f7d7092006009a6b6806b0f57`. The
oracle is upstream. No fork is in the measurement path.

| Measurement | Result |
| --- | --- |
| Parse rate (no syntax error raised) | **836 / 873 = 95.76%** |
| Parser rules never matched | **0 of 83** |
| Labeled alternatives never matched | **0 of 141** (3 unmatched classes are abstract base contexts) |
| `LL_EXACT_AMBIG_DETECTION` reports | **154,309** across **801 / 873** files |
| Prediction cost, SLL vs LL | **3.6 s vs 46.0 s** — 12.8×, identical results |
| Expression-position `qname` nodes whose shape contradicts `Parser2.scala` | **1,243** |
| `FieldOrMethodExpr` nodes actually produced | **318** |

---

## Q1. Is the source document itself trustworthy?

**Sven** opens by refusing the premise. "Before we discuss specifications, look at what we were
handed. Five citations. I had every one checked. Four resolve. One title is silently truncated
— arXiv 2602.00180 is *'…in the Age of AI Coding **Assistants**'*, and the note drops the last
word, which is exactly the drift that breaks a lookup. Two of the five are 2008 and 2012/2014
work presented undated alongside 2026 preprints. And the headline quote, from arXiv 2607.04232,
is the paper's *motivating premise*, not its finding — the same abstract goes on to say that
generated executable assertions are 'syntactically invalid, trivial, or too weak to reject
behavior-changing faults.' The note cites the setup and drops the result."

**Priya**: "So a document advocating machine-checkable specifications is itself an unchecked
natural-language artifact with a broken citation. That is not a cheap shot, it is the whole
argument in miniature. Nothing in that file is executable, so nothing in it was checked."

**Dana** is less impressed. "The citations being sloppy doesn't make the thesis wrong. Argue the
thesis."

**Sven**: "I am about to. My point is narrower: the note's own failure mode — prose asserting a
fact that no mechanism verifies — is the exact failure mode I expect to find in this repo. Let's
go look."

---

## Q2. Is `grammars/*.g4` a specification, or a second implementation?

**Sven**: "It is a second implementation. A specification is written *before* or *independently
of* the implementation, and derives its authority from being agreed. This grammar was
transliterated *from* `Parser2.scala` — `CLAUDE.md` says so explicitly, it is the stated
method. A transliteration cannot be the spec, because when the two disagree, `Parser2.scala`
wins by definition. That is the definition of an implementation, not a spec."

**Dana** pushes back hard. "That's an academic distinction with no operational content. Flix has
*no* prose grammar. `Parser2.scala` is 3,500 lines of hand-written recursive descent with
`nth(1)` lookahead scattered through it. `docs/SYNTAX.md` is 1,197 lines, generated from the
grammar by `tools/gen-docs.mjs`, and CI fails if it drifts (`.github/workflows/ci.yml:69-73`).
That is more of a syntax specification than the Flix project has ever published. I'd rate its
usefulness a 5 even if you refuse to call it a spec."

**Mo**: "Dana's right that it's useful and Sven's right about the direction of authority. But
there is a third thing neither of you said. A specification has to be *checkable against the
thing it specifies*. Ask the question that decides it: what in this repo compares the grammar to
`Parser2.scala` automatically?"

**Priya**: "Nothing. I had the repo audited. No script, test, or CI step reads `Lexer.scala`,
`Parser2.scala`, `TokenKind.scala`, or `SyntaxTree.scala`. `fixtures/keywords.txt` — the closest
thing to an extracted fact — is **hand-transcribed**, and the comment in
`FlixKeywordTableTest.kt:18` says so, calling it 'a deliberate manual step'. The test then
asserts that the grammar matches the file and that the file has 84 entries. Both sides of that
equality are ours. If the transcription is wrong, or Flix adds a keyword, the test stays green."

**Ingrid**: "And `docs/TREEKIND-MAP.md` — the artifact that maps our rule names onto Flix's
`TreeKind`s, which is the only place the *semantic* correspondence is written down — is 62
hand-written lines that nothing reads, nothing generates, and no CI path checks. It isn't even
in the doc-drift list; that list covers `SYNTAX.md` and `RAILROAD.md` only."

**Consensus on Q2**: it is a second implementation with a generated syntax reference attached.
That is genuinely valuable and should not be oversold. The gap that matters is not
philosophical — it is that *no mechanism* relates it to the authority it claims to follow.

---

## Q3. What does "95.81%" actually certify?

**Priya** states the mechanism plainly. "`CorpusCoverageTest.kt:50-55` counts files where
`parser.getNumberOfSyntaxErrors() == 0`. The `ParseResult` in `Main.kt:13-18` doesn't even carry
the tree. So the gate certifies exactly one proposition: *no syntax error was raised*. It says
nothing about the tree."

**Rafael**: "That's a known limitation of every parse-rate gate. tree-sitter-flix used the same
metric to get to 100%. Are you claiming it's worthless?"

**Mo**: "I'm the one who ran that project, so let me answer. It is not worthless — it took us
from 77% to 100% and it found real bugs. But I also shipped a rule, `invoke_method`, that
matched **zero times** across 890 files because `apply_expression(get_field(...))` shadowed it.
The parse rate was green the entire time. `CLAUDE.md:169-171` records that story as a warning to
this project. So my question for the room is whether this project actually avoided it."

**Ingrid**: "I checked reachability, since nobody had. `CLAUDE.md` flags it as a concern and
`DEFECTS.md:68-70` records that the old `testRuleReachability` was a fake — it asserted a walker
visited more than ten nodes. I instrumented the real thing over 873 files. **All 83 parser rules
match. All 141 labeled alternatives match.** Zero dead rules."

**Rafael**: "So we did avoid it. Good."

**Ingrid**: "No. That is the trap, and I want to be precise about why. Rule-level and even
alternative-level reachability are the *wrong granularity*. `FieldOrMethodExpr` is reachable —
it fires 318 times — because it fires whenever the receiver isn't a bare name, as in
`f(x).g()`. Reachability is satisfied by the residue. The bug lives in the cases the *other*
alternative steals."

**Dana**: "Show me."

**Ingrid**: "`qname` is `name ( dot name )*` — greedy, unbounded (`FlixParser.g4:262-264`).
`Parser2.scala` is not. `nameAllowQualified` (`Parser2.scala:738`) carries a `tail` parameter
defaulting to `NAME_LOWERCASE`, and the loop at `:759` sets `isTail` and **stops as soon as it
consumes a lowercase segment**. So the reference parses:

- `List.length(xs)` → qname stops after `length` (first lowercase) → `Expr.Apply` over the
  qualified name. Our grammar agrees, by luck.
- `x.foo(1)` → qname stops at `x` immediately → postfix loop at `Parser2.scala:1553` sees
  `Dot` + `NameLowercase` + `(` → **`Expr.InvokeMethod`**. Our grammar produces
  `Apply(qname(x.foo), 1)`. Wrong shape.
- `x.foo` → reference gives **`Expr.GetField`**. Ours gives `qname(x.foo)`. Wrong shape.
- `Foo.Bar.baz.qux(1)` → reference stops the qname after `baz`, then
  `InvokeMethod(Foo.Bar.baz, qux)`. Ours swallows all four segments."

**Priya**: "How many, in the corpus?"

**Ingrid**: "**1,243** expression-position `qname` nodes have a non-final lowercase segment, so
every one of them contradicts the reference. Against 318 `FieldOrMethodExpr` nodes actually
produced. We emit the correct shape roughly **20%** of the time for Java field and method
access. A further 612 sit in `use`/`import` position, where it splits: `iimport` passes
`tail = Set()` (`Parser2.scala:911`) so fully-qualified is *correct* there, but `use` takes the
default tail (`:885`), so the `use` share is wrong too."

**Mo**, dryly: "So the exact defect I documented as a warning is live in this grammar, at scale,
and every gate is green. Parse rate 95.81%, 141/141 alternatives reachable, 49 tests passing."

**Dana** concedes the point but narrows it. "Accepted, and it's a real bug — I'll open it as D12.
But note what kind of bug it is. It is not a *specification* problem. It's a transliteration
error in one rule. Sven will want to conclude we need a formal semantics. We need a `tail`
predicate on `qname`."

**Sven**: "I'll take that trade, but you're understating the lesson. You have three independent
green signals — parse rate, reachability, unit tests — and all three are structurally incapable
of seeing this class of defect, because all three ask 'did something match?' and none asks 'did
the *right* thing match?'. That is not one bug. That is a missing oracle."

**Consensus on Q3**: the parse rate certifies well-formedness, not shape. Reachability, now
measured and green, is too coarse to substitute. The project has no shape oracle, and the first
place anyone looked for one, it found 1,243 divergences.

---

## Q4. The 154,309 ambiguity reports

**Ingrid**: "`CLAUDE.md:160` instructs us to measure with `PredictionMode.LL_EXACT_AMBIG_DETECTION`.
Nobody ever did — grep for `PredictionMode` across the repo returns nothing. I ran it.
**154,309 exact-ambiguity reports across 801 of 873 files.**"

**Rafael**: "That number is alarming and I don't believe it means what it looks like."

**Ingrid**: "You're right, and I want to disarm it before someone quotes it. Most of it is
benign. `expr` (42,464), `primaryType` (26,469), `primaryPattern` (25,803) are left-recursive
rules, and ANTLR's precedence-climbing transformation reports ambiguity as a known artifact
while still resolving correctly — `1 + 2 * 3` reports `EXACT expr alts={1,2}` on `* 3` and
parses correctly, and `FlixGrammarPropertiesTest.kt:176` proves the whole precedence table. Raw
count: not a finding."

**Dana**: "Then which part is?"

**Ingrid**: "The non-left-recursive rules, where first-alternative-wins actually decides shape:

| Rule | Reports | Diagnosis |
| --- | --- | --- |
| `qname` | 38,187 | The `tail` defect above. Real. |
| `statement` | 20,264 | `expr ( SEMI expr )* SEMI?` — the loop and the trailing `SEMI?` both claim a final `;`. |
| `predicateAtom` / `fixpointClause` / `predicateBody` | 922 | Datalog, the predicted cost centre. |
| `enumCase` | 71 | `COMMA? CASE? nameUppercase … COMMA?` — leading *and* trailing optional comma. |

`enum E { case A, case B }` yields `(enumCase case A ,) (enumCase case B)`. The comma is
absorbed as the first case's *trailing* comma. Which case owns a separator is arbitrary, and the
grammar lets both claim it. That's a smell, not a crash — but it will surface the moment anyone
writes a formatter or a refactoring against these trees."

**Rafael** has a different concern. "The number I care about is 3.6 s versus 46.0 s. That's SLL
against LL over the same 873 files, **identical results** — 836 parsed both ways. ANTLR's Java
default is LL. We are paying 12.8× for prediction that changed no outcome on the entire
reference corpus. In an IDE that is ~53 ms/file versus ~4 ms/file on the keystroke path."

**Ingrid**: "Two-stage parsing: SLL first, on error re-parse with LL. Standard ANTLR practice.
It is strictly safe — SLL either agrees with LL or fails, never silently differs."

**Rafael**: "Then it's free, and I want it."

---

## Q5. Should we adopt an executable formal semantics?

This is where the room actually split.

**Sven** proposes it. "The note's real suggestion is a mechanized semantics — K, Ott, Redex,
Maude. Flix has none. If we want a *specification*, that's the shape of it."

**Dana**, flatly: "Rated 1. Reject. This project specifies syntax. A semantics for Flix means
formalizing Boolean-unification-based effect polymorphism, region inference, trait resolution,
and Datalog fixpoint semantics with lattices. That is a multi-year PhD, not a phase. And it
would not have caught the `qname` bug — the bug is *syntactic*."

**Ingrid** agrees on scope but not on principle. "There's a middle path Sven is entitled to. Not
a semantics — an *independent* syntax spec. The value of a spec isn't formality, it's
independence. The reason `qname` is wrong is that one person transliterated one rule and no
second source disagreed. A semantics is the wrong lever. A second oracle is the right one."

**Mo**: "And the second oracle already exists and is free. `Parser2.scala` runs. It emits a
`SyntaxTree` with `TreeKind` labels. We have `docs/TREEKIND-MAP.md` mapping our rules onto
those kinds. Run both over the corpus, project both trees through the map, diff. That is
differential testing against the reference, and it would have printed 1,243 disagreements on
day one."

**Sven** accepts the redirection but registers a caveat. "Fine — and I'll rate that a 5. But
note what you're conceding: the oracle is the implementation. You have no independent
specification and you're choosing not to build one. That's defensible on cost. It is not
defensible to then describe the result as a formal specification of Flix, which is what the
README implies. Say what it is: a conformant re-implementation, continuously diffed against the
reference."

**Dana**: "I can sign that sentence."

**Priya**: "I want it *in* the README, because right now the README says something else and is
also numerically wrong — badge and body both claim 90.90% while the gate enforces 95.81%.
Five separate prose sites still carry the old number: `CLAUDE.md:7`, `DEFECTS.md:6`,
`DEFECTS.md:90` ('the 63 files that still fail' — it's 29), `README.md:9`, `README.md:17`.
Commit `ed24185` moved the ratchet and updated none of them. `MEMBER-BODY-DEBATE.md` is the only
doc that tracked it."

**Sven**: "Six sources of truth for one integer, five of them stale. In a repository whose
subject is specification-as-code. I don't need to make the argument; the repo made it."

**Priya**: "There's a worse one. `ci.yml:53-57` checks out `flix/flix` at
`debf7df0fdd63f2b76b7a539ebbff5243070ca6e`. `corpus-baseline.json:3` names `318bb51`. The only
guard is `test "$COUNT" -ge 690` — a lower bound on file count, not an identity check. The
enforced gate and the documented gate are measured over different trees."

**Mo**: "Check whether `debf7df` is even upstream."

**Priya**, after checking: "It is not. `git cat-file` on `github.com/flix/flix` does not know
that object. `debf7df` exists only on `wstein/flix-fork`, and the corpus fetch resolves at all
because a tag was pushed there to keep CI green. So the gate's oracle is currently a repository
this project controls."

**Sven**: "Then the gate is not measuring conformance to Flix. It is measuring conformance to
*us*. That is the failure mode of every rotted specification I cited, arrived at by
infrastructure rather than by prose — and it is strictly worse than the stale README numbers,
because a stale number is visibly wrong while this one is invisibly circular."

**Dana**: "It's almost certainly a convenience that outlived its reason — a fork ref that was
handy when the pin was chosen. But Sven's characterisation is right and the rule should be
absolute: **the oracle is `github.com/flix/flix`, never a fork.** A corpus we can rewrite is not
evidence."

**Ingrid**: "For the record, the measurements in this session are unaffected. `318bb51` is on
upstream `master` and the tree SHA is `294b9ac53cd0d74f7d7092006009a6b6806b0f57` — identical
whichever remote you fetch it from, which is the property that makes SHA pinning worth doing in
the first place. The numbers stand. The CI configuration is what has to change."

---

## Q6. Who verifies the spec?

**Sven** raises the classical objection against himself. "Fair warning that my own field has a
long record here. Z, VDM, and B all have industrial retrospectives where the spec was written,
was never maintained, and diverged from the code it specified — at which point it is worse than
no spec, because people trust it. The one thing that reliably prevents that is *executability
plus a diff in CI*. Not formality. Mechanism."

**Priya**: "Which is my entire position stated in formal-methods vocabulary. If it isn't gated
in CI it will regress by Friday. `TREEKIND-MAP.md` is already there — 62 lines, hand-maintained,
nothing reads it. It is a Z spec with markdown syntax."

**Mo**: "Then the fix is to make it load-bearing. Stop treating it as documentation and make it
the input to the differential harness. A mapping that is *executed* cannot rot silently."

**Rafael**: "That's the strongest idea in this session. It converts our one unverified artifact
into the mechanism that verifies everything else."

---

## Addendum — external evidence, and two ratings it changed

Prior-art research returned after the session. It moved two proposals and hardened a third. The
researcher also opened by retracting three of its own earlier claims, including an invented
figure it had attributed to a Lämmel & Zaytsev table — noted here because it is the third time
today that an unexecuted assertion about specifications turned out to be wrong.

**Sven's best datapoint, granted in full.** WebAssembly is the one language standard where
formal semantics sit in the *normative* text, soundness theorems are stated in the standard, and
the document is now **generated** from a DSL. Wasm SpecTec (Youn et al., PACMPL 8/PLDI art. 210,
2024; DOI 10.1145/3656440) was adopted by the W3C Wasm CG on **11 March 2025 by 32–0 with zero
opposition**, and Wasm 3.0 shipped on **17 September 2025** as the first standard produced with
it. The DSL source for Wasm 3.0 is **9,351 lines** against a 16,966-line reST document that is
now splice templates carrying **700 `${…}` directives**. It caught **23 enumerated spec bugs** —
13 historical, 10 in in-flight 3.0 proposals, all confirmed by the authors.

**Dana's rebuttal, also granted.** The same briefing is a graveyard for *retrofits*:

- **JSCert** (POPL 2014, ~3,000 lines Coq) — scoped to ES5.1, passed 1,796 / 2,782 core Test262
  tests, and explicitly *did not specify the parser*. Last substantive commit **2016-12-16**.
  Never adopted by TC39.
- **K Framework's language semantics** — `javascript-semantics` last pushed 2016, `java-semantics`
  2021, `c-semantics` 2022. K itself thrives, as a blockchain verification engine.
- **Ott** — last release 0.34 (2024-12-30). **PLT Redex** — no releases; research and teaching.
- **Ferrocene's FLS** — the one specification carrying actual regulatory force (TÜV SÜD, 29 Oct
  2023, first qualification of a Rust compiler) is **hand-written structured prose**, its
  structure borrowed from the Ada Reference Manual. Upstreamed to `rust-lang/fls` on
  26 March 2025. Not mechanized at all.

**Ingrid**: "So the positive case is real and it is also narrow. Wasm succeeded because the
semantics were in the standard *from day one*, written by a small group, for a language designed
around them. Every attempt to retrofit formality onto an existing language either died, stayed a
research artifact, or won by abandoning mechanization. Flix is a retrofit. **P10 stays rejected,
and now it is rejected with citations rather than with intuition.**"

**Sven** keeps one point, and it is the sharpest thing in the briefing. Watt's Isabelle
mechanization of Wasm (CPP 2018; ~11,000 lines Isabelle for ~700 lines of spec — a **16:1
proof-to-spec ratio**) found three defects, at least two soundness-breaking. In the same paper's
§7, **Csmith/Binaryen differential fuzzing against commercial engines found nothing.** "Fuzzing
found no errors. The proofs found unsoundness. Whoever argues that testing subsumes proof should
read that section."

**Priya** takes the counterweight: Klein et al., *"Run Your Research: On the Effectiveness of
Lightweight Mechanization"* (POPL 2012) mechanized nine ICFP 2009 papers in Redex and found
**mistakes in all nine — including one whose essential result had been verified in Coq.** "The
trust problem moves. It does not vanish. That is my whole position and it is now a citation."

### What changed

**P8 (grammar fuzzing) is upgraded from Defer to Adopt-after-P2.** Bendrissou, Cadar &
Donaldson, *"Grammar Mutation for Testing Input Parsers"* (TOSEM 34(4):116, April 2025;
DOI 10.1145/3708517) is the exact technique, and it found **three bugs in `antlr/grammars-v4`
itself** (lua, xml, url — all fixed), plus **CVE-2024-38428** in Wget. Their two directions map
onto us directly: *accept-invalid* (we over-approximate) and *reject-valid* (we under-approximate).
We have **five** negative fixtures, so the reject-valid direction is essentially unmeasured.

**A new bar to be honest about.** Lämmel & Verhoef's grammar-level ladder (SP&E 31(15), 2001)
defines **L4 as "a tested level 3 grammar… tested [in] the range of millions of lines of code."**
Their VS COBOL II recovery hit **~2 million LOC in two weeks, ~70 transformations, US$25,000**.
Our corpus is **873 files**. By the published bar this grammar is L3 with a corpus gate, not L4.
`CLAUDE.md` should say so rather than implying the corpus is large.

**One clean pass, recorded because most of this session has not been.** grammars-v4 issue
**#2405** documents grammars whose start rule is not `EOF`-anchored, so a "successful" parse may
consume only a prefix — the precise silent failure a parse-rate gate cannot see. Checked:
`compilationUnit : usesOrImports* declaration* EOF` (`FlixParser.g4:16-18`). Verified
empirically — `def f(): Int32 = 1  @@@ ###` reports 1 syntax error, the clean version reports 0.
**Not vulnerable.**

**Gaps the researcher flagged and did not fill from memory** (do not treat as absent evidence):
the ECMAScript/JLS/C++/Ada standards survey, and the entire Z/VDM/B industrial-retrospective
literature. Kahrs's *"Mistakes and Ambiguities in the Definition of Standard ML"* is named as the
highest-value missing item — a fully formal definition of a real language that itself carried a
published errata list.

---

## Proposals and ratings

5 = adopt now, 1 = reject. Consensus column records what was agreed.

| # | Proposal | Dana | Rafael | Ingrid | Mo | Priya | Sven | Consensus |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P1 | **Fix `qname` to honour the `tail` rule** — stop the qualified name after the first `NameLowercase`; let the postfix chain produce `GetField`/`InvokeMethod`. Exempt `import` (`tail = Set()`). | 5 | 5 | 5 | 5 | 5 | 5 | **Adopt.** File as D12. Blocking. |
| P2 | **Differential tree oracle**: run `Parser2.scala` and the ANTLR parser over the corpus, project both through `TREEKIND-MAP.md`, diff shapes. Gate on the diff count with a ratchet. | 4 | 5 | 5 | 5 | 5 | 5 | **Adopt.** The single highest-value change. Start with a shape-only diff (kind sequence), not a full tree equality. |
| P3 | **Make `TREEKIND-MAP.md` generated/executable** and add it to the doc-drift CI path. | 5 | 4 | 5 | 5 | 5 | 5 | **Adopt**, as P2's input. |
| P4 | **Two-stage SLL→LL parsing** in the CLI and the gate. 12.8× on measured evidence, identical results. | 4 | 5 | 5 | 4 | 4 | 3 | **Adopt.** Keep LL fallback; never SLL-only. |
| P5 | **Ambiguity budget in CI**: record per-rule `LL_EXACT_AMBIG_DETECTION` counts for non-left-recursive rules only, fail on increase. | 3 | 3 | 5 | 4 | 5 | 4 | **Adopt with scope limit.** Left-recursive rules excluded or the number is noise. |
| P6 | **Extract `keywords.txt` from `Lexer.scala`** automatically at corpus-pin time instead of hand-transcribing. | 5 | 3 | 4 | 5 | 5 | 5 | **Adopt.** Cheap; removes the last hand-copied fact. |
| P7 | **Pin the oracle to upstream by SHA.** CI must fetch `github.com/flix/flix` at the *same* SHA `corpus-baseline.json` names, verified by tree SHA rather than a file count. Never a fork. | 5 | 5 | 5 | 5 | 5 | 5 | **Adopt. Blocking, alongside P1.** Currently CI fetches a fork-only ref. |
| P7b | **Fix the doc-drift**: one source of truth for the parse rate across `CLAUDE.md`, `DEFECTS.md`, `README.md`. | 4 | 4 | 4 | 4 | 5 | 5 | **Adopt.** Embarrassing and trivial. |
| P8 | **Grammar mutation + differential testing** (Bendrissou et al., TOSEM 2025): mutate the `.g4`, generate with Grammarinator, diff against the reference parser in *both* directions — accept-invalid and reject-valid. | 4 | 3 | 5 | 5 | 4 | 5 | **Adopt after P2** (upgraded from Defer on the TOSEM evidence). Reject-valid is currently covered by 5 negative fixtures. |
| P9 | **JVM↔TS differential test** (blocked on D11; TS parser isn't even generated in CI). | 3 | 4 | 3 | 3 | 4 | 3 | **Defer** to D11 completion. |
| P10 | **Mechanized formal semantics** (K / Ott / Redex / Maude). | 1 | 1 | 2 | 2 | 1 | 3 | **Reject** for this project. Out of scope; would not have caught D12. Confirmed post-session: every comparable *retrofit* is dead or dormant (JSCert 2016, K's language semantics 2016–2022, Redex, Ott). |
| P11 | **Own the `enumCase` / `statement` separator ambiguity** — move separators to the parent list rule. | 4 | 3 | 5 | 4 | 3 | 4 | **Adopt**, low priority, after P2 (P2 will tell us if it matters). |

---

## Where the room landed

Agreed, unanimously:

1. **This is not a specification, and calling it one is the actual risk.** It is a conformant
   re-implementation of `Parser2.scala`. Its authority is borrowed, not intrinsic. Every
   claim it makes should be phrased as conformance, not as definition.
2. **"Parses" is not "parses correctly", and the project has been measuring only the first.**
   Parse rate, rule reachability, and alternative reachability are all green while 1,243 nodes
   in the reference corpus carry a shape that contradicts the reference parser.
3. **Reachability is a genuinely good result that must not be oversold.** 141/141 alternatives
   matched is better than tree-sitter-flix managed, and it still could not see D12, because the
   defect hides in the residue rather than in a dead rule.
4. **The fix for a missing specification is a second oracle, not more formality.** The reference
   compiler is executable, emits labelled trees, and is already pinned. Diff against it.
5. **Unexecuted documentation rots, and this repo proves it twice** — five stale parse-rate
   claims, and a corpus pin that disagrees between CI and the baseline file.
6. **The oracle must be upstream `github.com/flix/flix`, pinned by SHA, never a fork.** A
   corpus the project can rewrite cannot falsify the project. CI currently violates this: it
   fetches `debf7df`, which exists on no upstream branch. This outranks every proposal below
   P1, because it determines whether any other measurement means anything.

Dissent recorded: **Sven** maintains that P2 leaves the project without any independent
specification and that "conformance to the reference" cannot detect a defect the reference
itself has. Nobody disputed this; the room judged it out of scope rather than wrong.

## Likely future developments

- **P2 will move the parse rate.** The 37 current failures cluster into recognizable families:
  annotations in statement position (`@Tailrec` inside blocks, 6 files), restrictable variants
  (`enum E[…]`, 4 files), record extension/restriction with a row variable (`{+a = 1 | r}`,
  3 files), named-argument tuples (`(a = 1)`, 3 files), Java `new` object expressions (D7,
  4 files), `_` in region/field position (3 files), and `;` inside Datalog fixpoint blocks
  (5 files). None require new theory.
- **The shape diff will initially be dominated by D12.** Expect the first run to report on the
  order of 1,500 disagreements and then fall off a cliff once P1 lands. Ratchet from the
  post-P1 number, not the first one.
- **If antlr-ng stays blocked (D11), the TS target should be declared experimental in the
  README** rather than carried as an equal customer. CI currently type-checks only
  `FlixLexerBase.ts`; the generated parser is gitignored and never built there.

## Concrete next actions

| | Action | Owner suggested | Gate |
| --- | --- | --- | --- |
| 1 | Add a `tail`-aware `qname` (stop after first `NameLowercase`; `import` exempt); re-measure | Dana | Corpus rate must not regress; `FieldOrMethodExpr` count should rise from 318 toward ~1,560 |
| 2 | Regenerate `TREEKIND-MAP.md` from an annotated grammar and add it to `ci.yml`'s drift check | Ingrid | `git diff --exit-code` |
| 3 | Build the shape-diff harness against `Parser2.scala`'s `SyntaxTree`; commit a baseline | Mo | New ratcheting gate, same pattern as `corpus-baseline.json` |
| 4 | Two-stage SLL→LL in `Main.kt` | Rafael | Corpus rate unchanged; record wall-clock |
| 5 | Per-rule ambiguity budget, left-recursive rules excluded | Ingrid | Fail on increase |
| 6 | Script `keywords.txt` extraction from `Lexer.scala` | Dana | Regenerate at every corpus-pin bump |
| 7 | Repoint `ci.yml` to `github.com/flix/flix@318bb51`, verify by tree SHA `294b9ac53cd0d74f7d7092006009a6b6806b0f57`, drop the `-ge 690` file-count guard | Priya | CI fails if the tree SHA differs |
| 8 | Single-source the parse rate across `CLAUDE.md`, `DEFECTS.md`, `README.md` | Priya | CI check, not prose |

Do not raise the corpus baseline by hand. Do not describe this artifact as a formal
specification of Flix until something in CI compares it to one.
