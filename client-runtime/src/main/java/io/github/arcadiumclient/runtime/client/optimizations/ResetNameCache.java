// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.LinkedHashMap;

final class ResetNameCache {
    private final int maximumEntries, maximumCharacters;
    private final LinkedHashMap<String, String> entries = new LinkedHashMap<>(32, .75f, true);
    private int characters;
    private boolean hit;

    ResetNameCache(int maximumEntries, int maximumCharacters) {
        if (maximumEntries < 1 || maximumCharacters < 2) throw new IllegalArgumentException("Invalid name cache budget");
        this.maximumEntries = maximumEntries; this.maximumCharacters = maximumCharacters;
    }

    String format(String name) {
        String key = String.valueOf(name);
        String existing = entries.get(key);
        hit = existing != null;
        if (hit) return existing;
        String result = key + "\u00a7r";
        long retained = (long) key.length() + result.length();
        if (retained > maximumCharacters) return result;
        while (!entries.isEmpty() && (entries.size() >= maximumEntries || characters + retained > maximumCharacters)) {
            var oldest = entries.entrySet().iterator(); var entry = oldest.next();
            characters -= entry.getKey().length() + entry.getValue().length(); oldest.remove();
        }
        entries.put(key, result); characters += (int) retained;
        return result;
    }

    boolean hit() { return hit; }
    int characters() { return characters; }
    int size() { return entries.size(); }
    void clear() { entries.clear(); characters = 0; hit = false; }
}
