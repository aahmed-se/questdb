/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.test.tools;

import com.questdb.ex.ParserException;
import com.questdb.io.RecordSourcePrinter;
import com.questdb.io.sink.StringSink;
import com.questdb.misc.Files;
import com.questdb.misc.Unsafe;
import com.questdb.model.configuration.ModelConfiguration;
import com.questdb.ql.Record;
import com.questdb.ql.RecordCursor;
import com.questdb.ql.RecordSource;
import com.questdb.ql.parser.QueryCompiler;
import com.questdb.ql.parser.QueryError;
import com.questdb.store.SymbolTable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;

public abstract class AbstractTest {
    @Rule
    public final JournalTestFactory factory = new JournalTestFactory(ModelConfiguration.MAIN.build(Files.makeTempDir()));

    protected final StringSink sink = new StringSink();
    protected final RecordSourcePrinter printer = new RecordSourcePrinter(sink);
    private final QueryCompiler compiler = new QueryCompiler();

    public void assertSymbol(String query, int columnIndex) throws ParserException {
        try (RecordSource src = compiler.compile(factory, query)) {
            RecordCursor cursor = src.prepareCursor(factory);
            SymbolTable tab = cursor.getStorageFacade().getSymbolTable(columnIndex);
            Assert.assertNotNull(factory);
            while (cursor.hasNext()) {
                Record r = cursor.next();
                TestUtils.assertEquals(r.getSym(columnIndex), tab.value(r.getInt(columnIndex)));
            }
        }
    }

    @Before
    public void setUp2() throws Exception {
        factory.getConfiguration().exists("none");
    }

    protected void assertEmpty(String query) throws ParserException {
        try (RecordSource src = compiler.compile(factory, query)) {
            Assert.assertFalse(src.prepareCursor(factory).hasNext());
        }
    }

    protected void assertPlan(CharSequence plan, CharSequence query) throws ParserException {
        long memUsed = Unsafe.getMemUsed();
        try (RecordSource recordSource = compile(query)) {
            sink.clear();
            sink.put(recordSource);
            TestUtils.assertEquals(plan, sink);
        }
        Assert.assertEquals(memUsed, Unsafe.getMemUsed());
    }

    protected void assertThat(String expected, String query, boolean header) throws ParserException, IOException {
        long memUsed = Unsafe.getMemUsed();
        try (RecordSource src = compiler.compile(factory, query)) {
            RecordCursor cursor = src.prepareCursor(factory);

            sink.clear();
            printer.print(cursor, header, src.getMetadata());
            TestUtils.assertEquals(expected, sink);

            cursor.toTop();

            sink.clear();
            printer.print(cursor, header, src.getMetadata());
            TestUtils.assertEquals(expected, sink);

            TestUtils.assertStrings(src, factory);
        } catch (ParserException e) {
            System.out.println(QueryError.getMessage());
            System.out.println(QueryError.getPosition());
            throw e;
        }
        Assert.assertEquals(memUsed, Unsafe.getMemUsed());
    }

    protected void assertThat(String expected, String query) throws ParserException, IOException {
        assertThat(expected, query, false);
        assertThat(expected, query, false);
    }

    protected RecordSource compile(CharSequence query) throws ParserException {
        return compiler.compile(factory, query);
    }

    protected void expectFailure(CharSequence query) throws ParserException {
        long memUsed = Unsafe.getMemUsed();
        try {
            compile(query);
            Assert.fail();
        } catch (ParserException e) {
            Assert.assertEquals(memUsed, Unsafe.getMemUsed());
            throw e;
        }
    }
}
