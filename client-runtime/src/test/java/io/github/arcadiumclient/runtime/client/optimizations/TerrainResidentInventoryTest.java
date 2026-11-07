// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TerrainResidentInventoryTest {
 private static final class Fixture {
  final TerrainResidentInventory inventory=new TerrainResidentInventory(new Object());
  final Object region=new Object(),storage=new Object();Object lists=new Object();int frame;
  Fixture(){mutate(()->{inventory.addRegion(region,0,0,0);inventory.addStorage(region,storage,0,mesh(3,100));inventory.publishOrder(lists,0,List.of(row(region,3)));});}
  void mutate(Runnable body){var ticket=inventory.begin();boolean ok=false;try{body.run();ok=true;}finally{try{inventory.end(ticket,ok);}catch(RuntimeException failure){if(ok)throw failure;}}}
  TerrainResidentInventory.Frame view(){return inventory.seal(0,lists);}
  void ack(){inventory.acknowledge(view());}
  void publish(List<TerrainResidentInventory.OrderRow> rows){Object next=new Object();mutate(()->inventory.publishOrder(next,++frame,rows));lists=next;}
 }
 private static TerrainResidentInventory.OrderRow row(Object region,int... ids){byte[] ranks=new byte[ids.length];for(int i=0;i<ids.length;i++)ranks[i]=(byte)ids[i];return new TerrainResidentInventory.OrderRow(region,new Object(),ranks,ranks.length);}
 private static int[] mesh(int section,int first){int[] result=new int[5120];System.arraycopy(section(first),0,result,section*20,20);return result;}
 private static int[] section(int first){int[] row=new int[20];row[10]=first;row[18]=6;return row;}
 @Test void unchangedFramesReturnTheSameSealedViewWithoutInventoryOrRankWork(){
  var f=new Fixture();f.ack();var expected=f.view();long visits=f.inventory.topologyVisits,reads=f.inventory.metadataRowsRead,orders=f.inventory.orderRowsCopied;
  for(int i=0;i<10000;i++){f.inventory.acknowledge(expected);assertSame(expected,f.view());assertTrue(expected.valid(f.lists));}
  assertEquals(visits,f.inventory.topologyVisits);assertEquals(reads,f.inventory.metadataRowsRead);assertEquals(orders,f.inventory.orderRowsCopied);assertTrue(expected.metadata().isEmpty());assertTrue(expected.orders().isEmpty());
 }
 @Test void onlyChangedAdjacentRowsAreUploadedAndEqualSizeEditsStillPublish(){
  var f=new Fixture();f.ack();var before=f.view();f.mutate(()->{f.inventory.section(f.storage,3,section(104));f.inventory.section(f.storage,4,section(200));});var current=f.view();
  assertFalse(before.valid(f.lists));assertEquals(1,current.metadata().size());var span=current.metadata().getFirst();assertEquals(60,span.firstWord);assertEquals(40,span.length());assertEquals(104,span.word(10));assertEquals(200,span.word(30));assertTrue(current.orders().isEmpty());assertEquals(2,f.inventory.metadataRowsRead);
  f.ack();var stableOrder=f.view();long old=stableOrder.throughUpload;f.mutate(()->f.inventory.section(f.storage,3,section(104)));assertTrue(f.view().throughUpload>old);assertEquals(stableOrder.layoutVersion,f.view().layoutVersion);assertSame(stableOrder.drawOrder(),f.view().drawOrder());assertEquals(20,f.view().metadata().getFirst().length());
 }
 @Test void spansAreImmutableAndHaveExactNativeWordOffsetsAndSentinelBounds(){
  var f=new Fixture();var initial=f.view();int[] copy=initial.metadata().getFirst().copy();copy[70]=999;assertEquals(100,initial.metadata().getFirst().word(70));
  f.ack();f.mutate(()->f.inventory.section(f.storage,3,section(108)));var span=f.view().metadata().getFirst();int[] target=new int[24];Arrays.fill(target,0x12345678);var buffer=IntBuffer.wrap(target);buffer.position(2);buffer.limit(22);span.put(buffer);assertEquals(22,buffer.position());assertEquals(108,target[12]);assertEquals(0x12345678,target[0]);assertEquals(0x12345678,target[23]);assertEquals(100,initial.metadata().getFirst().word(70));
 }
 @Test void nativePrefixIsExactAndOmittedResidentRegionsUseOnlyTheDeterministicSuffix(){
  var f=new Fixture();Object left=new Object(),right=new Object(),empty=new Object();f.mutate(()->{f.inventory.addRegion(left,-128,0,0);f.inventory.addStorage(left,new Object(),0,mesh(1,200));f.inventory.addRegion(right,128,0,0);f.inventory.addStorage(right,new Object(),0,mesh(1,300));f.inventory.addRegion(empty,256,0,0);});
  f.publish(List.of(row(right,1),row(empty),row(f.region,3)));var rows=f.view().drawOrder();assertEquals(List.of(right,empty,f.region,left),rows.stream().map(TerrainResidentInventory.RegionRef::region).toList());assertEquals(-1,rows.get(1).gpuSlot());assertEquals(3,f.view().activeRegions);
 }
 @Test void lateStorageInheritsTheAlreadyPublishedNativeRankAndReusedSlotsGetNewGeneration(){
  var f=new Fixture();Object r=new Object(),storage=new Object();f.mutate(()->f.inventory.addRegion(r,128,0,0));f.publish(List.of(row(r,255,3),row(f.region,3)));f.mutate(()->f.inventory.addStorage(r,storage,0,mesh(255,300)));
  var initial=f.view();var identity=initial.drawOrder().getFirst();int index=identity.gpuSlot();assertEquals(2,initial.orders().stream().filter(s->s.firstWord==index*65).findFirst().orElseThrow().word(0));long generation=identity.storageGeneration();f.ack();
  f.mutate(()->{f.inventory.removeStorage(storage);f.inventory.addStorage(r,storage,0,mesh(255,900));});var replacement=f.view().drawOrder().getFirst();assertEquals(index,replacement.gpuSlot());assertTrue(replacement.storageGeneration()>generation);assertFalse(initial.valid(f.lists));
 }
 @Test void nativeRankInputsAreCopiedAndUnchangedPublicationsDoNotRewriteRankBuffers(){
  var f=new Fixture();byte[] ranks={(byte)255,3};var row=new TerrainResidentInventory.OrderRow(f.region,new Object(),ranks,2);ranks[0]=0;f.publish(List.of(row));var order=f.view().orders().getFirst();assertEquals(2,order.word(0));assertEquals(0x3ff,order.word(1));f.ack();f.publish(List.of(new TerrainResidentInventory.OrderRow(f.region,new Object(),new byte[]{(byte)255,3},2)));assertTrue(f.view().orders().isEmpty());
 }
 @Test void deletionKeepsTheActualStaleNativePrefixAsAnEmptyTombstoneUntilPublication(){
  var f=new Fixture();f.ack();var old=f.view();f.mutate(()->f.inventory.removeRegion(f.region));var removed=f.view();assertEquals(0,removed.activeRegions);assertSame(f.region,removed.drawOrder().getFirst().region());assertEquals(-1,removed.drawOrder().getFirst().gpuSlot());assertEquals(5120,removed.metadata().getFirst().length());assertTrue(Arrays.stream(removed.metadata().getFirst().copy()).allMatch(x->x==0));assertFalse(old.valid(f.lists));f.publish(List.of());assertTrue(f.view().drawOrder().isEmpty());
 }
 @Test void removedRegionCoordinatesCanBeReusedWithoutRedirectingTheOldPrefix(){
  var f=new Fixture();long generation=f.view().drawOrder().getFirst().generation();Object replacement=new Object(),storage=new Object();f.mutate(()->{f.inventory.removeRegion(f.region);f.inventory.addRegion(replacement,0,0,0);f.inventory.addStorage(replacement,storage,0,mesh(3,700));});var current=f.view();assertEquals(2,current.drawOrder().size());assertSame(f.region,current.drawOrder().get(0).region());assertNull(current.drawOrder().get(0).storage());assertSame(replacement,current.drawOrder().get(1).region());assertTrue(current.drawOrder().get(1).generation()>generation);
 }
 @Test void theTwoPassesKeepIndependentRowsMaximumsAndEmptyTransitions(){
  var f=new Fixture();Object cutout=new Object();f.mutate(()->f.inventory.addStorage(f.region,cutout,1,mesh(9,500)));var solid=f.view();var cut=f.inventory.seal(1,f.lists);assertEquals(100,solid.metadata().getFirst().word(70));assertEquals(500,cut.metadata().getFirst().word(190));f.inventory.acknowledge(solid);f.inventory.acknowledge(cut);
  f.mutate(()->f.inventory.section(cutout,9,new int[20]));assertTrue(f.view().metadata().isEmpty());var empty=f.inventory.seal(1,f.lists);assertEquals(0,empty.activeRegions);assertEquals(0,empty.maximumElements);assertEquals(20,empty.metadata().getFirst().length());
 }
 @Test void arenaRelocationPublishesAllRowsWhileBindingAndFadeEventsAvoidMetadataCopies(){
  var f=new Fixture();f.ack();f.mutate(()->f.inventory.relocate(f.storage,mesh(3,4096)));var relocated=f.view();assertEquals(5120,relocated.metadata().getFirst().length());assertEquals(256,f.inventory.metadataRowsRead);f.ack();long bindings=f.view().drawOrder().getFirst().bindings();f.mutate(()->{f.inventory.bindingsChanged(f.region);f.inventory.loadTime(f.region,12345);});var changed=f.view();assertTrue(changed.metadata().isEmpty());assertTrue(changed.orders().isEmpty());assertEquals(bindings+1,changed.drawOrder().getFirst().bindings());assertEquals(12345,changed.newestLoad);
 }
 @Test void nativeListIdentityAndSuccessfulPublicationGateCameraReentry(){
  var f=new Fixture();var old=f.view();Object missing=new Object();assertFalse(old.valid(missing));assertThrows(IllegalStateException.class,()->f.inventory.seal(0,missing));f.publish(List.of(row(f.region,3)));assertFalse(old.valid(f.lists));assertTrue(f.view().valid(f.lists));
  var ready=f.view();var ticket=f.inventory.begin();assertFalse(ready.valid(f.lists));assertThrows(IllegalStateException.class,f::view);f.inventory.end(ticket,true);assertTrue(f.view().valid(f.lists));
 }
 @Test void aDroppedCompletionFailureOrOutOfOrderPublicationCannotReachDraw(){
  var f=new Fixture();var frame=f.view();var first=f.inventory.begin();var second=f.inventory.begin();assertFalse(frame.valid(f.lists));assertThrows(IllegalStateException.class,()->f.inventory.end(first,true));assertNotNull(f.inventory.failure());assertFalse(frame.valid(f.lists));
  var failed=new Fixture();var previous=failed.view();var operation=failed.inventory.begin();failed.inventory.end(operation,false);assertFalse(previous.valid(failed.lists));
  var stale=new Fixture();stale.publish(List.of(row(stale.region,3)));assertThrows(IllegalStateException.class,()->stale.mutate(()->stale.inventory.publishOrder(new Object(),0,List.of(row(stale.region,3)))));assertNotNull(stale.inventory.failure());
 }
 @Test void unknownDuplicateAndMalformedSourceEventsFailClosed(){
  var unknown=new Fixture();assertThrows(IllegalStateException.class,()->unknown.mutate(()->unknown.inventory.section(new Object(),3,section(100))));assertNotNull(unknown.inventory.failure());
  assertThrows(IllegalArgumentException.class,()->new TerrainResidentInventory.OrderRow(new Object(),new Object(),new byte[]{1,1},2));
  var duplicate=new Fixture();assertThrows(IllegalStateException.class,()->duplicate.mutate(()->duplicate.inventory.publishOrder(new Object(),1,List.of(row(duplicate.region,3),row(duplicate.region,4)))));assertNotNull(duplicate.inventory.failure());
  var malformed=new Fixture();int[] invalid=section(-1);assertThrows(IllegalArgumentException.class,()->malformed.mutate(()->malformed.inventory.section(malformed.storage,3,invalid)));assertNotNull(malformed.inventory.failure());
 }
 @Test void foreignThreadCannotReadOrMutateAndWorldRetirementInvalidatesAllViews()throws Exception{
  var f=new Fixture();var frame=f.view();AtomicReference<Throwable> error=new AtomicReference<>();var thread=new Thread(()->{try{f.inventory.begin();}catch(Throwable e){error.set(e);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,error.get());assertFalse(frame.valid(f.lists));
  var world=new Fixture();var before=world.view();world.inventory.reset();assertFalse(before.valid(world.lists));assertThrows(IllegalStateException.class,world::view);
 }
 @Test void replayIsExplicitAndBoundedAfterAConsumerAllocationOrPartialUploadFailure(){
  var f=new Fixture();f.ack();assertFalse(f.view().fullReplay);f.mutate(()->f.inventory.section(f.storage,3,section(200)));var partial=f.view();assertEquals(20,partial.metadata().getFirst().length());f.inventory.requestReplay(0);assertFalse(partial.valid(f.lists));var replay=f.view();assertTrue(replay.fullReplay);assertEquals(5120,replay.metadata().getFirst().length());assertEquals(65,replay.orders().getFirst().length());f.inventory.acknowledge(replay);assertTrue(f.view().metadata().isEmpty());assertFalse(f.view().fullReplay);
 }
 @Test void sourceBoundsRefuseAnUntrackedRegionBeforeAnyAdditionalStorageAllocation(){
  var inventory=new TerrainResidentInventory(new Object());var ticket=inventory.begin();for(int i=0;i<1024;i++)inventory.addRegion(new Object(),i*128,0,0);inventory.addRegion(new Object(),1024*128,0,0);assertNotNull(inventory.failure());assertThrows(IllegalStateException.class,()->inventory.end(ticket,true));
 }
}
