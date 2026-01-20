/*
 * This file is part of unsup.
 * Copyright © 2025 Exa Skye
 * https://git.sleeping.town/exa/unsup
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

package com.unascribed.sup.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Multimap<K, V> {

	protected final Map<K, List<V>> delegate;

	public Multimap(Map<K, List<V>> delegate) {
		this.delegate = delegate;
	}
	
	public Multimap() {
		this(new LinkedHashMap<>());
	}
	
	public List<V> get(K k) {
		return delegate.getOrDefault(k, Collections.emptyList());
	}
	
	public void put(K k, V v) {
		var li = delegate.get(k);
		if (li == null) {
			li = new ArrayList<>();
			delegate.put(k, li);
		}
		li.add(v);
	}
	
	public Set<Map.Entry<K, List<V>>> mapEntries() {
		return delegate.entrySet();
	}
	
	public Multimap<K, V> unmodifiable() {
		Map<K, List<V>> out = new HashMap<>(delegate);
		for (var en : out.entrySet()) {
			en.setValue(Collections.unmodifiableList(en.getValue()));
		}
		return new Multimap<>(Collections.unmodifiableMap(out));
	}
	
	@Override
	public String toString() {
		return delegate.toString();
	}
	
}
