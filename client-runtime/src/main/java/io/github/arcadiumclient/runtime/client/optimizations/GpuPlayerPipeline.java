// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess;
import java.util.*;
import java.lang.invoke.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.client.render.entity.layer.*;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GpuPlayerPipeline {
    private static PlayerBatchAccess api;
    private static Object lastBackend;
    private static final IdentityHashMap<PlayerModel,Profile> profiles=new IdentityHashMap<>();
    private static final Set<PlayerModel> unsupported=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Matrix4f root=new Matrix4f();
    private static final java.nio.FloatBuffer glRoot=BufferUtils.createFloatBuffer(16);
    private static boolean failed,forgeChecked,forgeActive;
    private static MethodHandle cosmeticModels;
    private static Object cosmetics;
    private static volatile String state="Waiting for a supported scene";
    public static long playersDrawn,partsQueued,rejectedState,rejectedLayers,rejectedModel;
    private static final int MAGIC_1=Float.floatToRawIntBits(-9876543f),MAGIC_2=Float.floatToRawIntBits(-8765432f);

    private static final class Profile {
        final ModelPart[] parts;
        final boolean[] mirrored=new boolean[12];
        final int[] uv=new int[12],textureSize=new int[12],size=new int[12];
        final Object[] geometry=new Object[2];
        final int template;
        Profile(PlayerModel model,Object backend)throws Throwable{
            parts=parts(model);
            Object arm=((Object[])api.boxTemplates.invoke(backend,model.rightArm))[0];
            PlayerModel rest=new PlayerModel(0,api.number(arm,11)==3);
            ModelPart[] defaults=parts(rest);float[] data=new float[12*16];
            for(int i=0;i<12;i++){
                var part=parts[i];if(part.getClass()!=ModelPart.class||part.boxes.size()!=1||(part.children!=null&&!part.children.isEmpty()))throw new IllegalArgumentException("Custom player geometry");
                Object[] boxes=(Object[])api.boxTemplates.invoke(backend,part);if(boxes==null||boxes.length!=1)throw new IllegalArgumentException("Non-cuboid player part");Object box=boxes[0];
                int p=i*16;data[p]=defaults[i].x;data[p+1]=defaults[i].y;data[p+2]=defaults[i].z;
                data[p+4]=part.translateX;data[p+5]=part.translateY;data[p+6]=part.translateZ;
                for(int axis=0;axis<3;axis++){data[p+8+axis]=api.number(box,axis)*.0625f;data[p+12+axis]=api.number(box,axis+3)*.0625f;}
                mirrored[i]=api.mirrored(box);uv[i]=((int)api.number(box,7)&65535)|(((int)api.number(box,8)&65535)<<16);
                textureSize[i]=((int)api.number(box,9)&65535)|(((int)api.number(box,10)&65535)<<16);
                size[i]=((int)api.number(box,11)&255)|(((int)api.number(box,12)&255)<<8)|(((int)api.number(box,13)&255)<<16);
            }
            geometry[0]=api.boxGeometry.invoke(backend,false);geometry[1]=api.boxGeometry.invoke(backend,true);template=GpuPlayerPackets.registerTemplate(data);
        }
    }
    private static ModelPart[] parts(PlayerModel model){return new ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg,model.hat,model.leftPants,model.rightPants,model.leftSleeve,model.rightSleeve,model.jacket};}

    public static boolean capture(PlayerRenderer renderer,ClientPlayerEntity player,double x,double y,double z,float delta){
        if(!BetaOptimizationsConfig.getGpuPlayerPipeline()||failed)return false;
        if(!PlayerPoseOptions.compatible()||renderer.getClass()!=PlayerRenderer.class||((LivingPipelineAccess)renderer).arcadium$outline()||player.isInvisible()||player.shouldRenderOnFire()||player.isBaby()||player.isRiding()||player.isSleeping()||player.isSpectator()||player.damagedTimer>0||player.deathTicks>0
                ||player.getName().equals("Dinnerbone")||player.getName().equals("Grumm")||!GpuModelTransforms.ready()){rejectedState++;return false;}
        if(Skin3DConfig.getEnabled()&&SkinDetailBudget.allow(player)){rejectedModel++;return false;}
        LivingPipelineAccess renderAccess=(LivingPipelineAccess)renderer;
        if(!supportedLayers(renderAccess.arcadium$layers())){rejectedLayers++;return false;}
        boolean extraLayers=hasLayers(player);
        PlayerModel model=renderer.getModel();if(model.getClass()!=PlayerModel.class){state="Unsupported model: "+model.getClass().getName();rejectedModel++;return false;}
        int job=-1;boolean committed=false;
        Object instances0=null,instances1=null;int oldSize0=0,oldCount0=0,oldSize1=0,oldCount1=0;
        try{
            if(api==null)api=new PlayerBatchAccess();
            extraLayers|=hasCosmetics(player,renderAccess.arcadium$layers());
            Object actor=api.current.invoke();if(actor==null||!(boolean)api.active.invoke(actor))return false;
            if(forge()){state="Forge hooks active; using original player renderer";rejectedModel++;return false;}
            Object backend=api.backend.get(actor);if(lastBackend!=backend){profiles.clear();unsupported.clear();lastBackend=backend;}
            if(unsupported.contains(model)){rejectedModel++;return false;}
            Profile profile=profiles.get(model);
            if(profile==null){try{profile=new Profile(model,backend);profiles.put(model,profile);}catch(IllegalArgumentException rejected){unsupported.add(model);rejectedModel++;return false;}}
            int mask=0;for(int i=0;i<12;i++)if(profile.parts[i].visible&&!profile.parts[i].invisible)mask|=1<<i;
            if(mask==0)return false;
            float body=angle(player.lastBodyYaw,player.bodyYaw,delta),head=angle(player.lastHeadYaw,player.headYaw,delta)-body,pitch=player.lastPitch+(player.pitch-player.lastPitch)*delta;
            float walk=player.walkAnimationProgress-player.walkAnimationSpeed*(1-delta),speed=Math.min(1,player.lastWalkAnimationSpeed+(player.walkAnimationSpeed-player.lastWalkAnimationSpeed)*delta),age=player.ticks+delta;
            root.identity().translate((float)x,(float)y,(float)z).rotateY((float)Math.toRadians(180-body)).scale(-1,-1,1).scale(.9375f).translate(0,-1.5078125f,0);
            job=GpuPlayerPackets.add(root,walk,speed,age,head,pitch,player.getAttackAnimationProgress(delta),model.itemInLeftHand,model.itemInRightHand,model.sneaking,false,model.aimingBow,profile.template);
            if(job<0)return false;
            Identifier skin=player.getSkinTextureLocation();Object batch=api.select.invoke(backend,skin,api.translucent);
            int light=player.getLightLevel(delta),layer=(int)api.selectedLayer.get(backend),lightWord=(light&255)|((light>>>16&255)<<8)|(layer&255)<<16;
            int added=0;
            for(int mirror=0;mirror<2;mirror++){
                int n=0;for(int i=0;i<12;i++)if((mask&(1<<i))!=0&&profile.mirrored[i]==(mirror==1))n++;
                if(n==0)continue;
                Object instances=api.instances.invoke(profile.geometry[mirror],batch);int[] data=(int[])api.data.get(instances);int size=(int)api.size.get(instances),count=(int)api.count.get(instances);
                if(mirror==0){instances0=instances;oldSize0=size;oldCount0=count;}else{instances1=instances;oldSize1=size;oldCount1=count;}
                if(size+n*19>data.length){data=Arrays.copyOf(data,Math.max(size+n*19,data.length*2));api.data.set(instances,data);}
                for(int i=0;i<12;i++)if((mask&(1<<i))!=0&&profile.mirrored[i]==(mirror==1)){
                    int at=size;Arrays.fill(data,at,at+19,0);data[at]=Float.floatToRawIntBits(-(job*16+i+1));data[at+1]=MAGIC_1;data[at+2]=MAGIC_2;
                    data[at+12]=Float.floatToRawIntBits(age);data[at+13]=lightWord;data[at+14]=-1;data[at+16]=profile.uv[i];data[at+17]=profile.textureSize[i];data[at+18]=profile.size[i];size+=19;
                }
                api.size.set(instances,size);api.count.set(instances,count+n);added+=n;
            }
            api.batchCount.set(batch,(int)api.batchCount.get(batch)+added);api.backendCount.set(backend,(int)api.backendCount.get(backend)+added);
            committed=true;
            boolean counted=extraLayers&&renderLayers(renderAccess,model,player,actor,skin,light,walk,speed,delta,age,head,pitch);
            if(!counted){api.entityCount.set(actor,(int)api.entityCount.get(actor)+1);api.playerCount.set(actor,(int)api.playerCount.get(actor)+1);}
            api.nameAdd.invoke(api.nameTags.get(actor),renderer,player,x,y,z,light);
            if(!extraLayers)GpuModelTransforms.notePlayer();playersDrawn++;partsQueued+=added;state="Compact poses active";return true;
        }catch(Throwable error){
            if(!committed){
                if(instances0!=null){api.size.set(instances0,oldSize0);api.count.set(instances0,oldCount0);}
                if(instances1!=null){api.size.set(instances1,oldSize1);api.count.set(instances1,oldCount1);}
                if(job>=0)GpuPlayerPackets.rollback(job);
            }
            failed=true;state="Disabled after error; using Argentum";LogManager.getLogger("Arcadium/GPU Players").warn("Compact player pipeline disabled",error);return committed;
        }
    }
    private static boolean renderLayers(LivingPipelineAccess renderer,PlayerModel model,ClientPlayerEntity player,Object actor,Identifier skin,int light,
                                        float walk,float speed,float delta,float age,float yaw,float pitch)throws Throwable{
        Object capture=api.beginCapture.invoke(actor,model,skin,true,true,light,age,0f,0f,0f,0f);
        if(capture==null)throw new IllegalStateException("Player layer capture was unavailable");
        boolean counted=false;GL11.glPushMatrix();
        try{
            ((Matrix4fStack)api.captureMatrices.get(capture)).set(root);api.firstPass.invoke(capture);
            glRoot.clear();root.get(glRoot);GL11.glMultMatrixf(glRoot);
            GlStateManager.disableCull();GlStateManager.enableRescaleNormal();GlStateManager.enableAlphaTest();
            model.attackAnimationProgress=player.getAttackAnimationProgress(delta);model.riding=false;model.isBaby=false;
            model.prepare(player,walk,speed,delta);model.setupAnimation(walk,speed,age,yaw,pitch,.0625f,player);
            renderer.arcadium$renderLayers(player,walk,speed,delta,age,yaw,pitch,.0625f);
            counted=(boolean)api.recorded.invoke(capture);
        }finally{
            ((AutoCloseable)capture).close();GlStateManager.disableRescaleNormal();GL11.glPopMatrix();
            GlStateManager.activeTexture(GLX.GL_TEXTURE1);GlStateManager.enableTexture();GlStateManager.activeTexture(GLX.GL_TEXTURE0);GlStateManager.enableCull();
        }
        return counted;
    }
    private static boolean supportedLayers(List<?> layers){
        for(Object layer:layers){Class<?> type=layer.getClass();
            if(type!=ArmorLayer.class&&type!=ItemInHandLayer.class&&type!=StuckArrowLayer.class&&type!=Deadmau5Layer.class&&type!=CapeLayer.class&&type!=WornSkullLayer.class&&!type.getName().equals("dev.rdh.cera.modules.OptifineCosmetics$Layer")){state="Unsupported layer: "+type.getName();return false;}
        }
        return true;
    }
    private static boolean hasCosmetics(ClientPlayerEntity player,List<?> layers)throws Throwable{
        boolean present=false;for(Object layer:layers)if(layer.getClass().getName().equals("dev.rdh.cera.modules.OptifineCosmetics$Layer")){present=true;break;}
        if(!present)return false;
        if(cosmeticModels==null){
            var mc=Minecraft.getInstance();cosmetics=mc.getClass().getMethod("cera$getOptifineCosmetics").invoke(mc);
            var type=cosmetics.getClass();cosmeticModels=MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(type.getDeclaredMethod("cosmetics",ClientPlayerEntity.class));
        }
        return !((List<?>)cosmeticModels.invoke(cosmetics,player)).isEmpty();
    }
    private static boolean hasLayers(ClientPlayerEntity player){
        for(int i=0;i<5;i++)if(player.getEquipment(i)!=null)return true;
        return player.getStuckArrows()!=0||player.getCapeTextureLocation()!=null||player.getName().equals("deadmau5");
    }
    private static boolean forge(){
        if(!forgeChecked){forgeChecked=true;try{var type=Class.forName("io.github.arcadiumclient.forgecompat.ForgeRuntimeContext");var field=type.getDeclaredField("configuration");field.setAccessible(true);forgeActive=field.get(null)!=null;}catch(ClassNotFoundException ignored){}catch(Exception error){forgeActive=true;}}
        return forgeActive;
    }
    private static float angle(float old,float current,float delta){float difference=current-old;while(difference< -180)difference+=360;while(difference>=180)difference-=360;return old+delta*difference;}
    public static void reset(){profiles.clear();unsupported.clear();lastBackend=null;}
    public static String status(){return BetaOptimizationsConfig.getGpuPlayerPipeline()?"GPU player pipeline: "+state+" | Players: "+playersDrawn+" | Layer/model fallback: "+(rejectedLayers+rejectedModel):"GPU player pipeline: Off";}
}
