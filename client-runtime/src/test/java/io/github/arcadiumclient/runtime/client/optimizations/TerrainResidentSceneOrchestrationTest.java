// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TerrainResidentSceneOrchestrationTest {
 private static class Source {
  final TerrainResidentInventory inventory=new TerrainResidentInventory(new Object());final Object a=new Object(),b=new Object(),sa=new Object(),sb=new Object(),list=new Object();
  int generation=1;boolean alive=true;final List<String> calls=new ArrayList<>();TerrainResidentInventory.Frame prepared;
  Source(){mutate(()->{inventory.addRegion(a,0,0,0);inventory.addRegion(b,128,0,0);inventory.addStorage(a,sa,0,words(100));inventory.addStorage(b,sb,0,words(200));inventory.publishOrder(list,0,List.of(new TerrainResidentInventory.OrderRow(a,a,new byte[]{0},1),new TerrainResidentInventory.OrderRow(b,b,new byte[]{0},1)));});prepared=inventory.seal(0,list);inventory.acknowledge(prepared);}
  void mutate(Runnable r){var token=inventory.begin();boolean ok=false;try{r.run();ok=true;}finally{inventory.end(token,ok);}}
  static int[] words(int first){int[] words=new int[5120];words[10]=first;words[18]=6;return words;}
  TerrainResidentPassSubmission.Driver<Object> driver(){return new TerrainResidentPassSubmission.Driver<>(){public boolean alive(){return Source.this.alive;}public boolean gpuValid(){return prepared.valid(list);}public TerrainResidentPassSubmission.Result execute(Object r,boolean gpu){calls.add((gpu?"gpu":"native")+":"+(r==a?"a":"b")+":"+generation);return gpu?TerrainResidentPassSubmission.Result.GPU:TerrainResidentPassSubmission.Result.NATIVE;}};}
 }
 @Test void lateHookPublicationAfterOneGpuIssueChangesOnlyCurrentAndUnissuedWork()throws Throwable{
  var s=new Source();var plan=new TerrainResidentPassSubmission<Object>();assertEquals(TerrainResidentPassSubmission.Result.GPU,TerrainResidentScene.drive(plan,s.a,s.driver()));
  s.mutate(()->s.inventory.section(s.sb,0,Arrays.copyOf(Source.words(900),20)));s.generation++;
  assertEquals(TerrainResidentPassSubmission.Result.NATIVE,TerrainResidentScene.drive(plan,s.b,s.driver()));assertEquals(TerrainResidentPassSubmission.Result.SKIP,TerrainResidentScene.drive(plan,s.a,s.driver()));
  assertEquals(List.of("gpu:a:1","native:b:2"),s.calls);assertEquals(1,plan.gpuIssued);assertEquals(1,plan.nativeIssued);assertEquals(1,plan.duplicates);
 }
 @Test void relocationAfterCacheSelectionCannotCombineOldCommandsAndNewVao()throws Throwable{
  var s=new Source();var selected=s.prepared;int oldVao=s.generation;var plan=new TerrainResidentPassSubmission<Object>();s.mutate(()->{s.inventory.relocate(s.sb,Source.words(4096));s.inventory.bindingsChanged(s.b);});s.generation++;
  assertFalse(selected.valid(s.list));assertEquals(TerrainResidentPassSubmission.Result.NATIVE,TerrainResidentScene.drive(plan,s.b,s.driver()));assertEquals("native:b:2",s.calls.getFirst());assertNotEquals(oldVao,s.generation);assertEquals(0,plan.gpuIssued);
 }
 @Test void orderPublicationChangesUseTheFrozenPassOrderWithoutWholePassReplay()throws Throwable{
  var s=new Source();var plan=new TerrainResidentPassSubmission<Object>();TerrainResidentScene.drive(plan,s.a,s.driver());Object next=new Object();s.mutate(()->s.inventory.publishOrder(next,1,List.of(new TerrainResidentInventory.OrderRow(s.b,s.b,new byte[]{0},1),new TerrainResidentInventory.OrderRow(s.a,s.a,new byte[]{0},1))));
  TerrainResidentScene.drive(plan,s.b,s.driver());TerrainResidentScene.drive(plan,s.a,s.driver());assertEquals(List.of("gpu:a:1","native:b:1"),s.calls);assertEquals(1,plan.gpuIssued);assertEquals(1,plan.nativeIssued);
 }
 @Test void aPostIssueThrowableNeverPermitsCurrentRegionToBeReplayed()throws Throwable{
  var s=new Source();var plan=new TerrainResidentPassSubmission<Object>();var broken=new TerrainResidentPassSubmission.Driver<Object>(){public boolean alive(){return true;}public boolean gpuValid(){return true;}public TerrainResidentPassSubmission.Result execute(Object r,boolean gpu){s.calls.add("gpu:a:possibly-issued");throw new IllegalStateException("Wrapper failed after native call");}};
  assertThrows(IllegalStateException.class,()->TerrainResidentScene.drive(plan,s.a,broken));assertEquals(TerrainResidentPassSubmission.Result.SKIP,TerrainResidentScene.drive(plan,s.a,s.driver()));assertEquals(TerrainResidentPassSubmission.Result.NATIVE,TerrainResidentScene.drive(plan,s.b,s.driver()));assertEquals(2,s.calls.size());assertEquals(1,plan.ambiguous);
 }
 @Test void destroyedWorldAbortsOnlyUnissuedWorkAndKeepsTheIssuedLedger()throws Throwable{
  var s=new Source();var plan=new TerrainResidentPassSubmission<Object>();TerrainResidentScene.drive(plan,s.a,s.driver());s.alive=false;s.inventory.reset();assertEquals(TerrainResidentPassSubmission.Result.ABORT,TerrainResidentScene.drive(plan,s.b,s.driver()));assertEquals(List.of("gpu:a:1"),s.calls);assertTrue(plan.aborted());
 }
 @Test void sceneOwnerCloseRetainsQuarantinedReservationAcrossRepeatedClose(){
  synchronized(TerrainGpuBudget.class){long before=TerrainGpuBudget.bytes();Object owner=new Object();int[] calls={0};Map<Integer,Object> names=new HashMap<>();Object original=new Object(),foreign=new Object();names.put(1,original);var resources=new TerrainResidentResources(()->{});resources.add(()->{calls[0]++;assertSame(original,names.remove(1));names.put(1,foreign);throw new IllegalStateException("Completed delete then name reuse");});assertTrue(TerrainGpuBudget.reserve(owner,1024));
   try{for(int i=0;i<3;i++){assertThrows(IllegalStateException.class,()->TerrainResidentScene.closeRetained(resources,resources::empty,owner));assertEquals(before+1024,TerrainGpuBudget.bytes());assertSame(foreign,names.get(1));}assertEquals(1,calls[0]);}finally{TerrainGpuBudget.release(owner);}assertEquals(before,TerrainGpuBudget.bytes());
  }
 }
 @Test void currentNativeFallbackKeepsRankOrderWithoutUsingItAsAVisibilityGate(){
  byte[] order=TerrainResidentNativeFallbackPlan.order(new byte[]{7,3},2,section->section==3||section==7||section==200);assertArrayEquals(new byte[]{7,3,(byte)200},order);
  byte[] filtered=TerrainResidentNativeFallbackPlan.order(new byte[]{7,3},2,section->section==3||section==200);assertArrayEquals(new byte[]{3,(byte)200},filtered);
  assertThrows(IllegalArgumentException.class,()->TerrainResidentNativeFallbackPlan.order(new byte[]{3,3},2,section->true));
 }
 @Test void actualSceneResourceResetInvalidatesGpuSelectionWithoutReissuingPriorRegions()throws Throwable{
  var s=new Source();var plan=new TerrainResidentPassSubmission<Object>();long token=TerrainResidentScene.resourceToken();var driver=new TerrainResidentPassSubmission.Driver<Object>(){public boolean alive(){return true;}public boolean gpuValid(){return s.prepared.valid(s.list)&&TerrainResidentScene.resourceCurrent(token);}public TerrainResidentPassSubmission.Result execute(Object region,boolean gpu){s.calls.add((gpu?"gpu:":"native:")+(region==s.a?"a":"b"));return gpu?TerrainResidentPassSubmission.Result.GPU:TerrainResidentPassSubmission.Result.NATIVE;}};
  TerrainResidentScene.drive(plan,s.a,driver);TerrainResidentScene.reset();assertFalse(TerrainResidentScene.resourceCurrent(token));TerrainResidentScene.drive(plan,s.b,driver);TerrainResidentScene.drive(plan,s.a,driver);assertEquals(List.of("gpu:a","native:b"),s.calls);
 }
}
