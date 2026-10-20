// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.skins.LegacyPlayerSkinModel;
import io.github.arcadiumclient.runtime.client.skins.LegacyPlayerSkins;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerRenderer.class)
public abstract class MixinLegacyPlayerSkins extends LivingEntityRenderer {
    @Shadow private HumanoidModel player;
    @Unique private LegacyPlayerSkinModel arcadium$classic;
    @Unique private LegacyPlayerSkinModel arcadium$slim;

    protected MixinLegacyPlayerSkins(Model model, float shadow) { super(model, shadow); }

    @Unique private LegacyPlayerSkinModel arcadium$model(ClientPlayerEntity entity) {
        if (LegacyPlayerSkins.slim(entity)) {
            if (arcadium$slim == null) arcadium$slim = new LegacyPlayerSkinModel(true);
            return arcadium$slim;
        }
        if (arcadium$classic == null) arcadium$classic = new LegacyPlayerSkinModel(false);
        return arcadium$classic;
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V")
    private void arcadium$renderSkin(ClientPlayerEntity entity, double x, double y, double z, float yaw, float delta, Operation<Void> original) {
        LegacyPlayerSkins.Skin skin = LegacyPlayerSkins.skin(entity);
        if (skin == null) { original.call(entity, x, y, z, yaw, delta); return; }
        LegacyPlayerSkinModel next = arcadium$model(entity);
        HumanoidModel previous = player;
        Model previousModel = model;
        ClientPlayerEntity previousOwner = next.owner();
        LegacyPlayerSkins.Skin previousSkin = next.skin();
        player = next;
        model = next;
        next.appearance(entity, skin);
        try { original.call(entity, x, y, z, yaw, delta); }
        finally { player = previous; model = previousModel; next.appearance(previousOwner, previousSkin); }
    }

    @WrapMethod(method = "renderRightArm")
    private void arcadium$renderHand(PlayerEntity entity, Operation<Void> original) {
        if (!(entity instanceof ClientPlayerEntity clientPlayer)) { original.call(entity); return; }
        LegacyPlayerSkins.Skin skin = LegacyPlayerSkins.skin(clientPlayer);
        if (skin == null) { original.call(entity); return; }
        LegacyPlayerSkinModel next = arcadium$model(clientPlayer);
        HumanoidModel previous = player;
        Model previousModel = model;
        ClientPlayerEntity previousOwner = next.owner();
        LegacyPlayerSkins.Skin previousSkin = next.skin();
        player = next;
        model = next;
        next.appearance(clientPlayer, skin);
        try { original.call(entity); }
        finally { player = previous; model = previousModel; next.appearance(previousOwner, previousSkin); }
    }
}
