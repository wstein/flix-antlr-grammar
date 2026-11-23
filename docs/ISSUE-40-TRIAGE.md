# Issue #40 triage checklist

Tracking: [#40](https://github.com/wstein/flix-antlr-grammar/issues/40).
Source: `conformance/remaining-divergences.json` (**33** identities after steps 1–3; 168 fixtures).
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

## 4. Negative recovery — recommendations (analysis only; no grammar edits yet)

Criteria:

- **change / remove** when ANTLR *accepts* input the reference rejects (language supersets), or when the lexer *falls back* instead of emitting an error token.
- **keep** when both sides reject and only the recovered tree shape differs (ANTLR `DefaultErrorStrategy` vs Parser2 `isRecoverIn*`), or when the production is already correct and the residual is recovery aesthetics.

| Fixture cluster | IDs | Verdict | Rationale |
| --- | ---: | --- | --- |
| `identifier__reserved-val` | 2 | **keep** | Already accepted; both reject `def val()`, shapes differ. |
| `declarations__package-path-separator-must-be-tight` | 2 | **keep** | `COLON_COLON_TIGHT` rule is correct; spaced `::` recovery is Package vs plain `USE qname`. |
| `declarations__trait-and-instance-with-an-operator-signature` | 4 | **change** / **remove** | ANTLR *accepts* `def <=>` via `definitionName → genericOperator → ANGLED_EQUAL`. Reference only allows `NameLowercase` / `NameMath` / `GenericOperator`. **Remove** reserved operator tokens from `definitionName` (keep them on `genericOperator` for sections/infix). |
| match / ematch missing-body (4 fixtures) | 4 | **keep** | Both reject; reference builds `Expr.Match`/`EMatch`, ANTLR leaves unlabeled `expr`. Documented in projection-map notes. |
| `expressions__malformed-tuple-reaches-enclosing-brace` | 1 | **keep** | Both reject; Tuple vs Binary recovery shape. |
| `expressions__trailing-separator-in-argument-list` | 1 | **keep** | `argumentList` correctly forbids trailing comma; Apply vs Ident recovery. |
| `lexical__bang-caret-dollar-have-no-standalone-meaning` | 2 | **keep** | No standalone primary for `!`/`^`/`$`; both reject; body/recovery shape differs. |
| `lexical__numeric-literal-errors` | 7 | **change** | Lexer greedily takes a valid prefix (`1`, `0x1`, `1.5`) and leaves junk names; reference errors the literal. Sync lexer error-token behaviour (do not treat as a successful body expr). |
| `lexical__static-lowercase-is-reserved-but-unused` | 1 | **remove** | **Remove** `STATIC_LOWER` from `StaticPrimary`. Only `Static` is `Expr.Static`; bare `static` must stay a reserved-unused reject. |
| `lexical__unterminated-block-comment` | 1 | **change** | Failed `BLOCK_COMMENT` falls back to `/` `*` tokens → bogus `MultExpr`. Consume unterminated `/*…` as a comment/error token like the reference. |
| `lexical__unterminated-regex` | 1 | **change** | Failed `REGEX_LITERAL` falls back to `regex` + string. Emit an unterminated-regex error token instead. |
| `operator-error` | 1 | **keep** | Reference inserts an error operator (`1 2` → Binary); ANTLR keeps first literal. Parser2-specific recovery. |
| `trailing-dot` | 1 | **keep** | Both reject; QName-with-dot vs Ident after `DOT_WS` at EOF. |
| `types__effect-annotation-wrong-slash` | 1 | **keep** | Effect annotation correctly requires `BACKSLASH` only; recovery drops `= ???` vs keeps Hole. |
| `types__missing-schema-tail` / `-schema-row-tail` / `-extensible-tail` | 3 | **keep** | `( BAR type )?` correctly requires a type after `\|`; recovery empties the alias type vs Schema-with-hole. |
| `unclosed-paren` | 1 | **keep** | Both reject; Apply vs Ident recovery. |

### Counts

| Verdict | Clusters | Identities |
| --- | ---: | ---: |
| **keep** forever (recovery shape only) | 11 clusters | 17 |
| **keep** (already accepted reserved-val) | 1 | 2 |
| **change** (lexer sync / narrow naming) | 3 | 12 |
| **remove** (`STATIC_LOWER` from `StaticPrimary`) | 1 | 1 |

Of the 31 still-open negatives: **14** proposed fix (change/remove), **17** proposed keep forever.

_Sum after steps 1–3: 0 open positive kind · 0 positive arity · 2 accepted · 31 negative-review = 33._
