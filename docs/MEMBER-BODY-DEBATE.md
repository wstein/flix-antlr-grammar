# Debate: local definitions swallowing member bodies

The last 29 corpus failures share one cause. `newBody` and `handlerRule` end in
`EQUAL statement`, and because `LocalDefExpr` is an alternative of `expr`, that trailing
statement greedily consumes the *next* `def` as a nested local definition:

```flix
new Comparator[a] {
    def compare(_this: Comparator[a], t: a, u: a): Int32 = ...   // body
    def equals(...) = ...                                         // swallowed
}
```

The member loop then meets `}` where it expected another member. The reference guards against
exactly this at `Parser2.scala:1863-1869`, which removes `def` from the argument FIRST set.

Ratings are 1–5.

---

## Evidence gathered before the debate

**Dana** ran the obvious experiment first. `Parser2.scala:1520-1524` shows `let` and local `def`
call `statement(rhsIsOptional = false)` — the reference *requires* a following `;` and
expression. So the grammar was changed to match:

```antlr
| LET pattern ( COLON typeAndEffect )? EQUAL expr SEMI statement  # LetExpr
| DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL expr SEMI statement
```

**Result: 663/692 before, 663/692 after.** No change.

**Dana**: "That kills my hypothesis and it should kill everyone's intuition about this bug. The
local def is not matching because the grammar permits a dangling one — it matches because
ALL(*) prefers continuing the `statement` subrule over exiting the enclosing `newBody*` loop.
Requiring a continuation just means the swallowed `def` needs a `;` after it, which inside a
member body it usually has. We are arguing about the wrong lever."

The change was reverted: it is more faithful to the reference but has no measured benefit, and
an unmeasured change to the expression rule is how the type precedence table got inverted.

---

## Q1. Semantic predicate on `LocalDefExpr`

**Proposal**: a parser field, set while parsing a member body, guarding the alternative:
`{!inMemberBody}? DEF definitionName ...`.

**Ingrid** rejects it on ALL(*) semantics: "Predicates in a left-recursive rule are evaluated
*during prediction*, not during parsing. The parser may be simulating a path far ahead of where
the field was last assigned, so `inMemberBody` will be read at a moment when it does not
describe the position being predicted. This is the classic mistake — the predicate appears to
work on the cases you test and misfires on nesting. And `expr` here is left-recursive, which is
the worst case for it."

**Rafael** adds a consumer objection: "It also makes the parser stateful, so it cannot be
reused across files without a reset, and any IDE that parses incrementally gets a field whose
value depends on parse order rather than on the text."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Semantic predicate | 2 | 1 | 1 | 2 | 1 |

**Rejected.**

## Q2. A separate non-local-def statement rule

**Proposal**: `memberStatement` mirroring `statement` but without `LocalDefExpr`.

**Mo**: "In practice this means duplicating `expr`, because the exclusion has to hold at the
*head* of the statement only, and ANTLR has no way to say 'this rule minus one alternative'.
You would maintain two expression grammars that must not drift. I did the equivalent once and
the copies diverged within a month."

**Ingrid** partially disagrees: "The duplication is smaller than Mo suggests. Only the head
position matters, so `memberStatement : (expr excluding a leading DEF) (SEMI expr)*`. But ANTLR
still cannot express it without restating the alternatives, and the restated copy is exactly
what the snapshot tests will not cover."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Duplicate statement rule | 2 | 2 | 3 | 1 | 2 |

**Rejected on maintenance grounds**, with Ingrid's reservation noted.

## Q3. Reframe: the member loop, not the expression rule

**Rafael** proposes attacking the other side: "Everyone is trying to stop `statement` from
eating the `def`. Stop the *loop* from needing it instead. `newBody*` is greedy; give the
member rule a right edge ANTLR can see — require members to be comma- or newline-separated, or
make the body `newBody (COMMA? newBody)*` so the prediction has a delimiter to anchor on."

**Dana** objects on fidelity: "Anonymous-class members in Flix have no separator.
`Parser2.scala:2918` parses them with `Separation.None`. Inventing one means rejecting valid
programs, which is a worse failure than the one we have."

**Priya**: "And it fails silently in the direction we cannot detect — the corpus rate would go
*down*, which we would notice, but any file that still parsed would parse differently."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Require member separators | 1 | 3 | 2 | 2 | 1 |

**Rejected.**

## Q4. Reorder so the member alternative is tried first

**Ingrid**: "There is a cheaper reading. `newBody` currently starts with `DEF ...`, and so does
`LocalDefExpr`. Both are viable at the same position, and ALL(*) resolves the ambiguity by
alternative order *within a rule* — but these are in different rules, so what actually decides
is whether the enclosing loop is entered. The lever is the loop's exit branch, and ANTLR
resolves loop exit against loop continue by preferring **continue**. That is not configurable."

**Mo**: "Which is why tree-sitter needed a declared conflict here and resolved it with GLR. We
do not have that escape hatch."

**Consensus**: not viable, but this is the clearest statement of *why* the other options fail,
and it should be recorded so the next person does not retry Q1 and Q3.

## Q5. Do nothing, and say so

**Priya**: "Twenty-nine files out of 692 is 4%. Every option above is either unsound, a
maintenance burden, or changes the accepted language. The honest move is to document the
limitation with a minimal reproducer and leave the grammar correct-but-incomplete rather than
complete-but-wrong."

**Dana** agrees with a condition: "Provided the reproducer is committed as a *negative* fixture
with the current error position pinned. Then if someone finds a sound fix, the fixture flips to
positive and the corpus rate moves. Right now the failure is invisible in the test suite — it
only shows up as a number in a JSON file."

**Rafael**: "Add that it is a known-failing case in the corpus gate too, so the 29 are
enumerated rather than aggregate."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Document with pinned reproducer | 4 | 4 | 5 | 5 | 5 |

**Adopted.**

---

## Consensus

| # | Decision | Vote |
| --- | --- | --- |
| 1 | Semantic predicate on `LocalDefExpr` — rejected, unsound under ALL(*) prediction | 1.4 |
| 2 | Duplicated statement rule — rejected, two expression grammars will drift | 2.0 |
| 3 | Invented member separators — rejected, changes the accepted language | 1.8 |
| 4 | Alternative reordering — not viable; loop-continue beats loop-exit and is not configurable | — |
| 5 | Pin a minimal reproducer, enumerate the known failures, leave the grammar sound | 4.6 |

### Recorded dissent

**Ingrid**, on Q2: the duplication objection is a maintenance argument, not a soundness one. A
`memberStatement` rule *is* expressible and *is* correct; it is merely tedious. If the 29 files
ever become load-bearing for a consumer, Q2 is the option to revisit, not Q1.

### Key points

- The greedy-`statement` framing is wrong. Requiring a continuation on local definitions —
  the change that matches the reference most closely — moved the corpus rate by zero files.
- The decisive mechanism is ANTLR resolving *loop continue* over *loop exit*, which no
  alternative ordering or precedence annotation can override.
- tree-sitter solved the same collision with a declared GLR conflict. ALL(*) has no equivalent.

### Future developments

- Revisit Q2 if a consumer needs anonymous-class bodies. Scope it to the statement head only.
- Watch upstream ANTLR for any control over loop-exit preference; that would make Q4 viable and
  is the only change that would make this cheap.
- If the Flix reference ever gives anonymous-class members a separator, Q3 becomes correct
  rather than a fabrication.

### Concrete improvements

1. Commit `fixtures/negative/06_member_body_local_def.flix` with the error position pinned, so
   the limitation is visible in the suite rather than only in a JSON number.
2. Record the failing file list alongside `corpus-baseline.json` so the 29 are enumerated and a
   fix can be attributed to specific files.
3. Note in `docs/DEFECTS.md` D7 that the cause is understood and the options are exhausted.
