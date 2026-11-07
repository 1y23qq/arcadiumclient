// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

final class TerrainResidentNativeOrder implements AutoCloseable {
    static final int BYTES=65*4;
    private final int capacity;
    private final GlMutableBuffer buffer;
    private final byte[] previous;
    private final int[] lengths;
    private final boolean[] seen=new boolean[256];
    private final boolean[] changedRegions;
    private final ByteBuffer upload=BufferUtils.createByteBuffer(BYTES);
    private final IntBuffer packed=upload.asIntBuffer();
    private boolean closed;
    private TerrainResidentResources resources;
    private int ticket=-1;
    long uploads,copiedBytes,comparedBytes;
    TerrainResidentNativeOrder(CommandList device,int capacity){
        if(capacity<1||capacity>1024)throw new IllegalArgumentException("Invalid native terrain order capacity");
        this.capacity=capacity;changedRegions=new boolean[capacity];previous=new byte[capacity*256];lengths=new int[capacity];Arrays.fill(lengths,-1);
        buffer=device.createMutableBuffer();
        try{device.allocateStorage(buffer,(long)capacity*BYTES,GlBufferUsage.DYNAMIC_DRAW);}catch(Throwable failure){buffer.delete();throw failure;}
    }
    TerrainResidentNativeOrder(CommandList device,int capacity,TerrainResidentResources resources){
        if(capacity<1||capacity>1024)throw new IllegalArgumentException("Invalid native terrain order capacity");
        this.capacity=capacity;changedRegions=new boolean[capacity];previous=new byte[capacity*256];lengths=new int[capacity];Arrays.fill(lengths,-1);this.resources=resources;resources.requireCurrent();
        buffer=device.createMutableBuffer();ticket=resources.add(buffer::delete);device.allocateStorage(buffer,(long)capacity*BYTES,GlBufferUsage.DYNAMIC_DRAW);TerrainResidentGpuBuffers.verifySize(buffer,(long)capacity*BYTES);
    }
    boolean update(byte[][] source,int[] counts,int regions){
        if(closed||regions<1||regions>capacity||(source==null)!=(counts==null)
                ||source!=null&&(regions>source.length||regions>counts.length))throw new IllegalArgumentException("Invalid native terrain order input");
        for(int region=0;region<regions;region++){
            int count=counts==null?0:counts[region];byte[] order=source==null?null:source[region];
            if(count<0||count>256||count!=0&&(order==null||count>order.length))throw new IllegalArgumentException("Invalid native terrain order range");
            comparedBytes+=count;changedRegions[region]=count!=lengths[region]||count!=0&&Arrays.mismatch(previous,region*256,region*256+count,order,0,count)!=-1;
            if(!changedRegions[region])continue;
            Arrays.fill(seen,false);
            for(int index=0;index<count;index++){int section=order[index]&255;if(seen[section])throw new IllegalArgumentException("Duplicate native terrain ordering rank");seen[section]=true;}
        }
        int previousWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);boolean changed=false;
        try{
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer.handle());
            for(int region=0;region<regions;region++){
                int count=counts==null?0:counts[region];byte[] order=source==null?null:source[region];
                if(!changedRegions[region])continue;
                for(int word=0;word<65;word++)packed.put(word,0);packed.put(0,count);
                for(int index=0;index<count;index++){int word=1+(index>>>2),shift=(index&3)*8;packed.put(word,packed.get(word)|(order[index]&255)<<shift);}
                upload.clear();GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,(long)region*BYTES,upload);
                if(count!=0)System.arraycopy(order,0,previous,region*256,count);lengths[region]=count;uploads++;copiedBytes+=count;changed=true;
            }
            for(int region=regions;region<capacity;region++)if(lengths[region]!=-1){lengths[region]=-1;changed=true;}
        }finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,previousWrite);}
        return changed;
    }
    void updatePublished(java.util.List<TerrainResidentInventory.Span> spans){
        if(closed)throw new IllegalStateException("Retired native order");if(spans.isEmpty())return;
        for(var span:spans)if(span.firstWord<0||span.firstWord%65!=0||span.length()!=65||(long)span.firstWord+65>(long)capacity*65)throw new IllegalArgumentException("Unknown published rank range");
        int before=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,buffer.handle());for(var span:spans){upload.clear();span.put(upload.asIntBuffer());upload.limit(BYTES);GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,(long)span.firstWord*4,upload);uploads++;copiedBytes+=span.word(0);}}
        finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,before);}
    }
    int handle(){return buffer.handle();}
    @Override public void close(){if(!closed){if(resources!=null)resources.delete(ticket);else buffer.delete();closed=true;}}
}
