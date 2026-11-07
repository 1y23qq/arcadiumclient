// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.util.Map;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class GpuVoxelSkinProjectionStore {
    private final Object owner;
    private final GpuVoxelSkinProjectionRows rows=new GpuVoxelSkinProjectionRows(GpuVoxelSkinPhysicalProjection.ROW_LIMIT,new GpuVoxelSkinProjectionQueries());
    private final FloatBuffer upload=BufferUtils.createFloatBuffer(GpuVoxelSkinPhysicalProjection.ROW_LIMIT*16);
    private final int[] programs=new int[4],modes=new int[4],counts=new int[4],fallbackModes=new int[4],fallbackMatrices=new int[4];
    private int boundProgramIndex=-1;
    private int programCount;
    private GlMutableBuffer buffer;
    private long epoch,frame;
    private boolean ready,checked,admitted;
    private int previousGeneric,previousBuffer;
    private long previousStart,previousSize;
    private boolean bound;
    GpuVoxelSkinProjectionStore(Object owner){this.owner=owner;}
    boolean prepare(CommandList device,Map<?,?> nativePrograms,long epoch,long frame) {
        ready=false;this.epoch=epoch;this.frame=frame;
        if(checked){
            if(!admitted||nativePrograms.size()!=programCount)return false;
            for(Object value:nativePrograms.values()){
                if(!(value instanceof GlProgram<?> nativeProgram))return false;
                boolean known=false;for(int i=0;i<programCount;i++)known|=programs[i]==nativeProgram.handle();if(!known)return false;
            }
            rows.beginPass(owner,epoch,frame,epoch,true,true,false,false);return ready=true;
        }
        checked=true;programCount=0;GpuVoxelSkinPhysicalProjection.fallbackReason="Physical MVP context or program is not qualified";
        if(!GpuVoxelSkinPhysicalProjection.contextSupported(GL.getCapabilities().OpenGL43,GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS),
                GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS),GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS),
                GL11.glGetString(GL11.GL_RENDERER),GL11.glGetString(GL11.GL_VERSION))||nativePrograms.isEmpty()||nativePrograms.size()>programs.length)return false;
        if(!GL.getCapabilities().GL_ARB_compatibility)return false;
        for(Object value:nativePrograms.values()) {
            if(!(value instanceof GlProgram<?> nativeProgram))return false;
            int id=nativeProgram.handle(),mode=GL20.glGetUniformLocation(id,"uArcVoxelProjectionMode"),count=GL20.glGetUniformLocation(id,"uArcVoxelProjectionCount");
            int fallback=GL20.glGetUniformLocation(id,"uArcVoxelProjectionFallback"),matrix=GL20.glGetUniformLocation(id,"uArcVoxelFallbackMvp");
            if(mode<0||count<0||fallback<0||matrix<0||GL20.glGetAttribLocation(id,"aArcVoxelProjectionRow")!=14
                    ||GL43.glGetProgramResourceIndex(id,GL43.GL_SHADER_STORAGE_BLOCK,"ArcVoxelPhysicalRows")==GL43.GL_INVALID_INDEX)return false;
            programs[programCount]=id;modes[programCount]=mode;counts[programCount]=count;fallbackModes[programCount]=fallback;fallbackMatrices[programCount++]=matrix;
        }
        if(buffer==null){buffer=device.createMutableBuffer();device.allocateStorage(buffer,(long)GpuVoxelSkinPhysicalProjection.ROW_LIMIT*64,GlBufferUsage.STREAM_DRAW);}
        rows.beginPass(owner,epoch,frame,epoch,true,true,false,false);admitted=ready=true;return true;
    }
    boolean ready(){return ready;}
    long capture(Object mesh,int part,float scale){return ready?rows.captureAtCompiledList(owner,epoch,frame,mesh,part,scale):0;}
    boolean reserve(long ticket,Object instances,int oldCount){return ready&&rows.reserveBeforeNativeSubmit(ticket,instances,oldCount);}
    boolean commit(long ticket,int newCount){boolean result=rows.commitAfterNativeAppend(ticket,newCount);if(result)GpuVoxelSkinPhysicalProjection.capturedRows++;return result;}
    void cancel(long ticket){rows.cancel(ticket);}
    void beginFrame(){ready=false;rows.reset();}
    void upload(Object instances,int count) {
        if(!ready||bound||buffer==null||count<1||count>GpuVoxelSkinPhysicalProjection.ROW_LIMIT)throw new IllegalStateException("Unknown physical voxel upload scope");
        cpuRows(instances,count);
        int old=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer.handle());GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0L,upload);}
        finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,old);}
        GpuVoxelSkinPhysicalProjection.tableUploads++;GpuVoxelSkinPhysicalProjection.tableUploadBytes+=(long)count*64;
    }
    private void cpuRows(Object instances,int count){upload.clear();if(!rows.writeUpload(owner,instances,epoch,frame,epoch,count,upload))throw new IllegalStateException("Physical voxel row/instance epoch mismatch");upload.flip();}
    void beginReplay(Object instances,int count){unbind();cpuRows(instances,count);bind(count);GL20.glUniform1i(fallbackModes[boundProgramIndex],1);}
    void replayMatrix(int index){if(!bound||boundProgramIndex<0||index<0||index>=rows.committedCount())throw new IllegalStateException("Unknown physical replay row");upload.limit((index+1)*16).position(index*16);GL20.glUniformMatrix4fv(fallbackMatrices[boundProgramIndex],false,upload);}
    void bind(int count) {
        int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),index=-1;for(int i=0;i<programCount;i++)if(programs[i]==current)index=i;
        if(!ready||bound||index<0||count!=rows.committedCount()||GL20.glGetUniformi(current,GL20.glGetUniformLocation(current,"uBoxInstancing"))!=0)
            throw new IllegalStateException("Unknown owned physical voxel graphics program");
        previousGeneric=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);previousBuffer=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0);
        previousStart=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,0);previousSize=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0);
        bound=true;boundProgramIndex=index;GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,0,buffer.handle(),0,(long)count*64);
        GL20.glUniform1i(fallbackModes[index],0);GL20.glUniform1i(modes[index],GpuVoxelSkinPhysicalProjection.MODE);GL30.glUniform1ui(counts[index],count);GpuVoxelSkinPhysicalProjection.drawScopes++;
    }
    void unbind() {
        if(!bound)return;
        try{if(previousBuffer==0||previousSize==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,previousBuffer);else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,0,previousBuffer,previousStart,previousSize);}
        finally{try{if(boundProgramIndex>=0){int i=boundProgramIndex;GL41.glProgramUniform1i(programs[i],modes[i],0);GL41.glProgramUniform1ui(programs[i],counts[i],0);GL41.glProgramUniform1i(programs[i],fallbackModes[i],0);}}finally{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previousGeneric);bound=false;boundProgramIndex=-1;}}
    }
    void delete(CommandList device){unbind();rows.reset();ready=false;programCount=0;if(buffer!=null){device.deleteBuffer(buffer);buffer=null;}}
}
