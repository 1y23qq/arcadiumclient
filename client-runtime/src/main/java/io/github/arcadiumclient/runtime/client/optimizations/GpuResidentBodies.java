// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.*;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.apache.logging.log4j.LogManager;

public final class GpuResidentBodies {
    private static final IdentityHashMap<Object,Owner> owners=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,Profile> geometries=new IdentityHashMap<>();
    private static final WeakHashMap<GpuVoxelSkinUploadFacts.Fact,Boolean> binary=new WeakHashMap<>();
    private static final ModernArmorAuxiliaryBudget budget=new ModernArmorAuxiliaryBudget();
    private static GpuResidentBodyAccess api;
    private static FloatBuffer normal,mirrored;
    private static boolean failed;
    private static volatile String state="Private resident body renderer is off";
    public static long actorsQueued,partsQueued,actorsDrawn,drawCalls,poseDispatches,replayedParts,materialFallbacks,budgetFallbacks,errors;
    private GpuResidentBodies() { }
    private static boolean enabled(){return GpuResidentBodyShaders.active()&&!failed;}
    public static void beginBatch(Object main){if(GpuModelAuxiliaries.isAuxiliaryBackend(main))return;if(owners.containsKey(main))budget.begin(main);}

    static boolean queue(Object main,Object source,int job,int mask,Object batch,Identifier texture,int light,float age,Matrix4fc root) {
        if(!enabled())return false;
        Object instances=null;int oldSize=0,oldCount=0,oldBatch=0,oldMain=0;boolean changed=false;
        try {
            if(!GpuResidentBodyShaders.vertexPatched||job<0||job>=GpuPlayerPackets.count()||mask<1||(mask&~0xfff)!=0||main==null||batch==null)return false;
            if(api==null)api=new GpuResidentBodyAccess();
            GlProgram<?> program=(GlProgram<?>)api.program.invoke(main);
            if(!GpuResidentBodyShaderState.verifiedProgram(program.handle())||!GpuResidentBodyDraw.ready())return false;
            GpuVoxelSkinUploadFacts.Fact fact=api.material(main,texture);
            if(fact==null||!binary(fact)){materialFallbacks++;state="Skin material keeps native per-part rendering";return false;}
            Owner owner=owners.get(main);
            if(owner==null){if(!budget.owner(main)){budgetFallbacks++;return false;}owner=new Owner(main);owners.put(main,owner);}
            Profile profile=owner.profiles.get(source);
            if(profile==null){if(!budget.profile(main,source)||owner.profiles.size()==128){budgetFallbacks++;return false;}profile=new Profile(owner,source);owner.profiles.put(source,profile);geometries.put(profile.geometry,profile);}
            profile.draw.prepare(program.handle());
            Object existing=((Map<?,?>)api.batchParts.get(batch)).get(profile.geometry);
            if(existing==null&&!budget.canInstance()){budgetFallbacks++;return false;}
            instances=existing!=null?existing:api.capture.instances.invoke(profile.geometry,batch);
            if(!owner.instances.containsKey(instances)) {
                if((int)api.capture.size.get(instances)!=0||(int)api.capture.count.get(instances)!=0||!budget.instance(profile.geometry,instances))return false;
                api.capture.data.set(instances,new int[19]);api.uploadBuffer.set(instances,BufferUtils.createIntBuffer(19));owner.instances.put(instances,profile);
            }
            oldSize=(int)api.capture.size.get(instances);oldCount=(int)api.capture.count.get(instances);
            int[] data=(int[])api.capture.data.get(instances);IntBuffer upload=(IntBuffer)api.uploadBuffer.get(instances);
            if(oldSize!=oldCount*19||data.length%19!=0||upload.capacity()%19!=0)throw new IllegalStateException("Native resident instance layout changed");
            int next=ModernArmorAuxiliaryBudget.capacity(data.length/19,oldCount+1),nextUpload=Math.max(next,upload.capacity()/19);
            if(!budget.grow(instances,next,nextUpload,Math.max(next,budget.gpuCapacity(profile.geometry)))||!budget.used(instances,oldCount+1)){budgetFallbacks++;return false;}
            if(data.length<next*19){data=Arrays.copyOf(data,next*19);api.capture.data.set(instances,data);}
            if(upload.capacity()<nextUpload*19)api.uploadBuffer.set(instances,BufferUtils.createIntBuffer(nextUpload*19));
            oldBatch=(int)api.capture.batchCount.get(batch);oldMain=(int)api.capture.backendCount.get(main);
            GpuResidentBodyPacket.write(data,oldSize,job,mask,profile.id,(light&255)|((light>>>16&255)<<8)|((int)api.capture.selectedLayer.get(main)&255)<<16,age,root.m30(),root.m31(),root.m32());
            changed=true;api.capture.size.set(instances,oldSize+19);api.capture.count.set(instances,oldCount+1);api.capture.batchCount.set(batch,oldBatch+1);api.capture.backendCount.set(main,oldMain+1);
            owner.batches.put(batch,Boolean.TRUE);actorsQueued++;partsQueued+=Integer.bitCount(mask);state="Whole-body actor records queued";return true;
        } catch(Throwable failure) {
            if(changed)try{api.capture.size.set(instances,oldSize);api.capture.count.set(instances,oldCount);api.capture.batchCount.set(batch,oldBatch);api.capture.backendCount.set(main,oldMain);budget.used(instances,oldCount);}catch(Throwable repair){failure.addSuppressed(repair);}
            fail(failure);return false;
        }
    }
    private static boolean binary(GpuVoxelSkinUploadFacts.Fact fact){Boolean value=binary.get(fact);if(value!=null)return value;if(binary.size()>=1024)return false;boolean result=true;for(byte alpha:fact.alpha)if(alpha!=0&&Byte.toUnsignedInt(alpha)!=255){result=false;break;}binary.put(fact,result);return result;}

    public static void drawNative(Object geometry,Object commands,Object nativeInstances) {
        Profile profile=geometries.get(geometry);if(profile==null)return;
        try {
            IntBuffer payload=(IntBuffer)api.instanceUpload.invoke(nativeInstances);int count=(int)api.capture.count.get(nativeInstances);
            if(count==0)return;if(count<0||count>8192||payload.remaining()!=count*19||!payload.isDirect())throw new IllegalStateException("Invalid resident actor batch");
            if(failed){profile.draw.replay(payload,profile.uv);return;}
            try {
                if(GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0)!=0)throw new IllegalStateException("Foreign sampler requires native per-part rendering");
                int meta=profile.owner.metadata();profile.draw.draw(payload,count,meta,profile.owner.profiles.size(),budget.gpuCapacity(geometry));
                state="Whole-body resident rendering active";
            } catch(Throwable failure){fail(failure);profile.draw.replay(payload,profile.uv);}
        } catch(Throwable failure){throw new IllegalStateException("Resident body replay could not preserve native rendering",failure);}
    }
    public static void deleteNative(Object geometry,Object commands){Profile profile=geometries.get(geometry);if(profile!=null)profile.draw.close();}
    public static void beforeMainClose(Object main,Object commands){Owner owner=owners.remove(main);if(owner!=null)owner.close();}
    public static void reset() {
        if(!owners.isEmpty()) {
            boolean own=GpuVoxelSkinNativeAccess.acquireManagedScope(api.managedActive,RenderDevice.INSTANCE,RenderDevice::enterManagedCode);
            try(var commands=RenderDevice.INSTANCE.createCommandList()){for(Owner owner:owners.values())owner.close();}
            finally{GpuVoxelSkinNativeAccess.releaseManagedScope(own,RenderDevice::exitManagedCode);}
        }
        owners.clear();geometries.clear();binary.clear();normal=mirrored=null;GpuResidentBodyDraw.closeProgram();GpuResidentBodyShaderState.close();failed=false;state="Waiting for a supported whole-body actor";
    }
    private static void fail(Throwable failure){if(!failed)LogManager.getLogger("Arcadium/Resident Bodies").warn("Private whole-body renderer disabled; native part rendering retained",failure);failed=true;errors++;state="Native per-part rendering retained after an error";}
    public static String status(){return GpuResidentBodyShaders.active()?state:"Private resident body renderer is off";}
    public static int owners(){return owners.size();}
    public static int profiles(){return geometries.size();}
    public static int retainedInstanceUnits(){return budget.units();}

    private static final class Owner {
        final Object main;
        final IdentityHashMap<Object,Profile> profiles=new IdentityHashMap<>();
        final IdentityHashMap<Object,Profile> instances=new IdentityHashMap<>();
        final IdentityHashMap<Object,Boolean> batches=new IdentityHashMap<>();
        final int[] uv=new int[128*36];int uvBuffer,uploadedProfiles;
        Owner(Object main){this.main=main;}
        int metadata(){
            if(uvBuffer==0)uvBuffer=GL15.glGenBuffers();if(uploadedProfiles==profiles.size())return uvBuffer;
            int old=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            try{IntBuffer data=BufferUtils.createIntBuffer(profiles.size()*36);data.put(uv,0,data.capacity()).flip();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,uvBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,GL15.GL_STATIC_DRAW);uploadedProfiles=profiles.size();return uvBuffer;}
            finally{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,old);}
        }
        void close(){
            for(Object batch:batches.keySet()){Map<?,?> parts=(Map<?,?>)api.batchParts.get(batch);for(Profile profile:profiles.values())parts.remove(profile.geometry);}
            for(Profile profile:profiles.values()){profile.draw.close();geometries.remove(profile.geometry);}if(uvBuffer!=0)GL15.glDeleteBuffers(uvBuffer);
            uvBuffer=0;profiles.clear();instances.clear();batches.clear();budget.release(main);
        }
    }
    private static final class Profile {
        final Owner owner;final Object geometry;final int id;final int[] uv=new int[36];final GpuResidentBodyDraw draw;
        Profile(Owner owner,Object source)throws Throwable {
            this.owner=owner;id=owner.profiles.size();geometry=api.geometry.invoke();
            boolean[] mirror=(boolean[])api.profileMirrors.get(source);int[] origin=(int[])api.profileUv.get(source),size=(int[])api.profileTextureSize.get(source),boxes=(int[])api.profileSize.get(source);
            if(mirror.length!=12||origin.length!=12||size.length!=12||boxes.length!=12)throw new IllegalStateException("Unsupported native player profile");
            for(int part=0;part<12;part++){uv[part*3]=origin[part];uv[part*3+1]=size[part];uv[part*3+2]=boxes[part];}
            if(normal==null){normal=(FloatBuffer)api.nativeCube.invoke(false);mirrored=(FloatBuffer)api.nativeCube.invoke(true);}
            FloatBuffer mesh=GpuResidentBodyMesh.build(normal,mirrored,mirror);
            if(!budget.geometry(owner.main,source,geometry,(long)mesh.remaining()*4))throw new IllegalStateException("Resident body geometry budget reached");
            System.arraycopy(uv,0,owner.uv,id*36,36);draw=new GpuResidentBodyDraw(mesh);
        }
    }
}
