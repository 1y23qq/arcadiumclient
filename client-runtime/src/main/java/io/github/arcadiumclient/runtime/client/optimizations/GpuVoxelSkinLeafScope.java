// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;

final class GpuVoxelSkinLeafScope {
    private final boolean enabled;
    private final Object[] meshes=new Object[8];
    private final float[] scales=new float[8];
    private final int[] lists=new int[8];
    private int depth;
    private Thread thread;
    private boolean poisoned;
    GpuVoxelSkinLeafScope(boolean enabled){this.enabled=enabled;}
    int enter(Object mesh,float scale,int list){
        if(!enabled)return 0;if(thread==null)thread=Thread.currentThread();
        if(thread!=Thread.currentThread()||poisoned||depth==meshes.length||mesh==null){poisoned=true;return 0;}
        meshes[depth]=mesh;scales[depth]=scale;lists[depth]=list;return ++depth;
    }
    void leave(int token){if(token==0)return;if(token!=depth){poisoned=true;Arrays.fill(meshes,null);depth=0;return;}meshes[--depth]=null;}
    Object mesh(int actualList){return enabled&&!poisoned&&thread==Thread.currentThread()&&depth>0&&lists[depth-1]==actualList?meshes[depth-1]:null;}
    float scale(){return scales[depth-1];}
    void beginFrame(){Arrays.fill(meshes,null);depth=0;poisoned=false;thread=Thread.currentThread();}
    void clear(){beginFrame();thread=null;}
}
