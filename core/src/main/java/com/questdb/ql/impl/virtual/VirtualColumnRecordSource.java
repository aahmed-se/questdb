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

package com.questdb.ql.impl.virtual;

import com.questdb.factory.JournalReaderFactory;
import com.questdb.factory.configuration.RecordMetadata;
import com.questdb.misc.Misc;
import com.questdb.ql.*;
import com.questdb.ql.impl.join.SplitRecordStorageFacade;
import com.questdb.ql.ops.AbstractCombinedRecordSource;
import com.questdb.ql.ops.VirtualColumn;
import com.questdb.std.CharSink;
import com.questdb.std.ObjList;
import com.questdb.store.SymbolTable;

public class VirtualColumnRecordSource extends AbstractCombinedRecordSource {
    private final RecordSource delegate;
    private final RecordMetadata metadata;
    private final VirtualRecord record;
    private final SplitRecordStorageFacade storageFacade;
    private final VirtualColumnStorageFacade virtualColumnStorageFacade;
    private RecordCursor cursor;

    public VirtualColumnRecordSource(RecordSource delegate, ObjList<VirtualColumn> virtualColumns) {
        this.delegate = delegate;
        RecordMetadata dm = delegate.getMetadata();
        this.metadata = new VirtualRecordMetadata(dm, virtualColumns);
        this.record = new VirtualRecord(dm.getColumnCount(), virtualColumns, delegate.getRecord());
        this.virtualColumnStorageFacade = VirtualColumnStorageFacade.INSTANCE;
        this.storageFacade = new SplitRecordStorageFacade(dm.getColumnCount());
    }

    @Override
    public void close() {
        Misc.free(delegate);
    }

    @Override
    public RecordMetadata getMetadata() {
        return metadata;
    }

    @Override
    public RecordCursor prepareCursor(JournalReaderFactory factory, CancellationHandler cancellationHandler) {
        this.cursor = delegate.prepareCursor(factory, cancellationHandler);
        storageFacade.prepare(cursor.getStorageFacade(), this.virtualColumnStorageFacade);
        record.prepare(storageFacade);
        return this;
    }

    @Override
    public Record getRecord() {
        return record;
    }

    @Override
    public Record newRecord() {
        return record.copy(delegate.newRecord());
    }

    @Override
    public StorageFacade getStorageFacade() {
        return storageFacade;
    }

    @Override
    public void toTop() {
        this.cursor.toTop();
    }

    @Override
    public boolean hasNext() {
        return cursor.hasNext();
    }

    @Override
    public Record next() {
        cursor.next();
        return record;
    }

    @Override
    public Record recordAt(long rowId) {
        cursor.recordAt(rowId);
        return record;
    }

    @Override
    public void recordAt(Record record, long atRowId) {
        cursor.recordAt(((VirtualRecord) record).getBase(), atRowId);
    }

    @Override
    public boolean supportsRowIdAccess() {
        return delegate.supportsRowIdAccess();
    }

    @Override
    public void toSink(CharSink sink) {
        sink.put('{');
        sink.putQuoted("op").put(':').putQuoted("VirtualColumnRecordSource").put(',');
        sink.putQuoted("src").put(':').put(delegate);
        sink.put('}');
    }

    private static class VirtualColumnStorageFacade implements StorageFacade {

        private static final VirtualColumnStorageFacade INSTANCE = new VirtualColumnStorageFacade();

        @Override
        public SymbolTable getSymbolTable(int index) {
            return null;
        }
    }
}
