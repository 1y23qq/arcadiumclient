// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.*;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class TerrainOracleGeometry073 {
    private TerrainOracleGeometry073(){ }
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static Object passScene(String passName)throws Exception{
        Field field=TerrainResidentScene.class.getDeclaredField("book");field.setAccessible(true);
        var book=(TerrainResidentPassBook<?>)field.get(null);if(book==null)throw new IllegalStateException("No resident terrain book");
        Object found=null;
        for(Object scene:book.values())if(((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)field(scene,"pass")).name().equals(passName)){
            if(found!=null)throw new IllegalStateException("Duplicated resident pass name");found=scene;
        }
        if(found==null)throw new IllegalStateException("Resident pass unavailable: "+passName);return found;
    }
    public static Map<String,Object> capture()throws Exception{return capture("solid");}
    public static Map<String,Object> capture(String passName)throws Exception{
        Object scene=passScene(passName);
        var buffers=(TerrainResidentGpuBuffers)field(scene,"buffers");var entries=new ArrayList<>(((Map<?,?>)field(scene,"byStorage")).values());if(entries.isEmpty()||entries.size()>32)throw new IllegalStateException("Owned region readback bound exceeded");
        entries.sort(Comparator.comparingInt(e->{try{return (int)field(e,"slot");}catch(Exception error){throw new IllegalStateException(error);}}));
        IntBuffer counts=BufferUtils.createIntBuffer(entries.size());int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);var rows=new ArrayList<Map<String,Object>>();int total=0;
        try{
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffers.countHandle());GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,counts);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffers.commandHandle());
            for(Object entry:entries){int slot=(int)field(entry,"slot"),count=counts.get(slot);if(slot<0||slot>=entries.size()||count<0||count>1792||(total+=count)>32768)throw new IllegalStateException("Owned indirect command readback bound exceeded");
                var region=(RenderRegion)field(entry,"region");var data=(TerrainResidentRegionData)field(entry,"data");var commands=new ArrayList<List<Integer>>();
                if(count>0){IntBuffer words=BufferUtils.createIntBuffer(count*5);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,buffers.commandOffset(slot),words);for(int at=0;at<count*5;at+=5)commands.add(List.of(words.get(at),words.get(at+1),words.get(at+2),words.get(at+3),words.get(at+4)));}
                rows.add(Map.of("slot",slot,"origin",List.of(region.getOriginX(),region.getOriginY(),region.getOriginZ()),"sourceVersion",data.version,"sourceSerial",field(entry,"serial"),"commandCount",count,"commands",commands));
            }
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Indirect recipe readback GL error");
        return Map.of("pass",passName,"readOnly",true,"regions",rows,"totalCommands",total,"commandWordCount",5,"maximumRegions",32,"maximumCommands",32768,"computeDispatches",buffers.dispatches,"meshUploads",buffers.meshUploads);
    }
    public static Map<String,Object> difference(Map<String,Object> source,Map<String,Object> target){
        Map<String,Long> oldVersions=versions(source),newVersions=versions(target);if(!oldVersions.equals(newVersions))throw new IllegalStateException("Native source/region publication changed during the same-anchor reversal");
        Set<String> before=keys(source),after=keys(target),added=new HashSet<>(after),removed=new HashSet<>(before);added.removeAll(before);removed.removeAll(after);
        return Map.of("sourceCommands",before.size(),"targetCommands",after.size(),"newlyExposedCommands",added.size(),"noLongerExposedCommands",removed.size(),"nativeSourcesUnchanged",true,"newlyExposedExamples",added.stream().sorted().limit(16).toList());
    }
    private static Map<String,Long> versions(Map<String,Object> value){var result=new TreeMap<String,Long>();for(Object object:(List<?>)value.get("regions")){var row=(Map<?,?>)object;result.put(row.get("origin").toString(),((Number)row.get("sourceVersion")).longValue());}return result;}
    private static Set<String> keys(Map<String,Object> value){Set<String> result=new HashSet<>();for(Object object:(List<?>)value.get("regions")){var row=(Map<?,?>)object;for(Object command:(List<?>)row.get("commands"))result.add(row.get("origin")+":"+command);}return result;}
}
