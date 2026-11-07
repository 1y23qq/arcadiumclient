// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.*;

public final class FusedRowProtocol073 {
    public record Producer(String subject,long frame,long batch,int job,int slot,long handle,boolean sceneOnly){ }
    public record Natural(long frame,long batch,int job,int slot,long handle,long tick,long revision,int partialBits,int arithmeticMask,boolean active,boolean exact,int[] row,int[] tickWords){ }
    private FusedRowProtocol073(){ }
    public static int[] jobs(IntBuffer payload,int rows){
        require(payload!=null&&payload.remaining()%19==0&&rows>=0&&rows<=256,"Unsupported bounded native payload");var ids=new TreeSet<Integer>();
        int magic=Float.floatToRawIntBits(-9876543f),normal=Float.floatToRawIntBits(-8765432f),sort=Float.floatToRawIntBits(-7654321f);
        for(int at=payload.position();at<payload.limit();at+=19){if(payload.get(at+1)!=magic||payload.get(at+2)!=normal&&payload.get(at+2)!=sort)continue;float marker=Float.intBitsToFloat(payload.get(at));if(marker>=0)continue;float value=-marker-1;int encoded=(int)value,job=encoded>>>4;require(Float.isFinite(marker)&&value==encoded&&encoded>=0&&(encoded&15)<12&&job<rows,"Malformed actual job marker");ids.add(job);}
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
    public static Map<String,Object> associate(Producer p,Natural n,int metadataBase,int[] metadata,int[] player,int[] tick,boolean selected,boolean dispatched){
        require(p!=null&&p.job>=0&&p.job<256&&selected&&dispatched,"No real selected/dispatched producer row");require(player!=null&&player.length==32,"Missing actual native input row");
        var out=new LinkedHashMap<String,Object>();out.put("subject",p.subject);out.put("frame",p.frame);out.put("batch",p.batch);out.put("producerJob",p.job);out.put("producerSlot",p.slot);out.put("producerHandle",p.handle);out.put("sceneOnlyProducer",p.sceneOnly);out.put("actualNativeDispatchReturned",true);
        boolean tagged=metadataBase>0&&metadata!=null&&metadata.length==4&&metadata[0]==1;
        out.put("fusedTaggedRow",tagged);out.put("gpuHasHandleWord",false);out.put("actualGpuPlayerWords",player.clone());
        if(!tagged){require(metadataBase==0||metadata!=null&&metadata.length==4&&metadata[0]==0,"Unknown actual metadata tag");out.put("lane","ordinary-native32");out.put("fallbackReason",metadataBase==0?"No fused metadata bound":"Subject row not tagged by the real fused producer");return out;}
        require(metadataBase%32==0&&metadataBase>=32&&metadataBase<=8192*32,"Invalid actual metadata base");int capacity=metadataBase/32;
        require(n!=null&&n.active&&!n.exact&&n.frame==p.frame&&n.batch==p.batch&&n.job==p.job&&n.slot==p.slot&&n.handle==p.handle&&p.slot>=0&&p.handle>0,"Producer/raw row identity differs");
        require(n.row!=null&&n.row.length==32&&n.tickWords!=null&&n.tickWords.length==16&&tick!=null&&tick.length==16,"Missing raw/current tick words");
        require(metadata[1]==capacity*36+n.slot*16&&metadata[2]==n.partialBits,"Actual GPU row points at a different slot/partial");
        int mask=n.arithmeticMask;float delta=Float.intBitsToFloat(n.partialBits);if(n.partialBits==0x80000000)mask|=31;else if(delta>0&&delta<0x1p-24f)mask|=30;
        require(metadata[3]==(mask==0?0:((capacity*52+p.job*5)<<5)|mask),"Actual override recipe differs");
        for(int word=0;word<32;word++)require(player[word]==(word>=16&&word<=20?0:n.row[word]),"Actual fused native32 prefix differs at"+word);
        require(Arrays.equals(n.tickWords,tick),"Actual dispatched tick block differs from the natural raw view");
        out.put("lane","fused-input-in-native-model-dispatch");out.put("naturalTick",n.tick);out.put("naturalTickRevision",n.revision);out.put("naturalViewBatch",n.batch);out.put("naturalRowJob",n.job);out.put("naturalSlot",n.slot);out.put("naturalHandle",n.handle);out.put("naturalPartialBits",n.partialBits);out.put("naturalArithmeticMask",n.arithmeticMask);out.put("naturalRawRowWords",n.row.clone());out.put("naturalRawTickWords",n.tickWords.clone());out.put("actualGpuMetadata",metadata.clone());out.put("actualGpuPlayerWords",player.clone());out.put("actualGpuTickWords",tick.clone());out.put("handleAssociation","Producer owner→dense slot/handle→naturally sealed raw tick→actual metadata slot and identical GPU tick bytes");return out;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
