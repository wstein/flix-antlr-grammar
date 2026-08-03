# Flix syntax reference

**Generated from `grammars/FlixLexer.g4` and `grammars/FlixParser.g4`.**
Do not edit by hand; run `node tools/gen-docs.mjs`.

This is the grammar as implemented, which is deliberately a *superset* of legal Flix.
Constraints such as duplicate modifiers, non-linear patterns and unknown annotation names
parse here and are rejected by a later validation pass, mirroring how the reference
compiler separates `Parser2` from `Weeder2`.

Parser rules: 83 · lexer rules: 136

## Parser rules

### `compilationUnit`

```antlr
compilationUnit
    : usesOrImports* declaration* EOF
    ;
```

### `usesOrImports`

```antlr
usesOrImports
    : useClause SEMI?
    | importClause SEMI?
    ;
```

### `useClause`

```antlr
useClause
    : USE qname ( dot LBRACE useName ( COMMA useName )* COMMA? RBRACE )?
    ;
```

### `importClause`

```antlr
importClause
    : IMPORT javaQname ( dot LBRACE useName ( COMMA useName )* COMMA? RBRACE )?
    ;
```

### `useName`

```antlr
useName
    : name ( ARROW_THICK_R name )?
    | genericOperator
    ;
```

### `declaration`

```antlr
declaration
    : modDeclaration
    | defDeclaration
    | enumDeclaration
    | structDeclaration
    | traitDeclaration
    | instanceDeclaration
    | effDeclaration
    | aliasDeclaration
    | lawDeclaration
    ;
```

### `declPrefix`

```antlr
declPrefix
    : annotation* modifier*
    ;
```

### `modDeclaration`

```antlr
modDeclaration
    : declPrefix MOD qname LBRACE ( usesOrImports | declaration )* RBRACE
    ;
```

### `defDeclaration`

```antlr
defDeclaration
    : declPrefix ( DEF | REDEF ) definitionName typeParams? formalParams
( COLON typeAndEffect )? withClause? whereClause? ( EQUAL statement )?
    ;
```

### `lawDeclaration`

```antlr
lawDeclaration
    : declPrefix LAW definitionName COLON FORALL typeParams? formalParams?
withClause? whereClause? ( EQUAL? statement )?
    ;
```

### `enumDeclaration`

```antlr
enumDeclaration
    : declPrefix RESTRICTABLE? ENUM nameUppercase typeParams?
( LPAREN type ( COMMA type )* RPAREN )?
derivations?
( LBRACE enumCase* RBRACE )?
    ;
```

### `enumCase`

```antlr
enumCase
    : COMMA? CASE? nameUppercase ( LPAREN type ( COMMA type )* RPAREN )? COMMA?
    ;
```

### `structDeclaration`

```antlr
structDeclaration
    : declPrefix STRUCT nameUppercase typeParams?
( LBRACE ( structField ( COMMA structField )* COMMA? )? RBRACE )?
    ;
```

### `structField`

```antlr
structField
    : modifier* nameLowercase COLON type
    ;
```

### `traitDeclaration`

```antlr
traitDeclaration
    : declPrefix TRAIT nameUppercase typeParams? withClause?
( LBRACE traitMember* RBRACE )?
    ;
```

### `traitMember`

```antlr
traitMember
    : defDeclaration
    | lawDeclaration
    | assocTypeDeclaration
    ;
```

### `instanceDeclaration`

```antlr
instanceDeclaration
    : declPrefix INSTANCE qname ( LBRACK type RBRACK )? withClause? whereClause?
( LBRACE instanceMember* RBRACE )?
    ;
```

### `instanceMember`

```antlr
instanceMember
    : defDeclaration
    | assocTypeDeclaration
    ;
```

### `assocTypeDeclaration`

```antlr
assocTypeDeclaration
    : declPrefix TYPE nameUppercase ( typeParams | typeArgs )? ( COLON kind )? ( EQUAL type )?
    ;
```

### `aliasDeclaration`

```antlr
aliasDeclaration
    : declPrefix TYPE ALIAS nameUppercase typeParams? EQUAL type
    ;
```

### `effDeclaration`

```antlr
effDeclaration
    : declPrefix EFF nameUppercase typeParams? ( LBRACE opDeclaration* RBRACE )?
    ;
```

### `opDeclaration`

```antlr
opDeclaration
    : declPrefix DEF definitionName typeParams? formalParams? COLON typeAndEffect withClause?
    ;
```

### `annotation`

```antlr
annotation
    : ANNOTATION ( LPAREN ( expr ( COMMA expr )* )? RPAREN )?
    ;
```

### `modifier`

```antlr
modifier
    : PUB | SEALED | LAWFUL | MUT
    ;
```

### `withClause`

```antlr
withClause
    : WITH constraint ( COMMA constraint )*
    ;
```

### `constraint`

```antlr
constraint
    : qname ( LBRACK type RBRACK )?
    ;
```

### `derivations`

```antlr
derivations
    : WITH qname ( COMMA qname )*
    ;
```

### `whereClause`

```antlr
whereClause
    : WHERE equalityConstraint ( COMMA equalityConstraint )*
    ;
```

### `equalityConstraint`

```antlr
equalityConstraint
    : type TILDE type
    ;
```

### `typeParams`

```antlr
typeParams
    : LBRACK typeParam ( COMMA typeParam )* RBRACK
    ;
```

### `typeParam`

```antlr
typeParam
    : ( name | UNDERSCORE ) ( COLON kind )?
    ;
```

### `typeArgs`

```antlr
typeArgs
    : LBRACK type ( COMMA type )* RBRACK
    ;
```

### `kind`

```antlr
kind
    : LPAREN kind RPAREN ( ARROW_WS kind )?
    | nameUppercase ( ARROW_WS kind )?
    ;
```

### `formalParams`

```antlr
formalParams
    : LPAREN ( formalParam ( COMMA formalParam )* )? RPAREN
    ;
```

### `formalParam`

```antlr
formalParam
    : variableName ( COLON typeAndEffect )?
    ;
```

### `typeAndEffect`

```antlr
typeAndEffect
    : type
    ;
```

### `type`

```antlr
type
    : ( NOT | TILDE | RVNOT ) type
    | type AND type
    | type OR type
    | type XOR type
    | type AMPERSAND type
    | type ( PLUS | MINUS ) type
    | type RVAND type
    | type ( RVADD | RVSUB ) type
    | <assoc=right> type ARROW_WS type
    | type BACKSLASH type
    | primaryType typeArgs*
    ;
```

### `primaryType`

```antlr
primaryType
    : qname
    | nameLowercase
    | nameMath
    | UNDERSCORE
    | STATIC_UPPER
    | UNIV
    | TRUE
    | FALSE
    | LPAREN ( type ( COMMA type )* )? RPAREN
    | LBRACE BAR RBRACE
    | LBRACE ( recordFieldType ( COMMA recordFieldType )* ( BAR type )? )? RBRACE
    | LBRACE type ( COMMA type )* RBRACE
    | HASH_LBRACE ( schemaTerm ( COMMA schemaTerm )* ( BAR name )? )? RBRACE
    | HASH_LPAREN ( schemaTerm ( COMMA schemaTerm )* ( BAR name )? )? RPAREN
    | HASH_BAR ( schemaTerm ( COMMA schemaTerm )* ( BAR name )? )? BAR_HASH
    | ANGLE_L qname ( COMMA qname )* ANGLE_R
    ;
```

### `recordFieldType`

```antlr
recordFieldType
    : nameLowercase EQUAL type
    ;
```

### `schemaTerm`

```antlr
schemaTerm
    : nameUppercase typeArgs
    | nameUppercase ( LPAREN ( type ( COMMA type )* ( SEMI type )? )? RPAREN )?
    ;
```

### `qname`

```antlr
qname
    : ( nameUppercase dot )* ( nameLowercase | nameUppercase | nameMath )
    ;
```

### `javaQname`

```antlr
javaQname
    : name ( dot name )*
    ;
```

### `dot`

```antlr
dot
    : DOT | DOT_WS
    ;
```

### `name`

```antlr
name
    : nameLowercase
    | nameUppercase
    | nameMath
    ;
```

### `variableName`

```antlr
variableName
    : nameLowercase
    | nameMath
    | UNDERSCORE
    ;
```

### `definitionName`

```antlr
definitionName
    : nameLowercase
    | nameUppercase
    | nameMath
    | genericOperator
    ;
```

### `nameLowercase`

```antlr
nameLowercase
    : NAME_LOWERCASE
    ;
```

### `nameUppercase`

```antlr
nameUppercase
    : NAME_UPPERCASE
    ;
```

### `nameMath`

```antlr
nameMath
    : NAME_MATH
    ;
```

### `genericOperator`

```antlr
genericOperator
    : GENERIC_OPERATOR
    | PLUS | MINUS | STAR | SLASH | BAR | AMPERSAND | CARET | EQUAL | EQUAL_EQUAL
    | BANG | BANG_EQUAL | ANGLE_L | ANGLE_R | ANGLE_L_EQUAL | ANGLE_R_EQUAL
    | ANGLED_EQUAL | ANGLED_PLUS | ARROW_THIN_L | ARROW_THICK_R | COLON_COLON_COLON
    ;
```

### `statement`

```antlr
statement
    : expr ( SEMI expr )* SEMI?
    ;
```

### `lambdaParams`

```antlr
lambdaParams
    : formalParams
    | variableName
    ;
```

### `argument`

```antlr
argument
    : expr ( EQUAL expr )?
    ;
```

### `block`

```antlr
block
    : LBRACE statement? RBRACE
    ;
```

### `matchRule`

```antlr
matchRule
    : CASE pattern ( IF expr )? ARROW_THICK_R statement COMMA?
    ;
```

### `catchRule`

```antlr
catchRule
    : CASE variableName COLON javaQname ARROW_THICK_R statement COMMA?
    ;
```

### `handlerRule`

```antlr
handlerRule
    : DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL statement COMMA?
    ;
```

### `selectRule`

```antlr
selectRule
    : CASE pattern ARROW_THIN_L expr ARROW_THICK_R statement COMMA?
    | CASE UNDERSCORE ARROW_THICK_R statement COMMA?
    ;
```

### `newBody`

```antlr
newBody
    : DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL statement
    | nameLowercase EQUAL expr COMMA?
    ;
```

### `forFragments`

```antlr
forFragments
    : LPAREN forFragment ( SEMI forFragment )* RPAREN
    ;
```

### `forFragment`

```antlr
forFragment
    : IF expr
    | LET pattern EQUAL expr
    | pattern ARROW_THIN_L expr
    ;
```

### `parFragment`

```antlr
parFragment
    : pattern ARROW_THIN_L expr
    ;
```

### `fixpointExpr`

```antlr
fixpointExpr
    : ( SOLVE | PSOLVE ) fixpointExpressions ( PROJECT qname ( COMMA qname )* )?
    | ( QUERY | PQUERY ) fixpointExpressions fixpointClause*
    | INJECT fixpointExpressions INTO predicateAndArity ( COMMA predicateAndArity )*
    ;
```

### `fixpointClause`

```antlr
fixpointClause
    : SELECT ( LPAREN ( expr ( COMMA expr )* )? RPAREN | expr )
    | FROM predicateAtom ( COMMA predicateAtom )*
    | WHERE expr
    | WITH LBRACE qname ( COMMA qname )* RBRACE
    ;
```

### `fixpointExpressions`

```antlr
fixpointExpressions
    : expr ( COMMA expr )*
    ;
```

### `predicateAndArity`

```antlr
predicateAndArity
    : nameUppercase SLASH INT_LITERAL
    ;
```

### `constraintSet`

```antlr
constraintSet
    : HASH_LBRACE datalogConstraint* RBRACE
    ;
```

### `datalogConstraint`

```antlr
datalogConstraint
    : predicateHead ( COLON_MINUS predicateBody ( COMMA predicateBody )* )? ( DOT_WS | DOT )
    ;
```

### `predicateHead`

```antlr
predicateHead
    : nameUppercase ( LPAREN ( expr ( COMMA expr )* ( SEMI expr )? )? RPAREN )?
    ;
```

### `predicateBody`

```antlr
predicateBody
    : IF LPAREN expr RPAREN
    | IF expr
    | LET ( LPAREN variableName ( COMMA variableName )* RPAREN | variableName ) EQUAL expr
    | predicateAtom
    ;
```

### `predicateAtom`

```antlr
predicateAtom
    : NOT? FIX? nameUppercase
( LPAREN ( pattern ( COMMA pattern )* ( SEMI pattern )? )? RPAREN )?
    ;
```

### `primaryExpr`

```antlr
primaryExpr
    : qname
    | INT_LITERAL
    | FLOAT_LITERAL
    | HEX_LITERAL
    | CHAR_LITERAL
    | REGEX_LITERAL
    | stringLiteral
    | DEBUG_INTERPOLATOR stringLiteral
    | HOLE_ANONYMOUS
    | HOLE_NAMED
    | HOLE_VARIABLE
    | BUILT_IN
    | TRUE
    | FALSE
    | NULL
    | STATIC_UPPER
    | STATIC_LOWER
    | UNDERSCORE
    | LPAREN ( argument ( COLON typeAndEffect )? ( COMMA argument )* )? RPAREN
    | LPAREN genericOperator RPAREN
    | constraintSet
    | HASH_LPAREN ( predicateParam ( COMMA predicateParam )* )? RPAREN ARROW_WS expr
    | collectionLiteral
    | recordOperation
    | block
    ;
```

### `predicateParam`

```antlr
predicateParam
    : nameUppercase ( LPAREN ( type ( COMMA type )* ( SEMI type )? )? RPAREN )?
    ;
```

### `collectionLiteral`

```antlr
collectionLiteral
    : ARRAY_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE ( AT expr )?
    | VECTOR_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | LIST_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | SET_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | MAP_HASH LBRACE ( mapEntry ( COMMA mapEntry )* )? RBRACE
    ;
```

### `mapEntry`

```antlr
mapEntry
    : expr ARROW_THICK_R expr
    ;
```

### `recordOperation`

```antlr
recordOperation
    : LBRACE ( recordOpField ( COMMA recordOpField )* ( BAR expr )? )? RBRACE
    ;
```

### `recordOpField`

```antlr
recordOpField
    : ( PLUS | MINUS )? nameLowercase ( EQUAL expr )?
    ;
```

### `stringLiteral`

```antlr
stringLiteral
    : STRING_START ( STRING_CONTENT | INTERPOLATION_START expr INTERPOLATION_END )* STRING_END
    ;
```

### `pattern`

```antlr
pattern
    : <assoc=right> pattern COLON_COLON pattern
    | primaryPattern
    ;
```

### `primaryPattern`

```antlr
primaryPattern
    : qname ( LPAREN ( pattern ( COMMA pattern )* )? RPAREN )?
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
    | NULL
    ;
```

### `tuplePattern`

```antlr
tuplePattern
    : LPAREN ( pattern ( COMMA pattern )* )? RPAREN
    ;
```

### `recordPattern`

```antlr
recordPattern
    : LBRACE ( recordFieldPattern ( COMMA recordFieldPattern )* ( BAR pattern )? )? RBRACE
    ;
```

### `recordFieldPattern`

```antlr
recordFieldPattern
    : nameLowercase ( EQUAL pattern )?
    ;
```

## Lexer rules

Keywords are omitted; see `fixtures/keywords.txt` for the 84.

### `HASH_LBRACE`

```antlr
HASH_LBRACE
    : '#{'
    ;
```

### `HASH_LPAREN`

```antlr
HASH_LPAREN
    : '#('
    ;
```

### `HASH_BAR`

```antlr
HASH_BAR
    : '#|'
    ;
```

### `BAR_HASH`

```antlr
BAR_HASH
    : '|#'
    ;
```

### `LPAREN`

```antlr
LPAREN
    : '('
    ;
```

### `RPAREN`

```antlr
RPAREN
    : ')'
    ;
```

### `LBRACK`

```antlr
LBRACK
    : '['
    ;
```

### `RBRACK`

```antlr
RBRACK
    : ']'
    ;
```

### `LBRACE`

```antlr
LBRACE
    : '{'
    ;
```

### `RBRACE`

```antlr
RBRACE
    : '}' { if (exitBrace())  }
    ;
```

### `COMMA`

```antlr
COMMA
    : ','
    ;
```

### `SEMI`

```antlr
SEMI
    : ';'
    ;
```

### `COLON_COLON_COLON`

```antlr
COLON_COLON_COLON
    : ':::'
    ;
```

### `COLON_COLON`

```antlr
COLON_COLON
    : '::'
    ;
```

### `COLON_MINUS`

```antlr
COLON_MINUS
    : ':-'
    ;
```

### `COLON`

```antlr
COLON
    : ':'
    ;
```

### `HASH`

```antlr
HASH
    : '#'
    ;
```

### `TILDE`

```antlr
TILDE
    : '~'
    ;
```

### `BACKTICK`

```antlr
BACKTICK
    : '`'
    ;
```

### `SLASH_TOKEN`

```antlr
SLASH_TOKEN
    : '/'
    ;
```

### `BACKSLASH_TOKEN`

```antlr
BACKSLASH_TOKEN
    : '\\'
    ;
```

### `ARROW`

```antlr
ARROW
    : '->'
    ;
```

### `DOT_TOKEN`

```antlr
DOT_TOKEN
    : '.'
    ;
```

### `OP_RUN`

```antlr
OP_RUN
    : '_'? [+\-*<>=!&|^$]+
    ;
```

### `HOLE_ANONYMOUS`

```antlr
HOLE_ANONYMOUS
    : '???'
    ;
```

### `HOLE_NAMED`

```antlr
HOLE_NAMED
    : '?' [a-zA-Z] [a-zA-Z0-9_!$]*
    ;
```

### `HOLE_VARIABLE`

```antlr
HOLE_VARIABLE
    : '_'? [a-zA-Z] [a-zA-Z0-9_!$]* '?'
    ;
```

### `BUILT_IN`

```antlr
BUILT_IN
    : '%%' [A-Z0-9_]* '%%'
    ;
```

### `ANNOTATION`

```antlr
ANNOTATION
    : '@' [a-zA-Z]+
    ;
```

### `AT`

```antlr
AT
    : '@'
    ;
```

### `DOLLAR_NAME`

```antlr
DOLLAR_NAME
    : '$' [a-zA-Z] [a-zA-Z0-9_!$]*
    ;
```

### `NAME_LOWERCASE`

```antlr
NAME_LOWERCASE
    : '_'? [a-z] [a-zA-Z0-9_!$]*
    ;
```

### `NAME_UPPERCASE`

```antlr
NAME_UPPERCASE
    : '_'? [A-Z] [a-zA-Z0-9_!$]*
    ;
```

### `NAME_MATH`

```antlr
NAME_MATH
    : '_'? [\u2200-\u22FF]+
    ;
```

### `UNDERSCORE`

```antlr
UNDERSCORE
    : '_'
    ;
```

### `DOLLAR`

```antlr
DOLLAR
    : '$'
    ;
```

### `HEX_LITERAL`

```antlr
HEX_LITERAL
    : '0x' HEXDIGITS INT_SUFFIX?
    ;
```

### `FLOAT_LITERAL`

```antlr
FLOAT_LITERAL
    : DIGITS ( '.' DIGITS EXPONENT? | EXPONENT ) FLOAT_SUFFIX?
    | DIGITS FLOAT_SUFFIX
    ;
```

### `INT_LITERAL`

```antlr
INT_LITERAL
    : DIGITS INT_SUFFIX?
    ;
```

### `CHAR_LITERAL`

```antlr
CHAR_LITERAL
    : '\'' ( '\\' . | ~['\\] )*? '\''
    ;
```

### `REGEX_LITERAL`

```antlr
REGEX_LITERAL
    : 'regex"' ( '\\' . | ~["\\\r\n] )* '"'
    ;
```

### `STRING_START`

```antlr
STRING_START
    : '"'
    ;
```

### `DOC_COMMENT`

```antlr
DOC_COMMENT
    : '
    ;
```

### `LINE_COMMENT`

```antlr
LINE_COMMENT
    : '
    ;
```

### `BLOCK_COMMENT`

```antlr
BLOCK_COMMENT
    : '/*' ( BLOCK_COMMENT | . )*? '*/'
    ;
```

### `WS`

```antlr
WS
    : [ \t\r\n\u000B\f\u001C-\u001F\u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]+
    ;
```

### `STRING_END`

```antlr
STRING_END
    : '"'
    ;
```

### `INTERPOLATION_START`

```antlr
INTERPOLATION_START
    : '${'
    ;
```

### `STRING_CONTENT`

```antlr
STRING_CONTENT
    : ( ~["\\$\r\n] | '\\' . )+
    ;
```

### `STRING_DOLLAR`

```antlr
STRING_DOLLAR
    : '$'
    ;
```

### `STRING_NEWLINE`

```antlr
STRING_NEWLINE
    : [\r\n] , popMode
    ;
```
