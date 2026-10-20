// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.Gson;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

final class ResourceDrag040Qa {
    private static final String ROOT = "Arcadium Drag040 QA/Deep";
    private static final String A = ROOT + "/§bAlpha.zip", B = ROOT + "/Beta.zip", C = ROOT + "/Gamma.zip", NEW = ROOT + "/New.zip";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static Screen screen, parent;
    private static List<String> original;
    private static int stage, ticks, dragTick, hold;
    private static Point from, to;
    private static boolean begun, done, moving;

    private ResourceDrag040Qa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        if (!begun) {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Pack QA requires the offline fixture");
            begun = true; parent = mc.screen;
            Path directory = mc.getResourcePacks().getDirectory().toPath();
            for (String id : List.of(A, B, C)) pack(directory.resolve(id), 1);
            pack(directory.resolve(NEW), 2);
            for (int i = 0; i < 28; i++) pack(directory.resolve(ROOT + "/Overflow " + i + ".zip"), 1);
            mc.openScreen(new ResourcePacksScreen(parent)); screen = mc.screen;
            require(screen.getClass().getSimpleName().equals("ArcadiumResourcePacksScreen"), "The Arcadium pack browser did not open");
            original = active();
            call(screen, "setActive", List.of());
            call(screen, "setExpanded", Set.of());
            checks.put("resourceDrag040", RESULT);
        }
        if (moving) {
            moveTick(mc);
            return false;
        }
        if (++ticks < 8) return false;
        ticks = 0;
        switch (stage++) {
            case 0 -> search("Drag040 Alpha");
            case 1 -> {
                Object enable = description("Enable pack", 0);
                List<Float> bounds = rectangle(enable, mc, "Enable pack");
                require(bounds.get(2) - bounds.get(0) >= 35 && bounds.get(3) - bounds.get(1) >= 35, "Enable target is still too small");
                RESULT.put("enableBounds", bounds);
                begin(mc, body(A), pane("activeBounds"), 0);
            }
            case 2 -> {
                require(active().equals(List.of(A)), "A nested filtered pack did not drag into Active: " + active());
                search("Drag040");
                RESULT.put("nestedFilteredAvailableToActive", true);
            }
            case 3 -> {
                Point target = pane("activeBounds");
                begin(mc, body(B), target, 0);
            }
            case 4 -> {
                require(active().containsAll(List.of(A, B)) && active().size() == 2, "Second pack drag failed");
                begin(mc, body(C), pane("activeBounds"), 0);
            }
            case 5 -> {
                require(active().size() == 3, "Third pack drag failed");
                List<String> order = active();
                String bottom = order.get(order.size() - 1);
                List<Float> top = rectangle(description("Resource pack " + order.get(0), 0), mc, "Top active card");
                begin(mc, body(bottom), new Point(top.get(0) + 50, top.get(1) + 2), 0);
                RESULT.put("movedToTop", bottom);
            }
            case 6 -> {
                require(active().get(0).equals(RESULT.get("movedToTop")), "Active priority drag did not move the last card to the top");
                RESULT.put("activePriorityReorder", true);
                String remove = active().get(1);
                RESULT.put("removed", remove);
                List<Float> bounds = rectangle(description("Disable pack", 0), mc, "Disable pack");
                require(bounds.get(2) - bounds.get(0) >= 35 && bounds.get(3) - bounds.get(1) >= 35, "Disable target is still too small");
                RESULT.put("disableBounds", bounds);
                begin(mc, body(remove), pane("availableBounds"), 0);
            }
            case 7 -> {
                require(!active().contains(RESULT.get("removed")) && active().size() == 2, "Active pack did not drag back to Available");
                RESULT.put("activeToAvailable", true);
                String id = active().get(0);
                RESULT.put("outsideOrder", active());
                begin(mc, body(id), new Point(3, 3), 0);
            }
            case 8 -> {
                require(active().equals(RESULT.get("outsideOrder")), "Dropping outside both lists changed selection");
                RESULT.put("outsideDropCancelled", true);
                search("Drag040 New");
            }
            case 9 -> begin(mc, body(NEW), pane("activeBounds"), 0);
            case 10 -> {
                require(hasText(screen, "Use this pack?") && !active().contains(NEW), "An incompatible dragged pack bypassed confirmation");
                click(screen, "Cancel");
            }
            case 11 -> {
                require(!active().contains(NEW), "Cancelling the compatibility dialog selected the pack");
                begin(mc, body(NEW), pane("activeBounds"), 0);
            }
            case 12 -> click(screen, "Use pack");
            case 13 -> {
                require(active().contains(NEW), "Confirming the dragged incompatible pack failed");
                RESULT.put("incompatibleConfirmationAndCancel", true);
                call(screen, "setActive", List.of(A, B, C)); search("Drag040");
            }
            case 14 -> {
                Object button = description("Disable pack", 0);
                pointer(mc, center(button), 0);
            }
            case 15 -> {
                require(active().equals(List.of(B, C)), "The enlarged remove button did not receive a normal pointer click");
                RESULT.put("normalActionClick", true);
                click(screen, "Apply");
            }
            case 16 -> {
                require(!(boolean) call(screen, "getApplying"), "Pack application did not finish");
                List<String> persisted = mc.options.resourcePacks;
                require(persisted.equals(List.of(C, B)), "The real repository priority did not match the visual order: " + persisted);
                mc.openScreen(new ResourcePacksScreen(parent)); screen = mc.screen;
            }
            case 17 -> {
                require(active().equals(List.of(B, C)), "Reopening the pack screen lost applied priority");
                RESULT.put("applyAndReopenPersistedPriority", true);
                call(screen, "setActive", new ArrayList<String>());
                search("Drag040");
            }
            case 18 -> {
                Point edge = pane("availableListBounds");
                Object bounds = field(screen, "availableListBounds");
                edge = new Point(edge.x, ((Number) call(bounds, "getBottom")).doubleValue() - 5);
                begin(mc, body(B), edge, 50);
            }
            case 19 -> {
                require(scroll(0) > 0, "Dragging to the available list edge did not auto-scroll");
                RESULT.put("edgeAutoScroll", true);
                call(screen, "setActive", original); click(screen, "Apply");
            }
            case 20 -> {
                require(!(boolean) call(screen, "getApplying"), "Restoring fixture packs did not finish");
                capture(mc, "resource-drag040.png");
                RESULT.put("completed", true);
                mc.openScreen(parent); done = true; return true;
            }
            default -> throw new IllegalStateException("Unknown pack drag QA stage");
        }
        return false;
    }

    private static void pack(Path path, int format) throws Exception {
        Files.createDirectories(path.getParent());
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(new Gson().toJson(Map.of("pack", Map.of("pack_format", format, "description", "§aArcadium pointer fixture"))).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    @SuppressWarnings("unchecked") private static List<String> active() throws Exception { return List.copyOf((List<String>) call(screen, "getActive")); }
    private static Object description(String text, int index) throws Exception {
        for (Object node : nodes(semanticsRoot(screen))) {
            Object value = properties(node).get("ContentDescription");
            if (value instanceof Collection<?> list && list.contains(text) && index-- == 0) return node;
        }
        throw new IllegalStateException("Pack pointer target missing at stage " + stage + ": " + text);
    }
    private static Point center(Object node) throws Exception { return centerRect(call(node, "getBoundsInRoot")); }
    private static Point centerRect(Object rect) throws Exception {
        return new Point((((Number) call(rect, "getLeft")).doubleValue() + ((Number) call(rect, "getRight")).doubleValue()) / 2,
            (((Number) call(rect, "getTop")).doubleValue() + ((Number) call(rect, "getBottom")).doubleValue()) / 2);
    }
    private static Point body(String id) throws Exception {
        Object rect = call(description("Resource pack " + id, 0), "getBoundsInRoot");
        Point center = centerRect(rect);
        return new Point(((Number) call(rect, "getLeft")).doubleValue() + 60, center.y);
    }
    private static Point pane(String name) throws Exception { return centerRect(field(screen, name)); }
    private static void begin(Minecraft mc, Point first, Point last, int holdTicks) throws Exception {
        moving = true; dragTick = 0; from = first; to = last; hold = holdTicks;
        cursor(mc, from); send(mc, from, 0);
    }
    private static void moveTick(Minecraft mc) throws Exception {
        int age = ++dragTick;
        double fraction = Math.max(0, Math.min(1, (age - 3) / 12.));
        Point position = new Point(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction);
        cursor(mc, position);
        int[] gui = gui(mc, position);
        if (age > 3 && age < 17 + hold) ((ScreenProbeAccess) screen).arcadium$drag(gui[0], gui[1], 0, age * 50L);
        if (age == 17 + hold) send(mc, position, 1);
        if (age > 21 + hold) { moving = false; ticks = 0; }
    }
    private static void pointer(Minecraft mc, Point at, int button) throws Exception { cursor(mc, at); int[] p=gui(mc, at); ((ScreenProbeAccess) screen).arcadium$click(p[0],p[1],button); ((ScreenProbeAccess) screen).arcadium$release(p[0],p[1],button); }
    private static void send(Minecraft mc, Point at, int kind) throws Exception { int[] p=gui(mc, at); if(kind==0)((ScreenProbeAccess)screen).arcadium$click(p[0],p[1],0); else ((ScreenProbeAccess)screen).arcadium$release(p[0],p[1],0); }
    private static double ratio() throws Exception { return ((Number) call(stat("org.polyfrost.oneconfig.api.platform.v1.Platform", "screen"), "pixelRatio")).doubleValue(); }
    private static void cursor(Minecraft mc, Point at) throws Exception { double r=ratio(); Mouse.setCursorPosition((int)Math.round(at.x*r),mc.height-(int)Math.round(at.y*r)-1); }
    private static int[] gui(Minecraft mc, Point at) throws Exception { double r=ratio(); return new int[]{(int)Math.round(at.x*screen.width/(mc.width/r)),(int)Math.round(at.y*screen.height/(mc.height/r))}; }
    private static void search(String query) throws Exception {
        for(Object node:nodes(semanticsRoot(screen))) { Object setter=properties(node).get("SetText"); if(setter==null)continue;
            Object value=Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class,List.class,List.class).newInstance(query,List.of(),List.of());
            require(Boolean.TRUE.equals(call(call(setter,"getAction"),"invoke",value)),"Pack search rejected input"); return; }
        throw new IllegalStateException("Pack search is missing");
    }
    private static float scroll(int index) throws Exception { for(Object node:nodes(semanticsRoot(screen))) {Object value=properties(node).get("VerticalScrollAxisRange");if(value!=null&&index--==0)return ((Number)call(call(value,"getValue"),"invoke")).floatValue();}throw new IllegalStateException("Pack scrolling is missing"); }
    private static void capture(Minecraft mc,String file){Screenshot.take(mc.gameDir,file,mc.width,mc.height,mc.getRenderTarget());}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private record Point(double x,double y) { }
}
