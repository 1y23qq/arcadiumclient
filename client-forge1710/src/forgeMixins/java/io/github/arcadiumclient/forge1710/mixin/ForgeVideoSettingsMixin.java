// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import io.github.arcadiumclient.forge1710.client.AngelicaVideoSettingsModel;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Minecraft.class, priority = 900)
public abstract class ForgeVideoSettingsMixin {
    @ModifyVariable(method = "func_147108_a", at = @At("HEAD"), argsOnly = true, remap = false)
    private GuiScreen arcadium$videoSettings(GuiScreen screen) {
        return screen instanceof ReeseSodiumVideoOptionsScreen angelica ? AngelicaVideoSettingsModel.replace(angelica) : screen;
    }
}
