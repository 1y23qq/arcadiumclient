// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.List;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.embeddedt.embeddium.impl.gui.framework.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.taumc.celeritas.api.options.structure.OptionPage;

@Pseudo
@IfModLoaded("argentum")
@Mixin(value = CeleritasVideoOptionsController.class, remap = false)
public interface CeleritasVideoOptionsAccessor {
    @Accessor(value = "pages", remap = false)
    List<OptionPage> arcadium$pages();

    @Accessor(value = "font", remap = false)
    DrawContext arcadium$font();

    @Invoker(value = "applyChanges", remap = false)
    void arcadium$apply();

    @Invoker(value = "undoChanges", remap = false)
    void arcadium$undo();
}
