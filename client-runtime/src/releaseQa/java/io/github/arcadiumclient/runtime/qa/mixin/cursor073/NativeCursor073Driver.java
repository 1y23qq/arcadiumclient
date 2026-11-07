// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.cursor073;
import io.github.arcadiumclient.runtime.qa.NativeCursor073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class NativeCursor073Driver {
 @Inject(method="tick",at=@At("TAIL")) private void cursor073$tick(CallbackInfo callback){NativeCursor073Qa.tick();}
 @Inject(method="runGame",at=@At("TAIL")) private void cursor073$frame(CallbackInfo callback){NativeCursor073Qa.frame();}
}
