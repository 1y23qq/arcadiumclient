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
        if(TerrainEvent073Evidence.enabled())return captureEvent(passName);
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
    private static int[] readWords(int handle,long offset,int words){GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,handle);IntBuffer out=BufferUtils.createIntBuffer(words);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,offset,out);int[] copy=new int[words];out.get(copy);return copy;}
    private static String digest(int[] words)throws Exception{var bytes=java.nio.ByteBuffer.allocate(words.length*4).order(java.nio.ByteOrder.nativeOrder());bytes.asIntBuffer().put(words);return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array()));}
    private static Map<String,Object> captureEvent(String passName)throws Exception{
        var c=TerrainEvent073Evidence.context(passName);var frame=c.frame();int high=frame.highWater;TerrainEvent073Evidence.need(high>0&&high<=32&&c.inventory().ownerThread(),"Event geometry/readback bound");
        Object nativeList=TerrainEvent073Evidence.field(frame,"nativeList");TerrainEvent073Evidence.need(frame.valid(nativeList),"Event source no longer current at actual return");
        var prefix=new IdentityHashMap<RenderRegion,org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList>();var nativeRegionOrder=new ArrayList<List<Integer>>();
        for(var iterator=((org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable)nativeList).iterator(false);iterator.hasNext();){var list=iterator.next();var region=list.getRegion();TerrainEvent073Evidence.need(prefix.put(region,list)==null,"Duplicate actual native region order");nativeRegionOrder.add(List.of(region.getOriginX(),region.getOriginY(),region.getOriginZ()));}
        var slots=(Object[])TerrainEvent073Evidence.field(((Object[])TerrainEvent073Evidence.field(c.inventory(),"passes"))[frame.pass],"slots");
        float[] clip=(float[])TerrainEvent073Evidence.field(c.owner(),"clip");float distance=(float)TerrainEvent073Evidence.field(c.owner(),"currentDistance");Object current=((ThreadLocal<?>)TerrainEvent073Evidence.field(TerrainResidentScene.class,null,"current")).get();TerrainEvent073Evidence.need(current!=null,"Event snapshot must be inside real native pass return");
        var face=(org.embeddedt.embeddium.impl.render.viewport.CameraTransform)TerrainEvent073Evidence.field(current,"face");var render=(org.embeddedt.embeddium.impl.render.viewport.CameraTransform)TerrainEvent073Evidence.field(current,"render");boolean cull=(boolean)TerrainEvent073Evidence.field(current,"cull");
        var rows=new ArrayList<Map<String,Object>>();var holes=new ArrayList<Integer>();int total=0,previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try{
            int[] counts=readWords(c.buffers().countHandle(),0,high);var meshBuffer=(org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer)TerrainEvent073Evidence.field(c.buffers(),"meshes");
            for(int slot=0;slot<high;slot++){
                int count=counts[slot];TerrainEvent073Evidence.need(count>=0&&count<=1792&&(total+=count)<=32768,"Invalid actual event indirect count");int[] gpuMetadata=readWords(meshBuffer.handle(),slot*20480L,5120),rankWords=readWords(c.buffers().orderHandle(),slot*260L,65);
                Object source=slots[slot];if(source==null){TerrainEvent073Evidence.need(count==0&&java.util.Arrays.stream(gpuMetadata).allMatch(x->x==0)&&java.util.Arrays.stream(rankWords).allMatch(x->x==0),"Retired event slot retained geometry");holes.add(slot);continue;}
                var region=(RenderRegion)TerrainEvent073Evidence.field(TerrainEvent073Evidence.field(source,"region"),"identity");var storage=(org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage)TerrainEvent073Evidence.field(source,"storage");
                TerrainEvent073Evidence.need(region.getStorage(c.pass().value())==storage,"Native storage identity changed");var nativeData=new TerrainResidentRegionData(region,storage,frame.epoch);TerrainEvent073Evidence.need(Arrays.equals(nativeData.words,gpuMetadata)&&Arrays.equals((int[])TerrainEvent073Evidence.field(source,"words"),gpuMetadata),"Actual native/CPU event/GPU metadata differ");
                var list=prefix.get(region);byte[] ranks=list==null?new byte[0]:Arrays.copyOf(list.getSectionsWithGeometry(),list.getSectionsWithGeometryCount());TerrainEvent073Evidence.need(rankWords[0]==ranks.length,"Actual native prefix rank count differs");for(int at=0;at<256;at++)TerrainEvent073Evidence.need(((rankWords[1+(at>>>2)]>>>((at&3)*8))&255)==(at<ranks.length?(ranks[at]&255):0),"Actual native/GPU rank differs");
                byte[] order=TerrainResidentNativeFallbackPlan.order(ranks,ranks.length,section->{int at=section*20;return storage.getSliceMask(section)!=0&&TerrainResidentFrustumPlan.withinDistance(nativeData.words[at],nativeData.words[at+1],nativeData.words[at+2],face.intX,face.intY,face.intZ,face.fracX,face.fracY,face.fracZ,distance)&&TerrainResidentFrustumPlan.visible(clip,nativeData.words[at],nativeData.words[at+1],nativeData.words[at+2],render.intX,render.intY,render.intZ,render.fracX,render.fracY,render.fracZ);});
                var ordered=new org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList(region);for(byte section:order)ordered.add(section&255,1);
                int[] gpu=count==0?new int[0]:readWords(c.buffers().commandHandle(),c.buffers().commandOffset(slot),count*5);var commands=new ArrayList<List<Integer>>();for(int at=0;at<gpu.length;at+=5)commands.add(List.of(gpu[at],gpu[at+1],gpu[at+2],gpu[at+3],gpu[at+4]));
                var nativeBatch=org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler.fillRegion(region,storage,ordered,face,c.pass().value(),cull);
                try{int nativeCount=nativeBatch==null?0:nativeBatch.size();TerrainEvent073Evidence.need(nativeCount==count,"Actual official native/GPU command count differs");if(nativeBatch!=null){TerrainEvent073Evidence.need(nativeBatch instanceof org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch,"Unknown native reference batch ABI");var direct=(org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch)nativeBatch;for(int at=0;at<count;at++)TerrainEvent073Evidence.need(gpu[at*5]==org.lwjgl.system.MemoryUtil.memGetInt(direct.pElementCount+at*4L)&&gpu[at*5+1]==1&&gpu[at*5+2]==0&&gpu[at*5+3]==org.lwjgl.system.MemoryUtil.memGetInt(direct.pBaseVertex+at*4L)&&gpu[at*5+4]==0,"Official native current-range command differs");}}finally{if(nativeBatch!=null)nativeBatch.delete();}
                var row=new LinkedHashMap<String,Object>();row.put("slot",slot);row.put("origin",List.of(region.getOriginX(),region.getOriginY(),region.getOriginZ()));row.put("sourceVersion",TerrainEvent073Evidence.field(source,"generation"));row.put("sourceVersionIsStorageGeneration",true);row.put("nativeMetadataSha256",digest(nativeData.words));row.put("nativeRanks",ranks);row.put("sourceSerial",frame.epoch);row.put("commandCount",count);row.put("commands",commands);row.put("nativeCurrentMetadataExact",true);row.put("officialNativeCommandsExact",true);row.put("currentStorageIdentity",System.identityHashCode(storage));rows.add(row);
            }
        }finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        TerrainEvent073Evidence.need(frame.valid(nativeList)&&GL11.glGetError()==0,"Event source/GL state changed during readonly snapshot");var result=new LinkedHashMap<String,Object>();result.put("schema","event-native-source-v1");result.put("pass",passName);result.put("readOnly",true);result.put("regions",rows);result.put("retiredSlots",holes);result.put("slotHighWater",high);result.put("nativeRegionPrefixOrder",nativeRegionOrder);result.put("sourceEpoch",frame.epoch);result.put("nativeOrderPublication",frame.orderPublication);result.put("totalCommands",total);result.put("commandWordCount",5);result.put("computeDispatches",c.buffers().dispatches);result.put("meshUploads",c.buffers().meshUploads);return result;
    }
    public static Map<String,Object> difference(Map<String,Object> source,Map<String,Object> target){
        Map<String,Long> oldVersions=versions(source),newVersions=versions(target);if(!oldVersions.equals(newVersions)||!metadataHashes(source).equals(metadataHashes(target)))throw new IllegalStateException("Native source/region publication changed during the same-anchor reversal");
        Set<String> before=keys(source),after=keys(target),added=new HashSet<>(after),removed=new HashSet<>(before);added.removeAll(before);removed.removeAll(after);
        return Map.of("sourceCommands",before.size(),"targetCommands",after.size(),"newlyExposedCommands",added.size(),"noLongerExposedCommands",removed.size(),"nativeSourcesUnchanged",true,"newlyExposedExamples",added.stream().sorted().limit(16).toList());
    }
    private static Map<String,Long> versions(Map<String,Object> value){var result=new TreeMap<String,Long>();for(Object object:(List<?>)value.get("regions")){var row=(Map<?,?>)object;result.put(row.get("origin").toString(),((Number)row.get("sourceVersion")).longValue());}return result;}
    private static Map<String,Object> metadataHashes(Map<String,Object> value){var result=new TreeMap<String,Object>();for(Object object:(List<?>)value.get("regions")){var row=(Map<?,?>)object;if(row.containsKey("nativeMetadataSha256"))result.put(row.get("origin").toString(),row.get("nativeMetadataSha256"));}return result;}
    private static Set<String> keys(Map<String,Object> value){Set<String> result=new HashSet<>();for(Object object:(List<?>)value.get("regions")){var row=(Map<?,?>)object;for(Object command:(List<?>)row.get("commands"))result.add(row.get("origin")+":"+command);}return result;}
}
