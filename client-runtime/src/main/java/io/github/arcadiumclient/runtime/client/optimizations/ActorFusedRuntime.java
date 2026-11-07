// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.Platform;

final class ActorFusedRuntime {
    private record Program(int name,Object link,ActorFusedContext.Key owner,int countUniform,int metadataUniform,String sourceHash){}
    private static final ActorFusedContext context=new ActorFusedContext();
    private static final Stores stores=new Stores();private static final ActorFusedLifetime lifetime=new ActorFusedLifetime(stores);
    private static final ActorFusedInput input=new ActorFusedInput();
    private static final Scope[] scopes=new Scope[8];
    private static final int[] recover=new int[3];
    private static Thread thread;private static int depth,recoverCount;private static long fenceOrder;
    private static boolean limitsChecked,limitsAccepted,frameReady,failed;
    private static Program program;
    private static Object currentModelLink;
    private record DeferredUniform(Program program,int count,int metadata){}
    private record DeferredCaller(int program,int generic,int[] buffers,long[] starts,long[] sizes){}
    private record DeferredCopy(int read,int write){}
    private record DeferredSort(int program,Object link,int location,int count,int previousProgram){}
    private static final List<DeferredUniform> deferredUniforms=new ArrayList<>();
    private static final List<DeferredCaller> deferredCallers=new ArrayList<>();
    private static final List<DeferredCopy> deferredCopies=new ArrayList<>();
    private static final List<DeferredSort> deferredSorts=new ArrayList<>();
    private static final Set<Integer> deferredShaders=new LinkedHashSet<>();
    private static ActorFusedInput.View cachedView;private static int[] cachedRows=new int[0];private static int cachedCount;
    private static long cachedWorld,cachedBatch,cachedRaw,cachedPackets;
    static long fusedDispatches,fallbackDispatches,fusedRows,fallbackRows,uploadedWords,differentialReuses,foreignRefusals,quarantines;
    static long uploadCalls,committedDirtyWords,transmittedWords,shadowComparedWords,committedViewReuses;
    private ActorFusedRuntime(){}
    private static long actual(){if(Platform.get()!=Platform.WINDOWS)return 0;return WGL.wglGetCurrentContext(null);}
    private static Object capabilities(){return GL.getCapabilities();}
    static boolean ownsCurrent(){if(!context.attached())return true;try{return context.owns(actual(),capabilities());}catch(Throwable unavailable){return false;}}
    static void requireCurrent(){if(!ownsCurrent()){foreignRefusals++;throw new IllegalStateException("Fused actor native objects belong to another actual platform context");}}
    static boolean prepareFrame(){
        frameReady=false;if(!ActorFusedMode.FUSED||Platform.get()!=Platform.WINDOWS)return false;
        try{
            if(thread!=null&&thread!=Thread.currentThread())return false;var cap=GL.getCapabilities();
            if(!context.accept(actual(),cap,cap.OpenGL43)){foreignRefusals++;return false;}thread=Thread.currentThread();
            if(!limitsChecked){limitsChecked=true;limitsAccepted=GL11.glGetInteger(GL43.GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS)>=7&&GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)>=7&&GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE)>=8192L*57*4;}
            if(!limitsAccepted)return false;
            deleteDeferredShaders();
            for(var saved:deferredUniforms)if(program==saved.program&&GL20.glIsProgram(saved.program.name)){GL20.glUseProgram(saved.program.name);GL20.glUniform1i(saved.program.countUniform,saved.count);if(saved.program.metadataUniform>=0)GL30.glUniform1ui(saved.program.metadataUniform,saved.metadata);}deferredUniforms.clear();
            for(var saved:deferredSorts)if(currentModelLink==saved.link&&GL20.glIsProgram(saved.program)){GL20.glUseProgram(saved.program);GL20.glUniform1i(saved.location,saved.count);GL20.glUseProgram(saved.previousProgram);}deferredSorts.clear();
            for(var saved:deferredCopies){GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,saved.read);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,saved.write);}deferredCopies.clear();
            for(var saved:deferredCallers){for(int i=0;i<7;i++){int name=saved.buffers[i];if(name!=0&&saved.sizes[i]>0)GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,name,saved.starts[i],saved.sizes[i]);else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,name);}GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,saved.generic);GL20.glUseProgram(saved.program);}deferredCallers.clear();
            for(int i=0;i<recoverCount;i++)lifetime.recoverWithOwnerFence(context.key(),recover[i],new Fence());recoverCount=0;lifetime.collect(context.key());frameReady=!failed;return frameReady;
        }catch(Throwable failure){failed=true;return false;}
    }
    static boolean frameReady(){return frameReady&&!failed;}
    static boolean qualifiedFused(){return program!=null&&program.metadataUniform>=0;}
    static boolean qualifiedOrdinary(){return program!=null&&program.metadataUniform<0;}
    static boolean privateFailed(){return failed;}
    static long contextEpoch(){return context.attached()?context.key().epoch():0;}
    static long bytes(){return stores.bytes;}
    static long busyFallbacks(){return lifetime.busyFallbacks;}
    static int nativeStores(){return lifetime.retainedStores();}
    static int scopeDepth(){return depth;}
    static boolean canPatch(){return ActorFusedMode.FUSED&&(frameReady||prepareFrame());}
    static void disableFusion(){failed=true;frameReady=false;program=null;cachedView=null;}
    static boolean linked(int name,Object link,int countUniform,ActorFusedShader.Result source){
        requireCurrent();program=null;if(!canPatch()||source==null||!source.accepted()||!source.fused()||link==null)return false;
        int metadata=GL20.glGetUniformLocation(name,"arcActorMetadataBase");
        if(!GL20.glIsProgram(name)||GL20.glGetProgrami(name,GL20.GL_LINK_STATUS)==0||countUniform<0||metadata<0||GL43.glGetProgramInterfacei(name,GL43.GL_SHADER_STORAGE_BLOCK,GL43.GL_ACTIVE_RESOURCES)!=7||property(name,GL43.GL_UNIFORM,"instanceCount",GL43.GL_TYPE)!=GL11.GL_INT||property(name,GL43.GL_UNIFORM,"arcActorMetadataBase",GL43.GL_TYPE)!=GL11.GL_UNSIGNED_INT||binding(name,"Players")!=3||binding(name,"ArcActorInputBits")!=3||binding(name,"Templates")!=4||binding(name,"Sines")!=5||binding(name,"NativePoses")!=6)return false;
        try{IntBuffer count=BufferUtils.createIntBuffer(1),attached=BufferUtils.createIntBuffer(2),workgroup=BufferUtils.createIntBuffer(3);GL20.glGetAttachedShaders(name,count,attached);GL20.glGetProgramiv(name,GL43.GL_COMPUTE_WORK_GROUP_SIZE,workgroup);String hash=ActorFusedShader.sha(source.source());if(count.get(0)!=1||GL20.glGetShaderi(attached.get(0),GL20.GL_SHADER_TYPE)!=GL43.GL_COMPUTE_SHADER||workgroup.get(0)!=64||workgroup.get(1)!=1||workgroup.get(2)!=1||!hash.equals(ActorFusedShader.sha(GL20.glGetShaderSource(attached.get(0)))))return false;program=new Program(name,link,context.key(),countUniform,metadata,hash);return true;}catch(Exception invalid){failed=true;return false;}
    }
    static void modelLinked(Object link){currentModelLink=link;}
    static void ordinaryLinked(int name,Object link,int count){
        if(!context.attached()||!ownsCurrent()||link==null||count<0)return;
        try{IntBuffer n=BufferUtils.createIntBuffer(1),shaders=BufferUtils.createIntBuffer(2);GL20.glGetAttachedShaders(name,n,shaders);if(n.get(0)!=1||GL20.glGetShaderi(shaders.get(0),GL20.GL_SHADER_TYPE)!=GL43.GL_COMPUTE_SHADER||GL43.glGetProgramInterfacei(name,GL43.GL_SHADER_STORAGE_BLOCK,GL43.GL_ACTIVE_RESOURCES)!=6||binding(name,"Players")!=3||binding(name,"Templates")!=4||binding(name,"Sines")!=5||binding(name,"NativePoses")!=6||!ActorFusedShader.sha(GL20.glGetShaderSource(shaders.get(0))).equals("2b35115692e6615f84dc3d8648c479cf8875d6f00f0f41cc5efd0647198e7777"))return;program=new Program(name,link,context.key(),count,-1,"ordinary-native32");}catch(Exception unsupported){program=null;}
    }
    static void forget(int name,Object link){if(program!=null&&program.name==name&&program.link==link)program=null;if(currentModelLink==link)currentModelLink=null;}
    static boolean sameLink(Object link){return currentModelLink==link;}
    static Scope begin(int name,Object link,ActorSceneData.Raw raw,FloatBuffer ordinary,int rows,long packetRevision,IntBuffer payload){
        if(!ActorFusedMode.FUSED)return null;requireCurrent();if(thread!=null&&thread!=Thread.currentThread())throw new IllegalStateException("Fused input left its render owner");
        if(program==null||program.name!=name||program.link!=link||program.owner!=context.key()||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=name)return null;
        if(depth==scopes.length)throw new IllegalStateException("Fused native reentry bound exceeded; recover this dispatch on CPU");
        Scope scope=scopes[depth];if(scope==null)scopes[depth]=scope=new Scope();int level=depth++;
        try{
            scope.open(program,level);
            if(!scope.rows.collect(payload,rows)){scope.close();return null;}
            if(raw==null||scope.rows.count()==0||!frameReady()||program.metadataUniform<0)return scope;
            raw.check();boolean same=cachedView!=null&&cachedWorld==raw.lifetime()&&cachedBatch==raw.batch()&&cachedRaw==raw.revision()&&cachedPackets==packetRevision&&cachedCount==scope.rows.count();
            for(int i=0;same&&i<cachedCount;i++)same=cachedRows[i]==scope.rows.at(i);
            ActorFusedInput.View view;if(same)view=cachedView;else{cachedView=null;view=input.prepare(context.key(),raw,ordinary,rows,scope.rows);}if(view==null||view.taggedRows==0)return scope;
            if(!same){if(cachedRows.length<scope.rows.count())cachedRows=new int[scope.rows.count()];cachedCount=scope.rows.count();for(int i=0;i<cachedCount;i++)cachedRows[i]=scope.rows.at(i);cachedWorld=raw.lifetime();cachedBatch=raw.batch();cachedRaw=raw.revision();cachedPackets=packetRevision;cachedView=view;}
            var lease=lifetime.acquire(context.key(),new ActorFusedLifetime.Key(view.world,view.batch,view.generation,view.capacity));
            if(lease==null)return scope;scope.lease=lease;
            int nameBuffer=lease.buffer();var shadow=stores.shadows.get(nameBuffer);var diff=shadow.diff(view);shadowComparedWords+=diff.comparedWords;
            if(!lease.uploadRequired&&diff.ranges!=0)throw new IllegalStateException("Read-only reused input requires an unexpected rewrite");
            try{
                GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,nameBuffer);
                for(int r=0;r<diff.ranges;r++){int count=diff.length(r);scope.reserve(count);scope.upload.clear().limit(count);for(int p=diff.start(r),end=p+count;p<end;p++)scope.upload.put(view.word(p));scope.upload.flip();GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,(long)diff.start(r)*4,scope.upload);uploadedWords+=count;transmittedWords+=count;uploadCalls++;}
                diff.commit();if(diff.ranges==0)differentialReuses++;if(lease.uploadRequired)lease.uploaded(context.key());committedDirtyWords+=diff.dirtyWords;if(diff.exactCommittedViewReuse)committedViewReuses++;
            }catch(Throwable uploadFailure){shadow.invalidate();throw uploadFailure;}
            requireCurrent();GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,nameBuffer);GL30.glUniform1ui(program.metadataUniform,view.metadataBase);scope.fused=true;scope.taggedRows=view.taggedRows;return scope;
        }catch(Throwable failure){failed=true;frameReady=false;if(!scope.open){depth--;throw failure;}if(!ownsCurrent()){scope.close();throw failure;}try{scope.dropUnusedLease();return scope;}catch(Throwable fallbackFailure){try{scope.close();}finally{if(fallbackFailure!=failure)failure.addSuppressed(fallbackFailure);}throw failure;}}
    }
    static void retireWorld(long world){lifetime.requestRetireWorld(world);cachedView=null;}
    static void deferCaller(int program,int generic,int[] buffers,long[] starts,long[] sizes){if(deferredCallers.size()==8)throw new IllegalStateException("Fused deferred caller budget exceeded");deferredCallers.add(new DeferredCaller(program,generic,buffers.clone(),starts.clone(),sizes.clone()));}
    static void deferCopies(int read,int write){if(deferredCopies.size()==8)throw new IllegalStateException("Fused deferred copy budget exceeded");deferredCopies.add(new DeferredCopy(read,write));}
    static void deferSort(int program,Object link,int location,int count,int previous){if(deferredSorts.size()==8)throw new IllegalStateException("Fused deferred sort budget exceeded");deferredSorts.add(new DeferredSort(program,link,location,count,previous));}
    static void deferShader(int shader){if(deferredShaders.size()==8&&!deferredShaders.contains(shader))throw new IllegalStateException("Fused deferred shader budget exceeded");deferredShaders.add(shader);}
    private static void deleteDeferredShaders(){requireCurrent();for(int shader:deferredShaders)if(GL20.glIsShader(shader))GL20.glDeleteShader(shader);deferredShaders.clear();}
    static void close(){
        lifetime.requestClose();frameReady=false;program=null;currentModelLink=null;cachedView=null;if(!ownsCurrent()){foreignRefusals++;return;}
        if(!context.attached())return;deleteDeferredShaders();if(!lifetime.close(context.key()))return;
        if(!stores.names.isEmpty()||depth!=0||!deferredCallers.isEmpty()||!deferredUniforms.isEmpty()||!deferredCopies.isEmpty()||!deferredSorts.isEmpty())return;input.release(context.key());context.release(true);limitsAccepted=limitsChecked=failed=false;thread=null;
    }
    static String status(){if(Platform.get()!=Platform.WINDOWS)return "Fused actor input: Native32 fallback; trusted platform context unavailable";return "Fused actor input: "+(failed?"Private failure; native32 fallback | ":"")+"native/fallback "+fusedDispatches+"/"+fallbackDispatches+" | Upload words "+uploadedWords+" | GPU bytes "+stores.bytes+" | Context refusal/quarantine "+foreignRefusals+"/"+quarantines;}
    static final class Scope implements AutoCloseable {
        private Program ticket;private int level,oldCount,oldMetadata,taggedRows;private boolean open,fused,attempted;
        private ActorFusedLifetime.Lease lease;private IntBuffer upload=BufferUtils.createIntBuffer(256);
        final ActorFusedRows rows=new ActorFusedRows();
        void open(Program ticket,int level){int count=GL20.glGetUniformi(ticket.name,ticket.countUniform),metadata=ticket.metadataUniform<0?0:GL30.glGetUniformui(ticket.name,ticket.metadataUniform);this.ticket=ticket;this.level=level;open=true;fused=attempted=false;taggedRows=0;lease=null;oldCount=count;oldMetadata=metadata;if(ticket.metadataUniform>=0)GL30.glUniform1ui(ticket.metadataUniform,0);}
        void reserve(int count){if(count<1||count>8192*57)throw new IllegalArgumentException("Fused upload staging bound exceeded");if(upload.capacity()<count)upload=BufferUtils.createIntBuffer(Integer.highestOneBit(count-1)<<1);}
        boolean fused(){return fused;}boolean known(){return open;}
        void dropUnusedLease(){requireCurrent();if(attempted)throw new IllegalStateException("Cannot discard an issued native reader");if(lease!=null){stores.shadows.get(lease.buffer()).invalidate();lease.finish(context.key(),null);lease=null;}fused=false;if(ticket.metadataUniform>=0)GL30.glUniform1ui(ticket.metadataUniform,0);}
        void beforeDispatch(){requireCurrent();if(!open||ticket!=program||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=ticket.name)throw new IllegalStateException("Fused native consumer/link changed before dispatch");if(lease!=null&&fused)lease.beforeNativeDispatch(context.key());attempted=true;}
        public void close(){
            if(!open)return;if(depth!=level+1)throw new IllegalStateException("Native fused scopes closed out of order");
            try{
                if(!ownsCurrent()){
                    if(deferredUniforms.size()==8)throw new IllegalStateException("Fused deferred uniform budget exceeded");deferredUniforms.add(new DeferredUniform(ticket,oldCount,oldMetadata));
                    if(lease!=null){int name=lease.buffer();lease.abandonWithoutGl();stores.shadows.get(name).invalidate();if(attempted){addRecovery(name);quarantines++;}}foreignRefusals++;return;
                }
                try{if(lease!=null){ActorFusedLifetime.Fence last=null;try{if(fused&&attempted)last=new Fence();}catch(Throwable fenceFailure){int name=lease.buffer();lease.abandonWithoutGl();addRecovery(name);lease=null;quarantines++;throw fenceFailure;}finally{if(lease!=null)lease.finish(context.key(),last);}}}
                finally{if(program==ticket&&GL20.glIsProgram(ticket.name)){GL20.glUseProgram(ticket.name);GL20.glUniform1i(ticket.countUniform,oldCount);if(ticket.metadataUniform>=0)GL30.glUniform1ui(ticket.metadataUniform,oldMetadata);}}
                if(attempted){if(fused){fusedDispatches++;fusedRows+=taggedRows;}else{fallbackDispatches++;fallbackRows+=rows.count();}}
            }finally{open=false;lease=null;ticket=null;depth--;}
        }
    }
    private static void addRecovery(int name){for(int i=0;i<recoverCount;i++)if(recover[i]==name)return;if(recoverCount==recover.length)throw new IllegalStateException("Fused owner quarantine budget exhausted");recover[recoverCount++]=name;}
    private static final class Stores implements ActorFusedLifetime.Stores {
        final Set<Integer> names=new HashSet<>();final Map<Integer,ActorFusedInput.Shadow> shadows=new HashMap<>();final Map<Integer,Integer> sizes=new HashMap<>();long bytes;
        public int create(int words){requireCurrent();int name=0;try{name=GL15.glGenBuffers();if(name==0||names.size()>=3||words<1||words>8192*57)throw new IllegalStateException("Fused native input bound exceeded");GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,name);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)words*4,GL15.GL_DYNAMIC_DRAW);if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=(long)words*4)throw new IllegalStateException("Fused native store allocation failed");names.add(name);shadows.put(name,new ActorFusedInput.Shadow());sizes.put(name,words);bytes+=(long)words*4;return name;}catch(Throwable failure){if(name!=0)GL15.glDeleteBuffers(name);throw failure;}}
        public void delete(int name){requireCurrent();if(!names.remove(name))throw new IllegalStateException("Unknown fused input object");GL15.glDeleteBuffers(name);bytes-=(long)sizes.remove(name)*4;shadows.remove(name);}
    }
    private static final class Fence implements ActorFusedLifetime.Fence {
        final ActorFusedContext.Key owner=context.key();final long order=++fenceOrder,sync;
        Fence(){requireCurrent();sync=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(sync==0)throw new IllegalStateException("Fused last-reader fence unavailable");}
        public Object context(){return owner;}public long order(){return order;}
        public boolean signaledWithoutWaiting(){requireCurrent();int value=GL32.glClientWaitSync(sync,0,0);if(value==GL32.GL_TIMEOUT_EXPIRED)return false;if(value!=GL32.GL_ALREADY_SIGNALED&&value!=GL32.GL_CONDITION_SATISFIED)throw new IllegalStateException("Fused zero-timeout fence poll failed");return true;}
        public void delete(){requireCurrent();GL32.glDeleteSync(sync);}
    }
    private static int binding(int name,String block){return property(name,GL43.GL_SHADER_STORAGE_BLOCK,block,GL43.GL_BUFFER_BINDING);}
    private static int property(int name,int resource,String item,int property){int index=GL43.glGetProgramResourceIndex(name,resource,item);if(index==GL43.GL_INVALID_INDEX)return -1;IntBuffer key=BufferUtils.createIntBuffer(1).put(property).flip(),value=BufferUtils.createIntBuffer(1);GL43.glGetProgramResourceiv(name,resource,index,key,null,value);return value.get(0);}
}
