// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.colorsaturation;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class CategoryColorPixelProbe {
    private static final Settings settings = new Settings();
    private static int checks, sourceDraws;
    private static final List<String> scenarios = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window = GLFW.glfwCreateWindow(96, 64, "Color category pixel QA", 0, 0);
        if (window == 0) throw new AssertionError("Hidden compatibility context unavailable");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Renderer: " + GL11.glGetString(GL11.GL_RENDERER) + "; OpenGL " + GL11.glGetString(GL11.GL_VERSION));
            inject("options", settings);
            inject("optionsChecked", true);
            settings.world.set(0, 1, 1, 0);
            settings.entities.set(1.4f, .9f, .8f, 20);
            runScene(64, 64, args.length == 0 ? null : new File(args[0]));
            runScene(80, 48, null);
            independentProfiles();
            handDepthClear();
            equalAndDisabledPaths();
            reloadAndFallback();
            errorFree("final state");
            System.out.println("Verified " + checks + " pixel/state assertions; " + sourceDraws + " source draws; " + scenarios);
        } finally {
            CategoryColorRenderer.free();
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void runScene(int width, int height, File image) throws Exception {
        Target target = new Target(width, height), readTarget = new Target(8, 8);
        target.bind();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget.fbo);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL20.glStencilFuncSeparate(GL11.GL_FRONT, GL11.GL_NOTEQUAL, 3, 7);
        GL20.glStencilFuncSeparate(GL11.GL_BACK, GL11.GL_LESS, 5, 15);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, false);
        int sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL33.glBindSampler(0, sampler);
        GL33.glBindSampler(1, sampler);
        CategoryColorRenderer.resetFrame();
        check(CategoryColorRenderer.beginWorld(false), "split frame starts");
        check(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != target.depth, "temporary depth attached");
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClearColor(.2f, .4f, .8f, 1);
        GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        matrices(width, height);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LESS);
        rect(0, 0, width, height, .8f, .8f, .2f, .1f, 1);
        rect(48, 0, width, height, .3f, .1f, .8f, .2f, 1);
        float[] block = transform(new float[] {.8f, .2f, .1f}, settings.world);
        float[] foreground = transform(new float[] {.1f, .8f, .2f}, settings.world);

        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        blend(false);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
        rect(16, 0, 56, height, .5f, .9f, .15f, .1f, .35f);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        float[] entity = clamp(transform(new float[] {.9f, .15f, .1f}, settings.entities));
        blend(true);
        blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        rect(0, 8, 16, 24, .5f, .14f, .25f, .9f, .3f);
        rect(4, 12, 12, 20, .4f, .2f, .7f, .1f, .4f);
        float coverage = .4f + .3f * .6f;
        float[] premultiplied = new float[] { .2f * .4f + .14f * .3f * .6f,
                .7f * .4f + .25f * .3f * .6f, .1f * .4f + .9f * .3f * .6f };
        blendFunction(GL11.GL_ONE, GL11.GL_ONE);
        rect(4, 12, 12, 20, .4f, .03f, .05f, .08f, .8f);
        premultiplied[0] += .03f; premultiplied[1] += .05f; premultiplied[2] += .08f;
        blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        blend(false);
        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, false);
        CategoryColorRenderer.colorMask(true, true, true, false);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        CategoryColorRenderer.endLayer();
        check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE3, "active texture restored");
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        check(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == sampler, "lightmap sampler restored");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        check(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == sampler, "sampler restored");
        check(!GL11.glIsEnabled(GL11.GL_STENCIL_TEST), "stencil enable restored");
        check(GL11.glGetInteger(GL11.GL_STENCIL_REF) == 3 && GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF) == 5,
                "front/back stencil functions restored");
        check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == target.fbo
                && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == readTarget.fbo, "framebuffer bindings restored");
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        pixel(1, 1, block, "blocks retain world profile");
        pixel(20, 4, entity, "fractional-alpha opaque entity remains opaque");
        pixel(52, 4, foreground, "foreground block occludes entity");
        float[] transparent = transform(new float[] {.14f, .25f, .9f}, settings.entities);
        pixel(2, 10, over(transparent, .3f, block), "transparent entity keeps background profile");
        float[] accumulated = transformPremultiplied(premultiplied, coverage, settings.entities);
        pixel(6, 16, add(accumulated, scale(block, 1 - coverage)), "overlap coverage and additive light");

        CategoryColorRenderer.beginLayer(CategoryColorRenderer.WORLD);
        blend(true);
        blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        rect(16, 8, 60, 24, .2f, .3f, .9f, .8f, .35f);
        rect(16, 28, 44, 40, .7f, .6f, .1f, .8f, .7f);
        CategoryColorRenderer.endLayer();
        float[] glass = transform(new float[] {.3f, .9f, .8f}, settings.world);
        pixel(20, 12, over(glass, .35f, entity), "glass blends over independently colored entity");
        pixel(52, 12, over(glass, .35f, foreground), "glass retains block profile");
        pixel(20, 32, entity, "glass behind entity fails depth test");

        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        blend(true);
        blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        rect(0, 28, 12, 40, .4f, .8f, .2f, .6f, .5f);
        blendFunction(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR);
        rect(0, 28, 4, 32, .3f, .3f, .3f, .3f, 1);
        blendFunction(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        rect(8, 32, 12, 40, .3f, .2f, .9f, .1f, .4f);
        CategoryColorRenderer.endLayer();
        float[] particle = clamp(over(transform(new float[] {.8f, .2f, .6f}, settings.entities), .5f, block));
        pixel(6, 36, particle, "particle has entity profile");
        pixel(2, 30, scale(particle, .6f), "destination-color effect remains visible");
        pixel(10, 36, over(transform(new float[] {.2f, .9f, .1f}, settings.entities), .4f, particle),
                "category capture resumes after destination-color effect");
        GL11.glDepthMask(true);
        blend(false);
        int sentinelProgram = constantProgram(), sentinelVao = GL30.glGenVertexArrays();
        GL20.glUseProgram(sentinelProgram);
        GL30.glBindVertexArray(sentinelVao);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_SUBTRACT);
        CategoryColorRenderer.endWorld();
        check(CategoryColorRenderer.skipLegacy(), "global postprocess skipped for split frame");
        check(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == sentinelProgram
                && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == sentinelVao, "program and VAO restored");
        check(GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) == GL14.GL_FUNC_REVERSE_SUBTRACT
                && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) == GL14.GL_FUNC_SUBTRACT, "blend equations restored");
        check(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == target.depth, "original depth attachment restored");
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        depth(20, 4, .5f, "entity depth preserved");
        depth(52, 4, .3f, "occluding block depth preserved");
        depth(2, 10, .8f, "transparent entity does not write depth");
        GL20.glUseProgram(0);
        GL30.glBindVertexArray(0);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        rect(0, height - 6, width, height, 0, .95f, .05f, .3f, 1);
        pixel(2, height - 2, new float[] {.95f, .05f, .3f}, "HUD remains unchanged");
        if (image != null) writeImage(image, width, height);
        errorFree("scene " + width + "x" + height);
        GL20.glDeleteProgram(sentinelProgram); GL30.glDeleteVertexArrays(sentinelVao);
        GL33.glBindSampler(0, 0); GL33.glBindSampler(1, 0); GL33.glDeleteSamplers(sampler);
        target.close(); readTarget.close();
        scenarios.add(width + "x" + height + " alpha/glass/depth/HUD");
    }

    private static void equalAndDisabledPaths() {
        settings.entities.set(settings.world.saturation, settings.world.contrast, settings.world.brightness, settings.world.hue);
        long layers = CategoryColorRenderer.capturedLayers;
        CategoryColorRenderer.resetFrame();
        check(!CategoryColorRenderer.beginWorld(false), "identical settings retain linked fast path");
        check(!CategoryColorRenderer.skipLegacy() && layers == CategoryColorRenderer.capturedLayers, "identical settings capture no layers");
        settings.separate = false;
        check(!CategoryColorRenderer.beginWorld(false), "existing config defaults retain linked fast path");
        settings.separate = true;
        settings.entities.set(1.4f, .9f, .8f, 20);
        check(!CategoryColorRenderer.beginWorld(true), "incompatible renderer retains linked fast path");
        scenarios.add("identical/default/incompatible fast paths");
    }

    private static void independentProfiles() {
        settings.world.set(.2f, 1, 1, 0);
        settings.entities.set(1.4f, .9f, .8f, 20);
        int[] before = simpleScene();
        settings.world.set(2, 1, 1, 0);
        int[] blockChange = simpleScene();
        check(before[0] != blockChange[0], "block setting changes blocks");
        check(before[1] == blockChange[1], "block setting preserves opaque entity pixels");
        check(before[2] == blockChange[2], "block setting preserves HUD pixels");
        settings.entities.set(0, 1, 1, 0);
        int[] entityChange = simpleScene();
        check(blockChange[0] == entityChange[0], "entity setting preserves block pixels");
        check(blockChange[1] != entityChange[1], "entity setting changes entities");
        check(blockChange[2] == entityChange[2], "entity setting preserves HUD pixels");
        settings.world.set(0, 1, 1, 0);
        settings.entities.set(1.4f, .9f, .8f, 20);
        scenarios.add("independent block/entity changes");
    }

    private static int[] simpleScene() {
        Target target = new Target(64, 64);
        target.bind();
        GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glColorMask(true, true, true, true);
        CategoryColorRenderer.resetFrame();
        check(CategoryColorRenderer.beginWorld(false), "profile comparison split starts");
        matrices(64, 64);
        GL11.glDepthMask(true); GL11.glClearDepth(1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LESS);
        rect(0, 0, 64, 64, .8f, .8f, .2f, .1f, 1);
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        blend(false);
        rect(32, 0, 64, 64, .4f, .2f, .7f, .3f, .4f);
        CategoryColorRenderer.endLayer();
        CategoryColorRenderer.endWorld();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        rect(0, 56, 64, 64, 0, .8f, .1f, .6f, 1);
        int[] pixels = {rgb(4, 4), rgb(40, 4), rgb(4, 60)};
        target.close();
        errorFree("profile comparison");
        return pixels;
    }

    private static void handDepthClear() {
        Target target = new Target(64, 64);
        target.bind();
        GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glColorMask(true, true, true, true);
        CategoryColorRenderer.resetFrame();
        check(CategoryColorRenderer.beginWorld(false), "hand split starts");
        matrices(64, 64);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glDepthMask(true); GL11.glClearDepth(1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        rect(0, 0, 64, 64, .2f, .3f, .7f, .1f, 1);
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.WORLD);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        CategoryColorRenderer.beginLayer(CategoryColorRenderer.ENTITY);
        blend(false);
        rect(32, 0, 64, 64, .6f, .7f, .2f, .4f, .4f);
        CategoryColorRenderer.endLayer();
        CategoryColorRenderer.endWorld();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        pixel(40, 4, transform(new float[] {.7f, .2f, .4f}, settings.entities), "held item uses entity profile after depth clear");
        depth(40, 4, .6f, "held item depth copied to original target");
        depth(4, 4, 1, "hand depth clear preserved outside hand");
        target.close();
        errorFree("hand depth clear");
        scenarios.add("first-person hand/depth clear");
    }

    private static void reloadAndFallback() throws Exception {
        CategoryColorRenderer.free();
        Target invalid = new Target(16, 16);
        invalid.bind();
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, 0);
        check(!CategoryColorRenderer.beginWorld(false), "missing depth fails safely before drawing");
        check(!CategoryColorRenderer.skipLegacy(), "fallback keeps original postprocess");
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, invalid.depth);
        check(CategoryColorRenderer.beginWorld(false), "reload creates resources again");
        GL11.glDepthMask(true); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        CategoryColorRenderer.endWorld();
        int oldProgram = resource("program"), oldColor = resource("layerColor"), oldDepth = resource("sharedDepth");
        CategoryColorRenderer.free();
        check(!CategoryColorRenderer.skipLegacy(), "reload clears frame marker");
        check(!GL20.glIsProgram(oldProgram) && !GL11.glIsTexture(oldColor) && !GL11.glIsTexture(oldDepth), "reload frees GPU resources");
        invalid.close();
        errorFree("reload/fallback");
        scenarios.add("resize/reload/failure fallback");
    }

    private static void blend(boolean enabled) {
        if (enabled) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
        CategoryColorRenderer.blendEnabled(enabled);
    }
    private static void blendFunction(int source, int destination) {
        GL11.glBlendFunc(source, destination);
        CategoryColorRenderer.blendFunction(source, destination, source, destination);
    }
    private static void matrices(int width, int height) {
        GL20.glUseProgram(0);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, width, 0, height, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_LIGHTING);
    }
    private static void rect(float x0, float y0, float x1, float y1, float depth, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        float z = 1 - 2 * depth;
        GL11.glVertex3f(x0, y0, z); GL11.glVertex3f(x1, y0, z); GL11.glVertex3f(x1, y1, z); GL11.glVertex3f(x0, y1, z);
        GL11.glEnd();
        sourceDraws++;
    }
    private static float[] transform(float[] color, CategoryColorProfile profile) { return transformPremultiplied(color, 1, profile); }
    private static float[] transformPremultiplied(float[] color, float coverage, CategoryColorProfile profile) {
        float[] out = color.clone();
        for (int i = 0; i < 3; i++) out[i] = (out[i] * profile.brightness - .5f * coverage) * profile.contrast + .5f * coverage;
        float luma = .3f * out[0] + .59f * out[1] + .11f * out[2];
        for (int i = 0; i < 3; i++) out[i] = luma + (out[i] - luma) * profile.saturation;
        double angle = Math.toRadians(profile.hue), c = Math.cos(angle), s = Math.sin(angle), axis = .57735026;
        double dot = axis * (out[0] + out[1] + out[2]);
        return new float[] {(float) (out[0] * c + axis * (out[2] - out[1]) * s + axis * dot * (1 - c)),
                (float) (out[1] * c + axis * (out[0] - out[2]) * s + axis * dot * (1 - c)),
                (float) (out[2] * c + axis * (out[1] - out[0]) * s + axis * dot * (1 - c))};
    }
    private static float[] over(float[] color, float alpha, float[] background) { return add(scale(color, alpha), scale(background, 1 - alpha)); }
    private static float[] scale(float[] color, float scale) { return new float[] {color[0] * scale, color[1] * scale, color[2] * scale}; }
    private static float[] add(float[] a, float[] b) { return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static float[] clamp(float[] color) {
        return new float[] {Math.clamp(color[0], 0, 1), Math.clamp(color[1], 0, 1), Math.clamp(color[2], 0, 1)};
    }
    private static void pixel(int x, int y, float[] expected, String message) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        for (int i = 0; i < 3; i++) {
            int actual = Byte.toUnsignedInt(pixel.get(i)), wanted = Math.round(Math.clamp(expected[i], 0, 1) * 255);
            check(Math.abs(actual - wanted) <= 2, message + " channel " + i + ": actual=" + actual + ", expected=" + wanted);
        }
    }
    private static int rgb(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return Byte.toUnsignedInt(pixel.get(0)) << 16 | Byte.toUnsignedInt(pixel.get(1)) << 8 | Byte.toUnsignedInt(pixel.get(2));
    }
    private static void depth(int x, int y, float expected, String message) {
        FloatBuffer depth = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        check(Math.abs(depth.get(0) - expected) < 1e-5, message + ": actual=" + depth.get(0));
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static void errorFree(String message) { int error = GL11.glGetError(); check(error == GL11.GL_NO_ERROR, message + " GL error=" + error); }
    private static void inject(String name, Object value) throws Exception {
        Field field = CategoryColorRenderer.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static int resource(String name) throws Exception {
        Field field = CategoryColorRenderer.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(null);
    }
    private static void writeImage(File output, int width, int height) throws Exception {
        ByteBuffer bytes = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            image.setRGB(x, height - 1 - y, 0xff000000 | Byte.toUnsignedInt(bytes.get(i)) << 16
                    | Byte.toUnsignedInt(bytes.get(i + 1)) << 8 | Byte.toUnsignedInt(bytes.get(i + 2)));
        }
        ImageIO.write(image, "png", output);
    }
    private static int constantProgram() {
        int program = GL20.glCreateProgram();
        int vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(vertex, "#version 130\nvoid main(){gl_Position=gl_Vertex;}");
        GL20.glShaderSource(fragment, "#version 130\nout vec4 c;void main(){c=vec4(1.0);}");
        GL20.glCompileShader(vertex); GL20.glCompileShader(fragment);
        GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        return program;
    }

    private static final class Settings implements CategoryColorOptions {
        final CategoryColorProfile world = new CategoryColorProfile(), entities = new CategoryColorProfile();
        boolean separate = true;
        @Override public boolean arcadium$colorEnabled() { return true; }
        @Override public boolean arcadium$separateEntityColors() { return separate; }
        @Override public void arcadium$readColors(CategoryColorProfile w, CategoryColorProfile e) {
            w.set(world.saturation, world.contrast, world.brightness, world.hue);
            e.set(entities.saturation, entities.contrast, entities.brightness, entities.hue);
        }
    }
    private static final class Target implements AutoCloseable {
        final int fbo = GL30.glGenFramebuffers(), color = GL11.glGenTextures(), depth = GL30.glGenRenderbuffers();
        final int width, height;
        Target(int width, int height) {
            this.width = width; this.height = height;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
            check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "QA framebuffer complete");
        }
        void bind() { GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL11.glViewport(0, 0, width, height); }
        @Override public void close() { GL30.glDeleteFramebuffers(fbo); GL11.glDeleteTextures(color); GL30.glDeleteRenderbuffers(depth); }
    }
}
