// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.*;

public final class CeraResidentBody072Evidence {
    private static final String TYPE="io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodies";
    private static final String[] METRICS={"actorsQueued","partsQueued","actorsDrawn","drawCalls","poseDispatches","replayedParts","errors","materialFallbacks","budgetFallbacks","sharedPoseDispatches","sharedPoseReuses","sharedPoseFallbacks","sharedInputWrites","sharedInputReuses","lateBodyCaptures"};
    private static final long[] start=new long[METRICS.length];
    private static final Snapshot[] stack=new Snapshot[8];
    private static final List<Object> records=new ArrayList<>();
    private static int depth,actualDraws,actualInstances,payloadCalls,restorations,mode2Draws;
    static{for(int i=0;i<stack.length;i++)stack[i]=new Snapshot();}
    private static final class Snapshot{int program,array,buffer,generic,uniform,location;final int[] ids=new int[8];final long[] starts=new long[8],sizes=new long[8];}
    private CeraResidentBody072Evidence(){}
    private static long metric(String field){try{return Class.forName(TYPE).getField(field).getLong(null);}catch(NoSuchFieldException absent){if(!CeraAdmission072Qa.residentSharedPalette()&&(field.startsWith("shared")||field.equals("lateBodyCaptures")))return 0;throw new IllegalStateException(absent);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public static void begin(){
        records.clear();actualDraws=actualInstances=payloadCalls=restorations=depth=mode2Draws=0;
        if(!CeraAdmission072Qa.residentBodyEnabled())return;for(int i=0;i<METRICS.length;i++)start[i]=metric(METRICS[i]);
    }
    public static int beforeDraw(IntBuffer payload,int count,int profiles,int capacity){return beforeDraw(payload,count,profiles,capacity,null);}
    public static int beforeDraw(IntBuffer payload,int count,int profiles,int capacity,Object palette){
        if(!CeraAdmission072Qa.observingResidentBody())return 0;
        require(depth<stack.length&&payload.isDirect()&&payload.order()==ByteOrder.nativeOrder()&&count>0&&count<=8192&&payload.remaining()==count*19&&capacity>=count&&capacity<=8192,"Resident actual payload bounds/19-word ABI changed");
        if(CeraAdmission072Qa.residentSharedPalette())require(palette!=null,"Requested shared palette has no actual draw object");
        payloadCalls++;int position=payload.position(),limit=payload.limit();
        for(int n=0;n<count;n++){
            int at=position+n*19,token=payload.get(at+16),job=token&65535,mask=token>>>16,profile=payload.get(at+17);
            require(job<8192&&mask>0&&(mask&~0xfff)==0&&profile>=0&&profile<profiles&&payload.get(at+18)==0,"Resident body token/profile/mask invalid");
            require(payload.get(at)==Float.floatToRawIntBits(1)&&payload.get(at+4)==Float.floatToRawIntBits(1)&&payload.get(at+8)==Float.floatToRawIntBits(1),"Resident actor is not one compact record");
            records.add(Map.of("job",job,"mask",mask,"parts",Integer.bitCount(mask),"profile",profile,"words",19));
        }
        require(payload.position()==position&&payload.limit()==limit,"Resident observer mutated actual payload");
        Snapshot s=stack[depth++];s.program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);s.array=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);s.buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);s.generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        s.location=GL20.glGetUniformLocation(s.program,"uArcResidentBodies");require(s.location>=0,"Resident native program uniform missing");s.uniform=GL20.glGetUniformi(s.program,s.location);
        for(int i=0;i<8;i++){s.ids[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);s.starts[i]=s.ids[i]==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);s.sizes[i]=s.ids[i]==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        return depth;
    }
    public static void actualDraw(int mode,int first,int vertices,int instances){
        if(!CeraAdmission072Qa.observingResidentBody())return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),location=GL20.glGetUniformLocation(program,"uArcResidentBodies");
        require(depth>0&&mode==GL11.GL_QUADS&&first==0&&vertices==288&&instances>0&&location>=0&&GL20.glGetUniformi(program,location)==(CeraAdmission072Qa.residentSharedPalette()?2:1),"Actual resident draw is not the owned whole-body path");
        require(GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,0)>0&&GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,1)>0,"Resident bone/UV storage missing at actual draw");
        actualDraws++;actualInstances+=instances;if(GL20.glGetUniformi(program,location)==2)mode2Draws++;
    }
    public static void afterDraw(int token){
        if(token==0)return;require(token==depth,"Resident observer scope mismatch");Snapshot s=stack[--depth];
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==s.program&&GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==s.array&&GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)==s.buffer&&GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)==s.generic,"Resident draw did not restore program/VAO/buffer state");
        require(GL20.glGetUniformi(s.program,s.location)==s.uniform,"Resident uniform leaked after owned draw");
        for(int i=0;i<8;i++){int id=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);require(id==s.ids[i]&&(id==0||GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i)==s.starts[i]&&GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i)==s.sizes[i]),"Resident SSBO ID/range leaked at"+i);}
        restorations++;
    }
    public static Map<String,Object> report(){
        Map<String,Object> r=new LinkedHashMap<>();r.put("mode",CeraAdmission072Qa.residentBodyMode());if(!CeraAdmission072Qa.residentBodyEnabled())return r;
        for(int i=0;i<METRICS.length;i++)r.put(METRICS[i],metric(METRICS[i])-start[i]);r.put("actualDraws",actualDraws);r.put("actualMode2Draws",mode2Draws);r.put("actualInstances",actualInstances);r.put("payloadCalls",payloadCalls);r.put("restorations",restorations);r.put("actualRecords",new ArrayList<>(records));r.put("balancedScopes",depth==0);return r;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
