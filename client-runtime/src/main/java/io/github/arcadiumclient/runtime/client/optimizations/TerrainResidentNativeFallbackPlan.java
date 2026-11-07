// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;
import java.util.Arrays;
import java.util.function.IntPredicate;
final class TerrainResidentNativeFallbackPlan {
 private TerrainResidentNativeFallbackPlan(){}
 static byte[] order(byte[] prefix,int count,IntPredicate included){
  if(count<0||count>256||count>prefix.length)throw new IllegalArgumentException("Unverified frozen native ranks");boolean[] seen=new boolean[256];byte[] result=new byte[256];int size=0;
  for(int i=0;i<count;i++){int section=prefix[i]&255;if(seen[section])throw new IllegalArgumentException("Duplicate native section");seen[section]=true;if(included.test(section))result[size++]=(byte)section;}
  for(int section=0;section<256;section++)if(!seen[section]&&included.test(section))result[size++]=(byte)section;return Arrays.copyOf(result,size);
 }
}
