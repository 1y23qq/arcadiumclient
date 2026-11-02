// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.cera;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@IfModLoaded("cera")
@Mixin(targets="dev.rdh.cera.modules.OptifineCosmetics",priority=1,remap=false)
public abstract class TrustedCosmeticUsernameMarker { }
