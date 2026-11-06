// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class GpuVoxelSkinMvpMath {
    private GpuVoxelSkinMvpMath(){}
    public static void multiply(float[] projection,float[] physicalEye,float[] destination){
        if(projection.length!=16||physicalEye.length!=16||destination.length!=16||projection==destination||physicalEye==destination)
            throw new IllegalArgumentException("Independent sixteen-word matrices required");
        for(int i=0;i<16;i++)if(!Float.isFinite(projection[i])||!Float.isFinite(physicalEye[i]))throw new IllegalArgumentException("Non-finite physical matrix");
        for(int column=0;column<4;column++)for(int row=0;row<4;row++){
            float a=projection[row]*physicalEye[column*4],b=projection[4+row]*physicalEye[column*4+1];
            float c=projection[8+row]*physicalEye[column*4+2],d=projection[12+row]*physicalEye[column*4+3];
            float value=(a+b)+(c+d);if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite combined MVP");destination[column*4+row]=value;
        }
    }
}
