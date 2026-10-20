// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.resource.Identifier;
import net.minecraft.world.World;

public final class LegacyQaPlayer extends RemoteClientPlayerEntity {
    public LegacyQaPlayer(World world, GameProfile profile) { super(world, profile); }
    @Override public Identifier getSkinTextureLocation() { return new Identifier("textures/entity/steve.png"); }
    @Override public Identifier getCapeTextureLocation() { return null; }
}
