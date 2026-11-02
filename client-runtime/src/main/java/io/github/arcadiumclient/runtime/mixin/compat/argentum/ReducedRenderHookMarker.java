// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("argentum")
@Mixin(value = GlStateManager.class, priority = 1)
public abstract class ReducedRenderHookMarker { }
