// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DenickEvidence {
    public static final class Candidate {
        public final String name;
        public final String method;
        Candidate(String name, String method) { this.name = name; this.method = method; }
    }

    private final Set<String> knownSkins = new HashSet<>();
    private final List<Rule> rules = new ArrayList<>();
    private final Map<UUID, Candidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, Observation> observations = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> audit = new java.util.LinkedHashMap<>();
    private volatile boolean loaded;

    public void load(Path directory) {
        if (loaded) return;
        try (InputStream stream = getClass().getResourceAsStream("/assets/arcadium/bedwars/denick-skins.txt")) {
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) if (!line.trim().isEmpty()) knownSkins.add(line.trim());
            }
        } catch (Exception ignored) { }
        try (InputStream stream = getClass().getResourceAsStream("/assets/arcadium/bedwars/denick-killmessages.json")) {
            if (stream != null) try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonArray array = new JsonParser().parse(reader).getAsJsonArray();
                for (JsonElement value : array) {
                    JsonObject object = value.getAsJsonObject();
                    String sample = BedwarsData.string(object, "sample");
                    String id = BedwarsData.string(object, "id");
                    if (sample != null && id != null) rules.add(new Rule(id, sample));
                }
            }
        } catch (Exception ignored) { }
        try {
            Path file = directory.resolve("denick-observations.json");
            if (Files.isRegularFile(file) && Files.size(file) < 1024 * 1024) {
                JsonObject root = BedwarsData.object(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                if (root != null) for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getValue().isJsonObject() && audit.size() < 512) audit.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        } catch (Exception ignored) { }
        loaded = true;
    }

    public void clear() { candidates.clear(); observations.clear(); }
    public Candidate candidate(UUID id) { return candidates.get(id); }

    public Candidate skin(String nick, String encoded) {
        if (!loaded || encoded == null || encoded.length() > 32768) return null;
        try {
            JsonObject profile = BedwarsData.object(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
            String real = BedwarsData.string(profile, "profileName");
            String profileId = BedwarsData.string(profile, "profileId");
            JsonObject skin = BedwarsData.child(BedwarsData.child(profile, "textures"), "SKIN");
            String url = BedwarsData.string(skin, "url");
            if (!PlayerNameUtil.valid(real) || real.equalsIgnoreCase(nick) || url == null || profileId == null) return null;
            String hash = url.substring(url.lastIndexOf('/') + 1);
            if (!hash.matches("[a-fA-F0-9]{32,64}") || knownSkins.contains(hash)) return null;
            return new Candidate(real, "skin profile");
        } catch (RuntimeException ignored) { return null; }
    }

    public boolean remember(UUID id, Candidate candidate) {
        if (candidate == null) return false;
        Candidate before = candidates.put(id, candidate);
        return before == null || !before.name.equalsIgnoreCase(candidate.name) || !before.method.equals(candidate.method);
    }

    public synchronized void save(Path directory, UUID id, String nick, Candidate candidate) {
        JsonObject value = new JsonObject();
        value.addProperty("nick", nick);
        value.addProperty("candidate", candidate.name);
        value.addProperty("method", candidate.method);
        value.addProperty("confirmed", false);
        value.addProperty("seenAt", System.currentTimeMillis());
        audit.put(id.toString(), value);
        while (audit.size() > 512) audit.remove(audit.keySet().iterator().next());
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("denick-observations.json");
            Path temp = directory.resolve("denick-observations.json.tmp");
            Files.write(temp, new GsonBuilder().setPrettyPrinting().create().toJson(audit).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }
    }

    public void observe(String text) {
        if (!loaded || text.length() > 512) return;
        for (Rule rule : rules) {
            Matcher match = rule.pattern.matcher(text);
            if (!match.matches()) continue;
            String nick = match.group("killer").toLowerCase(java.util.Locale.ROOT);
            if (observations.size() >= 256 && !observations.containsKey(nick)) return;
            Observation observation = observations.computeIfAbsent(nick, ignored -> new Observation());
            observation.killMessage = rule.id;
            if (rule.counter) {
                try {
                    int counter = Integer.parseInt(match.group("counter").replace(",", ""));
                    if (rule.bed) observation.beds = counter;
                    else observation.finals = counter;
                } catch (NumberFormatException ignored) { }
            }
            return;
        }
    }

    public JsonObject cosmeticBody(String nick) {
        Observation observation = observations.get(nick.toLowerCase(java.util.Locale.ROOT));
        if (observation == null) return null;
        JsonObject body = new JsonObject();
        body.addProperty("nick", nick);
        body.addProperty("killMessage", observation.killMessage);
        body.addProperty("brute", false);
        body.addProperty("seenAt", System.currentTimeMillis());
        body.add("cosmetics", new JsonObject());
        if (observation.beds >= 0) body.addProperty("beds", observation.beds);
        if (observation.finals >= 0) body.addProperty("finals", observation.finals);
        return body;
    }

    public static List<String> cosmeticNames(String json) {
        Set<String> names = new LinkedHashSet<>();
        try { collect(new JsonParser().parse(json), names, 0); }
        catch (RuntimeException ignored) { }
        return new ArrayList<>(names);
    }

    private static void collect(JsonElement element, Set<String> names, int depth) {
        if (element == null || depth > 8 || names.size() >= 64) return;
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) collect(child, names, depth + 1);
        else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String name = BedwarsData.string(object, "name");
            if (PlayerNameUtil.valid(name)) names.add(name);
            else for (Map.Entry<String, JsonElement> child : object.entrySet()) collect(child.getValue(), names, depth + 1);
        }
    }

    public static Pattern samplePattern(String sample) {
        StringBuilder pattern = new StringBuilder("^(?:(?:[A-Z][A-Z ]*)\\s*>\\s*)?");
        for (int i = 0; i < sample.length();) {
            if (sample.startsWith("FrostyCookies", i)) { pattern.append(".+?"); i += "FrostyCookies".length(); }
            else if (sample.startsWith("Blue Bed", i)) { pattern.append(".+? Bed"); i += 8; }
            else if (sample.startsWith("uwu_x3", i)) { pattern.append("(?<killer>[A-Za-z0-9_]{1,16})"); i += 6; }
            else if (sample.charAt(i) == '#') {
                pattern.append("#(?<counter>[0-9,]+)");
                i++;
                while (i < sample.length() && (Character.isDigit(sample.charAt(i)) || sample.charAt(i) == ',')) i++;
            } else pattern.append(Pattern.quote(sample.substring(i, ++i)));
        }
        return Pattern.compile(pattern + "(?:\\s+FINAL KILL!)?$");
    }

    private static final class Observation {
        String killMessage;
        int beds = -1;
        int finals = -1;
    }
    private static final class Rule {
        final String id;
        final Pattern pattern;
        final boolean counter;
        final boolean bed;
        Rule(String id, String sample) {
            this.id = id;
            pattern = samplePattern(sample);
            counter = sample.contains("#");
            bed = sample.startsWith("Blue Bed");
        }
    }
}
