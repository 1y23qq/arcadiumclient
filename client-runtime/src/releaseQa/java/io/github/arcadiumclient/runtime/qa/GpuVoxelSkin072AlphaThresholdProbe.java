// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072AlphaThresholdProbe {
    private static boolean attempted;
    private static int ticks;
    private GpuVoxelSkin072AlphaThresholdProbe(){}
    public static void run(){
        if(attempted||!Boolean.getBoolean("arcadium.voxelQuality072.alphaThreshold")||System.getProperty("arcadium.alphaThreshold072.output")==null||++ticks<65)return;
        Minecraft mc=Minecraft.getInstance();if(mc.screen==null)return;
        if(mc.world!=null||mc.player!=null||!mc.getSession().getUsername().equals("ArcadiumTest")||!mc.getSession().getAccessToken().equals("0"))throw new IllegalStateException("Use the owned offline title fixture only");
        attempted=true;Map<String,Object> report=new LinkedHashMap<>();
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),oldPack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),oldUnpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int oldSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0),program=0,fbo=0,target=0,source=0,vao=0;
        int oldPipeline=GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING);
        IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);int[] oldViewport={viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3)};
        float[] oldModel=matrix(GL11.GL_MODELVIEW_MATRIX),oldProjection=matrix(GL11.GL_PROJECTION_MATRIX);
        report.put("sceneFramebuffer",framebuffer(oldDraw));report.put("clampFragmentColor",GL11.glGetInteger(ARBColorBufferFloat.GL_CLAMP_FRAGMENT_COLOR_ARB));
        report.put("clampVertexColor",GL11.glGetInteger(ARBColorBufferFloat.GL_CLAMP_VERTEX_COLOR_ARB));
        int renderTarget=mc.getRenderTarget().frameBufferId;
        if(renderTarget>=0){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,renderTarget);report.put("minecraftRenderTarget",framebuffer(renderTarget));GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);}
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("GL error before alpha precision probe: "+error);
            program=program();fbo=GL30.glGenFramebuffers();target=GL11.glGenTextures();source=GL11.glGenTextures();vao=GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);GL41.glBindProgramPipeline(0);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL33.glBindSampler(0,0);
            for(int key:new int[]{GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_PIXELS})GL11.glPixelStorei(key,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,0);GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES,0);
            int units=GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            for(int unit=0;unit<units;unit++){
                GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_1D);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL12.GL_TEXTURE_3D);GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
                for(int coordinate:new int[]{GL11.GL_TEXTURE_GEN_S,GL11.GL_TEXTURE_GEN_T,GL11.GL_TEXTURE_GEN_R,GL11.GL_TEXTURE_GEN_Q})GL11.glDisable(coordinate);
            }
            for(int plane=0;plane<GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES);plane++)GL11.glDisable(GL11.GL_CLIP_PLANE0+plane);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_COLOR_MATERIAL);GL11.glDisable(GL11.GL_FOG);
            for(int flag:new int[]{GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_SCISSOR_TEST,GL11.GL_CULL_FACE,GL11.GL_DITHER,GL13.GL_MULTISAMPLE,GL30.GL_FRAMEBUFFER_SRGB})GL11.glDisable(flag);
            GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glViewport(0,0,1,1);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1f);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);List<Map<String,Object>> cases=new ArrayList<>();
            int alphaUniform=GL20.glGetUniformLocation(program,"uAlpha"),coordinate=GL20.glGetUniformLocation(program,"uCoordinate"),sourceUniform=GL20.glGetUniformLocation(program,"uSource"),cutoff=GL20.glGetUniformLocation(program,"uCutoff");
            FloatBuffer data=BufferUtils.createFloatBuffer(8);ByteBuffer pixel=BufferUtils.createByteBuffer(4);
            List<Float> sweep=sweep();
            for(int format:new int[]{GL11.GL_RGB8,GL11.GL_RGBA8,GL30.GL_RGBA16F}){
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,target);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete alpha probe framebuffer");
                for(int input=0;input<4;input++){
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);int width=input==3?2:1;
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,input==1?GL30.GL_RGBA32F:GL11.GL_RGBA8,width,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,(FloatBuffer)null);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,input==3?GL11.GL_LINEAR:GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,input==3?GL11.GL_LINEAR:GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                    if(input==3){data.clear();data.put(new float[]{1,0,0,0,1,0,0,1}).flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,2,1,GL11.GL_RGBA,GL11.GL_FLOAT,data);}
                    List<Map<String,Object>> rows=new ArrayList<>(),differences=new ArrayList<>();
                    for(float alpha:sweep){
                        if(input==1||input==2){data.clear();data.put(new float[]{1,0,0,alpha}).flip();GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,data);}
                        float u=input==3?.25f+alpha*.5f:.5f;boolean[] passed=new boolean[3];
                        for(int mode=0;mode<3;mode++){
                            GL20.glUseProgram(0);GL11.glClearColor(0,0,1,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                            if(input==0){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(1,0,0,alpha);}else{GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glColor4f(1,1,1,1);}
                            if(mode!=0){GL20.glUseProgram(program);GL20.glUniform1f(alphaUniform,alpha);GL20.glUniform1f(coordinate,u);GL20.glUniform1i(sourceUniform,input==0?0:1);GL20.glUniform1i(cutoff,mode==2?1:0);}
                            quad(u);pixel.clear();GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);passed[mode]=(pixel.get(0)&255)>127;
                        }
                        Map<String,Object> row=Map.of("alpha",alpha,"ffp",passed[0],"glslPassThrough",passed[1],"glslPointOneCutoff",passed[2]);rows.add(row);
                        if(passed[0]!=passed[1]||passed[0]!=passed[2])differences.add(row);
                    }
                    cases.add(Map.of("targetFormat",format,"source",List.of("current-color","rgba32f-nearest","rgba8-nearest","rgba8-linear-gradient").get(input),"sampleCount",rows.size(),"differences",differences,"rows",rows));
                }
            }
            report.put("cases",cases);report.put("targetCaseCount",cases.size());report.put("alphaValues",sweep.size());report.put("controlledDraws",cases.size()*sweep.size()*3);
            int postError=GL11.glGetError();report.put("postProbeGlError",postError);if(postError!=0)throw new IllegalStateException("GL error during alpha precision probe: "+postError);report.put("passed",true);
        }catch(Throwable failure){report.put("passed",false);report.put("failure",failure.toString());failure.printStackTrace();}
        finally{
            GL20.glUseProgram(oldProgram);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();
            GL30.glBindVertexArray(oldVao);GL11.glPopClientAttrib();GL11.glPopAttrib();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldPack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,oldUnpack);
            GL33.glBindSampler(0,oldSampler);GL41.glBindProgramPipeline(oldPipeline);GL13.glActiveTexture(oldActive);GL11.glMatrixMode(oldMode);
            if(vao!=0)GL30.glDeleteVertexArrays(vao);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(target!=0)GL11.glDeleteTextures(target);if(source!=0)GL11.glDeleteTextures(source);if(program!=0)GL20.glDeleteProgram(program);
            boolean restored=oldProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)&&oldDraw==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)&&oldRead==GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    &&oldVao==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&oldActive==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)&&oldMode==GL11.glGetInteger(GL11.GL_MATRIX_MODE)
                    &&oldArray==GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)&&oldPack==GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)&&oldUnpack==GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)
                    &&oldSampler==GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0)&&oldPipeline==GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING)
                    &&Arrays.equals(oldModel,matrix(GL11.GL_MODELVIEW_MATRIX))&&Arrays.equals(oldProjection,matrix(GL11.GL_PROJECTION_MATRIX));
            viewport.clear();GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);restored &= Arrays.equals(oldViewport,new int[]{viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3)});
            int finalError=GL11.glGetError();report.put("stateRestored",restored);report.put("finalGlError",finalError);if(!restored||finalError!=0)report.put("passed",false);
            report.put("correctnessOnly",true);report.put("notPerformanceEvidence",true);report.put("originalShaderSourceCopied",false);report.put("titleOnly",true);report.put("worldAbsent",mc.world==null);
            try{Path output=Path.of(System.getProperty("arcadium.alphaThreshold072.output"));Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}
            mc.stop();
        }
    }
    private static List<Float> sweep(){TreeSet<Float> values=new TreeSet<>();for(int i=0;i<256;i++)values.add(i/255f);for(int i=-60;i<=80;i++)values.add(.1f+i*.0001f);values.add(Math.nextDown(.1f));values.add(.1f);values.add(Math.nextUp(.1f));return new ArrayList<>(values);}
    private static void quad(float u){GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(u,.5f);GL11.glVertex2f(-1,-1);GL11.glVertex2f(1,-1);GL11.glVertex2f(1,1);GL11.glVertex2f(-1,1);GL11.glEnd();}
    private static int program(){
        int vertex=shader(GL20.GL_VERTEX_SHADER,"#version 130\nvoid main(){gl_Position=gl_Vertex;}"),fragment=0,candidate=GL20.glCreateProgram();
        try{
            fragment=shader(GL20.GL_FRAGMENT_SHADER,"#version 130\nuniform float uAlpha;uniform float uCoordinate;uniform int uSource;uniform int uCutoff;uniform sampler2D uTexture;void main(){float a=uSource==0?uAlpha:texture(uTexture,vec2(uCoordinate,.5)).a;gl_FragColor=uCutoff!=0&&a<=.1?vec4(0):vec4(1,0,0,a);}");
            GL20.glAttachShader(candidate,vertex);GL20.glAttachShader(candidate,fragment);GL20.glLinkProgram(candidate);if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));return candidate;
        }catch(Throwable failure){GL20.glDeleteProgram(candidate);throw failure;}finally{GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);}
    }
    private static int shader(int type,String source){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(log);}return shader;}
    private static float[] matrix(int key){FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloatv(key,data);float[] result=new float[16];data.get(result);return result;}
    private static Map<String,Object> framebuffer(int id){
        Map<String,Object> values=new LinkedHashMap<>();values.put("drawFramebuffer",id);values.put("alphaBits",GL11.glGetInteger(GL11.GL_ALPHA_BITS));values.put("redBits",GL11.glGetInteger(GL11.GL_RED_BITS));
        if(id!=0)for(var entry:Map.of("componentType",GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE,"colorEncoding",GL30.GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING,"alphaSize",GL30.GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE).entrySet())
            values.put(entry.getKey(),GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,entry.getValue()));return values;
    }
}
