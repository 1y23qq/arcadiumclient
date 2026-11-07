package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;

public final class ActorRootProtocol073 {
    public static final class Foreign extends RuntimeException {public Foreign(){super("Persistent root operation retained for its actual owner");}}
    public interface Access {
        Object owner();boolean owns(Object owner);
        int program();int generic();int copyRead();int copyWrite();int buffer(int index);long start(int index);long size(int index);
        void program(int name);void generic(int name);void copyRead(int name);void copyWrite(int name);void base(int index,int name);void range(int index,int name,long start,long size);
        long fence();void deleteFence(long name);
    }
    public static final class FrameHeader {
        private long frame=Long.MIN_VALUE,roster=Long.MIN_VALUE;private int count=-1;
        public boolean needs(long frame,int count,long roster){return this.frame!=frame||this.count!=count||this.roster!=roster;}
        public void uploaded(long frame,int count,long roster){this.frame=frame;this.count=count;this.roster=roster;}
        public void clear(){frame=roster=Long.MIN_VALUE;count=-1;}
    }
    public static final class Caller {
        Object owner;boolean open,deferred;int level;
        public int program,generic,copyRead,copyWrite;public final int[] buffers=new int[8];public final long[] starts=new long[8],sizes=new long[8];
    }
    private final Access access;private final Caller[] frames=new Caller[8];private final ArrayDeque<Caller> pending=new ArrayDeque<>();private int depth;
    public ActorRootProtocol073(Access access){this.access=access;}
    public int depth(){return depth;}
    public int pending(){return pending.size();}
    public void require(){Object owner=access.owner();if(owner==null||!access.owns(owner)||!drain())throw new Foreign();}
    public Caller capture(){
        require();if(depth==frames.length)throw new IllegalStateException("Persistent root caller bound");Caller c=frames[depth];if(c==null)frames[depth]=c=new Caller();if(c.deferred)throw new Foreign();
        Object owner=access.owner();c.owner=owner;c.level=depth;c.program=access.program();c.generic=access.generic();c.copyRead=access.copyRead();c.copyWrite=access.copyWrite();for(int i=0;i<8;i++){guard(owner);c.buffers[i]=access.buffer(i);c.starts[i]=access.start(i);c.sizes[i]=access.size(i);}guard(owner);c.open=true;depth++;return c;
    }
    public boolean finish(Caller caller){
        if(caller==null)return true;if(!caller.open||depth!=caller.level+1)throw new IllegalStateException("Persistent root caller order");caller.open=false;depth--;
        try{if(!drain())throw new Foreign();restore(caller);return true;}catch(Foreign foreign){if(pending.size()==frames.length)throw new IllegalStateException("Persistent root deferred caller bound");caller.deferred=true;pending.addLast(caller);return false;}
    }
    public boolean drain(){while(!pending.isEmpty()){Caller c=pending.peekFirst();try{restore(c);}catch(Foreign foreign){return false;}pending.removeFirst();c.deferred=false;c.owner=null;}return true;}
    private void restore(Caller c){guard(c.owner);for(int i=0;i<8;i++){guard(c.owner);if(c.buffers[i]==0||c.sizes[i]==0)access.base(i,c.buffers[i]);else access.range(i,c.buffers[i],c.starts[i],c.sizes[i]);}guard(c.owner);access.copyRead(c.copyRead);guard(c.owner);access.copyWrite(c.copyWrite);guard(c.owner);access.generic(c.generic);guard(c.owner);access.program(c.program);}
    private void guard(Object owner){if(!access.owns(owner))throw new Foreign();}
    public static final class Fences {
        private long current,pending;
        public long current(){return current;}public long pending(){return pending;}
        public boolean publish(Access access,Object owner){if(!access.owns(owner))return false;if(pending!=0&&!promote(access,owner))return false;long next=access.fence();if(next==0)return false;pending=next;return promote(access,owner);}
        public boolean recover(Access access,Object owner){if(!access.owns(owner))return false;return pending!=0?promote(access,owner):publish(access,owner);}
        private boolean promote(Access access,Object owner){if(!access.owns(owner))return false;if(current!=0){access.deleteFence(current);current=0;}if(!access.owns(owner))return false;current=pending;pending=0;return true;}
        public void deleteCurrent(Access access,Object owner){if(current==0)return;if(!access.owns(owner))throw new Foreign();access.deleteFence(current);current=0;}
    }
}
