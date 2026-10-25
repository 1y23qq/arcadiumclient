// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface ZoomClickAccess {
    @Accessor("clickCount") int arcadium$clicks();
    @Accessor("clickCount") void arcadium$clicks(int value);
}
