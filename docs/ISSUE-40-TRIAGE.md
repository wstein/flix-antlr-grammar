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

Compare the reject/accept result separately from the recovered tree. The
numbers below count divergence identities, not individual diagnostics.

- **fix acceptance** when ANTLR accepts input that Flix rejects.
- **align lexer** when both reject the input but ANTLR splits a malformed lexeme that Flix diagnoses at the lexer stage.
- **tolerate recovery** when both reject the input and the difference is in the recovered tree. These are recommendations for #40, separate from the already accepted reserved-`val` cases.

| Fixture cluster | IDs | Verdict | Rationale |
| --- | ---: | --- | --- |
| `identifier__reserved-val` | 2 | **accepted** | Both reject `def val()`; the differing recovered trees were already accepted. |
| `declarations__package-path-separator-must-be-tight` | 2 | **tolerate recovery** | Both reject spaced `::`; only the recovered path differs. |
| `declarations__trait-and-instance-with-an-operator-signature` | 4 | **fix acceptance** | ANTLR accepts `def <=>` because `definitionName` includes `genericOperator`, which accepts `ANGLED_EQUAL`. Match Parser2's `NAME_FUNCTION` in signatures and definitions: `NameLowercase`, `NameMath`, and `GenericOperator`. Check other call sites of the shared ANTLR rule before narrowing it. |
| match / ematch missing-body (4 fixtures) | 4 | **tolerate recovery** | Both reject; reference builds `Expr.Match`/`EMatch`, ANTLR leaves unlabeled `expr`. Documented in projection-map notes. |
| `expressions__malformed-tuple-reaches-enclosing-brace` | 1 | **tolerate recovery** | Both reject; tuple and binary recovery shapes differ. |
| `expressions__trailing-separator-in-argument-list` | 1 | **tolerate recovery** | Both reject the trailing comma; apply and identifier recovery shapes differ. |
| `lexical__bang-caret-dollar-have-no-standalone-meaning` | 2 | **tolerate recovery** | Both reject standalone `!`, `^`, and `$`; body recovery differs. |
| `lexical__numeric-literal-errors` | 7 | **align lexer** | Both reject. ANTLR splits literals such as `1_` and `0x1i99` into a valid prefix and leftover tokens; Flix reports a malformed literal. Match the Flix error-token boundary. |
| `lexical__static-lowercase-is-reserved-but-unused` | 1 | **fix acceptance** | Remove `STATIC_LOWER` from `StaticPrimary` and keep it reserved in the lexer. Flix accepts uppercase `Static` as an expression. |
| `lexical__unterminated-block-comment` | 1 | **align lexer** | Both reject. ANTLR falls back to `SLASH` and `STAR`; Flix reports `UnterminatedBlockComment`. Preserve rejection when aligning the error token. |
| `lexical__unterminated-regex` | 1 | **align lexer** | Both reject. ANTLR splits the input into a `regex` name and an unterminated string; Flix reports `UnterminatedRegex`. Match the Flix error-token boundary. |
| `operator-error` | 1 | **tolerate recovery** | ANTLR rejects `1 2`. The oracle records an `OperatorError` in the raw tree with no diagnostic, and Flix 0.80.0 `check` reports E9061 (missing operator). |
| `trailing-dot` | 1 | **tolerate recovery** | Both reject; name recovery differs. |
| `types__effect-annotation-wrong-slash` | 1 | **tolerate recovery** | Both reject `/` as the effect separator; Flix can continue parsing the body using that separator as a recovery landmark. |
| `types__missing-schema-tail` / `-schema-row-tail` / `-extensible-tail` | 3 | **tolerate recovery** | Both require a type after `\|`; malformed tails recover differently. |
| `unclosed-paren` | 1 | **tolerate recovery** | Both reject; apply and identifier recovery shapes differ. |

### Counts

| Verdict | Fixtures | Identities |
| --- | ---: | ---: |
| **fix acceptance** | 2 | 5 |
| **align lexer** | 3 | 9 |
| **tolerate recovery** | 15 | 17 |
| **accepted** (reserved `val`) | 1 | 2 |

Of the 31 open identities, 14 are candidates for grammar or lexer changes and
17 are candidates to tolerate. The two reserved-`val` identities are already
accepted.

_Sum after steps 1–3: 0 open positive kind · 0 positive arity · 2 accepted · 31 negative-review = 33._
