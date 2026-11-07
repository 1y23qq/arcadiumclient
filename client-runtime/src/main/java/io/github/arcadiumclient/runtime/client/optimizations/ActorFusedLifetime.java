// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class ActorFusedLifetime {
    public interface Fence {Object context();long order();boolean signaledWithoutWaiting();void delete();}
    public interface Stores {int create(int words);void delete(int name);}
    public record Key(long world,long batch,long revision,int capacity){}
    private final Stores stores;
    private final Slot[] slots={new Slot(),new Slot(),new Slot()};
    private Object owner;
    public long busyFallbacks,foreignRefusals,uploads,reuses,quarantines;
    public ActorFusedLifetime(Stores stores){this.stores=stores;}
    public Lease acquire(Object context,Key key){
        if(context==null||owner!=null&&owner!=context){foreignRefusals++;return null;}
        if(key.capacity()<1||key.capacity()>8192)return null;
        for(Slot slot:slots)if(!slot.retired&&!slot.uploading&&!slot.quarantined&&key.equals(slot.key)){owner=context;slot.open++;reuses++;return new Lease(this,slot,key,false);}
        for(Slot slot:slots)if(!slot.uploading&&!slot.quarantined&&slot.open==0&&free(slot)){
            if(slot.name!=0&&slot.capacity!=key.capacity()){stores.delete(slot.name);slot.name=0;}
            if(slot.name==0){slot.name=stores.create(key.capacity()*57);if(slot.name==0)return null;slot.capacity=key.capacity();}
            owner=context;slot.key=key;slot.retired=false;slot.uploading=true;slot.open=1;uploads++;return new Lease(this,slot,key,true);
        }
        busyFallbacks++;return null;
    }
    private boolean free(Slot slot){if(slot.fence==null)return true;if(!slot.fence.signaledWithoutWaiting())return false;slot.fence.delete();slot.fence=null;return true;}
    private void owner(Object context){if(owner!=null&&owner!=context){foreignRefusals++;throw new IllegalStateException("Native fused-input stores belong to a different actual context");}}
    public void requestRetireWorld(long world){for(Slot slot:slots)if(slot.key!=null&&slot.key.world()==world)slot.retired=true;}
    public void requestClose(){for(Slot slot:slots)slot.retired=true;}
    public void retireWorld(Object context,long world){owner(context);for(Slot slot:slots)if(slot.key!=null&&slot.key.world()==world)slot.retired=true;collect(context);}
    public void collect(Object context){owner(context);for(Slot slot:slots)if(slot.retired&&slot.open==0&&!slot.quarantined&&free(slot)){if(slot.name!=0)stores.delete(slot.name);slot.name=slot.capacity=0;slot.key=null;}}
    public boolean close(Object context){owner(context);for(Slot slot:slots)slot.retired=true;collect(context);for(Slot slot:slots)if(slot.name!=0)return false;owner=null;return true;}
    public void recoverWithOwnerFence(Object context,int name,Fence fence){
        owner(context);if(fence==null||fence.context()!=context)throw new IllegalArgumentException("A real owning-context completion fence is required");
        for(Slot slot:slots)if(slot.name==name){if(slot.open!=0||slot.fence!=null&&fence.order()<=slot.fence.order())throw new IllegalStateException("Completion fence does not cover all native consumers");if(slot.fence!=null)slot.fence.delete();slot.fence=fence;slot.quarantined=false;slot.retired=true;return;}
        throw new IllegalArgumentException("Unknown retained native store");
    }
    public int retainedStores(){int total=0;for(Slot slot:slots)if(slot.name!=0)total++;return total;}
    private static final class Slot {int name,capacity,open;Key key;Fence fence;boolean uploading,retired,quarantined;}
    public static final class Lease {
        private final ActorFusedLifetime source;private final Slot slot;private final Key key;private boolean dispatched,finished,ready;
        public final boolean uploadRequired;
        Lease(ActorFusedLifetime source,Slot slot,Key key,boolean upload){this.source=source;this.slot=slot;this.key=key;uploadRequired=upload;ready=!upload;}
        public int buffer(){check();return slot.name;}
        private void check(){if(finished||slot.key==null||slot.key!=key&&!slot.key.equals(key))throw new IllegalStateException("Fused-input native lease is stale");}
        public void uploaded(Object context){source.owner(context);check();if(!uploadRequired||!slot.uploading)throw new IllegalStateException("No owned upload is pending");slot.uploading=false;ready=true;}
        public void beforeNativeDispatch(Object context){source.owner(context);check();if(!ready||slot.retired)throw new IllegalStateException("Native input upload is incomplete or its world retired");dispatched=true;}
        public void abandonWithoutGl(){check();if(dispatched){slot.quarantined=true;source.quarantines++;}slot.retired=true;slot.uploading=false;slot.open--;finished=true;}
        public void finish(Object context,Fence afterLastConsumer){
            source.owner(context);check();
            if(dispatched&&afterLastConsumer==null||afterLastConsumer!=null&&(afterLastConsumer.context()!=context||slot.fence!=null&&afterLastConsumer.order()<=slot.fence.order())){
                slot.quarantined=true;slot.uploading=false;slot.open--;finished=true;source.quarantines++;throw new IllegalStateException("The actual native consumer/abort did not provide a new last-read fence");
            }
            if(afterLastConsumer!=null){if(slot.fence!=null)slot.fence.delete();slot.fence=afterLastConsumer;}
            if(uploadRequired&&!ready){slot.key=null;slot.uploading=false;slot.retired=true;}
            slot.open--;finished=true;
        }
    }
}
