package io.github.arcadiumclient.featuretest.mixin;
import io.github.arcadiumclient.featuretest.FeatureQa;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenProbeAccess {
    @Accessor("buttons") List<ButtonWidget> arcadium$buttons();
    @Invoker("mouseClicked") void arcadium$click(int x, int y, int button);
    @Invoker("mouseReleased") void arcadium$release(int x, int y, int button);
    @Invoker("mouseDragged") void arcadium$drag(int x, int y, int button, long duration);
}
