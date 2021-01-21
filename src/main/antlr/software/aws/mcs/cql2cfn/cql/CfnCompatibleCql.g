/*-
 * #%L
 * Amazon Keyspaces CQL Script to CFN Template Converter
 * %%
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

grammar CfnCompatibleCql;

options {
    language = Java;
}

@header {
    package software.aws.mcs.cql2cfn.cql;

    import software.aws.mcs.cql2cfn.cql.Identifier;
    import software.aws.mcs.cql2cfn.cql.KeyspaceProperties;
    import software.aws.mcs.cql2cfn.cql.ParsingException;
    import software.aws.mcs.cql2cfn.cql.Properties;
    import software.aws.mcs.cql2cfn.cql.PropertyMap;
    import software.aws.mcs.cql2cfn.cql.Statement;
    import software.aws.mcs.cql2cfn.cql.TableProperties;
    import software.aws.mcs.cql2cfn.cql.Type;
    import java.util.ArrayList;
    import java.util.LinkedHashMap;
    import java.util.List;
    import java.util.Map;
}

@members {
    @Override
    public void emitErrorMessage(String msg) {
        throw new ParsingException(msg);
    }
}

@lexer::header {
    package software.aws.mcs.cql2cfn.cql;

    import software.aws.mcs.cql2cfn.cql.ParsingException;
    import java.util.ArrayList;
    import java.util.List;
}

@lexer::members {
    private final List<Token> tokens = new ArrayList<Token>();

    @Override
    public void emit(Token token) {
        state.token = token;
        tokens.add(token);
    }

    @Override
    public Token nextToken() {
        super.nextToken();
        if (tokens.size() == 0) {
            return new CommonToken(Token.EOF);
        }
        return tokens.remove(0);
    }

    @Override
    public void emitErrorMessage(String msg) {
        throw new ParsingException(msg);
    }
}

cqlScript returns [List<Statement> stmts]
    @init { $stmts = new ArrayList<>(); }
    : (stmt=cqlStatement { $stmts.add(stmt); } ';')* EOF
    ;

cqlStatement returns [Statement stmt]
    : use=useStatement                       { $stmt = use; }
    | createKeyspace=createKeyspaceStatement { $stmt = createKeyspace; }
    | createTable=createTableStatement       { $stmt = createTable; }
    ;

useStatement returns [Statement.Use use]
    : K_USE ksName=identifier { $use = new Statement.Use(ksName); }
    ;

createKeyspaceStatement returns [Statement.CreateKeyspace createKeyspace]
    @init {
        boolean ifNotExists = false;
        KeyspaceProperties props = new KeyspaceProperties();
    }
    : K_CREATE K_KEYSPACE (K_IF K_NOT K_EXISTS { ifNotExists = true; })? ksName=identifier
      K_WITH ksProperty[props] (K_AND ksProperty[props])*
      { $createKeyspace = new Statement.CreateKeyspace(ksName, ifNotExists, props); }
    ;

ksProperty[KeyspaceProperties props]
    : property[props]
    ;

createTableStatement returns [Statement.CreateTable createTable]
    @init {
        boolean ifNotExists = false;
        TableProperties props = new TableProperties();
    }
    : K_CREATE K_COLUMNFAMILY (K_IF K_NOT K_EXISTS { ifNotExists = true; })? (ksName=identifier '.')? cfName=identifier
      '(' cfColumns[props] ( ',' cfColumns[props]? )* ')'
      ( K_WITH cfProperty[props] ( K_AND cfProperty[props] )*)?
      { createTable = new Statement.CreateTable(ksName, cfName, ifNotExists, props); }
    ;

cfColumns[TableProperties props]
    : k=identifier v=type { $props.addColumnDefinition(k, v); }
        (K_STATIC { $props.addStaticColumnName(k); })?
        (K_PRIMARY K_KEY { $props.startDefiningPartitionKeyColumnNames(); $props.addPartitionKeyColumnName(k); })?
    | K_PRIMARY K_KEY '(' pkDef[props] (',' k=identifier { $props.addClusteringColumnName(k); })* ')'
    ;

pkDef[TableProperties props]
    @init { $props.startDefiningPartitionKeyColumnNames(); }
    : k=identifier { $props.addPartitionKeyColumnName(k); }
    | '('
        k1=identifier { $props.addPartitionKeyColumnName(k1); }
        ( ',' kn=identifier { $props.addPartitionKeyColumnName(kn); } )*
      ')'
    ;

cfProperty[TableProperties props]
    : property[props]
    | K_CLUSTERING K_ORDER { $props.startDefiningClusteringOrder(); }
      K_BY '(' cfClusteringOrder[props] (',' cfClusteringOrder[props])* ')'
    | K_COMPACT K_STORAGE { $props.defineCompactStorage(); }
    ;

cfClusteringOrder[TableProperties props]
    : k=identifier ( K_ASC  { $props.addClusteringAscendingOrder(k); }
                   | K_DESC { $props.addClusteringDescendingOrder(k); }
                   )
    ;

identifier returns [Identifier id]
    : t=IDENT               { $id = new Identifier($t.text, false); }
    | t=QUOTED_NAME         { $id = new Identifier($t.text, true); }
    | k=unreservedKeyword   { $id = new Identifier(k, false); }
    | t=K_CUSTOM_PROPERTIES { $id = new Identifier($t.text, false); }
    ;

userTypeIdentifier returns [Identifier id]
    : t=IDENT                  { $id = new Identifier($t.text, false); }
    | t=QUOTED_NAME            { $id = new Identifier($t.text, true); }
    | k=basicUnreservedKeyword { $id = new Identifier(k, false); }
    | t=( K_KEY
        | K_CUSTOM_PROPERTIES
        ) { $id = new Identifier($t.text, false); }
    ;

propertyIdentifier returns [Identifier id]
    : t=IDENT             { $id = new Identifier($t.text, false); }
    | t=QUOTED_NAME       { $id = new Identifier($t.text, true); }
    | k=unreservedKeyword { $id = new Identifier(k, false); }
    ;

customPropertiesIdentifier returns [Identifier id]
    : t=K_CUSTOM_PROPERTIES { $id = new Identifier($t.text, false); }
    ;

property[Properties props]
    : k=propertyIdentifier '=' scalar=propertyScalarValue { $props.getRegularProperties().addProperty(k, scalar); }
    | k=propertyIdentifier '=' map=propertyMapValue { $props.getRegularProperties().addProperty(k, map); }
    | k=customPropertiesIdentifier '=' v=customPropertiesValue { $props.defineCustomProperties(v); }
    ;

propertyScalarValue returns [String string]
    : t=( STRING_LITERAL
        | INTEGER
        | FLOAT
        | BOOLEAN
        | UUID
        ) { $string = $t.text; }
    ;

propertyMapValue returns [PropertyMap<String, String> map]
    @init { $map = new PropertyMap<>(); }
    : '{' (
        k1=STRING_LITERAL ':' v1=propertyScalarValue { $map.addProperty($k1.text, v1); }
        ( ',' kn=STRING_LITERAL ':' vn=propertyScalarValue { $map.addProperty($kn.text, vn); } )*
      )? '}'
    ;

customPropertiesValue returns [PropertyMap<String, PropertyMap<String, String>> map]
    @init { $map = new PropertyMap<>(); }
    : '{' (
        k1=STRING_LITERAL ':' v1=propertyMapValue { $map.addProperty($k1.text, v1); }
        ( ',' kn=STRING_LITERAL ':' vn=propertyMapValue { $map.addProperty($kn.text, vn); } )*
      )? '}'
    ;

type returns [Type t]
    : ts=simpleType                                      { $t = ts; }
    | tc=compositeType                                   { $t = tc; }
    | (ksName=identifier '.')? utName=userTypeIdentifier { $t = new Type.User(ksName, utName); }
    | className=STRING_LITERAL                           { $t = new Type.Custom($className.text); }
    ;

simpleType returns [Type.Simple ts]
    : k=( K_ASCII
        | K_BIGINT
        | K_BLOB
        | K_BOOLEAN
        | K_COUNTER
        | K_DECIMAL
        | K_DOUBLE
        | K_DURATION
        | K_FLOAT
        | K_INET
        | K_INT
        | K_SMALLINT
        | K_TEXT
        | K_TIMESTAMP
        | K_TINYINT
        | K_UUID
        | K_VARCHAR
        | K_VARINT
        | K_TIMEUUID
        | K_DATE
        | K_TIME
        ) { $ts = new Type.Simple($k.text); }
    ;

compositeType returns [Type.Composite tc]
    : k=K_FROZEN '<' t=type '>'            { $tc = new Type.Composite($k.text, t); }
    | k=K_LIST '<' t=type '>'              { $tc = new Type.Composite($k.text, t); }
    | k=K_SET '<' t=type '>'               { $tc = new Type.Composite($k.text, t); }
    | k=K_MAP '<' t1=type ',' t2=type '>'  { $tc = new Type.Composite($k.text, t1, t2); }
    | k=K_TUPLE '<' { List<Type> types = new ArrayList<>(); }
        t1=type { types.add(t1); }
        ( ',' tn=type { types.add(tn); } )*
      '>' { $tc = new Type.Composite($k.text, types.toArray(new Type[0])); }
    ;

unreservedKeyword returns [String str]
    : t=simpleType               { $str = t.getName(); }
    | u=basicUnreservedKeyword   { $str = u; }
    | k=( K_TTL
        | K_COUNT
        | K_WRITETIME
        | K_KEY
        | K_CAST
        | K_JSON
        | K_DISTINCT
        ) { $str = $k.text; }
    ;

basicUnreservedKeyword returns [String str]
    : k=( K_KEYS
        | K_AS
        | K_CLUSTERING
        | K_COMPACT
        | K_STORAGE
        | K_TYPE
        | K_VALUES
        | K_MAP
        | K_LIST
        | K_FILTERING
        | K_PERMISSION
        | K_PERMISSIONS
        | K_KEYSPACES
        | K_ALL
        | K_USER
        | K_USERS
        | K_ROLE
        | K_ROLES
        | K_SUPERUSER
        | K_NOSUPERUSER
        | K_LOGIN
        | K_NOLOGIN
        | K_OPTIONS
        | K_PASSWORD
        | K_EXISTS
        | K_CUSTOM
        | K_TRIGGER
        | K_CONTAINS
        | K_STATIC
        | K_FROZEN
        | K_TUPLE
        | K_FUNCTION
        | K_FUNCTIONS
        | K_AGGREGATE
        | K_SFUNC
        | K_STYPE
        | K_FINALFUNC
        | K_INITCOND
        | K_RETURNS
        | K_LANGUAGE
        | K_CALLED
        | K_INPUT
        | K_LIKE
        | K_PER
        | K_PARTITION
        | K_GROUP
        ) { $str = $k.text; }
    ;

K_SELECT:      S E L E C T;
K_FROM:        F R O M;
K_AS:          A S;
K_WHERE:       W H E R E;
K_AND:         A N D;
K_KEY:         K E Y;
K_KEYS:        K E Y S;
K_ENTRIES:     E N T R I E S;
K_FULL:        F U L L;
K_INSERT:      I N S E R T;
K_UPDATE:      U P D A T E;
K_WITH:        W I T H;
K_LIMIT:       L I M I T;
K_PER:         P E R;
K_PARTITION:   P A R T I T I O N;
K_USING:       U S I N G;
K_USE:         U S E;
K_DISTINCT:    D I S T I N C T;
K_COUNT:       C O U N T;
K_SET:         S E T;
K_BEGIN:       B E G I N;
K_UNLOGGED:    U N L O G G E D;
K_BATCH:       B A T C H;
K_APPLY:       A P P L Y;
K_TRUNCATE:    T R U N C A T E;
K_DELETE:      D E L E T E;
K_IN:          I N;
K_CREATE:      C R E A T E;
K_KEYSPACE:    ( K E Y S P A C E
                 | S C H E M A );
K_KEYSPACES:   K E Y S P A C E S;
K_COLUMNFAMILY:( C O L U M N F A M I L Y
                 | T A B L E );
K_MATERIALIZED:M A T E R I A L I Z E D;
K_VIEW:        V I E W;
K_INDEX:       I N D E X;
K_CUSTOM:      C U S T O M;
K_ON:          O N;
K_TO:          T O;
K_DROP:        D R O P;
K_PRIMARY:     P R I M A R Y;
K_INTO:        I N T O;
K_VALUES:      V A L U E S;
K_TIMESTAMP:   T I M E S T A M P;
K_TTL:         T T L;
K_CAST:        C A S T;
K_ALTER:       A L T E R;
K_RENAME:      R E N A M E;
K_ADD:         A D D;
K_TYPE:        T Y P E;
K_COMPACT:     C O M P A C T;
K_STORAGE:     S T O R A G E;
K_ORDER:       O R D E R;
K_BY:          B Y;
K_ASC:         A S C;
K_DESC:        D E S C;
K_ALLOW:       A L L O W;
K_FILTERING:   F I L T E R I N G;
K_IF:          I F;
K_IS:          I S;
K_CONTAINS:    C O N T A I N S;
K_GROUP:       G R O U P;
K_GRANT:       G R A N T;
K_ALL:         A L L;
K_PERMISSION:  P E R M I S S I O N;
K_PERMISSIONS: P E R M I S S I O N S;
K_OF:          O F;
K_REVOKE:      R E V O K E;
K_MODIFY:      M O D I F Y;
K_AUTHORIZE:   A U T H O R I Z E;
K_DESCRIBE:    D E S C R I B E;
K_EXECUTE:     E X E C U T E;
K_NORECURSIVE: N O R E C U R S I V E;
K_MBEAN:       M B E A N;
K_MBEANS:      M B E A N S;
K_USER:        U S E R;
K_USERS:       U S E R S;
K_ROLE:        R O L E;
K_ROLES:       R O L E S;
K_SUPERUSER:   S U P E R U S E R;
K_NOSUPERUSER: N O S U P E R U S E R;
K_PASSWORD:    P A S S W O R D;
K_LOGIN:       L O G I N;
K_NOLOGIN:     N O L O G I N;
K_OPTIONS:     O P T I O N S;
K_CLUSTERING:  C L U S T E R I N G;
K_ASCII:       A S C I I;
K_BIGINT:      B I G I N T;
K_BLOB:        B L O B;
K_BOOLEAN:     B O O L E A N;
K_COUNTER:     C O U N T E R;
K_DECIMAL:     D E C I M A L;
K_DOUBLE:      D O U B L E;
K_DURATION:    D U R A T I O N;
K_FLOAT:       F L O A T;
K_INET:        I N E T;
K_INT:         I N T;
K_SMALLINT:    S M A L L I N T;
K_TINYINT:     T I N Y I N T;
K_TEXT:        T E X T;
K_UUID:        U U I D;
K_VARCHAR:     V A R C H A R;
K_VARINT:      V A R I N T;
K_TIMEUUID:    T I M E U U I D;
K_TOKEN:       T O K E N;
K_WRITETIME:   W R I T E T I M E;
K_DATE:        D A T E;
K_TIME:        T I M E;
K_NULL:        N U L L;
K_NOT:         N O T;
K_EXISTS:      E X I S T S;
K_MAP:         M A P;
K_LIST:        L I S T;
K_NAN:         N A N;
K_INFINITY:    I N F I N I T Y;
K_TUPLE:       T U P L E;
K_TRIGGER:     T R I G G E R;
K_STATIC:      S T A T I C;
K_FROZEN:      F R O Z E N;
K_FUNCTION:    F U N C T I O N;
K_FUNCTIONS:   F U N C T I O N S;
K_AGGREGATE:   A G G R E G A T E;
K_SFUNC:       S F U N C;
K_STYPE:       S T Y P E;
K_FINALFUNC:   F I N A L F U N C;
K_INITCOND:    I N I T C O N D;
K_RETURNS:     R E T U R N S;
K_CALLED:      C A L L E D;
K_INPUT:       I N P U T;
K_LANGUAGE:    L A N G U A G E;
K_OR:          O R;
K_REPLACE:     R E P L A C E;
K_JSON:        J S O N;
K_DEFAULT:     D E F A U L T;
K_UNSET:       U N S E T;
K_LIKE:        L I K E;
K_CUSTOM_PROPERTIES:C U S T O M '_' P R O P E R T I E S;

fragment A: ('a'|'A');
fragment B: ('b'|'B');
fragment C: ('c'|'C');
fragment D: ('d'|'D');
fragment E: ('e'|'E');
fragment F: ('f'|'F');
fragment G: ('g'|'G');
fragment H: ('h'|'H');
fragment I: ('i'|'I');
fragment J: ('j'|'J');
fragment K: ('k'|'K');
fragment L: ('l'|'L');
fragment M: ('m'|'M');
fragment N: ('n'|'N');
fragment O: ('o'|'O');
fragment P: ('p'|'P');
fragment Q: ('q'|'Q');
fragment R: ('r'|'R');
fragment S: ('s'|'S');
fragment T: ('t'|'T');
fragment U: ('u'|'U');
fragment V: ('v'|'V');
fragment W: ('w'|'W');
fragment X: ('x'|'X');
fragment Y: ('y'|'Y');
fragment Z: ('z'|'Z');

STRING_LITERAL
    @init{
        StringBuilder txt = new StringBuilder(); // temporary to build pg-style-string
    }
    @after{ setText(txt.toString()); }
    :
      /* pg-style string literal */
      (
        '\$' '\$'
        ( /* collect all input until '$$' is reached again */
          {  (input.size() - input.index() > 1)
               && !"$$".equals(input.substring(input.index(), input.index() + 1)) }?
             => c=. { txt.appendCodePoint(c); }
        )*
        '\$' '\$'
      )
      |
      /* conventional quoted string literal */
      (
        '\'' (c=~('\'') { txt.appendCodePoint(c);} | '\'' '\'' { txt.appendCodePoint('\''); })* '\''
      )
    ;

QUOTED_NAME
    @init { StringBuilder b = new StringBuilder(); }
    @after { setText(b.toString()); }
    : '\"' (c=~('\"') { b.appendCodePoint(c); } | '\"' '\"' { b.appendCodePoint('\"'); })+ '\"'
    ;

fragment DIGIT
    : '0'..'9'
    ;

fragment LETTER
    : ('A'..'Z' | 'a'..'z')
    ;

fragment HEX
    : ('A'..'F' | 'a'..'f' | '0'..'9')
    ;

fragment EXPONENT
    : E ('+' | '-')? DIGIT+
    ;

INTEGER
    : '-'? DIGIT+
    ;

FLOAT
    : INTEGER EXPONENT
    | INTEGER '.' DIGIT* EXPONENT?
    ;

BOOLEAN
    : T R U E | F A L S E
    ;

IDENT
    : LETTER (LETTER | DIGIT | '_')*
    ;

UUID
    : HEX HEX HEX HEX HEX HEX HEX HEX '-'
      HEX HEX HEX HEX '-'
      HEX HEX HEX HEX '-'
      HEX HEX HEX HEX '-'
      HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX HEX
    ;

WS
    : (' ' | '\t' | '\n' | '\r')+ { $channel = HIDDEN; }
    ;

COMMENT
    : ('--' | '//') .* ('\n'|'\r') { $channel = HIDDEN; }
    ;

MULTILINE_COMMENT
    : '/*' .* '*/' { $channel = HIDDEN; }
    ;
