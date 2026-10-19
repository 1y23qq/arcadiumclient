package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

final class NoRenderQa {
    private static boolean done;

    private NoRenderQa() {}

    static void verify(Minecraft minecraft, Map<String, Object> checks) throws Exception {
        if (done) return;
        Class<?> config = Class.forName("io.github.arcadiumclient.runtime.client.features.NoRenderConfig");
        Class<?> feature = Class.forName("io.github.arcadiumclient.runtime.client.features.NoRender");
        Method refresh = feature.getMethod("refresh");
        Map<Field, Boolean> saved = new LinkedHashMap<>();
        for (Field field : config.getDeclaredFields()) {
            if (field.getType() != boolean.class) continue;
            field.setAccessible(true);
            saved.put(field, field.getBoolean(null));
            field.setBoolean(null, false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        checks.put("noRender", result);
        try {
            require(saved.size() >= 60, "NoRender did not register the expected individual options");
            result.put("switches", saved.size());
            var grass = Blocks.TALLGRASS.getStateFromMetadata(1);
            BlockPos position = new BlockPos(minecraft.player);
            Method hideBlock = java.util.Arrays.stream(feature.getMethods())
                .filter(method -> method.getName().equals("hideBlock")).findFirst().orElseThrow();
            config.getMethod("setGrass", boolean.class).invoke(null, true);
            refresh.invoke(null);
            require(Boolean.FALSE.equals(hideBlock.invoke(null, grass, position, minecraft.world)),
                "A selected option changed rendering while Enabled was off");
            result.put("masterOffPreservedRendering", true);

            config.getMethod("setEnabled", boolean.class).invoke(null, true);
            refresh.invoke(null);
            require(Boolean.TRUE.equals(hideBlock.invoke(null, grass, position, minecraft.world)), "The grass filter was not activated");
            require(Boolean.FALSE.equals(hideBlock.invoke(null, Blocks.STONE.defaultState(), position, minecraft.world)),
                "NoRender hid ordinary terrain");
            require(Boolean.FALSE.equals(hideBlock.invoke(null, Blocks.TALLGRASS.getStateFromMetadata(2), position, minecraft.world)),
                "The grass option also hid independently configured ferns");

            boolean vanilla = false;
            for (Field field : Minecraft.class.getDeclaredFields()) {
                for (Method method : field.getType().getDeclaredMethods()) {
                    Class<?>[] args = method.getParameterTypes();
                    if (method.getReturnType() != boolean.class || args.length != 4 || !args[0].isInstance(grass)
                            || !args[1].isInstance(position) || !args[2].isInstance(minecraft.world)) continue;
                    field.setAccessible(true);
                    Object dispatcher = field.get(minecraft);
                    Object buffer = args[3].getConstructor(int.class).newInstance(64);
                    method.setAccessible(true);
                    require(Boolean.FALSE.equals(method.invoke(dispatcher, grass, position, minecraft.world, buffer)),
                        "The vanilla block renderer did not skip a filtered model");
                    vanilla = true;
                }
            }
            require(vanilla, "The real vanilla block dispatch hook was not exercised");
            result.put("vanillaMeshSkipped", true);

            if (FabricLoader.getInstance().isModLoaded("argentum")) {
                Class<?> renderer = Class.forName("dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer");
                Object worker = renderer.getConstructors()[0].newInstance(null, null);
                Method render = java.util.Arrays.stream(renderer.getMethods())
                    .filter(method -> method.getName().equals("render") && method.getParameterCount() == 6)
                    .findFirst().orElseThrow();
                render.invoke(worker, grass, position, null, null, null, null);
                result.put("argentumMeshSkippedBeforeWork", true);
                config.getMethod("setEnabled", boolean.class).invoke(null, false);
                refresh.invoke(null);
                try {
                    render.invoke(worker, grass, position, null, null, null, null);
                    throw new IllegalStateException("The master switch did not restore Argentum's model path");
                } catch (InvocationTargetException expected) {
                    require(expected.getCause() instanceof NullPointerException,
                        "Argentum's restored model path failed unexpectedly: " + expected.getCause());
                }
                result.put("argentumPathRestored", true);
            }

            config.getMethod("setEnabled", boolean.class).invoke(null, true);
            refresh.invoke(null);
            config.getMethod("setPlayers", boolean.class).invoke(null, true);
            refresh.invoke(null);
            QaPlayer other = new QaPlayer(minecraft.world,
                new com.mojang.authlib.GameProfile(new java.util.UUID(0, 1889), "NoRenderTest"));
            Method hideEntity = java.util.Arrays.stream(feature.getMethods())
                .filter(method -> method.getName().equals("hideEntity")).findFirst().orElseThrow();
            require(Boolean.TRUE.equals(hideEntity.invoke(null, other)), "The other-player filter did not match another player");
            require(Boolean.FALSE.equals(hideEntity.invoke(null, minecraft.player)), "The other-player filter hid the local player");
            boolean entityDispatcher = false;
            for (Field field : Minecraft.class.getDeclaredFields()) {
                for (Method method : field.getType().getDeclaredMethods()) {
                    Class<?>[] args = method.getParameterTypes();
                    if (method.getReturnType() != boolean.class || args.length != 7 || !args[0].isInstance(other)
                            || args[1] != double.class || args[4] != float.class || args[6] != boolean.class) continue;
                    field.setAccessible(true);
                    method.setAccessible(true);
                    require(Boolean.FALSE.equals(method.invoke(field.get(minecraft), other, 0d, 0d, 0d, 0f, 0f, false)),
                        "The real entity dispatcher did not skip a filtered player");
                    entityDispatcher = true;
                }
            }
            require(entityDispatcher, "The real entity render hook was not exercised");
            result.put("otherPlayerSkippedAndSelfPreserved", true);
            config.getMethod("setPlayers", boolean.class).invoke(null, false);
            refresh.invoke(null);
            verifyLeafCulling(minecraft, config, refresh, result);
            var particle = minecraft.particleManager.addParticle(9, minecraft.player.x, minecraft.player.y + 1,
                minecraft.player.z, 0, 0, 0);
            require(particle != null, "The reference critical-hit particle was not created");
            Class<?> tracked = Class.forName("io.github.arcadiumclient.runtime.client.features.NoRenderParticle");
            require(Integer.valueOf(9).equals(tracked.getMethod("arcadium$particleType").invoke(particle)),
                "An existing particle lost its type");
            config.getMethod("setCriticalParticles", boolean.class).invoke(null, true);
            refresh.invoke(null);
            require(minecraft.particleManager.addParticle(9, minecraft.player.x, minecraft.player.y + 1,
                minecraft.player.z, 0, 0, 0) == null, "A filtered particle was still spawned");
            Method hideParticle = java.util.Arrays.stream(feature.getMethods())
                .filter(method -> method.getName().equals("hideParticle")).findFirst().orElseThrow();
            require(Boolean.TRUE.equals(hideParticle.invoke(null, particle)), "An existing particle was not hidden after changing its filter");
            result.put("newAndExistingParticlesFiltered", true);
            Object spark = directParticle(minecraft, 3);
            require(Integer.valueOf(-1).equals(tracked.getMethod("arcadium$particleType").invoke(spark)),
                "The direct firework fixture accidentally used the tracked factory path");
            require(Boolean.FALSE.equals(hideParticle.invoke(null, spark)), "The critical-hit switch also hid firework sparks");
            config.getMethod("setFireworkParticles", boolean.class).invoke(null, true);
            refresh.invoke(null);
            require(Boolean.TRUE.equals(hideParticle.invoke(null, spark)), "A directly constructed firework spark bypassed its filter");
            boolean overlay = false;
            Class<?> particleClass = hideParticle.getParameterTypes()[0];
            for (Class<?> nested : spark.getClass().getDeclaringClass().getDeclaredClasses()) {
                if (!particleClass.isAssignableFrom(nested)) continue;
                for (var constructor : nested.getDeclaredConstructors()) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if (parameters.length != 4 || !parameters[0].isInstance(minecraft.world)
                            || parameters[1] != double.class || parameters[2] != double.class || parameters[3] != double.class) continue;
                    constructor.setAccessible(true);
                    Object flash = constructor.newInstance(minecraft.world, minecraft.player.x, minecraft.player.y, minecraft.player.z);
                    require(Boolean.TRUE.equals(hideParticle.invoke(null, flash)), "A directly constructed firework flash bypassed its filter");
                    overlay = true;
                }
            }
            require(overlay, "The firework flash fixture was not constructed");
            result.put("directFireworkSparksAndOverlayFiltered", true);
            Object barrier = directParticle(minecraft, 35);
            config.getMethod("setBarrierParticles", boolean.class).invoke(null, true);
            refresh.invoke(null);
            require(Boolean.TRUE.equals(hideParticle.invoke(null, barrier)), "An untracked barrier marker bypassed its filter");
            result.put("directBarrierParticleFiltered", true);
            config.getMethod("setEnabled", boolean.class).invoke(null, false);
            refresh.invoke(null);
            require(Boolean.FALSE.equals(hideParticle.invoke(null, particle)), "Master off did not restore existing particles");
            require(minecraft.particleManager.addParticle(9, minecraft.player.x, minecraft.player.y + 1,
                minecraft.player.z, 0, 0, 0) != null, "Master off did not restore particle spawning");
            result.put("masterOffRestoredParticles", true);
        } finally {
            for (Map.Entry<Field, Boolean> entry : saved.entrySet()) entry.getKey().setBoolean(null, entry.getValue());
            refresh.invoke(null);
            result.put("configurationRestored", true);
        }
        done = true;
    }

    private static void verifyLeafCulling(Minecraft minecraft, Class<?> config, Method refresh, Map<String, Object> result) throws Exception {
        boolean originalSolid = Blocks.LEAVES.isSolidRender();
        boolean originalSolid2 = Blocks.LEAVES2.isSolidRender();
        boolean originalFancy = minecraft.options.fancyGraphics;
        Object extras = null, originalLeafQuality = null;
        Field leafQuality = null;
        try {
            extras = Class.forName("dev.rdh.argentum.extras.ArgentumExtras").getField("CONFIG").get(null);
            leafQuality = extras.getClass().getField("leafQuality");
            originalLeafQuality = leafQuality.get(extras);
        } catch (ClassNotFoundException ignored) { }
        BlockState leaf = Blocks.LEAVES.getStateFromMetadata(0);
        BlockPos origin = new BlockPos(0, 0, 0);
        BlockState worldBefore = minecraft.world.getBlockState(origin);
        WorldView leafWorld = (WorldView) java.lang.reflect.Proxy.newProxyInstance(WorldView.class.getClassLoader(),
            new Class<?>[] {WorldView.class}, (proxy, method, args) -> {
                if (method.getReturnType() == BlockState.class) return leaf;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        try {
            if (leafQuality != null) leafQuality.set(extras, leafQuality.getType().getField("FAST").get(null));
            minecraft.options.fancyGraphics = false;
            Blocks.LEAVES.setCulling(false);
            Blocks.LEAVES2.setCulling(false);
            config.getMethod("setLeaves", boolean.class).invoke(null, true);
            for (boolean enabled : new boolean[] {false, true, false}) {
                config.getMethod("setEnabled", boolean.class).invoke(null, enabled);
                refresh.invoke(null);
                require(Blocks.LEAVES.isSolidRender(), "NoRender changed global leaf solidity");
                require(Blocks.STONE.shouldRenderFace(leafWorld, origin, Direction.EAST) == enabled,
                    "Native face culling behind fast leaves did not follow the master switch");
            }
            result.put("nativeFastLeafFacesOffOnOff", true);
            if (FabricLoader.getInstance().isModLoaded("argentum")) verifyArgentumLeafCulling(minecraft, config, refresh, leaf, origin, result);
            require(minecraft.world.getBlockState(origin) == worldBefore, "The private render fixture modified live world blocks");
            result.put("leafWorldAndSolidityPreserved", true);
        } finally {
            if (leafQuality != null) leafQuality.set(extras, originalLeafQuality);
            minecraft.options.fancyGraphics = originalFancy;
            config.getMethod("setLeaves", boolean.class).invoke(null, false);
            config.getMethod("setEnabled", boolean.class).invoke(null, true);
            refresh.invoke(null);
            Blocks.LEAVES.setCulling(!originalSolid);
            Blocks.LEAVES2.setCulling(!originalSolid2);
        }
    }

    private static void verifyArgentumLeafCulling(Minecraft minecraft, Class<?> config, Method refresh, BlockState leaf,
                                                   BlockPos origin, Map<String, Object> result) throws Exception {
        Class<?> sectionPos = Class.forName("org.embeddedt.embeddium.impl.util.position.SectionPos");
        Object position = sectionPos.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0);
        Class<?> cacheClass = Class.forName("dev.rdh.argentum.impl.world.cloned.ClonedChunkSectionCache");
        Object cache = cacheClass.getConstructors()[0].newInstance(minecraft.world);
        Class<?> contextClass = Class.forName("dev.rdh.argentum.impl.world.cloned.ChunkRenderContext");
        Method prepare = java.util.Arrays.stream(contextClass.getMethods()).filter(method -> method.getName().equals("prepare")).findFirst().orElseThrow();
        Object context = prepare.invoke(null, minecraft.world, position, cache);
        require(context != null, "The owned fixture's origin render section was empty");
        Field originSection = contextClass.getDeclaredField("originSection");
        originSection.setAccessible(true);
        Object section = originSection.get(context);
        Field blockStates = section.getClass().getDeclaredField("blockStates");
        blockStates.setAccessible(true);
        java.util.Arrays.fill((char[]) blockStates.get(section), (char) Block.STATE_REGISTRY.getId(leaf));
        Field rows = section.getClass().getDeclaredField("nonAirRows");
        rows.setAccessible(true);
        java.util.Arrays.fill((short[]) rows.get(section), (short) -1);
        require(((WorldView) context).getBlockState(origin).getBlock() == Blocks.LEAVES,
            "The private render section was not filled with leaf models");

        Class<?> rendererClass = Class.forName("dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer");
        Object renderer = rendererClass.getConstructors()[0].newInstance(null, null);
        rendererClass.getMethod("beginSection").invoke(renderer);
        Field neighbor = rendererClass.getDeclaredField("neighborPos");
        neighbor.setAccessible(true);
        ((BlockPos.Mutable) neighbor.get(renderer)).set(0, 0, 0);
        Object model = null;
        for (Field field : Minecraft.class.getDeclaredFields()) {
            for (Method method : field.getType().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 3 || !types[0].isInstance(Blocks.STONE.defaultState())
                        || !types[1].isInstance(context) || !types[2].isInstance(origin)) continue;
                field.setAccessible(true);
                method.setAccessible(true);
                model = method.invoke(field.get(minecraft), Blocks.STONE.defaultState(), context, origin);
            }
        }
        require(model != null, "The native stone model was not found");
        Method hidden = java.util.Arrays.stream(rendererClass.getDeclaredMethods())
            .filter(method -> method.getName().equals("isHiddenByNeighbor")).findFirst().orElseThrow();
        hidden.setAccessible(true);
        Boolean originalCoverage = null;
        java.util.List<Boolean> faceCoverage = new java.util.ArrayList<>();
        for (boolean enabled : new boolean[] {false, true, false}) {
            config.getMethod("setEnabled", boolean.class).invoke(null, enabled);
            refresh.invoke(null);
            rendererClass.getMethod("beginSection").invoke(renderer);
            boolean actual = Boolean.TRUE.equals(hidden.invoke(renderer, model, origin, context, Direction.EAST));
            if (enabled) require(!actual, "Argentum's independent face coverage still hid a face behind filtered leaves");
            else if (originalCoverage == null) originalCoverage = actual;
            else require(originalCoverage == actual, "Master off did not restore Argentum's original leaf face coverage");
            faceCoverage.add(actual);
        }
        result.put("argentumLeafFaceCoverageOffOnOff", faceCoverage);
        char[] privateStates = (char[]) blockStates.get(section);
        char originalState = privateStates[0];
        config.getMethod("setEnabled", boolean.class).invoke(null, true);
        refresh.invoke(null);
        rendererClass.getMethod("beginSection").invoke(renderer);
        try {
            privateStates[0] = (char) Block.STATE_REGISTRY.getId(Blocks.STONE.defaultState());
            require(Boolean.TRUE.equals(hidden.invoke(renderer, model, origin, context, Direction.EAST)),
                "The leaf filter disabled face culling behind an ordinary stone cube");
        } finally { privateStates[0] = originalState; }
        result.put("ordinaryCubeFaceCullingPreserved", true);

        Object worldRenderer = Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getMethod("instance").invoke(null);
        Object passes = worldRenderer.getClass().getMethod("getRenderPassConfiguration").invoke(worldRenderer);
        Class<?> buildContextClass = Class.forName("dev.rdh.argentum.impl.render.terrain.compile.ArgentumChunkBuildContext");
        Object builder = buildContextClass.getConstructors()[0].newInstance(passes);
        Class<?> renderSection = Class.forName("org.embeddedt.embeddium.impl.render.chunk.RenderSection");
        Object render = renderSection.getConstructors()[0].newInstance(null, 0, 0, 0);
        Class<?> taskClass = Class.forName("dev.rdh.argentum.impl.render.terrain.compile.task.ChunkBuilderMeshingTask");
        Object camera = Class.forName("org.joml.Vector3d").getConstructor().newInstance();
        Object task = taskClass.getConstructors()[0].newInstance(render, context, 0, camera, true);
        Method execute = java.util.Arrays.stream(taskClass.getDeclaredMethods())
            .filter(method -> method.getName().equals("execute") && method.getReturnType() != Object.class).findFirst().orElseThrow();
        Class<?> tokenClass = execute.getParameterTypes()[1];
        Object token = java.lang.reflect.Proxy.newProxyInstance(tokenClass.getClassLoader(), new Class<?>[] {tokenClass},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        long open = Class.forName("org.embeddedt.embeddium.impl.render.chunk.occlusion.VisibilityEncoding").getField("EVERYTHING").getLong(null);
        java.util.List<Long> encodings = new java.util.ArrayList<>();
        try {
            for (boolean enabled : new boolean[] {false, true, false}) {
                config.getMethod("setEnabled", boolean.class).invoke(null, enabled);
                refresh.invoke(null);
                Object output = execute.invoke(task, builder, token);
                require(output != null, "The native leaf section build was cancelled");
                try {
                    Object info = output.getClass().getField("info").get(output);
                    long encoding = info.getClass().getField("visibilityData").getLong(info);
                    require(encoding == (enabled ? open : 0), "Hidden leaves still occluded a complete render section");
                    encodings.add(encoding);
                } finally { output.getClass().getMethod("delete").invoke(output); }
            }
        } finally { buildContextClass.getMethod("cleanup").invoke(builder); }
        result.put("argentumLeafSectionOcclusionOffOnOff", encodings);
    }

    private static Object directParticle(Minecraft minecraft, int type) throws Exception {
        for (Field field : minecraft.particleManager.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object factory = ((Map<?, ?>) field.get(minecraft.particleManager)).get(type);
            if (factory == null) continue;
            for (Method method : factory.getClass().getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 9 || parameters[0] != int.class || !parameters[1].isInstance(minecraft.world)) continue;
                method.setAccessible(true);
                Object particle = method.invoke(factory, type, minecraft.world, minecraft.player.x, minecraft.player.y,
                    minecraft.player.z, 0d, 0d, 0d, new int[0]);
                require(particle != null, "The direct particle factory returned no fixture for " + type);
                return particle;
            }
        }
        throw new IllegalStateException("No direct particle factory was found for " + type);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
