package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.widget.ListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ListWidget.class)
public interface ListWidgetProbeAccess {
    @Accessor("minY") int qa028$top();
    @Accessor("minX") int qa028$left();
    @Accessor("width") int qa028$width();
    @Accessor("headerHeight") int qa028$headerHeight();
}
