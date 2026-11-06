// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPlayerEntity.class)
public abstract class LocalCapePlayerMixin {
    @WrapMethod(method = "getCapeTextureLocation")
    private Identifier arcadium$localCape(Operation<Identifier> original) {
        Identifier accountCape = original.call();
        Identifier selected = LocalCape.textureFor(((ClientPlayerEntity) (Object) this).getUuid());
        return selected == null ? accountCape : selected;
    }
}
