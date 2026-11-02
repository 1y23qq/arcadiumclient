// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class RetainedNameTags {
    private static final class PreparedPass {
        final NameTagPasses.State state;
        GpuNameTags manager;
        GpuNameTags.PassBinding binding;
        long epoch,generation;
        PreparedPass(NameTagPasses.State state){this.state=state;}
    }
    private record Packet(TagGeometryCache.Mesh mesh,PreparedPass pass,Matrix4f local,RetainedTagTransforms.Local transform,int alpha) { }
    private static final class Plan {
        final String name;final float height;final boolean sneaking;final int epoch;final long generation;final GpuNameTags manager;
        final ArrayList<Packet> packets=new ArrayList<>();
        final Matrix4f anchor=new Matrix4f(),inverse=new Matrix4f();
        float offsetX,offsetY,offsetZ;
        int transforms;
        boolean valid=true;
        Plan(Entity entity,String name,GpuNameTags gpu){this.name=name;manager=gpu;height=entity.height;sneaking=entity.isSneaking();epoch=optionsEpoch;generation=gpu.generation();}
        boolean matches(Entity entity,String text,GpuNameTags gpu){return manager==gpu&&epoch==optionsEpoch&&generation==gpu.generation()&&height==entity.height&&sneaking==entity.isSneaking()&&name.equals(text);}
    }
    private static final Map<Entity,Map<String,Plan>> plans=new WeakHashMap<>();
    private static final IdentityHashMap<NameTagPasses.State,BufferBuilder> framePasses=new IdentityHashMap<>();
    private static final Map<NameTagPasses.State,PreparedPass> preparedPasses=new HashMap<>();
    private static Object passBatch;
    private static long passEpoch=1;
    private static final Matrix4f anchor=new Matrix4f(),matrix=new Matrix4f();
    private static final RetainedTagTransforms transforms=new RetainedTagTransforms();
    private static final ArrayList<Field> optionFields=new ArrayList<>();
    private static RetainedTagOptions compactOptions;
    private static MethodHandle active;
    private static NameTagPasses passes;
    private static boolean initialized,failed,customBackground,customFont;
    public static boolean compact;
    private static int optionsEpoch;
    private static Object[] optionValues;
    private static Plan recording;
    private static Object recordingBatch;
    private static Entity owner;
    private static double originX,originY,originZ;
    public static long reused,built,rejected,passBindingHits,passBindingsResolved;
    public static long nativeTextCandidates,emojiRejected,animatedRejected,dormantTextRejected,unsafeTextRejected;
    private RetainedNameTags() { }

    public static void frame(){
        recording=null;recordingBatch=null;owner=null;
        clearBindings();
        boolean nextCompact=BetaOptimizationsConfig.getCompactNameTagPreparation();
        if(compact!=nextCompact){compact=nextCompact;invalidate();}
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
            boolean changed=false;
            if(compact){
                if(compactOptions==null)compactOptions=new RetainedTagOptions(Class.forName("org.polyfrost.polynametag.client.PolyNametagConfig"),Class.forName("org.polyfrost.oneconfig.internal.OneConfigConfig"));
                changed=compactOptions.poll();customBackground=compactOptions.customBackground();customFont=compactOptions.customFont();
            }else{
                int index=0;customBackground=false;customFont=false;
                for(Field field:optionFields){
                    Object value=field.get(null);
                    if(value!=null&&field.getType().getSimpleName().equals("PolyColor"))value=value.getClass().getMethod("getArgb").invoke(value);
                    if(!Objects.equals(optionValues[index],value)){changed=true;optionValues[index]=value;}index++;
                    if(field.getName().equals("rounded")&&Boolean.TRUE.equals(value))customBackground=true;
                    if((field.getName().equals("paddingX")||field.getName().equals("paddingY"))&&((Number)value).floatValue()>0)customBackground=true;
                    if((field.getName().equals("useInterInGame")||field.getName().equals("useInterForModText"))&&Boolean.TRUE.equals(value))customFont=true;
                }
            }
            if(changed)invalidate();passes.begin();
        }catch(Throwable error){disable(error);}
    }
    public static boolean begin(Entity entity,String name,double x,double y,double z){
        if(!BetaOptimizationsConfig.getRetainedNameTagScenes()||failed||active==null||recording!=null)return false;
        if(name==null||customBackground||customFont||PlayerBadgeRenderer.nametagEligible(entity)||GpuPlayerPipeline.hasForgeHooks()){rejected++;return false;}
        GpuNameTags pending=null;
        try{
            Object batch=active.invoke();if(!(batch instanceof NameTagSceneAccess scene)||!scene.arcadium$packets().enabled())return false;
            GpuNameTags gpu=scene.arcadium$packets();Map<String,Plan> labels=plans.get(entity);Plan plan=labels==null?null:labels.get(name);
            if(plan!=null&&plan.matches(entity,name,gpu)&&plan.valid&&!plan.packets.isEmpty()){
                var camera=Minecraft.getInstance().getEntityRenderDispatcher();
                if(compact)transforms.anchor(scene.arcadium$camera(),(float)x+plan.offsetX,(float)y+plan.offsetY,(float)z+plan.offsetZ,camera.cameraYaw,camera.cameraPitch);
                else anchor.set(scene.arcadium$camera()).translate((float)x+plan.offsetX,(float)y+plan.offsetY,(float)z+plan.offsetZ)
                            .rotateY((float)Math.toRadians(-camera.cameraYaw)).rotateX((float)Math.toRadians(camera.cameraPitch));
                gpu.checkpointRetained();pending=gpu;
                for(Packet packet:plan.packets){
                    if(compact){
                        transforms.combine(packet.transform,matrix);
                        gpu.queueBoundRetained(restorePreparedPass(batch,gpu,packet.pass),packet.mesh,packet.alpha,matrix,scene.arcadium$lightU(),scene.arcadium$lightV());
                    }else{
                        matrix.set(anchor).mul(packet.local);
                        gpu.queueRetained(restorePass(batch,packet.pass.state),packet.mesh,packet.alpha,matrix,scene.arcadium$lightU(),scene.arcadium$lightV());
                    }
                }
                gpu.commitRetained();gpu.noteLabel();reused++;return true;
            }
            RetainedTagText.Eligibility text=RetainedTagText.classify(name);
            if(text!=RetainedTagText.Eligibility.NATIVE){
                rejected++;
                switch(text){
                    case EMOJI -> emojiRejected++;
                    case OBFUSCATED, ANIMATED -> animatedRejected++;
                    case EMOJI_DISABLED -> dormantTextRejected++;
                    default -> unsafeTextRejected++;
                }
                return false;
            }
            nativeTextCandidates++;
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
        NameTagPasses.State state=passes.snapshot(pass);
        PreparedPass prepared=preparedPasses.get(state);
        if(prepared==null){if(preparedPasses.size()>=4096)preparedPasses.clear();prepared=new PreparedPass(state);preparedPasses.put(state,prepared);}
        recording.packets.add(new Packet(mesh,prepared,local,new RetainedTagTransforms.Local(local),alpha));
    }
    public static void end(){
        if(recording==null)return;
        if(recording.valid&&recording.packets.size()>=2&&recording.packets.size()<=32){
            Map<String,Plan> labels=plans.computeIfAbsent(owner,ignored->new HashMap<>());if(labels.size()>8)labels.clear();labels.put(recording.name,recording);built++;
        }else rejected++;
        recording=null;recordingBatch=null;owner=null;
    }
    private static BufferBuilder restorePass(Object batch,NameTagPasses.State state)throws Throwable{
        if(passBatch!=batch){clearBindings();passBatch=batch;}
        BufferBuilder buffer=framePasses.get(state);
        if(buffer!=null){passBindingHits++;return buffer;}
        buffer=passes.restore(batch,state);framePasses.put(state,buffer);passBindingsResolved++;return buffer;
    }
    private static GpuNameTags.PassBinding restorePreparedPass(Object batch,GpuNameTags gpu,PreparedPass pass)throws Throwable{
        if(passBatch!=batch){clearBindings();passBatch=batch;}
        if(pass.epoch==passEpoch&&pass.manager==gpu&&pass.generation==gpu.generation()){passBindingHits++;return pass.binding;}
        BufferBuilder buffer=passes.restore(batch,pass.state);
        pass.binding=gpu.bindRetained(buffer);pass.manager=gpu;pass.epoch=passEpoch;pass.generation=gpu.generation();
        framePasses.put(pass.state,buffer);passBindingsResolved++;return pass.binding;
    }
    private static void clearBindings(){framePasses.clear();passBatch=null;passEpoch++;}
    public static void invalidate(){plans.clear();preparedPasses.clear();clearBindings();transforms.invalidate();optionsEpoch++;}
    public static void flushed(){clearBindings();if(passes!=null)passes.begin();if(recording!=null)recording.valid=false;}
    private static void disable(Throwable error){failed=true;recording=null;recordingBatch=null;owner=null;plans.clear();preparedPasses.clear();clearBindings();LogManager.getLogger("Arcadium/Retained Name Tags").warn("Retained name tag scenes disabled; using original preparation",error);}
    public static String status(){return !BetaOptimizationsConfig.getRetainedNameTagScenes()?"Retained name tag scenes: Off":failed?"Retained name tag scenes: Using original preparation":"Retained name tag scenes: "+reused+" reused | "+built+" rebuilt | Emoji/animated fallback: "+(emojiRejected+animatedRejected);}
}
