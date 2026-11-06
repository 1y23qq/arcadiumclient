// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerInfo.class)
public abstract class LocalCapePlayerInfoMixin {
    @WrapMethod(method = "getCapeTexture")
    private Identifier arcadium$localCapeInfo(Operation<Identifier> original) {
        Identifier accountCape = original.call();
        Identifier selected = LocalCape.textureFor(((PlayerInfo) (Object) this).getProfile().getId());
        return selected == null ? accountCape : selected;
    }
}
