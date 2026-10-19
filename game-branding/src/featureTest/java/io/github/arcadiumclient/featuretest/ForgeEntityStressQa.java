package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

final class ForgeEntityStressQa {
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static Object scaffold;
    private static boolean begun, active, savedEnabled, done;
    private static int stage, ticks, frames;
    private static long started;

    private ForgeEntityStressQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        if (!Boolean.getBoolean("arcadium.forgeQa.renderStress") || !Boolean.getBoolean("arcadium.forge.prepared")) return true;
        if (!begun) {
            begun = true;
            scaffold = ((Collection<?>) Class.forName("keystrokesmod.module.ModuleManager").getField("modules").get(null)).stream()
                .filter(module -> module.getClass().getName().equals("keystrokesmod.module.impl.player.Scaffold")).findFirst().orElseThrow();
            savedEnabled = Boolean.TRUE.equals(invoke(scaffold, "isEnabled"));
            if (!savedEnabled) invoke(scaffold, "enable");
            checks.put("thirdPersonStress", SAMPLES);
            next(mc);
        }
        ticks++;
        mc.options.perspective = stage == 0 || stage == 5 ? 0 : 1;
        mc.player.setPositionAndAngles(8.5 + Math.sin(ticks * .08) * 2, 4, 8.5 + Math.cos(ticks * .08) * 2, ticks * 3f, 20);
        mc.player.lastYaw = mc.player.yaw;
        mc.player.lastPitch = mc.player.pitch;
        if (ticks < 100) return false;
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("view", mc.options.perspective);
        sample.put("elapsed", (System.nanoTime() - started) / 1e9);
        sample.put("frames", frames);
        sample.put("fps", frames * 1e9 / (System.nanoTime() - started));
        sample.put("modelViewDepth", GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
        Object instancing = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing").getMethod("current").invoke(null);
        Object backend = field(instancing, "backend");
        Object cache = field(backend, "itemGeometry");
        int cached = 0;
        for (String name : List.of("itemGeometries", "fixedGeometries", "blockGeometries", "layeredItems")) {
            Map<?, ?> models = (Map<?, ?>) field(cache, name);
            sample.put(name, models.size());
            cached += models.size();
        }
        Object batcher = field(backend, "batcher");
        int entityModels = ((Map<?, ?>) field(batcher, "models")).size();
        sample.put("entityModels", entityModels);
        if (stage >= 1 && stage <= 4 && (entityModels == 0 || cached == 0)) {
            throw new IllegalStateException("The rear third-person scene did not render the real player and held block: " + sample);
        }
        String capture = "forge-entity-stress-" + stage + ".png";
        net.minecraft.client.Screenshot.take(mc.gameDir, capture, mc.width, mc.height, mc.getRenderTarget());
        sample.put("screenshot", capture);
        SAMPLES.add(sample);
        if (cached >= 20000 && stage < 5) {
            stage = 5;
            next(mc);
            return false;
        }
        if (++stage < 6) {
            next(mc);
            return false;
        }
        if (!savedEnabled) invoke(scaffold, "disable");
        mc.options.perspective = 0;
        active = false;
        done = true;
        return true;
    }

    static void afterFrame() { if (active) frames++; }

    private static void next(Minecraft mc) {
        mc.options.perspective = stage == 0 || stage == 5 ? 0 : 1;
        started = System.nanoTime();
        frames = ticks = 0;
        active = true;
    }

    private static Object invoke(Object object, String method) throws Exception { return object.getClass().getMethod(method).invoke(object); }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
