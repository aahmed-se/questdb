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

package com.questdb.net.http;

import com.questdb.ex.NetworkError;
import com.questdb.iter.clock.Clock;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.misc.*;
import com.questdb.mp.*;
import com.questdb.net.NetworkChannelImpl;
import com.questdb.std.LongIntHashMap;
import com.questdb.std.LongMatrix;

import java.io.IOException;

public class Win32SelectDispatcher extends SynchronizedJob implements IODispatcher {

    private static final int M_TIMESTAMP = 0;
    private static final int M_FD = 1;
    private static final int M_OPERATION = 2;
    private static final Log LOG = LogFactory.getLog(Win32SelectDispatcher.class);
    private static final int COUNT_OFFSET;
    private static final int ARRAY_OFFSET;
    private static final int FD_READ = 1;
    private static final int FD_WRITE = 2;
    private final FDSet readFdSet;
    private final FDSet writeFdSet;
    private final long socketFd;
    private final RingQueue<IOEvent> ioQueue;
    private final Sequence ioSequence;
    private final RingQueue<IOEvent> interestQueue;
    private final MPSequence interestPubSequence;
    private final SCSequence interestSubSequence = new SCSequence();
    private final Clock clock;
    private final ServerConfiguration configuration;
    private final int timeout;
    private final LongMatrix<IOContext> pending = new LongMatrix<>(4);
    private final int maxConnections;
    private final LongIntHashMap fds = new LongIntHashMap();
    private int connectionCount = 0;

    public Win32SelectDispatcher(
            CharSequence ip,
            int port,
            RingQueue<IOEvent> ioQueue,
            Sequence ioSequence,
            Clock clock,
            ServerConfiguration configuration,
            int capacity
    ) {
        this.readFdSet = new FDSet(capacity);
        this.writeFdSet = new FDSet(capacity);
        this.ioQueue = ioQueue;
        this.ioSequence = ioSequence;
        this.interestQueue = new RingQueue<>(IOEvent.FACTORY, ioQueue.getCapacity());
        this.interestPubSequence = new MPSequence(interestQueue.getCapacity());
        this.interestPubSequence.then(this.interestSubSequence).then(this.interestPubSequence);
        this.clock = clock;
        this.configuration = configuration;
        this.maxConnections = configuration.getHttpMaxConnections();
        this.timeout = configuration.getHttpTimeout();

        // bind socket
        this.socketFd = Net.socketTcp(false);
        if (Net.bind(this.socketFd, ip, port)) {
            Net.listen(this.socketFd, 128);
            int r = pending.addRow();
            pending.set(r, M_TIMESTAMP, System.currentTimeMillis());
            pending.set(r, M_FD, socketFd);
            pending.set(r, M_OPERATION, ChannelStatus.READ);
            readFdSet.add(socketFd);
            readFdSet.setCount(1);
            writeFdSet.setCount(0);
        } else {
            throw new NetworkError("Failed to bind socket. System error " + Os.errno());
        }
    }

    @Override
    public void close() throws IOException {
        readFdSet.close();
        writeFdSet.close();

        for (int i = 0, n = pending.size(); i < n; i++) {
            Files.close(pending.get(i, M_FD));
            Misc.free(pending.get(i));
        }

        pending.zapTop(pending.size());
    }

    @Override
    public int getConnectionCount() {
        return connectionCount;
    }

    @Override
    public void registerChannel(IOContext context, int channelStatus) {
        long cursor = interestPubSequence.nextBully();
        IOEvent evt = interestQueue.get(cursor);
        evt.context = context;
        evt.channelStatus = channelStatus;
        LOG.debug().$("Re-queuing ").$(channelStatus).$(" on ").$(context.channel.getFd()).$();
        interestPubSequence.done(cursor);
    }

    private static native int select(long readfds, long writefds, long exceptfds);

    private static native int countOffset();

    private static native int arrayOffset();

    private void accept(long timestamp) {
        while (true) {
            long _fd = Net.accept(socketFd);

            if (_fd < 0) {
                int err = Os.errno();
                if (err != Net.EWOULDBLOCK && err != 0) {
                    LOG.error().$("Error in accept(): ").$(err).$();
                }
                break;
            }

            LOG.info().$(" Connected ").$(_fd).$();

            if (Net.configureNonBlocking(_fd) < 0) {
                LOG.error().$("Cannot make FD non-blocking").$();
                Files.close(_fd);
                continue;
            }

            connectionCount++;

            if (connectionCount > maxConnections) {
                LOG.info().$("Too many connections, kicking out ").$(_fd).$();
                Files.close(_fd);
                connectionCount--;
                return;
            }

            addPending(_fd, timestamp);
        }
    }

    private void addPending(long _fd, long timestamp) {
        int r = pending.addRow();
        LOG.debug().$(" Matrix row ").$(r).$(" for ").$(_fd).$();
        pending.set(r, M_TIMESTAMP, timestamp);
        pending.set(r, M_FD, _fd);
        pending.set(r, M_OPERATION, ChannelStatus.READ);
        NetworkChannelImpl channel = new NetworkChannelImpl(_fd);
        pending.set(r, new IOContext(channel, configuration, clock));
    }

    private void disconnect(IOContext context, int disconnectReason) {
        LOG.info().$("Disconnected ").$(context.channel.getFd()).$(": ").$(DisconnectReason.nameOf(disconnectReason)).$();
        context.close();
        connectionCount--;
    }

    private void enqueue(IOContext context, int channelStatus) {
        long cursor = ioSequence.nextBully();
        IOEvent evt = ioQueue.get(cursor);
        evt.context = context;
        evt.channelStatus = channelStatus;
        ioSequence.done(cursor);
        LOG.debug().$("Queuing ").$(channelStatus).$(" on ").$(context.channel.getFd()).$();

    }

    private boolean processRegistrations(long timestamp) {
        long cursor;
        boolean useful = false;
        while ((cursor = interestSubSequence.next()) > -1) {
            useful = true;
            IOEvent evt = interestQueue.get(cursor);
            IOContext context = evt.context;
            int channelStatus = evt.channelStatus;
            interestSubSequence.done(cursor);

            int r = pending.addRow();
            pending.set(r, M_TIMESTAMP, timestamp);
            pending.set(r, M_FD, context.channel.getFd());
            pending.set(r, M_OPERATION, channelStatus);
            pending.set(r, context);
        }

        return useful;
    }

    private void queryFdSets(long timestamp) {
        for (int i = 0, n = readFdSet.getCount(); i < n; i++) {
            long fd = readFdSet.get(i);

            if (fd == socketFd) {
                accept(timestamp);
            } else {
                fds.put(fd, FD_READ);
            }
        }

        // collect writes into hash map
        for (int i = 0, n = writeFdSet.getCount(); i < n; i++) {
            long fd = writeFdSet.get(i);
            int op = fds.get(fd);
            if (op == -1) {
                fds.put(fd, FD_WRITE);
            } else {
                fds.put(fd, FD_READ | FD_WRITE);
            }
        }
    }

    @Override
    protected boolean runSerially() {
        int count = select(readFdSet.address, writeFdSet.address, 0);
        if (count < 0) {
            LOG.error().$("Error in select(): ").$(Os.errno()).$();
            return false;
        }

        final long timestamp = System.currentTimeMillis();
        boolean useful = false;
        fds.clear();

        // collect reads into hash map
        if (count > 0) {
            queryFdSets(timestamp);
            useful = true;
        }

        // process returned fds
        useful = processRegistrations(timestamp) | useful;

        // re-arm select() fds
        int readFdCount = 0;
        int writeFdCount = 0;
        readFdSet.reset();
        writeFdSet.reset();
        long deadline = timestamp - timeout;
        for (int i = 0, n = pending.size(); i < n; ) {
            long ts = pending.get(i, M_TIMESTAMP);
            long fd = pending.get(i, M_FD);
            int _new_op = fds.get(fd);

            if (_new_op == -1) {

                // check if expired
                if (ts < deadline && fd != socketFd) {
                    disconnect(pending.get(i), DisconnectReason.IDLE);
                    pending.deleteRow(i);
                    n--;
                    useful = true;
                    continue;
                }

                // not fired, simply re-arm
                switch ((int) pending.get(i, M_OPERATION)) {
                    case ChannelStatus.READ:
                        readFdSet.add(fd);
                        readFdCount++;
                        i++;
                        break;
                    case ChannelStatus.WRITE:
                        writeFdSet.add(fd);
                        writeFdCount++;
                        i++;
                        break;
                    case ChannelStatus.DISCONNECTED:
                        disconnect(pending.get(i), DisconnectReason.SILLY);
                        pending.deleteRow(i);
                        n--;
                        useful = true;
                        break;
                    case ChannelStatus.EOF:
                        disconnect(pending.get(i), DisconnectReason.PEER);
                        pending.deleteRow(i);
                        n--;
                        useful = true;
                        break;
                    default:
                        break;
                }
            } else {
                // this fd just has fired
                // publish event
                // and remove from pending
                final IOContext context = pending.get(i);

                if ((_new_op & FD_READ) > 0) {
                    enqueue(context, ChannelStatus.READ);
                }

                if ((_new_op & FD_WRITE) > 0) {
                    enqueue(context, ChannelStatus.WRITE);
                }
                pending.deleteRow(i);
                n--;
            }
        }

        readFdSet.setCount(readFdCount);
        writeFdSet.setCount(writeFdCount);
        return useful;
    }

    private static class FDSet {
        private long address;
        private int size;
        private long _wptr;
        private long lim;

        private FDSet(int size) {
            int l = ARRAY_OFFSET + 8 * size;
            this.address = Unsafe.malloc(l);
            this.size = size;
            this._wptr = address + ARRAY_OFFSET;
            this.lim = address + l;
        }

        private void add(long fd) {
            if (_wptr == lim) {
                resize();
            }
            long p = _wptr;
            Unsafe.getUnsafe().putLong(p, fd);
            _wptr = p + 8;
        }

        private void close() {
            if (address != 0) {
                Unsafe.free(address, lim - address);
                address = 0;
            }
        }

        private long get(int index) {
            return Unsafe.getUnsafe().getLong(address + ARRAY_OFFSET + index * 8L);
        }

        private int getCount() {
            return Unsafe.getUnsafe().getInt(address + COUNT_OFFSET);
        }

        private void setCount(int count) {
            Unsafe.getUnsafe().putInt(address + COUNT_OFFSET, count);
        }

        private void reset() {
            _wptr = address + ARRAY_OFFSET;
        }

        private void resize() {
            int sz = size * 2;
            int l = ARRAY_OFFSET + 8 * sz;
            long _addr = Unsafe.malloc(l);
            Unsafe.getUnsafe().copyMemory(address, _addr, lim - address);
            Unsafe.free(address, lim - address);
            lim = _addr + l;
            size = sz;
            _wptr = _addr + (_wptr - address);
            address = _addr;
        }
    }

    static {
        Os.init();

        ARRAY_OFFSET = arrayOffset();
        COUNT_OFFSET = countOffset();
    }
}

