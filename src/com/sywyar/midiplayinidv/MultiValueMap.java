package com.sywyar.midiplayinidv;

import java.util.*;

public class MultiValueMap<K, V> {
    private final Map<K, ValueContainer<V>> map;
    private List<TempoEvent> tempoEvents;
    private final long resolution;
    private final String fileName;
    private String songName = "";
    private HashSet<Integer> ignoreEvents = new HashSet<>();
    private long totalMillis = 0;

    public void setTempoEvents(List<TempoEvent> tempoEvents) {
        this.tempoEvents = tempoEvents;
    }

    public List<TempoEvent> getTempoEvents() {
        return tempoEvents;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public void setTotalMillis(long totalMillis) {
        this.totalMillis = totalMillis;
    }

    public long getResolution() {
        return resolution;
    }

    public HashSet<Integer> getIgnoreEvents() {
        return ignoreEvents;
    }

    public void setIgnoreEvents(HashSet<Integer> ignoreEvents) {
        this.ignoreEvents = ignoreEvents;
    }

    public String getSongName() {
        return songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public String getFileName() {
        return fileName;
    }

    public MultiValueMap(long resolution, String fileName) {
        this.map = new HashMap<>();
        this.resolution = resolution;
        this.fileName = fileName;
    }

    public void put(K key, V value, int tempo) {
        map.computeIfAbsent(key, k -> new ValueContainer<>(tempo)).add(value);
    }

    public List<V> getValues(K key) {
        ValueContainer<V> container = map.get(key);
        return container != null ? container.getValues() : Collections.emptyList();
    }

    public double getTempo(K key) {
        ValueContainer<V> container = map.get(key);
        if (container != null) {
            return container.getTempo();
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
        private final int tempo;

        public ValueContainer(int tempo) {
            this.values = new ArrayList<>();
            this.tempo = tempo;
        }

        public void add(V value) {
            values.add(value);
        }

        public List<V> getValues() {
            return values;
        }

        public double getTempo() {
            return tempo;
        }

        public boolean remove(V value) {
            return values.remove(value);
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public String toString() {
            return "Values: " + values + ", Tempo: " + tempo;
        }
    }
}