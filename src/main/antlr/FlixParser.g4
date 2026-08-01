parser grammar FlixParser;

options {
    tokenVocab = FlixLexer;
}

// ---------------------------------------------------------------------
// Entry Point
// ---------------------------------------------------------------------

compilationUnit
    : usesOrImports* declaration* EOF
    ;

usesOrImports
    : useClause
    | importClause
    ;

useClause
    : USE qname ( dot LBRACE useName ( COMMA useName )* COMMA? RBRACE )?
    ;

useName
    : name
    | genericOperator
    | HASH
    ;

importClause
    : IMPORT qname ( AS nameUppercase )?
    ;

// ---------------------------------------------------------------------
// Declarations
// ---------------------------------------------------------------------

declaration
    : defDeclaration
    | enumDeclaration
    | structDeclaration
    | aliasDeclaration
    | classDeclaration
    | instanceDeclaration
    | effDeclaration
    | modDeclaration
    | lawDeclaration
    ;

modDeclaration
    : modifier* ( MOD | DEF? NAMESPACE ) qname? LBRACE ( usesOrImports | declaration )* RBRACE
    ;

defDeclaration
    : annotation* modifier* DEF ( nameLowercase | genericOperator ) typeParams? formalParams ( COLON type )? ( ARROW_THICK_R expr | EQUAL expr )?
    ;

lawDeclaration
    : annotation* modifier* LAW nameLowercase COLON type EQUAL expr
    ;

enumDeclaration
    : annotation* modifier* ENUM nameUppercase typeParams? ( LPAREN enumCaseList RPAREN )? ( LBRACE enumCaseList RBRACE )?
    ;

enumCaseList
    : enumCase ( COMMA enumCase )* COMMA?
    ;

enumCase
    : CASE nameUppercase ( LPAREN type ( COMMA type )* RPAREN )?
    ;

structDeclaration
    : annotation* modifier* STRUCT nameUppercase typeParams? LBRACE structFieldList? RBRACE
    ;

structFieldList
    : structField ( COMMA structField )* COMMA?
    ;

structField
    : nameLowercase COLON type
    ;

aliasDeclaration
    : annotation* modifier* TYPE ALIAS nameUppercase typeParams? EQUAL type
    ;

classDeclaration
    : annotation* modifier* CLASS nameUppercase typeParams? ( WHERE? LBRACE sigOrDef* RBRACE )?
    ;

sigOrDef
    : defDeclaration
    | sigDeclaration
    ;

sigDeclaration
    : annotation* modifier* DEF ( nameLowercase | genericOperator ) typeParams? formalParams COLON type
    ;

instanceDeclaration
    : annotation* modifier* INSTANCE nameUppercase typeArgs? ( WHERE? LBRACE defDeclaration* RBRACE )?
    ;

effDeclaration
    : annotation* modifier* EFF nameUppercase typeParams? LBRACE opDeclaration* RBRACE
    ;

opDeclaration
    : annotation* modifier* DEF ( nameLowercase | genericOperator ) typeParams? formalParams COLON type
    ;

// ---------------------------------------------------------------------
// Parameters & Modifiers
// ---------------------------------------------------------------------

annotation
    : AT nameUppercase ( LPAREN ( expr ( COMMA expr )* )? RPAREN )?
    ;

modifier
    : PUB | SEALED | REDEF | OVERRIDE | MUT
    ;

typeParams
    : LBRACK typeParam ( COMMA typeParam )* RBRACK
    ;

typeParam
    : nameLowercase ( COLON nameUppercase )?
    ;

typeArgs
    : LBRACK type ( COMMA type )* RBRACK
    ;

formalParams
    : LPAREN ( formalParam ( COMMA formalParam )* )? RPAREN
    ;

formalParam
    : pattern ( COLON type )?
    | nameLowercase COLON type
    ;

// ---------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------

type
    : type ( ARROW_WS | ARROW_TIGHT | ARROW_THICK_R ) type
    | primaryType typeArgs?
    ;

primaryType
    : qname
    | nameLowercase
    | LPAREN ( type ( COMMA type )* )? RPAREN
    | LBRACE ( recordField ( COMMA recordField )* )? RBRACE
    | HASH LBRACE ( schemaRow ( COMMA schemaRow )* )? RBRACE
    ;

recordField
    : nameLowercase COLON type
    ;

schemaRow
    : nameUppercase LPAREN ( type ( COMMA type )* )? RPAREN
    ;

// ---------------------------------------------------------------------
// Names & Qualifiers
// ---------------------------------------------------------------------

qname
    : name ( dot name )*
    ;

dot
    : DOT | DOT_WS
    ;

name
    : nameLowercase
    | nameUppercase
    | nameMath
    ;

nameLowercase : NAME_LOWERCASE ;
nameUppercase : NAME_UPPERCASE ;
nameMath      : NAME_MATH ;
genericOperator
    : GENERIC_OPERATOR
    | PLUS | MINUS | STAR | BAR | AMPERSAND | CARET | EQUAL | EQUAL_EQUAL
    | BANG | BANG_EQUAL | ANGLE_L | ANGLE_R | ANGLE_L_EQUAL | ANGLE_R_EQUAL
    | ANGLED_EQUAL | ANGLED_PLUS | ARROW_THIN_L | ARROW_THICK_R
    ;

// ---------------------------------------------------------------------
// Expressions & Patterns (Baseline)
// ---------------------------------------------------------------------

expr
    : primaryExpr
    ;

primaryExpr
    : qname
    | INT_LITERAL
    | FLOAT_LITERAL
    | HEX_LITERAL
    | CHAR_LITERAL
    | STRING_START STRING_CONTENT* STRING_END
    | TRUE
    | FALSE
    | NULL
    | LPAREN ( expr ( COMMA expr )* )? RPAREN
    | LBRACE ( expr ( COMMA expr )* )? RBRACE
    ;

pattern
    : primaryPattern
    ;

primaryPattern
    : qname ( LPAREN ( pattern ( COMMA pattern )* )? RPAREN )?
    | nameLowercase
    | UNDERSCORE
    | INT_LITERAL
    | CHAR_LITERAL
    | TRUE
    | FALSE
    | LPAREN ( pattern ( COMMA pattern )* )? RPAREN
    ;
