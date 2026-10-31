// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.debug;

import java.util.*;
import java.util.regex.Pattern;

public final class LegacyDebugParser {
    private static final Pattern FORMAT = Pattern.compile("\u00a7[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private LegacyDebugParser() { }
    public static Map<String, String> parse(List<String> left, List<String> right, Map<String, String> extra) {
        Map<String, String> result = new LinkedHashMap<>(); List<String> miscLeft = new ArrayList<>(), miscRight = new ArrayList<>();
        for (String original : left) {
            if (original == null || original.isBlank()) continue;
            String line = FORMAT.matcher(original).replaceAll("");
            if (line.startsWith("Minecraft ")) result.put("minecraft", line.substring(10));
            else if (line.matches("^\\d+ fps.*")) result.put("fps", line);
            else if (line.startsWith("C: ")) {
                String data = line.substring(3); result.put("chunk_sections",data.split(" ")[0]);
                result.put("chunk_culling",line.contains("(s)") ? "Enabled" : "Disabled");
                for (String[] field : new String[][]{{"pC","pending_chunks"},{"pU","pending_uploads"},{"aB","available_buffers"}}) {
                    var match = Pattern.compile("(?:^|\\s)"+field[0]+":\\s*(\\d+)").matcher(line);
                    if (match.find()) result.put(field[1],match.group(1));
                }
            }
            else if (line.startsWith("E: ")) result.put("entities", line.substring(3));
            else if (line.startsWith("P: ")) result.put("particles", line.substring(3).split("\\. T:")[0]);
            else if (line.startsWith("ClientChunkCache: ")) result.put("client_chunk_cache", line.substring(18));
            else if (line.startsWith("XYZ: ")) result.put("player_coords", line.substring(5));
            else if (line.startsWith("Block: ")) result.put("block_coords", line.substring(7));
            else if (line.startsWith("Chunk-relative: ")) result.put("chunk_relative_coords", line.substring(16));
            else if (line.startsWith("Chunk: ")) { String[] pair = line.substring(7).split(" in "); result.put("chunk_relative_coords", pair[0]); if (pair.length > 1) result.put("chunk_coords", pair[1]); }
            else if (line.startsWith("Facing: ")) result.put("facing", line.substring(8));
            else if (line.startsWith("Biome: ")) result.put("biome", line.substring(7));
            else if (line.startsWith("Light: ")) result.put("light", line.substring(7));
            else if (line.startsWith("Local Difficulty: ")) result.put("local_difficulty", line.substring(18));
            else if (line.startsWith("Shader: ")) result.put("shader", line.substring(8));
            else if (line.startsWith("Looking at: ")) result.put("targeted_block", line.substring(12));
            else miscLeft.add(original);
        }
        boolean display = false, gpu = false;
        for (String original : right) {
            if (original == null || original.isBlank()) continue;
            String line = FORMAT.matcher(original).replaceAll("");
            if (line.startsWith("Java: ")) result.put("java_version", line.substring(6));
            else if (line.startsWith("Mem: ")) result.put("memory_usage", line.substring(5));
            else if (line.startsWith("Allocated: ")) result.put("allocated_memory", line.substring(11));
            else if (line.startsWith("CPU: ")) result.put("cpu", line.substring(5));
            else if (line.startsWith("Display: ")) { result.put("display", line.substring(9)); display = true; }
            else if (display) { result.put("gpu", line); display = false; gpu = true; }
            else if (gpu) { result.put("gpu_driver", line); result.put("opengl_version", "OpenGL"); gpu = false; }
            else if (line.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) result.put("id_block", line);
            else if (result.containsKey("id_block") && line.contains(":")) result.merge("block_states", original, (a,b) -> a + "\n" + b);
            else miscRight.add(original);
        }
        result.put("misc_left", String.join("\n", miscLeft)); result.put("misc_right", String.join("\n", miscRight));
        result.putAll(extra); return result;
    }
}
