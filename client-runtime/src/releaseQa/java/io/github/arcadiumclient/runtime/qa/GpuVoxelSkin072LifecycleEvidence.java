// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072LifecycleEvidence {
    private static final String PREFIX="io.github.arcadiumclient.runtime.client.optimizations.";
    private static final List<Map<String,Object>> resets=new ArrayList<>(),milestones=new ArrayList<>();
    private static final Deque<Before> pending=new ArrayDeque<>();
    private static final List<String> failures=new ArrayList<>();
    private static boolean hooked,shutdownSeen,liveSeen;
    private record Before(String reason,Map<String,Object> gauges,Objects ids){}
    private static final class Objects {
        final Set<Integer> buffers=new LinkedHashSet<>(),textures=new LinkedHashSet<>(),programs=new LinkedHashSet<>(),arrays=new LinkedHashSet<>(),framebuffers=new LinkedHashSet<>();
        Map<String,Object> values(){return Map.of("buffers",buffers,"textures",textures,"programs",programs,"vertexArrays",arrays,"framebuffers",framebuffers);}
        int size(){return buffers.size()+textures.size()+programs.size()+arrays.size()+framebuffers.size();}
    }
    private GpuVoxelSkin072LifecycleEvidence(){}
    private static boolean enabled(){return Boolean.getBoolean("arcadium.voxelQuality072.lifecycle")&&PlayerPipeline072Qa.enabled();}

    public static void beforeReset(){
        if(!enabled())return;hook();
        try{
            Map<String,Object> before=gauges();Objects ids=objects();
            liveSeen|=((Number)before.getOrDefault("auxiliaryBackends",0)).intValue()>0;
            pending.push(new Before(reason(),before,ids));
        }catch(Throwable failure){failed("Cannot inspect owned resources before reset",failure);pending.push(new Before(reason(),Map.of(),new Objects()));}
    }
    public static void afterReset(){
        if(!enabled())return;hook();
        try{
            Before before=pending.isEmpty()?new Before(reason(),Map.of(),new Objects()):pending.pop();
            Map<String,Object> after=gauges(),record=new LinkedHashMap<>();
            record.put("reason",before.reason);record.put("before",before.gauges);record.put("after",after);record.put("ownedGlNamesBefore",before.ids.values());
            boolean zero=zero(after),scope=java.util.Objects.equals(before.gauges.get("managedActive"),after.get("managedActive"));
            boolean context=context();Map<String,Object> alive=new LinkedHashMap<>();
            if(context){
                alive.put("buffers",before.ids.buffers.stream().filter(GL15::glIsBuffer).toList());
                alive.put("textures",before.ids.textures.stream().filter(GL11::glIsTexture).toList());
                alive.put("programs",before.ids.programs.stream().filter(GL20::glIsProgram).toList());
                alive.put("vertexArrays",before.ids.arrays.stream().filter(GL30::glIsVertexArray).toList());
                alive.put("framebuffers",before.ids.framebuffers.stream().filter(GL30::glIsFramebuffer).toList());
            }
            boolean deleted=before.ids.size()==0||context&&alive.values().stream().allMatch(value->((List<?>)value).isEmpty());
            record.put("glContextPresent",context);record.put("ownedGlNamesStillAlive",alive);record.put("allCapturedGlNamesDeleted",deleted);
            record.put("allJavaOwnershipGaugesZero",zero);record.put("borrowedManagedScopePreserved",scope);record.put("passed",zero&&scope&&deleted);
            resets.add(record);
            if(before.reason.equals("shutdown"))shutdownSeen=true;
            if(!zero||!scope||!deleted)failures.add("Reset did not release every owned resource or preserve its caller scope: "+before.reason);
            write(false);
        }catch(Throwable failure){failed("Cannot inspect owned resources after reset",failure);}
    }
    public static void milestone(String name){
        if(!enabled())return;hook();
        try{
            Map<String,Object> values=gauges();
            boolean requiresZero=name.equals("world-unloaded")||name.equals("world-replaced")||name.equals("resource-reload-return");
            milestones.add(Map.of("name",name,"gauges",values,"allJavaOwnershipGaugesZero",zero(values)));
            if(requiresZero&&!zero(values))failures.add("Nonzero owned state at "+name);
            if(name.equals("shutdown-head"))shutdownSeen=true;
            write(false);
        }catch(Throwable failure){failed("Cannot inspect lifecycle milestone "+name,failure);}
    }
    private static String reason(){
        for(var frame:Thread.currentThread().getStackTrace()){
            String method=frame.getMethodName();
            if(method.contains("releaseGpuBuffers"))return "shutdown";
            if(method.contains("releaseWorldBuffers"))return "world-change";
            if(method.contains("invalidateParticleResources"))return "resource-reload";
            if(method.equals("invalidateBackend"))return "native-model-close";
        }
        return shutdownSeen?"shutdown":"other-reset";
    }
    private static Map<String,Object> gauges()throws Exception{
        Map<String,Object> result=new LinkedHashMap<>();Class<?> pipeline;
        try{pipeline=Class.forName(PREFIX+"GpuVoxelSkinPipeline");}catch(ClassNotFoundException absent){return Map.of("present",false);}
        result.put("present",true);result.put("auxiliaryBackends",pipeline.getField("auxiliaryBackends").getInt(null));
        result.put("auxiliaryTextureBytes",pipeline.getField("auxiliaryTextureBytes").getLong(null));
        for(String name:List.of("admissionSources","admissionMeshes","admissionClassifications","admissionAlphaBytes"))try{
            result.put(name,pipeline.getField(name).get(null));
        }catch(NoSuchFieldException olderBaseline){}
        Object registry=value(null,pipeline,"auxiliaries");result.put("registryEntries",((Map<?,?>)value(registry,"owners")).size());
        Object renderer=value(null,pipeline,"backend");result.put("rendererOwners",renderer==null?0:((Map<?,?>)value(renderer,"owners")).size());
        result.put("rendererGeometries",renderer==null?0:((Map<?,?>)value(renderer,"geometries")).size());
        if(renderer!=null)try{
            Object admission=value(renderer,"admission");
            result.put("actualAdmissionSources",((Map<?,?>)value(admission,"sources")).size());
            result.put("actualAdmissionFootprints",((Map<?,?>)value(admission,"footprints")).size());
            result.put("actualAdmissionClassifications",value(admission,"classifications"));
            result.put("retainedObservedParts",Arrays.stream((Object[])value(renderer,"observedParts")).filter(java.util.Objects::nonNull).count());
            result.put("retainedAdmissionCapture",value(renderer,"admissionCapture")==null?0:1);
        }catch(NoSuchFieldException olderBaseline){}
        if(context()){
            Class<?> device=Class.forName("org.embeddedt.embeddium.impl.gl.device.RenderDevice");Object instance=device.getField("INSTANCE").get(null);
            result.put("managedActive",value(instance,"isActive"));
        }
        return result;
    }
    private static boolean zero(Map<String,Object> values){
        if(Boolean.FALSE.equals(values.get("present")))return true;
        for(String key:List.of("auxiliaryBackends","auxiliaryTextureBytes","registryEntries","rendererOwners","rendererGeometries"))
            if(!(values.get(key)instanceof Number number)||number.longValue()!=0)return false;
        for(String key:List.of("admissionSources","admissionMeshes","admissionClassifications","admissionAlphaBytes","actualAdmissionSources",
                "actualAdmissionFootprints","actualAdmissionClassifications","retainedObservedParts","retainedAdmissionCapture"))
            if(values.containsKey(key)&&(!(values.get(key)instanceof Number number)||number.longValue()!=0))return false;
        return true;
    }
    private static Objects objects()throws Exception{
        Objects ids=new Objects();if(!context())return ids;
        Class<?> pipeline=Class.forName(PREFIX+"GpuVoxelSkinPipeline");Object renderer=value(null,pipeline,"backend");if(renderer==null)return ids;
        ids.programs.addAll(positive((int)value(null,Class.forName(PREFIX+"GpuVoxelSkinDraw"),"program")));
        for(Object state:((Map<?,?>)value(renderer,"owners")).values()){
            Object draw=value(state,"draw"),buffers=value(draw,"buffers"),commands=value(draw,"commands");
            addHandle(ids.buffers,commands);
            if(buffers!=null){
                addHandle(ids.buffers,value(buffers,"vertexBuffer"));addHandle(ids.buffers,value(buffers,"instanceBuffer"));
                Object tessellation=value(buffers,"tessellation");if(tessellation!=null)addHandle(ids.arrays,value(tessellation,"array"));
            }
            Object auxiliary=value(state,"auxiliary");
            for(Object program:((Map<?,?>)value(auxiliary,"programs")).values())addHandle(ids.programs,program);
            Object textures=value(auxiliary,"textureArrays");
            ids.textures.addAll(positive((int)value(textures,"fallbackTexture")));ids.framebuffers.addAll(positive((int)value(textures,"framebuffer")));
            for(Object pool:((Map<?,?>)value(textures,"pools")).values())ids.textures.addAll(positive((int)value(pool,"texture")));
        }
        return ids;
    }
    private static Set<Integer> positive(int value){return value>0?Set.of(value):Set.of();}
    private static void addHandle(Set<Integer> ids,Object value)throws Exception{
        if(value!=null){int handle=((Number)value.getClass().getMethod("handle").invoke(value)).intValue();if(handle>0)ids.add(handle);}
    }
    private static Object value(Object target,String field)throws Exception{return value(target,target.getClass(),field);}
    private static Object value(Object target,Class<?> type,String name)throws Exception{
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static boolean context(){try{GL.getCapabilities();return true;}catch(IllegalStateException absent){return false;}}
    private static void failed(String message,Throwable failure){failures.add(message+": "+failure.getClass().getName());write(false);}
    private static void hook(){
        if(hooked)return;hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(()->write(true),"Arcadium voxel lifecycle QA"));
    }
    private static void write(boolean finalSnapshot){
        try{
            Map<String,Object> last=gauges(),report=new LinkedHashMap<>();
            report.put("schema","arcadium.voxel-lifecycle.v1");report.put("correctnessOnly",true);report.put("finalSnapshot",finalSnapshot);
            report.put("observedLiveAuxiliary",liveSeen);report.put("normalShutdownObserved",shutdownSeen);report.put("resets",resets);report.put("milestones",milestones);
            report.put("lastJavaOwnershipGauges",last);report.put("failures",failures);
            report.put("passed",failures.isEmpty()&&(!finalSnapshot||shutdownSeen&&liveSeen&&zero(last)));
            Path output=Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath().resolveSibling("voxel-lifecycle.json");
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Throwable failure){failure.printStackTrace();}
    }
}
