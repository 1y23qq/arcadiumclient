package io.github.arcadiumclient.forgecompat.mapping;

import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForgeMappingsTest {
    private static final String TINY = """
        tiny\t2\t0\tofficial\tintermediary\tnamed
        c\ta\tnet/minecraft/class_1\tnet/minecraft/Parent
        \tf\tI\tx\tfield_101\tvalue
        \tf\t[La;\ty\tfield_102\tchildren
        \tm\t(La;)La;\ta\tmethod_101\tupdate
        \tm\t(I)V\ta\tmethod_102\tset
        c\tb\tnet/minecraft/class_2\tnet/minecraft/Child
        c\tc\tnet/minecraft/class_3\tnet/minecraft/Other
        \tf\tI\tx\tfield_103\tshared
        \tm\t(I)V\ta\tmethod_103\tshared
        c\td\tnet/minecraft/class_4\tnet/minecraft/Another
        \tf\tI\tx\tfield_104\tshared
        \tm\t(I)V\tb\tmethod_104\tshared
        """;
    private static final String SRG = """
        PK: . net/minecraft/src
        CL: a net/minecraft/test/Parent
        CL: b net/minecraft/test/Child
        CL: c net/minecraft/test/Other
        CL: d net/minecraft/test/Another
        FD: a/x net/minecraft/test/Parent/field_1
        FD: a/y net/minecraft/test/Parent/field_2
        FD: c/x net/minecraft/test/Other/field_3
        FD: d/x net/minecraft/test/Another/field_4
        MD: a/a (La;)La; net/minecraft/test/Parent/func_1 (Lnet/minecraft/test/Parent;)Lnet/minecraft/test/Parent;
        MD: a/a (I)V net/minecraft/test/Parent/func_2 (I)V
        MD: c/a (I)V net/minecraft/test/Other/func_shared (I)V
        MD: d/b (I)V net/minecraft/test/Another/func_shared (I)V
        """;

    static MappingResolver resolver(String namespace, String tiny) throws Exception {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(new StringReader(tiny), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl")
            .getDeclaredConstructor(MappingTree.class, String.class);
        constructor.setAccessible(true);
        return (MappingResolver) constructor.newInstance(tree, namespace);
    }

    private ForgeMappings mappings() throws Exception {
        return new ForgeMappings(resolver("intermediary", TINY), new StringReader(SRG));
    }

    @Test void mapsOfficialAndSrgClassesThroughRealFabricResolver() throws Exception {
        var mappings = mappings();
        assertEquals("net/minecraft/class_1", mappings.officialToRuntime().map("a"));
        assertEquals("net/minecraft/class_1", mappings.classFromSrg("net/minecraft/test/Parent"));
        assertEquals("net/minecraft/test/Parent", mappings.classToSrg("net/minecraft/class_1"));
        assertEquals("java/lang/String", mappings.classFromSrg("java/lang/String"));
    }

    @Test void handlesDescriptorsArraysSignaturesAndNamedDevelopmentRuntime() throws Exception {
        var mappings = mappings();
        assertEquals("([[Lnet/minecraft/class_1;I)[Lnet/minecraft/class_2;",
            mappings.descriptorFromSrg("([[Lnet/minecraft/test/Parent;I)[Lnet/minecraft/test/Child;"));
        assertEquals("Ljava/util/List<Lnet/minecraft/class_1;>;",
            mappings.srgToRuntime().mapSignature("Ljava/util/List<Lnet/minecraft/test/Parent;>;", true));
        var named = new ForgeMappings(resolver("named", TINY), new StringReader(SRG));
        assertEquals("net/minecraft/Parent", named.classFromSrg("net/minecraft/test/Parent"));
        assertEquals("update", named.methodFromSrg("net/minecraft/test/Parent", "func_1", "(Lnet/minecraft/test/Parent;)Lnet/minecraft/test/Parent;"));
    }

    @Test void mapsMethodsByOwnerAndDescriptorRatherThanNameOnly() throws Exception {
        var mappings = mappings();
        assertEquals("method_101", mappings.officialToRuntime().mapMethodName("a", "a", "(La;)La;"));
        assertEquals("method_102", mappings.officialToRuntime().mapMethodName("a", "a", "(I)V"));
        assertEquals("method_103", mappings.methodFromSrg("net/minecraft/test/Other", "func_shared", "(I)V"));
        assertEquals("<init>", mappings.methodFromSrg("net/minecraft/test/Parent", "<init>", "()V"));
        assertEquals("forgeExtension", mappings.methodFromSrg("net/minecraft/test/Parent", "forgeExtension", "()V"));
    }

    @Test void propagatesRuntimeOverrideNamesAndRebuildsReverseIndexesAfterHierarchyChanges() throws Exception {
        String overrides = SRG + "MD: b/a (La;)La; net/minecraft/test/Child/func_1 (Lnet/minecraft/test/Parent;)Lnet/minecraft/test/Parent;\n";
        ForgeMappings mappings = new ForgeMappings(resolver("intermediary", TINY), new StringReader(overrides));
        String descriptor = "(Lnet/minecraft/test/Parent;)Lnet/minecraft/test/Parent;";
        assertEquals("a", mappings.methodFromSrg("net/minecraft/test/Child", "func_1", descriptor));
        mappings.indexOfficialHierarchy("b", "a", List.of());
        assertEquals("method_101", mappings.methodFromSrg("net/minecraft/test/Child", "func_1", descriptor));
        assertEquals("method_101", mappings.officialToRuntime().mapMethodName("b", "a", "(La;)La;"));
        assertEquals("func_1", mappings.runtimeToSrg().mapMethodName("net/minecraft/class_2", "method_101",
            "(Lnet/minecraft/class_1;)Lnet/minecraft/class_1;"));
        mappings.indexOfficialMethod("b", "func_1", "(La;)La;");
        assertEquals("func_1", mappings.methodFromSrg("net/minecraft/test/Child", "func_1", descriptor));
    }

    @Test void preservesActualPatchedSrgFieldDeclarationsEvenWhenAnonymousOwnersWereReordered() throws Exception {
        ForgeMappings mappings = mappings();
        mappings.indexOfficialHierarchy("b", "a", List.of());
        assertEquals("field_101", mappings.fieldFromSrg("net/minecraft/test/Child", "field_1", "I"));
        String previous = mappings.identity();
        mappings.indexOfficialField("b", "field_1", "I");
        assertEquals("field_1", mappings.fieldFromSrg("net/minecraft/test/Child", "field_1", "I"));
        assertEquals("field_1", mappings.runtimeToSrg().mapFieldName("net/minecraft/class_2", "field_1", "I"));
        assertEquals("field_101", mappings.fieldFromSrg("net/minecraft/test/Parent", "field_1", "I"));
        assertNotEquals(previous, mappings.identity());
    }

    @Test void mapsFieldsUsingActualDescriptorAndReversesThem() throws Exception {
        var mappings = mappings();
        assertEquals("field_101", mappings.fieldFromSrg("net/minecraft/test/Parent", "field_1", "I"));
        assertEquals("field_102", mappings.fieldFromSrg("net/minecraft/test/Parent", "field_2", "[Lnet/minecraft/test/Parent;"));
        assertEquals("field_1", mappings.runtimeToSrg().mapFieldName("net/minecraft/class_1", "field_101", "I"));
        assertEquals("func_1", mappings.runtimeToSrg().mapMethodName("net/minecraft/class_1", "method_101", "(Lnet/minecraft/class_1;)Lnet/minecraft/class_1;"));
    }

    @Test void usesUnambiguousGlobalSrgFallbackForInheritedReferences() throws Exception {
        var mappings = mappings();
        assertEquals("method_101", mappings.methodFromSrg("net/minecraft/test/Child", "func_1", "(Lnet/minecraft/test/Parent;)Lnet/minecraft/test/Parent;"));
        assertEquals("field_101", mappings.fieldFromSrg("net/minecraft/test/Child", "field_1", "I"));
        assertEquals("field_1", mappings.runtimeToSrg().mapFieldName("net/minecraft/class_2", "field_101", "I"));
        assertThrows(IllegalArgumentException.class, () -> mappings.methodFromSrg("unknown/Child", "func_shared", "(I)V"));
    }

    @Test void hierarchyDisambiguatesReferencesAndSupportsForgeApiOverrides() throws Exception {
        var mappings = mappings();
        mappings.indexSrgHierarchy("unknown/Child", "net/minecraft/test/Other", List.of());
        assertEquals("method_103", mappings.methodFromSrg("unknown/Child", "func_shared", "(I)V"));
        mappings.indexOfficialHierarchy("b", "a", List.of());
        mappings.indexOfficialHierarchy("net/minecraftforge/Extension", "b", List.of());
        assertEquals("method_101", mappings.officialToRuntime().mapMethodName("net/minecraftforge/Extension", "a", "(La;)La;"));
        assertEquals("field_101", mappings.officialToRuntime().mapFieldName("net/minecraftforge/Extension", "x", "I"));
    }

    @Test void resolvesNearestAncestorAndInvalidatesPreviouslyCachedFallback() throws Exception {
        var mappings = mappings();
        assertEquals("a", mappings.officialToRuntime().mapMethodName("extension/Class", "a", "(La;)La;"));
        mappings.indexOfficialHierarchy("extension/Class", "a", List.of());
        assertEquals("method_101", mappings.officialToRuntime().mapMethodName("extension/Class", "a", "(La;)La;"));
        mappings.indexSrgHierarchy("cycle/A", "cycle/B", List.of());
        mappings.indexSrgHierarchy("cycle/B", "cycle/A", List.of());
        assertEquals("custom", mappings.methodFromSrg("cycle/A", "custom", "()V"));
    }

    @Test void mapsReflectionClassesAndFullyQualifiedMixinSelectors() throws Exception {
        var mappings = mappings();
        assertEquals("net.minecraft.class_1", mappings.reflectionClassFromSrg("net.minecraft.test.Parent"));
        assertEquals("[Lnet.minecraft.class_1;", mappings.reflectionClassFromSrg("[Lnet.minecraft.test.Parent;"));
        assertEquals("Lnet/minecraft/class_1;field_101:I", mappings.mixinSelectorFromSrg("Lnet/minecraft/test/Parent;field_1:I"));
        assertEquals("Lnet/minecraft/class_1;method_102(I)V", mappings.mixinSelectorFromSrg("Lnet/minecraft/test/Parent;func_2(I)V"));
        assertEquals("method_102(I)V", mappings.mixinSelectorFromSrg("func_2(I)V"));
        assertEquals("method_102", mappings.mixinSelectorFromSrg("func_2"));
        assertEquals("method_103", mappings.mixinSelectorFromSrg("net/minecraft/test/Other", "func_shared"));
        assertThrows(IllegalArgumentException.class, () -> mappings.mixinSelectorFromSrg("func_shared"));
        assertEquals("Load(I)V", mappings.mixinSelectorFromSrg("Load(I)V"));
        assertEquals("LEFT:I", mappings.mixinSelectorFromSrg("LEFT:I"));
        assertThrows(IllegalArgumentException.class, () -> mappings.mixinSelectorFromSrg("Lbroken/Owner"));
    }

    @Test void transformsRealClassStructureWithoutLoadingGuestClasses() throws Exception {
        var mappings = mappings();
        ClassWriter source = new ClassWriter(0);
        source.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/Mod", "Ljava/lang/Object;", "java/lang/Object", null);
        source.visitField(Opcodes.ACC_PUBLIC, "world", "Lnet/minecraft/test/Parent;", null, null).visitEnd();
        var method = source.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "(Lnet/minecraft/test/Child;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/test/Child", "func_2", "(I)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        source.visitEnd();
        ClassWriter output = new ClassWriter(0);
        new ClassReader(source.toByteArray()).accept(new ClassRemapper(output, mappings.srgToRuntime()), 0);
        ClassNode parsed = new ClassNode();
        new ClassReader(output.toByteArray()).accept(parsed, 0);
        parsed.check(Opcodes.ASM9);
        assertEquals("Lnet/minecraft/class_1;", parsed.fields.get(0).desc);
        assertEquals("(Lnet/minecraft/class_2;)V", parsed.methods.get(0).desc);
        var invocation = (org.objectweb.asm.tree.MethodInsnNode) parsed.methods.get(0).instructions.get(2);
        assertEquals("method_102", invocation.name);
    }

    @Test void rejectsMalformedOrConflictingMappingData() throws Exception {
        var resolver = resolver("intermediary", TINY);
        assertThrows(IllegalArgumentException.class, () -> new ForgeMappings(resolver, new StringReader("MD: incomplete")));
        assertThrows(IllegalArgumentException.class, () -> new ForgeMappings(resolver, new StringReader("CL: a one\nCL: a two\n")));
        assertThrows(IllegalArgumentException.class, () -> new ForgeMappings(resolver, new StringReader("")));
    }
}
