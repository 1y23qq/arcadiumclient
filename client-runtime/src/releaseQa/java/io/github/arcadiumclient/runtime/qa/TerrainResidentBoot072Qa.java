package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public final class TerrainResidentBoot072Qa {
    private static int ticks;
    private static boolean done;
    private static final Map<String,List<String>> TARGETS=Map.of(
            "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager",List.of("terrain072$scope","terrain072$managerDestroyed"),
            "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer",List.of("terrain072$preparedIterator"),
            "org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage",List.of("terrain072$sectionPublished","terrain072$arenaRelocated","terrain072$storageDeleted"),
            "org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer",List.of("terrain072$compileFacts","terrain072$actualConstants"),
            "org.embeddedt.embeddium.impl.render.shader.ShaderLoader",List.of("terrain072$sourceFacts"),
            "org.embeddedt.embeddium.impl.gl.GlObject",List.of("terrain072$programDeleted"));
    private TerrainResidentBoot072Qa(){}
    public static void tick(){
        String output=System.getProperty("arcadium.terrainResidentBoot072.output");if(output==null||done||++ticks<80)return;done=true;Map<String,Object> report=new LinkedHashMap<>();Minecraft mc=Minecraft.getInstance();
        try{
            require(mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0")&&mc.world==null,"Use the owned fake title fixture only");
            boolean expected=Boolean.parseBoolean(System.getProperty("arcadium.terrainResidentBoot072.expectedBoot"));ClassLoader loader=Minecraft.class.getClassLoader();Class<?> bridge=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands",true,loader);
            require((boolean)bridge.getMethod("residentBootRequested").invoke(null)==expected,"The bridge startup flag differs from the explicit fixture");
            int matched=0;List<Object> targets=new ArrayList<>();
            for(var target:TARGETS.entrySet()){
                Class<?> type=Class.forName(target.getKey(),false,loader);List<String> methods=Arrays.stream(type.getDeclaredMethods()).map(Method::getName).sorted().toList();Map<String,List<String>> handlers=new LinkedHashMap<>();
                for(String token:target.getValue()){
                    List<String> hits=methods.stream().filter(name->name.contains(token)).toList();handlers.put(token,hits);require(expected?!hits.isEmpty():hits.isEmpty(),"Actual private terrain weaving differs: "+target.getKey()+" "+token);if(!hits.isEmpty())matched++;
                }
                targets.add(Map.of("class",target.getKey(),"actualMergedHandlers",handlers));
            }
            require(matched==(expected?10:0),"The actual merged handler count differs");
            boolean scene=(boolean)bridge.getMethod("residentAttached").invoke(null);require(!scene,"Class-loading-only title proof initialized a terrain scene");
            report.put("passed",true);report.put("privateBootRequested",expected);report.put("actualMergedHandlers",matched);report.put("targetClasses",targets);report.put("sceneInitialized",scene);report.put("renderer",GL11.glGetString(GL11.GL_RENDERER));report.put("openGl",GL11.glGetString(GL11.GL_VERSION));report.put("correctnessOnly",true);report.put("notWorldOrPerformanceQualification",true);
        }catch(Throwable failure){report.put("passed",false);report.put("failure",failure.toString());failure.printStackTrace();}
        try{Path file=Path.of(output);Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}
        mc.stop();
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
