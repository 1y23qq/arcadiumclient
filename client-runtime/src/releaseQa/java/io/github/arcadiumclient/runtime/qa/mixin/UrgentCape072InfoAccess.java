// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.network.PlayerInfo;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInfo.class)
public interface UrgentCape072InfoAccess {
    @Accessor("waitingForTextures") void cape072$waitingForTextures(boolean waiting);
    @Accessor("capeTexture") void cape072$capeTexture(Identifier texture);
}
