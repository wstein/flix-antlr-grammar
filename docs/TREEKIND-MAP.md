# Rule names and `SyntaxTree.TreeKind`

Grammar rules are named after the reference compiler's `TreeKind` inventory
(`ast/SyntaxTree.scala`) so that a parse tree produced here can be diffed against the
compiler's own concrete syntax tree during review.

The convention, settled in [DESIGN-DEBATE.md](DESIGN-DEBATE.md) Q8: use the **leaf** name in
lowerCamelCase, and keep the group prefix only where a leaf collides across groups.

| Grammar rule | `TreeKind` |
| --- | --- |
| `compilationUnit` | `Root` |
| `usesOrImports` | `UsesOrImports.UseOrImportList` |
| `useClause` / `useName` | `UsesOrImports.Use`, `.UseMany`, `.Alias` |
| `importClause` | `UsesOrImports.Import`, `.ImportMany` |
| `modDeclaration` | `Decl.Module` |
| `defDeclaration` | `Decl.Def`, `Decl.Redef`, `Decl.Signature` |
| `lawDeclaration` | `Decl.Law` |
| `enumDeclaration` / `enumCase` | `Decl.Enum`, `Decl.RestrictableEnum`, `Case`, `CaseBody` |
| `structDeclaration` / `structField` | `Decl.Struct`, `StructField` |
| `traitDeclaration` / `traitMember` | `Decl.Trait` |
| `instanceDeclaration` / `instanceMember` | `Decl.Instance` |
| `assocTypeDeclaration` | `Decl.AssociatedTypeSig`, `Decl.AssociatedTypeDef` |
| `aliasDeclaration` | `Decl.TypeAlias` |
| `effDeclaration` / `opDeclaration` | `Decl.Effect`, `Decl.Op` |
| `annotation` / `modifier` | `AnnotationList`, `ModifierList` |
| `withClause` / `constraint` | `Type.ConstraintList`, `Type.Constraint` |
| `whereClause` / `equalityConstraint` | `Decl.EqualityConstraintList`, `.EqualityConstraintFragment` |
| `typeParams` / `typeParam` | `TypeParameterList`, `Parameter` |
| `formalParams` / `formalParam` | `ParameterList`, `Parameter` |
| `type` / `primaryType` | `Type.Type` and the `Type.*` group |
| `recordFieldType` | `Type.RecordFieldFragment` |
| `schemaTerm` | `Type.PredicateWithTypes`, `Type.PredicateWithAlias` |
| `qname` / `name` | `QName`, `Ident` |
| `statement` | `Expr.Statement` |
| `expr` | `Expr.Expr` and the `Expr.*` group |
| `matchRule` | `Expr.MatchRuleFragment` |
| `catchRule` | `Expr.TryCatchRuleFragment` |
| `handlerRule` | `Expr.RunWithRuleFragment` |
| `selectRule` | `Expr.SelectRuleFragment`, `.SelectRuleDefaultFragment` |
| `forFragment` | `Expr.ForFragmentGenerator`, `.ForFragmentGuard`, `.ForFragmentLet` |
| `parFragment` | `Expr.ParYieldFragment` |
| `fixpointExpr` | `Expr.FixpointSolveWithProject`, `.FixpointQuery`, `.FixpointInject` |
| `fixpointClause` | `Expr.FixpointSelect`, `.FixpointFromFragment`, `.FixpointWhere`, `.FixpointWith` |
| `constraintSet` / `datalogConstraint` | `Expr.FixpointConstraintSet`, `Expr.FixpointConstraint` |
| `predicateHead` / `predicateBody` / `predicateAtom` | `Predicate.Head`, `.Body`, `.Atom` |
| `predicateAndArity` | `PredicateAndArity` |
| `collectionLiteral` | `Expr.LiteralArray`, `.LiteralVector`, `.LiteralList`, `.LiteralSet`, `.LiteralMap` |
| `recordOperation` / `recordOpField` | `Expr.RecordOperation`, `.RecordOpExtend`, `.RecordOpRestrict`, `.RecordOpUpdate` |
| `stringLiteral` | `Expr.StringInterpolation`, `Expr.Literal` |
| `pattern` / `primaryPattern` | `Pattern.Pattern` and the `Pattern.*` group |
| `recordPattern` / `recordFieldPattern` | `Pattern.Record`, `.RecordFieldFragment` |

## Deliberately absent

Three `TreeKind`s are never produced by `Parser2` and have no rule here:
`Type.Function` (arrow types are `Type.Binary`), `Expr.InstanceOf` (parsed as `Expr.Binary`
with a keyword operator), and `TypeParameter` (type parameters use `Parameter`).

`Expr.Expr`, `Type.Type`, `Pattern.Pattern` and `Predicate.Body` are transparent one-child
wrappers in the reference; ANTLR produces the equivalent nesting implicitly, so they have no
dedicated rules.
