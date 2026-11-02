// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.polyblur;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.nio.FloatBuffer;
import net.minecraft.client.render.shaders.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfModLoaded("polyblur")
@Mixin(Uniform.class)
public interface HybridBlurUniformAccess {
    @Accessor("floatValues") FloatBuffer arcadium$blurFloatValues();
}
