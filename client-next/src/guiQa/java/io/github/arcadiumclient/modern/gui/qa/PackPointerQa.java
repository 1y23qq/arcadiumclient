// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import androidx.compose.ui.geometry.Rect;
import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.gui.qa.mixin.MouseQaAccess;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

final class PackPointerQa {
    private final Minecraft minecraft;
    private final Screen screen;
    private int phase;
    private int ticks;
    private int moves;
    private float x, startY, endY;
    private List<String> order;
    private Map<String, Object> proof;

    PackPointerQa(Minecraft minecraft) throws Exception {
        this.minecraft = minecraft;
        screen = minecraft.gui.screen();
        for (String name : List.of("Green", "Blue")) {
            var path = minecraft.getResourcePackDirectory().resolve("Pointer QA/" + name);
            Files.createDirectories(path);
            var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
            Files.writeString(path.resolve("pack.mcmeta"), new GsonBuilder().create().toJson(Map.of("pack", Map.of(
                    "description", name, "min_format", List.of(format.major(), format.minor()), "max_format", List.of(format.major(), format.minor())))));
        }
        minecraft.getResourcePackRepository().reload();
        var refresh = screen.getClass().getDeclaredMethod("refresh", boolean.class); refresh.setAccessible(true); refresh.invoke(screen, true);
        var type = Class.forName("io.github.arcadiumclient.modern.gui.PackView");
        var constructor = type.getDeclaredConstructor(Pack.class); constructor.setAccessible(true);
        var add = screen.getClass().getDeclaredMethod("add", type, int.class); add.setAccessible(true);
        for (String name : List.of("Green", "Blue")) add.invoke(screen,
                constructor.newInstance(minecraft.getResourcePackRepository().getPack("file/Pointer QA/" + name)), 0);
    }

    boolean tick() throws Exception {
        if (proof != null) return true;
        if (++ticks > 400) throw new IllegalStateException("Native pack drag timed out in phase " + phase);
        if (ticks < 8) return false;
        if (phase == 0) {
            var type = Class.forName("io.github.arcadiumclient.modern.gui.PackDrag");
            var constructor = type.getDeclaredConstructor(String.class, boolean.class); constructor.setAccessible(true);
            @SuppressWarnings("unchecked") var cards = (Map<Object, Rect>) field("cardBounds");
            var source = cards.get(constructor.newInstance("Pointer QA/Green", true));
            var target = cards.get(constructor.newInstance("Pointer QA/Blue", true));
            var list = (Rect) field("activeListBounds");
            if (source == null || target == null || !minecraft.isWindowActive()) return false;
            x = source.getLeft() + Math.min(90, (source.getWidth() - 90) / 2);
            float top = Math.max(source.getTop(), list.getTop()) + 3;
            float bottom = Math.min(source.getBottom(), list.getBottom()) - 3;
            if (top >= bottom) throw new IllegalStateException("Source pack has no visible drag surface");
            startY = (top + bottom) / 2;
            endY = Math.max(target.getTop(), list.getTop()) + 2;
            move(startY);
            phase = 1; ticks = 0;
            return false;
        }
        if (phase == 1) {
            screen.mouseClicked(event(startY), false);
            phase = 2; ticks = 0;
            return false;
        }
        if (phase == 2) {
            float previous = startY + (endY - startY) * moves / 10f;
            float y = startY + (endY - startY) * ++moves / 10f;
            move(y);
            screen.mouseDragged(event(y), 0, (y - previous) / minecraft.getWindow().getGuiScale());
            if (moves == 10) phase = 3;
            ticks = 6;
            return false;
        }
        if (phase == 3) {
            if (state("dragging") == null) throw new IllegalStateException("Native pointer did not start pack drag");
            screen.mouseReleased(event(endY));
            phase = 4; ticks = 0;
            return false;
        }
        if (phase == 4) {
            @SuppressWarnings("unchecked") var active = (List<String>) state("active");
            if (active.indexOf("Pointer QA/Green") >= active.indexOf("Pointer QA/Blue"))
                throw new IllegalStateException("Native pack drop did not reorder priority");
            order = List.copyOf(active);
            var apply = screen.getClass().getDeclaredMethod("finish", boolean.class); apply.setAccessible(true); apply.invoke(screen, false);
            phase = 5; ticks = 0;
            return false;
        }
        if (Boolean.TRUE.equals(state("applying"))) return false;
        var selected = new ArrayList<>(minecraft.getResourcePackRepository().getSelectedIds());
        boolean applied = selected.indexOf("file/Pointer QA/Green") > selected.indexOf("file/Pointer QA/Blue");
        if (!applied) throw new IllegalStateException("Applied native pack priority differs from the UI");
        proof = Map.of("nativeScreenInput", true, "realCardBounds", true, "moves", moves, "order", order,
                "applied", true, "pointerStart", List.of(x, startY), "pointerEnd", List.of(x, endY));
        return true;
    }

    Map<String, Object> proof() { return proof; }
    private Object field(String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    private Object state(String name) throws Exception { return ((androidx.compose.runtime.State<?>) field(name + "$delegate")).getValue(); }
    private void move(float y) { ((MouseQaAccess) minecraft.mouseHandler).arcadium$nativeMove(minecraft.getWindow().handle(), x, y); }
    private MouseButtonEvent event(float y) {
        double scale = minecraft.getWindow().getGuiScale();
        return new MouseButtonEvent(x / scale, y / scale, new MouseButtonInfo(0, 0));
    }
}
