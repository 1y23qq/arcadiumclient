// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.access;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BufferBuilder.class)
public interface ParticleBufferAccess {
    @Accessor("uncolored") boolean arcadium$uncolored();
    @Accessor("offsetX") double arcadium$offsetX();
    @Accessor("offsetY") double arcadium$offsetY();
    @Accessor("offsetZ") double arcadium$offsetZ();
}
