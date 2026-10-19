package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

final class HeldBlockQa {
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static Map<String, Object> drawn;
    private static Map<String, Object> peerDrawn;
    private static ItemStack peerStack;
    private static net.minecraft.client.entity.living.player.RemoteClientPlayerEntity peer;
    private static Object settings;
    private static Field positions;
    private static boolean started, savedPositions;
    private static int stage, ticks;
    private static int events;
    private static final Map<String, Integer> TYPES = new LinkedHashMap<>();

    private HeldBlockQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!started) {
            started = true;
            settings = Class.forName("org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson").getMethod("items").invoke(null);
            positions = settings.getClass().getField("itemPositionsInThirdPerson");
            savedPositions = positions.getBoolean(settings);
            checks.put("heldBlock", SAMPLES);
            mc.openScreen(null);
            mc.player.inventory.selectedSlot = 2;
            mc.player.inventory.items[2] = new ItemStack(Blocks.STONE, 64);
            peer = new net.minecraft.client.entity.living.player.RemoteClientPlayerEntity(mc.world,
                new com.mojang.authlib.GameProfile(new java.util.UUID(0, 72), "ArcadiumPeer"));
            peerStack = new ItemStack(Blocks.STONE, 32);
            peer.inventory.items[0] = peerStack;
            peer.inventory.selectedSlot = 0;
            peer.setNetworkId(-72);
            mc.world.addEntity(peer);
            mc.player.setPositionAndAngles(8.5, 4, 8.5, 0, 0);
            select(mc);
        }
        peer.setPositionAndAngles(7.1, 4, 8.5, 0, 0);
        if (++ticks < 30) return false;
        if (drawn == null) {
            checks.put("heldBlockProbe", Map.of("events", events, "types", TYPES, "view", mc.options.perspective,
                "selected", mc.player.inventory.selectedSlot, "stack", String.valueOf(mc.player.inventory.items[mc.player.inventory.selectedSlot])));
            if (ticks == 30) net.minecraft.client.Screenshot.take(mc.gameDir, "held-block-missing.png", mc.width, mc.height, mc.getRenderTarget());
            if (ticks < 60) return false;
            throw new IllegalStateException("The third-person stone model did not reach the item renderer");
        }
        Map<String, Object> sample = new LinkedHashMap<>(drawn);
        sample.put("view", mc.options.perspective);
        sample.put("legacyPositions", positions.getBoolean(settings));
        sample.put("stage", stage);
        if (peerDrawn == null) throw new IllegalStateException("The remote player's held block did not render");
        sample.put("remotePlayer", peerDrawn);
        SAMPLES.add(sample);
        net.minecraft.client.Screenshot.take(mc.gameDir, "held-block-" + stage + ".png", mc.width, mc.height, mc.getRenderTarget());
        if (++stage < 4) { select(mc); return false; }
        positions.setBoolean(settings, savedPositions);
        mc.options.perspective = 0;
        return true;
    }

    private static void select(Minecraft mc) throws Exception {
        ticks = 0;
        drawn = null;
        peerDrawn = null;
        positions.setBoolean(settings, stage >= 2);
        mc.options.perspective = stage % 2 == 0 ? 1 : 2;
    }

    static void record(ItemStack stack, BakedModel model, ModelTransformations.Type perspective) {
        if (started) { events++; TYPES.merge(perspective + ":third=" + (perspective == ModelTransformations.Type.THIRD_PERSON)
            + ":stone=" + (stack != null && stack.getItem() == net.minecraft.item.Item.byBlock(Blocks.STONE))
            + ":peer=" + (stack == peerStack), 1, Integer::sum); }
        if (!started || perspective != ModelTransformations.Type.THIRD_PERSON || stack == null
                || stack.getItem() != net.minecraft.item.Item.byBlock(Blocks.STONE)) return;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        List<Float> scale = new ArrayList<>(3);
        for (int column = 0; column < 3; column++) {
            int base = column * 4;
            float x = MATRIX.get(base), y = MATRIX.get(base + 1), z = MATRIX.get(base + 2);
            scale.add((float) Math.sqrt(x * x + y * y + z * z));
        }
        var third = model.getTransformations().thirdPerson;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("model", model.getClass().getName());
        value.put("matrixScale", scale);
        value.put("cameraScale", List.of(third.scale.x, third.scale.y, third.scale.z));
        try {
            Object capture = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture").getMethod("current").invoke(null);
            if (capture != null) {
                Field field = capture.getClass().getDeclaredField("matrices");
                field.setAccessible(true);
                Object matrix = field.get(capture);
                List<Float> captured = new ArrayList<>();
                for (int column = 0; column < 3; column++) {
                    float squared = 0;
                    for (int row = 0; row < 3; row++) {
                        float element = ((Number) matrix.getClass().getMethod("m" + column + row).invoke(matrix)).floatValue();
                        squared += element * element;
                    }
                    captured.add((float) Math.sqrt(squared));
                }
                value.put("capturedScale", captured);
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        if (stack == peerStack) peerDrawn = value;
        else drawn = value;
    }
}
