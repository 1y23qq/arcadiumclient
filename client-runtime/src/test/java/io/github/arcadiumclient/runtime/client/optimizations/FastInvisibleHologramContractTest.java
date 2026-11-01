// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

final class FastInvisibleHologramContractTest {
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String LIVING = "net/minecraft/entity/living/LivingEntity";
    private static final String RENDER = "net/minecraft/client/render/entity/";

    @Test void originalBaseRenderContainsOnlyTheVirtualNameEntry() throws Exception {
        MethodNode render = method(RENDER + "EntityRenderer", "render", "(L" + ENTITY + ";DDDFF)V");
        List<MethodInsnNode> calls = calls(render);
        assertEquals(1, calls.size());
        assertEquals(Opcodes.INVOKEVIRTUAL, calls.get(0).getOpcode());
        assertEquals(RENDER + "EntityRenderer", calls.get(0).owner);
        assertEquals("renderNameTag", calls.get(0).name);
        assertEquals("(L" + ENTITY + ";DDD)V", calls.get(0).desc);
    }

    @Test void stockArmorStandRendererHasOnlyTheThreeEquipmentLayers() throws Exception {
        ClassNode renderer = read(RENDER + "ArmorStandRenderer");
        assertTrue(renderer.methods.stream().noneMatch(method -> method.name.equals("render")), "Armor stands gained a custom render entry");
        MethodNode constructor = renderer.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
        List<String> allocations = new ArrayList<>();
        for (AbstractInsnNode node : constructor.instructions) if (node instanceof TypeInsnNode type && node.getOpcode() == Opcodes.NEW) allocations.add(type.desc);
        assertEquals(4, allocations.size());
        assertEquals("net/minecraft/client/render/model/entity/ArmorStandModel", allocations.get(0));
        assertTrue(allocations.get(1).startsWith(RENDER + "ArmorStandRenderer$"));
        assertEquals(RENDER + "layer/ItemInHandLayer", allocations.get(2));
        assertEquals(RENDER + "layer/WornSkullLayer", allocations.get(3));
        assertEquals(3, calls(constructor).stream().filter(call -> call.name.equals("addLayer")).count());
    }

    @Test void invisibleBodyAndSpectatorVisibilityRemainSeparateNativeDecisions() throws Exception {
        MethodNode body = method(RENDER + "LivingEntityRenderer", "renderModel", "(L" + LIVING + ";FFFFFF)V");
        List<MethodInsnNode> calls = calls(body);
        int invisible = index(calls, "isInvisible"), viewerInvisible = index(calls, "isInvisibleTo"), draw = index(calls, "render");
        assertTrue(invisible >= 0 && viewerInvisible > invisible && draw > viewerInvisible);
        MethodNode visibility = method(ENTITY, "isInvisibleTo", "(Lnet/minecraft/entity/living/player/PlayerEntity;)Z");
        List<MethodInsnNode> decisions = calls(visibility);
        assertEquals("isSpectator", decisions.get(0).name);
        assertEquals("isInvisible", decisions.get(1).name);
        assertTrue(decisions.get(0).owner.endsWith("PlayerEntity"));
    }

    @Test void armorStandEquipmentGetterIsTheExactFiveSlotBackingArray() throws Exception {
        String stand = "net/minecraft/entity/living/ArmorStandEntity";
        MethodNode getter = method(stand, "getEquipment", "()[Lnet/minecraft/item/ItemStack;");
        assertTrue(calls(getter).isEmpty(), "The equipment getter gained side effects");
        List<Integer> ops = new ArrayList<>();
        for (AbstractInsnNode node : getter.instructions) if (node.getOpcode() >= 0) ops.add(node.getOpcode());
        assertEquals(List.of(Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ARETURN), ops);
        MethodNode constructor = method(stand, "<init>", "(Lnet/minecraft/world/World;)V");
        boolean fiveSlots = false;
        for (AbstractInsnNode node : constructor.instructions) if (node instanceof TypeInsnNode type && type.desc.equals("net/minecraft/item/ItemStack"))
            fiveSlots |= node.getPrevious().getOpcode() == Opcodes.ICONST_5;
        assertTrue(fiveSlots);
    }

    @Test void nonNativeAndNonFiniteDimensionsCannotQualify() {
        assertTrue(FastInvisibleHolograms.nativeSize(.5f, 1.975f));
        assertTrue(FastInvisibleHolograms.nativeSize(.25f, .9875f));
        assertTrue(FastInvisibleHolograms.nativeSize(0, 0));
        assertFalse(FastInvisibleHolograms.nativeSize(Float.NaN, 1.975f));
        assertFalse(FastInvisibleHolograms.nativeSize(.5f, Float.POSITIVE_INFINITY));
        assertFalse(FastInvisibleHolograms.nativeSize(.75f, 1.975f));
        assertFalse(FastInvisibleHolograms.nativeSize(.5f, 4));
        assertFalse(FastInvisibleHolograms.nativeSize(-.5f, 1.975f));
    }

    private static int index(List<MethodInsnNode> calls, String name) { for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i; return -1; }
    private static List<MethodInsnNode> calls(MethodNode method) { List<MethodInsnNode> calls = new ArrayList<>(); for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call) calls.add(call); return calls; }
    private static MethodNode method(String type, String name, String descriptor) throws Exception { return read(type).methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow(); }
    private static ClassNode read(String type) throws Exception {
        try (InputStream input = FastInvisibleHologramContractTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(input, "Native Minecraft class is missing: " + type);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return node;
        }
    }
}
