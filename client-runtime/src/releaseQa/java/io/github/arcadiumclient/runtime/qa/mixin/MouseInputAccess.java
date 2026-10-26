package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface MouseInputAccess {
    @Invoker("mouseClicked") void release$press(int x, int y, int button);
    @Invoker("mouseReleased") void release$release(int x, int y, int button);
}
