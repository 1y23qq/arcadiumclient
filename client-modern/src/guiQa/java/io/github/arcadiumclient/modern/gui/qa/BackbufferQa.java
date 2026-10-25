// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class BackbufferQa {
    @FunctionalInterface interface Inspector { Map<String, Object> inspect(NativeImage image) throws Exception; }
    private record Request(Path image, Inspector inspector, CompletableFuture<Map<String, Object>> result) {}
    private static final AtomicReference<Request> pending = new AtomicReference<>();
    private BackbufferQa() {}

    static CompletableFuture<Map<String, Object>> capture(Path destination, Inspector inspector) {
        var request = new Request(destination, inspector, new CompletableFuture<>());
        if (!pending.compareAndSet(null, request)) return CompletableFuture.failedFuture(new IllegalStateException("A presented-frame readback is already pending"));
        QaTickScheduler.afterTicks(160).thenRunAsync(() -> {
            if (pending.compareAndSet(request, null)) request.result.completeExceptionally(new IllegalStateException("Presented-frame readback timed out"));
        }, QaTickScheduler.EXECUTOR);
        return request.result;
    }
    public static void present() {
        Request request = pending.getAndSet(null);
        if (request == null) return;
        try {
            var window = Minecraft.getInstance().getWindow();
            int width = window.getWidth(), height = window.getHeight();
            if (width <= 0 || height <= 0 || (long) width * height > 12_000_000L) throw new IllegalStateException("Invalid presented-frame dimensions");
            ByteBuffer rgba = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            try {
                read(rgba, width, height);
                try (var image = new NativeImage(NativeImage.Format.RGBA, width, height, false)) {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int offset = ((height - y - 1) * width + x) * 4;
                        image.setPixel(x, y, 0xFF000000 | (rgba.get(offset) & 255) << 16 | (rgba.get(offset + 1) & 255) << 8 | rgba.get(offset + 2) & 255);
                    }
                    image.writeToFile(request.image);
                    var proof = new LinkedHashMap<String, Object>();
                    proof.put("image", request.image.getFileName().toString()); proof.put("captureTarget", "GL_BACK after actual SkiaCtx.draw, before window swap");
                    proof.put("width", width); proof.put("height", height); proof.putAll(request.inspector.inspect(image));
                    request.result.complete(proof);
                }
            } finally { MemoryUtil.memFree(rgba); }
        } catch (Throwable failure) { request.result.completeExceptionally(failure); }
    }
    private static void read(ByteBuffer rgba, int width, int height) {
        int framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH), skipRows = GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0); GL11.glReadBuffer(GL11.GL_BACK);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer); GL11.glReadBuffer(readBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer); GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, rowLength); GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, skipPixels);
        }
    }
}
