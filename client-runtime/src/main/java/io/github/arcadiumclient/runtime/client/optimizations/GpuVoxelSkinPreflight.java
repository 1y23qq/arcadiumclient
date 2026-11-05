// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Objects;

final class GpuVoxelSkinPreflight {
    private Object main,parts,texture,fact;
    private int mask,sourceTemplate,variantTemplate;
    private long epoch;
    private boolean ready;
    void arm(Object main,Object parts,int mask,Object texture,int sourceTemplate,int variantTemplate,long epoch,Object fact){
        this.main=main;this.parts=parts;this.mask=mask;this.texture=texture;this.sourceTemplate=sourceTemplate;this.variantTemplate=variantTemplate;this.epoch=epoch;this.fact=fact;ready=true;
    }
    int consume(Object main,Object parts,int mask,Object texture,int sourceTemplate,long epoch){
        int result=ready&&this.main==main&&this.parts==parts&&this.mask==mask&&Objects.equals(this.texture,texture)
                &&this.sourceTemplate==sourceTemplate&&this.epoch==epoch?variantTemplate:-1;
        clear();return result;
    }
    Object fact(){return ready?fact:null;}
    void clear(){main=parts=texture=fact=null;mask=sourceTemplate=variantTemplate=0;epoch=0;ready=false;}
    static boolean hasRoom(int jobs,int maximum,int instances,int parts){
        return maximum>=2&&jobs>=0&&jobs<=maximum-2&&instances>=0&&parts>=0&&parts<=12&&instances<=65536-parts;
    }
}
