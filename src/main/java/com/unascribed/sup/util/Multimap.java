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
