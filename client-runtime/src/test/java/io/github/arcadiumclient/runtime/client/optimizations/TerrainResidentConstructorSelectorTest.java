// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import static org.junit.jupiter.api.Assertions.*;

class TerrainResidentConstructorSelectorTest {
    @Test void actualMixinSelectorCoversArgentumConstructorAndTheDelegatingOverload() throws Exception {
        String nativeName = "org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager";
        String celeritasJar = System.getProperty("arcadium.voxelSkin072.celeritasJar");
        assertNotNull(celeritasJar, "Use the pinned Celeritas runtime ABI input");
        ClassNode nativeClass;
        try (var zip = new ZipFile(Path.of(celeritasJar).toFile())) {
            var entry = zip.getEntry(nativeName + ".class");
            assertNotNull(entry);
            try (InputStream input = zip.getInputStream(entry)) { nativeClass = read(input); }
        }
        String argentumJar = System.getProperty("arcadium.voxelSkin072.argentumJar");
        assertNotNull(argentumJar, "Use the same pinned Argentum ABI input as the normal renderer tests");
        ClassNode argentum;
        try (var zip = new ZipFile(Path.of(argentumJar).toFile())) {
            var entry = zip.getEntry("dev/rdh/argentum/impl/render/terrain/ArgentumRenderSectionManager.class");
            assertNotNull(entry);
            try (InputStream input = zip.getInputStream(entry)) { argentum = read(input); }
        }
        List<String> invoked = new ArrayList<>();
        for (MethodNode method : argentum.methods) if (method.name.equals("<init>")) {
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(nativeName)
                    && call.name.equals("<init>")) invoked.add(call.desc);
        }
        assertEquals(1, invoked.size(), "The actual pinned Argentum constructor entry changed");
        String actualEntry = invoked.getFirst();
        assertTrue(actualEntry.endsWith("Z)V"), "The actual boolean overload must be covered");

        List<String> oldSelection = selected(nativeClass, "<init>");
        assertEquals(1, oldSelection.size());
        assertFalse(oldSelection.contains(actualEntry), "Preserve the failed original selector as a regression witness");

        ClassNode mixin = resource("io/github/arcadiumclient/runtime/mixin/compat/argentum/MixinTerrainInventoryManager");
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("inventoryAttach")).findFirst().orElseThrow();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        AnnotationNode inject = annotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        List<String> selectors = null;
        for (int i = 0; i < inject.values.size(); i += 2) if (inject.values.get(i).equals("method")) {
            @SuppressWarnings("unchecked") List<String> values = (List<String>) inject.values.get(i + 1);
            selectors = values;
        }
        assertEquals(List.of("<init>*"), selectors);
        List<String> current = selected(nativeClass, selectors.getFirst());
        assertEquals(2, current.size());
        assertTrue(current.contains(actualEntry));
        assertTrue(current.contains(oldSelection.getFirst()));
        MethodNode delegating = nativeClass.methods.stream().filter(m -> m.name.equals("<init>")
                && m.desc.equals(oldSelection.getFirst())).findFirst().orElseThrow();
        boolean delegatesToActualEntry = false;
        for (var instruction : delegating.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(nativeName) && call.name.equals("<init>") && call.desc.equals(actualEntry)) {
            delegatesToActualEntry = true;
        }
        assertTrue(delegatesToActualEntry, "Both real constructor paths must retain attach coverage");
    }

    private static List<String> selected(ClassNode owner, String selectorText) {
        var selector = MemberInfo.parse(selectorText, null).configure(ITargetSelector.Configure.SELECT_MEMBER);
        List<String> methods = new ArrayList<>();
        for (MethodNode method : owner.methods) if (selector.match(ElementNode.of(owner, method)).isExactMatch()
                && methods.size() < selector.getMaxMatchCount()) methods.add(method.desc);
        return methods;
    }

    private static ClassNode resource(String name) throws Exception {
        try (InputStream input = TerrainResidentConstructorSelectorTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            return read(input);
        }
    }

    private static ClassNode read(InputStream input) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, 0);
        return node;
    }
}
