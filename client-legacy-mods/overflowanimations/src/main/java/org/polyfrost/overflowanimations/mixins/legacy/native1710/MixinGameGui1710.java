/**
 * OverflowAnimations
 * The all-you-could-want legacy animations mod for modern minecraft versions.
 * Brings back animations from the 1.7/1.8 era and more.
 * <p>
 * Copyright (C) 2024-2027 lowercasebtw
 * Copyright (C) 2024-2027 mixces
 * Copyright (C) 2024-2027 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package org.polyfrost.overflowanimations.mixins.legacy.native1710;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import org.polyfrost.overflowanimations.config.OverflowAnimationsConfig;
import org.polyfrost.overflowanimations.legacy.hud.DebugCrosshair;
import org.polyfrost.overflowanimations.util.enums.DebugCrosshairSetting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameGui.class)
public abstract class MixinGameGui1710 {
    @Shadow @Final private Minecraft minecraft;
    @Unique private static boolean arcadium$crosshairLookedUp;
    @Unique private static java.lang.reflect.Method arcadium$thirdPersonCrosshair;

    @ModifyVariable(method = "renderStatusBars", at = @At("STORE"), index = 3)
    private int overflowanimations$heartFlash(int original) {
        if (!OverflowAnimationsConfig.instance().screen.disableHeartFlash) return original;
        int time = minecraft.player.invulnerableTimer;
        return time >= 10 && time / 3 % 2 == 1 ? 1 : 0;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawTexture(IIIIII)V"))
    private void overflowanimations$crosshair(GameGui gui, int x, int y, int u, int v, int width, int height,
                                               Operation<Void> original, @Local(argsOnly = true) float delta) {
        if (u == 0 && v == 0 && width == 16 && height == 16) {
            var settings = OverflowAnimationsConfig.instance().screen;
            if (!settings.thirdPersonCrosshair.isLegacy() && minecraft.options.perspective != 0 && !arcadium$showThirdPersonCrosshair()) return;
            if (settings.debugCrosshairStyle == DebugCrosshairSetting.V1_12 && DebugCrosshair.isDebugCrosshairVisible(minecraft)
                    && minecraft.options.perspective == 0) {
                DebugCrosshair.render(minecraft, new Window(minecraft, minecraft.width, minecraft.height), delta);
                return;
            }
        }
        original.call(gui, x, y, u, v, width, height);
    }

    @Unique private static boolean arcadium$showThirdPersonCrosshair() {
        if (!arcadium$crosshairLookedUp) {
            arcadium$crosshairLookedUp = true;
            try {
                arcadium$thirdPersonCrosshair = Class.forName("io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer", false,
                    Minecraft.class.getClassLoader()).getMethod("showInThirdPerson");
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (arcadium$thirdPersonCrosshair == null) return false;
        try { return Boolean.TRUE.equals(arcadium$thirdPersonCrosshair.invoke(null)); }
        catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;drawWithShadow(Ljava/lang/String;III)I"),
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=Minecraft 1.7.10 ("),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;overlayMessageCooldown:I", ordinal = 0)))
    private int overflowanimations$debugText(TextRenderer renderer, String text, int x, int y, int color, Operation<Integer> original) {
        return overflowanimations$drawDebug(renderer, text, x, y, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;drawString(Lnet/minecraft/client/render/TextRenderer;Ljava/lang/String;III)V"),
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=Minecraft 1.7.10 ("),
                       to = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;overlayMessageCooldown:I", ordinal = 0)))
    private void overflowanimations$debugString(GameGui gui, TextRenderer renderer, String text, int x, int y, int color, Operation<Void> original) {
        overflowanimations$drawDebug(renderer, text, x, y, color);
    }

    @Unique private static int overflowanimations$drawDebug(TextRenderer renderer, String text, int x, int y, int color) {
        var settings = OverflowAnimationsConfig.instance().screen;
        if (settings.legacyDebugScreen) return renderer.drawWithShadow(text, x, y, color);
        if (!settings.disableDebugHudBackground) GuiElement.fill(x - 1, y - 1, x + renderer.getWidth(text) + 1, y + renderer.fontHeight - 1, -1873784752);
        return settings.debugHudTextShadow ? renderer.drawWithShadow(text, x, y, color) : renderer.draw(text, x, y, color);
    }

}

//?}
