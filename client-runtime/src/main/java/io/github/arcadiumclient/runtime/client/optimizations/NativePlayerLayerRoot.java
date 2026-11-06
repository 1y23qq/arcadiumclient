// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.lwjgl.opengl.GL11;

final class NativePlayerLayerRoot {
    private static final boolean REQUESTED=Boolean.getBoolean("arcadium.nativeLayerRoot072.replay");
    static long replays;
    private NativePlayerLayerRoot(){}
    static boolean enabled(){return REQUESTED&&ModernArmorGlintPipeline.useGeometryMvp()&&ModernArmorGlintPipeline.shaderAdapterEnabled();}
    static boolean valid(double x,double y,double z,float previousYaw,float currentYaw,float height){
        return Float.isFinite((float)x)&&Float.isFinite((float)y)&&Float.isFinite((float)z)
                &&Float.isFinite(previousYaw)&&Float.isFinite(currentYaw)&&Float.isFinite(currentYaw-previousYaw)
                &&Float.isFinite(height)&&height>0;
    }
    static void apply(double x,double y,double z,float bodyYaw,boolean upsideDown,float height){
        GL11.glTranslatef((float)x,(float)y,(float)z);
        GL11.glRotatef(180f-bodyYaw,0,1,0);
        if(upsideDown){GL11.glTranslatef(0,height+.1f,0);GL11.glRotatef(180,0,0,1);}
        GL11.glScalef(-1,-1,1);
        GL11.glScalef(.9375f,.9375f,.9375f);
        GL11.glTranslatef(0,-1.5078125f,0);
        replays++;
    }
}
