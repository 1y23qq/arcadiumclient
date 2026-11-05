// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.lwjgl.opengl.GL11;

final class GpuVoxelSkinMaterialPolicy {
    private GpuVoxelSkinMaterialPolicy(){}
    static boolean standardCutout(int program,boolean blend,boolean alphaTest,int alphaFunction,float alphaReference,
                                  boolean depthTest,boolean depthWrite,int depthFunction){
        return program==0&&!blend&&alphaTest&&alphaFunction==GL11.GL_GREATER
                &&Float.floatToRawIntBits(alphaReference)==Float.floatToRawIntBits(.1f)
                &&depthTest&&depthWrite&&depthFunction==GL11.GL_LEQUAL;
    }
}
