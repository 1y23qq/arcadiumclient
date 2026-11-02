// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuNameTags {
    private static final Set<GpuNameTags> LIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int INSTANCE_FLOATS=16;
    private final TagGeometryCache geometry=new TagGeometryCache();
    private final IdentityHashMap<BufferBuilder,PassBinding> groups=new IdentityHashMap<>();
    private IntBuffer upload=BufferUtils.createIntBuffer(65536);
    private int observed,previousVisible,program,vao,vertexBuffer,instanceBuffer,commandsBuffer,uploadedWords,gpuCapacity;
    private int texturedUniform,lightmapUniform,fogUniform,radialUniform;
    private boolean checked,failed,invalidate;
    private long generation,retainedEpoch;
    private boolean retainedTransaction;
    private static volatile String capability="Not tested yet";
    public static long packetsDrawn,verticesReused,geometryUploads,multiDraws,cpuRecoveries,visibleLabels,commandUploads,commandsReused;

    public static final class PassBinding {
        private final BufferBuilder buffer;
        TagGeometryCache.Mesh[] meshes=new TagGeometryCache.Mesh[128];
        FloatBuffer instances=BufferUtils.createFloatBuffer(128*INSTANCE_FLOATS);
        IntBuffer commands=BufferUtils.createIntBuffer(128*4);
        int count,saved,commandBuffer,uploadedCommands;
        long savedEpoch;
        boolean commandsDirty=true;
        PassBinding(BufferBuilder buffer){this.buffer=buffer;}
        public BufferBuilder buffer(){return buffer;}
        void ensure(){
            if(count<meshes.length)return;
            meshes=Arrays.copyOf(meshes,meshes.length*2);
            FloatBuffer next=BufferUtils.createFloatBuffer(meshes.length*INSTANCE_FLOATS);
            next.put(instances.duplicate().position(0).limit(count*INSTANCE_FLOATS));instances=next;
            IntBuffer nextCommands=BufferUtils.createIntBuffer(meshes.length*4);
            nextCommands.put(commands.duplicate().clear());commands=nextCommands;commandsDirty=true;
        }
        void clear(){Arrays.fill(meshes,0,count,null);count=0;}
    }

    public void beginFrame(){
        previousVisible=observed;visibleLabels=observed;observed=0;discard();
        if(invalidate||geometry.size()>TagGeometryCache.MAX_WORDS*3/4){geometry.clear();uploadedWords=0;invalidate=false;generation++;}
    }
    public void noteLabel(){observed++;}
    public boolean enabled(){
        if(!BetaOptimizationsConfig.getGpuNameTags()||previousVisible<Math.max(1,BetaOptimizationsConfig.getMinimumGpuLabels())||failed)return false;
        if(!checked)initialize();return !failed&&program!=0;
    }

    public boolean queue(BufferBuilder pass,Object nativePass,int[] source,int length,int stride,int color,boolean text,int alpha,
                         Matrix4fc matrix,float x,float y,float lightU,float lightV){
        if(!enabled())return false;
        try{
            var mesh=geometry.mesh(source,length,stride,color,text,text);
            if(mesh.vertexCount()==0)return true;
            RetainedNameTags.packet(nativePass,mesh,matrix,x,y,text?alpha:-1,lightU,lightV);
            append(pass,mesh,text?alpha:-1,matrix,x,y,lightU,lightV);
            return true;
        }catch(Throwable error){
            fail(error);replayPending();return false;
        }
    }
    public long generation(){return generation;}
    public void checkpointRetained(){
        if(RetainedNameTags.compact){retainedEpoch++;retainedTransaction=true;}
        else for(PassBinding group:groups.values())group.saved=group.count;
    }
    public void commitRetained(){retainedTransaction=false;}
    public void rollbackRetained(){
        for(PassBinding group:groups.values())if(!RetainedNameTags.compact||group.savedEpoch==retainedEpoch){Arrays.fill(group.meshes,group.saved,group.count,null);group.count=group.saved;}
        retainedTransaction=false;
    }
    public void queueRetained(BufferBuilder pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float lightU,float lightV){
        append(pass,mesh,alpha,matrix,0,0,lightU,lightV);
    }
    public PassBinding bindRetained(BufferBuilder pass){
        PassBinding group=groups.get(pass);
        if(group==null){group=new PassBinding(pass);groups.put(pass,group);}
        return group;
    }
    public void queueBoundRetained(PassBinding pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float lightU,float lightV){
        append(pass,mesh,alpha,matrix,0,0,lightU,lightV);
    }
    private void append(BufferBuilder pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float x,float y,float lightU,float lightV){
            append(bindRetained(pass),mesh,alpha,matrix,x,y,lightU,lightV);
    }
    private void append(PassBinding group,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float x,float y,float lightU,float lightV){
            if(retainedTransaction&&group.savedEpoch!=retainedEpoch){group.savedEpoch=retainedEpoch;group.saved=group.count;}
            group.ensure();int i=group.count++*INSTANCE_FLOATS;group.meshes[group.count-1]=mesh;
            FloatBuffer out=group.instances;
            out.put(i,matrix.m00()).put(i+1,matrix.m01()).put(i+2,matrix.m02());
            out.put(i+3,matrix.m10()).put(i+4,matrix.m11()).put(i+5,matrix.m12());
            out.put(i+6,matrix.m20()).put(i+7,matrix.m21()).put(i+8,matrix.m22());
            out.put(i+9,matrix.m30()+matrix.m00()*x+matrix.m10()*y);
            out.put(i+10,matrix.m31()+matrix.m01()*x+matrix.m11()*y);
            out.put(i+11,matrix.m32()+matrix.m02()*x+matrix.m12()*y);
            out.put(i+12,alpha).put(i+13,lightU).put(i+14,lightV).put(i+15,0);
    }

    public boolean draw(BufferBuilder builder){
        PassBinding group=groups.get(builder);
        if(group==null||group.count==0)return false;
        if(failed){replayEnded(builder,group);return false;}
        if(builder.getVertexCount()!=0){
            fail(new IllegalStateException("Uncaptured name tag vertices shared a GPU pass"));
            replayEnded(builder,group);return false;
        }
        int previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousIndirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int textureUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{
            if(previousProgram!=0)throw new IllegalStateException("A custom shader is active for name tags");
            GL30.glBindVertexArray(vao);GL20.glUseProgram(program);
            uploadGeometry();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,group.instances.duplicate().position(0).limit(group.count*INSTANCE_FLOATS),GL15.GL_STREAM_DRAW);
            if(RetainedNameTags.compact){
                group.commands.clear();
                if(group.commandBuffer==0){group.commandBuffer=GL15.glGenBuffers();group.commandsDirty=true;}
                for(int i=0;i<group.count;i++){
                    int offset=i*4;TagGeometryCache.Mesh mesh=group.meshes[i];
                    if(group.commands.get(offset)!=mesh.vertexCount()||group.commands.get(offset+2)!=mesh.firstVertex()){
                        group.commands.put(offset,mesh.vertexCount()).put(offset+1,1).put(offset+2,mesh.firstVertex()).put(offset+3,i);group.commandsDirty=true;
                    }
                }
                GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,group.commandBuffer);
                if(group.commandsDirty||group.uploadedCommands!=group.count){
                    GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,group.commands.duplicate().position(0).limit(group.count*4),GL15.GL_STATIC_DRAW);
                    group.commandsDirty=false;group.uploadedCommands=group.count;commandUploads++;
                }else commandsReused++;
            }else{
                group.commands.clear();
                for(int i=0;i<group.count;i++)group.commands.put(group.meshes[i].vertexCount()).put(1).put(group.meshes[i].firstVertex()).put(i);
                group.commands.flip();GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,commandsBuffer);
                GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER,group.commands,GL15.GL_STREAM_DRAW);
                group.commandsDirty=true;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);boolean textured=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);boolean lightmap=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(textureUnit);
            GL20.glUniform1i(texturedUniform,textured?1:0);GL20.glUniform1i(lightmapUniform,lightmap?1:0);
            GL20.glUniform1i(fogUniform,GL11.glIsEnabled(GL11.GL_FOG)?GL11.glGetInteger(GL11.GL_FOG_MODE):0);
            int fogDistance=GL.getCapabilities().GL_NV_fog_distance?GL11.glGetInteger(NVFogDistance.GL_FOG_DISTANCE_MODE_NV):0;
            GL20.glUniform1i(radialUniform,fogDistance==NVFogDistance.GL_EYE_RADIAL_NV?1:fogDistance==GL11.GL_EYE_PLANE?2:0);
            GL43.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES,0L,group.count,16);
            packetsDrawn+=group.count;multiDraws++;
            for(int i=0;i<group.count;i++)verticesReused+=group.meshes[i].vertexCount();
            group.clear();builder.clear();return true;
        }catch(Throwable error){fail(error);replayEnded(builder,group);return false;}
        finally{
            GL20.glUseProgram(previousProgram);GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previousArray);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,previousIndirect);
            GL13.glActiveTexture(textureUnit);
        }
    }

    private void uploadGeometry(){
        int size=geometry.size();if(size==uploadedWords)return;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vertexBuffer);
        if(size>gpuCapacity){
            gpuCapacity=Math.min(TagGeometryCache.MAX_WORDS,Math.max(size,Math.max(65536,gpuCapacity*2)));
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,(long)gpuCapacity*4,GL15.GL_STATIC_DRAW);uploadedWords=0;
        }
        int length=size-uploadedWords;
        if(upload.capacity()<length)upload=BufferUtils.createIntBuffer(Integer.highestOneBit(length-1)<<1);
        upload.clear();upload.put(geometry.words(),uploadedWords,length).flip();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,(long)uploadedWords*4,upload);uploadedWords=size;geometryUploads++;
    }

    private void initialize(){
        checked=true;
        if(!GL.getCapabilities().OpenGL43){failed=true;capability="OpenGL 4.3 required; using Argentum";return;}
        int previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),previousArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        LIVE.add(this);
        try{
            program=GL20.glCreateProgram();int vertex=shader(GL20.GL_VERTEX_SHADER,"name_packets.vert"),fragment=shader(GL20.GL_FRAGMENT_SHADER,"name_packets.frag");
            try{
                GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            }finally{GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);}
            vao=GL30.glGenVertexArrays();vertexBuffer=GL15.glGenBuffers();instanceBuffer=GL15.glGenBuffers();commandsBuffer=GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vertexBuffer);
            attribute(0,3,GL11.GL_FLOAT,false,24,0,0);attribute(1,2,GL11.GL_FLOAT,false,24,12,0);attribute(2,4,GL11.GL_UNSIGNED_BYTE,true,24,20,0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,instanceBuffer);
            for(int c=0;c<4;c++)attribute(3+c,3,GL11.GL_FLOAT,false,64,c*12L,1);
            attribute(7,4,GL11.GL_FLOAT,false,64,48,1);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uDiffuse"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uLight"),1);
            texturedUniform=GL20.glGetUniformLocation(program,"uTextured");lightmapUniform=GL20.glGetUniformLocation(program,"uLightmap");fogUniform=GL20.glGetUniformLocation(program,"uFog");
            radialUniform=GL20.glGetUniformLocation(program,"uRadialFog");
            capability="Multi-draw available";
        }catch(Throwable error){fail(error);}
        finally{GL20.glUseProgram(previousProgram);GL30.glBindVertexArray(previousVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previousArray);}
    }
    private static void attribute(int index,int size,int type,boolean normalized,int stride,long offset,int divisor){
        GL20.glEnableVertexAttribArray(index);GL20.glVertexAttribPointer(index,size,type,normalized,stride,offset);GL33.glVertexAttribDivisor(index,divisor);
    }
    private static int shader(int type,String file)throws Exception{
        int shader=GL20.glCreateShader(type);
        try(var stream=GpuNameTags.class.getResourceAsStream("/assets/arcadium/shaders/"+file)){
            if(stream==null)throw new IllegalStateException("Missing name tag shader");
            GL20.glShaderSource(shader,new String(stream.readAllBytes(),StandardCharsets.UTF_8));GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));return shader;
        }catch(Throwable error){GL20.glDeleteShader(shader);throw error;}
    }

    private void replayPending(){for(var entry:groups.entrySet())if(entry.getValue().count>0)replay(entry.getKey(),entry.getValue());}
    private void replayEnded(BufferBuilder builder,PassBinding group){
        int[] old=null;
        if(builder.getVertexCount()>0){var source=builder.getBuffer().duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();old=new int[source.remaining()];source.get(old);}
        builder.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);replay(builder,group);if(old!=null)builder.vertices(old);builder.end();
    }
    private void replay(BufferBuilder builder,PassBinding group){
        for(int i=0;i<group.count;i++){
            int[] source=group.meshes[i].quads(),out=new int[source.length/6*7];int instance=i*INSTANCE_FLOATS;
            FloatBuffer matrix=group.instances;int alpha=(int)matrix.get(instance+12);
            int light=((int)matrix.get(instance+13)&0xffff)|(((int)matrix.get(instance+14)&0xffff)<<16);
            for(int v=0;v<source.length/6;v++){
                int from=v*6,to=v*7;float x=Float.intBitsToFloat(source[from]),y=Float.intBitsToFloat(source[from+1]),z=Float.intBitsToFloat(source[from+2]);
                for(int row=0;row<3;row++)out[to+row]=Float.floatToRawIntBits(matrix.get(instance+row)*x+matrix.get(instance+3+row)*y+matrix.get(instance+6+row)*z+matrix.get(instance+9+row));
                out[to+3]=source[from+3];out[to+4]=source[from+4];out[to+5]=alpha<0?source[from+5]:(source[from+5]&0xffffff)|(alpha<<24);out[to+6]=light;
            }
            builder.vertices(out);cpuRecoveries++;
        }
        group.clear();
    }
    private void fail(Throwable error){failed=true;capability="Disabled after error; using Argentum";LogManager.getLogger("Arcadium/GPU Name Tags").warn("GPU name tags disabled",error);}
    public void discard(){retainedTransaction=false;for(var group:groups.values())group.clear();}
    public void invalidate(){invalidate=true;}
    public void close(){
        discard();if(program!=0)GL20.glDeleteProgram(program);if(vao!=0)GL30.glDeleteVertexArrays(vao);
        if(vertexBuffer!=0)GL15.glDeleteBuffers(vertexBuffer);if(instanceBuffer!=0)GL15.glDeleteBuffers(instanceBuffer);if(commandsBuffer!=0)GL15.glDeleteBuffers(commandsBuffer);
        for(PassBinding group:groups.values())if(group.commandBuffer!=0)GL15.glDeleteBuffers(group.commandBuffer);
        program=vao=vertexBuffer=instanceBuffer=commandsBuffer=uploadedWords=gpuCapacity=0;geometry.clear();generation++;groups.clear();checked=failed=false;LIVE.remove(this);
    }
    public static void invalidateAll(){for(var value:LIVE)value.invalidate();RetainedNameTags.invalidate();}
    public static void closeAll(){RetainedNameTags.invalidate();for(var value:List.copyOf(LIVE))value.close();}
    public static String status(){return BetaOptimizationsConfig.getGpuNameTags()?"GPU name tags: "+capability+" | Labels: "+visibleLabels+" | Packets: "+packetsDrawn:"GPU name tags: Off";}
}
