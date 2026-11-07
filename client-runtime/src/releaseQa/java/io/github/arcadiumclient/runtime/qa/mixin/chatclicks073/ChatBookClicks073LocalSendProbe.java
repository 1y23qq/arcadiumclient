// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalClientPlayerEntity.class)
public abstract class ChatBookClicks073LocalSendProbe {
 @Inject(method="sendChat",at=@At("RETURN")) private void clicks073$sent(String text,CallbackInfo callback){ChatBookClicks073Qa.sent(text);}
}
