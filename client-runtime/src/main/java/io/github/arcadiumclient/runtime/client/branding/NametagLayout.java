// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

import io.github.arcadiumclient.runtime.client.features.NickHider;
import net.minecraft.entity.Entity;
import net.minecraft.text.Formatting;

public final class NametagLayout {
    public record Scope(String label, String visibleLabel, boolean badge) { }
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private NametagLayout() { }

    public static Scope begin(Entity entity, String label) {
        Scope previous = CURRENT.get();
        if (PlayerBadgeRenderer.onNametag(entity, label))
            CURRENT.set(new Scope(label, Formatting.strip(NickHider.display(label)), true));
        else CURRENT.remove();
        return previous;
    }

    public static void end(Scope previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public static boolean badge(String text) {
        Scope scope = CURRENT.get();
        return scope != null && scope.badge && text != null &&
                (text.equals(scope.label) || Formatting.strip(text).equals(scope.visibleLabel));
    }
}
