// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.*;
import java.util.*;

public final class ActorFused073ProducerWitness {
    public record Submission(Object context,long world,long batch,long inputRevision,int capacity,int buffer,long fenceOrder,
                             boolean closed,boolean uploaded,boolean retired,boolean quarantined,boolean exactNativeFence){}
    record State(Object world,Object context,DenseActorSlots slots,ActorSceneData scene,List<?> subjects,
                 boolean actorFrameReady,int reserved,int packetCount,long actorBatch,long packetRevision,
                 ActorFusedInput.View view,long cachedWorld,long cachedBatch,long cachedRaw,long cachedPackets,int[] cachedRows,int cachedCount,Submission submission){}
    public record Row(int subjectIndex,int primaryJob,long handle,long claimBatch,long observationBatch,long sourceGeneration,
                      long retainedTickAtRead,long retainedTickRevisionAtRead,int partialBits,boolean preparedTickWordsEqual,
                      int[] retainedTickWords,int[] preparedTickWords,int[] retainedStableRowWords,int[] preparedStableRowWords,int[] preparedTagWords){}
    public record TicketProof(long world,long batch,long inputRevision,int capacity,int buffer,long fenceOrder,
                              boolean closed,boolean uploaded,boolean retired,boolean quarantined,boolean exactNativeFence){}
    public record Evidence(String schema,String classification,long denseEpoch,long sceneEpoch,long sourceBatch,long sourceGeneration,
                           long currentArenaBatch,long currentArenaGeneration,int currentArenaUsed,long inputViewGeneration,
                           long rawRevision,long preparedPacketRevision,long currentPacketRevision,List<Row> rows,
                           TicketProof nativeSubmissionTicket,boolean preparedGpuOnlySourceProved,
                           boolean matchingNativeSubmissionTicket,boolean gpuExecutionOrCompletionProved,boolean nativeVisibilityOriginEpochKnown){}
    private ActorFused073ProducerWitness(){}

    static Evidence captureRuntime(Object world,List<?> subjects)throws ReflectiveOperationException{
        var metrics=GpuPlayerPackets.fusedActorMetrics();
        require(metrics.requested()&&metrics.enabled()&&metrics.ready()&&metrics.knownFusedProgram()&&!metrics.privateFailed()
            &&metrics.scopeDepth()==0&&metrics.contextRefusals()==0&&metrics.quarantines()==0,"Actual fused reader/context is not qualified");
        require(GpuModelTransforms.cpuRecoveries==0,"A native-model recovery prevents this exact witness");
        ActorFusedContext context=(ActorFusedContext)staticField(ActorFusedRuntime.class,"context");
        require(context.attached()&&staticField(ActorFusedRuntime.class,"thread")==Thread.currentThread(),"Actual fused context/thread is not retained");
        Object key=context.key();ActorFusedInput.View view=(ActorFusedInput.View)staticField(ActorFusedRuntime.class,"cachedView");
        require(view!=null,"No retained prepared input view");view.check();
        ActorFusedLifetime lifetime=(ActorFusedLifetime)staticField(ActorFusedRuntime.class,"lifetime");
        require(field(lifetime,"owner")==key,"Retained native store belongs to another context");
        Submission submission=null;
        for(Object slot:(Object[])field(lifetime,"slots")){
            ActorFusedLifetime.Key ticket=(ActorFusedLifetime.Key)field(slot,"key");
            if(ticket==null||ticket.world()!=view.world||ticket.batch()!=view.batch||ticket.revision()!=view.generation||ticket.capacity()!=view.capacity)continue;
            require(submission==null,"Duplicated retained native input key");Object fence=field(slot,"fence");
            boolean exactFence=fence!=null&&fence.getClass().getName().equals(ActorFusedRuntime.class.getName()+"$Fence");
            long order=exactFence?number(fence,"order"):0;
            require(exactFence&&field(fence,"owner")==key&&number(fence,"sync")!=0,"No real retained owning-context last-reader fence");
            submission=new Submission(key,ticket.world(),ticket.batch(),ticket.revision(),ticket.capacity(),((Number)field(slot,"name")).intValue(),order,
                ((Number)field(slot,"open")).intValue()==0,!(boolean)field(slot,"uploading"),(boolean)field(slot,"retired"),(boolean)field(slot,"quarantined"),exactFence);
        }
        return capture(new State(world,key,(DenseActorSlots)staticField(GpuPlayerPackets.class,"actors"),(ActorSceneData)staticField(GpuPlayerPackets.class,"scene"),subjects,
            (boolean)staticField(GpuPlayerPackets.class,"actorFrameReady"),((Number)staticField(GpuPlayerPackets.class,"reservedActors")).intValue(),GpuPlayerPackets.count(),
            ((Number)staticField(GpuPlayerPackets.class,"actorBatch")).longValue(),GpuPlayerPackets.playerRevision(),view,
            ((Number)staticField(ActorFusedRuntime.class,"cachedWorld")).longValue(),((Number)staticField(ActorFusedRuntime.class,"cachedBatch")).longValue(),
            ((Number)staticField(ActorFusedRuntime.class,"cachedRaw")).longValue(),((Number)staticField(ActorFusedRuntime.class,"cachedPackets")).longValue(),
            (int[])staticField(ActorFusedRuntime.class,"cachedRows"),((Number)staticField(ActorFusedRuntime.class,"cachedCount")).intValue(),submission));
    }

    static Evidence capture(State state)throws ReflectiveOperationException{
        require(state.world!=null&&state.context!=null&&state.slots!=null&&state.scene!=null&&state.view!=null,"Missing typed producer provenance");
        DenseActorSlots slots=state.slots;ActorSceneData scene=state.scene;ActorFusedInput.View view=state.view;
        require(field(scene,"owner")==Thread.currentThread(),"Foreign actor-scene owner");
        require(field(slots,"world")==state.world&&state.actorFrameReady,"World/actor frame changed");
        int actors=state.subjects.size();require((actors==3||actors==64)&&slots.size()==actors+1&&state.reserved==slots.size()&&state.packetCount==state.reserved,"Exact owned reserved roster required");
        view.check();require(view.context==state.context&&view.world==scene.epoch()&&view.world==state.cachedWorld&&view.batch==state.cachedBatch,"Retained input view world/context/batch differs");
        long batch=number(scene,"batch"),generation=number(scene,"generation"),raw=number(scene,"rawRevision");
        int used=((Number)field(scene,"used")).intValue(),touched=((Number)field(scene,"touchedCount")).intValue();
        require(batch==state.actorBatch&&raw==state.cachedRaw&&view.taggedRows==actors,"Current packet source changed since the prepared GPU-only view");
        boolean retired=used==0&&touched==0;
        long sourceBatch=retired?batch-1:batch,sourceGeneration=retired?generation-1:generation;
        require(sourceBatch>=0&&sourceGeneration>0&&view.batch==sourceBatch,"Only the current or exact immediately retired source batch is supported");
        require(state.cachedPackets>=0&&state.packetRevision==state.cachedPackets+(retired?1:0),"Unexpected intervening packet revision");
        require(state.cachedCount==actors&&state.cachedRows!=null&&state.cachedCount<=state.cachedRows.length,"Prepared native job coverage differs");
        Set<Integer> selected=new HashSet<>();for(int i=0;i<state.cachedCount;i++)require(selected.add(state.cachedRows[i]),"Duplicate prepared native job");
        boolean[] present=(boolean[])field(scene,"present"),discarded=(boolean[])field(scene,"discarded"),exact=(boolean[])field(scene,"exact");
        long[] handles=(long[])field(scene,"rowHandles"),rowGeneration=(long[])field(scene,"rowGeneration"),observed=(long[])field(scene,"observed");
        int[] rowSlots=(int[])field(scene,"rowSlots");float[] partial=(float[])field(scene,"partial"),rowPartial=(float[])field(scene,"rowPartial"),words=(float[])field(scene,"words");
        Object[] entries=(Object[])field(slots,"dense");Set<Object> owners=Collections.newSetFromMap(new IdentityHashMap<>());List<Row> rows=new ArrayList<>(actors);
        for(int index=0;index<actors;index++){
            Object subject=state.subjects.get(index);require(subject!=null&&owners.add(subject),"Missing/duplicate owned subject");
            int slot=slots.index(subject);long handle=slots.handle(subject);
            require(slot>=0&&slot<256&&slot<view.capacity&&handle>0&&selected.remove(slot),"Missing primary dense job in prepared native input");
            long claim=number(entries[slot],"claimed");
            require(rowSlots[slot]==slot&&handles[slot]==handle&&rowGeneration[slot]==sourceGeneration&&!exact[slot],"Retained primary ownership/generation/GPU-only type differs");
            require(claim==sourceBatch&&observed[slot]==sourceBatch,"Claim/tick observation is not from that exact source batch");
            if(retired)require(!present[slot]&&!discarded[slot],"The explicitly retired row-array shape differs");
            else require(scene.active(slot)&&present[slot]&&!discarded[slot],"Current primary row is absent/discarded");
            int p=slot*32,m=view.metadataBase+slot*4,tickPointer=view.word(m+1);
            require(view.word(m)==1&&tickPointer==view.capacity*36+slot*16,"The retained prepared source did not tag this primary row GPU-only");
            int delta=Float.floatToRawIntBits(rowPartial[slot]);
            require(view.word(m+2)==delta&&delta==Float.floatToRawIntBits(partial[slot]),"Prepared/captured/latest retained partial differs");
            ActorSceneData.Tick tick=scene.current(slot);require(tick!=null&&tick.handle==handle&&tick.tick>=0&&tick.tick<=16_777_216L,"Missing/reused/inexact-age retained tick");
            require(Float.floatToRawIntBits((float)tick.tick)==view.word(tickPointer+9),"Retained tick age differs from the prepared source");
            int[] retainedTick=new int[16],preparedTick=new int[16],retainedWords=new int[27],preparedWords=new int[27],tag=new int[4];
            for(int word=0;word<16;word++){retainedTick[word]=Float.floatToRawIntBits(tick.word(word));preparedTick[word]=view.word(tickPointer+word);require(preparedTick[word]==retainedTick[word],"Retained tick words changed after input preparation");}
            int at=0;for(int word=0;word<32;word++)if(word<16||word>20){retainedWords[at]=Float.floatToRawIntBits(words[p+word]);preparedWords[at]=view.word(p+word);require(preparedWords[at]==retainedWords[at],"Retained source row bytes changed");at++;}
            for(int word=0;word<4;word++)tag[word]=view.word(m+word);
            rows.add(new Row(index,slot,handle,claim,observed[slot],rowGeneration[slot],tick.tick,tick.revision,delta,true,retainedTick,preparedTick,retainedWords,preparedWords,tag));
        }
        require(selected.isEmpty(),"The prepared native job set includes an uncontrolled source");
        Submission ticket=state.submission;require(ticket!=null&&ticket.context==state.context&&ticket.world==view.world&&ticket.batch==view.batch
            &&ticket.inputRevision==view.generation&&ticket.capacity==view.capacity&&ticket.buffer>0&&ticket.fenceOrder>0
            &&ticket.closed&&ticket.uploaded&&!ticket.retired&&!ticket.quarantined&&ticket.exactNativeFence,"Missing exact closed native-input last-reader ticket");
        view.check();require(number(scene,"batch")==batch&&number(scene,"generation")==generation&&number(scene,"rawRevision")==raw,"Source lifetime changed during boundary collection");
        return new Evidence("arcadium.fused073.producer-witness.v4",retired?"last-retired-batch-gpu-only-source":"current-batch-gpu-only-source",
            slots.epoch(),scene.epoch(),sourceBatch,sourceGeneration,batch,generation,used,view.generation,raw,state.cachedPackets,state.packetRevision,
            List.copyOf(rows),new TicketProof(ticket.world,ticket.batch,ticket.inputRevision,ticket.capacity,ticket.buffer,ticket.fenceOrder,ticket.closed,ticket.uploaded,ticket.retired,ticket.quarantined,ticket.exactNativeFence),true,true,false,false);
    }
    private static Object field(Object owner,String name)throws ReflectiveOperationException{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static Object staticField(Class<?> owner,String name)throws ReflectiveOperationException{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
    private static long number(Object owner,String name)throws ReflectiveOperationException{return ((Number)field(owner,name)).longValue();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
