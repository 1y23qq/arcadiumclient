package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameGui.class)
public interface GameGuiAnimationProbeAccess {
    @Invoker("hasCrosshair") boolean arcadium$hasCrosshair();
    @Accessor("titleTime") int arcadium$titleTime();
    @Accessor("titleTime") void arcadium$titleTime(int value);
    @Accessor("title") String arcadium$title();
    @Accessor("title") void arcadium$title(String value);
    @Accessor("subtitle") String arcadium$subtitle();
    @Accessor("subtitle") void arcadium$subtitle(String value);
    @Accessor("titleFadeInTime") int arcadium$titleFadeInTime();
    @Accessor("titleFadeInTime") void arcadium$titleFadeInTime(int value);
    @Accessor("titleDuration") int arcadium$titleDuration();
    @Accessor("titleDuration") void arcadium$titleDuration(int value);
    @Accessor("titleFadeOutTime") int arcadium$titleFadeOutTime();
    @Accessor("titleFadeOutTime") void arcadium$titleFadeOutTime(int value);
}
