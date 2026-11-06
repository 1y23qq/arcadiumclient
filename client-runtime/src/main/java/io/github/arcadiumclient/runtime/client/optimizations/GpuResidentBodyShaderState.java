// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuResidentBodyShaderState {
    private static final int[] programs=new int[256],locations=new int[256];
    private static final boolean[] verified=new boolean[256];
    private static final Scope[] scopes=new Scope[16];
    private static int count,depth,dummyBones,dummyUv;
    public static long dummyBinds,ownedBinds,scopeRefusals;
    private static final class Scope {
        final int[] indexed=new int[2];final long[] starts=new long[2],sizes=new long[2];
        int program,location,mode,generic;
    }
    static {for(int i=0;i<scopes.length;i++)scopes[i]=new Scope();}
    private GpuResidentBodyShaderState() { }
    public static boolean supported() {
        return GL.getCapabilities().OpenGL43&&GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS)>=2
                &&GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)>=7;
    }
    public static void programCreated(int program) {
        if(!GpuResidentBodyShaders.enabled()||!GpuResidentBodyShaders.vertexPatched||!supported())return;
        int location=GL20.glGetUniformLocation(program,"uArcResidentBodies");if(location<0)return;
        int bone=GL43.glGetProgramResourceIndex(program,GL43.GL_SHADER_STORAGE_BLOCK,"ArcBodyBones");
        int uv=GL43.glGetProgramResourceIndex(program,GL43.GL_SHADER_STORAGE_BLOCK,"ArcBodyUv");
        if(bone<0||uv<0||property(program,bone,GL43.GL_BUFFER_BINDING)!=0||property(program,uv,GL43.GL_BUFFER_BINDING)!=1
                ||property(program,bone,GL43.GL_BUFFER_DATA_SIZE)<0||property(program,uv,GL43.GL_BUFFER_DATA_SIZE)<0
                ||property(program,bone,GL43.GL_BUFFER_DATA_SIZE)>64||property(program,uv,GL43.GL_BUFFER_DATA_SIZE)>64)
            throw new IllegalStateException("Unsupported resident player shader storage layout");
        int record=index(program);if(record<0){if(count==programs.length)throw new IllegalStateException("Resident program budget exceeded");record=count++;programs[record]=program;}
        locations[record]=location;verified[record]=false;
        int attached=GL20.glGetProgrami(program,GL20.GL_ATTACHED_SHADERS);IntBuffer shaders=BufferUtils.createIntBuffer(attached);
        GL20.glGetAttachedShaders(program,(IntBuffer)null,shaders);
        for(int i=0;i<attached;i++)if(GL20.glGetShaderi(shaders.get(i),GL20.GL_SHADER_TYPE)==GL20.GL_VERTEX_SHADER
                &&GL20.glGetShaderSource(shaders.get(i)).contains(GpuResidentBodyShaders.TAG))verified[record]=true;
    }
    private static int property(int program,int block,int property) {
        IntBuffer properties=BufferUtils.createIntBuffer(1).put(property).flip(),result=BufferUtils.createIntBuffer(1);
        GL43.glGetProgramResourceiv(program,GL43.GL_SHADER_STORAGE_BLOCK,block,properties,(IntBuffer)null,result);return result.get(0);
    }
    private static int index(int program){for(int i=0;i<count;i++)if(programs[i]==program)return i;return -1;}
    static boolean verifiedProgram(int program){int at=index(program);return at>=0&&verified[at];}
    public static void programDeleted(int program){int at=index(program);if(at>=0){--count;programs[at]=programs[count];locations[at]=locations[count];verified[at]=verified[count];}}
    public static int beginProgram(int program) {
        if(!GpuResidentBodyShaders.enabled())return 0;int record=index(program);if(record<0)return 0;
        int token=save(program,locations[record]);
        try {
            if(dummyBones==0)dummyBones=dummy();if(dummyUv==0)dummyUv=dummy();
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,dummyBones);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,dummyUv);
            GL41.glProgramUniform1i(program,locations[record],0);dummyBinds++;return token;
        } catch(RuntimeException|Error failure){end(token);throw failure;}
    }
    static int beginOwned(int bones,int uv,int actors,int profiles) {
        return beginOwned(bones,uv,actors,profiles,1);
    }
    static int beginOwned(int bones,int uv,int actors,int profiles,int mode) {
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),record=index(program);
        if(!GpuResidentBodyShaders.active()||record<0||!verified[record]||actors<1||actors>8192||profiles<1||profiles>256||(mode!=1&&mode!=2))
            throw new IllegalStateException("Resident body draw has no verified native program");
        int token=save(program,locations[record]);
        try {
            extent(bones,(long)actors*12*12*4);extent(uv,(long)profiles*36*4);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,bones);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,uv);
            GL41.glProgramUniform1i(program,locations[record],mode);ownedBinds++;return token;
        } catch(RuntimeException|Error failure){end(token);throw failure;}
    }
    private static int save(int program,int location) {
        if(depth==scopes.length||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program){scopeRefusals++;throw new IllegalStateException("Invalid resident shader scope");}
        Scope scope=scopes[depth++];scope.program=program;scope.location=location;scope.mode=GL20.glGetUniformi(program,location);scope.generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int i=0;i<2;i++){int buffer=scope.indexed[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);scope.starts[i]=buffer==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);scope.sizes[i]=buffer==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        return depth;
    }
    private static int dummy(){int buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,BufferUtils.createIntBuffer(16),GL15.GL_STATIC_DRAW);return buffer;}
    private static void extent(int buffer,long bytes){if(buffer<=0||!GL15.glIsBuffer(buffer))throw new IllegalStateException("Missing resident storage");GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)<bytes)throw new IllegalStateException("Resident storage is smaller than the current draw");}
    public static void end(int token) {
        if(token==0)return;if(token!=depth)throw new IllegalStateException("Unbalanced resident shader scope");
        Scope scope=scopes[--depth];
        for(int i=0;i<2;i++){if(scope.indexed[i]!=0&&scope.sizes[i]>0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,scope.indexed[i],scope.starts[i],scope.sizes[i]);else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,scope.indexed[i]);}
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,scope.generic);GL41.glProgramUniform1i(scope.program,scope.location,scope.mode);
    }
    static void close(){if(depth!=0)throw new IllegalStateException("Resident shader scopes are still active");if(dummyBones!=0)GL15.glDeleteBuffers(dummyBones);if(dummyUv!=0)GL15.glDeleteBuffers(dummyUv);dummyBones=dummyUv=0;}
}
