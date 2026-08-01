# Design debate: an ANTLR4 grammar for Flix

A working session held before writing a line of grammar. Five perspectives, deliberately
adversarial. Every claim is expected to carry a citation into the reference compiler
(`wstein/flix-fork@debf7df`, Flix 0.75.1) or into measured evidence from the sibling projects.

Proposals are rated **1–5** (5 = adopt, 1 = reject). Ratings are per-participant; the
consensus row records what was actually adopted.

## Participants

| | Role | Bias declared up front |
| --- | --- | --- |
| **Dana** | Compiler engineer, reads `Parser2.scala` daily | Fidelity to the reference implementation, including its warts |
| **Rafael** | IDE tooling, will consume this from the JetBrains plugin | Error recovery and latency beat theoretical purity |
| **Ingrid** | Parsing theory, ANTLR internals | ALL(*) prediction cost is a real budget, not an abstraction |
| **Mo** | Maintainer of `tree-sitter-flix` | Has already paid for most of these mistakes once |
| **Priya** | Release and QA | If it is not gated in CI it will regress by Friday |

---

## Q0. Should this project exist at all?

**Mo** opens hostile. "`tree-sitter-flix` parses 888 of 890 files in a Flix checkout, and the
two failures are deliberate negative tests. You are proposing to rebuild a solved problem.
Justify it or stop."

**Rafael**: "Different consumer. tree-sitter gives me incremental reparse in an editor buffer;
it does not give me a JVM artifact, a visitor API, or a diagnostic I can put in a tooltip. The
JetBrains plugin is Kotlin. Shipping a C library and a Node build step into a Gradle plugin
build is a tax I will pay every release."

**Ingrid** adds the technical argument: "Mo's grammar carries 22 declared `conflicts:` entries.
Each one is a place LR(1) could not decide and tree-sitter falls back to GLR — it explores both
and picks later. Eight of those are pattern-versus-expression. ANTLR's ALL(*) resolves those
with unbounded lookahead at the decision point instead, which is strictly closer to what
`Parser2.scala` actually does, because `Parser2` *is* recursive descent with bounded scanning.
We are transliterating a recursive-descent parser into a recursive-descent framework. Mo was
transliterating it into an LR framework. That mismatch is the source of most of his conflicts."

**Mo**, conceding partially: "That is fair and I will stop objecting to the premise. But I want
my evidence used. The corpus gate is the only reason my grammar is correct, and I will fight
for it later."

**Dana**: "One more reason. `Parser2` is a lossless, resilient parser — it emits `ErrorTree`
nodes rather than throwing. Nobody outside the compiler can reuse it. A published grammar is
the only executable specification of Flix syntax that a third party can consume."

**Consensus**: proceed. Not a replacement for `tree-sitter-flix`; a JVM-side sibling. The two
should cross-validate — see Q10.

---

## Q1. One combined grammar or split lexer/parser?

**Proposal A** — single `Flix.g4`. **Proposal B** — `FlixLexer.g4` + `FlixParser.g4`.

**Dana** argues for A: "One file, one place to look. Splitting means maintaining a `tokens {}`
block and a `tokenVocab` dependency that Gradle has to order correctly."

**Rafael** argues for B, and wins on a use case Dana had not considered: "Syntax highlighting
in the plugin needs *tokens only*. If the lexer is a separate artifact I can run it standalone
over a buffer, at every keystroke, without ever building a parse tree. With a combined grammar
ANTLR still generates two classes, but the grammar file mixes two concerns and the lexer half
is going to be the ugly half — modes, a mode stack, a brace-depth counter, and a custom
superclass."

**Ingrid**: "B also keeps the lexer's custom base class out of the parser's way. We will need
`options { superClass = FlixLexerBase; }` for the whitespace-sensitive tokens. Putting that on
a combined grammar drags it onto the parser too."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| A: combined | 3 | 1 | 2 | 2 | 3 |
| B: split | 4 | 5 | 5 | 4 | 4 |

**Consensus: B.** Split, with `FlixParser.g4` declaring `options { tokenVocab = FlixLexer; }`.

---

## Q2. How to lex the whitespace-sensitive `->` and `.`

This is the first genuinely hard one. `Lexer.scala:349-361` makes `a->b` (tight, no whitespace
either side) a *struct field access* and `a -> b` / `a ->b` / `a-> b` the function arrow.
`Lexer.scala:315-323` gives `.` three outcomes: `Dot`, `DotWhiteSpace` (the Datalog constraint
terminator), and an error when *preceded* by whitespace.

**Proposal A** — semantic predicates: `ARROW_TIGHT : '->' {noWsAround()}?;`
**Proposal B** — one token type, split later in a `TokenStream` filter.
**Proposal C** — one token type, decided in the parser with whitespace on a visible channel.

**Ingrid** kills C immediately: "If whitespace is visible the parser has to thread it through
every rule. That is how you get a grammar nobody can read. And it is not what `Parser2` does —
the reference parser never sees whitespace; the distinction is made in the lexer."

**Rafael** pushes B: "A token-stream filter is testable in isolation and does not touch the
grammar."

**Ingrid** objects with a specific cost: "It also means every consumer must remember to install
the filter, and the raw token stream is then *wrong*. Rafael, you just argued you want to run
the lexer standalone for highlighting. Under B your standalone lexer emits the wrong token."

Rafael concedes the point.

**Dana** then improves A into something better than what anyone proposed: "Do not use a
predicate. Predicates in an ANTLR lexer are evaluated during DFA simulation and defeat the
lexer's DFA cache — you pay on every token, not just arrows. We do not need a predicate here.
We need a *disambiguating action*. Match `'->'` unconditionally, then in the action inspect the
character before `_tokenStartCharIndex` and the character at the current index and assign
`_type = ARROW_TIGHT` or `ARROW_WS`. Same shape for `.` with three outcomes. No prediction
cost, no backtracking, and it is a one-line lookup on the `CharStream` we already hold."

**Ingrid**, convinced: "That is correct and it is the standard trick. Put the helpers on
`FlixLexerBase` so the `.g4` stays declarative."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| A: predicates | 2 | 3 | 2 | 3 | 2 |
| A′: type-setting actions | 5 | 4 | 5 | 5 | 4 |
| B: stream filter | 2 | 3 | 2 | 2 | 3 |
| C: parser-level | 1 | 1 | 1 | 1 | 1 |

**Consensus: A′.** Match, then assign the token type in an action, with the whitespace lookup
on `FlixLexerBase`. Note the reference treats out-of-bounds as whitespace on both sides.

---

## Q3. Reserved operators versus user-defined operators

`Lexer.scala:522-548`: a user-defined operator is any run of `+ - * < > = ! & | ^ $`. Reserved
spellings (`<-`, `<=>`, `<+>`, `::`, …) match **only if the next character is not** one of those.
`advanceIfInTree` (`Lexer.scala:404-434`) descends a trie and does **not backtrack**.

**Ingrid** states the trap precisely: "This is not longest-prefix match. ANTLR's default
maximal munch would lex `<--` as `<-` then `-`. Flix lexes it as one `GenericOperator`. Write
the reserved operators as literals and you get the wrong language."

**Proposal A** — enumerate every reserved operator as its own lexer rule, plus a
`GENERIC_OPERATOR` rule, and rely on ANTLR ordering.
**Proposal B** — one rule `OP_RUN : [+\-*<>=!&|^$]+ ;` with an action that looks the whole
matched text up in a reserved-spelling map and rewrites `_type`.

**Dana**: "B, and it is not close. B *is* the algorithm in `advanceIfInTree`, transcribed. A is
an approximation that will be wrong on exactly the inputs nobody writes tests for. The Flix
standard library defines `>=>`, `<><`, and `_>==>`."

**Mo** supplies the scar tissue: "I lost a week to the tree-sitter version of this. Giving
`generic_operator` an explicit precedence made `>>` lex as two `>`. And writing the repetition
as `{2,}` silently compiled to exactly `{2}`, truncating every operator of three or more
characters. The corpus pass rate moved 77% to 84% when I found it. Take B."

**Ingrid** raises the only real objection to B: "If the reserved operators are not lexer rules,
their token types do not exist for the parser to reference."

**Dana**: "They do if we declare them in a `tokens { … }` block and assign them from the action.
That is exactly what `tokens` is for."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| A: enumerate | 1 | 2 | 2 | 1 | 2 |
| B: run + reclassify | 5 | 4 | 5 | 5 | 4 |

**Consensus: B.** One `OP_RUN` rule, reserved spellings declared in `tokens {}` and assigned in
the action. Do not include `/`, `~`, `?`, `#`, `%`, or `:` in the run set.

---

## Q4. Encoding operator precedence

The table is at `Parser2.scala:1750-1790` — 15 levels, with unary prefixes interleaved at
levels 9, 12, 13, 14 rather than sitting at the top.

**Dana** leads with the landmine: "Both precedence docstrings in the compiler say *lower is
higher precedence*. Both are wrong. `rightBindsTighter` returns `right.precedence >
left.precedence`. Larger binds tighter. Anyone transcribing from the comment gets the whole
table backwards, and the tests will still mostly pass because most expressions are shallow."

**Mo**: "Confirmed independently. It bit me too. Note the second consequence nobody expects:
user-defined operators are level 11, `+`/`-` are level 6. So `a |> b + c` is `(a |> b) + c`.
Every pipeline operator in the standard library binds tighter than arithmetic."

**Proposal A** — a cascade of 15 rules, `orExpr : andExpr ('or' andExpr)*` and so on.
**Proposal B** — one left-recursive `expression` rule with ordered alternatives.

**Ingrid** argues B on both correctness and shape: "ANTLR rewrites direct left recursion into a
precedence-climbing parser. That is *the same algorithm* as the Pratt loop in `Parser2` at
`:1601-1634`. Alternative order gives precedence, earlier is tighter, and `<assoc=right>`
handles `::` and `:::`. Crucially, ANTLR supports prefix-operator alternatives inside a
left-recursive rule and assigns them precedence by position too — which is the only way to
express `discard` sitting at 9, *below* the backtick infix at 10 and user operators at 11.
Proposal A cannot express that interleaving without 15 mutually recursive rules whose names
lie about what they contain."

**Rafael** adds the tooling argument: "A cascade also produces a parse tree 15 levels deep for
the expression `x`. My PSI builder would spend its life collapsing degenerate nodes."

**Priya** raises the only concern: "Left-recursive rules are harder to unit test in isolation."

**Ingrid**: "Test through `expression` and assert on tree shape. That is what we want to assert
anyway."

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| A: rule cascade | 2 | 1 | 2 | 2 | 3 |
| B: left recursion | 5 | 5 | 5 | 4 | 4 |

**Consensus: B**, with the table transcribed **inverted from the docstring** and a test that
pins `1 + 2 * 3`, `a |> b + c`, `x :: y :: z`, and `discard a <*> b` as executable
documentation of the inversion.

---

## Q5. String interpolation — bug-compatible tokens or clean ones?

`Lexer.scala:609-637`. In `"a${x}b${y}c"` the reference emits **one** token with the text
`}b${` — it closes one interpolation and opens the next — typed `LiteralStringInterpolationL`,
distinguished from a nested opener only by its leading `}` (`Parser2.scala:3283-3288`).

**Dana** wants fidelity: "Reproduce the token stream exactly. If we diverge, we cannot
differentially test against the reference lexer."

**Rafael** objects hard: "That token is unusable for highlighting. It spans a closing
delimiter, literal string content, and an opening delimiter. I would have to re-lex its text to
colour it. And the JetBrains lexer contract wants tokens that map to one highlight each."

**Mo**: "tree-sitter used separate tokens — `_interpolation_start`, `_interpolation_middle`,
`_interpolation_end` — and the grammar is fine. The fused token is an artifact of how
`acceptStringInterpolation` recurses, not a property of the language."

**Ingrid** frames the actual question: "What are we claiming fidelity *to*? If it is the set of
accepted programs, separate tokens are exactly equivalent. If it is the token stream, we are
committing to reproduce an implementation detail that has no specification status and that the
compiler is free to change. Fidelity to the language, not to the token stream."

**Dana** concedes but extracts a condition: "Then it must be documented as a deliberate
divergence, in `CLAUDE.md`, with the reason. Undocumented divergences are how a grammar rots."

Agreed. Same treatment for the `$`-escaped-name quirk, where the reference drops the `$` from
the token span via `resetStart()` (`Lexer.scala:518`) leaving a one-character hole in token
coverage — we keep the span contiguous and record the divergence.

| | Dana | Rafael | Ingrid | Mo | Priya |
| --- | --- | --- | --- | --- | --- |
| Fused, bug-compatible | 4 | 1 | 2 | 1 | 2 |
| Separate, documented | 3 | 5 | 5 | 5 | 5 |

**Consensus: separate tokens**, via lexer modes with a mode stack for nested strings plus a
per-level brace-depth counter for bare `{}` blocks inside an interpolation. Nesting is
unbounded in the reference, so the counter must be a stack, not an `int`.

**Priya** attaches a condition that is adopted: "Then I want a mode-stack reset on EOF. Mo's
scanner needed an `_error_sentinel` because an unterminated string sent it scanning to EOF and
went quadratic — 40k braces hung an editor for 4.3 seconds. We will have the same failure with
a stuck lexer mode."

---

## Q6. Datalog — the known cost centre

**Mo** presents the measurement, and it is the most valuable single contribution of the
session: "In tree-sitter, generation took 16 seconds without the Datalog rules and 7 to 11
minutes with them. Adding just `solve`, `psolve`, and `inject` took it from 16s to 78s. The
cause is not Datalog syntax generally — it is that `query`, `solve`, `psolve`, `inject`, and
`pquery` each take a *greedy comma-separated expression list*. After any expression, a comma is
ambiguous between continuing that list and closing an enclosing argument list or tuple. That
makes `,` ambiguous everywhere in the language, not just inside Datalog."

**Ingrid** translates the risk: "In ANTLR the cost moves from build time to *runtime*. ALL(*)
will fall into full-context prediction at every comma inside a fixpoint argument, and the DFA
cache will grow. Build time will be fine and we will feel it as parse latency instead — which
for Rafael's consumer is worse."

**Rafael**: "Considerably worse. A build that takes 11 minutes once is annoying. A parser that
stalls on every keystroke inside a `query` is unusable."

Mitigations proposed:

1. **Share one `fixpointExpressions` rule** across all five keywords rather than repeating the
   list at each site. Mo: "Marked *do not undo* in my grammar for this reason." — adopted, 5/5.
2. **Do not encode `select`/`from`/`where` clause order.** `Parser2` requires a fixed order,
   each optional; three chained optionals after a greedy expression list is the worst possible
   shape. Accept `(selectClause | fromClause | whereClause)*` and check order in validation.
   Mo: "This alone cut minutes off my generation." — adopted, 4/5.
3. **A CI latency gate**, proposed by Priya: parse the corpus with a wall-clock budget and run
   one job under `PredictionMode.LL_EXACT_AMBIG_DETECTION`, failing on new ambiguity reports —
   adopted, 5/5.

**Dana** dissents on mitigation 2: "We are now accepting programs the reference rejects, in a
place where the reference is unambiguous. That is a different kind of permissiveness than
`Weeder2` permissiveness — the weeder rejects *semantically*; clause order is *syntactic*."

**Ingrid** answers: "It is still a superset that a validator can narrow, and the validator gets
to say `'from' must precede 'where'` instead of ANTLR saying `mismatched input`. The diagnostic
is better *and* the parser is faster. Dana's distinction is real but it does not change the
trade."

Dana accepts under protest, and the objection is recorded here rather than resolved away.

---

## Q7. Where exactly does "permissive" stop?

Settled policy from the project brief: follow the parser, not the weeder. The debate is about
the boundary, since "permissive" is not self-defining.

**Priya**: "Without a rule, every future contributor relitigates this per-construct."

The rule adopted, drafted by Ingrid and amended by Dana:

> A constraint belongs in the grammar **only if** (a) it is needed to disambiguate a parse, or
> (b) enforcing it costs nothing in error recovery. Everything else belongs to the validator.

Worked examples, checked against `Weeder2`:

| Construct | Grammar or validator | Why |
| --- | --- | --- |
| `pub pub sealed def` | Validator | `Parser2.scala:1392-1398` parses `modifier*` unconstrained; `DuplicateModifier` is a weeder error |
| Annotation names | Validator | `@Name` is one lexer token; the 17 legal names are a weeder table |
| `%%INTRINSIC%%` names | Validator | Same — one token, weeder-side table |
| Non-linear patterns | Validator | `NonLinearPattern` needs an environment, not a grammar |
| Empty `"${}"` | Validator | `EmptyInterpolatedExpression` |
| `->` tight vs spaced | **Grammar (lexer)** | Disambiguates struct access from lambda — rule (a) |
| `{` block vs record | **Grammar** | Disambiguates — rule (a) |
| `not fix P(x)` order | **Grammar** | `Parser2.scala:4009-4010` consumes them in fixed order; free to encode — rule (b) |
| Datalog clause order | Validator | Encoding it is the Q6 cost centre — fails rule (b) |

**Dana** notes a trap: "`enum Foo(Int32) { case Bar }` — the shorthand case body *and* a block
body together is `IllegalEnum`, but both individually are legal. The grammar must allow both
simultaneously and let the validator reject the combination. `Parser2.scala:1186-1207`."

---

## Q8. Rule naming

**Dana** proposes mirroring `SyntaxTree.TreeKind` exactly. Mo did this and reports it worked.

**Ingrid** objects on idiom: "`Expr.LiteralMapKeyValueFragment` is not an ANTLR rule name.
ANTLR convention is lowerCamelCase, and prefixing everything with its group gives us
`exprLiteralMapKeyValueFragment`, which is unreadable."

**Mo**: "Drop the group prefix where the leaf is unambiguous. I did that and the CST still lines
up with the compiler's, which is the actual benefit — you can diff my tree against
`Parser2`'s output and review it."

**Rafael**: "Keep the prefix where the leaf name collides across groups. `Pattern.Record` and
`Type.Record` and `Expr.RecordOperation` all exist."

**Consensus** (4/5 average): lowerCamelCase leaf names, group prefix only on collision
(`recordPattern`, `recordType`, `recordOperation`). A mapping table from rule name to `TreeKind`
ships in `docs/`. The three dead `TreeKind`s — `Type.Function`, `Expr.InstanceOf`,
`TypeParameter`, never produced by `Parser2` — get no rules.

---

## Q9. Comments

`Lexer.scala:864-897`: `//` is a line comment, `///` is a doc comment, `////` and beyond is a
line comment again (the count must be *exactly* one more slash). Block comments **nest**.
Comments are real tokens in the reference and the parser skips them explicitly.

**Rafael**: "Not `-> skip`. The formatter consumer cannot round-trip source it cannot see."

**Dana**: "And doc comments attach to declarations. If `///` lands on the same hidden channel as
`//`, attaching it means filtering by text, which is exactly the `slashCount` bug waiting to
happen."

**Consensus, unanimous 5/5**: three channels — default for code, `COMMENTS` for `//` and `/* */`,
`DOC_COMMENTS` for `///`. Nested block comments get a recursive lexer rule. Mo's warning
carries over: `LINE_COMMENT` and `DOC_COMMENT` must be mutually exclusive patterns with no
explicit precedence, or the three-character `///` prefix wins over a whole `////` line.

---

## Q10. Verification strategy

**Mo**, cashing in the concession from Q0: "Hand-written tests will not get you there. My
grammar went 77% → 89% → 96% → 99.8% → 100% on a real corpus, and each jump came from a
construct no one thought to write a test for. At 89%, *every* remaining standard-library
failure traced to one omission — that a Flix definition may be *named* by a user-defined
operator, as in `def >>` and `use Bool.{==>, <==>}`. Build the corpus gate first."

**Priya** operationalises it and adds the part Mo did not have: "A ratcheting parse-rate
baseline committed to the repo, so the number can only go up. Plus negative tests, because a
score *above* the target is also a regression. And — this is the gap in Mo's setup —
**reachability**, not just parse rate."

**Mo** confirms the need from his own scar: "`invoke_method` matched zero times across 890 files
because `apply_expression(get_field(...))` shadowed it. Same token sequence, wrong rule won. The
corpus parsed at 100% the whole time. After the fix it matched 2195 times. A parse-rate gate
cannot see a silently dead rule."

**Ingrid** proposes the ANTLR-native form: "Count rule invocations with a listener over the
corpus and fail CI on any rule with zero hits that is not explicitly allowlisted. In ANTLR this
is cheap — one `ParseTreeWalker` pass."

**Consensus**, adopted unanimously:

1. Corpus parse-rate gate over the Flix checkout, ratcheting baseline committed.
2. Negative corpus — inputs that must *fail*.
3. Rule-reachability gate with an explicit allowlist for genuinely unreachable rules.
4. Ambiguity gate under `LL_EXACT_AMBIG_DETECTION`.
5. Differential token-boundary check against `tree-sitter-flix`, which `flix-textmate` already
   does and which catches lexer divergence cheaply.

---

## Consensus summary

| # | Decision | Vote |
| --- | --- | --- |
| 0 | Build it; a JVM-side sibling to `tree-sitter-flix`, not a replacement | unanimous |
| 1 | Split `FlixLexer.g4` / `FlixParser.g4` | 4.4 |
| 2 | Whitespace-sensitive `->`/`.` via type-setting **actions**, not predicates, on a `FlixLexerBase` | 4.6 |
| 3 | One `OP_RUN` rule, reserved spellings reassigned in the action | 4.6 |
| 4 | One left-recursive `expression` rule; precedence table transcribed **inverted** | 4.6 |
| 5 | Separate interpolation tokens; documented divergence; mode-stack reset on EOF | 4.6 |
| 6 | Share `fixpointExpressions`; unordered query clauses; latency + ambiguity gates | 4.4 (Dana dissents on clause order) |
| 7 | Permissiveness rule: disambiguation-necessary or recovery-free, else validator | unanimous |
| 8 | lowerCamelCase `TreeKind` leaf names, prefixed only on collision | 4.0 |
| 9 | Three comment channels; nested block comments | unanimous |
| 10 | Corpus + negative + reachability + ambiguity + differential gates | unanimous |

### Recorded dissent

**Dana**, on Q6: accepting unordered `select`/`from`/`where` makes the grammar a superset in a
place where the reference is syntactically unambiguous, which is a different justification from
the `Weeder2` permissiveness the project otherwise rests on. Accepted on performance grounds,
not on principle. Revisit if ANTLR's prediction cost turns out to be tolerable — see below.

---

## Future developments

- **Measure Dana's dissent.** Build the ordered-clause variant behind a branch and benchmark
  both against the corpus. If ALL(*) handles the ordered form without full-context blowup, the
  superset is unjustified and should be reverted. This is a measurable question, not a taste
  one, and it should not stay open indefinitely.
- **Incremental parsing.** ANTLR has no incremental mode. If the JetBrains plugin needs
  sub-frame reparse, the honest answer is to keep `tree-sitter-flix` for the editor buffer and
  use this grammar for whole-file analysis. Do not attempt to bolt incrementality on.
- **Track the moving parts of the language.** Restrictable variants (`rvadd`, `rvand`, `xvar`,
  `choose*`), extensible variants (`ematch`, `#| |#`), Datalog provenance, and anonymous-class
  support are all actively changing in the compiler. `Weeder2.scala:3008` carries an
  `EFF-MIGRATION` marker; the effect system is mid-migration. Pin the reference commit in
  `CLAUDE.md` and bump deliberately.
- **`redef` is version-gated.** Under `-Xnodeprecated`, `pub redef` becomes illegal
  (`Weeder2.scala:326`). A validator that models compiler flags will eventually be needed.
- **Publish to Maven Central** under `io.github.wstein` once the corpus gate is green, so the
  JetBrains plugin can depend on a released artifact rather than a composite build.
- **Railroad diagrams in CI**, generated from the `.g4` files, as the human-readable syntax
  reference Flix currently lacks.

## Concrete next actions

1. `FlixLexerBase` with the whitespace lookups for `->` and `.`, and the reserved-operator map.
2. `FlixLexer.g4`: 84 keywords with `!isNameChar` tail guards, `OP_RUN`, three comment channels,
   interpolation modes with a brace-depth stack.
3. Executable precedence tests pinning the inverted table before any expression rule is written.
4. Corpus gate wired into `./gradlew check` before the grammar is half-finished, not after.
5. A `docs/TREEKIND-MAP.md` mapping rule names to `SyntaxTree.TreeKind`.
