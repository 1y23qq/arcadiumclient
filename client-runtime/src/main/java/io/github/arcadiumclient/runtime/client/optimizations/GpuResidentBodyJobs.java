// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;

final class GpuResidentBodyJobs {
    static final int MAX_JOBS=8192;
    private final int[] epochs=new int[MAX_JOBS],slots=new int[MAX_JOBS],tokens=new int[MAX_JOBS];
    private int generation=1,count,rows;
    private boolean frozen;
    void begin(){if(generation==Integer.MAX_VALUE){Arrays.fill(epochs,0);generation=1;}else generation++;count=rows=0;frozen=false;}
    boolean collecting(){return !frozen;}
    boolean canAdd(int job,int mask){return !frozen&&job>=0&&job<MAX_JOBS&&mask>0&&(mask&~0xfff)==0;}
    void add(int job,int mask){
        if(!canAdd(job,mask))throw new IllegalArgumentException("Invalid or frozen resident body job");
        if(epochs[job]!=generation){epochs[job]=generation;slots[job]=count;tokens[count++]=job|(mask<<16);rows=Math.max(rows,job+1);}
        else tokens[slots[job]]|=mask<<16;
    }
    void freeze(){frozen=true;}
    boolean contains(int job,int mask){return job>=0&&job<MAX_JOBS&&mask>0&&(mask&~0xfff)==0&&epochs[job]==generation&&((tokens[slots[job]]>>>16)&mask)==mask;}
    boolean covers(IntBuffer records){
        if(!frozen||records==null||records.remaining()==0||records.remaining()%19!=0)return false;
        for(int at=records.position();at<records.limit();at+=19){int token=records.get(at+16);if(!contains(token&65535,token>>>16))return false;}
        return true;
    }
    int count(){return count;}
    int rows(){return rows;}
    int token(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException(index);return tokens[index];}
    void copy(IntBuffer destination){
        if(destination==null||!destination.isDirect()||destination.order()!=ByteOrder.nativeOrder()||destination.remaining()<count)
            throw new IllegalArgumentException("Use a bounded direct resident job destination");
        destination.put(tokens,0,count);
    }
}
