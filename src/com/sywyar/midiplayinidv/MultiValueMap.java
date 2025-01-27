package com.sywyar.midiplayinidv;

import java.util.*;

public class MultiValueMap<K, V> {
    private final Map<K, ValueContainer<V>> map;

    public MultiValueMap() {
        this.map = new HashMap<>();
    }

    public void put(K key, V value, double bpm) {
        map.computeIfAbsent(key, k -> new ValueContainer<>(bpm)).add(value);
    }

    public List<V> getValues(K key) {
        ValueContainer<V> container = map.get(key);
        return container != null ? container.getValues() : Collections.emptyList();
    }

    public double getBpm(K key) {
        ValueContainer<V> container = map.get(key);
        if (container != null) {
            return container.getBpm();
        }
        throw new IllegalArgumentException("BPM not found for key: " + key);
    }

    public boolean remove(K key, V value) {
        ValueContainer<V> container = map.get(key);
        if (container != null) {
            boolean removed = container.remove(value);
            if (container.isEmpty()) {
                map.remove(key);
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

    private static class ValueContainer<V> {
        private final List<V> values;
        private final double bpm;

        public ValueContainer(double bpm) {
            this.values = new ArrayList<>();
            this.bpm = bpm;
        }

        public void add(V value) {
            values.add(value);
        }

        public List<V> getValues() {
            return values;
        }

        public double getBpm() {
            return bpm;
        }

        public boolean remove(V value) {
            return values.remove(value);
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public String toString() {
            return "Values: " + values + ", BPM: " + bpm;
        }
    }
}