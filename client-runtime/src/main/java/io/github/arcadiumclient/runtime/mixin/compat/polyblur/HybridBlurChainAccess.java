// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.polyblur;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
import net.minecraft.client.render.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfModLoaded("polyblur")
@Mixin(PostChain.class)
public interface HybridBlurChainAccess {
    @Accessor("passes") List<PostPass> arcadium$blurPasses();
    @Accessor("screenTarget") RenderTarget arcadium$blurMain();
    @Accessor("customRenderTargets") Map<String, RenderTarget> arcadium$blurTargets();
    @Accessor("fullSizedTargets") List<RenderTarget> arcadium$blurFullSizedTargets();
}
