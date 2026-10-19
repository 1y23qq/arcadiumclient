package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface PresenceMinecraftAccess {
    @Mutable @Accessor("session") void arcadium$session(Session session);
}
