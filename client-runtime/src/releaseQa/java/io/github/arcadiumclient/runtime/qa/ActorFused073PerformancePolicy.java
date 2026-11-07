// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPackets.FusedActorMetrics;
import java.util.*;

public final class ActorFused073PerformancePolicy {
    private ActorFused073PerformancePolicy(){}
    public static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    public static void mode(boolean on,boolean oldRequested,boolean quiet,int players,int warm,int seconds,int qualifiedPlayers){require(!oldRequested,"Legacy standalone actor experiment must remain off");require(quiet&&players==qualifiedPlayers&&players>0&&players<=64&&warm==25&&seconds==30,"Qualified actor domain or quiet25s/30s contract changed");}
    public static void metrics(FusedActorMetrics value,boolean on,boolean warm){
        require(value.requested()==on&&value.enabled()==on,"Actual fused startup mode differs");require(value.scopeDepth()==0,"Performance boundary entered a native reader scope");
        require(value.gpuBytes()>=0&&value.gpuBytes()<=5_603_328L&&value.nativeStores()>=0&&value.nativeStores()<=3,"Owned actor input exceeds its bound");
        if(on){require(value.ready()&&value.knownFusedProgram()&&!value.privateFailed(),"Actual fused native program is not eligible");if(warm)require(value.dispatches()>0&&value.actorRows()>0&&value.gpuOnlyCaptures()>0,"No actual fused reader/producer warmed");}
        else require(value.dispatches()==0&&value.actorRows()==0&&value.gpuBytes()==0,"OFF initialized or submitted the private input");
    }
    public static Map<String,Object> delta(FusedActorMetrics a,FusedActorMetrics b,boolean on){
        metrics(a,on,true);metrics(b,on,true);require(a.contextEpoch()==b.contextEpoch(),"Actual owner context epoch changed during timing");
        long dispatches=b.dispatches()-a.dispatches(),rows=b.actorRows()-a.actorRows(),busy=b.busyFallbacks()-a.busyFallbacks(),contexts=b.contextRefusals()-a.contextRefusals(),quarantine=b.quarantines()-a.quarantines();
        require(dispatches>=0&&rows>=0&&busy>=0&&contexts==0&&quarantine==0,"Fused counters reset or context/quarantine failure occurred");if(on)require(dispatches>0&&rows>0&&b.gpuOnlyCaptures()>a.gpuOnlyCaptures(),"No actual fused reader/producer was measured");
        var data=new LinkedHashMap<String,Object>();data.put("dispatches",dispatches);data.put("actorRowReads",rows);data.put("ordinaryDispatches",b.fallbackDispatches()-a.fallbackDispatches());data.put("ordinaryRowReads",b.fallbackRows()-a.fallbackRows());data.put("busyFallbacks",busy);data.put("contextRefusals",contexts);data.put("quarantines",quarantine);data.put("uploadWords",b.uploadedWords()-a.uploadedWords());data.put("inputReuses",b.inputReuses()-a.inputReuses());data.put("tickWrites",b.tickWrites()-a.tickWrites());data.put("frameCaptures",b.frameCaptures()-a.frameCaptures());data.put("gpuOnlyCaptures",b.gpuOnlyCaptures()-a.gpuOnlyCaptures());data.put("cpuInterpolationRows",b.cpuInterpolationRows()-a.cpuInterpolationRows());data.put("validationRows",b.validationRows()-a.validationRows());data.put("clearedFrameRows",b.clearedFrameRows()-a.clearedFrameRows());data.put("memoryBeforeBytes",a.gpuBytes());data.put("memoryAfterBytes",b.gpuBytes());data.put("memoryChangeBytes",b.gpuBytes()-a.gpuBytes());data.put("storesBefore",a.nativeStores());data.put("storesAfter",b.nativeStores());return data;
    }
    public static Map<String,Object> workload(long compact,long nativeParts,long nativeDispatches,long frames){require(frames>100&&compact>0&&nativeParts>=0&&nativeDispatches>0,"Actual native actor workload was absent");return Map.of("rawCompactCaptures",compact,"rawNativeBodyParts",nativeParts,"rawNativeModelDispatches",nativeDispatches,"sampledFrameIntervals",frames,"compactCapturesPerInterval",compact/(double)frames,"nativeBodyPartsPerInterval",nativeParts/(double)frames,"nativeDispatchesPerInterval",nativeDispatches/(double)frames);}
}
