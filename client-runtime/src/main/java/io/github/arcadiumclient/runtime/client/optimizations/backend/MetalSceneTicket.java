// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.IntSupplier;

final class MetalSceneTicket implements AutoCloseable {
    interface Main { int call(IntSupplier operation); }
    private enum Phase { IDLE, PREPARING, PREPARED, SUBMITTED, UNCERTAIN, CLOSED }
    private final Thread owner=Thread.currentThread();
    private final CurrentFrameCalls calls;
    private final Main main;
    private final Arena memory;
    private final MemorySegment descriptor;
    private Phase phase=Phase.IDLE;
    private MetalDeviceLease.Target pending;
    private long frame,lastPrepared;
    private boolean busy;

    MetalSceneTicket(CurrentFrameCalls calls,Main main){
        this.calls=Objects.requireNonNull(calls);this.main=Objects.requireNonNull(main);
        if(!calls.reserveSceneOwner(this))throw new IllegalStateException("The presentation bridge already has a scene ticket owner");
        Arena arena=null;
        try{arena=Arena.ofConfined();descriptor=arena.allocate(MetalPresentFrame.BYTES,8);memory=arena;}
        catch(RuntimeException|Error failure){if(arena!=null)arena.close();calls.releaseSceneOwner(this);throw failure;}
    }
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Scene tickets belong to their render owner");}
    private void enter(){owner();if(busy||phase==Phase.CLOSED)throw new IllegalStateException("The scene ticket is closed or reentrant");busy=true;}
    boolean prepare(MetalDeviceLease.Target target,Runnable submitExternal) {
        enter();
        try {
            if(phase==Phase.UNCERTAIN)throw new IllegalStateException("Cancel the uncertain scene ticket before another handoff");
            if(!calls.supportsSceneInput()||!calls.attached())return false;
            if(phase==Phase.PREPARED) {
                if(pending!=target)throw new IllegalStateException("A different scene ticket is already prepared");
            } else {
                long id=target.frame();if(id<=lastPrepared)throw new IllegalArgumentException("Scene ticket frame IDs must increase");
                MetalPresentFrame.writeScene(descriptor,id,target.width(),target.height());frame=id;pending=target;phase=Phase.PREPARING;
            }
            int result;
            try {
                result=target.handoff(this,submitExternal,token->{int code=calls.prepareScene(token,descriptor);if(code==0){lastPrepared=frame;phase=Phase.PREPARED;}return code;});
            }catch(RuntimeException|Error failure){if(phase!=Phase.PREPARED)phase=Phase.UNCERTAIN;throw failure;}
            if(result!=0){phase=Phase.IDLE;pending=null;return false;}
            phase=Phase.PREPARED;return true;
        } finally{busy=false;}
    }
    boolean publish() {
        enter();
        try {
            if(phase!=Phase.PREPARED||pending==null||!pending.handedOffTo(this))throw new IllegalStateException("Detach the exact prepared source handle before publishing");
            try {
                int result=main.call(()->calls.publish(frame));
                if(result!=0){phase=Phase.UNCERTAIN;return false;}
                phase=Phase.SUBMITTED;pending=null;return true;
            }catch(RuntimeException|Error failure){phase=Phase.UNCERTAIN;throw failure;}
        } finally{busy=false;}
    }
    boolean completed(){owner();return phase==Phase.SUBMITTED&&calls.query(84)==0&&calls.query(1)==frame&&calls.query(2)==frame&&calls.query(3)==frame;}
    boolean displayed(){return completed()&&calls.query(4)==frame;}
    void cancel(){enter();try{cancelInternal();}finally{busy=false;}}
    private void cancelInternal() {
        phase=Phase.UNCERTAIN;
        if(calls.attached()&&main.call(calls::hide)!=0)throw new IllegalStateException("The scene claim must be hidden/retired before fallback");
        if(pending!=null)pending.close();pending=null;phase=Phase.IDLE;
    }
    @Override public void close() {
        owner();if(phase==Phase.CLOSED)return;enter();
        try{cancelInternal();memory.close();phase=Phase.CLOSED;calls.releaseSceneOwner(this);}finally{busy=false;}
    }
}
