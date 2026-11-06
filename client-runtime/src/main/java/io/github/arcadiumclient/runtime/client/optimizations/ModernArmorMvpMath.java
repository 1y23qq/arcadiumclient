// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ModernArmorMvpMath {
    private ModernArmorMvpMath(){}
    static void multiply(float[] left,float[] right,float[] destination){
        if(left.length!=16||right.length!=16||destination.length!=16||destination==left||destination==right)throw new IllegalArgumentException("Independent sixteen-word matrix buffers required");
        for(int column=0;column<4;column++)for(int row=0;row<4;row++){
            float a=left[row]*right[column*4],b=left[4+row]*right[column*4+1],c=left[8+row]*right[column*4+2],d=left[12+row]*right[column*4+3];
            destination[column*4+row]=(a+b)+(c+d);
        }
    }
    static void clip(float[] matrix,float x,float y,float z,float[] destination){
        if(matrix.length!=16||destination.length!=4)throw new IllegalArgumentException("Invalid clip storage");
        for(int row=0;row<4;row++){float value=Math.fma(matrix[row],x,0f);value=Math.fma(matrix[4+row],y,value);value=Math.fma(matrix[8+row],z,value);destination[row]=Math.fma(matrix[12+row],1f,value);}
    }
}
