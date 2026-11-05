// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;

final class GpuResidentBodyPacket {
    static final int WORDS=19,MAX_JOBS=8192,MAX_PROFILES=256;
    private GpuResidentBodyPacket() { }
    static void write(int[] output,int offset,int job,int mask,int profile,int light,float age,float x,float y,float z) {
        if(output==null||offset<0||offset>output.length-WORDS||job<0||job>=MAX_JOBS
                ||mask<1||mask>0xfff||profile<0||profile>=MAX_PROFILES||(light&0xff000000)!=0
                ||!Float.isFinite(age)||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
            throw new IllegalArgumentException("Unsupported resident body record");
        Arrays.fill(output,offset,offset+WORDS,0);
        output[offset]=output[offset+4]=output[offset+8]=Float.floatToRawIntBits(1);
        output[offset+9]=Float.floatToRawIntBits(x);output[offset+10]=Float.floatToRawIntBits(y);output[offset+11]=Float.floatToRawIntBits(z);
        output[offset+12]=Float.floatToRawIntBits(age);output[offset+13]=light;output[offset+14]=-1;
        output[offset+16]=job|(mask<<16);output[offset+17]=profile;
    }
}
