// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ChatGui.class)
public abstract class ChatBookClicks073ChatGuiProbe {
 @Inject(method="getMessageAt",at=@At("RETURN")) private void clicks073$hit(int x,int y,CallbackInfoReturnable<Text> callback){ChatBookClicks073Qa.hit(callback.getReturnValue());}
 @Inject(method="addMessage(Lnet/minecraft/text/Text;IIZ)V",at=@At("HEAD")) private void clicks073$received(Text text,int id,int tick,boolean refresh,CallbackInfo callback){ChatBookClicks073Qa.received(text);}
}
