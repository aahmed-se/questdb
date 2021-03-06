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

package com.questdb.std;

public class FlyweightCharSequence extends AbstractCharSequence implements Mutable {
    public static final ObjectFactory<FlyweightCharSequence> FACTORY = new ObjectFactory<FlyweightCharSequence>() {
        @Override
        public FlyweightCharSequence newInstance() {
            return new FlyweightCharSequence();
        }
    };

    private CharSequence delegate;
    private int lo;
    private int len;

    @Override
    public void clear() {

    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public char charAt(int index) {
        return delegate.charAt(index + lo);
    }

    public FlyweightCharSequence of(CharSequence delegate, int lo, int len) {
        this.delegate = delegate;
        this.lo = lo;
        this.len = len;
        return this;
    }

    public FlyweightCharSequence ofQuoted(CharSequence delegate) {
        return of(delegate, 1, delegate.length() - 2);
    }
}
