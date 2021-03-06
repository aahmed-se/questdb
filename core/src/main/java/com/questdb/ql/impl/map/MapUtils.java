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

package com.questdb.ql.impl.map;

import com.questdb.ex.JournalRuntimeException;
import com.questdb.ql.Record;
import com.questdb.ql.impl.CollectionRecordMetadata;
import com.questdb.ql.impl.LongMetadata;
import com.questdb.ql.ops.VirtualColumn;
import com.questdb.std.IntList;
import com.questdb.std.ObjList;
import com.questdb.store.ColumnType;

public class MapUtils {
    public static final IntList ROWID_MAP_VALUES = new IntList(1);
    public static final CollectionRecordMetadata ROWID_RECORD_METADATA = new CollectionRecordMetadata().add(LongMetadata.INSTANCE);
    private static final ThreadLocal<IntList> tlTypeList = new ThreadLocal<IntList>() {
        @Override
        protected IntList initialValue() {
            return new IntList(1);
        }
    };

    private MapUtils() {
    }

    public static DirectMapValues getMapValues(DirectMap map, Record rec, ObjList<VirtualColumn> partitionBy) {
        final DirectMap.KeyWriter kw = map.keyWriter();
        for (int i = 0, n = partitionBy.size(); i < n; i++) {
            writeVirtualColumn(kw, rec, partitionBy.getQuick(i));
        }
        return map.getOrCreateValues(kw);
    }

    public static IntList toTypeList(int type1, int type2) {
        IntList l = tlTypeList.get();
        l.clear();
        l.add(type1);
        l.add(type2);
        return l;
    }

    public static IntList toTypeList(int type) {
        IntList l = tlTypeList.get();
        l.clear();
        l.add(type);
        return l;
    }

    public static void writeVirtualColumn(DirectMap.KeyWriter w, Record r, VirtualColumn vc) {
        switch (vc.getType()) {
            case ColumnType.BOOLEAN:
                w.putBool(vc.getBool(r));
                break;
            case ColumnType.BYTE:
                w.putByte(vc.get(r));
                break;
            case ColumnType.DOUBLE:
                w.putDouble(vc.getDouble(r));
                break;
            case ColumnType.INT:
                w.putInt(vc.getInt(r));
                break;
            case ColumnType.LONG:
                w.putLong(vc.getLong(r));
                break;
            case ColumnType.SHORT:
                w.putShort(vc.getShort(r));
                break;
            case ColumnType.FLOAT:
                w.putFloat(vc.getFloat(r));
                break;
            case ColumnType.STRING:
                w.putStr(vc.getFlyweightStr(r));
                break;
            case ColumnType.SYMBOL:
                w.putInt(vc.getInt(r));
                break;
            case ColumnType.BINARY:
                w.putBin(vc.getBin(r));
                break;
            case ColumnType.DATE:
                w.putLong(vc.getDate(r));
                break;
            default:
                throw new JournalRuntimeException("Unsupported type: " + vc.getType());
        }
    }

    static {
        ROWID_MAP_VALUES.add(ColumnType.LONG);
    }
}
