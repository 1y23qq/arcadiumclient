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
    private static long preparedRevision=Long.MIN_VALUE;
    private static final class Holder { Scope current; long revision=Long.MIN_VALUE; }
    private static final ThreadLocal<Holder> CURRENT = ThreadLocal.withInitial(Holder::new);
    private NametagLayout() { }

    public static Scope begin(Entity entity, String label) {
        Holder holder=CURRENT.get();
        Scope previous = holder.current;
        long revision=NickHider.renderRevision();
        if(optimized){
            if(holder.revision==revision&&previous!=null&&previous.owner==entity&&(label.equals(previous.label)||label.equals(previous.displayLabel))){NickHider.rememberRendered(previous.displayLabel,revision);return previous;}
            if(preparedRevision!=revision){PREPARED.clear();preparedRevision=revision;}
            String masked=NickHider.display(label);Prepared prepared=PREPARED.get(masked);
            if(prepared==null){String display=NametagText.withoutTrailingPadding(masked);prepared=new Prepared(display,NametagText.stripFormatting(display));PREPARED.put(masked,prepared);}
            holder.current=new Scope(label,NickHider.rememberRendered(prepared.display,revision),prepared.visible,PlayerBadgeRenderer.onNametag(entity,label),entity);holder.revision=revision;return previous;
        }
        String display = NickHider.rememberRendered(NametagText.withoutTrailingPadding(NickHider.display(label)),revision);
        holder.current=new Scope(label, display, Formatting.strip(display), PlayerBadgeRenderer.onNametag(entity, label),entity);
        holder.revision=revision;
        return previous;
    }

    public static void end(Scope previous) {
        CURRENT.get().current=previous;
        CURRENT.get().revision=Long.MIN_VALUE;
    }

    public static boolean badge(String text) {
        Scope scope = CURRENT.get().current;
        return scope != null && scope.badge && matches(scope, text);
    }

    public static String display(String text) {
        Scope scope = CURRENT.get().current;
        if(scope==null||!matches(scope,text))return text;
        Holder holder=CURRENT.get();long revision=NickHider.renderRevision();
        if(holder.revision!=revision){
            String display=NickHider.rememberRendered(NametagText.withoutTrailingPadding(NickHider.display(scope.label)),revision);
            scope=new Scope(scope.label,display,Formatting.strip(display),scope.badge,scope.owner);holder.current=scope;holder.revision=revision;
        }
        return NickHider.rememberRendered(scope.displayLabel,revision);
    }

    private static boolean matches(Scope scope, String text) {
        return text != null && (text.equals(scope.label) || text.equals(scope.displayLabel)
                || NametagText.withoutTrailingPadding(optimized?NametagText.stripFormatting(text):Formatting.strip(text)).equals(scope.visibleLabel));
    }
}
