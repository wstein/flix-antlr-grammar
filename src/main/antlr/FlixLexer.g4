lexer grammar FlixLexer;

options {
    superClass = FlixLexerBase;
}

tokens {
    // Whitespace-sensitive tokens classified by FlixLexerBase
    ARROW_WS,
    ARROW_TIGHT,
    DOT,
    DOT_WS,
    FREE_DOT,

    // Reserved operators reclassified by FlixLexerBase.classifyOperator()
    BANG,
    BANG_EQUAL,
    AMPERSAND,
    STAR,
    PLUS,
    MINUS,
    ANGLE_L,
    ANGLED_PLUS,
    ARROW_THIN_L,
    ANGLE_L_EQUAL,
    ANGLED_EQUAL,
    EQUAL,
    EQUAL_EQUAL,
    ARROW_THICK_R,
    ANGLE_R,
    ANGLE_R_EQUAL,
    CARET,
    BAR,
    GENERIC_OPERATOR,

    // String interpolation token emitted when closing brace closes interpolation
    INTERPOLATION_END
}

channels {
    COMMENTS,
    DOC_COMMENTS
}

// ---------------------------------------------------------------------
// Keywords (86 keywords, each with !isNameCharFollow() tail guard)
// ---------------------------------------------------------------------

ALIAS            : 'alias' { !isNameCharFollow() }? ;
AND              : 'and' { !isNameCharFollow() }? ;
AS               : 'as' { !isNameCharFollow() }? ;
ASSERT           : 'assert' { !isNameCharFollow() }? ;
ARRAY_HASH       : 'Array#' { !isNameCharFollow() }? ;
LIST_HASH        : 'List#' { !isNameCharFollow() }? ;
MAP_HASH         : 'Map#' { !isNameCharFollow() }? ;
SET_HASH         : 'Set#' { !isNameCharFollow() }? ;
VECTOR_HASH      : 'Vector#' { !isNameCharFollow() }? ;
BOOL             : 'bool' { !isNameCharFollow() }? ;
BUILT_IN         : 'built_in' { !isNameCharFollow() }? ;
CASE             : 'case' { !isNameCharFollow() }? ;
CATCH            : 'catch' { !isNameCharFollow() }? ;
CHAN             : 'chan' { !isNameCharFollow() }? ;
CHAR             : 'char' { !isNameCharFollow() }? ;
CHOOSE           : 'choose' { !isNameCharFollow() }? ;
CHOOSE_STAR      : 'choose*' { !isNameCharFollow() }? ;
CLASS            : 'class' { !isNameCharFollow() }? ;
DEF              : 'def' { !isNameCharFollow() }? ;
DEREF            : 'deref' { !isNameCharFollow() }? ;
DISCARD          : 'discard' { !isNameCharFollow() }? ;
DO               : 'do' { !isNameCharFollow() }? ;
EFF              : 'eff' { !isNameCharFollow() }? ;
ELSE             : 'else' { !isNameCharFollow() }? ;
ENUM             : 'enum' { !isNameCharFollow() }? ;
FALSE            : 'false' { !isNameCharFollow() }? ;
FIX              : 'fix' { !isNameCharFollow() }? ;
FLOAT32          : 'float32' { !isNameCharFollow() }? ;
FLOAT64          : 'float64' { !isNameCharFollow() }? ;
FOR              : 'for' { !isNameCharFollow() }? ;
FORALL           : 'forall' { !isNameCharFollow() }? ;
FORCE            : 'force' { !isNameCharFollow() }? ;
FROM             : 'from' { !isNameCharFollow() }? ;
GET              : 'get' { !isNameCharFollow() }? ;
IF               : 'if' { !isNameCharFollow() }? ;
IMPORT           : 'import' { !isNameCharFollow() }? ;
IN               : 'in' { !isNameCharFollow() }? ;
INJECT           : 'inject' { !isNameCharFollow() }? ;
INSTANCE         : 'instance' { !isNameCharFollow() }? ;
INT8             : 'int8' { !isNameCharFollow() }? ;
INT16            : 'int16' { !isNameCharFollow() }? ;
INT32            : 'int32' { !isNameCharFollow() }? ;
INT64            : 'int64' { !isNameCharFollow() }? ;
INSTANCEOF       : 'instanceof' { !isNameCharFollow() }? ;
INTO             : 'into' { !isNameCharFollow() }? ;
LAW              : 'law' { !isNameCharFollow() }? ;
LAZY             : 'lazy' { !isNameCharFollow() }? ;
LET              : 'let' { !isNameCharFollow() }? ;
MATCH            : 'match' { !isNameCharFollow() }? ;
MOD              : 'mod' { !isNameCharFollow() }? ;
NAMESPACE        : 'namespace' { !isNameCharFollow() }? ;
MUT              : 'mut' { !isNameCharFollow() }? ;
NEW              : 'new' { !isNameCharFollow() }? ;
NOT              : 'not' { !isNameCharFollow() }? ;
NULL             : 'null' { !isNameCharFollow() }? ;
OP               : 'op' { !isNameCharFollow() }? ;
OPEN             : 'open' { !isNameCharFollow() }? ;
OPEN_VARIANT     : 'open_variant' { !isNameCharFollow() }? ;
OPEN_VARIANT_AS  : 'open_variant_as' { !isNameCharFollow() }? ;
OR               : 'or' { !isNameCharFollow() }? ;
OVERRIDE         : 'override' { !isNameCharFollow() }? ;
PAR              : 'par' { !isNameCharFollow() }? ;
PQUERY           : 'pquery' { !isNameCharFollow() }? ;
PROJECT          : 'project' { !isNameCharFollow() }? ;
PSOLVE           : 'psolve' { !isNameCharFollow() }? ;
PUB              : 'pub' { !isNameCharFollow() }? ;
QUERY            : 'query' { !isNameCharFollow() }? ;
REDEF            : 'redef' { !isNameCharFollow() }? ;
REGION           : 'region' { !isNameCharFollow() }? ;
REF              : 'ref' { !isNameCharFollow() }? ;
RESTRICTABLE     : 'restrictable' { !isNameCharFollow() }? ;
RUN              : 'run' { !isNameCharFollow() }? ;
SEALED           : 'sealed' { !isNameCharFollow() }? ;
SELECT           : 'select' { !isNameCharFollow() }? ;
SET              : 'set' { !isNameCharFollow() }? ;
SOLVE            : 'solve' { !isNameCharFollow() }? ;
SPAWN            : 'spawn' { !isNameCharFollow() }? ;
STRING           : 'string' { !isNameCharFollow() }? ;
STRUCT           : 'struct' { !isNameCharFollow() }? ;
TRUE             : 'true' { !isNameCharFollow() }? ;
TRY              : 'try' { !isNameCharFollow() }? ;
TYPE             : 'type' { !isNameCharFollow() }? ;
USE              : 'use' { !isNameCharFollow() }? ;
WHERE            : 'where' { !isNameCharFollow() }? ;
WITH             : 'with' { !isNameCharFollow() }? ;
WITHOUT          : 'without' { !isNameCharFollow() }? ;
YIELD            : 'yield' { !isNameCharFollow() }? ;

// ---------------------------------------------------------------------
// Delimiters & Punctuation
// ---------------------------------------------------------------------

LPAREN           : '(' ;
RPAREN           : ')' ;
LBRACK           : '[' ;
RBRACK           : ']' ;
LBRACE           : '{' { enterBrace(); } ;
RBRACE           : '}' { if (exitBrace()) { popMode(); setType(INTERPOLATION_END); } } ;
COMMA            : ',' ;
SEMI             : ';' ;
COLON            : ':' ;
COLON_COLON      : '::' ;
COLON_COLON_COLON: ':::' ;
COLON_MINUS      : ':-' ;
AT               : '@' ;
HASH             : '#' ;
PERCENT_PERCENT  : '%%' ;
QUESTION         : '?' ;
TILDE            : '~' ;
BACKTICK         : '`' ;

// ---------------------------------------------------------------------
// Whitespace-sensitive Tokens & Operator Runs
// ---------------------------------------------------------------------

ARROW            : '->' { classifyArrow(); } ;
DOT_TOKEN        : '.' { classifyDot(); } ;
OP_RUN           : ( [+\-*<>=!&|^$]+ | '_' [+\-*<>=!&|^$]+ ) { classifyOperator(); } ;

// ---------------------------------------------------------------------
// Identifiers & Names
// ---------------------------------------------------------------------

DOLLAR_NAME      : '$' [a-zA-Z0-9_!$]+ { stripEscape(); setType(NAME_LOWERCASE); } ;
NAME_LOWERCASE   : ([a-z] | '_' [a-z]) [a-zA-Z0-9_!$]* ;
NAME_UPPERCASE   : [A-Z] [a-zA-Z0-9_!$]* ;
NAME_MATH        : [\u2200-\u22FF]+ | '_' [\u2200-\u22FF]+ ;
UNDERSCORE       : '_' ;

// ---------------------------------------------------------------------
// Literals
// ---------------------------------------------------------------------

HEX_LITERAL      : '0x' [0-9a-fA-F_]+ ( 'i8' | 'i16' | 'i32' | 'i64' | 'ii' | 'i' )? ;
INT_LITERAL      : [0-9] [0-9_]* ( 'i8' | 'i16' | 'i32' | 'i64' | 'ii' | 'i' )? ;
FLOAT_LITERAL    : [0-9] [0-9_]* '.' [0-9] [0-9_]* ('e' [+-]? [0-9]+)? ( 'f32' | 'f64' | 'f' | 'ff' )?
                 | [0-9] [0-9_]* ( 'f32' | 'f64' | 'ff' ) ;
CHAR_LITERAL     : '\'' ( ~['\\\r\n] | ESCAPE_SEQUENCE ) '\'' ;

fragment ESCAPE_SEQUENCE
    : '\\' [btnfr"'\\]
    | '\\' 'u' [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]
    ;

// ---------------------------------------------------------------------
// Strings & Interpolations
// ---------------------------------------------------------------------

STRING_START     : '"' -> pushMode(STRING_MODE) ;

// ---------------------------------------------------------------------
// Comments & Whitespace
// ---------------------------------------------------------------------

DOC_COMMENT      : '///' ~[/] ~[\r\n]* -> channel(DOC_COMMENTS) ;
LINE_COMMENT     : '//' ~[\r\n]* -> channel(COMMENTS) ;
BLOCK_COMMENT    : '/*' ( BLOCK_COMMENT | . )*? '*/' -> channel(COMMENTS) ;
WS               : [ \t\r\n\f]+ -> channel(HIDDEN) ;

// =====================================================================
// String Mode
// =====================================================================

mode STRING_MODE;

STRING_END          : '"' -> popMode ;
INTERPOLATION_START : '${' { openInterpolation(); } -> pushMode(DEFAULT_MODE) ;
STRING_CONTENT      : ( ~["\\$] | '\\' . | '$' ~[{] )+ ;
