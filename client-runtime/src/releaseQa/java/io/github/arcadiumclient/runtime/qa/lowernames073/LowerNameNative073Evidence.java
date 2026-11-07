// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.lowernames073;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import io.github.arcadiumclient.runtime.qa.HologramNameNative073Qa;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

public final class LowerNameNative073Evidence {
    private record Ticket(Object owner,long thread,long counter) { }
    private static final ThreadLocal<Ticket> pending=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> diagnostic=ThreadLocal.withInitial(()->false);
    private static final List<Map<String,Object>> images=new ArrayList<>(),probes=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static long guards,known,unowned,wrong,truth,notes,returned,initialCounter;
    private static boolean initialized,verified,finished;
    private static String captureName;
    private static Map<String,Object> image;
    private static Field currentField,threadField;private static Object scope;
    private LowerNameNative073Evidence() { }
    public static boolean enabled(){return System.getProperty("arcadium.lowerNames073.output")!=null;}
    private static boolean boot(){return Boolean.parseBoolean(System.getProperty("arcadium.namePreparation073.lowerEntity"));}
    private static void check(boolean value,String message){if(!value)failures.add(message);}
    private static void initialize(){if(initialized)return;initialized=true;initialCounter=LowerNamePreparation.typedCalls;try{Field root=HologramNamePreparation.class.getDeclaredField("CURRENT");root.setAccessible(true);scope=root.get(null);currentField=scope.getClass().getDeclaredField("current");threadField=scope.getClass().getDeclaredField("thread");currentField.setAccessible(true);threadField.setAccessible(true);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static Object owner(){try{return currentField.get(scope);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private static boolean owns(Object value){try{return value!=null&&owner()==value&&threadField.get(scope)==Thread.currentThread();}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    public static synchronized void active(Object value,boolean result){
        if(!enabled()||finished)return;initialize();boolean owned=owns(value),expected=boot()&&LowerNamePreparation.preparedSites==1&&RenderHookAllocationControl.enabled&&owned;
        check(result==expected,"Lower active predicate differs from the live BOOT/site/control/identity/thread scope");
        if(diagnostic.get()){probes.add(Map.of("matchingOwner",owned,"result",result,"expected",expected,"thread",Thread.currentThread().threadId()));return;}
        guards++;if(owned)known++;else if(owner()==null||value==null)unowned++;else wrong++;
        if(result){check(owned,"Unowned lower invocation became active");check(pending.get()==null,"An eligible active guard was not consumed before another guard");truth++;pending.set(new Ticket(value,Thread.currentThread().threadId(),LowerNamePreparation.typedCalls));}
    }
    public static synchronized void note(boolean before){
        if(!enabled()||finished)return;initialize();Ticket ticket=pending.get();
        check(ticket!=null,"noteTyped has no preceding actual eligible active guard");if(ticket==null)return;
        check(ticket.thread==Thread.currentThread().threadId()&&owns(ticket.owner),"noteTyped changed owner or thread after its active guard");
        if(before){notes++;check(LowerNamePreparation.typedCalls==ticket.counter,"Lower counter changed before noteTyped");}
        else{returned++;check(LowerNamePreparation.typedCalls==ticket.counter+1,"noteTyped did not increment exactly once");pending.remove();}
    }
    public static void start(){
        if(!enabled())return;initialize();
        if(!"on".equals(System.getProperty("arcadium.hologramNames073.cohort")))throw new IllegalStateException("Living cohort must be ON in both lower runs");
        String value=System.getProperty("arcadium.namePreparation073.lowerEntity");if(!List.of("false","true").contains(value))throw new IllegalStateException("An explicit lower BOOT false/true is required");
        if(LowerNamePreparation.preparedSites!=1)throw new IllegalStateException("Exactly one actual lower site must be prepared: "+LowerNamePreparation.preparedSites);
        if(HologramNamePreparation.preparedSites!=2)throw new IllegalStateException("Existing Living sites must remain prepared");
        boolean old=RenderHookAllocationControl.enabled;Object first=new Object(),other=new Object(),previous=null;
        diagnostic.set(true);
        try{
            check(owner()==null,"Native owner was retained before verification");RenderHookAllocationControl.enabled=true;check(!LowerNamePreparation.active(first),"Unowned diagnostic became active");previous=HologramNamePreparation.enter(first);
            check(!LowerNamePreparation.active(other)&&!LowerNamePreparation.active(null),"Wrong/null owner diagnostic became active");check(LowerNamePreparation.active(first)==boot(),"Matching owner did not honor BOOT");
            Thread worker=new Thread(()->{diagnostic.set(true);Object nested=HologramNamePreparation.enter(first);try{check(!LowerNamePreparation.active(first),"Foreign thread borrowed the native owner");}finally{HologramNamePreparation.exit(nested);diagnostic.remove();}},"LowerNames073-unowned-probe");worker.setDaemon(true);worker.start();worker.join(5000);check(!worker.isAlive(),"Foreign-thread diagnostic did not finish");
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
        finally{HologramNamePreparation.exit(previous);RenderHookAllocationControl.enabled=old;diagnostic.remove();}
        check(owner()==null,"Diagnostic scope retained its owner");verified=true;
    }
    public static synchronized void capture(String name,boolean before){if(!enabled())return;if(before){check(captureName==null,"Nested capture boundary");captureName=name;}else captureName=null;}
    public static synchronized void image(boolean before){
        if(!enabled())return;initialize();
        if(before){if(image!=null)closeImage(false);image=new LinkedHashMap<>();image.put("name",captureName==null?"direct throwing image":captureName);image.put("capture",captureName!=null);image.put("serial",HologramNameNative073Qa.observerImageSerial()+1);image.put("guardsStart",guards);image.put("knownStart",known);image.put("unownedStart",unowned);image.put("wrongStart",wrong);image.put("trueStart",truth);image.put("notesStart",notes);image.put("returnsStart",returned);image.put("counterStart",LowerNamePreparation.typedCalls);image.put("fontFinallySeen",false);check(pending.get()==null,"Pending guard at image entry");}
        else closeImage(true);
    }
    public static synchronized void fontFinally(){if(enabled()&&image!=null)image.put("fontFinallySeen",true);}
    private static void closeImage(boolean completed){
        if(image==null){check(false,"Image RETURN has no HEAD observation");return;}
        image.put("returned",completed);image.put("guards",guards-number("guardsStart"));image.put("knownScopeGuards",known-number("knownStart"));image.put("unownedGuards",unowned-number("unownedStart"));image.put("wrongOwnerGuards",wrong-number("wrongStart"));
        long actualTrue=truth-number("trueStart"),actualNotes=notes-number("notesStart"),actualReturns=returned-number("returnsStart"),delta=LowerNamePreparation.typedCalls-number("counterStart");
        image.put("activeTrue",actualTrue);image.put("noteTypedHead",actualNotes);image.put("noteTypedReturn",actualReturns);image.put("lowerCounterDelta",delta);image.put("preparedSites",LowerNamePreparation.preparedSites);
        check(actualTrue==actualNotes&&actualNotes==actualReturns&&actualReturns==delta,"Lower active/note/counter correlation differs in "+image.get("name"));check(boot()||delta==0,"BOOT OFF used lower typed calls");check(pending.get()==null,"Image left an unconsumed active guard");check(Boolean.TRUE.equals(image.get("fontFinallySeen")),"Original font finally boundary was not reached");images.add(image);image=null;
    }
    private static long number(String name){return((Number)image.get(name)).longValue();}
    public static synchronized void finish(Throwable originalFailure){
        if(!enabled()||finished)return;initialize();if(image!=null)closeImage(false);finished=true;
        check(verified,"Original native verify method was not entered");check(originalFailure==null,"Original27-scene suite failed: "+originalFailure);check(LowerNamePreparation.preparedSites==1,"Lower prepared site count changed");check(truth==notes&&notes==returned&&returned==LowerNamePreparation.typedCalls-initialCounter,"Global eligible/note/counter correlation differs");
        check(pending.get()==null,"A pending lower guard survived finalization");check(images.stream().filter(r->Boolean.TRUE.equals(r.get("capture"))).count()==27,"Exactly27 original capture boundaries are required");
        check(boot()?returned>0:returned==0,"Lower BOOT cohort did not activate exactly as requested");check(owner()==null,"Final owner scope was retained");
        Map<String,Object> report=new LinkedHashMap<>();report.put("schema","arcadium.lower-names073.native-evidence.v1");report.put("passed",failures.isEmpty());report.put("lowerBoot",boot());report.put("livingCohort","on");report.put("preparedSites",LowerNamePreparation.preparedSites);report.put("livingPreparedSites",HologramNamePreparation.preparedSites);report.put("activeGuards",guards);report.put("knownScopeGuards",known);report.put("unownedGuards",unowned);report.put("wrongOwnerGuards",wrong);report.put("activeTrue",truth);report.put("noteTypedHead",notes);report.put("noteTypedReturn",returned);report.put("lowerCounterDelta",LowerNamePreparation.typedCalls-initialCounter);report.put("images",images);report.put("diagnosticProbes",probes);report.put("failures",failures);report.put("exactActiveNoteCorrelation",true);report.put("diagnosticsExcludedFromActualGuardCounts",true);report.put("rendererOrLivingInstrumentation",false);report.put("original27SceneSequenceModified",false);report.put("admissionEqualsLowerCallCountAssumed",false);report.put("privateCacheWrites",false);report.put("extraFontCallbacks",false);report.put("performanceClaim",false);
        try{Path path=Path.of(System.getProperty("arcadium.lowerNames073.output"));Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));}catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
