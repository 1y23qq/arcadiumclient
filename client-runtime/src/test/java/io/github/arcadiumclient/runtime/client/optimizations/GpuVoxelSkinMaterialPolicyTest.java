// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinMaterialPolicyTest {
    @Test void observedOriginalUnblendedAlphaTestMaterialUsesTheCutoutPath(){
        assertTrue(accepts(0,false,true,GL11.GL_GREATER,.1f,true,true,GL11.GL_LEQUAL));
    }
    @Test void blendingOrCustomProgramsMustKeepTheOriginalRenderingPath(){
        assertFalse(accepts(0,true,true,GL11.GL_GREATER,.1f,true,true,GL11.GL_LEQUAL));
        assertFalse(accepts(17,false,true,GL11.GL_GREATER,.1f,true,true,GL11.GL_LEQUAL));
    }
    @Test void differentAlphaRulesCannotSilentlyChangeTransparentTexels(){
        assertFalse(accepts(0,false,false,GL11.GL_GREATER,.1f,true,true,GL11.GL_LEQUAL));
        for(int function:new int[]{GL11.GL_ALWAYS,GL11.GL_GEQUAL,GL11.GL_LESS,GL11.GL_EQUAL})
            assertFalse(accepts(0,false,true,function,.1f,true,true,GL11.GL_LEQUAL));
        for(float reference:new float[]{0,.5f,Math.nextDown(.1f),Math.nextUp(.1f),Float.NaN,Float.POSITIVE_INFINITY})
            assertFalse(accepts(0,false,true,GL11.GL_GREATER,reference,true,true,GL11.GL_LEQUAL));
    }
    @Test void depthTestWriteAndComparisonMustPreserveTheOriginalOcclusion(){
        assertFalse(accepts(0,false,true,GL11.GL_GREATER,.1f,false,true,GL11.GL_LEQUAL));
        assertFalse(accepts(0,false,true,GL11.GL_GREATER,.1f,true,false,GL11.GL_LEQUAL));
        for(int function:new int[]{GL11.GL_ALWAYS,GL11.GL_LESS,GL11.GL_EQUAL,GL11.GL_GEQUAL})
            assertFalse(accepts(0,false,true,GL11.GL_GREATER,.1f,true,true,function));
    }
    private static boolean accepts(int program,boolean blend,boolean alpha,int alphaFunction,float reference,boolean depth,boolean write,int depthFunction){
        return GpuVoxelSkinMaterialPolicy.standardCutout(program,blend,alpha,alphaFunction,reference,depth,write,depthFunction);
    }
}
