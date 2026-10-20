package org.codeberg.chromatic.freelook.mixin;

//? if >1.8.9 {
/*import net.minecraft.world.entity.Entity;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;
import org.codeberg.chromatic.freelook.util.CameraStateTracker;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    private Entity entity;

    @ModifyArgs(
            /^? if >=26.1 {^/
            /^method = "alignWithEntity",
            ^//^?} else ^/
            method = "setup",
            /^^/
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    /^? if >=1.21.2 {^/
                    /^ordinal = 1
                    ^//^?} else ^/
                    ordinal = 0
                    /^^/
            )
    )
    private void modifyRotationArgs(Args args) {
        if (entity instanceof CameraStateTracker tracker) {
            if (FreelookHandler.INSTANCE.freelookToggled) {
                args.set(0, tracker.freelook$getYaw());
                args.set(1, tracker.freelook$getPitch());
            } else {
                tracker.freelook$setYaw(args.get(0));
                tracker.freelook$setPitch(args.get(1));
            }
        }
    }

    @ModifyReturnValue(
            method = "getMaxZoom",
            at = @At("RETURN")
    )
    private float adjustClipReturn(float original) {
        return FreelookHandler.INSTANCE.applySmoothScale(original);
    }

}
*///?} else {
import net.minecraft.client.render.Camera;
import net.minecraft.entity.living.player.PlayerEntity;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Redirect(
            method = "setup",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/player/PlayerEntity;pitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private static float modifyPitch(PlayerEntity player) {
        return FreelookHandler.INSTANCE.cameraPitch(player, player.pitch);
    }

    @Redirect(
            method = "setup",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/player/PlayerEntity;yaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private static float modifyYaw(PlayerEntity player) {
        return FreelookHandler.INSTANCE.cameraYaw(player, player.yaw);
    }

}
//?}
