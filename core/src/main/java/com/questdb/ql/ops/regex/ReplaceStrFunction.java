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

package com.questdb.ql.ops.regex;

import com.questdb.ex.ParserException;
import com.questdb.ql.Record;
import com.questdb.ql.StorageFacade;
import com.questdb.ql.ops.AbstractVirtualColumn;
import com.questdb.ql.ops.Function;
import com.questdb.ql.ops.VirtualColumn;
import com.questdb.ql.ops.VirtualColumnFactory;
import com.questdb.ql.parser.QueryError;
import com.questdb.regex.Matcher;
import com.questdb.regex.Pattern;
import com.questdb.regex.PatternSyntaxException;
import com.questdb.std.CharSink;
import com.questdb.std.ConcatCharSequence;
import com.questdb.std.FlyweightCharSequence;
import com.questdb.store.ColumnType;

public class ReplaceStrFunction extends AbstractVirtualColumn implements Function {
    public final static VirtualColumnFactory<Function> FACTORY = new VirtualColumnFactory<Function>() {
        @Override
        public Function newInstance(int position) {
            return new ReplaceStrFunction(position);
        }
    };
    private CharSequence replacePatten;
    private VirtualColumn value;
    private Matcher matcher;
    private CharSequence base;

    public ReplaceStrFunction(int position) {
        super(ColumnType.STRING, position);
    }

    @Override
    public CharSequence getFlyweightStr(Record rec) {
        this.base = value.getFlyweightStr(rec);
        if (matcher.reset(base).find() && matcher.groupCount() > 0) {
            return replacePatten;
        }
        return null;
    }

    @Override
    public void getStr(Record rec, CharSink sink) {
        sink.put(getFlyweightStr(rec));
    }

    @Override
    public boolean isConstant() {
        return value.isConstant();
    }

    @Override
    public void prepare(StorageFacade facade) {
    }

    @Override
    public void setArg(int pos, VirtualColumn arg) throws ParserException {
        switch (pos) {
            case 0:
                compileRegex(arg);
                break;
            case 1:
                compileReplacePattern(arg);
                break;
            case 2:
                value = arg;
                break;
            default:
                throw QueryError.$(arg.getPosition(), "unexpected argument");
        }
    }

    private void compileRegex(VirtualColumn arg) throws ParserException {
        CharSequence pattern = arg.getStr(null);
        if (pattern == null) {
            throw QueryError.$(arg.getPosition(), "null regex?");
        }
        try {
            matcher = Pattern.compile(pattern.toString()).matcher("");
        } catch (PatternSyntaxException e) {
            throw QueryError.position(arg.getPosition() + e.getIndex() + 2 /* zero based index + quote symbol*/).$("Regex syntax error. ").$(e.getDescription()).$();
        }
    }

    private void compileReplacePattern(VirtualColumn arg) throws ParserException {
        CharSequence pattern = arg.getFlyweightStr(null);
        if (pattern == null) {
            throw QueryError.$(arg.getPosition(), "null pattern?");
        }

        int pos = arg.getPosition();
        int start = 0;
        int index = -1;
        int dollar = -2;

        ConcatCharSequence concat = new ConcatCharSequence();
        boolean collectIndex = false;
        int n = pattern.length();

        for (int i = 0; i < n; i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '$':
                    if (i == dollar + 1) {
                        throw QueryError.$(pos + i, "missing index");
                    }
                    if (i > start) {
                        concat.add(new FlyweightCharSequence().of(pattern, start, i - start));
                    }
                    collectIndex = true;
                    index = 0;
                    dollar = i;
                    break;
                default:
                    if (collectIndex) {
                        int k = c - '0';
                        if (k > -1 && k < 10) {
                            index = index * 10 + k;
                        } else {
                            if (i == dollar + 1) {
                                throw QueryError.$(pos + i, "missing index");
                            }
                            concat.add(new GroupCharSequence(index));
                            start = i;
                            collectIndex = false;
                            index = -1;
                        }
                    }
                    break;
            }
        }
        if (start < n) {
            concat.add(new FlyweightCharSequence().of(pattern, start, n - start));
        }
        this.replacePatten = concat;
    }

    private class GroupCharSequence implements CharSequence {
        private final int group;

        public GroupCharSequence(int group) {
            this.group = group;
        }

        @Override
        public int length() {
            if (base == null) {
                return 0;
            }

            if (group < matcher.groupCount()) {
                int lo = matcher.start(group);
                int hi = matcher.end(group);
                return hi - lo;
            } else {
                return 0;
            }
        }

        @Override
        public char charAt(int index) {
            return base.charAt(index + matcher.start(group));
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return null;
        }
    }
}
