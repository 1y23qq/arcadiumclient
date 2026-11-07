// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Screen.class)
public abstract class ChatBookClicks073ScreenProbe {
 @Inject(method="handleMouse",at=@At("HEAD")) private void clicks073$mouseStart(CallbackInfo callback){ChatBookClicks073Qa.mouseEvent((Screen)(Object)this,true);}
 @Inject(method="handleMouse",at=@At("RETURN")) private void clicks073$mouseEnd(CallbackInfo callback){ChatBookClicks073Qa.mouseEvent((Screen)(Object)this,false);}
 @Inject(method="handleClickEvent",at=@At("HEAD")) private void clicks073$actionStart(Text text,CallbackInfoReturnable<Boolean> callback){ChatBookClicks073Qa.action((Screen)(Object)this,text,false,false);}
 @Inject(method="handleClickEvent",at=@At("RETURN")) private void clicks073$actionEnd(Text text,CallbackInfoReturnable<Boolean> callback){ChatBookClicks073Qa.action((Screen)(Object)this,text,true,callback.getReturnValueZ());}
}
