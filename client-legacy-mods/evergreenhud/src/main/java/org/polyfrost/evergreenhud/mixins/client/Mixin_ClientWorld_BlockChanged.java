package org.polyfrost.evergreenhud.mixins.client;

//? if = 1.7.10 {
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.level.block.Block;
import org.polyfrost.evergreenhud.client.EvergreenHudClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class Mixin_ClientWorld_BlockChanged {
    @Inject(method = "setBlockStateFromPacket", at = @At("RETURN"))
    private void evergreenhud$changed(int x, int y, int z, Block block, int metadata,
                                     CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) EvergreenHudClient.recordBlockChange(x, y, z);
    }
}
//?}
