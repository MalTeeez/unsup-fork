/*
 * This file is part of unsup.
 * Copyright © 2023, 2025 Una Kearney
 * https://git.sleeping.town/unascribed/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

package com.unascribed.sup.pieces;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class NullRejectingMap<K, V> extends AbstractMap<K, V> {
	private final Map<K, V> delegate;

	private NullRejectingMap(Map<K, V> delegate) {
		this.delegate = delegate;
	}

	public static <K, V> NullRejectingMap<K, V> of(Map<K, V> delegate) {
		return new NullRejectingMap<>(delegate);
	}
	public static <K, V> NullRejectingMap<K, V> create() {
		return of(new HashMap<>());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		var delegateSet = delegate.entrySet();
		return new AbstractSet<>() {

			@Override
			public int size() {
				return delegate.size();
			}

			@Override
			public Iterator<Entry<K, V>> iterator() {
				var delegateIter = delegateSet.iterator();
				return new Iterator<>() {
					@Override
					public boolean hasNext() {
						return delegateIter.hasNext();
					}
					
					@Override
					public Entry<K, V> next() {
						var delegateEn = delegateIter.next();
						return new Entry<>() {

							@Override
							public K getKey() {
								return delegateEn.getKey();
							}

							@Override
							public V getValue() {
								return delegateEn.getValue();
							}

							@Override
							public V setValue(V value) {
								if (value == null) throw new IllegalArgumentException("Cannot assign null to a key: "+getKey());
								return delegateEn.setValue(value);
							}
						};
					}
				};
			}

			@Override
			public boolean add(Entry<K, V> e) {
				return delegateSet.add(e);
			}

			@Override
			public boolean addAll(Collection<? extends Entry<K, V>> c) {
				return delegateSet.addAll(c);
			}
		};
	}

	@Override
	public V put(K key, V value) {
		if (key == null) throw new IllegalArgumentException("Cannot assign a value to a null key: "+value);
		if (value == null) throw new IllegalArgumentException("Cannot assign null to a key: "+key);
		return delegate.put(key, value);
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public V remove(Object key) {
		return delegate.remove(key);
	}
}