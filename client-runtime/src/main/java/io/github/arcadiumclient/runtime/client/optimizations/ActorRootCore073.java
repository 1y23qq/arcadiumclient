package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;
import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRootCore073 {
    public static final int CAPACITY=2048,JOB_BASE=8192,POSE_WORDS=16;
    public interface Backend {
        boolean reserve(ActorRootCore073 core);
        void dirty(int slot);void roster();void pose(Entry entry);
        void endBatch();void invalidate();
    }
    public static final class Entry {
        public Object owner;public State state;public int slot,generation,world;public long seenTick,claimBatch,commitBatch;public boolean alive,retirePending;
        public final float[] pose=new float[16];
    }
    final IdentityHashMap<Object,Entry> owners=new IdentityHashMap<>();
    final Entry[] entries=new Entry[CAPACITY];final int[] generations=new int[CAPACITY],free=new int[CAPACITY];
    final Backend backend;private final Thread owner=Thread.currentThread();private int freeCount=CAPACITY;
    private Object worldObject;private long tick,batch,frame;private float partial;private Frame current;
    int lifetime=1;long inputRevision,rosterRevision;private int claims,pendingRetirements;
    public long reservations,commits,duplicateFallbacks,mathFallbacks,coordinateFallbacks,capacityFallbacks,lateChanges,worldResets,poseBytes,rootMatrixConstructions;
    public ActorRootCore073(Backend backend){this.backend=backend;for(int i=0;i<CAPACITY;i++)free[i]=CAPACITY-1-i;}
    boolean owns(){return Thread.currentThread()==owner;}
    public void world(Object world){if(!owns())return;if(world==worldObject)return;backend.invalidate();worldObject=world;reset();}
    public void invalidate(){if(!owns())return;backend.invalidate();reset();}
    private void reset(){if(lifetime==Integer.MAX_VALUE)throw new IllegalStateException("Root world epoch exhausted");lifetime++;owners.clear();Arrays.fill(entries,null);Arrays.fill(generations,0);freeCount=CAPACITY;claims=pendingRetirements=0;for(int i=0;i<CAPACITY;i++)free[i]=CAPACITY-1-i;current=null;inputRevision++;rosterRevision++;worldResets++;}
    public void beginTick(){if(owns())tick++;}
    public void endTick(){if(!owns())return;for(var iterator=owners.values().iterator();iterator.hasNext();){Entry e=iterator.next();if(e.seenTick==tick)continue;if(e.commitBatch!=0&&e.commitBatch==batch){if(!e.retirePending){e.retirePending=true;pendingRetirements++;}continue;}iterator.remove();retire(e);}}
    public State previous(Object actor){Entry e=owners.get(actor);return e==null?null:e.state;}
    public Entry observe(Object actor,State state){
        if(!owns()||worldObject==null||actor==null||state==null)return null;Entry e=owners.get(actor);
        if(e==null){if(freeCount==0){capacityFallbacks++;return null;}int slot=free[--freeCount];if(generations[slot]==Integer.MAX_VALUE){capacityFallbacks++;free[freeCount++]=slot;return null;}e=new Entry();e.owner=actor;e.slot=slot;e.generation=++generations[slot];e.world=lifetime;e.alive=true;e.state=state;entries[slot]=e;owners.put(actor,e);changed(e);rosterRevision++;backend.roster();}
        e.seenTick=tick;if(e.retirePending){e.retirePending=false;pendingRetirements--;}
        if(!ActorRootInput073.rawEqual(e.state,state)){if(e.commitBatch!=0&&e.commitBatch==batch){lateChanges++;return null;}e.state=state;changed(e);}return e;
    }
    private void changed(Entry e){inputRevision++;backend.dirty(e.slot);}
    private void retire(Entry e){e.alive=false;entries[e.slot]=null;free[freeCount++]=e.slot;inputRevision++;rosterRevision++;backend.dirty(e.slot);backend.roster();}
    public void beginBatch(float delta){if(!owns())return;backend.endBatch();batch++;frame++;if(frame==0)throw new IllegalStateException("Root frame stamp exhausted");partial=delta;current=null;claims=0;if(pendingRetirements!=0){for(var iterator=owners.values().iterator();iterator.hasNext();){Entry e=iterator.next();if(e.retirePending){iterator.remove();retire(e);}}pendingRetirements=0;}}
    public boolean camera(double x,double y,double z){if(!owns()||worldObject==null)return false;if(current==null){current=new Frame(x,y,z,partial,owners.size(),frame,0);return true;}return ActorRootScalar073.raw(current.cameraX(),x)&&ActorRootScalar073.raw(current.cameraY(),y)&&ActorRootScalar073.raw(current.cameraZ(),z);}
    public Frame frame(){if(current!=null&&current.activeCount()!=owners.size())current=new Frame(current.cameraX(),current.cameraY(),current.cameraZ(),current.partial(),owners.size(),current.frameId(),current.mathFlags());return current;}
    public int size(){return owners.size();}
    public long batch(){return batch;}
    public int reserve(Object actor,State actual,double x,double y,double z,float[] pose){
        if(!owns()||current==null||pose.length!=16)return -1;Entry existing=owners.get(actor);if(existing!=null&&existing.claimBatch==batch){duplicateFallbacks++;return -1;}
        Entry e=observe(actor,actual);if(e==null)return -1;State s=e.state;double px=s.previousX()+(s.x()-s.previousX())*(double)partial-current.cameraX(),py=s.previousY()+(s.y()-s.previousY())*(double)partial-current.cameraY(),pz=s.previousZ()+(s.z()-s.previousZ())*(double)partial-current.cameraZ();
        if(!ActorRootScalar073.raw(px,x)||!ActorRootScalar073.raw(pz,z)){coordinateFallbacks++;return -1;}int bias;
        if(ActorRootScalar073.raw(py,y))bias=0;else if(ActorRootScalar073.raw(py-.125d,y))bias=4;else if(ActorRootScalar073.raw((py-.125d)-.125d,y))bias=12;else{coordinateFallbacks++;return -1;}
        if((s.flags()&12)!=bias){s=new State(s.previousX(),s.previousY(),s.previousZ(),s.x(),s.y(),s.z(),s.previousBodyYaw(),s.bodyYaw(),s.height(),(s.flags()&~12)|bias);e.state=s;changed(e);}
        if(ActorRootScalar073.admit(s,current)!=0){mathFallbacks++;return -1;}for(int i=0;i<14;i++)if(!Float.isFinite(pose[i]))return -1;
        if(!backend.reserve(this))return -1;System.arraycopy(pose,0,e.pose,0,16);e.pose[14]=Float.intBitsToFloat(e.generation);e.pose[15]=Float.intBitsToFloat(lifetime);
        try{backend.pose(e);}catch(RuntimeException unavailable){return -1;}e.claimBatch=batch;claims++;reservations++;poseBytes+=64;return JOB_BASE+e.slot;
    }
    public boolean hasJobs(){return claims!=0;}
    public Entry job(int job){int slot=job-JOB_BASE;if(slot<0||slot>=CAPACITY)return null;Entry e=entries[slot];return e!=null&&e.claimBatch==batch&&e.world==lifetime?e:null;}
    public void commit(int job){Entry e=job(job);if(e==null||e.commitBatch==batch)throw new IllegalStateException("Root append must commit exactly once");e.commitBatch=batch;commits++;}
    public void cancel(int job){Entry e=job(job);if(e!=null&&e.commitBatch!=batch){e.claimBatch=0;claims--;}}
    public static boolean rootJob(int job){return job>=JOB_BASE&&job<JOB_BASE+CAPACITY;}
}
