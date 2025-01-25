package com.sywyar.midiplayinidv;

import java.util.*;

public class MultiValueMap<K, V> {
    private final Map<K, List<V>> map;

    public MultiValueMap() {
        this.map = new HashMap<>();
    }

    public void put(K key, V value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public List<V> get(K key) {
        return map.getOrDefault(key, Collections.emptyList());
    }

    public boolean remove(K key, V value) {
        List<V> values = map.get(key);
        if (values != null) {
            boolean removed = values.remove(value);
            if (values.isEmpty()) {
                map.remove(key); // 如果列表为空，移除键
            }
            return removed;
        }
        return false;
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
