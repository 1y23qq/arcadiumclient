// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class RetainedNameTags {
    private record Packet(TagGeometryCache.Mesh mesh,NameTagPasses.State pass,Matrix4f local,int alpha) { }
    private static final class Plan {
        final String name;final float height;final boolean sneaking;final int epoch;final long generation;
        final ArrayList<Packet> packets=new ArrayList<>();
        final Matrix4f anchor=new Matrix4f(),inverse=new Matrix4f();
        float offsetX,offsetY,offsetZ;
        int transforms;
        boolean valid=true;
        Plan(Entity entity,String name,GpuNameTags gpu){this.name=name;height=entity.height;sneaking=entity.isSneaking();epoch=optionsEpoch;generation=gpu.generation();}
        boolean matches(Entity entity,String text,GpuNameTags gpu){return epoch==optionsEpoch&&generation==gpu.generation()&&height==entity.height&&sneaking==entity.isSneaking()&&name.equals(text);}
    }
    private static final Map<Entity,Map<String,Plan>> plans=new WeakHashMap<>();
    private static final Matrix4f anchor=new Matrix4f(),matrix=new Matrix4f();
    private static final ArrayList<Field> optionFields=new ArrayList<>();
    private static MethodHandle active;
    private static NameTagPasses passes;
    private static boolean initialized,failed,customBackground,customFont;
    private static int optionsEpoch;
    private static Object[] optionValues;
    private static Plan recording;
    private static Object recordingBatch;
    private static Entity owner;
    private static double originX,originY,originZ;
    public static long reused,built,rejected;
    private RetainedNameTags() { }

    public static void frame(){
        recording=null;recordingBatch=null;owner=null;
        if(!BetaOptimizationsConfig.getRetainedNameTagScenes()||!BetaOptimizationsConfig.getGpuNameTags()||failed)return;
        try{
            if(!initialized){
                initialized=true;Class<?> type=Class.forName("dev.rdh.argentum.impl.render.entity.NameTagBatch");
                active=MethodHandles.lookup().unreflect(type.getMethod("active"));passes=new NameTagPasses();
                for(String name:List.of("org.polyfrost.polynametag.client.PolyNametagConfig","org.polyfrost.oneconfig.internal.OneConfigConfig")){
                    Class<?> config=Class.forName(name);
                    for(Field field:config.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&(field.getType().isPrimitive()||field.getType().isEnum()||field.getType().getSimpleName().equals("PolyColor"))){field.setAccessible(true);optionFields.add(field);}
                }
                optionValues=new Object[optionFields.size()];
            }
            boolean changed=false;int index=0;customBackground=false;customFont=false;
            for(Field field:optionFields){
                Object value=field.get(null);
                if(value!=null&&field.getType().getSimpleName().equals("PolyColor"))value=value.getClass().getMethod("getArgb").invoke(value);
                if(!Objects.equals(optionValues[index],value)){changed=true;optionValues[index]=value;}index++;
                if(field.getName().equals("rounded")&&Boolean.TRUE.equals(value))customBackground=true;
                if((field.getName().equals("paddingX")||field.getName().equals("paddingY"))&&((Number)value).floatValue()>0)customBackground=true;
                if((field.getName().equals("useInterInGame")||field.getName().equals("useInterForModText"))&&Boolean.TRUE.equals(value))customFont=true;
            }
            if(changed)invalidate();passes.begin();
        }catch(Throwable error){disable(error);}
    }
    public static boolean begin(Entity entity,String name,double x,double y,double z){
        if(!BetaOptimizationsConfig.getRetainedNameTagScenes()||failed||active==null||recording!=null)return false;
        if(name==null||name.contains("§k")||specialText(name)||customBackground||customFont||PlayerBadgeRenderer.nametagEligible(entity)||GpuPlayerPipeline.hasForgeHooks()){rejected++;return false;}
        GpuNameTags pending=null;
        try{
            Object batch=active.invoke();if(!(batch instanceof NameTagSceneAccess scene)||!scene.arcadium$packets().enabled())return false;
            GpuNameTags gpu=scene.arcadium$packets();Map<String,Plan> labels=plans.get(entity);Plan plan=labels==null?null:labels.get(name);
            if(plan!=null&&plan.matches(entity,name,gpu)&&plan.valid&&!plan.packets.isEmpty()){
                var camera=Minecraft.getInstance().getEntityRenderDispatcher();
                anchor.set(scene.arcadium$camera()).translate((float)x+plan.offsetX,(float)y+plan.offsetY,(float)z+plan.offsetZ)
                        .rotateY((float)Math.toRadians(-camera.cameraYaw)).rotateX((float)Math.toRadians(camera.cameraPitch));
                gpu.checkpointRetained();pending=gpu;
                for(Packet packet:plan.packets){
                    matrix.set(anchor).mul(packet.local);
                    gpu.queueRetained(passes.restore(batch,packet.pass),packet.mesh,packet.alpha,matrix,scene.arcadium$lightU(),scene.arcadium$lightV());
                }
                gpu.noteLabel();reused++;return true;
            }
            if(plans.size()>8192)plans.clear();
            recording=new Plan(entity,name,gpu);recordingBatch=batch;owner=entity;originX=x;originY=y;originZ=z;return false;
        }catch(Throwable error){if(pending!=null)pending.rollbackRetained();disable(error);return false;}
    }
    public static void translate(Object batch,float x,float y,float z){
        if(recording==null||batch!=recordingBatch||recording.transforms!=0)return;
        recording.offsetX=x-(float)originX;recording.offsetY=y-(float)originY;recording.offsetZ=z-(float)originZ;recording.transforms=1;
    }
    public static void rotate(Object batch,float angle,float x,float y,float z,Matrix4fc matrix){
        if(recording==null||batch!=recordingBatch||recording.transforms<1||recording.transforms>2)return;
        var camera=Minecraft.getInstance().getEntityRenderDispatcher();
        if(recording.transforms==1){if(x!=0||y!=1||z!=0||angle!=-camera.cameraYaw){recording.valid=false;return;}}
        else if(x!=1||y!=0||z!=0||angle!=camera.cameraPitch){recording.valid=false;return;}
        if(++recording.transforms==3){recording.anchor.set(matrix);recording.inverse.set(matrix).invert();}
    }
    public static void packet(Object pass,TagGeometryCache.Mesh mesh,Matrix4fc source,float x,float y,int alpha,float lightU,float lightV){
        if(recording==null||!recording.valid)return;
        if(recording.transforms!=3){recording.valid=false;return;}
        Matrix4f local=new Matrix4f(recording.inverse).mul(source).translate(x,y,0);
        recording.packets.add(new Packet(mesh,passes.snapshot(pass),local,alpha));
    }
    public static void end(){
        if(recording==null)return;
        if(recording.valid&&recording.packets.size()>=2&&recording.packets.size()<=32){
            Map<String,Plan> labels=plans.computeIfAbsent(owner,ignored->new HashMap<>());if(labels.size()>8)labels.clear();labels.put(recording.name,recording);built++;
        }else rejected++;
        recording=null;recordingBatch=null;owner=null;
    }
    public static void invalidate(){plans.clear();optionsEpoch++;}
    public static void flushed(){if(passes!=null)passes.begin();if(recording!=null)recording.valid=false;}
    private static boolean specialText(String text){
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c==':'||c>=0x2000)return true;}
        return false;
    }
    private static void disable(Throwable error){failed=true;recording=null;recordingBatch=null;owner=null;plans.clear();LogManager.getLogger("Arcadium/Retained Name Tags").warn("Retained name tag scenes disabled; using original preparation",error);}
    public static String status(){return !BetaOptimizationsConfig.getRetainedNameTagScenes()?"Retained name tag scenes: Off":failed?"Retained name tag scenes: Using original preparation":"Retained name tag scenes: "+reused+" reused | "+built+" rebuilt";}
}
