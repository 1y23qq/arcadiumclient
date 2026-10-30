package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextRenderer.class)
public interface Chat059FontAccess {
    @Accessor("x") float release$x();
}
