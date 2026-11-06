// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

final class TerrainResidentGpuBuffers implements AutoCloseable {
    static final int MAX_REGIONS=1024,MESH_BYTES=256*20*4,COMMAND_BYTES=1792*20,REGION_BYTES=MESH_BYTES+COMMAND_BYTES+4+TerrainResidentNativeOrder.BYTES;
    private final GlMutableBuffer meshes,commands,counts;
    private final TerrainResidentNativeOrder nativeOrder;
    private final int program,clipLocation,renderLocation,fractionLocation,faceLocation,faceFractionLocation,distanceLocation,distanceEnabledLocation,cullLocation;
    private final int capacity;
    private final long[] versions;
    private final Object[] identities;
    private final ByteBuffer metadataUpload=BufferUtils.createByteBuffer(MESH_BYTES);
    private final FloatBuffer matrixUpload=BufferUtils.createFloatBuffer(16);
    private final int[] oldBindings=new int[4];
    private final long[] oldStarts=new long[4],oldSizes=new long[4];
    private final int[] previousFrame=new int[33];
    private boolean initialized,closed;
    long meshUploads,dispatches,reuses;
    TerrainResidentGpuBuffers(CommandList device,int capacity,String shaderSource){
        if(capacity<1||capacity>MAX_REGIONS||(long)capacity*REGION_BYTES>64L*1024*1024)throw new IllegalArgumentException("Resident terrain GPU budget exceeded");
        this.capacity=capacity;versions=new long[capacity];identities=new Object[capacity];
        GlMutableBuffer newMeshes=null,newCommands=null,newCounts=null;TerrainResidentNativeOrder newOrder=null;int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),candidate=GL20.glCreateProgram();
        try{
            GL20.glShaderSource(shader,shaderSource);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate,shader);GL20.glLinkProgram(candidate);if(GL20.glGetProgrami(candidate,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            clipLocation=uniform(candidate,"uClip");renderLocation=uniform(candidate,"uRenderCamera");fractionLocation=uniform(candidate,"uCameraFraction");faceLocation=uniform(candidate,"uFaceCamera");
            faceFractionLocation=uniform(candidate,"uFaceCameraFraction");distanceLocation=uniform(candidate,"uSearchDistance");distanceEnabledLocation=uniform(candidate,"uDistanceEnabled");cullLocation=uniform(candidate,"uCullFaces");
            newMeshes=device.createMutableBuffer();newCommands=device.createMutableBuffer();newCounts=device.createMutableBuffer();
            device.allocateStorage(newMeshes,(long)capacity*MESH_BYTES,GlBufferUsage.DYNAMIC_DRAW);device.allocateStorage(newCommands,(long)capacity*COMMAND_BYTES,GlBufferUsage.DYNAMIC_DRAW);device.allocateStorage(newCounts,(long)capacity*4,GlBufferUsage.DYNAMIC_DRAW);
            newOrder=new TerrainResidentNativeOrder(device,capacity);
            meshes=newMeshes;commands=newCommands;counts=newCounts;nativeOrder=newOrder;program=candidate;
        }catch(Throwable failure){if(newMeshes!=null)newMeshes.delete();if(newCommands!=null)newCommands.delete();if(newCounts!=null)newCounts.delete();if(newOrder!=null)newOrder.close();GL20.glDeleteProgram(candidate);throw failure;}
        finally{GL20.glDeleteShader(shader);}
    }
    private static int uniform(int program,String name){int location=GL20.glGetUniformLocation(program,name);if(location<0)throw new IllegalStateException("Missing resident terrain uniform"+name);return location;}
    boolean prepare(Object[] storageIdentities,TerrainResidentRegionData[] regions,int count,float[] clip,
                    int renderX,int renderY,int renderZ,float fractionX,float fractionY,float fractionZ,
                    int faceX,int faceY,int faceZ,float faceFractionX,float faceFractionY,float faceFractionZ,float distance,boolean distanceEnabled,boolean cull){
        return prepare(storageIdentities,regions,count,null,null,clip,renderX,renderY,renderZ,fractionX,fractionY,fractionZ,faceX,faceY,faceZ,faceFractionX,faceFractionY,faceFractionZ,distance,distanceEnabled,cull);
    }
    boolean prepare(Object[] storageIdentities,TerrainResidentRegionData[] regions,int count,byte[][] orders,int[] orderCounts,float[] clip,
                    int renderX,int renderY,int renderZ,float fractionX,float fractionY,float fractionZ,
                    int faceX,int faceY,int faceZ,float faceFractionX,float faceFractionY,float faceFractionZ,float distance,boolean distanceEnabled,boolean cull){
        if(closed||count<1||count>capacity||count>regions.length||count>storageIdentities.length||clip.length!=16)throw new IllegalArgumentException("Invalid resident terrain frame");
        for(float value:clip)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite resident terrain clip matrix");
        if(!Float.isFinite(distance)||distance<=0||!fractions(fractionX,fractionY,fractionZ)||!fractions(faceFractionX,faceFractionY,faceFractionZ))throw new IllegalArgumentException("Invalid resident terrain camera/distance");
        boolean changed=nativeOrder.update(orders,orderCounts,count);
        int previousWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try{
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,meshes.handle());
            for(int region=0;region<count;region++){
                TerrainResidentRegionData data=regions[region];Object identity=storageIdentities[region];if(data==null||identity==null||data.version<=0)throw new IllegalArgumentException("Missing current resident mesh version");
                if(identities[region]==identity&&versions[region]==data.version)continue;
                metadataUpload.clear();metadataUpload.asIntBuffer().put(data.words);metadataUpload.limit(MESH_BYTES);GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,(long)region*MESH_BYTES,metadataUpload);
                identities[region]=identity;versions[region]=data.version;meshUploads++;changed=true;
            }
            for(int region=count;region<capacity;region++)if(identities[region]!=null){identities[region]=null;versions[region]=0;changed=true;}
        }finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,previousWrite);}
        int[] frame=new int[33];for(int i=0;i<16;i++)frame[i]=Float.floatToRawIntBits(clip[i]);
        frame[16]=renderX;frame[17]=renderY;frame[18]=renderZ;frame[19]=Float.floatToRawIntBits(fractionX);frame[20]=Float.floatToRawIntBits(fractionY);frame[21]=Float.floatToRawIntBits(fractionZ);
        frame[22]=faceX;frame[23]=faceY;frame[24]=faceZ;frame[25]=Float.floatToRawIntBits(faceFractionX);frame[26]=Float.floatToRawIntBits(faceFractionY);frame[27]=Float.floatToRawIntBits(faceFractionZ);
        frame[28]=Float.floatToRawIntBits(distance);frame[29]=distanceEnabled?1:0;frame[30]=cull?1:0;frame[31]=count;
        if(initialized&&!changed&&Arrays.equals(frame,previousFrame)){reuses++;return false;}
        int previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),previousGeneric=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int i=0;i<4;i++){int binding=oldBindings[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);oldStarts[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);oldSizes[i]=binding==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        try{
            GL20.glUseProgram(program);matrixUpload.clear();matrixUpload.put(clip).flip();GL20.glUniformMatrix4fv(clipLocation,false,matrixUpload);
            GL20.glUniform3i(renderLocation,renderX,renderY,renderZ);GL20.glUniform3f(fractionLocation,fractionX,fractionY,fractionZ);GL20.glUniform3i(faceLocation,faceX,faceY,faceZ);GL20.glUniform3f(faceFractionLocation,faceFractionX,faceFractionY,faceFractionZ);
            GL20.glUniform1f(distanceLocation,distance);GL30.glUniform1ui(distanceEnabledLocation,distanceEnabled?1:0);GL30.glUniform1ui(cullLocation,cull?1:0);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,meshes.handle());GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,commands.handle());GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,counts.handle());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,nativeOrder.handle());
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT);GL43.glDispatchCompute(count,1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_COMMAND_BARRIER_BIT);
            System.arraycopy(frame,0,previousFrame,0,frame.length);initialized=true;dispatches++;return true;
        }finally{
            for(int i=0;i<4;i++)if(oldBindings[i]==0||oldSizes[i]==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,oldBindings[i]);else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,oldBindings[i],oldStarts[i],oldSizes[i]);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previousGeneric);GL20.glUseProgram(previousProgram);
        }
    }
    private static boolean fractions(float x,float y,float z){return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z)&&Math.abs(x)<=1&&Math.abs(y)<=1&&Math.abs(z)<=1;}
    int commandHandle(){return commands.handle();}
    int countHandle(){return counts.handle();}
    int orderHandle(){return nativeOrder.handle();}
    long orderUploads(){return nativeOrder.uploads;}
    long orderCopiedBytes(){return nativeOrder.copiedBytes;}
    long orderComparedBytes(){return nativeOrder.comparedBytes;}
    long commandOffset(int slot){if(slot<0||slot>=capacity)throw new IllegalArgumentException("Invalid resident terrain slot");return(long)slot*COMMAND_BYTES;}
    long countOffset(int slot){if(slot<0||slot>=capacity)throw new IllegalArgumentException("Invalid resident terrain slot");return(long)slot*4;}
    long residentBytes(){return(long)capacity*REGION_BYTES;}
    @Override public void close(){if(closed)return;closed=true;meshes.delete();commands.delete();counts.delete();nativeOrder.close();GL20.glDeleteProgram(program);Arrays.fill(identities,null);}
}
