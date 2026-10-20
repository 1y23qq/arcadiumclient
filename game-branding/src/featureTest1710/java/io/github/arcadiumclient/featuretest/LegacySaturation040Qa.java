// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebug;

final class LegacySaturation040Qa {
    private static final String CONFIG = "org.polyfrost.colorsaturation.client.ColorSaturationConfig";
    private static final String HANDLER = "org.polyfrost.colorsaturation.client.SaturationHandler";
    private static final int SIZE = 64;

    private LegacySaturation040Qa() {}

    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        require(GLX.usePostProcess, "Native saturation QA requires post-processing support");
        Map<Field, Object> saved = new LinkedHashMap<>();
        for (String name : new String[]{"isEnabled", "strength", "contrast", "brightness", "hue"}) {
            Field field = Class.forName(CONFIG).getField(name);
            saved.put(field, field.get(null));
        }
        RenderTarget original = client.getRenderTarget();
        Field targetField = null;
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() != RenderTarget.class) continue;
            field.setAccessible(true);
            if (field.get(client) == original) { targetField = field; break; }
        }
        require(targetField != null, "The actual native Minecraft framebuffer field was not found");
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        Map<String, List<Integer>> errors = new LinkedHashMap<>();
        errors.put("initial", errors());
        FloatBuffer textureMatrix = BufferUtils.createFloatBuffer(16);
        RenderTarget target = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, textureMatrix);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            stat(HANDLER, "free");
            target = new RenderTarget(SIZE, SIZE, false);
            targetField.set(client, target);
            target.bindWrite(true);
            set("isEnabled", true); set("strength", 0f); set("contrast", 1f);
            set("brightness", 1f); set("hue", 0f);
            stat(HANDLER, "update");
            errors.put("pipelineCreation", errors());
            Object chain = field(Class.forName(HANDLER), "postChain");
            require(chain != null, "The real native saturation shader did not initialize");
            int passes = ((Collection<?>) call(chain, "getPasses")).size();
            require(passes == 2, "The native saturation pipeline did not contain both authored passes");
            target.bindWrite(true);
            int expectedFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(.6f, .3f, .15f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            int[] before = pixel();
            int expectedMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int expectedActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int processProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            result.put("nativeSaturationProcessState", Map.of("program", processProgram,
                "programValid", processProgram == 0 || GL20.glIsProgram(processProgram),
                "arrayBuffer", GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                "textureStackDepth", GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH),
                "textureStackLimit", GL11.glGetInteger(GL11.GL_MAX_TEXTURE_STACK_DEPTH)));
            List<Map<String, Object>> messages = new ArrayList<>();
            result.put("nativeSaturationDriverMessages", messages);
            boolean debug = GLContext.getCapabilities().GL_KHR_debug;
            long originalCallback = debug ? ((Number) stat("org.lwjgl.opengl.GL11", "glGetPointer", KHRDebug.GL_DEBUG_CALLBACK_FUNCTION)).longValue() : 0;
            long originalCallbackUser = debug ? ((Number) stat("org.lwjgl.opengl.GL11", "glGetPointer", KHRDebug.GL_DEBUG_CALLBACK_USER_PARAM)).longValue() : 0;
            boolean output = debug && GL11.glIsEnabled(KHRDebug.GL_DEBUG_OUTPUT);
            boolean synchronous = debug && GL11.glIsEnabled(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            result.put("nativeSaturationDriverCallbackInstalled", debug);
            Object callback = null;
            if (debug) {
                Class<?> callbackType = Class.forName("org.lwjgl.opengl.GLDebugMessageCallbackI");
                Object handler = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (proxy, method, arguments) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                    if (method.getName().equals("invoke")) {
                        if (((Number) arguments[1]).intValue() == KHRDebug.GL_DEBUG_TYPE_ERROR) {
                            String message = (String) stat("org.lwjgl.opengl.GLDebugMessageCallback", "getMessage", arguments[4], arguments[5]);
                            List<String> stack = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                                .map(StackTraceElement::toString).limit(40).toList();
                            messages.add(Map.of("type", arguments[1], "id", arguments[2], "severity", arguments[3],
                                "message", message, "javaStack", stack));
                        }
                        return null;
                    }
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> "Arcadium saturation QA callback";
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
                callback = stat("org.lwjgl.opengl.GLDebugMessageCallback", "create", handler);
                stat("org.lwjgl.opengl.KHRDebug", "glDebugMessageCallback", callback, 0L);
                GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
            }
            errors.put("driverDebugSetup", errors());
            try {
                stat(HANDLER, "render", 0f);
            } finally {
                errors.put("process", errors());
                if (debug) {
                    stat("org.lwjgl.opengl.KHRDebug", "nglDebugMessageCallback", originalCallback, originalCallbackUser);
                    if (!output) GL11.glDisable(KHRDebug.GL_DEBUG_OUTPUT);
                    if (!synchronous) GL11.glDisable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                    call(callback, "free");
                }
                java.lang.ref.Reference.reachabilityFence(callback);
                errors.put("driverDebugCleanup", errors());
            }
            require(GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == expectedFramebuffer,
                "Saturation left the real Minecraft framebuffer unbound");
            require(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == expectedMatrix,
                "Saturation changed the caller's matrix mode");
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == expectedActive,
                "Saturation changed the caller's active texture unit");
            int[] after = pixel();
            errors.put("readback", errors());
            require(Math.abs(after[0] - after[1]) < 4 && Math.abs(after[1] - after[2]) < 4,
                "Zero saturation did not produce grayscale pixels: " + java.util.Arrays.toString(after));
            require(after[0] > 75 && after[0] < 115 && after[3] == before[3],
                "Native saturation lost valid RGB from a zero-alpha world: " + java.util.Arrays.toString(after));
            require(Math.abs(before[0] - after[0]) > 35 && Math.abs(before[2] - after[2]) > 35,
                "The saturation setting did not meaningfully change the actual framebuffer");
            stat(HANDLER, "free");
            errors.put("disable", errors());
            require(field(Class.forName(HANDLER), "postChain") == null
                && GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == expectedFramebuffer,
                "Disabling native saturation unbound the Minecraft framebuffer");
            result.put("nativeSaturationPixels", Map.of("passes", passes, "before", before, "after", after,
                "zeroAlphaWorldPreserved", true, "framebufferAndGlRestored", true,
                "disableRestoredFramebuffer", true));
        } finally {
            errors.put("beforeCleanup", errors());
            stat(HANDLER, "free");
            targetField.set(client, original);
            for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
            if (target != null) target.destroyBuffers();
            GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glLoadMatrix(textureMatrix);
            GL11.glPopAttrib(); GL20.glUseProgram(program);
            GL13.glActiveTexture(activeTexture); GL11.glMatrixMode(matrix);
            errors.put("cleanup", errors());
            result.put("nativeSaturationGlErrors", errors);
        }
        require(errors.values().stream().allMatch(List::isEmpty), "Native saturation produced OpenGL errors: " + errors);
    }

    private static void set(String name, Object value) throws Exception { Class.forName(CONFIG).getField(name).set(null, value); }

    private static List<Integer> errors() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) break;
            values.add(error);
        }
        return values;
    }

    private static int[] pixel() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        GL11.glFinish();
        GL11.glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return new int[]{buffer.get(0) & 255, buffer.get(1) & 255, buffer.get(2) & 255, buffer.get(3) & 255};
    }
}
