# Remediation debate: fixing D1–D6

Held after the corpus gate reported **10.26% (71/692)**. Same five participants as
[DESIGN-DEBATE.md](DESIGN-DEBATE.md). The question is no longer *what to build* but *how to
repair something that was built from the wrong source*.

Proposals rated **1–5**.

---

## Q1. Repair incrementally, or rewrite both grammars?

**Ingrid** opens for a rewrite: "Look at what the measurement actually says. The keyword table
was not derived from `Lexer.scala`; it contains `namespace` and `class`, which Flix replaced
with `mod` and `trait` years ago. That is not a typo, it is a grammar written from memory of a
different language. The parser has `try … with`, `do op(…)`, `ref` and `deref` — four
constructs that do not exist. If the *source* was wrong, every rule derived from it is suspect.
Patching leaves the wrong assumptions in place where nobody will look again."

**Mo** disagrees on evidence: "I got to 100% incrementally, in measurable steps: 77, 89, 96,
99.8, 100. Each step told me what the next problem was. A rewrite gives you one number at the
end and no gradient. And the parts that *are* right — the operator run, the whitespace-sensitive
arrow, the interpolation modes — came from the first debate and are correct. Throwing them away
to prove a point is waste."

**Priya** kills the rewrite on process grounds: "A rewrite is one enormous commit that cannot be
bisected and cannot be reviewed. The ratchet is worthless if the number goes 10 → 0 → 85 across
one change; I lose the ability to attribute a regression to anything. Incremental, with a
committed number per phase."

**Ingrid** concedes the mechanism but extracts a condition: "Then the rule is that no phase may
be committed without moving the corpus number, and the number goes in the commit message. If a
phase does not move it, we did not understand the problem."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Full rewrite | 3 | 2 | 4 | 1 | 1 |
| Incremental, number per phase | 4 | 5 | 4 | 5 | 5 |

**Consensus: incremental.** Every phase states its measured before/after rate.

---

## Q2. Which comes first, lexer or parser?

**Dana**: "Lexer, and it is not a preference. While `trait` lexes as an ordinary identifier,
every parser error involving a trait declaration is noise. You cannot read parser diagnostics
through a broken token stream — we already saw declaration errors resyncing into the Datalog
rule and reporting `expecting {DOT, DOT_WS, '(', ':-'}` on an `enum`. That diagnostic is
garbage, and fixing the parser rule that produced it would be fixing the wrong thing."

**Ingrid** raises the coupling: "Agreed on order, but they cannot be separate *commits*.
`FlixParser.g4` references `NAMESPACE`, `CLASS`, `DO`, `REF`, `DEREF` and `OVERRIDE`. Delete
those lexer rules and the parser stops compiling, because `tokenVocab` resolution fails. A phase
that does not build is not a phase."

**Rafael**: "So phase one is 'correct the token vocabulary and drag the parser along far enough
to compile'. Not elegant, but it is the smallest change that builds and measures."

**Consensus, unanimous**: one phase covering the lexer plus the minimum parser edits to keep the
build green. Deeper parser work follows separately.

---

## Q3. How do we stop the keyword table drifting again?

This is the most productive exchange of the session.

**Priya**: "D1 and D2 are not really grammar bugs. They are a *synchronization* bug: a table
that must match `Lexer.scala:49-139` and has nothing enforcing it. Fix the values and it drifts
again on the next Flix release. `flix-textmate` solved this with `extract-lexicon.mjs`, which
scrapes the compiler."

**Dana** objects to copying that: "Their extractor makes the build depend on a Scala checkout
being present at a known path. Ours would too. That is a real cost — CI would need to clone the
compiler to build a grammar."

**Rafael** proposes the middle: "Commit the extracted list as data, and *test* against it rather
than generate from it. A test that asserts the lexer's keyword set equals a committed
`keywords.txt` costs nothing, needs no Scala, and fails loudly the moment someone adds a keyword
that is not on the list — or forgets one that is."

**Ingrid** sharpens it: "It has to be bidirectional to catch both defects. D1 was *missing*
keywords, D2 was *extra* ones. A one-directional check catches half."

**Mo**: "And regenerate `keywords.txt` from `Lexer.scala` as a documented manual step at each
Flix bump, not on every build."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Build-time extractor | 2 | 2 | 3 | 3 | 4 |
| Committed list + bidirectional test | 5 | 5 | 5 | 5 | 5 |

**Consensus, unanimous**: `fixtures/keywords.txt` holding the 84 keywords, plus a test asserting
set equality in both directions. This is the single change that prevents recurrence.

---

## Q4. Do we fix the precedence table now or later?

**Dana** flags a defect the corpus gate cannot see: "The expression rule collapses `and` and
`or` into one `LogicalExpr` alternative. They are levels 2 and 1 — different. So `a or b and c`
currently parses as `(a or b) and c` instead of `a or (b and c)`. It parses *fine*. The corpus
gate will never catch it, because the file still parses. It is silently wrong output."

**Mo** connects it to his own scar: "Same shape as my dead `invoke_method` rule. Parse rate said
100% while a rule matched zero times. A number that only counts success cannot see wrong
structure."

**Priya**: "Then precedence needs its own test, not corpus coverage. Assert tree shape for one
expression per level."

**Ingrid** adds what else is wrong: "`<+>` is lumped into the comparison alternative; it is level
8, above `+`. `instanceof` at level 0 is absent entirely. And `MOD` appears as a multiplicative
operator — `mod` is the *module* keyword in Flix, not a modulo operator. That last one is
another artefact of writing from memory of another language."

**Consensus**: fix precedence in the expression phase, guarded by shape assertions rather than
parse-success assertions. Rated 5/5.

---

## Q5. Should the top-level `declaration` rule keep `datalogConstraint`?

**Ingrid**: "This is D6 and it is the cause of the misleading diagnostics. A Datalog constraint
is not a top-level declaration in Flix — constraints live inside `#{ … }`. Having it as a
declaration alternative means any declaration ANTLR cannot match gets retried as a constraint,
and the reported expected-set is the constraint's."

**Dana** confirms against the reference: "`Parser2.scala:948-960` dispatches declarations on
eight keywords. There is no constraint alternative. `#{ … }` is an *expression*."

**Rafael**: "Removing it also improves recovery, because the failure is then reported against
the declaration keyword the user actually typed."

**Consensus, unanimous**: remove it. Constraints stay reachable through the fixpoint expression.

---

## Consensus summary

| # | Decision | Vote |
| --- | --- | --- |
| 1 | Incremental repair; every phase reports its measured rate | 4.6 |
| 2 | Token vocabulary first, with the minimum parser edits to keep the build green | unanimous |
| 3 | `fixtures/keywords.txt` + bidirectional set-equality test | unanimous |
| 4 | Fix precedence with tree-shape assertions, not parse-success | unanimous |
| 5 | Drop `datalogConstraint` from top-level declarations | unanimous |

### Recorded dissent

**Ingrid**, on Q1: incremental repair leaves rules whose derivation was never verified against
`Parser2.scala`. The corpus gate proves files parse, not that they parse *correctly*. Q4 already
produced one example of silently wrong structure that no amount of corpus coverage would catch.
A structural audit against `Parser2.scala` remains owed after the rate is respectable.

---

## Phase plan

| Phase | Scope | Gate |
| --- | --- | --- |
| A | Keyword table, holes, literals; parser adapted to compile | rate up; keyword test green |
| B | Declarations: trait/instance/eff/redef, enum flexibility, drop top-level Datalog | rate up |
| C | Expressions: precedence table, holes, missing forms | rate up; shape tests green |
| D | Types: effect sets, schemas, records, casts | rate up |
| E | Documentation and baseline ratchet | docs accurate |

## Future developments

- **The structural audit Ingrid is owed.** Once the rate is respectable, walk `Parser2.scala`
  rule by rule and diff against the grammar. Corpus coverage cannot substitute for it.
- **Rule-reachability gate.** Still not implemented; the placeholder was removed as misleading.
  Count rule invocations over the corpus and fail on unreachable non-allowlisted rules.
- **Differential token check** against `tree-sitter-flix`, which already has a token-boundary
  oracle.
