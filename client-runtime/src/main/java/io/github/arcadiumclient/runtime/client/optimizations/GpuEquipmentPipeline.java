// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.util.*;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class GpuEquipmentPipeline {
    private static PlayerBatchAccess api;
    private static Object backend;
    private static final IdentityHashMap<HumanoidModel,Profile> profiles=new IdentityHashMap<>();
    private static final Set<HumanoidModel> unsupported=Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean failed;
    private static volatile String state="Waiting for standard armor";
    public static long statesQueued,partsQueued,glintStates,leatherStates,fallbacks;
    private static final int MAGIC_1=Float.floatToRawIntBits(-9876543f),MAGIC_2=Float.floatToRawIntBits(-8765432f);
    private static final class Profile {
        final ModelPart[] parts;
        final boolean[] mirrored=new boolean[7];
        final int[] uv=new int[7],dimensions=new int[7],size=new int[7];
        final Object[] geometry=new Object[2];
        final int template;
        Profile(HumanoidModel model,Object device)throws Throwable{
            parts=parts(model);float[] data=new float[12*16];
            for(int i=0;i<7;i++){
                ModelPart part=parts[i];if(part.getClass()!=ModelPart.class||part.boxes.size()!=1||(part.children!=null&&!part.children.isEmpty()))throw new IllegalArgumentException("Non-standard armor part");
                Object[] templates=(Object[])api.boxTemplates.invoke(device,part);if(templates==null||templates.length!=1)throw new IllegalArgumentException("Non-cuboid armor");Object box=templates[0];
                int p=i*16;data[p]=part.x;data[p+1]=part.y;data[p+2]=part.z;
                data[p+4]=part.translateX;data[p+5]=part.translateY;data[p+6]=part.translateZ;
                for(int a=0;a<3;a++){data[p+8+a]=api.number(box,a)*.0625f;data[p+12+a]=api.number(box,a+3)*.0625f;}
                mirrored[i]=api.mirrored(box);uv[i]=((int)api.number(box,7)&65535)|(((int)api.number(box,8)&65535)<<16);
                dimensions[i]=((int)api.number(box,9)&65535)|(((int)api.number(box,10)&65535)<<16);
                size[i]=((int)api.number(box,11)&255)|(((int)api.number(box,12)&255)<<8)|(((int)api.number(box,13)&255)<<16);
            }
            geometry[0]=api.boxGeometry.invoke(device,false);geometry[1]=api.boxGeometry.invoke(device,true);template=GpuPlayerPackets.registerTemplate(data);
        }
    }
    private GpuEquipmentPipeline(){}
    private static ModelPart[] parts(HumanoidModel model){return new ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg,model.hat};}
    @SuppressWarnings("unchecked")
    public static boolean capture(Model input,Entity entity,float walk,float speed,float age,float yaw,float pitch,float scale){
        if(!BetaOptimizationsConfig.getGpuEquipmentPipeline()||failed)return false;
        if(ModernArmorGlintPipeline.active()||ModernArmorGlintPipeline.nativeModelLayer(input,entity))return false;
        if(input.getClass()!=HumanoidModel.class){state="Custom armor model; using original";fallbacks++;return false;}
        if(!(entity instanceof ClientPlayerEntity)||scale!=.0625f||input.isBaby){state="Nonstandard armor state; using original";fallbacks++;return false;}
        if(GpuPlayerPipeline.hasForgeHooks()){state="Forge hooks active; using original";fallbacks++;return false;}
        if(!PlayerPoseOptions.compatible()||!GpuModelTransforms.ready()){state="Compute/animation path inactive";fallbacks++;return false;}
        HumanoidModel model=(HumanoidModel)input;Object capture=null,first=null,second=null;
        int oldSize0=0,oldCount0=0,oldSize1=0,oldCount1=0,job=-1,addedGlint=0;Set<ModelPart> glint=null;
        Profile profile=null;
        try{
            if(api==null)api=new PlayerBatchAccess();Object actor=api.current.invoke();if(actor==null)return false;
            capture=api.activeCapture.get(actor);
            if(capture==null){state="No active native capture";fallbacks++;return false;}
            if(!(boolean)api.capturePlayer.get(capture)||!(boolean)api.captureArmor.get(capture)||!(boolean)api.captureActive.get(capture)||(boolean)api.captureFinished.get(capture)||(int)api.captureMode.get(capture)!=5888){
                state="Native animation pass; using original armor";fallbacks++;return false;
            }
            Object pass=api.capturePass.get(capture);String kind=((Enum<?>)pass).name();
            if(!kind.equals("NORMAL")&&!kind.equals("GLINT")){fallbacks++;return false;}
            Identifier texture=(Identifier)api.captureTexture.get(capture);
            if(CeraEmissiveAdmission.nativeSubmitRequired(capture,input,texture,kind)){state="Armor material retains native submission callbacks";fallbacks++;return false;}
            Object device=api.backend.get(actor);if(backend!=device){profiles.clear();unsupported.clear();backend=device;}
            if(unsupported.contains(model)){fallbacks++;return false;}
            profile=profiles.get(model);
            if(profile==null){if(profiles.size()>=128){state="Armor profile budget; using native capture";fallbacks++;return false;}try{profile=new Profile(model,device);profiles.put(model,profile);}catch(IllegalArgumentException rejected){unsupported.add(model);fallbacks++;return false;}}
            boolean isGlint=kind.equals("GLINT");if(isGlint)glint=(Set<ModelPart>)api.captureGlint.get(capture);
            int mask=0;
            for(int i=0;i<7;i++)if(profile.parts[i].visible&&!profile.parts[i].invisible){
                if(!isGlint||glint.add(profile.parts[i])){mask|=1<<i;if(isGlint)addedGlint|=1<<i;}
            }
            if(mask==0)return true;
            Matrix4fc root=(Matrix4fc)api.captureMatrices.get(capture);
            job=GpuPlayerPackets.add(root,walk,speed,age,yaw,pitch,model.attackAnimationProgress,model.itemInLeftHand,model.itemInRightHand,model.sneaking,model.riding,model.aimingBow,profile.template);
            if(job<0){if(glint!=null)for(int i=0;i<7;i++)if((addedGlint&(1<<i))!=0)glint.remove(profile.parts[i]);fallbacks++;return false;}
            Object batch=api.select.invoke(device,texture,pass);
            int light=(int)api.captureLight.get(capture),layer=(int)api.selectedLayer.get(device),color=pack((Vector4fc)api.captureColor.get(capture)),overlay=pack((Vector4fc)api.captureOverlay.get(capture));
            int lightWord=(light&255)|(((light>>>16)&255)<<8)|((layer&255)<<16),time=Float.floatToRawIntBits(ModernArmorGlintPipeline.effectTime(capture,(float)api.captureTime.get(capture)));int added=0;
            for(int mirror=0;mirror<2;mirror++){
                int n=0;for(int i=0;i<7;i++)if((mask&(1<<i))!=0&&profile.mirrored[i]==(mirror==1))n++;
                if(n==0)continue;Object instances=api.instances.invoke(profile.geometry[mirror],batch);int[] data=(int[])api.data.get(instances);int size=(int)api.size.get(instances),count=(int)api.count.get(instances);
                if(mirror==0){first=instances;oldSize0=size;oldCount0=count;}else{second=instances;oldSize1=size;oldCount1=count;}
                if(size+n*19>data.length){data=Arrays.copyOf(data,Math.max(size+n*19,data.length*2));api.data.set(instances,data);}
                for(int i=0;i<7;i++)if((mask&(1<<i))!=0&&profile.mirrored[i]==(mirror==1)){
                    int p=size;Arrays.fill(data,p,p+19,0);data[p]=Float.floatToRawIntBits(-(job*16+i+1));data[p+1]=MAGIC_1;data[p+2]=MAGIC_2;
                    data[p+12]=time;data[p+13]=lightWord;data[p+14]=color;data[p+15]=overlay;data[p+16]=profile.uv[i];data[p+17]=profile.dimensions[i];data[p+18]=profile.size[i];size+=19;
                }
                api.size.set(instances,size);api.count.set(instances,count+n);added+=n;
            }
            api.batchCount.set(batch,(int)api.batchCount.get(batch)+added);api.backendCount.set(device,(int)api.backendCount.get(device)+added);api.captureRecorded.set(capture,true);
            statesQueued++;partsQueued+=added;if(isGlint)glintStates++;else if(color!=-1)leatherStates++;
            state="GPU armor poses active";return true;
        }catch(Throwable error){
            if(first!=null){api.size.set(first,oldSize0);api.count.set(first,oldCount0);}if(second!=null){api.size.set(second,oldSize1);api.count.set(second,oldCount1);}
            if(job>=0)GpuPlayerPackets.rollback(job);if(glint!=null&&profile!=null)for(int i=0;i<7;i++)if((addedGlint&(1<<i))!=0)glint.remove(profile.parts[i]);
            failed=true;state="Disabled after error; using original armor";LogManager.getLogger("Arcadium/GPU Equipment").warn("GPU equipment pipeline disabled",error);return false;
        }
    }
    private static int pack(Vector4fc color){return channel(color.x())|channel(color.y())<<8|channel(color.z())<<16|channel(color.w())<<24;}
    private static int channel(float color){return Math.clamp((int)(color*255+.5f),0,255);}
    public static void reset(){profiles.clear();unsupported.clear();backend=null;}
    static boolean usesBackend(Object value){return backend==value;}
    public static String status(){return BetaOptimizationsConfig.getGpuEquipmentPipeline()?"GPU equipment: "+state+" | Parts: "+partsQueued+" | Fallback: "+fallbacks:"GPU equipment: Off";}
}
