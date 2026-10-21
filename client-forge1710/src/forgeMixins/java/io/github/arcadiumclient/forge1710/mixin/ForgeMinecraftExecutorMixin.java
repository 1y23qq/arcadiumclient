// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.internal.legacy.compat.MinecraftCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class ForgeMinecraftExecutorMixin implements java.util.concurrent.Executor {
    @Override @Unique
    public void execute(Runnable task) {
        if (isSameThread()) task.run();
        else ((MinecraftCompat) (Object) this).tell(task);
    }

    @Unique public void executeTask(Runnable task) { execute(task); }
    @Unique public boolean isSameThread() { return ((Minecraft) (Object) this).func_152345_ab(); }
}
