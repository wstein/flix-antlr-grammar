# Issue #40 triage checklist

Tracking: [#40](https://github.com/wstein/flix-antlr-grammar/issues/40).
Source: `conformance/remaining-divergences.json` (**25** identities after accept-invalid fixes; 168 fixtures).
Order: positive *kind* → positive *arity* → negative recovery.

## 1. Positive `kind` — grammar / projection (PR #41)

- [x] `expressions__intrinsic-and-static-region.json` · `Expr.Static` (StaticPrimary before NamePrimary)
- [x] `type-kind-ascription.json` · `Type.Ascribe` (one optional kind ascription at each type level)
- [x] `types__effect-annotation-binds-to-the-arrow-result.json` · attach the trailing effect to the last unparenthesized arrow in Projection.kt

Also cleared with those fixes (bonus): constrained-def ParameterList arity (2), effect-annotation Root/Def arity (2).

## 2. Positive `arity` — PR #42

- [x] `lexical__holes.json` · Decl.Def / Expr.Tuple (2 identities) — project an `Ident` spanning the full named or variable hole token.

## 3. Already accepted

- [x] `identifier__reserved-val.json` (2×) · reserved-`val` recovery — **keep** (owner accepted 2026-11-15)

## 4. Negative recovery — recommendations (PR #44) then implemented (PR #45)

Compare the reject/accept result separately from the recovered tree.

### Fixed (accept-invalid / lexer sync)

- [x] **fix acceptance** · `STATIC_LOWER` removed from `StaticPrimary` — `lexical__static-lowercase-is-reserved-but-unused`
- [x] **fix acceptance** · `definitionName` = Parser2 `NAME_FUNCTION` (lowercase / math / `userDefinedOperator`); reserved ops stay on `genericOperator`; uppercase def names rejected (E6629)
- [x] **align lexer** · `ERR_NUMBER` via `acceptNumber` / `acceptHexNumber` — `lexical__numeric-literal-errors`
- [x] **align lexer** · `UNTERMINATED_BLOCK_COMMENT` via `acceptBlockComment` — `lexical__unterminated-block-comment`
- [x] **align lexer** · `UNTERMINATED_REGEX` — `lexical__unterminated-regex`

Cleared: static-lowercase, unterminated comment/regex, and six of seven per-def numeric arities.
Residual recovery shapes for `<=>` signatures (4) and numeric forest/Root[0] (2) remain as tolerate-recovery.

### Tolerate recovery (keep forever)

| Fixture cluster | IDs | Rationale |
| --- | ---: | --- |
| `declarations__package-path-separator-must-be-tight` | 2 | Both reject spaced `::`; recovered path differs. |
| `declarations__trait-and-instance-with-an-operator-signature` | 4 | Now correctly *rejects* `<=>`; recovered signature shape still differs. |
| match / ematch missing-body (4 fixtures) | 4 | Match/EMatch vs unlabeled `expr`. |
| `expressions__malformed-tuple-reaches-enclosing-brace` | 1 | Tuple vs binary recovery. |
| `expressions__trailing-separator-in-argument-list` | 1 | Apply vs Ident recovery. |
| `lexical__bang-caret-dollar-have-no-standalone-meaning` | 2 | Both reject; body recovery differs. |
| `lexical__numeric-literal-errors` residual | 2 | Forest 7→1 / Root[0] after `ERR_NUMBER`. |
| `operator-error` | 1 | Inserted-operator recovery is Parser2-specific. |
| `trailing-dot` | 1 | QName vs Ident recovery. |
| `types__effect-annotation-wrong-slash` | 1 | `BACKSLASH` only is correct; body recovery differs. |
| `types__missing-schema-tail` / `-schema-row-tail` / `-extensible-tail` | 3 | Both require a type after `\|`. |
| `unclosed-paren` | 1 | Apply vs Ident recovery. |
| `identifier__reserved-val` | 2 | Already accepted. |

### Gate

- Structural identities: **33 → 25**
- Canonical depth floor: **67 → 66**
- Legacy common-path: **24 → 14** divergences; depth floor **62 → 61**
