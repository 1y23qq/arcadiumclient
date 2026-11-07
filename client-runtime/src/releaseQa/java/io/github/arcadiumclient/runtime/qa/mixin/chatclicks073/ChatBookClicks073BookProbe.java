// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(BookEditScreen.class)
public abstract class ChatBookClicks073BookProbe {
 @Inject(method="mouseClicked",at=@At("HEAD")) private void clicks073$click(int x,int y,int button,CallbackInfo callback){ChatBookClicks073Qa.clicked((Screen)(Object)this,x,y,button);}
 @Inject(method="getHoveredText",at=@At("RETURN")) private void clicks073$hit(int x,int y,CallbackInfoReturnable<Text> callback){ChatBookClicks073Qa.hit(callback.getReturnValue());}
 @Inject(method="handleClickEvent",at=@At("HEAD")) private void clicks073$actionStart(Text text,CallbackInfoReturnable<Boolean> callback){ChatBookClicks073Qa.action((Screen)(Object)this,text,false,false);}
 @Inject(method="handleClickEvent",at=@At("RETURN")) private void clicks073$actionEnd(Text text,CallbackInfoReturnable<Boolean> callback){ChatBookClicks073Qa.action((Screen)(Object)this,text,true,callback.getReturnValueZ());}
}
