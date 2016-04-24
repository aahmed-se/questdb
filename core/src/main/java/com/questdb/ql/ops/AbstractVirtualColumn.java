/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (c) 2014-2016 Appsicle
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
 ******************************************************************************/

package com.questdb.ql.ops;

import com.questdb.io.sink.CharSink;
import com.questdb.ql.Record;
import com.questdb.std.DirectInputStream;
import com.questdb.store.ColumnType;
import com.questdb.store.SymbolTable;

import java.io.OutputStream;

public abstract class AbstractVirtualColumn implements VirtualColumn {
    private final ColumnType type;
    private String name;

    protected AbstractVirtualColumn(ColumnType type) {
        this.type = type;
    }

    @Override
    public byte get(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getBin(Record rec, OutputStream s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectInputStream getBin(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBinLen(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBool(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getDate(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getFlyweightStr(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getFlyweightStrB(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence getStr(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getStr(Record rec, CharSink sink) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getStrLen(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSym(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getBucketCount() {
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public SymbolTable getSymbolTable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ColumnType getType() {
        return type;
    }

    @Override
    public boolean isIndexed() {
        return false;
    }
}