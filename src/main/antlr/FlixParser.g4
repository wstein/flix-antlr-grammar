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
// Expressions (Precedence: Highest / Tightest at top)
// ---------------------------------------------------------------------

expr
    : primaryExpr                                            # PrimaryExpression
    | expr dot nameLowercase                                 # FieldSelectExpr
    | expr ARROW_TIGHT nameLowercase                         # FieldSelectTightExpr
    | expr LPAREN ( argument ( COMMA argument )* )? RPAREN   # ApplyExpr
    | ( BANG | MINUS | NOT | MUT | FORCE | LAZY ) expr        # PrefixExpr
    | expr ( GENERIC_OPERATOR | nameMath ) expr              # UserOpExpr
    | BACKTICK nameLowercase BACKTICK expr                   # InfixCallExpr
    | DISCARD expr                                           # DiscardExpr
    | expr ( STAR | MOD ) expr                               # MultExpr
    | expr ( PLUS | MINUS ) expr                             # AddExpr
    | <assoc=right> expr ( COLON_COLON | COLON_COLON_COLON ) expr # ConsExpr
    | expr ( EQUAL_EQUAL | BANG_EQUAL | ANGLE_L | ANGLE_R | ANGLE_L_EQUAL | ANGLE_R_EQUAL | ANGLED_EQUAL | ANGLED_PLUS ) expr # CompareExpr
    | expr ( OR | AND ) expr                                 # LogicalExpr
    | REF expr                                               # RefExpr
    | DEREF expr                                             # DerefExpr
    | SPAWN expr                                             # SpawnExpr
    | PAR LPAREN expr ( COMMA expr )* RPAREN                 # ParExpr
    | lambdaParams ( ARROW_WS | ARROW_TIGHT ) expr           # LambdaExpr
    | IF LPAREN expr RPAREN expr ( ELSE expr )?              # IfExpr
    | MATCH expr LBRACE matchCase+ RBRACE                    # MatchExpr
    | LET pattern ( COLON type )? EQUAL expr SEMI expr       # LetExpr
    ;

lambdaParams
    : formalParams
    | nameLowercase
    ;

argument
    : expr
    | nameLowercase EQUAL expr
    ;

matchCase
    : CASE pattern ( IF expr )? ARROW_THICK_R expr
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
    | LBRACE ( recordOpField ( COMMA recordOpField )* ( BAR expr )? | expr ( SEMI expr )* )? RBRACE
    | LBRACK ( expr ( COMMA expr )* )? RBRACK
    ;

recordOpField
    : ( PLUS | MINUS )? nameLowercase ( EQUAL expr | COLON type )?
    ;

// ---------------------------------------------------------------------
// Patterns
// ---------------------------------------------------------------------

pattern
    : pattern ( COLON_COLON | COLON_COLON_COLON ) pattern # ConsPattern
    | primaryPattern                                      # PrimaryPat
    ;

primaryPattern
    : qname ( LPAREN ( pattern ( COMMA pattern )* )? RPAREN )?
    | recordPattern
    | tuplePattern
    | nameLowercase
    | UNDERSCORE
    | INT_LITERAL
    | FLOAT_LITERAL
    | CHAR_LITERAL
    | STRING_START STRING_CONTENT* STRING_END
    | TRUE
    | FALSE
    ;

tuplePattern
    : LPAREN ( pattern ( COMMA pattern )* )? RPAREN
    ;

recordPattern
    : LBRACE ( recordFieldPattern ( COMMA recordFieldPattern )* ( BAR pattern )? )? RBRACE
    ;

recordFieldPattern
    : nameLowercase ( EQUAL pattern )?
    ;
