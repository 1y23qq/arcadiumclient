// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class BadgeText {
    public static final int ADVANCE = 10;
    private static final FontDescription BADGE = new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcadium", "badge"));
    private BadgeText() {}
    public static Component prefix(Component original) {
        return Component.empty().append(Component.literal("A ").setStyle(Style.EMPTY.withFont(BADGE).withColor(0xFFFFFF).withShadowColor(0))).append(original);
    }
}
