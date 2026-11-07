// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.Arrays;

final class ActorFusedRows {
    private static final int MAX=8192,MAGIC1=Float.floatToRawIntBits(-9876543f),MAGIC2=Float.floatToRawIntBits(-8765432f),SORT=Float.floatToRawIntBits(-7654321f);
    private final int[] seen=new int[MAX];private int[] ids=new int[16];private int generation,count;
    int count(){return count;}int at(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException();return ids[index];}
    boolean collect(IntBuffer payload,int rows){
        count=0;if(payload==null||payload.remaining()%19!=0||rows<0||rows>MAX)return false;
        if(generation==Integer.MAX_VALUE){Arrays.fill(seen,0);generation=0;}generation++;
        for(int i=payload.position();i<payload.limit();i+=19){
            if(payload.get(i+1)!=MAGIC1||(payload.get(i+2)!=MAGIC2&&payload.get(i+2)!=SORT))continue;
            float marker=Float.intBitsToFloat(payload.get(i));if(marker>=0)continue;
            float value=-marker-1;int encoded=(int)value,job=encoded>>>4;
            if(!Float.isFinite(marker)||value!=encoded||encoded<0||(encoded&15)>=12||job>=rows)return false;
            if(seen[job]==generation)continue;seen[job]=generation;if(count==ids.length)ids=Arrays.copyOf(ids,Math.min(MAX,ids.length*2));ids[count++]=job;
        }
        Arrays.sort(ids,0,count);return true;
    }
}
