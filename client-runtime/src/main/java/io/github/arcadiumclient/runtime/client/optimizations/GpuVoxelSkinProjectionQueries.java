// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GpuVoxelSkinProjectionQueries implements GpuVoxelSkinProjectionRows.Queries {
    private final FloatBuffer scratch=BufferUtils.createFloatBuffer(16);
    public int matrixMode(){GpuVoxelSkinPhysicalProjection.modeQueries++;return GL11.glGetInteger(GL11.GL_MATRIX_MODE);}
    public void modelView(float[] destination){GpuVoxelSkinPhysicalProjection.modelViewQueries++;read(GL11.GL_MODELVIEW_MATRIX,destination);}
    public void projection(float[] destination){GpuVoxelSkinPhysicalProjection.projectionQueries++;read(GL11.GL_PROJECTION_MATRIX,destination);}
    private void read(int name,float[] destination) {
        if(destination.length!=16||!scratch.isDirect()||scratch.order()!=ByteOrder.nativeOrder())throw new IllegalArgumentException("Bounded native-order matrix destination required");
        scratch.clear();GL11.glGetFloatv(name,scratch);scratch.get(destination);
    }
}
