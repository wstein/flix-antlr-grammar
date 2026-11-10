# Flix 0.79 conformance

The grammar targets `flix/flix@36d923ca325dc466b28c63419667e6026a80f920`
(v0.79.0). Syntax decisions follow the pinned `Lexer.scala`, `Parser2.scala`,
`TokenKind.scala` and `SyntaxTree.scala`, compared with the former 0.75.1 sources.
The shared grammar serves both the JVM ANTLR4 and TypeScript antlr-ng customers.

The migration removes `law`/`lawful` from the 82-keyword inventory, distinguishes
whitespace-sensitive `::`, parses package-qualified uses, and accepts full type tails
in schema, schema-row and extensible types. The corpus also exposed a pre-existing
comprehension omission: Parser2 accepts `pattern = expression` without `let`.

## Reproduce

```bash
scripts/flix-spec-conformance.sh
python3 -m unittest discover -s scripts -p test_conformance_binding.py
```

The gate downloads these immutable timestamped artifacts from the public flix-spec
Maven repository, or accepts local JARs through `FLIX_SPEC_DATA` and
`FLIX_SPEC_RUNNER`. Every JAR is verified before extraction or execution.
`0.79.0-20261109.125843-3` is the published version used here; this is not a claim
that stable `0.79.0` is available. No mutable snapshot alias or Gradle plugin is resolved.

| Artifact | SHA-256 |
| --- | --- |
| Data | `f58c987974501e22f6671511a4454458fb669511c140788d27a560ac09b03922` |
| Runner | `989d4117462ddd7b3a5d8de57e21e0ca5c82f121e9e5d2fdc239c7ccbfa0f1ed` |

The data pin, tree/token vocabulary digests, schema, fixture revision, fixture count,
canonical node denominator, divergence counts and remaining fixture/path/expected/
actual/reason identities are binding. The full revision is
`7e017bc8d4850fd293c6c97ddb3e4e5c4cb8cee0ed691d2750d461eccc236dd2`.
The fixed 135-path revision is
`2028e3a14c3dac6ca9327d2350dcd9bdc284e27880e5e0b7f71184170b372175`.
An optional extracted bundle argument is also compared byte for byte with all runtime
files in the SHA-checked JAR, including source text, pin, vocabulary, profiles and corpus
metadata; missing and extra runtime paths fail. The report checks remain additional.

Python's standard library is used only for artifact ZIP/SHA-256/JSON binding and
process orchestration. The comparison remains in the published runner; it is not ported.
Processes receive argument arrays and literal environment values, without shell evaluation.

## Two depth definitions

The old report recorded 88% over 136 fixtures: `818 / (818 + 113)`, rounded.
The old shell gate enforced only 75 structural and zero recovery divergences; it
had no depth gate. The new walk-depth floor of 88% preserves and strengthens that
historical measured value; it does not preserve an already-enforced check.

The historical formula is in flix-spec v0.75.2 commit
`02d8a2e7c2a9e2dac19d4c0b8513b356dc30c8c1`,
`tools/project/src/main/scala/flix/spec/Conformance.scala:138–140`:
`nodesCompared / (nodesCompared + nodesUnmapped)`. Unmapped subtrees stop the walk,
so their descendants are absent from this denominator. The old schema-6 comparison
also stopped listing/comparing after 20 divergences per fixture. Historical report
counts therefore cannot be compared directly with current schema-9 counts.

Schema 9's additional canonical-depth gate divides `nodesCompared` by
`nodesExpected`: all canonical rule nodes in the reference's published normalized
expectation, before this consumer's `elide` policy. Token leaves are not rule nodes.
Both percentages round half up, matching the runner; neither is language coverage.

The following paired runs use the same published 0.79 data, schema-9 runner and
fixture identities on both sides. The original grammar is commit `5a1853430f05d226f2c46d18bbdbe8d3e55d8955`;
its scratch map removes only the retired canonical `Decl.Law` and `CommentList` targets.

| Fixed set / parser | Compared / canonical nodes | Canonical depth | Unmapped | Walk depth | Divergences |
| --- | --- | --- | --- | --- | --- |
| 166 / original | 1001 / 2412 | 42% | 127 | 89% | 121 |
| 166 / syntax migration | 1207 / 2412 | 50% | 135 | 90% | 65 |
| 135 common paths / original | 792 / 2010 | 39% | 111 | 88% | 77 |
| 135 common paths / syntax migration | 892 / 2010 | 44% | 122 | 88% | 50 |
| 166 / structure port | 1588 / 2412 | 66% | 103 | 94% | 48 |
| 135 common paths / structure port | 1234 / 2010 | 61% | 98 | 93% | 31 |

Both fixed sets now gate independently: structural limits 48 and 31, canonical-depth
floors 66% and 61%, and walk-depth floors 94% and 93%. Denominators 2412 and 2010
are bound separately, preventing denominator shrinkage from masquerading as improvement.
`conformance/legacy-fixture-paths.json` records all 135 surviving paths and the one
retired trait-law fixture out of the original 136, including its replacement.
`remaining-divergences.json` records every remaining full-set divergence identity.

## Projection and claim boundaries

Labelled expression alternatives retain call roles, argument lists and synthetic
operator groups, including math-name and backtick operators. Interpolated strings
retain embedded expressions; debug interpolation keeps its outer `Expr.Expr` wrapper. The projection retains
concrete literal/Unit nodes, type applications, tuples, operators, constants,
type variables, record rows and schema forms,
enum payloads, declaration roles and grouped uses/imports. Comments are reconstructed
from native token positions. Leading docs attach to the outermost documentable node
starting at the next token; same-line trailing and orphan docs remain plain comments.
Enum commas remain outside the documentable case boundary. Multiline, CRLF and EOF
positions follow native token positions and code points.

The inherited canonical `elide` classes remain `AnnotationList`, `Expr.Expr`,
`Expr.Statement`, `ModifierList`, `Pattern.Pattern`, `QName` and
`UsesOrImports.UseOrImportList`; empty/singular occurrences collapse, branching ones
remain. Removed `CommentList` is retired vocabulary, not a newly hidden comment class.
The raw `useList` follows the same empty/singular policy as its canonical counterpart.
Type applications are retained; simple types use a separate transparent alternative.
Type/effect grouping rules flatten without adding a canonical wrapper. Empty braces
are an effect set; empty/field/tail-only parentheses are record rows, following
`Parser2.scala`'s dispatch. The full pre-elision canonical denominator remains binding.

Recovery agreement remains N/A: no recovery marker vocabulary is declared.
Diagnostic agreement remains N/A: projected diagnostic arrays remain empty.
Document shape passes. Token vocabulary, token accounting, token positions and
lexical correctness remain unmeasured because the adapter emits no terminal token
leaves or hidden whitespace. Native comment groups do not establish those capabilities.

## Corpus evidence

The active corpus is exactly the pinned compiler's `main/`: 717 regular `.flix`
files, including one deliberately truncated resiliency fixture. Both targets move
from 714/716 to 716/716 measured files; the existing 100% baseline is not lowered.
The `dev.flix` directory is not counted as a source file.

The former CI corpus `debf7df0fdd63f2b76b7a539ebbff5243070ca6e` is retained as
a diagnostic comparison, with the same truncation exclusion. Its old law syntax is
not a separate compatibility feature. Both targets move from 690/691 to 672/691;
the existing `IfElseCoverage.flix` failure is unchanged, and all 18 newly rejected
files contain removed law syntax. The digest-verified official 0.79 compiler's
Lexer/Parser2 pipeline also rejects all 18 before weeding or type checking.
This historical compatibility change does not lower an active corpus gate.
