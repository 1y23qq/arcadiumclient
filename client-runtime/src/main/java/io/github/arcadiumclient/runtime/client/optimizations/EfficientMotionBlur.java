// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.compat.polyblur.HybridBlurChainAccess;
import io.github.arcadiumclient.runtime.mixin.compat.polyblur.HybridBlurPassAccess;
import io.github.arcadiumclient.runtime.mixin.compat.polyblur.HybridBlurUniformAccess;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import kotlin.ranges.IntRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Effect;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.manager.ReloadableResourceManager;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class EfficientMotionBlur {
    private static final String HYBRID = "org/polyfrost/polyblur/client/blur/phosphor/HybridHandPhosphor.class";
    private static final String PASSES = "org/polyfrost/polyblur/client/blur/phosphor/PhosphorBlurKt.class";
    private static final List<String> AUX_NAMES = List.of("PrevSampler", "WorldSampler");
    private static final String[] RESOURCES = {
        "shaders/post/phosphor_hand.json", "shaders/program/phosphor_hand.json", "shaders/program/phosphor_hand.fsh",
        "shaders/program/sobel.vsh", "shaders/program/blit.json", "shaders/program/blit.vsh", "shaders/program/blit.fsh"
    };
    private static final String[] HASHES = {
        "fbce47f8c1f1ada185c0f68dfde54cb376444109bb92487016f78c9f672fc075",
        "b3621875feaf38120605967cc1418dccaa58df0990d822dd9a6e903841d179b0",
        "b4ed64b5a924c89b53ce2de97dc09be09aa16b5372f8946df1335ddfa3fd5a7b",
        "60b7661feb6305f7bb72a7055493b36cf64290f9b43c8207d451fd1ec7214705",
        "699a33dc01597a4d4bde0ac9d22e9f0d36cdf77436a461604136fff106878102",
        "a0b051dcc203ff4640b50b226e259c326ece72708f766cf895fde0fcbd44ae37",
        "9dd20d81362e583760f4f31f3cf10b7748fbd2263adae5ca0a63a5d802c08d42"
    };
    private static ResourceManager resources;
    private static boolean classChecked, stockClasses, resourceChecked, stockResources, failed;
    private static Plan cached, scope;
    private static Rejected rejected;
    private static Thread ownerThread;
    private static String state = "Off";
    public static long swappedFrames, skippedCopies, nativeFrames;

    private EfficientMotionBlur() { }

    public static boolean begin(PostChain chain, RenderTarget main) {
        if (!BetaOptimizationsConfig.getEfficientMotionBlur() || failed || chain == null) return false;
        if (scope != null) { scope.valid = false; return false; }
        if (ownerThread == null) ownerThread = Thread.currentThread();
        if (Thread.currentThread() != ownerThread) return false;
        try {
            if (GpuPlayerPipeline.hasForgeHooks() || !stockResources() || rejected != null && rejected.matches(chain, main)) { nativeFrames++; return false; }
            if (cached == null || cached.chain != chain || !cached.geometryMatches(main)) {
                cached = Plan.create(chain, main);
                if (cached == null) { rejected = new Rejected(chain, main); nativeFrames++; state = "Native post chain"; return false; }
                rejected = null;
            }
            if (!cached.matches(main)) { nativeFrames++; state = "Native target or copy state"; return false; }
            scope = cached;
            scope.step = 1;
            scope.skipped = false;
            scope.valid = true;
            return true;
        } catch (Throwable error) {
            disable(error);
            return false;
        }
    }

    public static boolean process(PostPass pass, float delta, PostChain chain, IntRange range) {
        Plan active = scope;
        if (active == null || active.chain != chain) return true;
        if (!active.valid || range == null || range.getFirst() != 1 || range.getLast() != 3 || delta != 0f) {
            repair(active); active.valid = false; return true;
        }
        if (active.step == 1 && pass == active.passes.get(1)) { active.step = 2; return true; }
        if (active.step == 2 && pass == active.passes.get(2) && active.matches(active.main)) {
            active.step = 3;
            active.skipped = true;
            skippedCopies++;
            return false;
        }
        if (active.step == 3 && pass == active.passes.get(3)) { active.step = 4; return true; }
        repair(active); active.valid = false; return true;
    }

    public static void end(boolean completed) {
        Plan active = scope;
        try {
            if (active == null) return;
            if (completed && active.valid && active.skipped && active.step == 4 && active.matches(active.main)) {
                swapAttachments(active.swap, active.previous);
                active.rememberIds();
                active.skipped = false;
                swappedFrames++;
                state = "Private history ping-pong";
            } else {
                repair(active);
                nativeFrames++;
                state = "Native incomplete pass sequence";
            }
        } catch (Throwable error) {
            repair(active);
            disable(error);
        } finally {
            scope = null;
        }
    }

    public static void invalidate() {
        resourceChecked = false;
        cached = null;
        rejected = null;
        if (scope != null) scope.valid = false;
    }

    private static void repair(Plan active) {
        if (active == null || !active.skipped) return;
        active.skipped = false;
        try { active.passes.get(2).process(0f); }
        catch (Throwable error) { disable(error); }
    }

    static void swapAttachments(RenderTarget swap, RenderTarget previous) {
        int framebuffer = swap.frameBufferId, color = swap.colorTextureId, depth = swap.depthBufferId;
        swap.frameBufferId = previous.frameBufferId;
        swap.colorTextureId = previous.colorTextureId;
        swap.depthBufferId = previous.depthBufferId;
        previous.frameBufferId = framebuffer;
        previous.colorTextureId = color;
        previous.depthBufferId = depth;
    }

    private static boolean stockResources() throws Exception {
        ResourceManager current = Minecraft.getInstance().getResourceManager();
        if (resources != current) {
            if (!(current instanceof ReloadableResourceManager reloadable)) return false;
            resources = current;
            invalidate();
            reloadable.addListener(ignored -> invalidate());
        }
        if (!classChecked) {
            classChecked = true;
            ClassLoader loader = EfficientMotionBlur.class.getClassLoader();
            try (InputStream hybrid = loader.getResourceAsStream(HYBRID); InputStream passes = loader.getResourceAsStream(PASSES)) {
                stockClasses = hash(hybrid).equals("cf8bbc0f0e132b162ba4a7cc3b5ff8a9c81c851f826f90f84c836edb3e7e7f0c")
                    && hash(passes).equals("162142b86a372272db85d90db98bb3eab8a8dd6abe06f3ea3fe005375c1650cc");
            }
        }
        if (!stockClasses) { state = "Native PolyBlur implementation"; return false; }
        if (!resourceChecked) {
            resourceChecked = true;
            stockResources = true;
            for (int i = 0; i < RESOURCES.length; i++) {
                try (InputStream input = resources.getResource(new Identifier("minecraft", RESOURCES[i])).asStream()) {
                    if (!hash(input).equals(HASHES[i])) { stockResources = false; break; }
                } catch (java.io.IOException unavailable) {
                    stockResources = false;
                    break;
                }
            }
        }
        if (!stockResources) state = "Native custom blur resources";
        return stockResources;
    }

    private static String hash(InputStream input) throws Exception {
        if (input == null) return "";
        byte[] bytes = input.readNBytes(131073);
        if (bytes.length > 131072) return "";
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static FloatBuffer copyValues(PostPass pass) {
        if (pass.getEffect().getClass() != Effect.class) return null;
        var uniform = pass.getEffect().getUniform("ColorModulate");
        return uniform instanceof HybridBlurUniformAccess access ? access.arcadium$blurFloatValues() : null;
    }

    private static boolean unitCopy(FloatBuffer values) {
        return values != null && values.capacity() == 4 && values.get(0) == 1f && values.get(1) == 1f
            && values.get(2) == 1f && values.get(3) == 1f;
    }

    private static boolean target(RenderTarget value, int width, int height) {
        return value != null && value.getClass() == RenderTarget.class && width > 0 && height > 0
            && value.width == width && value.height == height && value.viewWidth == width && value.viewHeight == height
            && value.frameBufferId > 0 && value.colorTextureId > 0 && value.filterMode == GL11.GL_NEAREST;
    }

    private static boolean privateTarget(RenderTarget value, int width, int height) {
        if (!target(value, width, height) || !value.useDepth || value.depthBufferId <= 0
                || value.clearChannels == null || value.clearChannels.length != 4) return false;
        for (float channel : value.clearChannels) if (channel != 0f) return false;
        return true;
    }

    private static boolean distinct(RenderTarget a, RenderTarget b) {
        return a != b && a.frameBufferId != b.frameBufferId && a.colorTextureId != b.colorTextureId
            && (a.depthBufferId <= 0 || b.depthBufferId <= 0 || a.depthBufferId != b.depthBufferId);
    }

    private static boolean rgbaAttachment(RenderTarget target) {
        if (!GL.getCapabilities().OpenGL30) return false;
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GlStateManager.bindTexture(target.colorTextureId);
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT) != GL11.GL_RGBA8
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != target.width
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != target.height
                    || GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) != GL11.GL_NEAREST
                    || GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) != GL11.GL_NEAREST) return false;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            return GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == target.colorTextureId
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == target.depthBufferId;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager.bindTexture(texture);
        }
    }

    private static boolean compiledProgram(Effect effect, boolean composite) throws Exception {
        int program = effect.getId();
        if (GL20.glGetProgrami(program, GL20.GL_ATTACHED_SHADERS) != 2) return false;
        IntBuffer shaders = BufferUtils.createIntBuffer(2);
        IntBuffer count = BufferUtils.createIntBuffer(1);
        GL20.glGetAttachedShaders(program, count, shaders);
        boolean vertex = false, fragment = false;
        for (int i = 0; i < count.get(0); i++) {
            int shader = shaders.get(i);
            if (GL20.glGetShaderi(shader, GL20.GL_SHADER_SOURCE_LENGTH) > 131072) return false;
            String source = GL20.glGetShaderSource(shader).replace("\r\n", "\n").replace('\r', '\n').trim();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
            if (GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE) == GL20.GL_VERTEX_SHADER) {
                vertex = hash.equals(composite ? "20c7366031e66060a102fac695277793f2c12c113d7b20490d5d6abe50e353f1"
                    : "f094a6b0b6aa98584c6e355c9a3f0592d2cfb318bba8b19757e4cd8200424bf6");
            } else if (GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE) == GL20.GL_FRAGMENT_SHADER) {
                fragment = hash.equals(composite ? "adcb4bc0a8b0b9ff4e7d47d89cfaa2810cdd65085a3e8d52a51f6d7d7a1c0322"
                    : "02602f016a0da8e7c63752934010543fdfe79f744f78166b0593e287b47890fd");
            }
        }
        return vertex && fragment;
    }

    private static void disable(Throwable error) {
        if (!failed) LogManager.getLogger("Arcadium/Motion Blur").warn("Private blur history optimization disabled", error);
        failed = true;
        state = "Native after unsupported state";
    }

    public static String status() {
        return BetaOptimizationsConfig.getEfficientMotionBlur() ? "Optimized motion blur: " + state + " | " + swappedFrames + " history copies avoided"
            : "Optimized motion blur: Off";
    }

    private static final class Plan {
        final PostChain chain;
        final List<PostPass> passes;
        final RenderTarget main, swap, previous, world;
        final int width, height;
        final FloatBuffer worldCopyValues, historyCopyValues, mainCopyValues;
        final int auxWidth0, auxWidth1, auxHeight0, auxHeight1;
        int swapFramebuffer, swapColor, swapDepth, previousFramebuffer, previousColor, previousDepth;
        int mainFramebuffer, mainColor, mainDepth, worldFramebuffer, worldColor, worldDepth;
        int step;
        boolean skipped, valid;

        private Plan(PostChain chain, List<PostPass> passes, RenderTarget main, RenderTarget swap, RenderTarget previous, RenderTarget world) {
            this.chain = chain; this.passes = List.copyOf(passes); this.main = main; this.swap = swap; this.previous = previous; this.world = world;
            worldCopyValues = copyValues(passes.get(0)); historyCopyValues = copyValues(passes.get(2)); mainCopyValues = copyValues(passes.get(3));
            var composite = (HybridBlurPassAccess) passes.get(1);
            auxWidth0 = composite.arcadium$blurAuxWidths().get(0); auxWidth1 = composite.arcadium$blurAuxWidths().get(1);
            auxHeight0 = composite.arcadium$blurAuxHeights().get(0); auxHeight1 = composite.arcadium$blurAuxHeights().get(1);
            width = main.viewWidth; height = main.viewHeight; rememberIds();
        }

        static Plan create(PostChain chain, RenderTarget main) throws Exception {
            if (chain.getClass() != PostChain.class || !(chain instanceof HybridBlurChainAccess access)
                    || !"minecraft:shaders/post/phosphor_hand.json".equals(chain.getName()) || access.arcadium$blurMain() != main) return null;
            List<PostPass> passes = access.arcadium$blurPasses();
            Map<String, RenderTarget> targets = access.arcadium$blurTargets();
            if (passes.size() != 4 || targets.size() != 3) return null;
            RenderTarget swap = targets.get("swap"), previous = targets.get("previous"), world = targets.get("world");
            if (swap == null || previous == null || world == null || main == null) return null;
            List<RenderTarget> sized = access.arcadium$blurFullSizedTargets();
            if (sized.size() != 3 || !sized.contains(swap) || !sized.contains(previous) || !sized.contains(world)) return null;
            for (PostPass pass : passes) if (pass.getClass() != PostPass.class || !(pass instanceof HybridBlurPassAccess)) return null;
            var composite = (HybridBlurPassAccess) passes.get(1);
            if (composite.arcadium$blurAuxWidths().size() != 2 || composite.arcadium$blurAuxHeights().size() != 2
                    || passes.get(1).getEffect().getUniform("AuxSize0") != null || passes.get(1).getEffect().getUniform("AuxSize1") != null) return null;
            if (passes.get(0).inTarget != main || passes.get(0).outTarget != world
                    || passes.get(1).inTarget != main || passes.get(1).outTarget != swap
                    || passes.get(2).inTarget != swap || passes.get(2).outTarget != previous
                    || passes.get(3).inTarget != swap || passes.get(3).outTarget != main) return null;
            Plan plan = new Plan(chain, passes, main, swap, previous, world);
            if (!plan.matches(main) || !rgbaAttachment(swap) || !rgbaAttachment(previous)) return null;
            for (int i = 0; i < 4; i++) if (!compiledProgram(passes.get(i).getEffect(), i == 1)) return null;
            return plan;
        }

        boolean matches(RenderTarget target) {
            var access = (HybridBlurChainAccess) chain;
            List<PostPass> current = access.arcadium$blurPasses();
            if (current.size() != 4 || access.arcadium$blurMain() != main || access.arcadium$blurTargets().size() != 3
                    || access.arcadium$blurTargets().get("swap") != swap || access.arcadium$blurTargets().get("previous") != previous
                    || access.arcadium$blurTargets().get("world") != world) return false;
            for (int i = 0; i < 4; i++) if (current.get(i) != passes.get(i)) return false;
            if (target != main || !target(main, width, height) || !target(world, width, height)
                    || !privateTarget(swap, width, height) || !privateTarget(previous, width, height)
                    || !distinct(swap, previous) || !distinct(swap, main) || !distinct(swap, world)
                    || !distinct(previous, main) || !distinct(previous, world) || !distinct(main, world)
                    || swap.frameBufferId != swapFramebuffer || swap.colorTextureId != swapColor || swap.depthBufferId != swapDepth
                    || previous.frameBufferId != previousFramebuffer || previous.colorTextureId != previousColor || previous.depthBufferId != previousDepth
                    || !unitCopy(worldCopyValues) || !unitCopy(historyCopyValues) || !unitCopy(mainCopyValues)) return false;
            var composite = (HybridBlurPassAccess) passes.get(1);
            if (!composite.arcadium$blurAuxNames().equals(AUX_NAMES)
                    || composite.arcadium$blurAuxAssets().size() != 2
                    || composite.arcadium$blurAuxAssets().get(0) != previous || composite.arcadium$blurAuxAssets().get(1) != world
                    || !sizes(composite.arcadium$blurAuxWidths(), auxWidth0, auxWidth1)
                    || !sizes(composite.arcadium$blurAuxHeights(), auxHeight0, auxHeight1)) return false;
            for (int i = 0; i < 4; i++) if (i != 1 && !((HybridBlurPassAccess) passes.get(i)).arcadium$blurAuxAssets().isEmpty()) return false;
            return true;
        }

        private static boolean sizes(List<Integer> values, int first, int second) {
            // Vanilla keeps these values on resize; the verified shader has no AuxSize uniforms.
            return values.size() == 2 && values.get(0) == first && values.get(1) == second;
        }

        boolean geometryMatches(RenderTarget target) {
            return target == main && main.width == width && main.height == height && main.viewWidth == width && main.viewHeight == height
                && world.width == width && world.height == height && swap.width == width && swap.height == height
                && previous.width == width && previous.height == height
                && main.frameBufferId == mainFramebuffer && main.colorTextureId == mainColor && main.depthBufferId == mainDepth
                && world.frameBufferId == worldFramebuffer && world.colorTextureId == worldColor && world.depthBufferId == worldDepth
                && swap.frameBufferId == swapFramebuffer && swap.colorTextureId == swapColor && swap.depthBufferId == swapDepth
                && previous.frameBufferId == previousFramebuffer && previous.colorTextureId == previousColor && previous.depthBufferId == previousDepth;
        }

        void rememberIds() {
            swapFramebuffer = swap.frameBufferId; swapColor = swap.colorTextureId; swapDepth = swap.depthBufferId;
            previousFramebuffer = previous.frameBufferId; previousColor = previous.colorTextureId; previousDepth = previous.depthBufferId;
            mainFramebuffer = main.frameBufferId; mainColor = main.colorTextureId; mainDepth = main.depthBufferId;
            worldFramebuffer = world.frameBufferId; worldColor = world.colorTextureId; worldDepth = world.depthBufferId;
        }
    }

    private static final class Rejected {
        final PostChain chain;
        final RenderTarget[] targets;
        final int[] properties = new int[36];
        Rejected(PostChain chain, RenderTarget main) {
            this.chain = chain;
            targets = new RenderTarget[]{main, chain.getTempTarget("swap"), chain.getTempTarget("previous"), chain.getTempTarget("world")};
            for (int i = 0; i < 4; i++) for (int j = 0; j < 9; j++) properties[i * 9 + j] = value(targets[i], j);
        }
        boolean matches(PostChain chain, RenderTarget main) {
            if (chain != this.chain || main != targets[0] || chain.getTempTarget("swap") != targets[1]
                    || chain.getTempTarget("previous") != targets[2] || chain.getTempTarget("world") != targets[3]) return false;
            for (int i = 0; i < 4; i++) for (int j = 0; j < 9; j++) if (properties[i * 9 + j] != value(targets[i], j)) return false;
            return true;
        }
        private static int value(RenderTarget target, int index) {
            if (target == null) return 0;
            return switch (index) {
                case 0 -> target.frameBufferId; case 1 -> target.colorTextureId; case 2 -> target.depthBufferId;
                case 3 -> target.width; case 4 -> target.height; case 5 -> target.viewWidth; case 6 -> target.viewHeight;
                case 7 -> target.filterMode; default -> target.useDepth ? 1 : 0;
            };
        }
    }
}
