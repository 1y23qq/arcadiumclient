// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ParticlePacketMathTest {
    private static final int CANARY=0x4a91c220;

    @Test void preservesNativeWindingAndAllVertexAttributes(){
        int[] output=new int[38];Arrays.fill(output,CANARY);
        int[] source=new int[22];System.arraycopy(packet(),0,source,3,15);
        ParticlePacketMath.quads(source,3,output,5);
        assertArrayEquals(golden(),Arrays.copyOfRange(output,5,33));
        for(int i=0;i<5;i++)assertEquals(CANARY,output[i]);
        for(int i=33;i<output.length;i++)assertEquals(CANARY,output[i]);
        assertArrayEquals(golden(),ParticlePacketMath.quads(source,3));
    }

    @Test void rejectsInvalidRangesBeforeWriting(){
        int[] output=new int[36];Arrays.fill(output,CANARY);int[] original=output.clone();
        for(int[] range:new int[][]{{-1,0},{1,0},{0,-1},{0,9},{Integer.MAX_VALUE,0},{0,Integer.MAX_VALUE}}){
            assertThrows(IllegalArgumentException.class,()->ParticlePacketMath.quads(packet(),range[0],output,range[1]));
            assertArrayEquals(original,output);
        }
        assertThrows(IllegalArgumentException.class,()->ParticlePacketMath.quads(null,0,output,0));
        assertArrayEquals(original,output);
        assertThrows(IllegalArgumentException.class,()->ParticlePacketMath.quads(packet(),0,null,0));
        assertThrows(IllegalArgumentException.class,()->ParticlePacketMath.quads(new int[14],0,output,0));
        assertArrayEquals(original,output);
    }

    @Test void rejectsOverlappingInputWithoutChangingEitherRange(){
        int[] shared=new int[64];System.arraycopy(packet(),0,shared,20,15);int[] original=shared.clone();
        for(int destination:new int[]{0,7,20,34}){
            assertThrows(IllegalArgumentException.class,()->ParticlePacketMath.quads(shared,20,shared,destination));
            assertArrayEquals(original,shared);
        }
    }

    @Test void permitsDisjointRangesInOneArray(){
        int[] shared=new int[64];System.arraycopy(packet(),0,shared,36,15);
        ParticlePacketMath.quads(shared,36,shared,0);
        assertArrayEquals(golden(),Arrays.copyOf(shared,28));
        assertArrayEquals(packet(),Arrays.copyOfRange(shared,36,51));
        System.arraycopy(packet(),0,shared,0,15);
        ParticlePacketMath.quads(shared,0,shared,36);
        assertArrayEquals(golden(),Arrays.copyOfRange(shared,36,64));
        assertArrayEquals(packet(),Arrays.copyOf(shared,15));
    }

    private static int[] packet(){return new int[]{b(1),b(2),b(3),b(.5f),b(.125f),b(.875f),b(.25f),b(.75f),0x89abcdef,0x00f000a0,b(.25f),b(.5f),b(.75f),b(-.125f),b(.375f)};}
    private static int[] golden(){return new int[]{
            b(.9375f),b(1.75f),b(2.4375f),b(.875f),b(.75f),0x89abcdef,0x00f000a0,
            b(.8125f),b(2.25f),b(2.8125f),b(.875f),b(.25f),0x89abcdef,0x00f000a0,
            b(1.0625f),b(2.25f),b(3.5625f),b(.125f),b(.25f),0x89abcdef,0x00f000a0,
            b(1.1875f),b(1.75f),b(3.1875f),b(.125f),b(.75f),0x89abcdef,0x00f000a0};}
    private static int b(float value){return Float.floatToRawIntBits(value);}
}
