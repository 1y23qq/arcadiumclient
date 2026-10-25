// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.polyfrost.compose.node.PolyNode;
import org.polyfrost.compose.node.TextNode;
import org.polyfrost.compose.runtime.PolyComposeRuntime;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsRenderer;

final class PresentedWorldProof {
    private PresentedWorldProof() {}

    static Map<String, Object> inspect(NativeImage image, int[] original, String name, String marker, int canvasSide) throws Exception {
        int width = image.getWidth(), height = image.getHeight(), changed = 0, hudChanged = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (distance(image.getPixel(x, y), original[y * width + x]) >= 12) {
            changed++;
            if (x >= 40 && x < Math.min(330, width / 2) && y >= 80 && y < height * 4 / 5) hudChanged++;
        }
        var proof = new LinkedHashMap<String, Object>();
        proof.put("mainTargetVsPresentedChangedPixels", changed); proof.put("hudRegionChangedPixels", hudChanged);
        proof.put("centerPixel", Integer.toHexString(image.getPixel(width / 2, height / 2)));
        if (canvasSide > 0) {
            int count = 0, minX = width, minY = height, maxX = -1, maxY = -1;
            for (int y = height / 2 - 30; y < height / 2 + 30; y++) for (int x = width / 2 - 30; x < width / 2 + 30; x++) {
                int pixel = image.getPixel(x, y), red = pixel >>> 16 & 255, green = pixel >>> 8 & 255, blue = pixel & 255;
                if (green >= 32 && green >= Math.max(red, blue) * 4 + 8) { count++; minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
            }
            proof.put("canvasPixels", count); proof.put("canvasBounds", List.of(minX, minY, maxX + 1, maxY + 1));
            require(count >= canvasSide * canvasSide * .8 && count <= canvasSide * canvasSide * 1.35 && Math.abs(maxX - minX + 1 - canvasSide) <= 2 && Math.abs(maxY - minY + 1 - canvasSide) <= 2 &&
                Math.abs((minX + maxX + 1) / 2d - width / 2d) <= 1.5 && Math.abs((minY + maxY + 1) / 2d - height / 2d) <= 1.5, "Presented Canvas geometry differs from native expected scale in " + name);
        }
        if (name.startsWith("world-notification-")) proof.put("notification", notification(image, original, marker, !name.endsWith("disabled")));
        return proof;
    }
    private static Map<String, Object> notification(NativeImage image, int[] original, String marker, boolean expected) throws Exception {
        var field = NotificationsRenderer.class.getDeclaredField("runtime"); field.setAccessible(true);
        var runtime = (PolyComposeRuntime) field.get(null);
        var texts = new ArrayList<TextNode>();
        if (runtime != null) collect(runtime.getRoot(), texts);
        TextNode title = texts.stream().filter(text -> text.getText().equals("Arcadium QA")).findFirst().orElse(null);
        TextNode body = texts.stream().filter(text -> text.getText().contains(marker)).findFirst().orElse(null);
        var proof = new LinkedHashMap<String, Object>(); proof.put("expectedVisible", expected);
        if (title == null || body == null) {
            proof.put("nativeToastNodesPresent", false);
            require(!expected, "Visible notification has no actual rendered title/body nodes");
            return proof;
        }
        Class<?> viewport = Class.forName("org.polyfrost.oneconfig.api.notifications.v1.ToastViewport");
        float scale = ((Number) viewport.getMethod("getScale").invoke(viewport.getField("INSTANCE").get(null))).floatValue();
        int titlePixels = ink(image, original, title, scale), bodyPixels = ink(image, original, body, scale);
        PolyNode card = title.getParent();
        while (card != null && (card.getStyle().getBackground().getAlpha() <= .1f || card.getWidth() < 300)) card = card.getParent();
        require(card != null, "Actual toast text has no themed card parent");
        int left = Math.max(0, (int) Math.floor(card.getX() * scale)), top = Math.max(0, (int) Math.floor(card.getY() * scale));
        int right = Math.min(image.getWidth(), (int) Math.ceil((card.getX() + card.getWidth()) * scale)), bottom = Math.min(image.getHeight(), (int) Math.ceil((card.getY() + card.getHeight()) * scale));
        int background = card.getStyle().getBackground().getArgb(), composited = 0;
        for (int y = top + 4; y < bottom - 4; y++) for (int x = left + 4; x < right - 4; x++) {
            int nativePixel = original[y * image.getWidth() + x], presented = image.getPixel(x, y);
            if (distance(presented, nativePixel) >= 20 && distance(presented, background) + 10 < distance(nativePixel, background)) composited++;
        }
        proof.put("nativeToastNodesPresent", true); proof.put("scale", scale); proof.put("title", title.getText()); proof.put("body", body.getText());
        proof.put("titleSourceArgb", Integer.toHexString(title.getColor().getArgb())); proof.put("bodySourceArgb", Integer.toHexString(body.getColor().getArgb()));
        proof.put("titlePixels", titlePixels); proof.put("bodyPixels", bodyPixels); proof.put("compositedCardPixels", composited);
        proof.put("cardBounds", List.of(left, top, right, bottom));
        if (expected) require(titlePixels >= 8 && bodyPixels >= 8 && composited >= 300, "Presented notification title/body/card pixels are absent: " + proof);
        else require(titlePixels <= 2, "Disabled notification still has presented title pixels: " + proof);
        proof.put("passed", true);
        return proof;
    }
    private static int ink(NativeImage image, int[] original, TextNode node, float scale) {
        int count = 0, color = node.getColor().getArgb();
        for (int y = Math.max(0, (int) Math.floor(node.getY() * scale)); y < Math.min(image.getHeight(), (int) Math.ceil((node.getY() + node.getHeight()) * scale)); y++)
            for (int x = Math.max(0, (int) Math.floor(node.getX() * scale)); x < Math.min(image.getWidth(), (int) Math.ceil((node.getX() + node.getWidth()) * scale)); x++) {
                int pixel = image.getPixel(x, y);
                if (distance(pixel, color) <= 35 && distance(pixel, original[y * image.getWidth() + x]) >= 12) count++;
            }
        return count;
    }
    private static void collect(PolyNode node, List<TextNode> texts) { if (node instanceof TextNode text) texts.add(text); for (PolyNode child : node.getChildren()) collect(child, texts); }
    private static int distance(int first, int second) { return Math.max(Math.max(Math.abs((first >>> 16 & 255) - (second >>> 16 & 255)), Math.abs((first >>> 8 & 255) - (second >>> 8 & 255))), Math.abs((first & 255) - (second & 255))); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
