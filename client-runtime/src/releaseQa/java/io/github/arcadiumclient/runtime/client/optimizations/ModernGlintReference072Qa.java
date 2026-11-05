// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import io.github.arcadiumclient.runtime.qa.ModernGlintPixelTrace072Qa;
import net.minecraft.client.render.model.ModelPart;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ModernGlintReference072Qa {
    private static PlayerBatchAccess api;
    private ModernGlintReference072Qa() {}

    public static void part(Object capture, ModelPart part, float scale) {
        if (!ModernGlint072Qa.needsModelViewReference() || scale != .0625f || !part.visible || part.invisible) return;
        try {
            if (api == null) api = new PlayerBatchAccess();
            if (!(boolean)api.capturePlayer.get(capture) || !(boolean)api.captureArmor.get(capture) || !(boolean)api.captureActive.get(capture)) return;
            Matrix4f matrix = new Matrix4f((Matrix4fc)api.captureMatrices.get(capture));
            ModernGlint072Qa.optimizedRootModelView(matrix);
            matrix.translate(part.translateX, part.translateY, part.translateZ).translate(part.x * scale, part.y * scale, part.z * scale)
                    .rotateZ(part.rotationZ).rotateY(part.rotationY).rotateX(part.rotationX);
            ModernGlint072Qa.optimizedModelView(part, matrix);
            ModernGlintPixelTrace072Qa.captured(part, (Matrix4fc)api.captureMatrices.get(capture), matrix,
                    String.valueOf(api.capturePass.get(capture)), (int)api.captureLight.get(capture));
        } catch (Throwable error) { ModernGlint072Qa.referenceFailure(error); }
    }
}
