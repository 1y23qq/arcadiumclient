// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ActorSceneContext {
    private Object owner;
    private boolean capability;
    private long epoch;
    boolean accept(Object current,boolean available){
        if(current==null)return false;
        if(owner==null){if(epoch==Long.MAX_VALUE)throw new IllegalStateException("Actor context epoch exhausted");owner=current;capability=available;epoch++;}
        return owner==current&&capability;
    }
    boolean owns(Object current){return owner==null||owner==current;}
    boolean attached(){return owner!=null;}
    long epoch(){return epoch;}
    void release(Object current){
        if(owner!=null&&owner!=current)throw new IllegalStateException("Actor scene teardown requires its actual owning GL context");
        if(owner!=null){owner=null;capability=false;if(epoch==Long.MAX_VALUE)throw new IllegalStateException("Actor context epoch exhausted");epoch++;}
    }
}
