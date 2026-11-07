// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class QuietPerformanceWindow073 {
    private final String caseId,nonce;
    private final long pid;
    private final Path readyFile,releaseFile;
    private JsonObject release;
    private long releaseNano,startNano,endNano;
    private Instant startUtc,endUtc;
    private boolean published,started,ended;

    public QuietPerformanceWindow073(Path directory,String caseId,String nonce,long pid){
        if(!caseId.matches("[a-z0-9][a-z0-9-]{0,95}")||!nonce.matches("[a-f0-9]{32}")||pid<=0)throw new IllegalArgumentException("Invalid owned performance identity");
        Path root=directory.toAbsolutePath().normalize();this.caseId=caseId;this.nonce=nonce;this.pid=pid;
        readyFile=root.resolve("quiet-ready.json");releaseFile=root.resolve("quiet-release.json");
    }
    public boolean awaitRelease()throws Exception{
        return awaitRelease(System.nanoTime());
    }
    boolean awaitRelease(long now)throws Exception{
        if(started)throw new IllegalStateException("Release polling continued inside the measurement window");
        if(!published){
            if(Files.exists(readyFile)||Files.exists(releaseFile))throw new IllegalStateException("A fresh quiet-window namespace is required");
            Files.createDirectories(readyFile.getParent());
            Files.writeString(readyFile,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("schema","arcadium.performance073.ready.v1","caseId",caseId,"nonce",nonce,"pid",pid,"createdUtc",Instant.now().toString(),"createdNano",now,"samplingStarted",false)),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            published=true;return false;
        }
        if(release==null&&Files.exists(releaseFile)){
            if(Files.isSymbolicLink(releaseFile)||Files.size(releaseFile)>8192)throw new IllegalStateException("Release metadata exceeds its owned boundary");
            JsonObject value=JsonParser.parseString(Files.readString(releaseFile,StandardCharsets.UTF_8)).getAsJsonObject();
            validateRelease(value,caseId,nonce,pid,Instant.now());release=value;releaseNano=now;
        }
        return release!=null&&now-releaseNano>=2_000_000_000L;
    }
    static void validateRelease(JsonObject value,String caseId,String nonce,long pid,Instant now){
        Set<String> fields=Set.of("caseId","nonce","pid","rootUiComplete","allAgentsQuiet","rootUiStartedUtc","rootUiEndedUtc","releasedUtc");
        if(!value.keySet().equals(fields)||!caseId.equals(value.get("caseId").getAsString())||!nonce.equals(value.get("nonce").getAsString()))throw new IllegalStateException("Root release identity differs");
        var process=value.getAsJsonPrimitive("pid");
        if(!process.isNumber()||!process.getAsString().matches("[1-9][0-9]{0,17}")||process.getAsLong()!=pid)throw new IllegalStateException("Root release process differs");
        for(String key:List.of("rootUiComplete","allAgentsQuiet"))if(!value.get(key).isJsonPrimitive()||!value.getAsJsonPrimitive(key).isBoolean()||!value.get(key).getAsBoolean())throw new IllegalStateException("Root UI or quiet acknowledgement is absent");
        Instant begin=Instant.parse(value.get("rootUiStartedUtc").getAsString()),end=Instant.parse(value.get("rootUiEndedUtc").getAsString()),released=Instant.parse(value.get("releasedUtc").getAsString());
        if(begin.isAfter(end)||end.isAfter(released)||released.isAfter(now))throw new IllegalStateException("Root UI and release timestamps are not ordered");
    }
    public long start(){
        if(release==null||started||System.nanoTime()-releaseNano<2_000_000_000L)throw new IllegalStateException("The quiet release has not settled");
        startUtc=Instant.now();started=true;return startNano=System.nanoTime();
    }
    public void end(long nano){
        if(!started||ended||nano-startNano<30_000_000_000L)throw new IllegalStateException("The actual measurement window is incomplete");
        endNano=nano;endUtc=Instant.now();ended=true;
    }
    public Map<String,Object> report(){
        if(!ended||startUtc.isAfter(endUtc))throw new IllegalStateException("No complete measured interval exists");
        var result=new LinkedHashMap<String,Object>();result.put("schema","arcadium.performance073.quiet-window.v1");result.put("caseId",caseId);result.put("nonce",nonce);result.put("pid",pid);
        result.put("rootRelease",release);result.put("measurementStartedNano",startNano);result.put("measurementEndedNano",endNano);result.put("measurementStartedUtc",startUtc.toString());result.put("measurementEndedUtc",endUtc.toString());
        result.put("quietSettleNanos",2_000_000_000L);result.put("releasePollingInsideMeasurement",false);result.put("externalRootOperationAuditStillRequired",true);return result;
    }
}
