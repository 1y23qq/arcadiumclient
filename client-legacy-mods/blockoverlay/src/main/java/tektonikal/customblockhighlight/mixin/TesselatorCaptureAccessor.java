// SPDX-License-Identifier: GPL-3.0-only
package tektonikal.customblockhighlight.mixin;

//? if = 1.7.10 {
import net.minecraft.client.render.vertex.Tesselator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Tesselator.class)
public interface TesselatorCaptureAccessor {
    @Accessor("tesselating") boolean cbh$isTesselating();
    @Accessor("tesselating") void cbh$setTesselating(boolean value);
    @Accessor("vertexBuffer") int[] cbh$vertices();
    @Accessor("vertexCount") int cbh$vertexCount();
    @Accessor("offsetX") double cbh$offsetX();
    @Accessor("offsetY") double cbh$offsetY();
    @Accessor("offsetZ") double cbh$offsetZ();
    @Invoker("clear") void cbh$clear();
}
//? }
