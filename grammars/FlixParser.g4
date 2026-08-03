parser grammar FlixParser;

options {
    tokenVocab = FlixLexer;
}

// =====================================================================
// Entry point
//
// Uses and imports form a strict prefix of a compilation unit, then
// declarations follow. Datalog constraints are NOT declarations: they live
// inside `#{ ... }` expressions. Listing them here made every unmatched
// declaration retry as a constraint and report the constraint's expected set.
// =====================================================================

compilationUnit
    : usesOrImports* declaration* EOF
    ;

usesOrImports
    : useClause SEMI?
    | importClause SEMI?
    ;

useClause
    : USE qname ( dot LBRACE useName ( COMMA useName )* COMMA? RBRACE )?
    ;

importClause
    : IMPORT javaQname ( dot LBRACE useName ( COMMA useName )* COMMA? RBRACE )?
    ;

useName
    : name ( ARROW_THICK_R name )?
    | genericOperator
    ;

// =====================================================================
// Declarations
//
// Annotations and modifiers are parsed as unconstrained repetitions. Which
// combinations are legal is a validation concern, not a syntactic one: the
// reference parses `modifier*` and rejects duplicates and illegal combinations
// in a later phase.
// =====================================================================

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

declPrefix
    : annotation* modifier*
    ;

modDeclaration
    : declPrefix MOD qname LBRACE ( usesOrImports | declaration )* RBRACE
    ;

defDeclaration
    : declPrefix ( DEF | REDEF ) definitionName typeParams? formalParams
      ( COLON typeAndEffect )? withClause? whereClause? ( EQUAL statement )?
    ;

lawDeclaration
    : declPrefix LAW definitionName COLON FORALL typeParams? formalParams?
      withClause? whereClause? ( EQUAL? statement )?
    ;

enumDeclaration
    : declPrefix RESTRICTABLE? ENUM nameUppercase typeParams?
      ( LPAREN type ( COMMA type )* RPAREN )?
      derivations?
      ( LBRACE enumCase* RBRACE )?
    ;

// Deliberately permissive: the reference accepts `case A, B, C`, `case A, case B,`
// and `case A case B` alike, and the enum body itself is optional.
enumCase
    : COMMA? CASE? nameUppercase ( LPAREN type ( COMMA type )* RPAREN )? COMMA?
    ;

structDeclaration
    : declPrefix STRUCT nameUppercase typeParams?
      ( LBRACE ( structField ( COMMA structField )* COMMA? )? RBRACE )?
    ;

structField
    : modifier* nameLowercase COLON type
    ;

traitDeclaration
    : declPrefix TRAIT nameUppercase typeParams? withClause?
      ( LBRACE traitMember* RBRACE )?
    ;

traitMember
    : defDeclaration
    | lawDeclaration
    | assocTypeDeclaration
    ;

instanceDeclaration
    : declPrefix INSTANCE qname ( LBRACK type RBRACK )? withClause? whereClause?
      ( LBRACE instanceMember* RBRACE )?
    ;

instanceMember
    : defDeclaration
    | assocTypeDeclaration
    ;

assocTypeDeclaration
    : declPrefix TYPE nameUppercase ( typeParams | typeArgs )? ( COLON kind )? ( EQUAL type )?
    ;

aliasDeclaration
    : declPrefix TYPE ALIAS nameUppercase typeParams? EQUAL type
    ;

effDeclaration
    : declPrefix EFF nameUppercase typeParams? ( LBRACE opDeclaration* RBRACE )?
    ;

opDeclaration
    : declPrefix DEF definitionName typeParams? formalParams? COLON typeAndEffect withClause?
    ;

// =====================================================================
// Declaration fragments
// =====================================================================

annotation
    : ANNOTATION ( LPAREN ( expr ( COMMA expr )* )? RPAREN )?
    ;

modifier
    : PUB | SEALED | LAWFUL | MUT
    ;

// `with` introduces trait constraints on definitions and derivations on enums;
// the reference chooses by caller context rather than by lookahead.
withClause
    : WITH constraint ( COMMA constraint )*
    ;

constraint
    : qname ( LBRACK type RBRACK )?
    ;

derivations
    : WITH qname ( COMMA qname )*
    ;

whereClause
    : WHERE equalityConstraint ( COMMA equalityConstraint )*
    ;

equalityConstraint
    : type TILDE type
    ;

typeParams
    : LBRACK typeParam ( COMMA typeParam )* RBRACK
    ;

typeParam
    : ( name | UNDERSCORE ) ( COLON kind )?
    ;

typeArgs
    : LBRACK type ( COMMA type )* RBRACK
    ;

kind
    : LPAREN kind RPAREN ( ARROW_WS kind )?
    | nameUppercase ( ARROW_WS kind )?
    ;

formalParams
    : LPAREN ( formalParam ( COMMA formalParam )* )? RPAREN
    ;

// The type ascription is optional everywhere; the reference makes its presence
// required, optional or forbidden depending on context and validates later.
formalParam
    : variableName ( COLON typeAndEffect )?
    ;

// =====================================================================
// Types
//
// TYPE_OP_PRECEDENCE lists operators LOOSEST first: `->`, rvadd/rvsub, rvand,
// `+`/`-`, `&`, xor, or, and, then unary. Equal precedence is left-associative
// and the arrow is right-associative. Its docstring claims the opposite, as
// does the expression table's; both are inverted.
// =====================================================================

// The effect annotation binds looser than everything, including the arrow:
// in `a -> b \ ef` the effect belongs to the arrow type as a whole.
typeAndEffect
    : type
    ;

// ANTLR assigns precedence by alternative order, earliest tightest, so this
// table is the reverse of TYPE_OP_PRECEDENCE, whose index 0 is the LOOSEST.
type
    : ( NOT | TILDE | RVNOT ) type        # UnaryType
    | type AND type                       # AndType
    | type OR type                        # OrType
    | type XOR type                       # XorType
    | type AMPERSAND type                 # EffectIntersectionType
    | type ( PLUS | MINUS ) type          # EffectSumType
    | type RVAND type                     # RvAndType
    | type ( RVADD | RVSUB ) type         # RvAddSubType
    | <assoc=right> type ARROW_WS type    # ArrowType
    | type BACKSLASH type                 # EffectAnnotatedType
    | primaryType typeArgs*               # ApplyType
    ;

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

// Record types use `=`, never `:`. That is what keeps `{ a = t }` distinct
// from a block and from an effect set.
recordFieldType
    : nameLowercase EQUAL type
    ;

schemaTerm
    : nameUppercase typeArgs
    | nameUppercase ( LPAREN ( type ( COMMA type )* ( SEMI type )? )? RPAREN )?
    ;

// =====================================================================
// Names
// =====================================================================

// Stops after the first lowercase segment, mirroring Parser2.scala's `nameAllowQualified`'s
// default `tail = NAME_LOWERCASE`: a namespace-qualified reference like `Foo.Bar.baz.qux` stops
// after `baz`, leaving `.qux` unconsumed for the postfix chain to turn into GetField/InvokeMethod.
// Module/type/trait/effect names are conventionally all-uppercase multi-segment paths, so this
// never stops early for them -- it is only observably different from unrestricted consumption when
// a lowercase segment is followed by more segments, which is exactly the case the reference itself
// singles out with `tail`. Was `name ( dot name )*` (unrestricted) until this fix: 1,243
// expression-position qname nodes had the wrong shape as a result (a flat qname where the
// reference produces a qname stopped at the first lowercase segment plus a postfix chain).
qname
    : ( nameUppercase dot )* ( nameLowercase | nameUppercase | nameMath )
    ;

// Unrestricted consumption -- `nameAllowQualified(..., tail = Set())` in the reference, used only
// for Java package/class paths (`java.util.List`), which have lowercase package segments that must
// NOT trigger qname's early stop. Mirrors Parser2.scala's `iimport()` (:911) and `catchRule()`
// (:2811) exactly; every other qname position uses the tail-aware `qname` above, matching the
// reference's own near-universal default.
javaQname
    : name ( dot name )*
    ;

dot : DOT | DOT_WS ;

name
    : nameLowercase
    | nameUppercase
    | nameMath
    ;

variableName
    : nameLowercase
    | nameMath
    | UNDERSCORE
    ;

definitionName
    : nameLowercase
    | nameUppercase
    | nameMath
    | genericOperator
    ;

nameLowercase : NAME_LOWERCASE ;
nameUppercase : NAME_UPPERCASE ;
nameMath      : NAME_MATH ;

genericOperator
    : GENERIC_OPERATOR
    | PLUS | MINUS | STAR | SLASH | BAR | AMPERSAND | CARET | EQUAL | EQUAL_EQUAL
    | BANG | BANG_EQUAL | ANGLE_L | ANGLE_R | ANGLE_L_EQUAL | ANGLE_R_EQUAL
    | ANGLED_EQUAL | ANGLED_PLUS | ARROW_THIN_L | ARROW_THICK_R | COLON_COLON_COLON
    ;

// =====================================================================
// Statements and expressions
//
// Precedence is transcribed from Parser2.Op.precedence, whose docstring is
// inverted: a LARGER number binds TIGHTER. ANTLR assigns precedence by
// alternative order, earliest tightest, so the table below reads from level 14
// (`not`) down to level 0 (`instanceof`).
//
// Note that the unary prefixes are NOT all tightest: `discard` is level 9,
// below the backtick infix at 10 and user-defined operators at 11.
// =====================================================================

statement
    : expr ( SEMI expr )* SEMI?
    ;

expr
    // --- postfix chain, tighter than every operator ---------------------
    : expr LPAREN ( argument ( COMMA argument )* )? RPAREN  # ApplyExpr
    | expr dot nameLowercase
      ( LPAREN ( argument ( COMMA argument )* )? RPAREN )?  # FieldOrMethodExpr
    | expr HASH nameLowercase                               # RecordSelectExpr
    | expr ARROW_TIGHT nameLowercase ( EQUAL expr )?        # StructFieldExpr
    | expr LBRACK expr RBRACK ( EQUAL expr )?               # IndexExpr
    // --- unary and binary operators, tightest first ---------------------
    | NOT expr                                              # NotExpr
    | ( PLUS | MINUS ) expr                                 # SignExpr
    | ( LAZY | FORCE ) expr                                 # LazyForceExpr
    | expr ( GENERIC_OPERATOR | nameMath ) expr             # UserOpExpr
    | expr BACKTICK qname BACKTICK expr                     # InfixCallExpr
    | DISCARD expr                                          # DiscardExpr
    | expr ANGLED_PLUS expr                                 # AngledPlusExpr
    | expr ( STAR | SLASH ) expr                            # MultExpr
    | expr ( PLUS | MINUS ) expr                            # AddExpr
    | <assoc=right> expr ( COLON_COLON | COLON_COLON_COLON ) expr # ConsExpr
    | expr ( ANGLE_L | ANGLE_R | ANGLE_L_EQUAL | ANGLE_R_EQUAL ) expr # CompareExpr
    | expr ( EQUAL_EQUAL | BANG_EQUAL | ANGLED_EQUAL ) expr # EqualityExpr
    | expr AND expr                                         # AndExpr
    | expr OR expr                                          # OrExpr
    | expr INSTANCEOF qname                                 # InstanceOfExpr
    // --- forms whose trailing expression extends greedily ---------------
    | lambdaParams ARROW_WS expr                            # LambdaExpr
    | IF LPAREN expr RPAREN expr ( ELSE expr )?             # IfExpr
    | LET pattern ( COLON typeAndEffect )? EQUAL statement  # LetExpr
    | DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL statement # LocalDefExpr
    | REGION nameLowercase block                            # RegionExpr
    | MATCH expr LBRACE matchRule* RBRACE                   # MatchExpr
    | MATCH pattern ARROW_WS expr                           # MatchLambdaExpr
    | EMATCH expr LBRACE matchRule* RBRACE                  # EMatchExpr
    | ( CHOOSE | CHOOSE_STAR ) expr LBRACE matchRule* RBRACE # ChooseExpr
    | XVAR qname ( LPAREN ( expr ( COMMA expr )* )? RPAREN )? # ExtTagExpr
    | ( OPEN_VARIANT | OPEN_VARIANT_AS ) qname expr?        # OpenVariantExpr
    | FOREACH forFragments expr                             # ForeachExpr
    | ( FORA | FORM ) forFragments YIELD expr               # ForYieldExpr
    | TRY expr ( CATCH LBRACE catchRule* RBRACE )*          # TryCatchExpr
    | RUN expr ( WITH expr )*                               # RunWithExpr
    | HANDLER qname ( LBRACE handlerRule* RBRACE )?         # HandlerExpr
    | THROW expr                                            # ThrowExpr
    | UNSAFE type ( AS type )? block                        # UnsafeExpr
    | SPAWN expr ( AT expr )?                               # SpawnExpr
    | PAR LPAREN parFragment ( SEMI parFragment )* RPAREN YIELD expr # ParYieldExpr
    | SELECT LBRACE selectRule* RBRACE                      # SelectExpr
    | ( CHECKED_CAST | CHECKED_ECAST ) LPAREN expr RPAREN   # CheckedCastExpr
    | UNCHECKED_CAST LPAREN expr AS typeAndEffect RPAREN    # UncheckedCastExpr
    | NEW qname typeArgs? ( AT expr )?
      ( LBRACE newBody* RBRACE | LPAREN ( expr ( COMMA expr )* )? RPAREN )? # NewExpr
    | SUPER ( dot nameLowercase )? LPAREN ( expr ( COMMA expr )* )? RPAREN # SuperExpr
    | useClause SEMI expr                                   # UseExpr
    | fixpointExpr                                          # FixpointExpression
    | primaryExpr                                           # PrimaryExpression
    ;

lambdaParams
    : formalParams
    | variableName
    ;

argument
    : expr ( EQUAL expr )?
    ;

block
    : LBRACE statement? RBRACE
    ;

matchRule
    : CASE pattern ( IF expr )? ARROW_THICK_R statement COMMA?
    ;

catchRule
    : CASE variableName COLON javaQname ARROW_THICK_R statement COMMA?
    ;

handlerRule
    : DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL statement COMMA?
    ;

selectRule
    : CASE pattern ARROW_THIN_L expr ARROW_THICK_R statement COMMA?
    | CASE UNDERSCORE ARROW_THICK_R statement COMMA?
    ;

newBody
    : DEF definitionName formalParams ( COLON typeAndEffect )? EQUAL statement
    | nameLowercase EQUAL expr COMMA?
    ;

forFragments
    : LPAREN forFragment ( SEMI forFragment )* RPAREN
    ;

forFragment
    : IF expr
    | LET pattern EQUAL expr
    | pattern ARROW_THIN_L expr
    ;

parFragment
    : pattern ARROW_THIN_L expr
    ;

// =====================================================================
// Datalog and fixpoint
//
// All five fixpoint keywords share one `fixpointExpressions` rule. Repeating a
// greedy comma-separated expression list at each site is what makes `,`
// ambiguous across the whole language; sharing it confines the cost.
// =====================================================================

fixpointExpr
    : ( SOLVE | PSOLVE ) fixpointExpressions ( PROJECT qname ( COMMA qname )* )?
    | ( QUERY | PQUERY ) fixpointExpressions fixpointClause*
    | INJECT fixpointExpressions INTO predicateAndArity ( COMMA predicateAndArity )*
    ;

// Clause order is deliberately unconstrained. The reference fixes the order,
// but three chained optionals after a greedy expression list is the most
// expensive shape in the grammar; ordering is checked in validation instead.
fixpointClause
    : SELECT ( LPAREN ( expr ( COMMA expr )* )? RPAREN | expr )
    | FROM predicateAtom ( COMMA predicateAtom )*
    | WHERE expr
    | WITH LBRACE qname ( COMMA qname )* RBRACE
    ;

fixpointExpressions
    : expr ( COMMA expr )*
    ;

predicateAndArity
    : nameUppercase SLASH INT_LITERAL
    ;

constraintSet
    : HASH_LBRACE datalogConstraint* RBRACE
    ;

// A constraint is terminated by a dot followed by whitespace, which is a
// distinct token from the qualified-name separator.
datalogConstraint
    : predicateHead ( COLON_MINUS predicateBody ( COMMA predicateBody )* )? ( DOT_WS | DOT )
    ;

predicateHead
    : nameUppercase ( LPAREN ( expr ( COMMA expr )* ( SEMI expr )? )? RPAREN )?
    ;

predicateBody
    : IF LPAREN expr RPAREN
    | IF expr
    | LET ( LPAREN variableName ( COMMA variableName )* RPAREN | variableName ) EQUAL expr
    | predicateAtom
    ;

predicateAtom
    : NOT? FIX? nameUppercase
      ( LPAREN ( pattern ( COMMA pattern )* ( SEMI pattern )? )? RPAREN )?
    ;

// =====================================================================
// Primary expressions
// =====================================================================

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

predicateParam
    : nameUppercase ( LPAREN ( type ( COMMA type )* ( SEMI type )? )? RPAREN )?
    ;

collectionLiteral
    : ARRAY_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE ( AT expr )?
    | VECTOR_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | LIST_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | SET_HASH LBRACE ( expr ( COMMA expr )* )? RBRACE
    | MAP_HASH LBRACE ( mapEntry ( COMMA mapEntry )* )? RBRACE
    ;

mapEntry
    : expr ARROW_THICK_R expr
    ;

// `{}` is an empty record operation; a non-empty brace is a block unless the
// first two tokens look like a record field or a record extension/restriction.
recordOperation
    : LBRACE ( recordOpField ( COMMA recordOpField )* ( BAR expr )? )? RBRACE
    ;

recordOpField
    : ( PLUS | MINUS )? nameLowercase ( EQUAL expr )?
    ;

stringLiteral
    : STRING_START ( STRING_CONTENT | INTERPOLATION_START expr INTERPOLATION_END )* STRING_END
    ;

// =====================================================================
// Patterns
// =====================================================================

pattern
    : <assoc=right> pattern COLON_COLON pattern # ConsPattern
    | primaryPattern                            # PrimaryPat
    ;

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

tuplePattern
    : LPAREN ( pattern ( COMMA pattern )* )? RPAREN
    ;

recordPattern
    : LBRACE ( recordFieldPattern ( COMMA recordFieldPattern )* ( BAR pattern )? )? RBRACE
    ;

recordFieldPattern
    : nameLowercase ( EQUAL pattern )?
    ;
