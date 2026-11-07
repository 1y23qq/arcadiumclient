package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.sdl.SDLVideo;
import static io.github.arcadiumclient.runtime.client.optimizations.ActorRootInput073.*;

public final class ActorRootGl073 implements ActorRootCore073.Backend {
    private static final int CAP=ActorRootCore073.CAPACITY,OUT_WORDS=4+CAP*48,POSE_AT=4+CAP*32;
    private static final class Store {
        final int[] names=new int[4],dirty=new int[CAP];final boolean[] pending=new boolean[CAP];final BitSet poses=new BitSet(CAP);
        int dirtyCount;long roster=-1,frame=-1,revision=-1,batch=-1;boolean retiring,poisoned,workIssued;
        final ActorRootProtocol073.FrameHeader header=new ActorRootProtocol073.FrameHeader();final ActorRootProtocol073.Fences fences=new ActorRootProtocol073.Fences();
    }
    private final Store[] stores={new Store(),new Store(),new Store()};private final Scope[] scopes=new Scope[4];
    private final ByteBuffer actor=BufferUtils.createByteBuffer(80).order(ByteOrder.LITTLE_ENDIAN),frame=BufferUtils.createByteBuffer(48).order(ByteOrder.LITTLE_ENDIAN),members=BufferUtils.createByteBuffer(CAP*16).order(ByteOrder.LITTLE_ENDIAN),pose=BufferUtils.createByteBuffer(CAP*64).order(ByteOrder.LITTLE_ENDIAN);
    private Thread thread;private Object caps;private long context;private int rootProgram,modelProgram,dummy,cursor,depth;private Store active;private boolean retiring,failed,disabled;
    public long actorBytes,frameBytes,poseBytes,membershipBytes,dispatches,storeAllocations,busyFallbacks,foreignFallbacks,sourceRefusals,scopesOpened,readerFences,declaredMatrixQueries;
    private Object ownerKey;private final int[] deferredShaders=new int[4],deferredBuffers=new int[4];private int deferredShaderCount,deferredBufferCount,deferredRootShader,deferredRootProgram;
    private final NativeAccess access=new NativeAccess();private final ActorRootProtocol073 callers=new ActorRootProtocol073(access);
    private final class NativeAccess implements ActorRootProtocol073.Access {
        public Object owner(){return ownerKey;}public boolean owns(Object key){return key!=null&&key==ownerKey&&current();}
        private void check(){if(!current())throw new ActorRootProtocol073.Foreign();}
        public int program(){check();return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);}public int generic(){check();return GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);}public int copyRead(){check();return GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);}public int copyWrite(){check();return GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);}
        public int buffer(int i){check();return GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);}public long start(int i){check();return GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);}public long size(int i){check();return GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        public void program(int name){check();GL20.glUseProgram(name);}public void generic(int name){check();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,name);}public void copyRead(int name){check();GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,name);}public void copyWrite(int name){check();GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,name);}public void base(int i,int name){check();GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,name);}public void range(int i,int name,long start,long size){check();GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,name,start,size);}
        public long fence(){check();return GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);}public void deleteFence(long name){check();GL32.glDeleteSync(name);}
    }
    public void requireOwner(){if(context==0){long key=actual();if(key==0)throw new ActorRootProtocol073.Foreign();context=key;caps=GL.getCapabilities();thread=Thread.currentThread();ownerKey=new Object();}callers.require();deleteDeferred();}
    public boolean ownsOwner(){return context==0||current();}
    public ActorRootProtocol073.Caller captureCaller(){requireOwner();return callers.capture();}
    public boolean finishCaller(ActorRootProtocol073.Caller caller){return callers.finish(caller);}
    public void deferShader(int shader){if(shader==0)return;for(int i=0;i<deferredShaderCount;i++)if(deferredShaders[i]==shader)return;if(deferredShaderCount==deferredShaders.length)throw new IllegalStateException("Root deferred shader bound");deferredShaders[deferredShaderCount++]=shader;}
    public void deferBuffer(int buffer){if(buffer==0)return;for(int i=0;i<deferredBufferCount;i++)if(deferredBuffers[i]==buffer)return;if(deferredBufferCount==deferredBuffers.length)throw new IllegalStateException("Root deferred buffer bound");deferredBuffers[deferredBufferCount++]=buffer;}
    private void deleteDeferred(){if(!current())throw new ActorRootProtocol073.Foreign();for(int i=0;i<deferredShaderCount;i++){if(!current())throw new ActorRootProtocol073.Foreign();if(deferredShaders[i]!=0)GL20.glDeleteShader(deferredShaders[i]);deferredShaders[i]=0;}deferredShaderCount=0;for(int i=0;i<deferredBufferCount;i++){if(!current())throw new ActorRootProtocol073.Foreign();if(deferredBuffers[i]!=0)GL15.glDeleteBuffers(deferredBuffers[i]);deferredBuffers[i]=0;}deferredBufferCount=0;if(deferredRootShader!=0){if(!current())throw new ActorRootProtocol073.Foreign();GL20.glDeleteShader(deferredRootShader);deferredRootShader=0;}if(deferredRootProgram!=0){if(!current())throw new ActorRootProtocol073.Foreign();GL20.glDeleteProgram(deferredRootProgram);deferredRootProgram=0;}}
    private static long actual(){return SDLVideo.SDL_GL_GetCurrentContext();}
    private boolean current(){try{return thread==Thread.currentThread()&&context!=0&&actual()==context&&GL.getCapabilities()==caps;}catch(Throwable unavailable){return false;}}
    public boolean linked(int program,String source){
        modelProgram=0;if(!ActorRoot073.enabled())return false;
        try{requireOwner();long handle=actual();Object capability=GL.getCapabilities();if(handle==0||!GL.getCapabilities().OpenGL43||!(GL.getCapabilities().OpenGL40||GL.getCapabilities().GL_ARB_gpu_shader_fp64))return false;
            if(!current()){foreignFallbacks++;throw new ActorRootProtocol073.Foreign();}collect();
            IntBuffer attached=BufferUtils.createIntBuffer(2),count=BufferUtils.createIntBuffer(1);GL20.glGetAttachedShaders(program,count,attached);if(count.get(0)!=1||!ActorRootSource073.sha(GL20.glGetShaderSource(attached.get(0))).equals(source))return false;
            int index=GL43.glGetProgramResourceIndex(program,GL43.GL_SHADER_STORAGE_BLOCK,"ArcPersistentRootData");if(index==GL43.GL_INVALID_INDEX)return false;
            IntBuffer key=BufferUtils.createIntBuffer(1).put(GL43.GL_BUFFER_BINDING).flip(),value=BufferUtils.createIntBuffer(1);GL43.glGetProgramResourceiv(program,GL43.GL_SHADER_STORAGE_BLOCK,index,key,null,value);if(value.get(0)!=7)return false;
            if(GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)<8||GL11.glGetInteger(GL43.GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS)<7||GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE)<(long)OUT_WORDS*4)return false;
            if(dummy==0){int old=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);try{dummy=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,dummy);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,16L,GL15.GL_STATIC_DRAW);}finally{if(!current())throw new ActorRootProtocol073.Foreign();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,old);}}
            modelProgram=program;disabled=false;return true;
        }catch(Throwable unsupported){failed=true;if(!current())throw new ActorRootProtocol073.Foreign();return false;}
    }
    public void dirty(int slot){for(Store s:stores)if(!s.pending[slot]){s.pending[slot]=true;s.dirty[s.dirtyCount++]=slot;}}
    public void roster(){for(Store s:stores)s.roster=-1;}
    public void pose(ActorRootCore073.Entry e){if(active==null||!current())throw new IllegalStateException("Root owner changed before reservation");active.poses.set(e.slot);}
    public boolean reserve(ActorRootCore073 core){
        if(!current()){foreignFallbacks++;throw new ActorRootProtocol073.Foreign();}if(modelProgram==0||failed||disabled){sourceRefusals++;return false;}collect();if(retiring)return false;
        if(active!=null&&active.batch==core.batch())return true;
        try{ensureProgram();for(int step=0;step<stores.length;step++){Store candidate=stores[(cursor+step)%stores.length];if(candidate.retiring||candidate.poisoned||!free(candidate))continue;if(candidate.names[0]==0)allocate(candidate,core);active=candidate;active.batch=core.batch();cursor=(cursor+step+1)%stores.length;return true;}busyFallbacks++;return false;}
        catch(Throwable allocation){failed=true;if(!current())throw new ActorRootProtocol073.Foreign();return false;}
    }
    private void ensureProgram()throws Exception{
        if(rootProgram!=0)return;String source;try(var in=ActorRootGl073.class.getResourceAsStream("/assets/arcadium/shaders/persistent_actor_root_073.comp")){if(in==null)throw new IllegalStateException("Missing owned root kernel");source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}if(!ActorRootSource073.sha(source).equals(ActorRoot073.KERNEL_SHA))throw new IllegalStateException("Unknown root kernel");
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),program=GL20.glCreateProgram();try{GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));rootProgram=program;}finally{if(current()){GL20.glDeleteShader(shader);if(rootProgram!=program)GL20.glDeleteProgram(program);}else{deferredRootShader=shader;if(rootProgram!=program)deferredRootProgram=program;failed=true;throw new ActorRootProtocol073.Foreign();}}
    }
    private void allocate(Store s,ActorRootCore073 core){
        int old=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);long[] bytes={CAP*80L,CAP*16L,48L,OUT_WORDS*4L};
        try{for(int i=0;i<4;i++){s.names[i]=GL15.glGenBuffers();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,s.names[i]);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,bytes[i],GL15.GL_DYNAMIC_DRAW);if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=bytes[i])throw new IllegalStateException("Root store extent");}for(var e:core.entries)if(e!=null&&!s.pending[e.slot]){s.pending[e.slot]=true;s.dirty[s.dirtyCount++]=e.slot;}storeAllocations++;}
        catch(Throwable partial){if(!current()){s.retiring=s.poisoned=true;retiring=true;throw new ActorRootProtocol073.Foreign();}for(int i=0;i<4;i++)if(s.names[i]!=0){GL15.glDeleteBuffers(s.names[i]);s.names[i]=0;}throw partial;}
        finally{if(!current())throw new ActorRootProtocol073.Foreign();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,old);}
    }
    private boolean free(Store s){requireOwner();if(s.fences.current()==0)return true;int result=GL32.glClientWaitSync(s.fences.current(),0,0);if(result==GL32.GL_TIMEOUT_EXPIRED)return false;if(result!=GL32.GL_ALREADY_SIGNALED&&result!=GL32.GL_CONDITION_SATISFIED)throw new IllegalStateException("Root zero-wait fence");s.fences.deleteCurrent(access,ownerKey);return true;}
    public Scope bind(ActorRootCore073 core){
        if(modelProgram==0)return null;if(!current())throw new ActorRootProtocol073.Foreign();collect();if(depth==scopes.length)throw new IllegalStateException("Root scope bound");
        Scope scope=scopes[depth];if(scope==null)scopes[depth]=scope=new Scope();scope.open(depth,core.hasJobs()?active:null);depth++;
        try{if(core.hasJobs()){if(active==null||active.batch!=core.batch()||retiring||failed||disabled)throw new IllegalStateException("Current root batch not reserved");prepare(core,active);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,7,active.names[3]);}else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,7,dummy);scopesOpened++;return scope;}catch(Throwable error){scope.close();throw error;}
    }
    private void prepare(ActorRootCore073 core,Store s){
        if(!current())throw new ActorRootProtocol073.Foreign();
        if(!s.header.needs(core.frame().frameId(),core.size(),core.rosterRevision)&&s.revision==core.inputRevision&&s.roster==core.rosterRevision&&s.dirtyCount==0&&s.poses.isEmpty())return;
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldGeneric=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);int[] names=new int[4];long[] starts=new long[4],sizes=new long[4];
        for(int i=0;i<4;i++){names[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);starts[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);sizes[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        try{
            boolean roots=s.frame!=core.frame().frameId()||s.revision!=core.inputRevision||s.roster!=core.rosterRevision;
            if(s.dirtyCount!=0){GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,s.names[0]);for(int i=0;i<s.dirtyCount;i++){int slot=s.dirty[i];var e=core.entries[slot];actor.clear();ActorRootInput073.encode(actor,e==null?1:e.generation,core.lifetime,e!=null,e==null?null:e.state);actor.flip();GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,(long)slot*80,actor);actorBytes+=80;}for(int i=0;i<s.dirtyCount;i++)s.pending[s.dirty[i]]=false;s.dirtyCount=0;}
            if(s.roster!=core.rosterRevision){members.clear();for(var e:core.entries)if(e!=null)members.putInt(e.slot).putInt(e.generation).putInt(e.world).putInt(0);members.flip();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,s.names[1]);if(members.hasRemaining())GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0L,members);membershipBytes+=(long)core.size()*16;s.roster=core.rosterRevision;}
            if(s.header.needs(core.frame().frameId(),core.size(),core.rosterRevision)){frame.clear();ActorRootInput073.encodeFrame(frame,core.frame(),CAP);frame.flip();GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,s.names[2]);GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0L,frame);frameBytes+=48;s.header.uploaded(core.frame().frameId(),core.size(),core.rosterRevision);}
            for(int slot=s.poses.nextSetBit(0);slot>=0;slot=s.poses.nextSetBit(slot+1))if(core.entries[slot]==null||core.entries[slot].claimBatch!=core.batch())s.poses.clear(slot);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,s.names[3]);for(int slot=s.poses.nextSetBit(0);slot>=0;){int end=s.poses.nextClearBit(slot);pose.clear();for(int n=slot;n<end;n++){var entry=core.entries[n];for(int word=0;word<14;word++)pose.putFloat(entry.pose[word]);pose.putInt(entry.generation).putInt(entry.world);}pose.flip();GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,(long)(POSE_AT+slot*16)*4,pose);poseBytes+=(long)(end-slot)*64;s.poses.clear(slot,end);slot=s.poses.nextSetBit(end);}
            if(roots){for(int i=0;i<4;i++)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,s.names[i]);GL20.glUseProgram(rootProgram);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);s.workIssued=true;GL43.glDispatchCompute(Math.max(1,(core.size()+63)/64),1,1);GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);if(GL11.glGetError()!=0)throw new IllegalStateException("Root dispatch failed before model expansion");dispatches++;s.frame=core.frame().frameId();s.revision=core.inputRevision;}
        }finally{if(!current()){failed=true;s.retiring=s.poisoned=true;retiring=true;throw new ActorRootProtocol073.Foreign();}for(int i=0;i<4;i++)restore(i,names[i],starts[i],sizes[i]);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,oldGeneric);GL20.glUseProgram(oldProgram);}
    }
    public void endBatch(){active=null;if(current())collect();}
    public void invalidate(){retiring=true;active=null;for(Store s:stores)s.retiring=true;if(current())collect();}
    private void collect(){
        if(!current()||depth!=0||callers.depth()!=0)return;if(!callers.drain())return;deleteDeferred();boolean pending=false;for(Store s:stores)if(s.retiring){if(s.poisoned){if(!s.fences.recover(access,ownerKey)){pending=true;continue;}s.poisoned=false;s.workIssued=false;}if(!free(s)){pending=true;continue;}for(int i=0;i<4;i++)if(s.names[i]!=0){if(!current())throw new ActorRootProtocol073.Foreign();GL15.glDeleteBuffers(s.names[i]);s.names[i]=0;}s.retiring=false;s.workIssued=false;s.header.clear();s.frame=s.revision=s.roster=-1;s.poses.clear();s.dirtyCount=0;Arrays.fill(s.pending,false);}
        if(retiring&&!pending){if(rootProgram!=0)GL20.glDeleteProgram(rootProgram);rootProgram=0;retiring=failed=false;}
    }
    public void close(){requireOwner();if(callers.depth()!=0||depth!=0)throw new IllegalStateException("Root close while a native caller is active");modelProgram=0;invalidate();if(current()&&!retiring&&depth==0&&dummy!=0){GL15.glDeleteBuffers(dummy);dummy=0;}}
    public void disablePrivate(){disabled=true;retiring=true;for(Store s:stores)s.retiring=true;if(current())collect();}
    public int nativeBuffers(){int count=dummy==0?0:1;for(Store store:stores)for(int name:store.names)if(name!=0)count++;return count;}
    public long retainedBytes(){long bytes=dummy==0?0:16;for(Store store:stores)if(store.names[0]!=0)bytes+=CAP*80L+CAP*16L+48L+OUT_WORDS*4L;return bytes;}
    public final class Scope implements AutoCloseable {
        private int level;private Store used;private boolean open,attempted;
        void open(int level,Store used){if(callers.depth()==0)throw new IllegalStateException("Root binding requires its full caller scope");this.level=level;this.used=used;open=true;attempted=false;}
        public void beforeDispatch(){if(!open||!current())throw new ActorRootProtocol073.Foreign();if(used!=null&&(used.retiring||used.poisoned)||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=modelProgram)throw new ActorRoot073.Failure(new IllegalStateException("Root model owner/program changed"));attempted=true;}
        public void close(){try{closeOwned();}catch(RuntimeException failure){throw failure instanceof ActorRootProtocol073.Foreign||failure instanceof ActorRoot073.Failure?failure:new ActorRoot073.Failure(failure);}}
        private void closeOwned(){if(!open)return;if(depth!=level+1)throw new IllegalStateException("Root scopes closed out of order");try{if(!current()){failed=true;if(used!=null&&(attempted||used.workIssued)){used.retiring=used.poisoned=true;retiring=true;}return;}if(used!=null&&(attempted||used.workIssued)){if(!used.fences.publish(access,ownerKey)){failed=true;used.retiring=used.poisoned=true;retiring=true;if(!current())throw new ActorRootProtocol073.Foreign();throw new IllegalStateException("Root last-reader fence unavailable");}used.workIssued=false;readerFences++;}}finally{open=false;used=null;depth--;}}
    }
    private void restore(int index,int buffer,long start,long size){if(!current())throw new ActorRootProtocol073.Foreign();if(buffer==0||size==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,index,buffer);else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,index,buffer,start,size);}
}
