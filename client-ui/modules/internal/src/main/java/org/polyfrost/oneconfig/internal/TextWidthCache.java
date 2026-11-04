// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextWidthCache {
    public static final int MISSING = Integer.MIN_VALUE;
    private Object source;
    private boolean unicode;
    private int revision;
    private long hits, misses;
    private final Map<String, Integer> values = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) { return size() > 2048; }
    };

    public int get(String text, Object source, boolean unicode, int revision) {
        scope(source, unicode, revision);
        if (text.length() > 4096) return MISSING;
        Integer value = values.get(text);
        if (value == null) { misses++; return MISSING; }
        hits++; return value;
    }
    public void put(String text, int width, Object source, boolean unicode, int revision) {
        scope(source, unicode, revision);
        if (text.length() <= 4096) values.put(text, width);
    }
    public void clear() { values.clear(); }
    public long hits() { return hits; }
    public long misses() { return misses; }
    private void scope(Object source, boolean unicode, int revision) {
        if (this.source != source || this.unicode != unicode || this.revision != revision) {
            values.clear(); this.source = source; this.unicode = unicode; this.revision = revision;
        }
    }
}
