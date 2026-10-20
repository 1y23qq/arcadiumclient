package org.codeberg.chromatic.freelook.mixin.legacy;

//? if =1.8.9 {
/*import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.world.entity.Entity;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Redirect(
            method = {"setupRender", "getLookVector"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;yaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyYaw(Entity entity) {
        return FreelookHandler.INSTANCE.cameraYaw(entity, entity.yaw);
    }

    @Redirect(
            method = "getLookVector",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;lastYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyLastYaw(Entity entity) {
        return FreelookHandler.INSTANCE.cameraYaw(entity, entity.lastYaw);
    }

    @Redirect(
            method = {"setupRender", "getLookVector"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;pitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyPitch(Entity entity) {
        return FreelookHandler.INSTANCE.cameraPitch(entity, entity.pitch);
    }

    @Redirect(
            method = "getLookVector",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;lastPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyLastPitch(Entity entity) {
        return FreelookHandler.INSTANCE.cameraPitch(entity, entity.lastPitch);
    }

}
*///?}
