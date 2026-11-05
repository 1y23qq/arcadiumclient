// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

public final class GpuVoxelSkin072InterpolationProbe {
    private static final int SIZE=128;
    private static final String[] MODES={"original-display-list","fixed-vbo-quads","glsl-vbo-2d","glsl-mdi-2d","glsl-mdi-array","fixed-diagonal02","fixed-diagonal13"};
    private static int ticks;
    private static boolean done;
    private static Path progressPath;
    private static boolean originalReference;
    private static String originalFailure;
    private static int originalChecks;
    private static final List<Map<String,Object>> originalPrerequisites=new ArrayList<>();
    private GpuVoxelSkin072InterpolationProbe(){}
    public static void run(){
        String output=System.getProperty("arcadium.voxelInterpolation072.output");
        if(output==null||done||++ticks<65)return;Minecraft mc=Minecraft.getInstance();if(mc.screen==null)return;done=true;
        Map<String,Object> report=new LinkedHashMap<>();progressPath=Path.of(output).resolveSibling("progress.json");progress("entered",Map.of());
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldClient=GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldPipeline=GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING);
        int oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),oldIndirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),oldPack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),oldUnpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int sampler0=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0),sampler1=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,1);
        float[] oldModel=matrix(GL11.GL_MODELVIEW_MATRIX),oldProjection=matrix(GL11.GL_PROJECTION_MATRIX);
        int program=0,fbo=0,target=0,source=0,array=0,vao=0,vbo=0,indices=0,commands=0,comparisons=0,identical=0,nonblankComparisons=0,nonblankIdentical=0,ordinaryControls=0,saved=0;
        List<Object> meshes=new ArrayList<>();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL13.glClientActiveTexture(GL13.GL_TEXTURE0);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{
            require(mc.world==null&&mc.player==null&&mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0"),"Use only the owned offline title fixture");
            require(GL11.glGetError()==0,"GL was not clean before interpolation probe");
            report.put("entryVertexArray",oldVao);report.put("entryArrayBuffer",oldArray);report.put("originalClientArrayContract","VAO0 and ARRAY_BUFFER0; https://registry.khronos.org/OpenGL/extensions/ARB/ARB_vertex_array_object.txt");
            progress("extract",Map.of());var samples=new ArrayList<>(GpuVoxelSkin072InterpolationData.extract());samples.sort(Comparator.comparing(GpuVoxelSkin072InterpolationData.Sample::collapsed));report.put("immutableSamples",samples);report.put("sampleCount",samples.size());progress("extracted",Map.of("samples",samples.size()));
            report.put("renderer",GL11.glGetString(GL11.GL_RENDERER));report.put("version",GL11.glGetString(GL11.GL_VERSION));
            program=program();fbo=GL30.glGenFramebuffers();target=GL11.glGenTextures();source=GL11.glGenTextures();array=GL11.glGenTextures();vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();indices=GL15.glGenBuffers();commands=GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);GL41.glBindProgramPipeline(0);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            GL33.glBindSampler(0,0);GL33.glBindSampler(1,0);
            for(int unit=0;unit<GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);unit++){
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);for(int flag:new int[]{GL11.GL_TEXTURE_1D,GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_3D,GL13.GL_TEXTURE_CUBE_MAP,GL11.GL_TEXTURE_GEN_S,GL11.GL_TEXTURE_GEN_T,GL11.GL_TEXTURE_GEN_R,GL11.GL_TEXTURE_GEN_Q})GL11.glDisable(flag);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glEnable(GL11.GL_TEXTURE_2D);
            for(int flag:new int[]{GL11.GL_LIGHTING,GL11.GL_COLOR_MATERIAL,GL11.GL_FOG,GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_SCISSOR_TEST,GL11.GL_CULL_FACE,GL11.GL_DITHER,GL13.GL_MULTISAMPLE,GL30.GL_FRAMEBUFFER_SRGB})GL11.glDisable(flag);
            for(int plane=0;plane<GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES);plane++)GL11.glDisable(GL11.GL_CLIP_PLANE0+plane);
            GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glViewport(0,0,SIZE,SIZE);GL11.glColor4f(1,1,1,1);GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            for(int key:new int[]{GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS})GL11.glPixelStorei(key,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,0);GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glBindTexture(GL11.GL_TEXTURE_2D,target);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,SIZE,SIZE,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Interpolation FBO incomplete");
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,commands);IntBuffer indirect=BufferUtils.createIntBuffer(4);indirect.put(new int[]{4,1,0,0}).flip();GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,indirect,GL15.GL_STATIC_DRAW);
            var constructor=Class.forName("org.polyfrost.skinlayers.render.CompiledMesh").getConstructor(float[].class);List<Map<String,Object>> results=new ArrayList<>();
            FloatBuffer vertices=BufferUtils.createFloatBuffer(20);ByteBuffer pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);
            int path=GL20.glGetUniformLocation(program,"uPath"),useArray=GL20.glGetUniformLocation(program,"uArray");
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uTexture"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uArrayTexture"),1);GL20.glUseProgram(0);
            Path folder=Path.of(output).toAbsolutePath().getParent();Files.createDirectories(folder);
            int maximum=Integer.getInteger("arcadium.voxelInterpolation072.maxCases",0);probeCases:for(int texture=0;texture<2;texture++){
                BufferedImage image=texture==0?GpuVoxelSkin072AlphaSeed.image():gradient();ImageIO.write(image,"png",folder.resolve("input-"+texture+".png").toFile());ByteBuffer rgba=rgba(image);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,64,64,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);
                GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,array);rgba.rewind();GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA8,64,64,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                for(int filter:new int[]{GL11.GL_NEAREST,GL11.GL_LINEAR}){
                    parameters(GL11.GL_TEXTURE_2D,filter);GL13.glActiveTexture(GL13.GL_TEXTURE1);parameters(GL30.GL_TEXTURE_2D_ARRAY,filter);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    Map<String,Object> samplerState=new LinkedHashMap<>();samplerState.put("texture2D",textureState(GL11.GL_TEXTURE_2D));GL13.glActiveTexture(GL13.GL_TEXTURE1);samplerState.put("textureArray",textureState(GL30.GL_TEXTURE_2D_ARRAY));GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    samplerState.put("environmentMode",GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE));FloatBuffer color=BufferUtils.createFloatBuffer(4);GL11.glGetFloatv(GL11.GL_CURRENT_COLOR,color);samplerState.put("currentColor",new float[]{color.get(0),color.get(1),color.get(2),color.get(3)});
                    for(var sample:samples){
                        progress("construct-display-list",Map.of("texture",texture,"filter",filter,"sample",sample.name()));
                        Object mesh=constructor.newInstance((Object)sample.quad().clone());meshes.add(mesh);float[] converted=GpuVoxelSkin072InterpolationData.vertices(sample);vertices.clear();vertices.put(converted).flip();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
                        for(int projection=0;projection<3;projection++)for(boolean alphaTest:new boolean[]{false,true}){
                            configureView(sample,projection);if(alphaTest){GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1f);}else GL11.glDisable(GL11.GL_ALPHA_TEST);
                            List<Map<String,Object>> modes=new ArrayList<>();byte[] reference=null,referenceClear=null;
                            for(int mode=0;mode<MODES.length;mode++){
                                Map<String,Object> stage=Map.of("case",results.size(),"sample",sample.name(),"texture",texture,"filter",filter,"projection",projection,"alphaTest",alphaTest,"mode",MODES[mode]);progress("before-draw",stage);
                                GL20.glUseProgram(0);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);GL11.glColor4f(1,1,1,1);
                                pixels.clear();GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] clear=new byte[pixels.remaining()];pixels.get(clear);
                                GL20.glDisableVertexAttribArray(0);GL20.glDisableVertexAttribArray(1);GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                                GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
                                if(mode==0){
                                    GL30.glBindVertexArray(0);GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
                                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0);GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,0);cleanClientArrays();
                                    int checks=originalChecks;originalFailure=null;originalReference=true;
                                    try{mesh.getClass().getMethod("render",float.class).invoke(mesh,.0625f);}
                                    finally{originalReference=false;GL11.glPopClientAttrib();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);}
                                    require(originalFailure==null,originalFailure);if(meshes.size()==1&&projection==0&&!alphaTest)require(originalChecks>checks,"Original client-array draw guard did not execute");
                                }
                                else if(mode==1||mode>=5){
                                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);GL11.glVertexPointer(3,GL11.GL_FLOAT,20,0L);GL11.glTexCoordPointer(2,GL11.GL_FLOAT,20,12L);
                                    if(mode==1)GL11.glDrawArrays(GL11.GL_QUADS,0,4);else{
                                        IntBuffer order=BufferUtils.createIntBuffer(6);order.put(mode==5?new int[]{0,1,2,0,2,3}:new int[]{0,1,3,1,2,3}).flip();GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,indices);GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER,order,GL15.GL_STREAM_DRAW);GL11.glDrawElements(GL11.GL_TRIANGLES,6,GL11.GL_UNSIGNED_INT,0L);
                                    }
                                }else{
                                    GL20.glUseProgram(program);GL20.glUniform1i(path,projection==2?1:0);GL20.glUniform1i(useArray,mode==4?1:0);
                                    GL20.glEnableVertexAttribArray(0);GL20.glEnableVertexAttribArray(1);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,20,0L);GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,20,12L);
                                    if(mode==2)GL11.glDrawArrays(GL11.GL_QUADS,0,4);else GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS,0L,1,16);
                                }
                                progress("before-readback",stage);pixels.clear();GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] actual=new byte[pixels.remaining()];pixels.get(actual);progress("readback-complete",stage);
                                if(reference==null){reference=actual;referenceClear=clear;if(results.isEmpty()){save(reference,folder.resolve("first-original-reference.png"));save(referenceClear,folder.resolve("first-clear-reference.png"));require(!sample.collapsed()&&GpuVoxelSkin072InterpolationData.areaMeasure(sample)>0&&foreground(reference,referenceClear)>4,"First ordinary positive-area control did not produce a visible original image");}if(!sample.collapsed()&&foreground(reference,referenceClear)>4)ordinaryControls++;if(GpuVoxelSkin072InterpolationData.areaMeasure(sample)==0)require(foreground(reference,referenceClear)==0,"Zero-area original quad unexpectedly covered pixels");}Map<String,Object> difference=difference(reference,actual);difference.put("mode",MODES[mode]);difference.put("foregroundPixels",foreground(actual,clear));modes.add(difference);
                                if(mode!=0){comparisons++;boolean equal=(int)difference.get("pixelsBeyond2")==0;if(equal)identical++;if(foreground(reference,referenceClear)>4){nonblankComparisons++;if(equal)nonblankIdentical++;}}
                                if(mode>0&&(int)difference.get("pixelsBeyond2")>0&&saved<12){save(reference,folder.resolve("difference-"+saved+"-reference.png"));save(actual,folder.resolve("difference-"+saved+"-"+mode+".png"));saved++;}
                            }
                            Map<String,Object> result=new LinkedHashMap<>();result.put("sample",sample.name());result.put("quadHash",sample.quadHash());result.put("vertexHash",GpuVoxelSkin072InterpolationData.hash(converted));result.put("texture",texture);result.put("filter",filter);result.put("samplerState",samplerState);
                            result.put("projection",projection);result.put("alphaTest",alphaTest);result.put("collapsedCorner",sample.collapsed());result.put("areaMeasure",GpuVoxelSkin072InterpolationData.areaMeasure(sample));result.put("expectedCoverage",GpuVoxelSkin072InterpolationData.areaMeasure(sample)==0?"zero-area":sample.collapsed()?"collapsed-positive-area":"ordinary-positive-area");result.put("originalForegroundPixels",foreground(reference,referenceClear));result.put("nonblankReference",foreground(reference,referenceClear)>4);result.put("modelView",matrix(GL11.GL_MODELVIEW_MATRIX));result.put("projectionMatrix",matrix(GL11.GL_PROJECTION_MATRIX));result.put("modes",modes);results.add(result);
                            if(maximum>0&&results.size()>=maximum)break probeCases;
                        }
                    }
                }
            }
            report.put("cases",results);report.put("diagnosticCaseLimit",maximum);report.put("comparisons",comparisons);report.put("comparisonsWithin2",identical);report.put("nonblankComparisons",nonblankComparisons);report.put("nonblankComparisonsWithin2",nonblankIdentical);report.put("ordinaryPositiveControls",ordinaryControls);require(ordinaryControls>0,"No visible ordinary positive-area control ran");report.put("originalClientArrayChecks",originalChecks);report.put("originalPrerequisites",originalPrerequisites);report.put("glError",GL11.glGetError());require(((Number)report.get("glError")).intValue()==0,"GL error in interpolation probe");report.put("passed",true);progress("complete",Map.of("cases",results.size()));
        }catch(Throwable failure){report.put("passed",false);report.put("failure",failure.toString());failure.printStackTrace();}
        finally{
            for(Object mesh:meshes)try{mesh.getClass().getMethod("dispose").invoke(mesh);}catch(Exception failure){report.put("displayListDisposalFailure",failure.toString());report.put("passed",false);}
            GL20.glUseProgram(oldProgram);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();
            GL30.glBindVertexArray(oldVao);GL11.glPopClientAttrib();GL11.glPopAttrib();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,oldIndirect);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldPack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,oldUnpack);
            GL33.glBindSampler(0,sampler0);GL33.glBindSampler(1,sampler1);GL41.glBindProgramPipeline(oldPipeline);GL13.glActiveTexture(oldActive);GL13.glClientActiveTexture(oldClient);GL11.glMatrixMode(oldMode);
            if(vao!=0)GL30.glDeleteVertexArrays(vao);for(int buffer:new int[]{vbo,indices,commands})if(buffer!=0)GL15.glDeleteBuffers(buffer);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);for(int tex:new int[]{target,source,array})if(tex!=0)GL11.glDeleteTextures(tex);if(program!=0)GL20.glDeleteProgram(program);
            boolean restored=oldProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)&&oldVao==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&oldActive==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)&&oldClient==GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE)
                    &&oldDraw==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&oldRead==GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)&&oldMode==GL11.glGetInteger(GL11.GL_MATRIX_MODE)
                    &&oldArray==GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)&&oldIndirect==GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING)&&oldPack==GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)&&oldUnpack==GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)
                    &&sampler0==GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0)&&sampler1==GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,1)&&oldPipeline==GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING)
                    &&Arrays.equals(oldModel,matrix(GL11.GL_MODELVIEW_MATRIX))&&Arrays.equals(oldProjection,matrix(GL11.GL_PROJECTION_MATRIX));
            int error=GL11.glGetError();report.put("stateRestored",restored);report.put("finalGlError",error);if(!restored||error!=0)report.put("passed",false);
            report.put("correctnessOnly",true);report.put("notPerformanceEvidence",true);report.put("titleOnly",true);report.put("worldAbsent",mc.world==null);report.put("nativeShaderCopied",false);
            try{Path file=Path.of(output);Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}mc.stop();
        }
    }
    private static void configureView(GpuVoxelSkin072InterpolationData.Sample sample,int projection){
        float[] data=GpuVoxelSkin072InterpolationData.vertices(sample),min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int v=0;v<4;v++)for(int c=0;c<3;c++){min[c]=Math.min(min[c],data[v*5+c]);max[c]=Math.max(max[c],data[v*5+c]);}
        float extent=Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]));require(extent>0,"A selected quad has no area");
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();if(projection==0)GL11.glOrtho(-extent,extent,-extent,extent,.001,extent*20);else{double near=extent*.05,r=near*Math.tan(Math.toRadians(75)/2);GL11.glFrustum(-r,r,-r,r,near,extent*20);}
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslatef(0,0,-extent*3);GL11.glRotatef(13,1,0,0);GL11.glRotatef(17,0,1,0);
        float[] q=sample.quad();if(q[0]!=0)GL11.glRotatef(q[0]>0?-90:90,0,1,0);else if(q[1]!=0)GL11.glRotatef(q[1]>0?90:-90,1,0,0);else if(q[2]<0)GL11.glRotatef(180,0,1,0);
        GL11.glTranslatef(-(min[0]+max[0])*.5f,-(min[1]+max[1])*.5f,-(min[2]+max[2])*.5f);
    }
    private static Map<String,Object> difference(byte[] before,byte[] after){int changed=0,beyond=0,max=0;for(int pixel=0;pixel<before.length;pixel+=4){int delta=0;for(int channel=0;channel<4;channel++)delta=Math.max(delta,Math.abs((before[pixel+channel]&255)-(after[pixel+channel]&255)));if(delta!=0)changed++;if(delta>2)beyond++;max=Math.max(max,delta);}Map<String,Object> result=new LinkedHashMap<>();result.put("changedPixels",changed);result.put("pixelsBeyond2",beyond);result.put("maxChannelDifference",max);return result;}
    private static int foreground(byte[] pixels,byte[] clear){int count=0;for(int at=0;at<pixels.length;at+=4)if(pixels[at]!=clear[at]||pixels[at+1]!=clear[at+1]||pixels[at+2]!=clear[at+2]||pixels[at+3]!=clear[at+3])count++;return count;}
    private static void save(byte[] rgba,Path path)throws Exception{BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int at=((SIZE-y-1)*SIZE+x)*4;image.setRGB(x,y,(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|(rgba[at+2]&255));}ImageIO.write(image,"png",path.toFile());}
    private static BufferedImage gradient(){BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xFF000000|x*4<<16|y*4<<8|((x^y)&7)*36);return image;}
    private static ByteBuffer rgba(BufferedImage image){ByteBuffer data=BufferUtils.createByteBuffer(64*64*4);for(int y=0;y<64;y++)for(int x=0;x<64;x++){int c=image.getRGB(x,y);data.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}return data.flip();}
    private static void parameters(int target,int filter){GL11.glTexParameteri(target,GL11.GL_TEXTURE_MIN_FILTER,filter);GL11.glTexParameteri(target,GL11.GL_TEXTURE_MAG_FILTER,filter);GL11.glTexParameteri(target,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);GL11.glTexParameteri(target,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);}
    private static Map<String,Object> textureState(int target){return Map.of("minFilter",GL11.glGetTexParameteri(target,GL11.GL_TEXTURE_MIN_FILTER),"magFilter",GL11.glGetTexParameteri(target,GL11.GL_TEXTURE_MAG_FILTER),"wrapS",GL11.glGetTexParameteri(target,GL11.GL_TEXTURE_WRAP_S),"wrapT",GL11.glGetTexParameteri(target,GL11.GL_TEXTURE_WRAP_T),"format",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),"width",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_WIDTH),"height",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_HEIGHT));}
    private static void cleanClientArrays(){
        for(int flag:new int[]{GL11.GL_VERTEX_ARRAY,GL11.GL_NORMAL_ARRAY,GL11.GL_COLOR_ARRAY,GL11.GL_INDEX_ARRAY,GL11.GL_EDGE_FLAG_ARRAY,GL14.GL_SECONDARY_COLOR_ARRAY,GL14.GL_FOG_COORDINATE_ARRAY})GL11.glDisableClientState(flag);
        for(int unit=0;unit<GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);unit++){GL13.glClientActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);}
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);for(int attribute=0;attribute<GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);attribute++)GL20.glDisableVertexAttribArray(attribute);
    }
    public static boolean validateOriginal(BufferBuilder builder){
        if(!originalReference)return true;originalChecks++;
        Map<String,Object> state=new LinkedHashMap<>();int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),error=GL11.glGetError();
        ByteBuffer data=builder.getBuffer();long base=MemoryUtil.memAddress(data)-data.position();state.put("vao",vao);state.put("arrayBuffer",array);state.put("drawMode",builder.getDrawMode());state.put("vertices",builder.getVertexCount());state.put("stride",builder.getFormat().getVertexSize());state.put("glError",error);
        boolean valid=vao==0&&array==0&&builder.getDrawMode()==GL11.GL_QUADS&&builder.getVertexCount()==4&&error==0;
        for(int index=0;index<builder.getFormat().getElements().size();index++){
            String usage=builder.getFormat().getElements().get(index).getUsage().name();int pointer=0,enabled=0,binding=0;
            if(usage.equals("POSITION")){pointer=GL11.GL_VERTEX_ARRAY_POINTER;enabled=GL11.GL_VERTEX_ARRAY;binding=GL15.GL_VERTEX_ARRAY_BUFFER_BINDING;}
            else if(usage.equals("UV")){pointer=GL11.GL_TEXTURE_COORD_ARRAY_POINTER;enabled=GL11.GL_TEXTURE_COORD_ARRAY;binding=GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING;}
            else if(usage.equals("NORMAL")){pointer=GL11.GL_NORMAL_ARRAY_POINTER;enabled=GL11.GL_NORMAL_ARRAY;binding=GL15.GL_NORMAL_ARRAY_BUFFER_BINDING;}
            if(pointer!=0){PointerBuffer address=BufferUtils.createPointerBuffer(1);GL11.glGetPointerv(pointer,address);long actual=address.get(0),expected=base+builder.getFormat().getOffset(index);int buffer=GL11.glGetInteger(binding);boolean active=GL11.glGetBoolean(enabled);state.put(usage,Map.of("pointer",Long.toUnsignedString(actual),"expectedPointer",Long.toUnsignedString(expected),"buffer",buffer,"enabled",active));valid&=actual==expected&&buffer==0&&active;}
        }
        if(originalPrerequisites.size()<32)originalPrerequisites.add(state);progress("original-client-arrays-validated",state);
        if(!valid){originalFailure="Original client-memory reference prerequisites failed";builder.clear();}
        return valid;
    }
    private static void progress(String stage,Map<String,Object> detail){try{Map<String,Object> value=new LinkedHashMap<>(detail);value.put("stage",stage);value.put("nanoTime",System.nanoTime());Files.writeString(progressPath,new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception failure){throw new IllegalStateException("Cannot persist owned interpolation progress",failure);}}
    private static int program(){int v=shader(GL20.GL_VERTEX_SHADER,"#version 130\nin vec3 aPosition;in vec2 aUv;uniform int uPath;out vec2 uv;void main(){vec4 p=vec4(aPosition,1);gl_Position=uPath==0?gl_ModelViewProjectionMatrix*p:gl_ProjectionMatrix*(gl_ModelViewMatrix*p);uv=(gl_TextureMatrix[0]*vec4(aUv,0,1)).xy;}"),f=0,p=GL20.glCreateProgram();try{f=shader(GL20.GL_FRAGMENT_SHADER,"#version 130\nuniform sampler2D uTexture;uniform sampler2DArray uArrayTexture;uniform int uArray;in vec2 uv;void main(){gl_FragColor=uArray==0?texture(uTexture,uv):texture(uArrayTexture,vec3(uv,0));}");GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glBindAttribLocation(p,0,"aPosition");GL20.glBindAttribLocation(p,1,"aUv");GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}catch(Throwable failure){GL20.glDeleteProgram(p);throw failure;}finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}}
    private static int shader(int type,String source){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(log);}return shader;}
    private static float[] matrix(int key){FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloatv(key,data);float[] result=new float[16];data.get(result);return result;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
