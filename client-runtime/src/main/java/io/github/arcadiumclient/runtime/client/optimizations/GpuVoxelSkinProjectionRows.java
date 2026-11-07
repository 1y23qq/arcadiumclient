// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.util.Arrays;

public final class GpuVoxelSkinProjectionRows {
    public interface Queries {
        int matrixMode();
        void modelView(float[] destination);
        void projection(float[] destination);
    }
    public record Metrics(long attempts,long modeQueries,long modelViewQueries,long projectionQueries,long projectionReuse,
                          long committedRows,long declined,long timedCaptureNanos) {}
    private final Queries queries;
    private final float[] physical=new float[16],projection=new float[16],pendingMvp=new float[16],rows;
    private final int capacity;
    private Object owner,instances,pendingMesh,pendingInstances;
    private long epoch,frame,projectionEpoch,serial,pending;
    private int count;
    private boolean qualified,reuseProjection,projectionValid,reserved,timing;
    private long attempts,modeQueries,mvQueries,pQueries,reuse,committed,declined,nanos;
    public GpuVoxelSkinProjectionRows(int capacity,Queries queries) {
        if(capacity<1||capacity>8192||queries==null)throw new IllegalArgumentException("Bounded owner rows and queries required");
        this.capacity=capacity;this.queries=queries;rows=new float[capacity*16];
    }
    public void beginPass(Object owner,long epoch,long frame,long projectionEpoch,boolean contextAndHooksQualified,
                          boolean normalUnsortedPass,boolean projectionEpochQualified,boolean diagnosticTiming) {
        reset();this.owner=owner;this.epoch=epoch;this.frame=frame;this.projectionEpoch=projectionEpoch;
        qualified=owner!=null&&epoch>=0&&frame>=0&&projectionEpoch>=0&&contextAndHooksQualified&&normalUnsortedPass;
        reuseProjection=projectionEpochQualified;timing=diagnosticTiming;
    }
    public long captureAtCompiledList(Object owner,long epoch,long frame,Object mesh,int part,float actualScale) {
        attempts++;
        if(!matches(owner,epoch,frame)||!qualified||mesh==null||part<6||part>11||Float.floatToRawIntBits(actualScale)!=0x3d800000||pending!=0||count==capacity){declined++;return 0;}
        long started=timing?System.nanoTime():0;
        try {
            modeQueries++;if(queries.matrixMode()!=5888){declined++;return 0;}
            mvQueries++;queries.modelView(physical);
            if(!reuseProjection||!projectionValid){pQueries++;queries.projection(projection);projectionValid=knownPerspective(projection);}
            else reuse++;
            if(!affine(physical)||!projectionValid){declined++;return 0;}
            for(int c=0;c<4;c++)for(int r=0;r<4;r++) {
                float a=projection[r]*physical[c*4],b=projection[4+r]*physical[c*4+1],d=projection[8+r]*physical[c*4+2],e=projection[12+r]*physical[c*4+3];
                pendingMvp[c*4+r]=(a+b)+(d+e);
            }
            if(!finite(pendingMvp)){declined++;return 0;}
            pending=++serial;if(pending==0)pending=++serial;pendingMesh=mesh;reserved=false;pendingInstances=null;return pending;
        } catch(RuntimeException failure){projectionValid=false;declined++;return 0;}
        finally{if(timing)nanos+=System.nanoTime()-started;}
    }
    public boolean reserveBeforeNativeSubmit(long ticket,Object actualInstances,int expectedCount) {
        if(ticket==0||ticket!=pending||actualInstances==null||expectedCount!=count||count>=capacity||instances!=null&&instances!=actualInstances){declined++;return false;}
        pendingInstances=actualInstances;reserved=true;return true;
    }
    public boolean commitAfterNativeAppend(long ticket,int actualCount) {
        if(ticket==0||ticket!=pending||!reserved||actualCount!=count+1){declined++;return false;}
        System.arraycopy(pendingMvp,0,rows,count*16,16);instances=pendingInstances;count++;committed++;clearPending();return true;
    }
    public void cancel(long ticket){if(ticket!=0&&ticket==pending)clearPending();}
    public boolean copyPending(long ticket,float[] eye,float[] p,float[] mvp) {
        if(ticket==0||ticket!=pending||eye.length!=16||p.length!=16||mvp.length!=16)return false;
        System.arraycopy(physical,0,eye,0,16);System.arraycopy(projection,0,p,0,16);System.arraycopy(pendingMvp,0,mvp,0,16);return true;
    }
    public boolean writeUpload(Object owner,Object actualInstances,long epoch,long frame,long projectionEpoch,int actualCount,FloatBuffer destination) {
        if(!matches(owner,epoch,frame)||actualInstances!=instances||this.projectionEpoch!=projectionEpoch||pending!=0||actualCount!=count||destination.remaining()<count*16)return false;
        destination.put(rows,0,count*16);return true;
    }
    public void invalidateProjection(long nextEpoch){projectionEpoch=nextEpoch;projectionValid=false;}
    public void reset(){owner=instances=null;qualified=projectionValid=false;count=0;clearPending();}
    public int retainedRowCapacity(){return capacity;}
    public int committedCount(){return count;}
    public Metrics metrics(){return new Metrics(attempts,modeQueries,mvQueries,pQueries,reuse,committed,declined,nanos);}
    private void clearPending(){pending=0;pendingMesh=pendingInstances=null;reserved=false;}
    private boolean matches(Object owner,long epoch,long frame){return this.owner==owner&&owner!=null&&this.epoch==epoch&&this.frame==frame;}
    private static boolean finite(float[] m){for(float v:m)if(!Float.isFinite(v))return false;return true;}
    private static boolean affine(float[] m){return finite(m)&&m[3]==0&&m[7]==0&&m[11]==0&&m[15]==1;}
    private static boolean knownPerspective(float[] m){
        if(!finite(m)||!(m[0]>0)||!(m[5]>0)||!(m[10]<-1)||m[11]!=-1||!(m[14]<0)||m[15]!=0)return false;
        for(int i:new int[]{1,2,3,4,6,7,8,9,12,13})if(m[i]!=0)return false;return true;
    }
}
