# Known defects

Findings from measuring the grammar against the real Flix corpus (the revision pinned in
`.github/workflows/ci.yml`; corpus size drifts with the local checkout, see
`fixtures/corpus-baseline.json`). Ordered by impact.

Measured parse rate: **100% of the syntactically valid corpus**, up from 10.26% when this log
was opened. The gate now excludes `test/flix/resiliency/ford-fulkerson-prefix.flix` by name --
Flix ships it truncated on purpose, to test its own recovery -- and fails if an excluded file
ever parses, so the exclusion cannot go stale unnoticed.

D1-D7, D9, D10 and D12-D14 are resolved; D15 (Flix v0.77.0 tight `::`) is open. The entry that made the rest possible
is D4: the build was green and all 38 unit tests passed while the grammar rejected nine out of
ten real Flix files, because the tests only ever exercised hand-written snippets.

Do not raise the corpus baseline by hand; let the gate ratchet it.

---

## D1 — 22 real keywords are missing from the lexer — RESOLVED

`grammars/FlixLexer.g4` defines 86 keywords, but they are not the 84 keywords Flix actually
has. Absent, despite being in `Lexer.scala:49-139`:

```
Static  Univ  checked_cast  checked_ecast  ematch  forA  forM  foreach  handler
lawful  rvadd  rvand  rvnot  rvsub  static  super  throw  trait  unchecked_cast
unsafe  xor  xvar
```

`trait`, `throw`, `foreach`, `handler`, `super`, `unsafe`, `Static` and `Univ` are core
constructs. Their absence alone accounts for a large share of the failures.

**Fix**: regenerate the keyword table from `Lexer.scala:49-139`. The authoritative list of 84
is reproduced in `CLAUDE.md`.

## D2 — 24 phantom keywords that do not exist in Flix — RESOLVED

These are defined as keywords but are ordinary identifiers in Flix 0.75.1. Any real program
using one as a name fails to parse:

```
assert  bool  built_in  chan  char  class  deref  do  float32  float64  for  get
in  int8  int16  int32  int64  namespace  op  open  override  ref  set  string
without
```

`namespace` and `class` are from a Flix version that predates `mod` and `trait`. `ref`,
`deref`, `do` and `without` were removed from the language. `bool`, `char`, `string`, `int32`
and friends were never keywords — Flix's primitive types are the *uppercase* names `Bool`,
`Char`, `String`, `Int32`, which resolve as ordinary type names.

This is the same defect class `flix-textmate` catalogues as its "phantom keywords" finding.

**Fix**: delete these rules and every parser reference to them.

## D3 — Holes are not tokenized at all — RESOLVED

`???` (`HoleAnonymous`), `?name` (`HoleNamed`) and `name?` (`HoleVariable`) have no lexer
rules and no parser alternative. `???` is Flix's standard "unimplemented" marker and appears
throughout the corpus and the standard library; `def f(): Bool = ???` does not parse.

**Fix**: add the three token rules (`Lexer.scala:153`, `:470-482`, `:509-513`) and an
expression alternative.

## D4 — The corpus gate did not test a corpus — RESOLVED

`CorpusCoverageTest.testCorpusParseRate` wrote two hand-written snippets into a temporary
directory and parsed those. It never touched the corpus, so the parse rate it reported was
meaningless and a 10% grammar showed as green.

`testRuleReachability` likewise did not measure reachability: it walked one snippet's tree and
asserted the walker visited more than ten nodes, which is true for any non-empty parse.

**Status**: fixed. The test now parses a real corpus and enforces a ratcheting baseline
committed to `fixtures/corpus-baseline.json`. It skips, rather than passes, when no corpus is
available, so an absent corpus can never read as success.

## D5 — Enum bodies and case separators are too strict — RESOLVED

`enum E0` with no body is rejected, and `enum E { case A case B }` (no comma between cases) is
rejected. `Parser2.scala:1214` defines `FIRST_ENUM_CASE = { CommentDoc, 'case', ',' }` and
deliberately accepts `case A, B, C`, `case A, case B,` and `case A case B` alike. The enum
body is also optional.

## D6 — Stray input falls through to the Datalog constraint rule — RESOLVED

Errors in declaration position resync into the Datalog constraint rule, producing
`expecting {DOT, DOT_WS, '(', ':-'}` on ordinary declarations. The diagnostics are actively
misleading. This is an alternative-ordering problem in `compilationUnit`.

## D7 — Java interop and remaining edge cases — RESOLVED

Opened at 29 failing files; closed at 687/688 (99.85%), the one remaining "failure" being
`resiliency/ford-fulkerson-prefix.flix`, an intentionally truncated negative test the corpus
script cannot distinguish from a real gap. Every fix here traces to a real position in
`Parser2.scala`, verified against the failing file before changing the grammar, not guessed
from the error message. Two recurring bug classes account for most of the count:

**The "optional-leading-group blocks an independent optional trailing part" class.** A native
shape like `LPAREN ( item ( COMMA item )* ( SEMI extra )? )? RPAREN` reads as "the whole
comma-list-plus-extra is optional", but the reference treats the list and the trailing part as
*independently* optional -- `zeroOrMore(..., optionallyWith = Some((Bar, ...)))` in
`Parser2.scala` accepts zero items *and* the trailing part together. A flat ANTLR translation
that nests the trailing part inside the same optional group as the list rejects exactly the
zero-items-plus-trailing case. Found and fixed in five places, each confirmed against its own
`Parser2.scala` function: `predicateHead`/`predicateAtom`/`predicateParam` (Datalog lattice
terms, `P(;1)`, `Nodes(; ns)` -- `Parser2.scala:3909,3928,4035`), the `{ ... }` and `( ... )`
record-row types (`{| r}`, `Parser2.scala:3740`), and the three schema-type delimiters
`#{...}`/`#(...)`/`#|...|#` (`#{ | r}`, `Parser2.scala:3796,3812,3778`). Same fix each time:
split `( list )? ( trailing )?` into two independent optional groups.

**Missing alternatives, not malformed ones.** `argument` only allowed a bare `nameLowercase` on
the left of `=`, and only call-argument lists routed through it at all -- plain
parenthesized/tuple expressions never did, so `(a = 1)` had no path to succeed regardless of the
left-hand side. `Parser2.scala:1878`'s `argument()` is `expression() (EQUAL expression)?`
uniformly for every argument position, including tuple elements (`parenOrTupleOrAscribe`,
`Parser2.scala:2110`), not a call-specific feature. Widened `argument` to `expr (EQUAL expr)?`
and routed tuple elements through it; the identical named-element gap existed at the type level
too (`(y = Int32)`, a `Type.RecordRow` sharing the plain tuple's parens,
`Parser2.scala:3675`) and got the same treatment via a new `recordFieldOrType` rule.
`newBody`'s `def new(): T = super(...)` constructor override (`Parser2.scala:2956`, dispatched
whenever `def` is immediately followed by `new`, distinct from an ordinary method and unlike it
not annotatable) had no alternative at all, nor did a leading `annotation*` on an ordinary
method (`Parser2.scala:2940`) or a local `def` inside a statement (`Parser2.scala:2252`,
`@Tailrec def loop(...) = ...;`). `ematch`'s lambda short-hand (`ematch pattern -> expr`,
`Parser2.scala`'s `extMatchExpr`) was missing the same way `match`'s already-present
`MatchLambdaExpr` alternative covers `match pattern -> expr`. `region`'s bound name required
`nameLowercase`; the reference's `NAME_VARIABLE` also allows `_` and a math name
(`Parser2.scala:2274`, `region _ { ... }`). A restrictable enum's index parameter (`Expr[_][t]`)
had no bracket group of its own before the ordinary type-parameter list
(`Parser2.scala:1170`). A use/import rename could not target or produce an operator name
(`use Op.{<>< => ><>}`) because `aliasedName`'s underlying `NAME_USE` set includes
`GenericOperator` on both sides (`Parser2.scala:690,932`), not just names. `Static` doubles as
an ordinary declaration name (`type alias Static = IO` in `Prelude.flix`) via
`Parser2.scala:696`'s `NAME_TYPE = Set(NameUppercase, KeywordStaticUppercase)`, alongside its
existing use as a type reference.

**One lexer bug, not a grammar gap**: `DEBUG_INTERPOLATOR` (`d"..."`) was declared *after*
`NAME_LOWERCASE` in `FlixLexer.g4`. For input `d"`, both match exactly the single character `d`
(`"` is not a name character), and ANTLR breaks same-length lexer ties by declaration order --
so `d"Hello"` silently lexed as the bare identifier `d` followed by an ordinary string, and the
parser failed several tokens later at the string, nowhere near the real cause. Reordering the
two rules was the entire fix; nothing else in this bucket was a lexer issue.

## D8 — The corpus gate does not run in CI — RESOLVED

The gate needs a Flix checkout and skips without one, so an absent step would silently stop
enforcing the ratchet. `ci.yml`'s `build` job checks out a pinned `flix/flix` revision to
`corpus/` and passes `-Dflix.corpus=$GITHUB_WORKSPACE/corpus/main`; the `antlr-ng target` job
does the same via `FLIX_CORPUS` (see D11). Both run the gate on every push and PR.

## D9 — `/` listed as a reserved operator in the antlr-ng target — RESOLVED

`antlr-ng/src/FlixLexerBase.ts` mapped `/` to `SLASH` in its reserved-operator table. Since
`/` is excluded from the user-operator character set, it can never appear in an operator run,
so the entry was unreachable and misleading. Removed to mirror the Java implementation.

## D10 — CI matrix was a no-op — RESOLVED

The build job pinned a `java:21-bookworm` container while running a 21/25 JDK matrix, so both
legs built on the image's JDK 21 and the JDK 25 leg proved nothing. The container is gone and
`actions/setup-java` now supplies the matrix JDK.

## D11 — The antlr-ng target cannot consume the shared grammar — RESOLVED

`grammars/` is described as canonical for both targets, but only the JVM target could generate
from it. Two independent obstacles:

1. **Embedded actions are Java syntax.** The lexer's actions and predicates call
   `isNameCharFollow()`, `enterBrace()` and `_input.LA(1)` unqualified. TypeScript requires
   `this.` on every one, and Java forbids nothing — so no single spelling satisfies both.
   ANTLR has no target-portable action syntax.
2. **antlr-ng ignores `options { superClass }` for TypeScript.** The generated lexer extends
   nothing, so every helper call is unresolved and `override` is rejected
   (`TS4112`).

**Fix**: `tools/gen-antlr-ng.mjs` (`npm run generate` in `antlr-ng/`) stages a TypeScript-adapted
copy of `grammars/*.g4` into `antlr-ng/build/grammars/` -- qualifying the shared helper calls
with `this.`, translating `setType`/`_input.` to antlr4ng's API, and rewriting Java char-literal
comparisons to code-point comparisons -- runs `antlr-ng` over the copy, then patches the
generated lexer to import and extend `FlixLexerBase`. The output is not committed
(`antlr-ng/src/generated/` is gitignored, same reasoning as `antlr4`'s generated sources) but
now type-checks cleanly and CI runs `npm run generate` before `npm run build`/`npm test`.

**Proof, not just a clean build**: `antlr-ng/test/corpus-coverage.test.ts` parses the same real
Flix corpus the JVM target's `CorpusCoverageTest.kt` does, against the same shared
`fixtures/corpus-baseline.json`, and gets the **same rate** (662/688, 96.22%) -- the two targets
are not just both green, they agree on what they accept. `antlr-ng/test/lexer-base.test.ts`
alone (parity checks on the hand-written runtime support, predating a generated parser to test
against) was not that proof.

Note also that `antlr4ng-cli` never had a 3.x release, so the declared `^3.0.0` could not
install at all; the target had therefore never been built. `FlixLexerBase.ts` did not compile
either, using the Java field `_input` instead of antlr4ng's `inputStream`, calling `reset()` on
a plain array, and reading `charIndex`. All three are fixed.

## D12 — U+FFFF cannot be lexed — RESOLVED

ANTLR's Java target reserves U+FFFF in its serialized ATN: any char-range-based rule --
`~[...]` negated sets, the `.` wildcard, U+FFFF added explicitly to `STRING_CONTENT`, all of it
-- silently fails to match the character, because the *interval encoding itself* cannot
represent that codepoint, regardless of which `CharStream` implementation is used at runtime.
This is a compile-time ATN-serialization limitation, confirmed against
[antlr/antlr4's own Unicode documentation](https://github.com/tunnelvisionlabs/antlr4/blob/master/doc/unicode.md)
and issue tracker before attempting a fix, not assumed.

**Fix**: a semantic predicate does not compile into the ATN's char-range encoding at all -- it
is arbitrary Java code evaluated against `_input.LA(1)` as a runtime `int`, which represents
0xFFFF (65535) without difficulty (EOF is -1, not 0xFFFF, so the two never collide). Added
`{ _input.LA(1) == 0xFFFF }? .` as a third alternative to `STRING_CONTENT`. Verified two ways:
the file that exercises it (`TestJson.flix`) now parses (previously the lexer got stuck in
`STRING_MODE` and everything after the character was lost), and the token-tiling property (the
stricter, character-exact check -- see `assertTokensTileInput`) now correctly consumes U+FFFF
itself into the token span rather than losing it.

Un-excluding `TestJson.flix` from the token-tiling property surfaced a second, unrelated defect
in the same file -- see D14. It does not reopen this one: replacing every U+FFFF in the file
with an ordinary character and re-running produces the identical failure at the identical
offset, proving D14 has nothing to do with U+FFFF specifically.

## D13 — `qname` never honored `tail`, so it never stopped early — RESOLVED

`grammars/FlixParser.g4`'s `qname : name ( dot name )*` was greedy and unbounded.
`Parser2.scala`'s `nameAllowQualified` (`:738`) is not: it takes a `tail` parameter and, by
default, stops consuming dot-separated segments the moment it consumes one whose kind is in
`tail` (`NAME_LOWERCASE` by default). A namespace-qualified reference like `Foo.Bar.baz.qux`
therefore stops after `baz` in the reference, leaving `.qux` for the postfix chain
(`postfixSuffix`-equivalent) to turn into a field/method access. This grammar's `qname` consumed
the whole thing instead, so `x.foo(1)` parsed as `Apply(qname(x.foo), 1)` where the reference
produces `Apply(GetField(x, foo))` (or `InvokeMethod` outright) — a real shape defect, not a
cosmetic one, affecting 1,243 expression-position `qname` nodes across the corpus (measured
before the fix; see the corpus-analysis notes this defect was found from).

Fixed by splitting `qname` into two rules: the general one now stops after the first
`nameLowercase`/`nameMath` segment (or the last `nameUppercase` segment if none is lower/math),
matching the reference's default; a new `javaQname` keeps the old unrestricted definition for the
two positions the reference itself marks `tail = Set()` — `import` (`Parser2.scala:911`) and a
`catch` clause's exception type (`:2811`), both of which need Java's lowercase package segments
(`java.util.List`) to NOT trigger an early stop. Every other `qname` call site in this grammar
matches a reference call site that already used the default `tail`, so no other position needed
to change.

## D14 — `TestJson.flix` appeared to drop two characters — RESOLVED

The lexer was not dropping anything. `FlixGrammarPropertiesTest.assertTokensTileInput` compared
a **code-point** index against a **UTF-16** length: ANTLR's `CodePointCharStream` measures token
spans in code points, while Kotlin's `String.length` counts UTF-16 units. The two differ by one
per astral character, and `TestJson.flix` contains two U+1F600 emoji — so the test reported the
file's last two UTF-16 units, which happen to be `}` and `\n`, as dropped.

That also explains the observation recorded here as evidence of a real defect: replacing every
U+FFFF with an ordinary character changed nothing, because U+FFFF is a BMP character and was
never the cause. The surrogate pairs were, and nothing in the report pointed at them.

Measured directly: the file is 82,652 bytes, 82,492 code points, 82,494 UTF-16 units — and the
lexer's tokens tile all 82,492 of them with no gap.

**Fix**: the property works in code-point space throughout and converts only when it slices the
string. The `TestJson.flix` exclusion is gone; the corpus tiling check now runs over every file
with none excluded.

**Worth keeping**: this was invisible to every gate except the token-tiling property, and the
property itself was what reported it wrongly. A test that measures two things in different units
can manufacture a defect that survives three rounds of investigation into the wrong component.

## D15 — Tight `::` and `use` package paths (Flix v0.77.0) — OPEN

Flix v0.77.0 adds `ColonColonTight`: `::` written without surrounding whitespace is its own token
and separates a package path, as in `use flixball::Game.Board` or `use flixball::{Game, Board}`.
Both tight and spaced `::` are list cons, and `:::` has no tight variant. The parser gains
`UsesOrImports.Package`: a single lowercase segment plus the tight separator. It rejects spaced
`::` in that position. All of this was verified against the v0.77.0 release jar.

This grammar has neither. `COLON_COLON : '::' ;` (`FlixLexer.g4:166`) is a plain literal. `useClause`
(`FlixParser.g4:26`) takes a `qname`, and `qname` joins segments only with `dot`, so `use a::b` is a
syntax error today.

The obvious fix has a hazard. Splitting the token without also admitting the tight form in
`ConsExpr` (`FlixParser.g4:373`) and `ConsPattern` (`:591`) breaks every tight cons. The corpus
holds exactly one, `List.point(42::Nil)` in `TestList.flix:537`, so that line is all that stands
between the gate and a silent regression. Upstream hit the same trap from the other side: it left
`("::", ColonColon)` in its operator table and decides tightness outside it.

**Fix**: see step 6 of [FLIX-SPEC-MIGRATION.md](FLIX-SPEC-MIGRATION.md). Add `COLON_COLON_TIGHT`
through a `classifyColonColon()` modelled on `classifyArrow()`, in both `FlixLexerBase.java` and
`FlixLexerBase.ts`. Admit it in both cons rules, add an optional single-segment package prefix to `useClause`, and
add positive and negative fixtures, because the corpus does not exercise the package form.

---

## How these were found

Every defect above came from one measurement — parsing a real Flix checkout — rather than from
review. None was visible in a passing test suite. When adding a construct, re-measure; when a
number does not move, the construct was not the problem.
