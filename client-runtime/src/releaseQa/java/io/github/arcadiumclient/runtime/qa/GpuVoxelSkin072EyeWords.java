// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

public final class GpuVoxelSkin072EyeWords {
    public static final int CAPACITY=64;
    private GpuVoxelSkin072EyeWords(){}
    public static float encode(int index,int count){
        if(count<1||count>CAPACITY||index<0||index>=count)throw new IllegalArgumentException("Eye matrix index outside current batch");
        return -(index+1);
    }
    public static int decode(int word,int count){
        if(count<1||count>CAPACITY)throw new IllegalArgumentException("Invalid eye matrix batch size");
        float encoded=Float.intBitsToFloat(word);int index=(int)(-encoded)-1;
        if(!Float.isFinite(encoded)||encoded>=0||index<0||index>=count||encoded!=-(index+1))throw new IllegalArgumentException("Invalid native eye matrix index word");
        return index;
    }
}
