// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.player.PlayerEntity;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072TextureCopyEvidence {
    private static final String PREFIX="io.github.arcadiumclient.runtime.client.optimizations.";
    private static final int[] PACK={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,
            GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES};
    private static final List<Map<String,Object>> copies=new ArrayList<>(),draws=new ArrayList<>(),legacy=new ArrayList<>();
    private static final Set<String> compared=new HashSet<>();
    private static final Set<String> observedMeshes=new HashSet<>();
    private static final IdentityHashMap<Object,Integer> meshParts=new IdentityHashMap<>();
    private static final List<String> failures=new ArrayList<>();
    private static boolean hooked;
    private static String actor;
    private GpuVoxelSkin072TextureCopyEvidence(){}
    private static boolean enabled(){return Boolean.getBoolean("arcadium.voxelQuality072.textureCopyProbe")&&PlayerPipeline072Qa.enabled()
            &&System.getProperty("arcadium.playerPipeline072.mode","visual").equals("visual");}
    public static void observeModel(Object model,Object player){
        actor=null;if(!enabled()||!(model instanceof PlayerModel parts)||!(player instanceof PlayerEntity entity)
                ||!player.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor"))return;
        try{
            if((int)value(null,PlayerPipeline072Qa.class,"phase")<3)return;actor=entity.getName();
            ModelPart[] outer={parts.hat,parts.leftPants,parts.rightPants,parts.leftSleeve,parts.rightSleeve,parts.jacket};
            for(int index=0;index<outer.length;index++){Object mesh=value(outer[index],"skinLayers$mesh");if(mesh!=null&&meshParts.size()<64)meshParts.put(mesh,6+index);}
        }catch(Exception failure){hook();failures.add("Cannot associate the actual owned model: "+failure);}
    }
    public static void legacy(Object mesh,float scale){
        if(!enabled()||actor==null||legacy.size()>=24||!meshParts.containsKey(mesh))return;
        String key=actor+":"+meshParts.get(mesh)+":"+System.identityHashCode(mesh);if(!observedMeshes.add(key))return;hook();
        try{
            Map<String,Object> record=new LinkedHashMap<>();record.put("actor",actor);record.put("part",meshParts.get(mesh));record.put("mesh",System.identityHashCode(mesh));record.put("scale",scale);
            record.put("nanoTime",System.nanoTime());record.put("visualPhase",value(null,PlayerPipeline072Qa.class,"phase"));
            record.put("physicalModelView",matrix(GL11.GL_MODELVIEW_MATRIX));record.put("physicalProjection",matrix(GL11.GL_PROJECTION_MATRIX));
            record.put("physicalModelViewDouble",matrixDouble(GL11.GL_MODELVIEW_MATRIX));record.put("physicalProjectionDouble",matrixDouble(GL11.GL_PROJECTION_MATRIX));
            float[] quads=(float[])value(mesh,"quads");record.put("firstRawQuad",Arrays.copyOf(quads,Math.min(23,quads.length)));
            if(quads.length>=23){float[] vertices=new float[12];for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++)vertices[vertex*3+axis]=quads[3+vertex*5+axis]*scale;record.put("firstQuadVertices",vertices);record.put("vertexUnits","model units after the actual render(scale), before ModelView");}
            Class<?> instancing=Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing");var currentMethod=instancing.getDeclaredMethod("current");currentMethod.setAccessible(true);Object current=currentMethod.invoke(null);
            if(current!=null){Object capture=value(current,"activeCapture");if(capture!=null){Object captured=value(capture,"matrices");if(captured instanceof Matrix4fc m){float[] values=new float[16];m.get(values);record.put("capturedModelMatrix",values);}record.put("captureActive",value(capture,"modelActive"));}}
            legacy.add(record);
        }catch(Throwable failure){failures.add("Cannot capture actual legacy matrix evidence: "+failure);}
    }

    public static void beforeDraw(Object draw,int count){
        if(!enabled()||draws.size()>=16)return;
        int phase;try{phase=(int)value(null,PlayerPipeline072Qa.class,"phase");}catch(Exception failure){hook();failures.add("Cannot read actual visual phase: "+failure);return;}
        if(phase<3)return;
        Minecraft mc=Minecraft.getInstance();
        if(!mc.getSession().getUsername().equals("ArcadiumTest")||!mc.getSession().getAccessToken().equals("0"))throw new IllegalStateException("Texture-copy evidence requires the owned fake session");
        hook();Map<String,Object> record=new LinkedHashMap<>();record.put("visualPhase",phase);record.put("nanoTime",System.nanoTime());
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int oldPack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),oldCopy=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER),oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int[] oldPacking=new int[PACK.length];for(int i=0;i<PACK.length;i++)oldPacking[i]=GL11.glGetInteger(PACK[i]);
        int fbo=0;boolean pixelScope=false;
        try{
            if(count<1||count>512)throw new IllegalStateException("Owned voxel instance count is outside the diagnostic bound");
            if(GL11.glGetError()!=0)throw new IllegalStateException("GL was not clean before native texture-copy evidence");
            int arrayEnabled=GL20.glGetUniformLocation(oldProgram,"uTextureArrayEnabled"),sampler=GL20.glGetUniformLocation(oldProgram,"uTextureArray");
            if(arrayEnabled<0||sampler<0||GL20.glGetUniformi(oldProgram,arrayEnabled)==0)throw new IllegalStateException("The actual native array sampler is not active");
            int unit=GL20.glGetUniformi(oldProgram,sampler);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int array=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY),width=GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_TEXTURE_HEIGHT);
            if(array==0||width!=64||height!=64)throw new IllegalStateException("Expected the actual64x64 skin array");
            record.put("arrayTexture",array);record.put("arrayUnit",unit);record.put("samplerObject",GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit));
            record.put("arrayFormat",GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_TEXTURE_INTERNAL_FORMAT));
            record.put("arrayLayers",GL11.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D_ARRAY,0,GL12.GL_TEXTURE_DEPTH));
            Map<String,Object> filtering=new LinkedHashMap<>();int samplerName=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
            for(var property:Map.of("minFilter",GL11.GL_TEXTURE_MIN_FILTER,"magFilter",GL11.GL_TEXTURE_MAG_FILTER,"wrapS",GL11.GL_TEXTURE_WRAP_S,"wrapT",GL11.GL_TEXTURE_WRAP_T).entrySet())
                filtering.put(property.getKey(),samplerName==0?GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,property.getValue()):GL33.glGetSamplerParameteri(samplerName,property.getValue()));
            filtering.put("baseLevel",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL12.GL_TEXTURE_BASE_LEVEL));filtering.put("maxLevel",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL12.GL_TEXTURE_MAX_LEVEL));
            filtering.put("swizzleR",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL33.GL_TEXTURE_SWIZZLE_R));filtering.put("swizzleG",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL33.GL_TEXTURE_SWIZZLE_G));
            filtering.put("swizzleB",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL33.GL_TEXTURE_SWIZZLE_B));filtering.put("swizzleA",GL11.glGetTexParameteri(GL30.GL_TEXTURE_2D_ARRAY,GL33.GL_TEXTURE_SWIZZLE_A));
            record.put("actualArraySampler",filtering);GL13.glActiveTexture(oldActive);
            Object renderer=value(null,Class.forName(PREFIX+"GpuVoxelSkinPipeline"),"backend"),storage=null;
            for(Object candidate:((Map<?,?>)value(renderer,"owners")).values())if(value(candidate,"draw")==draw){storage=candidate;break;}
            if(storage==null)throw new IllegalStateException("No actual owned auxiliary backend matches the voxel draw");
            Object auxiliary=value(storage,"auxiliary"),manager=value(auxiliary,"textureArrays"),pool=null;
            for(Object candidate:((Map<?,?>)value(manager,"pools")).values())if((int)value(candidate,"texture")==array){pool=candidate;break;}
            if(pool==null)throw new IllegalStateException("Native sampler array is absent from the actual auxiliary pools");
            int attribute=GL20.glGetAttribLocation(oldProgram,"aModel0");
            if(attribute<0||GL20.glGetVertexAttribi(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE)!=76
                    ||GL20.glGetVertexAttribi(attribute,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)!=1)throw new IllegalStateException("Unknown actual native instance attribute layout");
            int buffer=GL20.glGetVertexAttribi(attribute,GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
            PointerBuffer pointer=BufferUtils.createPointerBuffer(1);GL20.glGetVertexAttribPointerv(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER,pointer);long byteOffset=pointer.get(0);
            ByteBuffer raw=BufferUtils.createByteBuffer(count*76);GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT|GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);long bufferBytes=GL32.glGetBufferParameteri64(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE);
            if(byteOffset<0||byteOffset>bufferBytes-(long)count*76)throw new IllegalStateException("Actual GPU instance attribute range exceeds its bound buffer");
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,byteOffset,raw);IntBuffer words=raw.asIntBuffer();record.put("instanceByteOffset",byteOffset);record.put("instanceBufferBytes",bufferBytes);
            Set<Integer> layers=new LinkedHashSet<>();List<Map<String,Object>> instances=new ArrayList<>();
            for(int instance=0;instance<count;instance++){
                int offset=instance*19,layer=(words.get(offset+13)>>>16)&255;layers.add(layer);
                if(instance<48){float[] matrix=new float[16];matrix[15]=1;for(int column=0;column<4;column++)for(int row=0;row<3;row++)matrix[column*4+row]=Float.intBitsToFloat(words.get(offset+column*3+row));
                    Map<String,Object> instanceRecord=new LinkedHashMap<>();instanceRecord.put("matrix",matrix);instanceRecord.put("layer",layer);instanceRecord.put("firstVertex",words.get(offset+16));instanceRecord.put("vertices",words.get(offset+17));
                    instanceRecord.put("stillHasComputeMarker",words.get(offset+1)==Float.floatToRawIntBits(-9876543f));instanceRecord.put("geometry",geometry(storage,words.get(offset+16)));instances.add(instanceRecord);}
            }
            record.put("actualGpuInstanceBuffer",buffer);record.put("actualGpuInstances",instances);record.put("count",count);record.put("referencedLayers",layers);
            record.put("physicalModelView",matrix(GL11.GL_MODELVIEW_MATRIX));record.put("physicalProjection",matrix(GL11.GL_PROJECTION_MATRIX));
            record.put("physicalModelViewDouble",matrixDouble(GL11.GL_MODELVIEW_MATRIX));record.put("physicalProjectionDouble",matrixDouble(GL11.GL_PROJECTION_MATRIX));
            int mvp=GL20.glGetUniformLocation(oldProgram,"uModelViewProjection");record.put("nativeMvpLocation",mvp);
            if(mvp>=0){FloatBuffer actual=BufferUtils.createFloatBuffer(16);GL20.glGetUniformfv(oldProgram,mvp,actual);float[] values=new float[16];actual.get(values);record.put("nativeMvp",values);}
            else{List<String> names=new ArrayList<>();int uniforms=GL20.glGetProgrami(oldProgram,GL20.GL_ACTIVE_UNIFORMS);IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);for(int index=0;index<Math.min(uniforms,128);index++)names.add(GL20.glGetActiveUniform(oldProgram,index,256,size,type));record.put("activeUniformNames",names);}
            record.put("samples",GL11.glGetInteger(GL13.GL_SAMPLES));record.put("sampleBuffers",GL11.glGetInteger(GL13.GL_SAMPLE_BUFFERS));
            record.put("multisample",GL11.glIsEnabled(GL13.GL_MULTISAMPLE));record.put("alphaToCoverage",GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE));record.put("alphaToOne",GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE));
            record.put("sampleCoverage",GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE));record.put("sampleCoverageValue",GL11.glGetFloat(GL13.GL_SAMPLE_COVERAGE_VALUE));
            record.put("alphaTest",GL11.glIsEnabled(GL11.GL_ALPHA_TEST));record.put("alphaReference",GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
            record.put("alphaFunc",GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));record.put("program",oldProgram);
            if(GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)>=GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH))throw new IllegalStateException("No pixel state stack capacity");
            GL11.glPushAttrib(GL11.GL_PIXEL_MODE_BIT);pixelScope=true;GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            for(int key:PACK)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?1:0);
            for(int key:new int[]{GL11.GL_RED_SCALE,GL11.GL_GREEN_SCALE,GL11.GL_BLUE_SCALE,GL11.GL_ALPHA_SCALE})GL11.glPixelTransferf(key,1);
            for(int key:new int[]{GL11.GL_RED_BIAS,GL11.GL_GREEN_BIAS,GL11.GL_BLUE_BIAS,GL11.GL_ALPHA_BIAS})GL11.glPixelTransferf(key,0);
            GL11.glPixelTransferi(GL11.GL_MAP_COLOR,0);fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            Map<?,?> poolLayers=(Map<?,?>)value(pool,"layers");
            for(int layer:layers){
                Map.Entry<?,?> selected=null;for(var entry:poolLayers.entrySet())if((int)value(entry.getValue(),"index")==layer){selected=entry;break;}
                if(selected==null)throw new IllegalStateException("An actual GPU layer has no native source mapping: "+layer);
                Object descriptor=selected.getValue(),source=value(descriptor,"source");int sourceId=(int)value(descriptor,"sourceId"),generation=(int)value(descriptor,"generation");
                String identity=array+":"+layer+":"+sourceId+":"+generation+":"+System.identityHashCode(source);
                if(!compared.add(identity))continue;
                if(copies.size()>=16)throw new IllegalStateException("Texture-copy evidence exceeded its bounded layer count");
                Map<String,Object> copy=new LinkedHashMap<>();copy.put("identifier",selected.getKey().toString());copy.put("sourceClass",source.getClass().getName());copy.put("sourceIdentity",System.identityHashCode(source));
                copy.put("sourceTexture",sourceId);copy.put("nativeArray",array);copy.put("layer",layer);copy.put("nativeGeneration",generation);copy.put("layerFrame",value(descriptor,"frame"));copy.put("backendFrame",value(auxiliary,"frame"));
                if(!selected.getKey().equals(Features062Qa.skin))throw new IllegalStateException("The source layer is outside the owned synthetic skin fixture");
                copy.put("actual2DSampler",sourceState(sourceId));
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,sourceId,0);byte[] original=readPixels();copy.put("sourceAttachmentBits",attachmentBits());
                GL30.glFramebufferTextureLayer(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,array,0,layer);byte[] destination=readPixels();copy.put("arrayAttachmentBits",attachmentBits());
                Map<String,Object> delta=difference(original,destination);copy.putAll(delta);copy.put("rgba8Bytes",original.length);copy.put("pixelOrigin","texture row0 for both GPU images");
                copies.add(copy);
                Path folder=output().getParent();int ordinal=copies.size();save(original,folder.resolve("voxel-copy-"+ordinal+"-source.png"));save(destination,folder.resolve("voxel-copy-"+ordinal+"-array.png"));
                copy.put("sourceSampledPixels",nonzero(original));copy.put("arraySampledPixels",nonzero(destination));
                if((int)delta.get("differentBytes")!=0)failures.add("Actual native array copy differs from2D source at layer "+layer);
                copy.put("cpuImageAvailable",false);copy.put("cpuEquivalence","not checked: CPU image unavailable");
                try{
                    Class<?> imageAccess=Class.forName("org.polyfrost.skinlayers.skin.SkinTextureImageAccess");copy.put("cpuImageBridge",imageAccess.isInstance(source));
                    Object cpu=imageAccess.isInstance(source)?imageAccess.getMethod("image").invoke(source):null;copy.put("cpuImageType",cpu==null?"null":cpu.getClass().getName());
                    if(cpu instanceof BufferedImage bitmap){copy.put("cpuImageWidth",bitmap.getWidth());copy.put("cpuImageHeight",bitmap.getHeight());
                        if(bitmap.getWidth()==64&&bitmap.getHeight()==64){
                            copy.put("cpuImageAvailable",true);byte[] expected=new byte[original.length];for(int y=0;y<64;y++)for(int x=0;x<64;x++){int at=(y*64+x)*4,color=bitmap.getRGB(x,y);expected[at]=(byte)(color>>16);expected[at+1]=(byte)(color>>8);expected[at+2]=(byte)color;expected[at+3]=(byte)(color>>24);}
                            Map<String,Object> cpuDelta=difference(expected,original);copy.put("actualCpuBitmapVs2D",cpuDelta);copy.put("cpuEquivalence","checked against actual retained bitmap");
                            if((int)cpuDelta.get("differentBytes")!=0)failures.add("Actual2D source differs from its owned CPU bitmap at layer "+layer);
                        }
                    }
                }catch(ReflectiveOperationException unavailable){copy.put("cpuImageApiUnavailable",unavailable.toString());}
            }
            record.put("glError",GL11.glGetError());if(((Number)record.get("glError")).intValue()!=0)failures.add("GL error while observing the actual native draw");
        }catch(Throwable failure){record.put("failure",failure.toString());failures.add(failure.toString());}
        finally{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            if(pixelScope)GL11.glPopAttrib();GL11.glReadBuffer(oldReadBuffer);
            for(int i=0;i<PACK.length;i++)GL11.glPixelStorei(PACK[i],oldPacking[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldPack);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,oldCopy);GL13.glActiveTexture(oldActive);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);
            boolean restored=oldRead==GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)&&oldDraw==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    &&oldReadBuffer==GL11.glGetInteger(GL11.GL_READ_BUFFER)&&oldPack==GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
                    &&oldCopy==GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER)&&oldActive==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                    &&oldProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)&&oldVao==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&oldArray==GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            for(int i=0;i<PACK.length;i++)restored&=oldPacking[i]==GL11.glGetInteger(PACK[i]);record.put("allTrackedStateRestored",restored);
            int error=GL11.glGetError();record.put("finalGlError",error);if(!restored||error!=0)failures.add("Texture-copy observation leaked state or produced a GL error");draws.add(record);
        }
    }
    private static byte[] readPixels(){
        if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Owned read-only framebuffer is incomplete");
        ByteBuffer pixels=BufferUtils.createByteBuffer(64*64*4);GL11.glReadPixels(0,0,64,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] bytes=new byte[pixels.remaining()];pixels.get(bytes);return bytes;
    }
    private static Map<String,Object> sourceState(int texture){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0);int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);Map<String,Object> state=new LinkedHashMap<>();
            state.put("texture",texture);state.put("samplerObject",GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0));state.put("internalFormat",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT));
            int sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0);
            for(var property:Map.of("minFilter",GL11.GL_TEXTURE_MIN_FILTER,"magFilter",GL11.GL_TEXTURE_MAG_FILTER,"wrapS",GL11.GL_TEXTURE_WRAP_S,"wrapT",GL11.GL_TEXTURE_WRAP_T).entrySet())state.put(property.getKey(),sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,property.getValue()):GL33.glGetSamplerParameteri(sampler,property.getValue()));
            state.put("baseLevel",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL));state.put("maxLevel",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL));
            state.put("swizzleR",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R));state.put("swizzleG",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_G));
            state.put("swizzleB",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_B));state.put("swizzleA",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A));
            state.put("environmentMode",GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE));return state;
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(active);}
    }
    private static Map<String,Object> attachmentBits(){
        Map<String,Object> result=new LinkedHashMap<>();
        for(var property:Map.of("red",GL30.GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE,"green",GL30.GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE,"blue",GL30.GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE,"alpha",GL30.GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE).entrySet())
            result.put(property.getKey(),GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,property.getValue()));
        return result;
    }
    private static Map<String,Object> geometry(Object storage,int first)throws Exception{
        Object cache=value(storage,"cache");
        for(Object entry:(List<?>)value(cache,"entries"))if((int)value(entry,"first")==first){
            Object mesh=value(entry,"mesh");float[] raw=(float[])value(mesh,"quads");Map<String,Object> record=new LinkedHashMap<>();record.put("mesh",System.identityHashCode(mesh));record.put("part",meshParts.getOrDefault(mesh,-1));
            record.put("rawQuad",Arrays.copyOf(raw,Math.min(23,raw.length)));
            if(raw.length>=23){float[] vertices=new float[12];for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++)vertices[vertex*3+axis]=raw[3+vertex*5+axis]*.0625f;record.put("firstQuadVertices",vertices);record.put("vertexScale",.0625f);record.put("vertexUnits","actual immutable atlas model units, before instance matrix");}return record;
        }
        throw new IllegalStateException("Actual GPU voxel atlas range has no immutable mesh association");
    }
    private static Map<String,Object> difference(byte[] source,byte[] destination){int different=0,max=0;for(int i=0;i<source.length;i++){int delta=Math.abs((source[i]&255)-(destination[i]&255));if(delta!=0)different++;max=Math.max(max,delta);}return Map.of("differentBytes",different,"maximumByteDifference",max,"exact",different==0);}
    private static int nonzero(byte[] pixels){int count=0;for(int i=0;i<pixels.length;i+=4)if(pixels[i]!=0||pixels[i+1]!=0||pixels[i+2]!=0||pixels[i+3]!=0)count++;return count;}
    private static void save(byte[] bytes,Path file)throws Exception{BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++){int i=(y*64+x)*4;image.setRGB(x,y,(bytes[i+3]&255)<<24|(bytes[i]&255)<<16|(bytes[i+1]&255)<<8|(bytes[i+2]&255));}ImageIO.write(image,"png",file.toFile());}
    private static float[] matrix(int name){FloatBuffer buffer=BufferUtils.createFloatBuffer(16);GL11.glGetFloatv(name,buffer);float[] values=new float[16];buffer.get(values);return values;}
    private static double[] matrixDouble(int name){DoubleBuffer buffer=BufferUtils.createDoubleBuffer(16);GL11.glGetDoublev(name,buffer);double[] values=new double[16];buffer.get(values);return values;}
    private static Object value(Object target,String name)throws Exception{return value(target,target.getClass(),name);}
    private static Object value(Object target,Class<?> type,String name)throws Exception{for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Path output(){return Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath().resolveSibling("voxel-texture-copy.json");}
    private static void hook(){if(hooked)return;hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(()->{
        try{Map<String,Object> result=new LinkedHashMap<>();result.put("correctnessOnly",true);result.put("notPerformanceEvidence",true);result.put("draws",draws);result.put("copies",copies);result.put("legacy",legacy);result.put("failures",failures);
            result.put("passed",!copies.isEmpty()&&!draws.isEmpty()&&failures.isEmpty());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception failure){failure.printStackTrace();}
    },"Arcadium native skin texture-copy QA"));}
}
