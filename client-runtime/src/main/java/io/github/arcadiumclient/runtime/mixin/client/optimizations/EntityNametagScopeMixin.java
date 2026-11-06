// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.optimizations;

import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.client.branding.NametagScopeAccess;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityNametagScopeMixin implements NametagScopeAccess {
    @Unique private NametagLayout.Scope arcadium$nameScope;
    @Override public NametagLayout.Scope arcadium$nameScope(){return arcadium$nameScope;}
    @Override public void arcadium$nameScope(NametagLayout.Scope scope){arcadium$nameScope=scope;}
}
