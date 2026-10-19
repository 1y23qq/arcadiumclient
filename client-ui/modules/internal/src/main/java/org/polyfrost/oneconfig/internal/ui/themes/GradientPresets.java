package org.polyfrost.oneconfig.internal.ui.themes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class GradientPresets {
    public record Preset(String name, int start, int end) { }

    private static final List<Preset> ALL = load();

    private GradientPresets() { }

    public static List<Preset> all() { return ALL; }

    public static Preset get(int index) { return ALL.get(index >= 0 && index < ALL.size() ? index : 0); }

    public static String[] names() { return ALL.stream().map(Preset::name).toArray(String[]::new); }

    private static List<Preset> load() {
        List<Preset> presets = new ArrayList<>(256);
        HashSet<String> names = new HashSet<>();
        try (var stream = GradientPresets.class.getResourceAsStream("/assets/arcadium/gradient-presets.csv")) {
            if (stream == null) throw new IllegalStateException("Missing gradient presets");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    String[] fields = line.split(",");
                    if (fields.length != 3 || !names.add(fields[0])) throw new IllegalStateException("Invalid gradient preset");
                    presets.add(new Preset(fields[0], parseColor(fields[1]), parseColor(fields[2])));
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not load gradient presets", error);
        }
        if (presets.size() != 256 || !presets.get(0).name().equals("Arcadium")) throw new IllegalStateException("Invalid gradient catalog");
        return List.copyOf(presets);
    }

    public static int parseColor(String hex) {
        if (!hex.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Expected #RRGGBB");
        return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }

    public static int mix(int start, int end, double fraction) {
        double t = Math.max(0, Math.min(1, fraction));
        int r = (int) Math.round((start >>> 16 & 255) * (1 - t) + (end >>> 16 & 255) * t);
        int g = (int) Math.round((start >>> 8 & 255) * (1 - t) + (end >>> 8 & 255) * t);
        int b = (int) Math.round((start & 255) * (1 - t) + (end & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
