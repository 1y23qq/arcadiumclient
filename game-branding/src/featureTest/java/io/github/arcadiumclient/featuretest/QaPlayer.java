package io.github.arcadiumclient.featuretest;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;

public final class QaPlayer extends RemoteClientPlayerEntity {
    public QaPlayer(World world, GameProfile profile) { super(world, profile); }
    @Override public Identifier getSkinTextureLocation() { return new Identifier("textures/entity/steve.png"); }
    @Override public Identifier getCapeTextureLocation() { return null; }
    @Override public String getModelType() { return "default"; }
    @Override public boolean isSpectator() { return false; }
}
