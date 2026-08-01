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
    SLASH,
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
    BACKSLASH,
    GENERIC_OPERATOR,

    // String interpolation token emitted when a closing brace closes an interpolation
    INTERPOLATION_END,

    // Emitted when a raw newline terminates a string, which the reference rejects
    UNTERMINATED_STRING
}

channels {
    COMMENTS,
    DOC_COMMENTS
}

// ---------------------------------------------------------------------
// Keywords
//
// The 84 keywords of Flix 0.75.1, transcribed from Lexer.scala:49-139 and
// pinned by fixtures/keywords.txt. Each carries a !isNameCharFollow() tail
// guard because `!` and `$` are name characters, so `let!` is one identifier
// rather than a keyword followed by an operator.
//
// Do not add a keyword without adding it to fixtures/keywords.txt; the
// bidirectional set-equality test in FlixKeywordTableTest will fail.
// ---------------------------------------------------------------------

ARRAY_HASH       : 'Array#' { !isNameCharFollow() }? ;
LIST_HASH        : 'List#' { !isNameCharFollow() }? ;
MAP_HASH         : 'Map#' { !isNameCharFollow() }? ;
SET_HASH         : 'Set#' { !isNameCharFollow() }? ;
VECTOR_HASH      : 'Vector#' { !isNameCharFollow() }? ;
STATIC_UPPER     : 'Static' { !isNameCharFollow() }? ;
UNIV             : 'Univ' { !isNameCharFollow() }? ;

ALIAS            : 'alias' { !isNameCharFollow() }? ;
AND              : 'and' { !isNameCharFollow() }? ;
AS               : 'as' { !isNameCharFollow() }? ;
CASE             : 'case' { !isNameCharFollow() }? ;
CATCH            : 'catch' { !isNameCharFollow() }? ;
CHECKED_CAST     : 'checked_cast' { !isNameCharFollow() }? ;
CHECKED_ECAST    : 'checked_ecast' { !isNameCharFollow() }? ;
CHOOSE_STAR      : 'choose*' { !isNameCharFollow() }? ;
CHOOSE           : 'choose' { !isNameCharFollow() }? ;
DEF              : 'def' { !isNameCharFollow() }? ;
DISCARD          : 'discard' { !isNameCharFollow() }? ;
EFF              : 'eff' { !isNameCharFollow() }? ;
ELSE             : 'else' { !isNameCharFollow() }? ;
EMATCH           : 'ematch' { !isNameCharFollow() }? ;
ENUM             : 'enum' { !isNameCharFollow() }? ;
FALSE            : 'false' { !isNameCharFollow() }? ;
FIX              : 'fix' { !isNameCharFollow() }? ;
FORA             : 'forA' { !isNameCharFollow() }? ;
FORM             : 'forM' { !isNameCharFollow() }? ;
FORALL           : 'forall' { !isNameCharFollow() }? ;
FORCE            : 'force' { !isNameCharFollow() }? ;
FOREACH          : 'foreach' { !isNameCharFollow() }? ;
FROM             : 'from' { !isNameCharFollow() }? ;
HANDLER          : 'handler' { !isNameCharFollow() }? ;
IF               : 'if' { !isNameCharFollow() }? ;
IMPORT           : 'import' { !isNameCharFollow() }? ;
INJECT           : 'inject' { !isNameCharFollow() }? ;
INSTANCEOF       : 'instanceof' { !isNameCharFollow() }? ;
INSTANCE         : 'instance' { !isNameCharFollow() }? ;
INTO             : 'into' { !isNameCharFollow() }? ;
LAW              : 'law' { !isNameCharFollow() }? ;
LAWFUL           : 'lawful' { !isNameCharFollow() }? ;
LAZY             : 'lazy' { !isNameCharFollow() }? ;
LET              : 'let' { !isNameCharFollow() }? ;
MATCH            : 'match' { !isNameCharFollow() }? ;
MOD              : 'mod' { !isNameCharFollow() }? ;
MUT              : 'mut' { !isNameCharFollow() }? ;
NEW              : 'new' { !isNameCharFollow() }? ;
NOT              : 'not' { !isNameCharFollow() }? ;
NULL             : 'null' { !isNameCharFollow() }? ;
OPEN_VARIANT_AS  : 'open_variant_as' { !isNameCharFollow() }? ;
OPEN_VARIANT     : 'open_variant' { !isNameCharFollow() }? ;
OR               : 'or' { !isNameCharFollow() }? ;
PAR              : 'par' { !isNameCharFollow() }? ;
PQUERY           : 'pquery' { !isNameCharFollow() }? ;
PROJECT          : 'project' { !isNameCharFollow() }? ;
PSOLVE           : 'psolve' { !isNameCharFollow() }? ;
PUB              : 'pub' { !isNameCharFollow() }? ;
QUERY            : 'query' { !isNameCharFollow() }? ;
REDEF            : 'redef' { !isNameCharFollow() }? ;
REGION           : 'region' { !isNameCharFollow() }? ;
RESTRICTABLE     : 'restrictable' { !isNameCharFollow() }? ;
RUN              : 'run' { !isNameCharFollow() }? ;
RVADD            : 'rvadd' { !isNameCharFollow() }? ;
RVAND            : 'rvand' { !isNameCharFollow() }? ;
RVNOT            : 'rvnot' { !isNameCharFollow() }? ;
RVSUB            : 'rvsub' { !isNameCharFollow() }? ;
SEALED           : 'sealed' { !isNameCharFollow() }? ;
SELECT           : 'select' { !isNameCharFollow() }? ;
SOLVE            : 'solve' { !isNameCharFollow() }? ;
SPAWN            : 'spawn' { !isNameCharFollow() }? ;
STATIC_LOWER     : 'static' { !isNameCharFollow() }? ;
STRUCT           : 'struct' { !isNameCharFollow() }? ;
SUPER            : 'super' { !isNameCharFollow() }? ;
THROW            : 'throw' { !isNameCharFollow() }? ;
TRAIT            : 'trait' { !isNameCharFollow() }? ;
TRUE             : 'true' { !isNameCharFollow() }? ;
TRY              : 'try' { !isNameCharFollow() }? ;
TYPE             : 'type' { !isNameCharFollow() }? ;
UNCHECKED_CAST   : 'unchecked_cast' { !isNameCharFollow() }? ;
UNSAFE           : 'unsafe' { !isNameCharFollow() }? ;
USE              : 'use' { !isNameCharFollow() }? ;
WHERE            : 'where' { !isNameCharFollow() }? ;
WITH             : 'with' { !isNameCharFollow() }? ;
XOR              : 'xor' { !isNameCharFollow() }? ;
XVAR             : 'xvar' { !isNameCharFollow() }? ;
YIELD            : 'yield' { !isNameCharFollow() }? ;

// ---------------------------------------------------------------------
// Delimiters & punctuation
// ---------------------------------------------------------------------

HASH_LBRACE      : '#{' ;
HASH_LPAREN      : '#(' ;
HASH_BAR         : '#|' ;
BAR_HASH         : '|#' ;

LPAREN           : '(' ;
RPAREN           : ')' ;
LBRACK           : '[' ;
RBRACK           : ']' ;
LBRACE           : '{' { enterBrace(); } ;
RBRACE           : '}' { if (exitBrace()) { popMode(); setType(INTERPOLATION_END); } } ;
COMMA            : ',' ;
SEMI             : ';' ;
COLON_COLON_COLON: ':::' ;
COLON_COLON      : '::' ;
COLON_MINUS      : ':-' ;
COLON            : ':' ;
HASH             : '#' ;
TILDE            : '~' ;
BACKTICK         : '`' ;

// `/` is excluded from the user-operator character set purely so that `//` can
// start a comment, so it needs a rule of its own. `\` is the effect separator
// in `def f(): t \ ef`, never a lambda introducer.
SLASH_TOKEN      : '/' -> type(SLASH) ;
BACKSLASH_TOKEN  : '\\' -> type(BACKSLASH) ;

// ---------------------------------------------------------------------
// Whitespace-sensitive tokens & operator runs
//
// `->` and `.` are classified from the surrounding characters. The operator
// run reproduces the reference's "maximal run, then exact match": a run that
// is exactly a reserved spelling becomes that token, anything else becomes a
// user-defined operator. `<--` is therefore one token, not `<-` then `-`.
// ---------------------------------------------------------------------

ARROW            : '->' { classifyArrow(); } ;
DOT_TOKEN        : '.' { classifyDot(); } ;
OP_RUN           : '_'? [+\-*<>=!&|^$]+ { classifyOperator(); } ;

// ---------------------------------------------------------------------
// Holes, intrinsics & annotations
// ---------------------------------------------------------------------

HOLE_ANONYMOUS   : '???' ;
HOLE_NAMED       : '?' [a-zA-Z] [a-zA-Z0-9_!$]* ;
HOLE_VARIABLE    : '_'? [a-zA-Z] [a-zA-Z0-9_!$]* '?' ;
BUILT_IN         : '%%' [A-Z0-9_]* '%%' ;
ANNOTATION       : '@' [a-zA-Z]+ ;
AT               : '@' ;

// ---------------------------------------------------------------------
// Identifiers & names
//
// `_` is a prefix dispatcher rather than a name character at position zero:
// `_foo` is a name, `_+` is a user-defined operator, `_1` is an underscore
// followed by an integer, and a bare `_` is the wildcard.
// ---------------------------------------------------------------------

DOLLAR_NAME      : '$' [a-zA-Z] [a-zA-Z0-9_!$]* { stripEscape(); setType(NAME_LOWERCASE); } ;
NAME_LOWERCASE   : '_'? [a-z] [a-zA-Z0-9_!$]* ;
NAME_UPPERCASE   : '_'? [A-Z] [a-zA-Z0-9_!$]* ;
NAME_MATH        : '_'? [\u2200-\u22FF]+ ;
UNDERSCORE       : '_' ;
DOLLAR           : '$' ;

// ---------------------------------------------------------------------
// Literals
// ---------------------------------------------------------------------

HEX_LITERAL      : '0x' HEXDIGITS INT_SUFFIX? ;
FLOAT_LITERAL    : DIGITS ( '.' DIGITS EXPONENT? | EXPONENT ) FLOAT_SUFFIX?
                 | DIGITS FLOAT_SUFFIX
                 ;
INT_LITERAL      : DIGITS INT_SUFFIX? ;
CHAR_LITERAL     : '\'' ( '\\' . | ~['\\] )*? '\'' ;
REGEX_LITERAL    : 'regex"' ( '\\' . | ~["\\\r\n] )* '"' ;

// The reference lexes `d` as a token of its own when a string follows; the
// string is then lexed normally.
DEBUG_INTERPOLATOR : 'd' { _input.LA(1) == '"' }? ;

fragment DIGITS       : [0-9]+ ( '_' [0-9]+ )* ;
fragment HEXDIGITS    : [0-9a-fA-F]+ ( '_' [0-9a-fA-F]+ )* ;
fragment INT_SUFFIX   : 'i8' | 'i16' | 'i32' | 'i64' | 'ii' ;
fragment FLOAT_SUFFIX : 'f32' | 'f64' | 'ff' ;
fragment EXPONENT     : 'e' [+-]? DIGITS ( '.' DIGITS )? ;

// ---------------------------------------------------------------------
// Strings & interpolations
// ---------------------------------------------------------------------

STRING_START     : '"' -> pushMode(STRING_MODE) ;

// ---------------------------------------------------------------------
// Comments & whitespace
//
// A doc comment is exactly three slashes: `////` is an ordinary line comment.
// Both patterns must stay free of explicit precedence, or the three-character
// prefix wins over a whole four-slash line.
// ---------------------------------------------------------------------

DOC_COMMENT      : '///' ( ~[/\r\n] ~[\r\n]* )? -> channel(DOC_COMMENTS) ;
LINE_COMMENT     : '//' '/'* ~[\r\n]* -> channel(COMMENTS) ;
BLOCK_COMMENT    : '/*' ( BLOCK_COMMENT | . )*? '*/' -> channel(COMMENTS) ;
WS               : [ \t\r\n\u000B\f\u001C-\u001F\u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]+ -> channel(HIDDEN) ;

// =====================================================================
// String mode
// =====================================================================

mode STRING_MODE;

STRING_END          : '"' -> popMode ;
INTERPOLATION_START : '${' { openInterpolation(); } -> pushMode(DEFAULT_MODE) ;
STRING_CONTENT      : ( ~["\\$\r\n] | '\\' . )+ ;
STRING_DOLLAR       : '$' -> type(STRING_CONTENT) ;

// A raw newline terminates the string in the reference. Popping the mode here
// keeps an unterminated string from leaving the lexer stuck inside a string
// for the remainder of the file.
STRING_NEWLINE      : [\r\n] -> type(UNTERMINATED_STRING), popMode ;
