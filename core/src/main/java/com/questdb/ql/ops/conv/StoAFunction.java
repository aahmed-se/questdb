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

package com.questdb.ql.ops.conv;

import com.questdb.ql.Record;
import com.questdb.ql.ops.AbstractUnaryOperator;
import com.questdb.ql.ops.Function;
import com.questdb.ql.ops.VirtualColumnFactory;
import com.questdb.std.CharSink;
import com.questdb.store.ColumnType;
import com.questdb.store.VariableColumn;

public class StoAFunction extends AbstractUnaryOperator {

    public final static VirtualColumnFactory<Function> FACTORY = new VirtualColumnFactory<Function>() {
        @Override
        public Function newInstance(int position) {
            return new StoAFunction(position);
        }
    };

    private StoAFunction(int position) {
        super(ColumnType.STRING, position);
    }

    @Override
    public CharSequence getFlyweightStr(Record rec) {
        return value.getSym(rec);
    }

    @Override
    public CharSequence getFlyweightStrB(Record rec) {
        return getFlyweightStr(rec);
    }

    @Override
    public CharSequence getStr(Record rec) {
        return value.getSym(rec);
    }

    @Override
    public void getStr(Record rec, CharSink sink) {
        sink.put(value.getSym(rec));
    }

    @Override
    public int getStrLen(Record rec) {
        CharSequence cs = value.getSym(rec);
        return cs == null ? VariableColumn.NULL_LEN : cs.length();
    }
}
