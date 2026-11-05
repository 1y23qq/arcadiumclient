// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline;
import java.lang.invoke.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;

public final class GpuVoxelSkin072PendingPartEvidence {
    private static long calls,rejected,restored,reasonMatches,unknownCounterAdvances;
    private static boolean hooked;
    private static final List<Map<String,Object>> samples=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static final ClassValue<VarHandle> meshes=new ClassValue<>(){
        @Override protected VarHandle computeValue(Class<?> type){
            for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())try{
                return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).unreflectVarHandle(owner.getDeclaredField("skinLayers$mesh"));
            }catch(NoSuchFieldException ignored){}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
            throw new IllegalStateException("No actual Legacy mesh attachment");
        }
    };
    private GpuVoxelSkin072PendingPartEvidence(){}
    public static boolean run(ModelPart[] parts,int mask,BooleanSupplier original,Supplier<String> reason){
        if(!Boolean.getBoolean("arcadium.voxelQuality072.pendingPart")||!PlayerPipeline072Qa.enabled()
                ||!System.getProperty("arcadium.playerPipeline072.mode","visual").equals("visual")
                ||!GpuVoxelSkin072AlphaSeed.ownedFixtureActor())return original.getAsBoolean();
        Minecraft mc=Minecraft.getInstance();
        if(!mc.getSession().getUsername().equals("ArcadiumTest")||!mc.getSession().getAccessToken().equals("0"))throw new IllegalStateException("Pending-part QA requires the owned fake account");
        if(parts==null||parts.length!=12||parts[6]==null||parts[11]==null)throw new IllegalStateException("Pending-part QA requires the owned full player model");
        VarHandle head=meshes.get(parts[6].getClass()),body=meshes.get(parts[11].getClass());Object headMesh=head.get(parts[6]),bodyMesh=body.get(parts[11]);
        if(headMesh==null||bodyMesh==null||!parts[6].visible||parts[6].invisible||!parts[11].visible||parts[11].invisible)throw new IllegalStateException("The controlled head and body must be ready and visible");
        hook();calls++;long beforeUnknown=GpuVoxelSkinPipeline.unknownImageFallbacks;boolean accepted=false;
        body.set(parts[11],null);
        try{
            accepted=original.getAsBoolean();String actualReason=reason.get();
            if(!accepted)rejected++;
            if("An active outer skin part is pending".equals(actualReason))reasonMatches++;
            if(GpuVoxelSkinPipeline.unknownImageFallbacks>beforeUnknown)unknownCounterAdvances++;
            if(samples.size()<32)samples.add(Map.of("requestedMask",Integer.toHexString(mask),"captureOnly",Boolean.getBoolean("arcadium.voxelQuality072.captureOnly"),
                    "headWasReady",true,"bodyWasReady",true,"bodyHiddenOnlyDuringAdmission",true,"accepted",accepted,
                    "reason",actualReason,"unknownCounterDelta",GpuVoxelSkinPipeline.unknownImageFallbacks-beforeUnknown));
            if(accepted||!"An active outer skin part is pending".equals(actualReason)){
                failures.add("Actual common admission did not reject the pending body");throw new IllegalStateException("Actual pending-part guard was not exercised");
            }
            return false;
        }finally{
            body.set(parts[11],bodyMesh);
            if(body.get(parts[11])==bodyMesh&&head.get(parts[6])==headMesh)restored++;
            else{failures.add("Head/body attachment restoration failed");throw new IllegalStateException("Pending-part QA leaked an attachment change");}
        }
    }
    private static void hook(){if(hooked)return;hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(GpuVoxelSkin072PendingPartEvidence::write,"Arcadium pending skin part QA"));}
    private static void write(){
        try{
            Path output=Path.of(System.getProperty("arcadium.playerPipeline072.output")).toAbsolutePath().resolveSibling("voxel-pending-part.json");
            Map<String,Object> report=new LinkedHashMap<>();report.put("correctnessOnly",true);report.put("notPerformanceEvidence",true);
            report.put("actualGuardInvocations",calls);report.put("rejected",rejected);report.put("restoredBeforeOriginalRendering",restored);
            report.put("pendingReasonMatches",reasonMatches);report.put("unknownCounterAdvances",unknownCounterAdvances);
            report.put("captureOnly",Boolean.getBoolean("arcadium.voxelQuality072.captureOnly"));report.put("samples",samples);report.put("failures",failures);
            report.put("passed",calls>0&&calls==rejected&&calls==restored&&calls==reasonMatches&&unknownCounterAdvances>0&&failures.isEmpty());
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception failure){failure.printStackTrace();}
    }
}
