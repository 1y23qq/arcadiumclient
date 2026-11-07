// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.BitSet;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuPlayerPackets {
    public static final int PLAYER_WORDS=32,PART_WORDS=16,PARTS=12,MAX_PLAYERS=8192,POSE_WORDS=PARTS*8;
    private static FloatBuffer players=BufferUtils.createFloatBuffer(256*PLAYER_WORDS);
    private static FloatBuffer poses=BufferUtils.createFloatBuffer(256*POSE_WORDS);
    private static FloatBuffer playerView=players.duplicate(),poseView=poses.duplicate();
    private static float[] templates=new float[PARTS*PART_WORDS*4];
    private static final HashMap<TemplateKey,Integer> templateIds=new HashMap<>();
    private static int playerCount,poseCount,templateWords,uploadedPlayers,uploadedTemplates,playerBuffer,templateBuffer,sineBuffer,poseBuffer;
    private static long poseRevision,uploadedPoseRevision=-1,stateRevision,playerRevision,uploadedPlayerRevision=-1,templateRevision;
    private static final BitSet poseOwners=new BitSet();
    private static final DenseActorSlots actors=new DenseActorSlots(MAX_PLAYERS);
    private static final boolean ACTOR_SCENE=ActorFusedMode.OLD,FUSED_SCENE=ActorFusedMode.FUSED,SCENE_CAPTURE=ACTOR_SCENE||FUSED_SCENE;
    private static final ActorSceneData scene=SCENE_CAPTURE?new ActorSceneData(MAX_PLAYERS):null;
    private static final ActorSceneMaterials materials=ACTOR_SCENE?new ActorSceneMaterials():null;
    private static final BitSet lazyActors=SCENE_CAPTURE?new BitSet(MAX_PLAYERS):null;
    private static boolean actorFrameReady;
    private static int reservedActors;
    private static long actorBatch;
    public static long actorReconciliations,actorAssignments,actorDuplicateCaptures,actorCompactions,actorSceneStreamRows;
    private static final Matrix4f root=new Matrix4f();
    private static final float[] scratch=new float[6];
    private GpuPlayerPackets(){}
    private static final class TemplateKey {
        final float[] values;final int hash;
        TemplateKey(float[] values){this.values=values.clone();hash=Arrays.hashCode(this.values);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof TemplateKey key&&Arrays.equals(values,key.values);}
    }
    public static boolean actorSceneEnabled(){return SCENE_CAPTURE;}
    public static boolean fusedActorInputEnabled(){return FUSED_SCENE;}
    public record FusedActorMetrics(boolean requested,boolean enabled,boolean ready,boolean knownFusedProgram,boolean knownOrdinaryProgram,boolean privateFailed,
            long contextEpoch,long gpuBytes,long dispatches,long actorRows,long fallbackDispatches,long fallbackRows,long uploadedWords,long inputReuses,long busyFallbacks,long contextRefusals,long quarantines,int nativeStores,int scopeDepth,
            long tickWrites,long frameCaptures,long gpuOnlyCaptures,long cpuInterpolationRows,long validationRows,long clearedFrameRows){}
    public static FusedActorMetrics fusedActorMetrics(){
        if(!FUSED_SCENE)return new FusedActorMetrics(ActorFusedMode.FUSED_REQUESTED,false,false,false,false,false,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);
        return new FusedActorMetrics(true,true,ActorFusedRuntime.frameReady(),ActorFusedRuntime.qualifiedFused(),ActorFusedRuntime.qualifiedOrdinary(),ActorFusedRuntime.privateFailed(),ActorFusedRuntime.contextEpoch(),ActorFusedRuntime.bytes(),ActorFusedRuntime.fusedDispatches,ActorFusedRuntime.fusedRows,ActorFusedRuntime.fallbackDispatches,ActorFusedRuntime.fallbackRows,ActorFusedRuntime.uploadedWords,ActorFusedRuntime.differentialReuses,ActorFusedRuntime.busyFallbacks(),ActorFusedRuntime.foreignRefusals,ActorFusedRuntime.quarantines,ActorFusedRuntime.nativeStores(),ActorFusedRuntime.scopeDepth(),scene.tickWrites,scene.frameCaptures,scene.gpuOnlyCaptures,scene.cpuInterpolationRows,scene.captureValidationRows,scene.frameRowsCleared);
    }

    public static void beginActorFrame(){if(FUSED_SCENE)ActorFusedRuntime.prepareFrame();}
    public static boolean actorSceneReady(){return FUSED_SCENE?ActorFusedRuntime.frameReady():ACTOR_SCENE&&ActorSceneGpu.supported();}
    public static String actorSceneStatus(){if(ActorFusedMode.OLD_REQUESTED&&ActorFusedMode.FUSED_REQUESTED)return "Actor input: Conflicting private flags; exact native32";if(FUSED_SCENE)return ActorFusedRuntime.status();return !ACTOR_SCENE?"Actor tick/frame scene: Private prototype off":"Actor tick/frame scene: Resolved/streamed "+ActorSceneGpu.resolvedActorRows+"/"+(ActorSceneGpu.streamRows+actorSceneStreamRows)+" | Tick uploads "+ActorSceneGpu.tickUploads+" | GPU-only/lazyCPU "+scene.gpuOnlyCaptures+"/"+scene.cpuInterpolationRows+" | Sparse words/cached/interpolated "+scene.overrideWords+"/"+scene.constantOverrideWords+"/"+scene.overrideInterpolationWords+" | Pose/material fallback "+scene.poseMismatches+"/"+materials.fallbacks+" | Resolver failures "+ActorSceneGpu.failures+" | Context epoch/refusal "+ActorSceneGpu.contextEpoch()+"/"+ActorSceneGpu.contextRefusals+" | GPU bytes "+ActorSceneGpu.bytes();}
    public static int count(){return playerCount;}
    public static void notePersistentActor(){stateRevision++;playerRevision++;}
    public static long playerRevision(){return playerRevision;}
    public static long poseRevision(){return poseRevision;}
    public static long templateRevision(){return templateRevision;}
    public static int explicitPoseRows(){return poseCount;}
    public static int residentActorCount(){return actorFrameReady?actors.size():0;}
    public static long actorEpoch(){return actors.epoch();}
    public static void prepareActors(Object world,List<?> owners,boolean enabled){
        if(!enabled||world==null){clearActors();return;}
        long before=actors.epoch();if(!actors.prepare(world,owners)){clearActors();return;}
        if(scene!=null&&before!=actors.epoch()){if(FUSED_SCENE)ActorFusedRuntime.retireWorld(scene.epoch());scene.clear();if(materials!=null)materials.clear();lazyActors.clear();}
        ensurePlayers(actors.size());
        for(int move=0;move<actors.moves();move++){
            int from=actors.moveFrom(move),to=actors.moveTo(move);materialize(from);
            int source=from*PLAYER_WORDS,target=to*PLAYER_WORDS;
            for(int word=0;word<PLAYER_WORDS;word++)players.put(target+word,players.get(source+word));
            if(scene!=null)scene.move(from,to);
        }
        if(scene!=null){for(int slot=0;slot<actors.size();slot++)scene.retain(slot,actors.handleAt(slot));scene.truncate(actors.size());}
        actorCompactions+=actors.moves();actorReconciliations++;actorFrameReady=true;
    }
    private static void clearActors(){if(actorFrameReady||actors.size()!=0)actors.clear();actorFrameReady=false;reservedActors=0;if(scene!=null){if(FUSED_SCENE)ActorFusedRuntime.retireWorld(scene.epoch());scene.clear();if(materials!=null)materials.clear();lazyActors.clear();}}
    public static void observeActor(Object owner,long tick,float partial,float oldBody,float body,float oldHead,float head,float oldPitch,float pitch,float walk,float speed,float oldSpeed,float oldSwing,float swing){
        if(scene==null||!actorFrameReady)return;int slot=actors.index(owner);if(slot<0)return;
        scene.observe(slot,actors.handleAt(slot),tick,partial,oldBody,body,oldHead,head,oldPitch,pitch,walk,speed,oldSpeed,oldSwing,swing);
    }
    public static void observeMaterial(int job,Object main,Object batch,net.minecraft.resource.Identifier texture,int layer){
        if(!ACTOR_SCENE||scene==null||!scene.active(job))return;int id=materials.selected(main,batch,texture,layer);
        if(scene.materialId(job)!=id){if(scene.material(job,id))lazyActors.set(job);else{materialize(job);scene.discard(job);}stateRevision++;playerRevision++;}
    }
    private static void materialize(int job){if(scene!=null&&lazyActors.get(job)){if(scene.present(job))scene.cpu(job,players,job*PLAYER_WORDS);lazyActors.clear(job);if(FUSED_SCENE){stateRevision++;playerRevision++;}}}
    private static void materializeFused(ActorFusedRows requested){int copied=ActorFusedReplay.materialize(scene,lazyActors,players,requested);stateRevision+=copied;playerRevision+=copied;}
    private static void materializeAll(){if(FUSED_SCENE){materializeFused(null);return;}if(scene!=null)for(int job=lazyActors.nextSetBit(0);job>=0;job=lazyActors.nextSetBit(job+1))materialize(job);}
    private static void ensurePlayers(int count){
        int words=count*PLAYER_WORDS;if(words<=players.capacity())return;
        FloatBuffer next=BufferUtils.createFloatBuffer(grownPlayerWords(players.capacity(),words));
        next.put(players.duplicate().position(0).limit(playerCount*PLAYER_WORDS));players=next;playerView=players.duplicate();
    }
    static int grownPlayerWords(int current,int required){
        int maximum=MAX_PLAYERS*PLAYER_WORDS;
        if(current<0||current>maximum||required<0||required>maximum)throw new IllegalArgumentException("Player packet capacity exceeded");
        return required<=current?current:Math.max(required,Math.min(maximum,current*2));
    }
    public static void beginBatch(){
        ActorRoot073.beginBatch();
        if(actorBatch==Long.MAX_VALUE){clearActors();actorBatch=0;}actorBatch++;
        playerCount=reservedActors=actorFrameReady&&(GpuResidentPlayerState.enabled||SCENE_CAPTURE)?actors.size():0;
        if(scene!=null){scene.begin(actorBatch);lazyActors.clear();}
        poseCount=uploadedPlayers=0;poseOwners.clear();uploadedPoseRevision=uploadedPlayerRevision=-1;
        playerRevision++;poseRevision++;GpuResidentPlayerState.beginBatch();
    }
    public static int registerTemplate(float[] parts){
        if(parts.length!=PARTS*PART_WORDS)
            throw new IllegalArgumentException("Player template budget exceeded or invalid template");
        TemplateKey key=new TemplateKey(parts);Integer existing=templateIds.get(key);if(existing!=null)return existing;
        if(templateWords+parts.length>256*PARTS*PART_WORDS)throw new IllegalArgumentException("Player template budget exceeded");
        int offset=templateWords;if(templateWords+parts.length>templates.length)templates=Arrays.copyOf(templates,Math.max(templateWords+parts.length,templates.length*2));
        System.arraycopy(parts,0,templates,offset,parts.length);templateWords+=parts.length;templateRevision++;templateIds.put(key,offset);return offset;
    }
    public static int add(Matrix4fc base,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,
                          boolean sneak,boolean riding,boolean bow,int template){
        if(playerCount==MAX_PLAYERS)return -1;
        ensurePlayers(playerCount+1);int job=playerCount;write(job,base,walk,speed,age,yaw,pitch,swing,left,right,sneak,riding,bow,template);playerCount++;return job;
    }
    public static int addActor(Object owner,Matrix4fc base,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,
                               boolean sneak,boolean riding,boolean bow,int template){
        int job=actorFrameReady&&(GpuResidentPlayerState.enabled||SCENE_CAPTURE)&&reservedActors==actors.size()?actors.claim(owner,actorBatch):-1;
        if(job<0){if(actorFrameReady&&actors.index(owner)>=0)actorDuplicateCaptures++;return add(base,walk,speed,age,yaw,pitch,swing,left,right,sneak,riding,bow,template);}
        boolean captured=ACTOR_SCENE&&scene!=null&&scene.capture(job,actors.handleAt(job),job,base,walk,speed,age,yaw,pitch,swing,left,right,sneak,riding,bow,template,PlayerPoseOptions.flags(),players.get(job*PLAYER_WORDS+30),players.get(job*PLAYER_WORDS+31));
        if(!captured)write(job,base,walk,speed,age,yaw,pitch,swing,left,right,sneak,riding,bow,template);
        else{lazyActors.set(job);poseOwners.clear(job);stateRevision++;playerRevision++;}
        actorAssignments++;return job;
    }
    public static boolean actorSceneCanCapture(Object owner){
        if(scene==null||FUSED_SCENE&&!ActorFusedRuntime.frameReady()||!actorFrameReady||reservedActors!=actors.size()||!actors.canClaim(owner,actorBatch))return false;int slot=actors.index(owner);return slot>=0&&scene.canCapture(slot,actors.handleAt(slot));
    }
    public static int addActorScene(Object owner,Matrix4fc base,float swing,int left,int right,boolean sneak,boolean riding,boolean bow,int template){
        if(!actorSceneCanCapture(owner))return -1;int job=actors.claim(owner,actorBatch);
        if(!scene.captureGpuOnly(job,actors.handleAt(job),job,base,swing,left,right,sneak,riding,bow,template,PlayerPoseOptions.flags(),players.get(job*PLAYER_WORDS+30),players.get(job*PLAYER_WORDS+31)))return -1;
        lazyActors.set(job);poseOwners.clear(job);stateRevision++;playerRevision++;actorAssignments++;return job;
    }
    private static void write(int job,Matrix4fc base,float walk,float speed,float age,float yaw,float pitch,float swing,int left,int right,
                              boolean sneak,boolean riding,boolean bow,int template){
        int offset=job*PLAYER_WORDS;
        if(scene!=null){scene.discard(job);lazyActors.clear(job);}
        players.clear();base.get(offset,players);
        players.put(offset+16,walk).put(offset+17,speed).put(offset+18,age).put(offset+19,yaw).put(offset+20,pitch).put(offset+21,swing);
        players.put(offset+22,sneak?1:0).put(offset+23,bow?1:0).put(offset+24,left).put(offset+25,right).put(offset+26,riding?1:0).put(offset+27,template).put(offset+28,PlayerPoseOptions.flags());
        players.put(offset+29,-1);poseOwners.clear(job);
        stateRevision++;playerRevision++;
    }
    public static int addPose(Matrix4fc base,int template){
        int job=add(base,0,0,0,0,0,0,0,0,false,false,false,template);
        if(job<0)return job;
        int row=poseCount,words=(row+1)*POSE_WORDS;
        if(words>poses.capacity()){
            FloatBuffer next=BufferUtils.createFloatBuffer(Math.max(words,poses.capacity()*2));
            next.put(poses.duplicate().position(0).limit(poseCount*POSE_WORDS));poses=next;poseView=poses.duplicate();
        }
        players.put(job*PLAYER_WORDS+29,row);poseOwners.set(job);poseCount++;poseRevision++;stateRevision++;playerRevision++;
        return job;
    }
    public static void copyTemplate(int offset,float[] destination){
        if(offset<0||offset% (PARTS*PART_WORDS)!=0||offset+PARTS*PART_WORDS>templateWords||destination.length!=PARTS*PART_WORDS)
            throw new IllegalArgumentException("Invalid player template copy");
        System.arraycopy(templates,offset,destination,0,destination.length);
    }
    public static int addVariant(int source,int template){
        if(source<0||source>=playerCount||template<0||template%(PARTS*PART_WORDS)!=0||template+PARTS*PART_WORDS>templateWords)
            throw new IllegalArgumentException("Invalid player pose variant");
        if(playerCount==MAX_PLAYERS)return -1;
        int target=playerCount*PLAYER_WORDS;
        ensurePlayers(playerCount+1);
        if(scene!=null&&scene.variant(source,playerCount,template)){lazyActors.set(playerCount);poseOwners.clear(playerCount);stateRevision++;playerRevision++;return playerCount++;}
        materialize(source);
        int origin=source*PLAYER_WORDS;
        for(int word=0;word<PLAYER_WORDS;word++)players.put(target+word,players.get(origin+word));
        players.put(target+27,template);poseOwners.clear(playerCount);stateRevision++;playerRevision++;
        return playerCount++;
    }
    public static void writePose(int job,int part,float tx,float ty,float tz,float rx,float ry,float rz){
        if(job>=0&&job<playerCount)materialize(job);
        if(job<0||job>=playerCount||part<0||part>=PARTS||players.get(job*PLAYER_WORDS+29)<0)
            throw new IllegalArgumentException("Invalid native player pose");
        int offset=(int)players.get(job*PLAYER_WORDS+29)*POSE_WORDS+part*8;
        poses.put(offset,tx).put(offset+1,ty).put(offset+2,tz).put(offset+3,rx).put(offset+4,ry).put(offset+5,rz);
        poseRevision++;stateRevision++;
    }
    public static void rollback(int index){if(ActorRoot073.rootJob(index)){ActorRoot073.cancel(index);return;}if(scene!=null&&index>=0&&index<playerCount&&scene.present(index)){materialize(index);scene.discard(index);lazyActors.clear(index);stateRevision++;playerRevision++;}if(index>=reservedActors&&index>=0&&index==playerCount-1){
        if(poseOwners.get(index)&&(int)players.get(index*PLAYER_WORDS+29)==poseCount-1)poseCount--;
        poseOwners.clear(index);playerCount--;poseRevision++;stateRevision++;playerRevision++;
    }}
    public static void resetTemplates(){ActorRoot073.invalidate();clearActors();templateIds.clear();templateWords=0;templateRevision++;uploadedTemplates=-1;beginBatch();}
    public static Matrix4f resolve(int encoded,Matrix4f destination){
        if(ActorRoot073.rootJob(encoded/16))return ActorRoot073.resolve(encoded,destination,templates);
        int player=encoded/16,part=encoded%16,base=player*PLAYER_WORDS;
        if(player<0||player>=playerCount||part>=PARTS)throw new IllegalArgumentException("Invalid compact player instance");
        materialize(player);
        int t=(int)players.get(base+27)+part*PART_WORDS;
        int pose=(int)players.get(base+29);
        if(pose>=0){
            int p=pose*POSE_WORDS+part*8;
            root.set(base,players);
            return destination.set(root).translate(poses.get(p),poses.get(p+1),poses.get(p+2))
                    .rotateZ(poses.get(p+5)).rotateY(poses.get(p+4)).rotateX(poses.get(p+3))
                    .translate(templates[t+8],templates[t+9],templates[t+10]).scale(templates[t+12],templates[t+13],templates[t+14]);
        }
        PlayerPoseMath.part(players.get(base+16),players.get(base+17),players.get(base+18),players.get(base+19),players.get(base+20),players.get(base+21),
                (int)players.get(base+24),(int)players.get(base+25),players.get(base+22)!=0,players.get(base+26)!=0,players.get(base+23)!=0,
                PlayerPoseMath.BONES[part],templates[t],templates[t+1],templates[t+2],(int)players.get(base+28),scratch);
        root.set(base,players);if(players.get(base+22)!=0&&((int)players.get(base+28)&2)==0)root.translate(0,.2f,0);
        return destination.set(root).translate(scratch[0]*.0625f+templates[t+4],scratch[1]*.0625f+templates[t+5],scratch[2]*.0625f+templates[t+6])
                .rotateZ(scratch[5]).rotateY(scratch[4]).rotateX(scratch[3])
                .translate(templates[t+8],templates[t+9],templates[t+10]).scale(templates[t+12],templates[t+13],templates[t+14]);
    }
    public static void bind(){
        if(ActorRoot073.hasJobs()&&playerCount==0){ensureGpuStores();GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,playerBuffer);sharedBindings(false);return;}
        if(FUSED_SCENE){bindOrdinary(false,null);return;}
        if(ACTOR_SCENE)ActorSceneGpu.requireCurrent();
        if(playerBuffer==0){
            playerBuffer=GL15.glGenBuffers();templateBuffer=GL15.glGenBuffers();sineBuffer=GL15.glGenBuffers();poseBuffer=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,sineBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,PlayerPoseMath.sineTable(),GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
        }
        if(scene!=null)for(int job=lazyActors.nextSetBit(0);job>=0;job=lazyActors.nextSetBit(job+1)){if(!scene.gpuReady(job))materialize(job);}
        boolean resolvedScene=scene!=null&&ActorSceneGpu.bind(scene,playerView.position(0).limit(playerCount*PLAYER_WORDS),playerCount,actorBatch,stateRevision);
        if(resolvedScene)GpuResidentPlayerState.externalActors();else{materializeAll();if(scene!=null)actorSceneStreamRows+=playerCount;}
        boolean resident=!resolvedScene&&GpuResidentPlayerState.enabled&&GpuResidentPlayerState.bind(playerView.position(0).limit(playerCount*PLAYER_WORDS),poseView.position(0).limit(poseCount*POSE_WORDS),stateRevision);
        if(!resolvedScene&&!resident&&playerCount>0&&uploadedPlayerRevision!=playerRevision){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,playerView.position(0).limit(playerCount*PLAYER_WORDS),GL15.GL_STREAM_DRAW);uploadedPlayers=playerCount;uploadedPlayerRevision=playerRevision;
        }
        if(uploadedTemplates!=templateWords){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,Arrays.copyOf(templates,templateWords),GL15.GL_STATIC_DRAW);uploadedTemplates=templateWords;
        }
        if(!resident&&uploadedPoseRevision!=poseRevision&&poseCount>0){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,poseView.position(0).limit(poseCount*POSE_WORDS),GL15.GL_STREAM_DRAW);uploadedPoseRevision=poseRevision;
        }
        if(!resident){if(!resolvedScene)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,playerBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,6,poseBuffer);}
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,4,templateBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,5,sineBuffer);
    }
    private static void bindOrdinary(boolean subset,ActorFusedRows requested){
        if(FUSED_SCENE)ActorFusedRuntime.requireCurrent();
        if(ACTOR_SCENE)ActorSceneGpu.requireCurrent();
        ensureGpuStores();
        if(ACTOR_SCENE)for(int job=lazyActors.nextSetBit(0);job>=0;job=lazyActors.nextSetBit(job+1)){if(!scene.gpuReady(job))materialize(job);}
        boolean resolvedScene=ACTOR_SCENE&&scene!=null&&ActorSceneGpu.bind(scene,playerView.position(0).limit(playerCount*PLAYER_WORDS),playerCount,actorBatch,stateRevision);
        if(resolvedScene)GpuResidentPlayerState.externalActors();else{if(FUSED_SCENE&&subset){materializeFused(requested);}else materializeAll();if(scene!=null)actorSceneStreamRows+=subset?requested.count():playerCount;}
        boolean resident=!resolvedScene&&GpuResidentPlayerState.enabled&&GpuResidentPlayerState.bind(playerView.position(0).limit(playerCount*PLAYER_WORDS),poseView.position(0).limit(poseCount*POSE_WORDS),stateRevision);
        if(!resolvedScene&&!resident&&playerCount>0&&uploadedPlayerRevision!=playerRevision){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,playerView.position(0).limit(playerCount*PLAYER_WORDS),GL15.GL_STREAM_DRAW);uploadedPlayers=playerCount;uploadedPlayerRevision=playerRevision;
        }
        if(!resident&&!resolvedScene)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,playerBuffer);
        sharedBindings(resident);
    }
    private static void ensureGpuStores(){
        if(playerBuffer==0){
            playerBuffer=GL15.glGenBuffers();templateBuffer=GL15.glGenBuffers();sineBuffer=GL15.glGenBuffers();poseBuffer=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,sineBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,PlayerPoseMath.sineTable(),GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,playerBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,128,GL15.GL_STREAM_DRAW);
        }
    }
    private static void sharedBindings(boolean resident){
        if(uploadedTemplates!=templateWords){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,templateBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,Arrays.copyOf(templates,templateWords),GL15.GL_STATIC_DRAW);uploadedTemplates=templateWords;
        }
        if(!resident&&uploadedPoseRevision!=poseRevision&&poseCount>0){
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,poseBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,poseView.position(0).limit(poseCount*POSE_WORDS),GL15.GL_STREAM_DRAW);uploadedPoseRevision=poseRevision;
        }
        if(!resident)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,6,poseBuffer);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,4,templateBuffer);GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,5,sineBuffer);
    }
    static ActorFusedRuntime.Scope bindModelInput(int program,Object link,IntBuffer payload){
        if(!FUSED_SCENE){bind();return null;}ActorFusedRuntime.requireCurrent();ensureGpuStores();
        var raw=scene==null?null:scene.seal().raw();var scope=ActorFusedRuntime.begin(program,link,raw,playerView.position(0).limit(playerCount*PLAYER_WORDS),playerCount,playerRevision,payload);
        try{if(scope!=null&&scope.fused()){GpuResidentPlayerState.externalActors();sharedBindings(false);}else bindOrdinary(scope!=null,scope==null?null:scope.rows);return scope;}
        catch(Throwable failure){if(scope!=null)scope.close();throw failure;}
    }
    public static void close(){
        if(FUSED_SCENE&&!ActorFusedRuntime.ownsCurrent())return;
        if(ACTOR_SCENE&&!ActorSceneGpu.ownsCurrent())return;
        try{
        try{GpuResidentPlayerState.reset();}finally{if(ACTOR_SCENE)ActorSceneGpu.close();}
        if(playerBuffer!=0)GL15.glDeleteBuffers(playerBuffer);if(templateBuffer!=0)GL15.glDeleteBuffers(templateBuffer);if(sineBuffer!=0)GL15.glDeleteBuffers(sineBuffer);if(poseBuffer!=0)GL15.glDeleteBuffers(poseBuffer);
        playerBuffer=templateBuffer=sineBuffer=poseBuffer=uploadedPlayers=uploadedTemplates=playerCount=poseCount=templateWords=0;uploadedPoseRevision=uploadedPlayerRevision=-1;
        templateIds.clear();
        poseOwners.clear();
        clearActors();
        playerRevision++;poseRevision++;templateRevision++;
        }finally{if(FUSED_SCENE)ActorFusedRuntime.close();}
    }
    public static final class PreparedAppend {
        private int players,poses;private long batch;private Thread owner;private boolean open;
        PreparedFault fault;public boolean open(){return open;}
        void point(int stage){if(fault!=null)fault.at(stage);}
    }
    @FunctionalInterface interface PreparedFault {void at(int stage);}
    public static void beginPreparedAppend(PreparedAppend tx){if(tx.open)throw new IllegalStateException("Nested prepared append checkpoint");tx.players=playerCount;tx.poses=poseCount;tx.batch=actorBatch;tx.owner=Thread.currentThread();tx.open=true;}
    public static void commitPreparedAppend(PreparedAppend tx){checkPrepared(tx);tx.open=false;}
    public static void rollbackPreparedAppend(PreparedAppend tx){
        checkPrepared(tx);if(playerCount<tx.players||poseCount<tx.poses)throw new IllegalStateException("Prepared append ownership changed");
        for(int job=playerCount-1;job>=tx.players;job--){if(scene!=null)scene.discard(job);if(lazyActors!=null)lazyActors.clear(job);poseOwners.clear(job);}
        playerCount=tx.players;poseCount=tx.poses;poseRevision++;stateRevision++;playerRevision++;tx.open=false;
    }
    private static void checkPrepared(PreparedAppend tx){if(!tx.open||tx.owner!=Thread.currentThread()||tx.batch!=actorBatch)throw new IllegalStateException("Stale prepared append checkpoint");}
    public static int addPreparedPose(Matrix4fc base,int template,PreparedAppend tx){
        checkPrepared(tx);tx.point(0);int job=add(base,0,0,0,0,0,0,0,0,false,false,false,template);if(job<0)return job;
        tx.point(1);int row=poseCount,words=(row+1)*POSE_WORDS;tx.point(2);
        if(words>poses.capacity()){FloatBuffer next=BufferUtils.createFloatBuffer(Math.max(words,poses.capacity()*2));next.put(poses.duplicate().position(0).limit(poseCount*POSE_WORDS));poses=next;poseView=poses.duplicate();}
        tx.point(3);players.put(job*PLAYER_WORDS+29,row);poseOwners.set(job);tx.point(4);poseCount++;poseRevision++;stateRevision++;playerRevision++;tx.point(5);return job;
    }

}
