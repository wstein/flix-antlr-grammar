# Issue #40 triage checklist

Tracking: [#40](https://github.com/wstein/flix-antlr-grammar/issues/40).
Source: `conformance/remaining-divergences.json` (was 42, now **35** identities; 168 fixtures).
Order: positive *kind* → positive *arity* → negative recovery.

## 1. Positive `kind` — grammar / projection (this PR)

- [x] `expressions__intrinsic-and-static-region.json` · `Expr.Static` (StaticPrimary before NamePrimary)
- [x] `type-kind-ascription.json` · `Type.Ascribe` (`type COLON kind` / AscribeType)
- [x] `types__effect-annotation-binds-to-the-arrow-result.json` · fold `\\ ef` into ArrowType in Projection.kt

Also cleared with those fixes (bonus): constrained-def ParameterList arity (2), effect-annotation Root/Def arity (2).

## 2. Positive `arity` — follow-up PR

- [ ] `lexical__holes.json` · Decl.Def / Expr.Tuple (2 identities) — still open

## 3. Already accepted

- [x] `identifier__reserved-val.json` (2×) · reserved-`val` recovery

## 4. Negative recovery — decide later (keep vs grammar sync)

31 → **31** still open (unchanged by this PR), including:

- [ ] `declarations__package-path-separator-must-be-tight.json` (2×)
- [ ] `declarations__trait-and-instance-with-an-operator-signature.json` (4×)
- [ ] match/ematch missing-body fixtures
- [ ] `lexical__numeric-literal-errors.json` (7×)
- [ ] other negatives listed in `remaining-divergences.json`

_Sum after this PR: 0 open positive kind · 2 positive arity · 2 accepted · 31 negative-review = 35._
