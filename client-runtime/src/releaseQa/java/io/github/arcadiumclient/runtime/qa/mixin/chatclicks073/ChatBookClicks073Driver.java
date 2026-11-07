// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class ChatBookClicks073Driver {
 @Inject(method="tick",at=@At("TAIL")) private void clicks073$tick(CallbackInfo callback){ChatBookClicks073Qa.tick();}
 @Inject(method="runGame",at=@At("TAIL")) private void clicks073$frame(CallbackInfo callback){ChatBookClicks073Qa.frame();}
}
