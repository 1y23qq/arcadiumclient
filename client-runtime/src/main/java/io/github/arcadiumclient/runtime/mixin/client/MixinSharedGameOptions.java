// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.ClientBuild;
import io.github.arcadiumclient.runtime.client.gui.SharedGameOptions;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import net.minecraft.client.options.GameOptions;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameOptions.class)
public abstract class MixinSharedGameOptions {
    @Shadow private File file;
    @Shadow public int ambientOcclusion;
    @Unique private Map<String, String> arcadium$loadedOptions = Map.of();
    @Unique private SharedGameOptions.Transaction arcadium$optionsTransaction;

    @WrapMethod(method = "load")
    private void arcadium$loadSharedOptions(Operation<Void> original) {
        try {
            arcadium$loadedOptions = SharedGameOptions.read(file.toPath());
            original.call();
        } catch (IOException failure) {
            LogManager.getLogger("Arcadium").error("Could not read shared Minecraft options", failure);
        } finally {
            arcadium$loadedOptions = Map.of();
        }
    }

    @ModifyExpressionValue(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/BufferedReader;readLine()Ljava/lang/String;"))
    private String arcadium$readCompatibleOption(String line) {
        return SharedGameOptions.forLoad(line, arcadium$loadedOptions, ClientBuild.MINECRAFT_VERSION.equals("1.7.10"));
    }

    @WrapMethod(method = "save")
    private void arcadium$saveSharedOptions(Operation<Void> original) {
        File previous = file;
        try (var transaction = SharedGameOptions.begin(previous.toPath(), ClientBuild.MINECRAFT_VERSION.equals("1.7.10"))) {
            arcadium$optionsTransaction = transaction;
            file = transaction.temporary().toFile();
            original.call();
            transaction.commit(ambientOcclusion);
        } catch (IOException failure) {
            LogManager.getLogger("Arcadium").error("Could not save shared Minecraft options; previous options were retained", failure);
        } finally {
            file = previous;
            arcadium$optionsTransaction = null;
        }
    }

    @Redirect(method = "save", at = @At(value = "INVOKE", target = "Ljava/io/PrintWriter;close()V"))
    private void arcadium$checkOptionsWrite(PrintWriter writer) {
        writer.close();
        if (arcadium$optionsTransaction != null) {
            if (writer.checkError()) arcadium$optionsTransaction.fail();
            else arcadium$optionsTransaction.complete();
        }
    }
}
