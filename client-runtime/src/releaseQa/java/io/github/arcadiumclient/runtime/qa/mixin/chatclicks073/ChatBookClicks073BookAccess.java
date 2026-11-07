// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.chatclicks073;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BookEditScreen.class)
public interface ChatBookClicks073BookAccess {
    @Accessor("currentPage") int clicks073$currentPage();
    @Accessor("unsigned") boolean clicks073$unsigned();
}
