// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

public final class ResidentBody072LifecycleEvidence {
    private record Snapshot(String kind,Object owner,Set<Integer> buffers,Set<Integer> arrays,Set<Integer> programs){}
    private static final ArrayDeque<Snapshot> pending=new ArrayDeque<>();
    private static final ArrayList<Map<String,Object>> milestones=new ArrayList<>();
    private static boolean hooked;
    private static String failure;
    private static int ownedSnapshots,deletedBuffers,deletedArrays,deletedPrograms,completedResets;
    private ResidentBody072LifecycleEvidence(){}
    private static boolean enabled(){return PlayerPipeline072Qa.enabled()&&System.getProperty("arcadium.residentBodies072.mode","off").equals("active");}
    private static Object field(Object value,String name)throws ReflectiveOperationException{Field field=value instanceof Class<?> type?type.getDeclaredField(name):value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value instanceof Class<?>?null:value);}
    private static Class<?> type(String name)throws ClassNotFoundException{return Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+name);}
    private static Map<?,?> owners()throws ReflectiveOperationException{return (Map<?,?>)field(type("GpuResidentBodies"),"owners");}
    private static void add(Set<Integer> output,Object value)throws ReflectiveOperationException{int id=((Number)value).intValue();if(id!=0)output.add(id);}
    public static void before(String kind,Object main){
        if(!enabled())return;
        try {
            require(!PlayerPipeline072Qa.timingActive(),"Resident lifecycle inspection occurred during performance timing");
            Minecraft mc=Minecraft.getInstance();require(mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0"),"Use only the owned offline lifecycle fixture");
            if(!hooked){hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(()->write(true),"Arcadium resident body lifecycle QA"));}
            require(pending.size()<8&&milestones.size()<64,"Resident lifecycle evidence exceeded its bounded fixture");
            Set<Integer> buffers=new LinkedHashSet<>(),arrays=new LinkedHashSet<>(),programs=new LinkedHashSet<>();
            for(var entry:owners().entrySet()) {
                if(main!=null&&entry.getKey()!=main)continue;
                Object owner=entry.getValue();add(buffers,field(owner,"uvBuffer"));
                if(Boolean.getBoolean("arcadium.residentBodies072.sharedPalette")){Object palette=field(owner,"palette");if(palette!=null){add(buffers,field(palette,"bones"));add(buffers,field(palette,"input"));}}
                for(Object profile:((Map<?,?>)field(owner,"profiles")).values()) {
                    Object draw=field(profile,"draw");for(String name:List.of("vertexBuffer","instanceBuffer","bones"))add(buffers,field(draw,name));
                    for(Object array:((Map<?,?>)field(draw,"arrays")).values())add(arrays,array);
                }
            }
            if(main==null){Class<?> state=type("GpuResidentBodyShaderState");add(buffers,field(state,"dummyBones"));add(buffers,field(state,"dummyUv"));add(programs,field(type("GpuResidentBodyDraw"),"poseProgram"));if(Boolean.getBoolean("arcadium.residentBodies072.sharedPalette"))add(programs,field(type("GpuResidentBodyPalette"),"program"));}
            for(int id:buffers)require(GL15.glIsBuffer(id),"An owned resident buffer was already invalid before cleanup");
            for(int id:arrays)require(GL30.glIsVertexArray(id),"An owned resident VAO was already invalid before cleanup");
            for(int id:programs)require(GL20.glIsProgram(id),"The owned resident compute program was already invalid before cleanup");
            if(!buffers.isEmpty()||!arrays.isEmpty()||!programs.isEmpty())ownedSnapshots++;
            pending.push(new Snapshot(kind,main,buffers,arrays,programs));
        } catch(Throwable error){failed(error);}
    }
    public static void after(String kind,Object main){
        if(!enabled())return;
        try {
            require(!PlayerPipeline072Qa.timingActive(),"Resident lifecycle inspection occurred during performance timing");
            require(!pending.isEmpty(),"Resident cleanup ended without a matching entry");Snapshot before=pending.pop();require(before.kind.equals(kind)&&before.owner==main,"Resident cleanup nesting changed");
            for(int id:before.buffers)require(!GL15.glIsBuffer(id),"Resident cleanup retained an owned GPU buffer");
            for(int id:before.arrays)require(!GL30.glIsVertexArray(id),"Resident cleanup retained an owned GPU VAO");
            for(int id:before.programs)require(!GL20.glIsProgram(id),"Resident cleanup retained its compute program");
            deletedBuffers+=before.buffers.size();deletedArrays+=before.arrays.size();deletedPrograms+=before.programs.size();
            require(main==null?owners().isEmpty():!owners().containsKey(main),"Resident cleanup retained the native owner");
            if(main==null) {
                require(((Map<?,?>)field(type("GpuResidentBodies"),"geometries")).isEmpty(),"Resident reset retained profiles");
                Object budget=field(type("GpuResidentBodies"),"budget");require(((Number)field(budget,"units")).intValue()==0,"Resident reset retained instance allocations");
                require(((Number)field(type("GpuResidentBodyShaderState"),"depth")).intValue()==0,"Resident reset retained an active shader scope");completedResets++;
            }
            require(GL11.glGetError()==0,"OpenGL error during resident cleanup");
            milestones.add(Map.of("kind",kind,"ownedBuffersDeleted",before.buffers.size(),"ownedArraysDeleted",before.arrays.size(),"ownedProgramsDeleted",before.programs.size(),"remainingOwners",owners().size()));write(false);
        }catch(Throwable error){failed(error);}
    }
    private static void failed(Throwable error){if(failure==null)failure=error.toString();error.printStackTrace();write(false);}
    private static synchronized void write(boolean terminal){
        try {
            Map<String,Object> report=new LinkedHashMap<>();report.put("passed",failure==null&&pending.isEmpty()&&ownedSnapshots>0&&deletedBuffers>0&&completedResets>0);
            report.put("finalSnapshot",terminal);report.put("correctnessOnly",true);report.put("ownedSnapshots",ownedSnapshots);report.put("deletedBuffers",deletedBuffers);report.put("deletedVertexArrays",deletedArrays);report.put("deletedPrograms",deletedPrograms);report.put("completedResets",completedResets);report.put("milestones",milestones);if(failure!=null)report.put("failure",failure);
            Path output=Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath().resolveSibling("resident-body-lifecycle.json");Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        }catch(Exception error){error.printStackTrace();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
