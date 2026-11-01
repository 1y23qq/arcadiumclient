// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

import io.github.arcadiumclient.runtime.client.features.NickHider;
import net.minecraft.entity.Entity;
import net.minecraft.text.Formatting;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NametagLayout {
    public record Scope(String label, String displayLabel, String visibleLabel, boolean badge, Entity owner) { }
    private record Prepared(String display,String visible) { }
    private static final Map<String,Prepared> PREPARED=new LinkedHashMap<>(1024,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Prepared> entry){return size()>2048;}
    };
    public static boolean optimized;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private NametagLayout() { }

    public static Scope begin(Entity entity, String label) {
        Scope previous = CURRENT.get();
        if(optimized){
            if(previous!=null&&previous.owner==entity&&(label.equals(previous.label)||label.equals(previous.displayLabel)))return previous;
            String masked=NickHider.display(label);Prepared prepared=PREPARED.get(masked);
            if(prepared==null){String display=NametagText.withoutTrailingPadding(masked);prepared=new Prepared(display,NametagText.stripFormatting(display));PREPARED.put(masked,prepared);}
            CURRENT.set(new Scope(label,prepared.display,prepared.visible,PlayerBadgeRenderer.onNametag(entity,label),entity));return previous;
        }
        String display = NametagText.withoutTrailingPadding(NickHider.display(label));
        CURRENT.set(new Scope(label, display, Formatting.strip(display), PlayerBadgeRenderer.onNametag(entity, label),entity));
        return previous;
    }

    public static void end(Scope previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public static boolean badge(String text) {
        Scope scope = CURRENT.get();
        return scope != null && scope.badge && matches(scope, text);
    }

    public static String display(String text) {
        Scope scope = CURRENT.get();
        return scope != null && matches(scope, text) ? scope.displayLabel : text;
    }

    private static boolean matches(Scope scope, String text) {
        return text != null && (text.equals(scope.label) || text.equals(scope.displayLabel)
                || NametagText.withoutTrailingPadding(optimized?NametagText.stripFormatting(text):Formatting.strip(text)).equals(scope.visibleLabel));
    }
}
