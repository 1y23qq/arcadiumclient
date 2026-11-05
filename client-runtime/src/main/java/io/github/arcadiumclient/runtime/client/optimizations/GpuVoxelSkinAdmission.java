// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.BitSet;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.*;

final class GpuVoxelSkinAdmission {
    static final int MAX_SOURCES=1024, MAX_MESHES=1024, MAX_CLASSIFICATIONS=4096;
    private record Footprint(int part,boolean slim,int wrapS,int wrapT,BitSet texels) {}
    private record Classification(int part,boolean slim,int wrapS,int wrapT,GpuVoxelSkinImagePolicy.Alpha alpha) {}
    private static final class Snapshot {
        final GpuVoxelSkinImagePolicy.Stamp stamp;
        final Object poolKey;
        final byte[] alpha;
        final int min,mag,wrapS,wrapT;
        boolean ambiguousImage;
        final IdentityHashMap<Object,Classification> meshes=new IdentityHashMap<>();
        Snapshot(GpuVoxelSkinImagePolicy.Stamp stamp,Object key,byte[] alpha,int min,int mag,int s,int t) {
            this.stamp=stamp;poolKey=key;this.alpha=alpha;this.min=min;this.mag=mag;wrapS=s;wrapT=t;
        }
    }
    private final IdentityHashMap<Object,Snapshot> sources=new IdentityHashMap<>();
    private final IdentityHashMap<Object,Footprint> footprints=new IdentityHashMap<>();
    private final Object[] actorMeshes=new Object[12],actorOffsets=new Object[12];
    private GpuVoxelSkinImageAccess images;
    private boolean imageApiUnavailable;
    private int classifications;
    private boolean sampled,rejected;
    private int physicalTexture,physicalActive,physicalSampler,physicalEnvironment;
    private Object admittedUpload;
    private String reason="Waiting for an eligible opaque skin";

    void beginActor(){sampled=false;rejected=false;admittedUpload=null;}
    boolean admit(GpuVoxelSkinNativeAccess api,Object main,Identifier texture,ModelPart[] parts,int mask) {
        try {
            if(texture==null||parts==null||parts.length!=12||(mask&~0xFC0)!=0)return reject("Unknown outer skin model",0);
            if(!GpuVoxelSkinImagePolicy.defaultDimensions(GpuVoxelSkinBridge.options()))return reject("Custom voxel dimensions use original layers",1);
            int visible=0,meshes=0,offsets=0;
            for(int part=6;part<12;part++){
                ModelPart node=parts[part];if(node==null||node.visible&&!node.invisible)visible|=1<<part;
                actorMeshes[part]=node==null?null:GpuVoxelSkinBridge.mesh(node);actorOffsets[part]=node==null?null:GpuVoxelSkinBridge.rawOffset(node);
                if(actorMeshes[part]!=null)meshes|=1<<part;if(actorOffsets[part]!=null)offsets|=1<<part;
            }
            int required=GpuVoxelSkinImagePolicy.requiredParts(GpuVoxelSkinBridge.enabledMask(),visible);
            if((mask&~required)!=0)return reject("Custom skin part visibility uses original layers",0);
            if(!GpuVoxelSkinImagePolicy.attachmentsReady(required,meshes,offsets))return reject("An active outer skin part is pending",0);
            if(CeraEmissiveAdmission.nativeSubmitRequired(texture))return reject("Emissive skin uses original layers",2);
            var source=Minecraft.getInstance().getTextureManager().get(texture);
            if(source==null)return reject("Skin texture is not ready",0);
            int name=source.getGlId(),generation=api.sourceGeneration(source);
            Object key=api.sourcePoolKey(main,texture,source,name,generation);
            if(key==null)return reject("Skin texture generation is not current",0);
            if(!sampled){
                physicalActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                physicalTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                physicalSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0);
                physicalEnvironment=GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE);sampled=true;
            }
            if(physicalActive!=GL13.GL_TEXTURE0||physicalTexture!=name||physicalSampler!=0||physicalEnvironment!=GL11.GL_MODULATE)return reject("Unsupported skin sampler binding",3);
            if(imageApiUnavailable)return reject("Skin image API is unavailable",0);
            if(images==null)try{images=new GpuVoxelSkinImageAccess();}catch(ReflectiveOperationException mismatch){imageApiUnavailable=true;GpuVoxelSkinPipeline.admissionFailure=mismatch.toString();return reject("Skin image API is unavailable",0);}
            Object image=GpuVoxelSkinUploadFacts.current(source,name,generation);
            if(image==null)return reject("Skin upload alpha facts are unavailable",0);
            if(!api.sourceCopyMatches(main,texture,source,name,generation,image))return reject("Native skin array has no current upload copy",0);
            Snapshot snapshot=sources.get(source);
            if(snapshot!=null&&GpuVoxelSkinImagePolicy.ambiguousImage(snapshot.stamp,snapshot.ambiguousImage,source,image,name,generation)){
                if(!snapshot.ambiguousImage)GpuVoxelSkinPipeline.admissionInvalidations++;
                snapshot.ambiguousImage=true;return reject("Skin CPU image changed without native texture invalidation",0);
            }
            if(snapshot==null||!snapshot.stamp.matches(source,image,name,generation)||!snapshot.poolKey.equals(key)){
                if(snapshot==null&&sources.size()==MAX_SOURCES)return reject("Skin image admission cache is full",4);
                if((int)api.poolWidth.invoke(key)!=64||(int)api.poolHeight.invoke(key)!=64)return reject("Unsupported skin image dimensions",0);
                int min=(int)api.poolMin.invoke(key),mag=(int)api.poolMag.invoke(key),s=(int)api.poolWrapS.invoke(key),t=(int)api.poolWrapT.invoke(key);
                if(!GpuVoxelSkinImagePolicy.supportedSampler(min,mag,s,t))return reject("Skin sampler is outside the qualified nearest path",3);
                if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=64
                        ||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=64
                        ||!GpuVoxelSkinImagePolicy.rgba8(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),
                            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_RED_SIZE),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_GREEN_SIZE),
                            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_BLUE_SIZE),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_ALPHA_SIZE))
                        ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)!=min
                        ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)!=mag
                        ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)!=s
                        ||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)!=t)return reject("Skin CPU/native sampler descriptors differ",3);
                byte[] alpha=images.snapshot(image);
                if(alpha==null)return reject("Skin CPU image is unavailable",0);
                if(name!=source.getGlId()||generation!=api.sourceGeneration(source)||image!=GpuVoxelSkinUploadFacts.current(source,name,generation)
                        ||api.sourcePoolKey(main,texture,source,name,generation)!=key)return reject("Skin upload changed during admission",0);
                if(snapshot!=null){classifications-=snapshot.meshes.size();GpuVoxelSkinPipeline.admissionInvalidations++;}
                snapshot=new Snapshot(new GpuVoxelSkinImagePolicy.Stamp(source,image,name,generation),key,alpha,min,mag,s,t);
                sources.put(source,snapshot);GpuVoxelSkinPipeline.admissionSnapshots++;gauges();
            }
            for(int part=6;part<12;part++){
                if((required&1<<part)==0)continue;
                ModelPart modelPart=parts[part];if(modelPart==null)return reject("Outer skin model was not observed",0);
                Object mesh=actorMeshes[part],raw=actorOffsets[part];
                if(mesh==null)return reject("An active outer skin mesh is pending",0);
                if(raw==null)return reject("Outer skin offset is unavailable",0);
                var parameters=GpuVoxelSkinBridge.parameters(raw);
                if(!standard(part,parameters))return reject("Custom voxel offset uses original layers",1);
                boolean slim=parameters.shape().equals("SLIM_ARM");
                Classification known=snapshot.meshes.get(mesh);
                if(known==null||known.part!=part||known.slim!=slim||known.wrapS!=snapshot.wrapS||known.wrapT!=snapshot.wrapT){
                    if(known==null&&classifications==MAX_CLASSIFICATIONS)return reject("Skin alpha classification cache is full",4);
                    Footprint footprint=footprints.get(mesh);
                    if(footprint==null||footprint.part!=part||footprint.slim!=slim||footprint.wrapS!=snapshot.wrapS||footprint.wrapT!=snapshot.wrapT){
                        if(footprint==null&&footprints.size()==MAX_MESHES)return reject("Skin UV footprint cache is full",4);
                        BitSet texels=GpuVoxelSkinImagePolicy.footprint(GpuVoxelSkinBridge.quads(mesh),images.domain(part,slim),snapshot.min,snapshot.mag,snapshot.wrapS,snapshot.wrapT);
                        if(texels==null)return reject("Unknown skin UV footprint",0);
                        footprint=new Footprint(part,slim,snapshot.wrapS,snapshot.wrapT,texels);footprints.put(mesh,footprint);
                    }
                    known=new Classification(part,slim,snapshot.wrapS,snapshot.wrapT,GpuVoxelSkinImagePolicy.classify(snapshot.alpha,footprint.texels));
                    if(!snapshot.meshes.containsKey(mesh))classifications++;
                    snapshot.meshes.put(mesh,known);gauges();
                }else GpuVoxelSkinPipeline.admissionHits++;
                if(known.alpha==GpuVoxelSkinImagePolicy.Alpha.BINARY)return reject("Binary-cutout skin uses original layers",5);
                if(known.alpha==GpuVoxelSkinImagePolicy.Alpha.FRACTIONAL)return reject("Fractional-alpha skin uses original layers",6);
                if(known.alpha!=GpuVoxelSkinImagePolicy.Alpha.OPAQUE)return reject("Skin alpha domain is unknown",0);
            }
            admittedUpload=image;return true;
        }catch(Throwable unsupported){GpuVoxelSkinPipeline.admissionFailure=unsupported.toString();return reject("Skin image API unavailable; using original layers",0);}
    }
    private static boolean standard(int part,GpuVoxelSkinBridge.Parameters p){
        if(p==null||p.firstPerson()||p.skull())return false;
        return switch(part){case 6->p.shape().equals("HEAD");case 7,8->p.shape().equals("LEG");
            case 9,10->(p.shape().equals("ARM")||p.shape().equals("SLIM_ARM"))&&p.mirrored()==(part==10);case 11->p.shape().equals("BODY");default->false;};
    }
    private boolean reject(String message,int category){
        reason=message;
        if(!rejected){
            rejected=true;GpuVoxelSkinPipeline.admissionFallbacks++;
            switch(category){case 1->GpuVoxelSkinPipeline.dimensionFallbacks++;case 2->GpuVoxelSkinPipeline.emissiveFallbacks++;
                case 3->GpuVoxelSkinPipeline.samplerFallbacks++;case 4->GpuVoxelSkinPipeline.budgetFallbacks++;
                case 5->GpuVoxelSkinPipeline.binaryAlphaFallbacks++;case 6->GpuVoxelSkinPipeline.fractionalAlphaFallbacks++;
                default->GpuVoxelSkinPipeline.unknownImageFallbacks++;}
        }
        return false;
    }
    String reason(){return reason;}
    Object admittedUpload(){return admittedUpload;}
    void dispose(Object mesh){
        footprints.remove(mesh);for(Snapshot snapshot:sources.values())if(snapshot.meshes.remove(mesh)!=null)classifications--;
        gauges();
    }
    void reset(){sources.clear();footprints.clear();java.util.Arrays.fill(actorMeshes,null);java.util.Arrays.fill(actorOffsets,null);admittedUpload=null;classifications=0;sampled=false;rejected=false;imageApiUnavailable=false;GpuVoxelSkinPipeline.admissionFailure="";gauges();}
    private void gauges(){GpuVoxelSkinPipeline.admissionSources=sources.size();GpuVoxelSkinPipeline.admissionMeshes=footprints.size();
        GpuVoxelSkinPipeline.admissionClassifications=classifications;GpuVoxelSkinPipeline.admissionAlphaBytes=(long)sources.size()*4096;}
}
