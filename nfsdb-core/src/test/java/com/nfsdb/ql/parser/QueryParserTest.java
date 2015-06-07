/*
 * Copyright (c) 2014. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.ql.parser;

import com.nfsdb.ql.model.ExprNode;
import com.nfsdb.ql.model.Statement;
import com.nfsdb.ql.model.StatementType;
import com.nfsdb.test.tools.AbstractTest;
import com.nfsdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

public class QueryParserTest extends AbstractTest {
    private final QueryParser parser = new QueryParser();

    @Test
    public void testEmptyGroupBy() throws Exception {
        try {
            parse("select x, y from tab group by");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(27, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testEmptyOrderBy() throws Exception {
        try {
            parse("select x, y from tab order by");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(27, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testGroupBy1() throws Exception {
        Statement statement = parse("select x,y from tab group by x,y,z");
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals(3, statement.getQueryModel().getGroupBy().size());
        Assert.assertEquals("[x,y,z]", statement.getQueryModel().getGroupBy().toString());
    }

    @Test
    public void testInvalidGroupBy1() throws Exception {
        try {
            parse("select x, y from tab group by x,");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(32, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testInvalidGroupBy2() throws Exception {
        try {
            parse("select x, y from (tab group by x,)");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(33, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("name expected"));
        }
    }

    @Test
    public void testInvalidGroupBy3() throws Exception {
        try {
            parse("select x, y from tab group by x, order by y");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(33, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("name expected"));
        }
    }

    @Test
    public void testInvalidOrderBy1() throws Exception {
        try {
            parse("select x, y from tab order by x,");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(32, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testInvalidOrderBy2() throws Exception {
        try {
            parse("select x, y from (tab order by x,)");
            Assert.fail("Expected exception");
        } catch (ParserException e) {
            Assert.assertEquals(33, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("Expression expected"));
        }
    }

    @Test
    public void testInvalidSubQuery() throws Exception {
        try {
            parse("select x,y from (tab where x = 100) latest by x");
            Assert.fail("Exception expected");
        } catch (ParserException e) {
            Assert.assertEquals(36, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("latest"));
        }

    }

    @Test
    public void testJoin1() throws Exception {
        Statement statement = parse("select x, y from (select x from tab t2 latest by x where x > 100) t1 " +
                "join tab2 xx2 on tab2.x = t1.x " +
                "join tab3 on xx2.x > tab3.b " +
                "join (select x,y from tab4 latest by z where a > b) x4 on x4.x = t1.y " +
                "where y > 0");

        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        Assert.assertEquals("t1", statement.getQueryModel().getAlias());
        Assert.assertEquals(3, statement.getQueryModel().getJoinModels().size());
        Assert.assertNotNull(statement.getQueryModel().getNestedQuery());
        Assert.assertNull(statement.getQueryModel().getJournalName());
        Assert.assertEquals("y0>", TestUtils.toRpn(statement.getQueryModel().getWhereClause()));
        Assert.assertEquals("tab", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getJournalName()));
        Assert.assertEquals("t2", statement.getQueryModel().getNestedQuery().getAlias());
        Assert.assertEquals(0, statement.getQueryModel().getNestedQuery().getJoinModels().size());

        Assert.assertEquals("xx2", statement.getQueryModel().getJoinModels().getQuick(0).getAlias());
        Assert.assertNull(statement.getQueryModel().getJoinModels().getQuick(1).getAlias());
        Assert.assertEquals("x4", statement.getQueryModel().getJoinModels().getQuick(2).getAlias());
        Assert.assertNotNull(statement.getQueryModel().getJoinModels().getQuick(2).getNestedQuery());

        Assert.assertEquals("tab2", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(0).getJournalName()));
        Assert.assertEquals("tab3", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(1).getJournalName()));
        Assert.assertNull(statement.getQueryModel().getJoinModels().getQuick(2).getJournalName());

        Assert.assertEquals("tab2.xt1.x=", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(0).getJoinCriteria()));
        Assert.assertEquals("xx2.xtab3.b>", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(1).getJoinCriteria()));
        Assert.assertEquals("x4.xt1.y=", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(2).getJoinCriteria()));

        Assert.assertEquals("ab>", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(2).getNestedQuery().getWhereClause()));
        Assert.assertEquals("z", TestUtils.toRpn(statement.getQueryModel().getJoinModels().getQuick(2).getNestedQuery().getLatestBy()));
    }

    @Test
    public void testJoin2() throws Exception {
        Statement statement = parse("select x from ((tab join tab2 on tab.x=tab2.x) join tab3 on tab3.x = tab2.x)");
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals(0, statement.getQueryModel().getJoinModels().size());
        Assert.assertNotNull(statement.getQueryModel().getNestedQuery());
        Assert.assertEquals(1, statement.getQueryModel().getNestedQuery().getJoinModels().size());
        Assert.assertEquals("tab3", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getJoinModels().getQuick(0).getJournalName()));
        Assert.assertEquals("tab3.xtab2.x=", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getJoinModels().getQuick(0).getJoinCriteria()));
        Assert.assertEquals(0, statement.getQueryModel().getNestedQuery().getColumns().size());
        Assert.assertNotNull(statement.getQueryModel().getNestedQuery().getNestedQuery());
        Assert.assertEquals("tab", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getNestedQuery().getJournalName()));
        Assert.assertEquals(1, statement.getQueryModel().getNestedQuery().getNestedQuery().getJoinModels().size());
        Assert.assertEquals("tab2", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getNestedQuery().getJoinModels().getQuick(0).getJournalName()));
        Assert.assertEquals("tab.xtab2.x=", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getNestedQuery().getJoinModels().getQuick(0).getJoinCriteria()));
    }

    @Test
    public void testMostRecentWhereClause() throws Exception {
        QueryParser parser = new QueryParser();
        parser.setContent("select a+b*c x, sum(z)+25 ohoh from zyzy latest by x where a in (x,y) and b = 10");
        Statement statement = parser.parse();
        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        // journal name
        Assert.assertEquals("zyzy", statement.getQueryModel().getJournalName().token);
        // columns
        Assert.assertEquals(2, statement.getQueryModel().getColumns().size());
        Assert.assertEquals("x", statement.getQueryModel().getColumns().get(0).getName());
        Assert.assertEquals("ohoh", statement.getQueryModel().getColumns().get(1).getName());
        // where
        Assert.assertEquals("axyinb10=and", TestUtils.toRpn(statement.getQueryModel().getWhereClause()));
        // latest by
        Assert.assertEquals("x", TestUtils.toRpn(statement.getQueryModel().getLatestBy()));
    }

    @Test
    public void testMultipleExpressions() throws Exception {
        QueryParser parser = new QueryParser();
        parser.setContent("select a+b*c x, sum(z)+25 ohoh from zyzy");
        Statement statement = parser.parse();
        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals("zyzy", statement.getQueryModel().getJournalName().token);
        Assert.assertEquals(2, statement.getQueryModel().getColumns().size());
        Assert.assertEquals("x", statement.getQueryModel().getColumns().get(0).getName());
        Assert.assertEquals("ohoh", statement.getQueryModel().getColumns().get(1).getName());
    }

    @Test
    public void testOptionalSelect() throws Exception {
        Statement statement = parse("tab t2 latest by x where x > 100");
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals("tab", TestUtils.toRpn(statement.getQueryModel().getJournalName()));
        Assert.assertEquals("t2", statement.getQueryModel().getAlias());
        Assert.assertEquals("x100>", TestUtils.toRpn(statement.getQueryModel().getWhereClause()));
        Assert.assertEquals(0, statement.getQueryModel().getColumns().size());
        Assert.assertEquals("x", TestUtils.toRpn(statement.getQueryModel().getLatestBy()));
    }

    @Test
    public void testOrderBy1() throws Exception {
        Statement statement = parse("select x,y from tab order by x,y,z");
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals(3, statement.getQueryModel().getOrderBy().size());
        Assert.assertEquals("x", TestUtils.toRpn(statement.getQueryModel().getOrderBy().getQuick(0)));
        Assert.assertEquals("y", TestUtils.toRpn(statement.getQueryModel().getOrderBy().getQuick(1)));
        Assert.assertEquals("z", TestUtils.toRpn(statement.getQueryModel().getOrderBy().getQuick(2)));
    }

    @Test
    public void testSelectPlainColumns() throws Exception {
        Statement statement = parse("select a,b,c from t");

        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals("t", statement.getQueryModel().getJournalName().token);
        Assert.assertEquals(3, statement.getQueryModel().getColumns().size());
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(ExprNode.NodeType.LITERAL, statement.getQueryModel().getColumns().get(i).getAst().type);
        }
    }

    @Test
    public void testSelectSingleExpression() throws Exception {
        Statement statement = parse("select a+b*c x from t");
        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertEquals(1, statement.getQueryModel().getColumns().size());
        Assert.assertEquals("x", statement.getQueryModel().getColumns().get(0).getName());
        Assert.assertEquals("+", statement.getQueryModel().getColumns().get(0).getAst().token);
        Assert.assertEquals("t", statement.getQueryModel().getJournalName().token);
    }

    @Test
    public void testSubQuery() throws Exception {
        Statement statement = parse("select x, y from (select x from tab t2 latest by x where x > 100) t1 " +
                "where y > 0");
        Assert.assertNotNull(statement.getQueryModel());
        Assert.assertNotNull(statement.getQueryModel().getNestedQuery());
        Assert.assertNull(statement.getQueryModel().getJournalName());
        Assert.assertEquals("t1", statement.getQueryModel().getAlias());

        Assert.assertEquals("tab", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getJournalName()));
        Assert.assertEquals("t2", statement.getQueryModel().getNestedQuery().getAlias());
        Assert.assertEquals("x100>", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getWhereClause()));
        Assert.assertEquals("x", TestUtils.toRpn(statement.getQueryModel().getNestedQuery().getLatestBy()));
    }

    @Test
    public void testUnbalancedBracketInSubQuery() throws Exception {
        try {
            parse("select x from (tab where x > 10 t1");
            Assert.fail("Exception expected");
        } catch (ParserException e) {
            Assert.assertEquals(32, e.getPosition());
            Assert.assertTrue(e.getMessage().contains("expected"));
        }
    }

    @Test
    public void testWhereClause() throws Exception {
        Statement statement = parse("select a+b*c x, sum(z)+25 ohoh from zyzy where a in (x,y) and b = 10");
        Assert.assertEquals(StatementType.QUERY_JOURNAL, statement.getType());
        // journal name
        Assert.assertEquals("zyzy", statement.getQueryModel().getJournalName().token);
        // columns
        Assert.assertEquals(2, statement.getQueryModel().getColumns().size());
        Assert.assertEquals("x", statement.getQueryModel().getColumns().get(0).getName());
        Assert.assertEquals("ohoh", statement.getQueryModel().getColumns().get(1).getName());
        // where
        Assert.assertEquals("axyinb10=and", TestUtils.toRpn(statement.getQueryModel().getWhereClause()));
    }

    private Statement parse(CharSequence query) throws ParserException {
        parser.setContent(query);
        return parser.parse();
    }
}