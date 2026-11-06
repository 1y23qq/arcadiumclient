// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.util.ArrayList;
import java.util.Objects;

public final class MetalDeviceLease implements AutoCloseable {
    @FunctionalInterface public interface DeviceFactory<T extends AutoCloseable> { T create(long device,long queue); }
    @FunctionalInterface public interface TargetFactory<T extends AutoCloseable> { T create(long texture,int width,int height); }
    record Acquisition(int status,long handle) {}
    record Info(long frame,long generation,int width,int height,long device,long queue,long texture) {}
    record InfoResult(int status,Info info) {}
    interface Calls {
        void ownerLive();
        Acquisition acquire(int kind,long frame);
        InfoResult info(long handle);
        int release(long handle);
    }
    private final Thread owner=Thread.currentThread();
    private final Calls calls;
    private final Children children=new Children();
    private long handle;
    private Info info;
    private Target target;
    private boolean opened,closed,creating,closing,retiring;

    MetalDeviceLease(Calls calls){this.calls=Objects.requireNonNull(calls);}
    void open() {
        ownerLive();if(opened)throw new IllegalStateException("The device lease was already opened");opened=true;
        Acquisition acquired=calls.acquire(1,0);handle=acquired.handle();accepted(acquired.status(),handle!=0,"acquire device");
        InfoResult result=calls.info(handle);accepted(result.status(),result.info()!=null,"read device");
        Info candidate=result.info();
        accepted(0,candidate.device()!=0&&candidate.queue()!=0&&candidate.texture()==0&&candidate.frame()==0
                &&candidate.generation()==0&&candidate.width()==0&&candidate.height()==0,"validate device objects");info=candidate;
    }
    private void ownerLive(){owner();calls.ownerLive();}
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Metal leases belong to their render thread");}
    private void live(){ownerLive();if(!opened||closed||handle==0||info==null)throw new IllegalStateException("The device lease is not open");}
    boolean blocksBackend(){return !closed&&(target!=null||creating||closing||retiring);}
    public boolean isClosed(){return closed;}

    public <T extends AutoCloseable> T createResource(DeviceFactory<T> factory) {
        live();if(creating||closing||retiring)throw new IllegalStateException("Reentrant or retiring device resource creation");children.capacity();
        creating=true;
        try{return own(children,Objects.requireNonNull(factory).create(info.device(),info.queue()));}
        finally{creating=false;}
    }
    public Target target(long frame) {
        live();if(creating||closing||retiring||target!=null)throw new IllegalStateException("The device already owns an active target scope");
        if(frame<=0)throw new IllegalArgumentException("A target requires a submitted frame");
        Target candidate=new Target(frame);target=candidate;
        try{candidate.open();return candidate;}
        catch(RuntimeException|Error failure){try{candidate.close();}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public void close() {
        owner();if(closed)return;ownerLive();if(creating||closing||target!=null&&(target.creating||target.closing))throw new IllegalStateException("Reentrant device lease close");
        closing=true;retiring=true;
        try {
            if(target!=null)target.close();children.close();
            if(handle!=0)accepted(calls.release(handle),true,"release device");
            handle=0;closed=true;
        } finally{closing=false;}
    }

    public final class Target implements AutoCloseable {
        private final long frame;
        private final Children resources=new Children();
        private long token;
        private Info snapshot;
        private boolean closed,creating,closing,retiring;
        private Target(long frame){this.frame=frame;}
        private void open() {
            Acquisition acquired=calls.acquire(2,frame);token=acquired.handle();accepted(acquired.status(),token!=0,"acquire frame target");
            InfoResult result=calls.info(token);accepted(result.status(),result.info()!=null,"read frame target");
            Info value=result.info();
            accepted(0,value.frame()==frame&&value.generation()>0&&value.texture()!=0&&value.device()==info.device()
                    &&value.queue()==info.queue(),"validate frame target identity");
            SceneBufferChecks.rgbaBytes(value.width(),value.height());snapshot=value;
        }
        private void live(){MetalDeviceLease.this.live();if(closed||token==0||snapshot==null||target!=this)throw new IllegalStateException("The target scope is not open");}
        public long frame(){live();return snapshot.frame();}
        public long generation(){live();return snapshot.generation();}
        public int width(){live();return snapshot.width();}
        public int height(){live();return snapshot.height();}
        public <T extends AutoCloseable> T createResource(TargetFactory<T> factory) {
            live();if(creating||closing||retiring||MetalDeviceLease.this.creating||MetalDeviceLease.this.closing||MetalDeviceLease.this.retiring)throw new IllegalStateException("Reentrant or retiring target resource creation");
            resources.capacity();creating=true;
            try{return own(resources,Objects.requireNonNull(factory).create(snapshot.texture(),snapshot.width(),snapshot.height()));}
            finally{creating=false;}
        }
        @Override public void close() {
            owner();if(closed)return;ownerLive();if(creating||closing)throw new IllegalStateException("Reentrant target close");
            closing=true;retiring=true;
            try {
                resources.close();if(token!=0)accepted(calls.release(token),true,"retire frame target");
                token=0;closed=true;if(target==this)target=null;
            } finally{closing=false;}
        }
    }
    private static void accepted(int status,boolean valid,String action) {
        if(status!=0||!valid)throw new IllegalStateException("Metal lease could not "+action+" (status "+status+")");
    }
    private <T extends AutoCloseable> T own(Children scope,T value) {
        if(value==null)throw new IllegalStateException("The Metal resource factory returned no resource");
        if(children.owns(value)||target!=null&&target.resources.owns(value))
            throw new IllegalArgumentException("The resource is already owned by a Metal scope");
        scope.values.add(value);return value;
    }
    private static final class Children {
        private final ArrayList<AutoCloseable> values=new ArrayList<>(8);
        void capacity(){if(values.size()>=8)throw new IllegalStateException("A Metal lease supports at most eight child resources");}
        boolean owns(AutoCloseable value){for(AutoCloseable child:values)if(child==value)return true;return false;}
        void close() {
            while(!values.isEmpty()) {
                AutoCloseable value=values.getLast();
                try{value.close();}catch(RuntimeException|Error failure){throw failure;}
                catch(Exception failure){throw new IllegalStateException("A Metal child resource could not close",failure);}
                values.removeLast();
            }
        }
    }
}
