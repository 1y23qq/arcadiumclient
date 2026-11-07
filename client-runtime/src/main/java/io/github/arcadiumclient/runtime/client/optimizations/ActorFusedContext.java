// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ActorFusedContext {
    record Key(long platformHandle,Object capabilities,long epoch){}
    private Key owner;private long epoch;
    boolean accept(long handle,Object capabilities,boolean eligible){
        if(!eligible||handle==0||capabilities==null)return false;
        if(owner!=null)return owner.platformHandle==handle&&owner.capabilities==capabilities;
        if(epoch==Long.MAX_VALUE)return false;owner=new Key(handle,capabilities,++epoch);return true;
    }
    boolean owns(long handle,Object capabilities){return owner==null||owner.platformHandle==handle&&owner.capabilities==capabilities;}
    boolean attached(){return owner!=null;}Key key(){return owner;}
    void release(boolean allOwnedResourcesGone){if(!allOwnedResourcesGone)throw new IllegalStateException("Owned fused context still has native resources");owner=null;}
}
