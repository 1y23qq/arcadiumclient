// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
import java.util.IdentityHashMap;
final class TerrainResidentPassSubmission<R> {
 enum Result{GPU,NATIVE,EMPTY,ABORT,SKIP}
 interface Driver<R>{boolean alive();boolean gpuValid();Result execute(R region,boolean gpu)throws Throwable;}
 private final Thread owner=Thread.currentThread();
 private final IdentityHashMap<R,Result> attempted=new IdentityHashMap<>();
 private boolean nativeOnly,aborted,active;
 long gpuIssued,nativeIssued,empty,ambiguous,duplicates,lateFallbacks;
 Result run(R region,Driver<R> driver)throws Throwable{
  if(Thread.currentThread()!=owner||active){aborted=true;return Result.ABORT;}
  if(aborted||!driver.alive()){aborted=true;return Result.ABORT;}
  if(attempted.containsKey(region)){duplicates++;return Result.SKIP;}
  if(!driver.gpuValid()){if(!nativeOnly)lateFallbacks++;nativeOnly=true;}
  attempted.put(region,Result.ABORT);active=true;
  try{Result result=driver.execute(region,!nativeOnly);attempted.put(region,result);if(result==Result.GPU)gpuIssued++;else if(result==Result.NATIVE){nativeIssued++;nativeOnly=true;}else if(result==Result.EMPTY)empty++;else if(result==Result.ABORT)aborted=true;return result;}
  catch(Throwable failure){ambiguous++;nativeOnly=true;throw failure;}finally{active=false;}
 }
 void fallback(){nativeOnly=true;}
 boolean aborted(){return aborted;}
}
