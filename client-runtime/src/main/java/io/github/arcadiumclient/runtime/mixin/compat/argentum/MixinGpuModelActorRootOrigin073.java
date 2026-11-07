package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface MixinGpuModelActorRootOrigin073 {
    @Accessor("offsetX") double arcadium$rootOffsetX();
    @Accessor("offsetY") double arcadium$rootOffsetY();
    @Accessor("offsetZ") double arcadium$rootOffsetZ();
}
