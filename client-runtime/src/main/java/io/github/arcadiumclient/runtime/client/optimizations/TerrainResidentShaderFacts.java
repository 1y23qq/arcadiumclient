// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;

public final class TerrainResidentShaderFacts {
    private static final Pattern DEFINE=Pattern.compile("^#define\\s+([A-Za-z_][A-Za-z_0-9]*)(?:\\s+(.*))?$"),REGION=Pattern.compile("(?m)^\\s*#define\\s+REGION_SIZE\\s+(\\d+)\\s*$");
    private static final GpuVoxelSkinWeakIdentityMap<Fact> programs=new GpuVoxelSkinWeakIdentityMap<>(32);
    private static final ThreadLocal<Compile> compiling=new ThreadLocal<>();
    public static long captures,rejected,unknownPrograms,capacityFallbacks;
    static {
        if(GpuTerrainCommands.residentBootRequested())GpuTerrainCommands.attachResidentIfAbsent(new GpuTerrainCommands.Backend(){
            public String status(){return "GPU resident terrain: Waiting for scene";}
            public long residentBytes(){return 0;}
            public void reset(){}
            public void shutdown(){TerrainResidentShaderFacts.shutdown();}
        });
    }
    private TerrainResidentShaderFacts(){}
    private record Parsed(boolean fog,boolean fragmentDiscard,float fadeMilliseconds,int explicitRegion,String digest){}
    public record Fact(boolean fog,boolean fragmentDiscard,float fadeMilliseconds,long requiredAgeNanos,int regionSize,String definesSha256,String sourcesSha256){
        public boolean settled(long now,long newestLoad){return requiredAgeNanos==0||newestLoad==0||newestLoad<=now&&now-newestLoad>=requiredAgeNanos;}
    }
    public static final class Compile {
        private final Compile previous;
        private final int depth;
        private final Map<String,String> sources=new TreeMap<>();
        private Parsed constants;
        private int regionSize=-1,sourceCalls;
        private boolean valid=true;
        private Compile(Compile previous){this.previous=previous;depth=previous==null?1:previous.depth+1;valid=depth<=4;}
    }
    public static Compile begin(){Compile value=new Compile(compiling.get());compiling.set(value);return value;}
    public static void constants(ShaderConstants constants){
        Compile value=compiling.get();if(value==null)return;
        if(value.constants!=null||constants==null){value.valid=false;return;}
        value.constants=parse(constants.getDefineStrings());if(value.constants==null)value.valid=false;
    }
    public static void source(String name,String source){
        Compile value=compiling.get();if(value==null)return;
        if(name==null||source==null||source.length()>262144||++value.sourceCalls>128){value.valid=false;return;}
        String key=name.indexOf(':')<0?"minecraft:"+name:name;
        String expected=TerrainResidentShaderPins.SOURCES.get(key),actual=digest(source);
        if(expected==null||!expected.equals(actual)){value.valid=false;return;}
        value.sources.put(key,actual);
        if(key.equals("sodium:include/chunk_vertex.glsl")){
            var found=REGION.matcher(source);if(!found.find()){value.valid=false;return;}
            int count=Integer.parseInt(found.group(1));if(count!=256||found.find()){value.valid=false;return;}value.regionSize=count;
        }
    }
    public static void finish(Compile value,Object actualProgram){
        if(value==null||compiling.get()!=value){rejected++;compiling.remove();return;}
        try{
            Parsed parsed=value.constants;
            if(actualProgram==null||!value.valid||parsed==null||value.regionSize!=256
                    ||parsed.explicitRegion!=-1&&parsed.explicitRegion!=value.regionSize
                    ||!value.sources.containsKey("sodium:blocks/block_layer_opaque.vsh")||!value.sources.containsKey("sodium:blocks/block_layer_opaque.fsh")){rejected++;return;}
            long duration=parsed.fog?(long)Math.ceil(parsed.fadeMilliseconds)*1_000_000L:0;
            Fact fact=new Fact(parsed.fog,parsed.fragmentDiscard,parsed.fadeMilliseconds,duration,value.regionSize,parsed.digest,digest(value.sources.toString()));
            if(programs.put(actualProgram,fact))captures++;else capacityFallbacks++;
        }finally{if(value.previous==null)compiling.remove();else compiling.set(value.previous);}
    }
    public static Fact current(Object actualProgram){Fact value=programs.get(actualProgram);if(value==null)unknownPrograms++;return value;}
    public static void deleted(Object actualProgram){programs.remove(actualProgram);}
    public static void shutdown(){programs.clear();compiling.remove();}
    public static int retainedPrograms(){return programs.size();}
    public static int activeDepth(){Compile value=compiling.get();return value==null?0:value.depth;}
    static Parsed parse(List<String> defines){
        if(defines==null||defines.size()>256)return null;
        Map<String,String> values=new HashMap<>();
        for(String line:defines){
            if(line==null||line.length()>1024)return null;var matcher=DEFINE.matcher(line.strip());if(!matcher.matches())return null;
            String key=matcher.group(1),value=matcher.group(2)==null?"":matcher.group(2).strip();if(values.putIfAbsent(key,value)!=null)return null;
        }
        float duration=0;int region=-1;
        try{
            String raw=values.get("CHUNK_FADE_IN_DURATION_MS");
            if(raw!=null){if(!raw.matches("[0-9]+(?:\\.[0-9]+)?"))return null;duration=Float.parseFloat(raw);if(!Float.isFinite(duration)||duration<0||duration>30000)return null;}
            raw=values.get("REGION_SIZE");if(raw!=null){if(!raw.matches("[0-9]+"))return null;region=Integer.parseInt(raw);if(region!=256)return null;}
        }catch(NumberFormatException invalid){return null;}
        if(values.containsKey("USE_FRAGMENT_DISCARD")&&!values.get("USE_FRAGMENT_DISCARD").isEmpty())return null;
        return new Parsed(values.containsKey("USE_FOG"),values.containsKey("USE_FRAGMENT_DISCARD"),duration,region,digest(new TreeMap<>(values).toString()));
    }
    private static String digest(String source){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
