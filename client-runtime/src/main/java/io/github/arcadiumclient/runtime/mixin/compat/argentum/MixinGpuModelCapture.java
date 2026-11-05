// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.NativePlayerPosePipeline;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.lang.invoke.*;
import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityCapture", remap = false)
public abstract class MixinGpuModelCapture {
    @Shadow private Matrix4fStack matrices;
    @Shadow private boolean player;
    @Shadow private boolean item;
    @Shadow private boolean armorLayer;
    @Unique private boolean arcadium$recording;
    @Unique private final Matrix4f arcadium$root = new Matrix4f();
    @Unique private ModelPart arcadium$part;
    @Unique private float arcadium$scale;
    @Unique private static MethodHandle arcadium$submitBoxes;
    @Unique private static MethodHandle arcadium$pass;
    @Unique private final NativePlayerPosePipeline.CaptureState arcadium$nativePose = new NativePlayerPosePipeline.CaptureState();

    @Inject(method = "beginEntity", at = @At("HEAD"), remap = false)
    private void arcadium$countPlayer(@Coerce Object model, @Coerce Object texture, boolean isPlayer, boolean preserveFixedFunction,
            int light, float time, float red, float green, float blue, float alpha, CallbackInfo callback) {
        arcadium$nativePose.begin();
    }

    @WrapMethod(method = "recordPart")
    private void arcadium$compactPart(ModelPart part, float scale, Operation<Void> original) {
        if (player && !item && NativePlayerPosePipeline.capture(arcadium$nativePose, (Object)this, part, scale)) return;
        if (!BetaOptimizationsConfig.getGpuModelTransforms()||!GpuModelTransforms.ready()) { original.call(part, scale); return; }
        if (!player || item || armorLayer || ((BetaModelPartAccess)part).arcadium$baseModel().getClass() != PlayerModel.class
                || part.getClass() != ModelPart.class || part.invisible || !part.visible
                || part.boxes.size() != 1 || (part.children != null && !part.children.isEmpty()) || !GpuModelTransforms.ready()) {
            if (player && part.visible && !part.invisible && !part.boxes.isEmpty()) GpuModelTransforms.fallbackParts++;
            original.call(part, scale); return;
        }
        boolean submitted = false;
        try {
            if (arcadium$submitBoxes == null) {
                var lookup = MethodHandles.privateLookupIn(((Object)this).getClass(), MethodHandles.lookup());
                var type = ((Object)this).getClass();
                arcadium$submitBoxes = lookup.findVirtual(type, "submitBoxes", MethodType.methodType(boolean.class, ModelPart.class, float.class));
                var field = type.getDeclaredField("pass"); field.setAccessible(true);
                arcadium$pass = lookup.unreflectGetter(field);
            }
            String pass = ((Enum<?>) arcadium$pass.invoke((Object)this)).name();
            if (!pass.equals("NORMAL") && !pass.equals("TRANSLUCENT")) {
                GpuModelTransforms.fallbackParts++;
            } else {
                arcadium$root.set(matrices); arcadium$part = part; arcadium$scale = scale;
                matrices.pushMatrix().identity(); arcadium$recording = true;
                try { submitted = (boolean) arcadium$submitBoxes.invoke((Object)this, part, scale); }
                finally { arcadium$recording = false; matrices.popMatrix(); arcadium$part = null; }
                if (!submitted) GpuModelTransforms.fallbackParts++;
            }
        } catch (Throwable error) {
            GpuModelTransforms.disable(error);
        }
        if (!submitted) original.call(part, scale);
    }

    @ModifyArg(method = "submitBoxes", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/entity/instancing/EntityCapture;submit(Ldev/rdh/argentum/impl/render/entity/instancing/InstanceGeometry;Lorg/joml/Matrix4f;Ldev/rdh/argentum/api/render/instancing/BoxTemplate;)Z"), index = 1, remap = false)
    private Matrix4f arcadium$queueTransform(Matrix4f localBox) {
        if (!arcadium$recording) return localBox;
        ModelPart part = arcadium$part;
        return GpuModelTransforms.enqueue(arcadium$root,
                part.translateX + part.x * arcadium$scale, part.translateY + part.y * arcadium$scale, part.translateZ + part.z * arcadium$scale,
                part.rotationX, part.rotationY, part.rotationZ, localBox);
    }
}
