// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.debug;
import java.util.*;
import java.util.regex.Pattern;
public final class DebugLineFilter {
    private static final Pattern FORMAT=Pattern.compile("\u00a7[0-9a-fk-or]",Pattern.CASE_INSENSITIVE);
    private static final Pattern FPS=Pattern.compile("^(?:HUD: )?(?:\\d+ fps|p50=.*\\bfps).*",Pattern.CASE_INSENSITIVE);
    private static final List<String> CORE=List.of("Minecraft ","Integrated server", "Integrated Server","C:","E:","P:","XYZ:","Block:","Chunk:","Chunk-relative:","Facing:","Biome:","Local Difficulty:","ServerChunkCache:","ClientChunkCache:","Client Light:","Server Light:","CH ","SH ","SC:","Sounds:","Java:","Mem:","Allocated:","Allocation rate:","CPU:","Display:","Targeted Block", "Targeted Fluid", "Targeted Entity");
    private DebugLineFilter() { }
    public static List<String> extras(List<String> lines) {
        List<String> result=new ArrayList<>();
        int hardwareRows=0;
        for(String original:lines){
            String line=FORMAT.matcher(original).replaceAll("");
            if(line.startsWith("Display:")){hardwareRows=2;continue;}
            if(line.isBlank())hardwareRows=0;
            else if(hardwareRows>0){hardwareRows--;continue;}
            if(FPS.matcher(line).matches() || CORE.stream().anyMatch(line::startsWith) || line.startsWith("GPU:") || line.startsWith("OpenGL:"))continue;
            result.add(original);
        }
        return result;
    }
}
