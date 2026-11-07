// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import io.github.arcadiumclient.runtime.qa.ChatBookClicks073Qa;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatScreen.class)
public abstract class ChatBookClicks073ChatProbe {
 @Inject(method="mouseClicked",at=@At("HEAD")) private void clicks073$click(int x,int y,int button,CallbackInfo callback){ChatBookClicks073Qa.clicked((Screen)(Object)this,x,y,button);}
}
