// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.NativePoseReference072Qa;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class ForgePose072SubmitReference {
    @WrapMethod(method = "submit(Ldev/rdh/argentum/impl/render/entity/instancing/InstanceGeometry;Lorg/joml/Matrix4f;Ldev/rdh/argentum/api/render/instancing/BoxTemplate;)Z")
    private boolean forgePose$nativeReference(@Coerce Object geometry, Matrix4f matrix, @Coerce Object box, Operation<Boolean> original) {
        if (NativePoseReference072Qa.active(this)) return NativePoseReference072Qa.submit(this, matrix, box);
        return original.call(geometry, matrix, box);
    }
}
