/* Copyright (c) 2013 Pivotal Software, Inc. All rights reserved. */
package com.rabbitmq.jms.parse.sql;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.rabbitmq.jms.util.TimeTracker;

public class SqlProductionTest {

    @Test
    public void root() throws Exception {
        assertEquals("ROOT incorrectly set", SqlProduction.ROOT, SqlProduction.expression);
    }

    @Test
    public void expression() throws Exception {
        assertNullParse(SqlProduction.expression);

        assertParse(SqlProduction.expression, "nothing IS NULL"
                   , "POSTFIXUNARYOP: is_null"
                   , "    LEAF: ident: nothing"
                   );
        assertParse(SqlProduction.expression, "nothing IS NULL And JMSPrefix > 12-4"
                   , "CONJUNCTION:"
                   , "    POSTFIXUNARYOP: is_null"
                   , "        LEAF: ident: nothing"
                   , "    BINARYOP: >"
                   , "        LEAF: ident: JMSPrefix"
                   , "        BINARYOP: -"
                   , "            LEAF: integer: 12"
                   , "            LEAF: integer: 4"
                   );
        assertParse(SqlProduction.expression, "nothing IS NulL oR JMSPrefix > 12- 4"
                   , "DISJUNCTION:"
                   , "    POSTFIXUNARYOP: is_null"
                   , "        LEAF: ident: nothing"
                   , "    BINARYOP: >"
                   , "        LEAF: ident: JMSPrefix"
                   , "        BINARYOP: -"
                   , "            LEAF: integer: 12"
                   , "            LEAF: integer: 4"
                   );
        assertParse(SqlProduction.expression, "nothing IS NulL oR (JMSPrefix >= 12/-4.3)"
                   , "DISJUNCTION:"
                   , "    POSTFIXUNARYOP: is_null"
                   , "        LEAF: ident: nothing"
                   , "    BINARYOP: >="
                   , "        LEAF: ident: JMSPrefix"
                   , "        BINARYOP: /"
                   , "            LEAF: integer: 12"
                   , "            PREFIXUNARYOP: -"
                   , "                LEAF: float: 4.3"
                   );
    }

    @Test
    public void not_expr() throws Exception {
        assertNullParse(SqlProduction.not_expr);

        assertParse(SqlProduction.not_expr,  "not 2 < 3"
                    , "PREFIXUNARYOP: not"
                    , "    BINARYOP: <"
                    , "        LEAF: integer: 2"
                    , "        LEAF: integer: 3"
                   );
    }

    @Test
    public void cmp_expr() throws Exception {
        assertNullParse(SqlProduction.cmp_expr);

        assertParse(SqlProduction.cmp_expr,
                    "2.5 < 3"
                    , "BINARYOP: <"
                    , "    LEAF: float: 2.5"
                    , "    LEAF: integer: 3"
                   );
        assertParse(SqlProduction.cmp_expr,
                    "2.5 between 2 and 3"
                    , "TERNARYOP: between"
                    , "    LEAF: float: 2.5"
                    , "    LEAF: integer: 2"
                    , "    LEAF: integer: 3"
                   );
        assertParse(SqlProduction.cmp_expr,
                    "2.5 not between 2 and 3"
                    , "TERNARYOP: not_between"
                    , "    LEAF: float: 2.5"
                    , "    LEAF: integer: 2"
                    , "    LEAF: integer: 3"
                   );
    }

    @Test
    public void arith_expr() throws Exception {
        assertNullParse(SqlProduction.arith_expr);

        assertParse(SqlProduction.arith_expr,
                    "2.5 * 2 - 3"
                    , "BINARYOP: -"
                    , "    BINARYOP: *"
                    , "        LEAF: float: 2.5"
                    , "        LEAF: integer: 2"
                    , "    LEAF: integer: 3"
                   );
    }

    @Test
    public void sign_expr() throws Exception {
        assertNullParse(SqlProduction.sign_expr);

        assertParse(SqlProduction.sign_expr,
                    "--+-23.3"
                    , "PREFIXUNARYOP: -"
                    , "    PREFIXUNARYOP: -"
                    , "        PREFIXUNARYOP: +"
                    , "            PREFIXUNARYOP: -"
                    , "                LEAF: float: 23.3"
                   );
    }

    @Test
    public void stringlist() throws Exception {
        assertNullParse(SqlProduction.stringlist);

        assertParse(SqlProduction.stringlist,
                    "( '', 'asd' )"
                    , "LIST: list: [, asd]"
                   );
        assertParse(SqlProduction.stringlist,
                    "( 'a', 'b', 'c' )"
                    , "LIST: list: [a, b, c]"
                   );
    }

    @Test
    public void STRING() throws Exception {
        assertNullParse(SqlProduction.STRING);

        assertParse(SqlProduction.STRING,
                    "'asd'"
                    , "LEAF: string: 'asd'"
                   );
    }

    @Test
    public void pattern() throws Exception {
        assertNullParse(SqlProduction.pattern);

        assertParse(SqlProduction.pattern,
                    "'asd'"
                    , "PATTERN1:"
                    , "    LEAF: string: 'asd'"
                   );
        assertParse(SqlProduction.pattern,
                    "'asd' escape '`'"
                    , "PATTERN2:"
                    , "    LEAF: string: 'asd'"
                    , "    LEAF: string: '`'"
                   );
    }

    @Test
    public void slow_expression() throws Exception {
        TimeTracker tt = new TimeTracker(500, TimeUnit.MILLISECONDS);
        assertParse(SqlProduction.expression,
                    "(((-1)))"
                    , "PREFIXUNARYOP: -"
                    , "    LEAF: integer: 1"
                   );
        assertFalse("Took too long", tt.timedOut());
    }

    @Test
    public void simple() throws Exception {
        assertNullParse(SqlProduction.simple);

        assertParse(SqlProduction.simple,
                    "(asd + 1)"
                    , "BINARYOP: +"
                    , "    LEAF: ident: asd"
                    , "    LEAF: integer: 1"
                   );
        assertParse(SqlProduction.simple,
                    "  true "
                    , "LEAF: true"
                   );
        assertParse(SqlProduction.simple,
                    "FalSe"
                    , "LEAF: false"
                   );
        assertParse(SqlProduction.simple,
                    "fred is null"
                    , "POSTFIXUNARYOP: is_null"
                    , "    LEAF: ident: fred"
                   );
        assertParse(SqlProduction.simple,
                    "fred is not null"
                    , "POSTFIXUNARYOP: not_null"
                    , "    LEAF: ident: fred"
                   );
        assertParse(SqlProduction.simple,
                    "fred iN  ('a' ,'b')"
                    , "BINARYOP: in"
                    , "    LEAF: ident: fred"
                    , "    LIST: list: [a, b]"
                   );
        assertParse(SqlProduction.simple,
                    "fred nOt  iN  ('a' ,'b')"
                    , "BINARYOP: not_in"
                    , "    LEAF: ident: fred"
                    , "    LIST: list: [a, b]"
                   );
        assertParse(SqlProduction.simple,
                    "fred like  'a*'"
                    , "BINARYOP: like"
                    , "    LEAF: ident: fred"
                    , "    PATTERN1:"
                    , "        LEAF: string: 'a*'"
                   );
        assertParse(SqlProduction.simple,
                    "fred like  'a*' escape '\\'"
                    , "BINARYOP: like"
                    , "    LEAF: ident: fred"
                    , "    PATTERN2:"
                    , "        LEAF: string: 'a*'"
                    , "        LEAF: string: '\\'"
                   );
        assertParse(SqlProduction.simple,
                    "fred like  'a' escape ''''"
                    , "BINARYOP: like"
                    , "    LEAF: ident: fred"
                    , "    PATTERN2:"
                    , "        LEAF: string: 'a'"
                    , "        LEAF: string: ''''"
                   );
    }

    private static final int STACKFRAME_NESTED_PARENS_LIMIT = 150;

    @Test
    public void deeplyNestedParenthesesParsingDoesNotOverflowUpToALimit() throws Exception {
        assertParse(SqlProduction.simple,
                    parens(STACKFRAME_NESTED_PARENS_LIMIT, "1")
                   , "LEAF: integer: 1"
                   );
    }

    @Test(expected = StackOverflowError.class)
    public void deeplyNestedParenthesesParsingOverflowsAboveALimit() throws Exception {
        assertParse(SqlProduction.simple,
                    parens(STACKFRAME_NESTED_PARENS_LIMIT * 10, "1")
                   , "LEAF: integer: 1"
                   );
    }

    private String parens(int num, String base) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<num; ++i) sb.append('(');
        sb.append(base);
        for (int i=0; i<num; ++i) sb.append(')');
        return sb.toString();
    }

    private void assertParse(SqlProduction sct, String inStr, String... outStr) {
        SqlTokenStream stream = new SqlTokenStream(inStr);
        assertEquals("Residue not empty", "", stream.getResidue());

        SqlParseTree stn = sct.parse(stream);
        assertNotNull("parse failed", stn);

        assertTrue("Stream not fully used", !stream.moreTokens());

        String[] formatted = stn.formattedTree();
        assertArrayEquals("Parsed tree doesn't match", outStr, formatted);
    }

    private void assertNullParse(SqlProduction sct) {
        SqlTokenStream stream = new SqlTokenStream("");
        assertEquals("Residue not empty", "", stream.getResidue());

        SqlParseTree stn = sct.parse(stream);
        assertNull("parse not null for empty tokenstream", stn);
    }

}
