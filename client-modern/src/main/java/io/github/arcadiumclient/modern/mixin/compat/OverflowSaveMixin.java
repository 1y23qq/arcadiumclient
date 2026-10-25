// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FileBackend.class, remap = false)
public abstract class OverflowSaveMixin {
    @WrapMethod(method = "save0", remap = false)
    private boolean arcadium$keepOverflowChoices(Tree tree, Operation<Boolean> original) {
        if (!"overflowanimations.json".equals(tree.getID())) return original.call(tree);
        return AnimationsConfig.withOverflowValues(() -> original.call(tree));
    }
}
