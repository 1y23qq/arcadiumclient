// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.colorsaturation;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.IntBuffer;

public final class CategoryColorRenderer {
    public static final int WORLD = 0, ENTITY = 1;
    private static final CategoryColorProfile world = new CategoryColorProfile(), entities = new CategoryColorProfile();
    private static final IntBuffer integers = BufferUtils.createIntBuffer(4);
    private static final boolean checkFeedback = Boolean.getBoolean("arcadium.colorCategories.checkFeedback");
    private static CategoryColorOptions options;
    private static boolean optionsChecked, failed, inWorld, depthAttached, baseReady, capturing, frameApplied;
    private static int width, height, sourceFramebuffer, originalReadFramebuffer;
    private static int originalDepthType, originalDepth, originalDepthLevel;
    private static int layerFramebuffer, layerColor, backgroundColor, sharedDepth, program, vao, modeUniform, settingsUniform;
    private static int category = -1, suspendedCategory = -1, layerReadFramebuffer;
    private static boolean blend, red, green, blue, alpha;
    private static int sourceRgb, destinationRgb, sourceAlpha, destinationAlpha;
    private static StencilState stencil;
    private static String state = "Linked colors";
    public static long splitFrames, capturedLayers;

    private CategoryColorRenderer() { }

    public static void resetFrame() { frameApplied = false; }
    public static boolean skipLegacy() { return frameApplied; }
    public static String status() { return state; }

    private static CategoryColorOptions options() {
        if (!optionsChecked) {
            optionsChecked = true;
            try {
                options = (CategoryColorOptions) Class.forName("org.polyfrost.colorsaturation.client.ColorSaturationConfig")
                        .getField("INSTANCE").get(null);
            } catch (ClassNotFoundException ignored) {
            } catch (ReflectiveOperationException | ClassCastException error) {
                LogManager.getLogger("Arcadium/ColorSaturation").warn("Separate color settings unavailable", error);
            }
        }
        return options;
    }

    public static boolean beginWorld(boolean incompatible) {
        CategoryColorOptions config = options();
        if (config == null || !config.arcadium$colorEnabled() || !config.arcadium$separateEntityColors()) {
            state = "Linked colors";
            releaseBuffers();
            return false;
        }
        config.arcadium$readColors(world, entities);
        if (world.sameAs(entities)) {
            state = "Linked colors (identical settings)";
            releaseBuffers();
            return false;
        }
        if (failed || incompatible || !GL.getCapabilities().OpenGL30) {
            state = "Linked colors (renderer compatibility)";
            return false;
        }
        try (GlState ignored = new GlState()) {
            sourceFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            originalReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            if (sourceFramebuffer == 0 || GL11.glGetInteger(GL13.GL_SAMPLES) != 0 || GL11.glIsEnabled(GL11.GL_STENCIL_TEST)) {
                state = "Linked colors (framebuffer compatibility)";
                return false;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sourceFramebuffer);
            if (attachment(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE
                    || attachment(GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE) {
                state = "Linked colors (framebuffer compatibility)";
                return false;
            }
            originalDepthType = attachment(GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            originalDepth = attachment(GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if ((originalDepthType != GL30.GL_RENDERBUFFER && originalDepthType != GL11.GL_TEXTURE) || originalDepth == 0) {
                state = "Linked colors (depth attachment unavailable)";
                return false;
            }
            originalDepthLevel = originalDepthType == GL11.GL_TEXTURE
                    ? attachment(GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) : 0;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, attachment(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
            int nextWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int nextHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (nextWidth < 1 || nextHeight < 1) return false;
            allocate(nextWidth, nextHeight);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sourceFramebuffer);
            depthAttached = true;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, sharedDepth, 0);
            requireComplete();
            inWorld = true;
            baseReady = false;
            capturing = false;
            category = -1;
            suspendedCategory = -1;
            state = "Separate block and entity colors";
            return true;
        } catch (RuntimeException error) {
            if (depthAttached) restoreDepthAttachment();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalReadFramebuffer);
            inWorld = false;
            failed = true;
            releaseBuffers();
            state = "Linked colors (allocation failed)";
            LogManager.getLogger("Arcadium/ColorSaturation").warn("Separate colors disabled; using linked colors", error);
            return false;
        }
    }

    public static void beginLayer(int nextCategory) {
        if (!inWorld) return;
        endLayer();
        ensureBase();
        category = nextCategory;
        CategoryColorProfile profile = profile();
        if (profile.identity()) return;
        layerReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        if (blend && !supportedBlend(sourceRgb, destinationRgb)) {
            suspendedCategory = category;
            category = -1;
            return;
        }
        integers.clear();
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, integers);
        red = integers.get(0) != 0; green = integers.get(1) != 0;
        blue = integers.get(2) != 0; alpha = integers.get(3) != 0;
        stencil = new StencilState();
        try (GlState ignored = new GlState()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFramebuffer);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glStencilMask(-1);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, layerFramebuffer);
        capturing = true;
        applyCaptureBlend();
        applyCoverage();
        capturedLayers++;
    }

    public static void endLayer() {
        suspendedCategory = -1;
        if (!capturing) { category = -1; return; }
        CategoryColorProfile profile = profile();
        capturing = false;
        GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        GL11.glColorMask(red, green, blue, alpha);
        stencil.restore();
        try (GlState ignored = new GlState()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, backgroundColor);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            prepareQuad();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, layerFramebuffer);
            // Opaque cutouts must remain opaque even when the texture alpha is fractional.
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glColorMask(false, false, false, true);
            draw(2, 0, profile);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            GL11.glColorMask(red, green, blue, alpha);
            draw(1, layerColor, profile);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, layerReadFramebuffer);
            category = -1;
        }
    }

    private static CategoryColorProfile profile() { return category == ENTITY ? entities : world; }

    private static void ensureBase() {
        if (baseReady) return;
        if (!world.identity()) {
            try (GlState ignored = new GlState()) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, layerColor);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
                prepareQuad();
                GL11.glColorMask(true, true, true, true);
                draw(0, layerColor, world);
            }
        }
        baseReady = true;
        frameApplied = true;
    }

    public static void endWorld() {
        if (!inWorld) return;
        try {
            endLayer();
            ensureBase();
        } finally {
            inWorld = false;
            capturing = false;
            category = -1;
            suspendedCategory = -1;
            try (GlState ignored = new GlState()) {
                restoreDepthAttachment();
                prepareQuad();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
                GL11.glColorMask(false, false, false, false);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_ALWAYS);
                GL11.glDepthMask(true);
                GL11.glDepthRange(0, 1);
                draw(3, sharedDepth, world);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sourceFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalReadFramebuffer);
            }
            splitFrames++;
        }
    }

    public static void blendEnabled(boolean enabled) {
        if (suspendedCategory != -1 && !enabled) {
            int resume = suspendedCategory;
            beginLayer(resume);
            return;
        }
        if (!capturing) return;
        blend = enabled;
        if (enabled && !supportedBlend(sourceRgb, destinationRgb)) {
            suspendLayer();
            return;
        }
        applyCaptureBlend();
        applyCoverage();
    }

    public static void blendFunction(int source, int destination, int sourceA, int destinationA) {
        if (suspendedCategory != -1 && supportedBlend(source, destination)) {
            int resume = suspendedCategory;
            beginLayer(resume);
            return;
        }
        if (!capturing) return;
        sourceRgb = source; destinationRgb = destination;
        sourceAlpha = sourceA; destinationAlpha = destinationA;
        if (blend && !supportedBlend(source, destination)) {
            suspendLayer();
            return;
        }
        applyCaptureBlend();
        applyCoverage();
    }

    private static boolean supportedBlend(int source, int destination) {
        return (destination == GL11.GL_ONE_MINUS_SRC_ALPHA && (source == GL11.GL_ONE || source == GL11.GL_SRC_ALPHA))
                || (destination == GL11.GL_ONE && (source == GL11.GL_ONE || source == GL11.GL_SRC_ALPHA || source == GL11.GL_SRC_COLOR))
                || (destination == GL11.GL_ZERO && source == GL11.GL_ONE);
    }

    private static void suspendLayer() {
        // Destination-color effects, such as mining cracks, must blend directly with the visible scene.
        int resume = category;
        endLayer();
        suspendedCategory = resume;
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        if (!capturing) return;
        red = r; green = g; blue = b; alpha = a;
        applyCoverage();
    }

    private static void applyCaptureBlend() {
        // RGB retains the renderer's equation; alpha stores coverage, including across overlapping transparent draws.
        int alphaSource = destinationRgb == GL11.GL_ONE ? GL11.GL_ZERO : GL11.GL_ONE;
        int alphaDestination = destinationRgb == GL11.GL_ONE ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA;
        GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, alphaSource, alphaDestination);
    }

    private static void applyCoverage() {
        boolean writes = red || green || blue;
        GL11.glColorMask(red, green, blue, writes);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(1);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        boolean opaque = !blend || sourceRgb == GL11.GL_ONE && destinationRgb == GL11.GL_ZERO;
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, opaque && writes ? GL11.GL_REPLACE : GL11.GL_KEEP);
    }

    public static void free() {
        if (inWorld) endWorld();
        releaseBuffers();
        failed = false;
        frameApplied = false;
        if (program != 0) { GL20.glDeleteProgram(program); program = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
    }

    private static int attachment(int point, int parameter) {
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, point, parameter);
    }

    private static void restoreDepthAttachment() {
        if (!depthAttached) return;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sourceFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        if (originalDepthType == GL30.GL_RENDERBUFFER) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, originalDepth);
        } else {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, originalDepth, originalDepthLevel);
        }
        depthAttached = false;
    }

    private static void allocate(int nextWidth, int nextHeight) {
        if (program == 0) initializeProgram();
        if (layerFramebuffer != 0 && nextWidth == width && nextHeight == height) return;
        releaseBuffers();
        width = nextWidth; height = nextHeight;
        layerColor = texture(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT);
        backgroundColor = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        sharedDepth = texture(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8);
        layerFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, layerFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, layerColor, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, sharedDepth, 0);
        requireComplete();
    }

    private static int texture(int internalFormat, int format, int type) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0L);
        return texture;
    }

    private static void requireComplete() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete color framebuffer: " + status);
    }

    private static void releaseBuffers() {
        if (layerFramebuffer == 0 && layerColor == 0 && backgroundColor == 0 && sharedDepth == 0) return;
        if (depthAttached) restoreDepthAttachment();
        if (layerFramebuffer != 0) GL30.glDeleteFramebuffers(layerFramebuffer);
        if (layerColor != 0) GL11.glDeleteTextures(layerColor);
        if (backgroundColor != 0) GL11.glDeleteTextures(backgroundColor);
        if (sharedDepth != 0) GL11.glDeleteTextures(sharedDepth);
        layerFramebuffer = layerColor = backgroundColor = sharedDepth = width = height = 0;
    }

    private static void prepareQuad() {
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_POINT);
        for (int plane = 0; plane < 6; plane++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + plane);
        GL11.glDepthMask(false);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        if (samplers()) { GL33.glBindSampler(0, 0); GL33.glBindSampler(1, 0); }
    }

    private static void draw(int mode, int texture, CategoryColorProfile profile) {
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backgroundColor);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (checkFeedback) requireDistinctInputs(mode, texture);
        GL20.glUniform1i(modeUniform, mode);
        GL20.glUniform4f(settingsUniform, profile.saturation, profile.contrast, profile.brightness, profile.hue);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void requireDistinctInputs(int mode, int source) {
        for (int point : new int[] {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_STENCIL_ATTACHMENT}) {
            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE) continue;
            int attached = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (attached != 0 && (attached == source || attached == backgroundColor))
                throw new IllegalStateException("Category shader framebuffer feedback in mode " + mode + ", attachment " + point);
        }
    }

    private static void initializeProgram() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, """
                #version 130
                out vec2 uv;
                void main() {
                    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                    uv = p;
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
        int fragment = 0, created = 0;
        try {
            fragment = shader(GL20.GL_FRAGMENT_SHADER, """
                    #version 130
                    uniform sampler2D Source;
                    uniform sampler2D Background;
                    uniform vec4 Settings;
                    uniform int Mode;
                    in vec2 uv;
                    out vec4 Fragment;
                    void main() {
                        gl_FragDepth = gl_FragCoord.z;
                        if (Mode == 2) { Fragment = vec4(0.0, 0.0, 0.0, 1.0); return; }
                        vec4 sampleColor = texture2D(Source, uv);
                        if (Mode == 3) { gl_FragDepth = sampleColor.r; Fragment = vec4(0.0); return; }
                        float coverage = Mode == 0 ? 1.0 : sampleColor.a;
                        vec3 color = sampleColor.rgb * Settings.z;
                        color = (color - 0.5 * coverage) * Settings.y + 0.5 * coverage;
                        color = mix(vec3(dot(color, vec3(0.3, 0.59, 0.11))), color, Settings.x);
                        float angle = radians(Settings.w);
                        float c = cos(angle), s = sin(angle);
                        vec3 axis = vec3(0.57735026);
                        color = color * c + cross(axis, color) * s + axis * dot(axis, color) * (1.0 - c);
                        Fragment = vec4(color, coverage);
                        if (Mode == 1) Fragment += texture2D(Background, uv) * (1.0 - coverage);
                    }
                    """);
            created = GL20.glCreateProgram();
            GL20.glAttachShader(created, vertex);
            GL20.glAttachShader(created, fragment);
            GL30.glBindFragDataLocation(created, 0, "Fragment");
            GL20.glLinkProgram(created);
            if (GL20.glGetProgrami(created, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(created));
            GL20.glUseProgram(created);
            GL20.glUniform1i(GL20.glGetUniformLocation(created, "Source"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(created, "Background"), 1);
            modeUniform = GL20.glGetUniformLocation(created, "Mode");
            settingsUniform = GL20.glGetUniformLocation(created, "Settings");
            vao = GL30.glGenVertexArrays();
            program = created;
        } catch (RuntimeException error) {
            if (created != 0) GL20.glDeleteProgram(created);
            throw error;
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) return shader;
        String error = GL20.glGetShaderInfoLog(shader);
        GL20.glDeleteShader(shader);
        throw new IllegalStateException(error);
    }

    private static final class GlState implements AutoCloseable {
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int texture, sampler, texture1, sampler1;
        GlState() {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            sampler1 = samplers() ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            sampler = samplers() ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
        }
        @Override public void close() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousVao);
            GL11.glPopAttrib();
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            if (samplers()) GL33.glBindSampler(1, sampler1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (samplers()) GL33.glBindSampler(0, sampler);
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static boolean samplers() { return GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects; }

    private static final class StencilState {
        private final boolean enabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final int function = GL11.glGetInteger(GL11.GL_STENCIL_FUNC), reference = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        private final int valueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK), writeMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        private final int fail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL), depthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        private final int pass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        private final int backFunction = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FUNC), backReference = GL11.glGetInteger(GL20.GL_STENCIL_BACK_REF);
        private final int backValueMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_VALUE_MASK), backWriteMask = GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK);
        private final int backFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_FAIL), backDepthFail = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL);
        private final int backPass = GL11.glGetInteger(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS);
        void restore() {
            if (enabled) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL20.glStencilFuncSeparate(GL11.GL_FRONT, function, reference, valueMask);
            GL20.glStencilFuncSeparate(GL11.GL_BACK, backFunction, backReference, backValueMask);
            GL20.glStencilMaskSeparate(GL11.GL_FRONT, writeMask);
            GL20.glStencilMaskSeparate(GL11.GL_BACK, backWriteMask);
            GL20.glStencilOpSeparate(GL11.GL_FRONT, fail, depthFail, pass);
            GL20.glStencilOpSeparate(GL11.GL_BACK, backFail, backDepthFail, backPass);
        }
    }
}
