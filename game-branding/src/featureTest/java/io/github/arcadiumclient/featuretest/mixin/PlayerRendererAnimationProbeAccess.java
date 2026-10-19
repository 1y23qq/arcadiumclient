package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerRenderer.class)
public interface PlayerRendererAnimationProbeAccess {
    @Accessor("thinArms") boolean arcadium$thinArms();
}
