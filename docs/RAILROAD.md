# Railroad diagrams

**Generated from `grammars/FlixParser.g4`.** Do not edit by hand; run `node tools/gen-docs.mjs`.

Rendered by [Mermaid](https://mermaid.js.org) 11.16 or newer, which added railroad diagrams.
GitHub renders these inline; older Mermaid versions will show the block as an error.

A curated subset of the rules whose shape is worth seeing. The complete grammar is in
[SYNTAX.md](SYNTAX.md).

## `compilationUnit`

```mermaid
railroad-ebnf-beta
compilationUnit ::= usesOrImports* declaration* EOF ;
```

## `declaration`

```mermaid
railroad-ebnf-beta
declaration ::= modDeclaration
    | defDeclaration
    | valDeclaration
    | enumDeclaration
    | structDeclaration
    | traitDeclaration
    | instanceDeclaration
    | effDeclaration
    | aliasDeclaration ;
```

## `defDeclaration`

```mermaid
railroad-ebnf-beta
defDeclaration ::= declPrefix ( DEF | REDEF ) definitionName typeParams? formalParams ( COLON typeAndEffect )? withClause? whereClause? ( EQUAL ( statement
    | ERR_NUMBER ) )? ;
```

## `enumDeclaration`

```mermaid
railroad-ebnf-beta
enumDeclaration ::= declPrefix RESTRICTABLE ENUM nameUppercase LBRACK typeParam RBRACK typeParams? enumDeclarationTail
    | declPrefix ENUM nameUppercase typeParams? enumDeclarationTail ;
```

## `traitDeclaration`

```mermaid
railroad-ebnf-beta
traitDeclaration ::= declPrefix TRAIT nameUppercase typeParams? withClause? ( LBRACE traitMember* RBRACE )? ;
```

## `effDeclaration`

```mermaid
railroad-ebnf-beta
effDeclaration ::= declPrefix EFF nameUppercase typeParams? ( LBRACE opDeclaration* RBRACE )? ;
```

## `type`

```mermaid
railroad-ebnf-beta
type ::= typeOperators COLON kind
    | typeOperators ;
```

## `typeOperators`

```mermaid
railroad-ebnf-beta
typeOperators ::= ( NOT | TILDE | RVNOT ) typeOperators
    | typeOperators AND typeOperators
    | typeOperators OR typeOperators
    | typeOperators XOR typeOperators
    | typeOperators AMPERSAND typeOperators
    | typeOperators ( PLUS | MINUS ) typeOperators
    | typeOperators RVAND typeOperators
    | typeOperators ( RVADD | RVSUB ) typeOperators
    | <assoc=right> typeOperators ARROW_WS typeOperators
    | typeOperators BACKSLASH typeOperators
    | primaryType typeArgs+
    | primaryType ;
```

## `primaryType`

```mermaid
railroad-ebnf-beta
primaryType ::= NAME_LOWERCASE
    | qname
    | nameMath
    | UNDERSCORE
    | STATIC_UPPER
    | UNIV
    | ( TRUE | FALSE )
    | LPAREN ( recordFieldOrType ( COMMA recordFieldOrType )* )? ( BAR type )? RPAREN
    | LBRACE BAR RBRACE
    | LBRACE ( recordFieldType ( COMMA recordFieldType )* ( BAR type )? | BAR type ) RBRACE
    | LBRACE ( type ( COMMA type )* )? RBRACE
    | HASH_LBRACE ( schemaTerm ( COMMA schemaTerm )* )? ( BAR type )? RBRACE
    | HASH_LPAREN ( schemaTerm ( COMMA schemaTerm )* )? ( BAR type )? RPAREN
    | HASH_BAR ( schemaTerm ( COMMA schemaTerm )* )? ( BAR type )? BAR_HASH
    | ANGLE_L qname ( COMMA qname )* ANGLE_R ;
```

## `pattern`

```mermaid
railroad-ebnf-beta
pattern ::= <assoc=right> pattern ( COLON_COLON | COLON_COLON_TIGHT ) pattern
    | primaryPattern ;
```

## `primaryPattern`

```mermaid
railroad-ebnf-beta
primaryPattern ::= qname ( LPAREN ( pattern ( COMMA pattern )* )? RPAREN )?
    | recordPattern
    | tuplePattern
    | variableName
    | MINUS ( INT_LITERAL | FLOAT_LITERAL | HEX_LITERAL )
    | INT_LITERAL
    | FLOAT_LITERAL
    | HEX_LITERAL
    | CHAR_LITERAL
    | REGEX_LITERAL
    | stringLiteral
    | TRUE
    | FALSE
    | NULL ;
```

## `datalogConstraint`

```mermaid
railroad-ebnf-beta
datalogConstraint ::= predicateHead ( COLON_MINUS predicateBody ( COMMA predicateBody )* )? ( DOT_WS | DOT ) ;
```

## `predicateBody`

```mermaid
railroad-ebnf-beta
predicateBody ::= IF LPAREN expr RPAREN
    | IF expr
    | LET ( LPAREN variableName ( COMMA variableName )* RPAREN | variableName ) EQUAL expr
    | predicateAtom ;
```

## `fixpointExpr`

```mermaid
railroad-ebnf-beta
fixpointExpr ::= ( SOLVE | PSOLVE ) fixpointExpressions ( PROJECT qname ( COMMA qname )* )?
    | ( QUERY | PQUERY ) fixpointExpressions fixpointClause*
    | INJECT fixpointExpressions INTO predicateAndArity ( COMMA predicateAndArity )* ;
```

## `matchRule`

```mermaid
railroad-ebnf-beta
matchRule ::= CASE pattern ( IF expr )? ARROW_THICK_R statement COMMA? ;
```

## `forFragment`

```mermaid
railroad-ebnf-beta
forFragment ::= IF expr
    | pattern ( EQUAL | ARROW_THIN_L ) expr ;
```
