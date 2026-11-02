// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.polyblur;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.List;
import net.minecraft.client.render.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfModLoaded("polyblur")
@Mixin(PostPass.class)
public interface HybridBlurPassAccess {
    @Accessor("auxAssets") List<Object> arcadium$blurAuxAssets();
    @Accessor("auxNames") List<String> arcadium$blurAuxNames();
    @Accessor("auxWidths") List<Integer> arcadium$blurAuxWidths();
    @Accessor("auxHeights") List<Integer> arcadium$blurAuxHeights();
}
