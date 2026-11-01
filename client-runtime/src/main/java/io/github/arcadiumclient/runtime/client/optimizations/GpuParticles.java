// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.ParticleBufferAccess;
import java.lang.invoke.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.entity.particle.SpellParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuParticles {
    private static final int WORDS=16,MAX_PARTICLES=65536;
    private static final IdentityHashMap<BufferBuilder,Group> groups=new IdentityHashMap<>();
    private static BufferBuilder probe;
    private static int depth,observed,previousVisible,program,vao,instances;
    private static int lightmapUniform,fogUniform,radialUniform;
    private static long recipeEpoch;
    private static boolean checked,failed,scopeEnabled,colorChecked;
    private static MethodHandle config,color,chroma,fade;
    private static volatile String state="Waiting for particles";
    public static long particlesDrawn,cpuRecoveries,draws,recipeHits,recipesBuilt;
    private record Recipe(float size,float u0,float u1,float v0,float v1,int color,int light) { }
    private static final class Group {
        IntBuffer packets=BufferUtils.createIntBuffer(256*WORDS);
        int[] offsets=new int[256];
        int count;
        void ensure(){if(count<offsets.length)return;offsets=Arrays.copyOf(offsets,offsets.length*2);IntBuffer next=BufferUtils.createIntBuffer(offsets.length*WORDS);next.put(packets.duplicate().position(0).limit(count*WORDS));packets=next;}
    }
    private GpuParticles(){}
    public static void begin(boolean normal){
        if(normal){previousVisible=observed;observed=0;if(Minecraft.getInstance().screen!=null)recipeEpoch++;}
        depth++;
        scopeEnabled=BetaOptimizationsConfig.getGpuParticles()&&!failed&&previousVisible>=Math.max(1,BetaOptimizationsConfig.getMinimumGpuParticles());
        if(scopeEnabled&&!checked)initialize();scopeEnabled&=!failed&&program!=0;
    }
    public static void end(){if(--depth==0){scopeEnabled=false;for(Group group:groups.values())if(group.count!=0){failed=true;state="Native particle pass did not flush; using original";group.count=0;}}}
    public static boolean queue(Particle particle,BufferBuilder builder,float x,float y,float z,float size,float u0,float u1,float v0,float v1,
                                float red,float green,float blue,float alpha,float dx,float dy,float dz,float forward,float side,int light){
        if(!BetaOptimizationsConfig.getGpuParticles()||depth==0)return false;
        observed++;
        if(!scopeEnabled||!particle.getClass().getName().startsWith("net.minecraft.")||!DefaultVertexFormat.PARTICLE.equals(builder.getFormat())||GpuPlayerPipeline.hasForgeHooks())return false;
        ParticleBufferAccess format=(ParticleBufferAccess)builder;
        if(builder.getDrawMode()!=GL11.GL_QUADS||format.arcadium$uncolored()||format.arcadium$offsetX()!=0||format.arcadium$offsetY()!=0||format.arcadium$offsetZ()!=0)return false;
        try{
            Object style=particleStyle(particle);
            if(animatedColor(style))return false;
            Group group=groups.get(builder);if(group==null){group=new Group();groups.put(builder,group);}
            if(group.count>=MAX_PARTICLES)return false;
            int packed=packedColor(red,green,blue,alpha);group.ensure();int offset=group.count*WORDS;
            if(BetaOptimizationsConfig.getRetainedParticleRecipes()&&(particle.getClass()==Particle.class||particle.getClass()==SpellParticle.class)&&!fading(style)){
                ((ParticleRecipeAccess)particle).arcadium$recipe(new Recipe(size,u0,u1,v0,v1,packed,light),recipeEpoch);recipesBuilt++;
            }
            var data=group.packets;
            put(data,offset,x);put(data,offset+1,y);put(data,offset+2,z);put(data,offset+3,size);
            put(data,offset+4,u0);put(data,offset+5,u1);put(data,offset+6,v0);put(data,offset+7,v1);
            data.put(offset+8,packed).put(offset+9,light);put(data,offset+10,dx);put(data,offset+11,dy);put(data,offset+12,dz);put(data,offset+13,forward);put(data,offset+14,side);data.put(offset+15,0);
            group.offsets[group.count++]=builder.getVertexCount();
            return true;
        }catch(Throwable error){disable(error);return false;}
    }
    public static void tick(){recipeEpoch++;}
    public static boolean tryRecipe(Particle particle,BufferBuilder builder,float delta,float dx,float dy,float dz,float forward,float side){
        if(!BetaOptimizationsConfig.getGpuParticles()||!BetaOptimizationsConfig.getRetainedParticleRecipes()||!scopeEnabled||depth==0||Minecraft.getInstance().screen!=null)return false;
        ParticleRecipeAccess access=(ParticleRecipeAccess)particle;
        if(access.arcadium$recipeEpoch()!=recipeEpoch||!(access.arcadium$recipe() instanceof Recipe recipe))return false;
        try{Object style=particleStyle(particle);if(animatedColor(style)||fading(style)){access.arcadium$recipe(null,-1);return false;}}
        catch(Throwable error){disable(error);return false;}
        if(!DefaultVertexFormat.PARTICLE.equals(builder.getFormat())||builder.getDrawMode()!=GL11.GL_QUADS||GpuPlayerPipeline.hasForgeHooks())return false;
        ParticleBufferAccess format=(ParticleBufferAccess)builder;
        if(format.arcadium$uncolored()||format.arcadium$offsetX()!=0||format.arcadium$offsetY()!=0||format.arcadium$offsetZ()!=0)return false;
        Group group=groups.get(builder);if(group==null){group=new Group();groups.put(builder,group);}if(group.count>=MAX_PARTICLES)return false;
        group.ensure();int p=group.count*WORDS;IntBuffer data=group.packets;
        put(data,p,(float)(particle.lastX+(particle.x-particle.lastX)*(double)delta-Particle.cameraX));
        put(data,p+1,(float)(particle.lastY+(particle.y-particle.lastY)*(double)delta-Particle.cameraY));
        put(data,p+2,(float)(particle.lastZ+(particle.z-particle.lastZ)*(double)delta-Particle.cameraZ));put(data,p+3,recipe.size);
        put(data,p+4,recipe.u0);put(data,p+5,recipe.u1);put(data,p+6,recipe.v0);put(data,p+7,recipe.v1);data.put(p+8,recipe.color).put(p+9,recipe.light);
        put(data,p+10,dx);put(data,p+11,dy);put(data,p+12,dz);put(data,p+13,forward);put(data,p+14,side);data.put(p+15,0);
        group.offsets[group.count++]=builder.getVertexCount();observed++;recipeHits++;return true;
    }
    private static void put(IntBuffer data,int offset,float value){data.put(offset,Float.floatToRawIntBits(value));}
    private static int packedColor(float r,float g,float b,float a){
        if(probe==null)probe=new BufferBuilder(32);
        probe.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
        try{probe.vertex(0,0,0).texture(0d,0d).color(r,g,b,a);return probe.getBuffer().order(ByteOrder.nativeOrder()).getInt(20);}
        finally{probe.end();probe.clear();}
    }
    private static Object particleStyle(Particle particle)throws Throwable{
        if(!colorChecked){
            colorChecked=true;
            try{
                Class<?> type=Class.forName("org.polyfrost.overflowparticles.client.config.PerParticleConfigManager");
                config=MethodHandles.lookup().unreflect(type.getMethod("getConfig",Particle.class));
                var returned=type.getMethod("getConfig",Particle.class).getReturnType();var getter=returned.getMethod("getColor");
                color=MethodHandles.lookup().unreflect(getter);chroma=MethodHandles.lookup().unreflect(getter.getReturnType().getMethod("getChroma"));
                fade=MethodHandles.lookup().unreflect(returned.getMethod("getFade"));
            }catch(ClassNotFoundException absent){config=null;}
        }
        return config==null?null:config.invoke(particle);
    }
    private static boolean animatedColor(Object style)throws Throwable{return style!=null&&(boolean)chroma.invoke(color.invoke(style));}
    private static boolean fading(Object style)throws Throwable{return style!=null&&(boolean)fade.invoke(style);}
    public static boolean draw(BufferBuilder builder){
        Group group=groups.get(builder);if(group==null||group.count==0)return false;
        if(failed||builder.getVertexCount()!=0||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0){replay(builder,group);return false;}
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{
            GL30.glBindVertexArray(vao);GL20.glUseProgram(program);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instances);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,group.packets.duplicate().position(0).limit(group.count*WORDS),GL15.GL_STREAM_DRAW);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);boolean lightmap=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);GL13.glActiveTexture(unit);
            GL20.glUniform1i(lightmapUniform,lightmap?1:0);
            GL20.glUniform1i(fogUniform,GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetInteger(GL11.GL_FOG_MODE):0);
            int distance=GL.getCapabilities().GL_NV_fog_distance?GL11.glGetInteger(NVFogDistance.GL_FOG_DISTANCE_MODE_NV):0;
            GL20.glUniform1i(radialUniform,distance==NVFogDistance.GL_EYE_RADIAL_NV?1:distance==GL11.GL_EYE_PLANE?2:0);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES,0,6,group.count);particlesDrawn+=group.count;draws++;group.count=0;builder.clear();state="GPU billboards active";return true;
        }catch(Throwable error){disable(error);replay(builder,group);return false;}
        finally{GL20.glUseProgram(oldProgram);GL30.glBindVertexArray(oldVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);GL13.glActiveTexture(unit);}
    }
    private static void replay(BufferBuilder builder,Group group){
        int[] source=new int[builder.getVertexCount()*7];builder.getBuffer().duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().get(source);
        int[] output=new int[source.length+group.count*28],packet=new int[WORDS];int from=0,to=0;
        for(int i=0;i<group.count;i++){
            int end=group.offsets[i]*7,length=end-from;System.arraycopy(source,from,output,to,length);from=end;to+=length;
            for(int j=0;j<WORDS;j++)packet[j]=group.packets.get(i*WORDS+j);
            int[] quad=ParticlePacketMath.quads(packet,0);System.arraycopy(quad,0,output,to,quad.length);to+=quad.length;
        }
        System.arraycopy(source,from,output,to,source.length-from);builder.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);builder.vertices(output);builder.end();cpuRecoveries+=group.count;group.count=0;
    }
    private static void initialize(){
        checked=true;
        if(!GL.getCapabilities().OpenGL33){failed=true;state="OpenGL 3.3 required; using original";return;}
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try{
            program=GL20.glCreateProgram();int vertex=shader(GL20.GL_VERTEX_SHADER,"particle_packets.vert"),fragment=shader(GL20.GL_FRAGMENT_SHADER,"name_packets.frag");
            try{GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));}
            finally{GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);}
            vao=GL30.glGenVertexArrays();instances=GL15.glGenBuffers();GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instances);
            attribute(0,4,0);attribute(1,4,16);GL20.glEnableVertexAttribArray(2);GL30.glVertexAttribIPointer(2,2,GL11.GL_UNSIGNED_INT,64,32L);GL33.glVertexAttribDivisor(2,1);attribute(3,4,40);attribute(4,1,56);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uDiffuse"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uLight"),1);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uTextured"),1);
            lightmapUniform=GL20.glGetUniformLocation(program,"uLightmap");fogUniform=GL20.glGetUniformLocation(program,"uFog");radialUniform=GL20.glGetUniformLocation(program,"uRadialFog");state="GPU billboards available";
        }catch(Throwable error){disable(error);}
        finally{GL20.glUseProgram(oldProgram);GL30.glBindVertexArray(oldVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);}
    }
    private static void attribute(int index,int size,long offset){GL20.glEnableVertexAttribArray(index);GL20.glVertexAttribPointer(index,size,GL11.GL_FLOAT,false,64,offset);GL33.glVertexAttribDivisor(index,1);}
    private static int shader(int type,String file)throws Exception{
        int value=GL20.glCreateShader(type);
        try(var source=GpuParticles.class.getResourceAsStream("/assets/arcadium/shaders/"+file)){
            if(source==null)throw new IllegalStateException("Missing particle shader");String code=new String(source.readAllBytes(),StandardCharsets.UTF_8).replace("#version 430 compatibility","#version 330 compatibility");
            GL20.glShaderSource(value,code);GL20.glCompileShader(value);if(GL20.glGetShaderi(value,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(value));return value;
        }catch(Exception failure){GL20.glDeleteShader(value);throw failure;}
    }
    public static void disable(Throwable error){failed=true;scopeEnabled=false;state="Disabled after error; using original particles";LogManager.getLogger("Arcadium/GPU Particles").warn("GPU particles disabled",error);}
    public static void close(){recipeEpoch++;for(Group g:groups.values())g.count=0;groups.clear();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(instances!=0)GL15.glDeleteBuffers(instances);program=vao=instances=0;checked=failed=false;depth=observed=previousVisible=0;}
    public static String status(){return BetaOptimizationsConfig.getGpuParticles()?"GPU particles: "+state+" | Visible: "+previousVisible+" | GPU: "+particlesDrawn+" | Recipes: "+recipeHits+" | Native: "+cpuRecoveries:"GPU particles: Off";}
}
