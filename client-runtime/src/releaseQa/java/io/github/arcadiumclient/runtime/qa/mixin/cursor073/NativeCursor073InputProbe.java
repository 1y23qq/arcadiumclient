// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.cursor073;
import io.github.arcadiumclient.runtime.qa.NativeCursor073Qa;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class NativeCursor073InputProbe {
 @Inject(method="handleMouse",at=@At("HEAD")) private void cursor073$mouse(CallbackInfo callback){NativeCursor073Qa.mouse((Screen)(Object)this);}
 @Inject(method="handleKeyboard",at=@At("HEAD")) private void cursor073$key(CallbackInfo callback){NativeCursor073Qa.key((Screen)(Object)this);}
}
