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
    private static final boolean REUSE_SCOPES=Boolean.parseBoolean(System.getProperty("arcadium.nameScope072.reuse","true"));
    public static long scopesReused,scopesCreated;
    private static final class Holder { Scope current; }
    private static final ThreadLocal<Holder> CURRENT = ThreadLocal.withInitial(Holder::new);
    private NametagLayout() { }

    public static Scope begin(Entity entity, String label) {
        Holder holder=CURRENT.get();
        Scope previous = holder.current;
        if(optimized){
            if(previous!=null&&previous.owner==entity&&(label.equals(previous.label)||label.equals(previous.displayLabel)))return previous;
            String masked=NickHider.display(label);Prepared prepared=PREPARED.get(masked);
            if(prepared==null){String display=NametagText.withoutTrailingPadding(masked);prepared=new Prepared(display,NametagText.stripFormatting(display));PREPARED.put(masked,prepared);}
            holder.current=retain(entity,label,prepared.display,prepared.visible,PlayerBadgeRenderer.onNametag(entity,label));return previous;
        }
        String display = NametagText.withoutTrailingPadding(NickHider.display(label));
        holder.current=new Scope(label, display, Formatting.strip(display), PlayerBadgeRenderer.onNametag(entity, label),entity);
        return previous;
    }

    static Scope retain(Entity entity,String label,String display,String visible,boolean badge) {
        if(REUSE_SCOPES&&entity instanceof NametagScopeAccess access){
            Scope cached=access.arcadium$nameScope();
            if(cached!=null&&cached.owner==entity&&cached.badge==badge&&java.util.Objects.equals(cached.label,label)
                    &&java.util.Objects.equals(cached.displayLabel,display)&&java.util.Objects.equals(cached.visibleLabel,visible)){scopesReused++;return cached;}
            Scope created=new Scope(label,display,visible,badge,entity);access.arcadium$nameScope(created);scopesCreated++;return created;
        }
        scopesCreated++;return new Scope(label,display,visible,badge,entity);
    }

    public static void end(Scope previous) {
        CURRENT.get().current=previous;
    }

    public static boolean badge(String text) {
        Scope scope = CURRENT.get().current;
        return scope != null && scope.badge && matches(scope, text);
    }

    public static String display(String text) {
        Scope scope = CURRENT.get().current;
        return scope != null && matches(scope, text) ? scope.displayLabel : text;
    }

    private static boolean matches(Scope scope, String text) {
        return text != null && (text.equals(scope.label) || text.equals(scope.displayLabel)
                || NametagText.withoutTrailingPadding(optimized?NametagText.stripFormatting(text):Formatting.strip(text)).equals(scope.visibleLabel));
    }
}
