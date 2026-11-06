// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import javax.tools.ToolProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class CeraLivingOperationFusionProbe implements Opcodes {
    private static final String EXPECTED = "0479ad54828699bace96e6d33c48dd99cad953b7f07235da9e9d640dc2bd8e1a";
    private static final String OWNER = "net/minecraft/unmapped/C_71252617", ENTITY = "net/minecraft/unmapped/C_74425583";
    private static final String MODEL = "net/minecraft/unmapped/C_36076665", BASE_ENTITY = "net/minecraft/unmapped/C_42232651";
    private static final String STATE = Type.getInternalName(Events.class);
    private static final String DRIVER = "(L" + OWNER + ";L" + ENTITY + ";FFFFFFF)V";
    private static final String RENDER = "(L" + ENTITY + ";FFFFFF)V";
    private CeraLivingOperationFusionProbe() { }

    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Path.of(args[0])); require(sha(original).equals(EXPECTED), "Unexpected transformed Living fixture");
        Path output = Path.of(args[1]); Files.createDirectories(output);
        ClassNode actual = read(original); Map<String, String> fingerprints = CeraLivingOperationFusion.audit(actual);
        Map<String, byte[]> untouched = handlerBytes(actual); List<CeraLivingOperationFusion.Site> sites = CeraLivingOperationFusion.apply(actual);
        require(sites.size() == 2 && sites.stream().filter(CeraLivingOperationFusion.Site::captured).count() == 1, "Expected one direct and one captured Cera site: " + fingerprints);
        for (Map.Entry<String, byte[]> method : untouched.entrySet()) require(Arrays.equals(method.getValue(), handlerBytes(actual).get(method.getKey())), "Native handler changed: " + method.getKey());
        int verified = 0;
        for (MethodNode method : actual.methods) if ((method.access & ACC_ABSTRACT) == 0) { new Analyzer<>(new BasicVerifier()).analyze(actual.name, method); verified++; }
        List<MethodNode> typed = actual.methods.stream().filter(method -> method.name.startsWith("arcadium$typedCera")).toList();
        require(typed.size() == 3, "Expected exactly three typed clones");
        require(typed.stream().mapToInt(method -> method.tryCatchBlocks.size()).sum() == 4, "Native Cera finally regions changed");
        for (MethodNode method : typed) for (AbstractInsnNode instruction : method.instructions) {
            require(!(instruction instanceof MethodInsnNode call && (call.owner.endsWith("/Operation") || call.owner.equals("java/lang/Float") && call.name.equals("valueOf"))), "Typed method retained packing: " + method.name);
            require(!(instruction instanceof TypeInsnNode type && type.getOpcode() == ANEWARRAY && type.desc.equals("java/lang/Object")), "Typed method retained Object[] creation");
        }
        require(CeraLivingOperationFusion.apply(actual).isEmpty(), "Repeated fusion was not idempotent");
        List<String> negative = negatives(original);
        ClassNode fixture = fixture(read(original)); Map<String, byte[]> stubs = compileStubs(output);
        byte[] nativeFixture = bytes(fixture, true); require(CeraLivingOperationFusion.apply(fixture).size() == 2, "Exact handlers did not match in JVM fixture");
        byte[] fusedFixture = bytes(fixture, false);
        Fixture nativeRun = load(nativeFixture, stubs), fusedRun = load(fusedFixture, stubs);
        int scenarios = 0, exceptionCases = 0, cleanupFailureCases = 0;
        float[][] values = {{1.25f, -2.5f, 3f, 4f, 5f, 0.0625f, 0.75f},
                {-0f, 0f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0f},
                {Float.intBitsToFloat(0x7fc00042), Float.intBitsToFloat(0xffc00017), -1f, 1f, -0.0625f, 0.0625f, 1f}};
        for (int bits = 0; bits < 128; bits++) for (boolean nested : new boolean[]{false, true}) for (boolean override : new boolean[]{false, true}) for (float[] floats : values) {
            Scenario scenario = new Scenario(bits, nested, override, floats);
            Outcome expected = run(nativeRun, scenario, false, 0), disabled = run(fusedRun, scenario, false, 0), enabled = run(fusedRun, scenario, true, 0);
            same(expected, disabled, "disabled " + scenario); same(expected, enabled, "enabled " + scenario); scenarios++;
            if (floats == values[0]) for (int fail = 1; fail <= expected.events.size(); fail++) {
                Outcome nativeFailure = run(nativeRun, scenario, false, fail);
                same(nativeFailure, run(fusedRun, scenario, false, fail), "disabled failure " + fail + " " + scenario);
                same(nativeFailure, run(fusedRun, scenario, true, fail), "enabled failure " + fail + " " + scenario); exceptionCases++;
                for (int cleanup = fail + 1; cleanup <= nativeFailure.events.size(); cleanup++) {
                    Outcome cleanupFailure = run(nativeRun, scenario, false, fail, cleanup);
                    same(cleanupFailure, run(fusedRun, scenario, false, fail, cleanup), "disabled cleanup failure " + scenario);
                    same(cleanupFailure, run(fusedRun, scenario, true, fail, cleanup), "enabled cleanup failure " + scenario); cleanupFailureCases++;
                }
            }
        }
        require(stubs.keySet().stream().noneMatch(name -> name.contains("org.lwjgl")), "JVM proof loaded GL stubs");
        Map<String, Object> report = new LinkedHashMap<>(); report.put("passed", true); report.put("wired", false); report.put("inputSha256", sha(original));
        report.put("sites", sites); report.put("verifiedActualMethods", verified); report.put("typedMethods", typed.stream().map(method -> method.name + method.desc).toList());
        report.put("preservedNativeHandlers", untouched.keySet()); report.put("preservedFinallyRegions", 4); report.put("jvmScenarios", scenarios); report.put("callbackFailureScenarios", exceptionCases); report.put("nestedCleanupFailureScenarios", cleanupFailureCases);
        report.put("checks", List.of("enabled and disabled gates", "inactive, batched, outline, normal, emissive and flat-tint branches", "captured this differs from supplied virtual receiver", "virtual override dispatch", "raw float bits including NaN payloads and signed zero", "LocalFloatRef construction/init/get/dispose order", "identical thrown callback object and finally order", "no GL context or game process"));
        report.put("unknownPatternsRejectedWithoutMutation", negative); report.put("fingerprints", fingerprints); report.put("idempotent", true);
        Files.writeString(output.resolve("verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.write(output.resolve("LivingEntityRenderer-cera-unwired.class"), bytes(actual, false));
        Files.write(output.resolve("jvm-native.class"), nativeFixture); Files.write(output.resolve("jvm-fused.class"), fusedFixture);
        System.out.println("Cera contract PASS: " + sites.size() + " actual sites, " + verified + " ASM methods, " + scenarios + " JVM scenarios, " + exceptionCases + " callback failures, " + cleanupFailureCases + " nested cleanup failures, " + negative.size() + " native fallbacks.");
    }

    private static List<String> negatives(byte[] original) {
        List<String> passed = new ArrayList<>();
        reject(original, "unknown Cera mixin", node -> annotation(cera(node)).values.set(annotation(cera(node)).values.indexOf("mixin") + 1, "unknown.Cera"), passed);
        reject(original, "different Cera priority", node -> annotation(cera(node)).values.set(annotation(cera(node)).values.indexOf("priority") + 1, 1500), passed);
        reject(original, "unknown Cera branch body", node -> cera(node).instructions.insert(new InsnNode(NOP)), passed);
        reject(original, "changed Cera finally catch type", node -> cera(node).tryCatchBlocks.getFirst().type = "java/lang/RuntimeException", passed);
        reject(original, "changed Cera cleanup range", node -> cera(node).tryCatchBlocks.getFirst().end = cera(node).tryCatchBlocks.getFirst().handler, passed);
        reject(original, "changed Cera callback order", node -> { MethodNode method = cera(node); for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("hasEmissive")) call.name = "active"; }, passed);
        reject(original, "changed tint callback", node -> { for (AbstractInsnNode instruction : tint(node).instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("beginFlatPass")) call.name = "unknownTint"; }, passed);
        reject(original, "changed sugar access", node -> { for (AbstractInsnNode instruction : sugar(node).instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("get")) call.name = "unknownGet"; }, passed);
        reject(original, "changed terminal virtual target", node -> { for (MethodNode method : node.methods) if (method.name.matches("mixinextras\\$bridge\\$m_94468904\\$7[01]")) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKEVIRTUAL && call.owner.equals(node.name)) call.name = "unknownModel"; }, passed);
        reject(original, "changed captured bridge receiver", node -> { MethodNode bridge = node.methods.stream().filter(method -> method.name.startsWith("mixinextras$bridge$wrapOperation$") && method.name.contains("cera$emissivePass")).findFirst().orElseThrow(); for (AbstractInsnNode instruction : bridge.instructions) if (instruction instanceof VarInsnNode variable && variable.getOpcode() == ALOAD && variable.var == 0) { variable.var = 1; break; } }, passed);
        reject(original, "foreign captured-this operand", node -> { MethodNode caller = caller(node); List<AbstractInsnNode> code = code(caller); int at = capturedIndex(caller); ((VarInsnNode) code.get(at - 2)).var = 20; }, passed);
        reject(original, "unknown lambda bootstrap", node -> { InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) code(caller(node)).get(capturedIndex(caller(node))); dynamic.bsm = new Handle(H_INVOKESTATIC, "unknown/Bootstrap", dynamic.bsm.getName(), dynamic.bsm.getDesc(), false); }, passed);
        reject(original, "interior metadata boundary", node -> { MethodNode caller = caller(node); AbstractInsnNode at = code(caller).get(capturedIndex(caller) + 3); LabelNode boundary = new LabelNode(); caller.instructions.insertBefore(at, boundary); caller.instructions.insert(boundary, new LineNumberNode(777, boundary)); }, passed);
        reject(original, "foreign jump into captured operands", node -> { MethodNode caller = caller(node); AbstractInsnNode at = code(caller).get(capturedIndex(caller) - 1); LabelNode boundary = new LabelNode(); caller.instructions.insertBefore(at, boundary); caller.instructions.insertBefore(caller.instructions.getLast(), new JumpInsnNode(GOTO, boundary)); }, passed);
        reject(original, "extra captured operation consumer", node -> { MethodNode caller = caller(node); List<AbstractInsnNode> code = code(caller); int at = capturedIndex(caller); int operation = ((VarInsnNode) code.get(at - 1)).var; caller.instructions.insertBefore(caller.instructions.getLast(), new VarInsnNode(ALOAD, operation)); caller.instructions.insertBefore(caller.instructions.getLast(), new InsnNode(POP)); }, passed);
        reject(original, "duplicate matching call chain", node -> { MethodNode caller = caller(node); MethodNode copy = new MethodNode(ASM9, caller.access, "unknownExtraCeraPass", caller.desc, null, null); caller.accept(copy); node.methods.add(copy); }, passed);
        return passed;
    }

    private static void reject(byte[] original, String name, Consumer<ClassNode> mutate, List<String> passed) {
        ClassNode node = read(original); mutate.accept(node); byte[] before = bytes(node, false);
        require(CeraLivingOperationFusion.apply(node).isEmpty(), "Unsupported Cera shape transformed: " + name);
        require(Arrays.equals(before, bytes(node, false)), "Unsupported Cera shape partially mutated: " + name); passed.add(name);
    }

    private static ClassNode fixture(ClassNode input) {
        ClassNode fixture = new ClassNode(ASM9); fixture.version = V25; fixture.access = ACC_PUBLIC; fixture.name = input.name; fixture.superName = "java/lang/Object";
        fixture.fields.add(new FieldNode(ACC_PUBLIC, "f_44076704", "Z", null, null)); fixture.fields.add(new FieldNode(ACC_PUBLIC, "f_16171538", "L" + MODEL + ";", null, null));
        for (MethodNode method : input.methods) if (method == cera(input) || method == tint(input) || method == sugar(input)
                || method.name.matches("mixinextras\\$bridge\\$m_94468904\\$7[01]") || method.name.startsWith("mixinextras$bridge$wrapOperation$") && method.name.contains("cera$emissivePass")) fixture.methods.add(method);
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0)); constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new VarInsnNode(ALOAD, 0)); constructor.instructions.add(new TypeInsnNode(NEW, MODEL)); constructor.instructions.add(new InsnNode(DUP)); constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, MODEL, "<init>", "()V", false)); constructor.instructions.add(new FieldInsnNode(PUTFIELD, fixture.name, "f_16171538", "L" + MODEL + ";")); constructor.instructions.add(new InsnNode(RETURN)); fixture.methods.add(constructor);
        fixture.methods.add(renderMethod(OWNER, false));
        MethodNode source = caller(input); List<AbstractInsnNode> code = code(source);
        for (boolean nested : new boolean[]{false, true}) {
            MethodNode driver = new MethodNode(ACC_PUBLIC, nested ? "probeNested" : "probeDirect", DRIVER, null, null);
            for (int arg = 0; arg < 8; arg++) { driver.instructions.add(new VarInsnNode(arg < 2 ? ALOAD : FLOAD, arg + 1)); driver.instructions.add(new VarInsnNode(arg < 2 ? ASTORE : FSTORE, arg + 20)); }
            int start = -1, end = -1;
            if (!nested) {
                for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof MethodInsnNode call && call.name.equals(cera(input).name)) { start = i - 10; end = i; break; }
            } else {
                int captured = capturedIndex(source);
                for (int i = captured - 1; i >= 0; i--) if (code.get(i) instanceof InvokeDynamicInsnNode dynamic && dynamic.desc.startsWith("()")) { start = i - 9; break; }
                for (int i = captured + 1; i < code.size(); i++) if (code.get(i) instanceof MethodInsnNode call && call.name.equals(sugar(input).name)) { end = i + 3; break; }
            }
            require(start >= 0 && end >= start, "Missing actual driver bytecode");
            for (int i = start; i <= end; i++) driver.instructions.add(code.get(i).clone(new HashMap<>()));
            driver.instructions.add(new InsnNode(RETURN)); driver.maxLocals = 30; driver.maxStack = 16; fixture.methods.add(driver);
        }
        return read(bytes(fixture, true));
    }

    private static MethodNode renderMethod(String owner, boolean override) {
        MethodNode method = new MethodNode(ACC_PUBLIC, "m_94468904", RENDER, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0)); method.instructions.add(new VarInsnNode(ALOAD, 1));
        for (int arg = 2; arg < 8; arg++) method.instructions.add(new VarInsnNode(FLOAD, arg));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, STATE, override ? "overrideRender" : "render", "(Ljava/lang/Object;Ljava/lang/Object;FFFFFF)V", false)); method.instructions.add(new InsnNode(RETURN)); return method;
    }

    private static Map<String, byte[]> compileStubs(Path output) throws Exception {
        String events = Events.class.getCanonicalName(), prefix = "import " + events + "; ";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(BASE_ENTITY, "package net.minecraft.unmapped; public class C_42232651 { }");
        sources.put(ENTITY, "package net.minecraft.unmapped; " + prefix + "public class C_74425583 extends C_42232651 { public boolean m_69151229(){return Events.hidden();} }");
        sources.put(MODEL, "package net.minecraft.unmapped; " + prefix + "public class C_36076665 { public void m_61514729(C_42232651 e,float a,float b,float c,float d,float f,float g){Events.flat(e,a,b,c,d,f,g);} }");
        sources.put("net/minecraft/client/Minecraft", "package net.minecraft.client; " + prefix + "public class Minecraft { private static final Minecraft INSTANCE=new Minecraft(); public static Minecraft m_20213497(){Events.event(\"minecraft\");return INSTANCE;} public net.minecraft.unmapped.C_82920792 m_65924382(){Events.event(\"client\");return new net.minecraft.unmapped.C_82920792();} }");
        sources.put("net/minecraft/unmapped/C_82920792", "package net.minecraft.unmapped; " + prefix + "public class C_82920792 { public dev.rdh.cera.modules.EmissiveTextures cera$getEmissiveTextures(){Events.event(\"module\");return new dev.rdh.cera.modules.EmissiveTextures();} }");
        sources.put("dev/rdh/cera/modules/EmissiveTextures", "package dev.rdh.cera.modules; " + prefix + "public class EmissiveTextures { public boolean active(){return Events.active();} public int beginRender(){Events.event(\"begin\");return 0x1234;} public boolean hasEmissive(){return Events.emissive();} public long beginRenderEmissive(){Events.event(\"emissive.begin\");return 0x1020304050607080L;} public void endRenderEmissive(long state){Events.event(\"emissive.end:\"+Long.toHexString(state));} public void endRender(int state){Events.event(\"end:\"+Integer.toHexString(state));} }");
        sources.put("dev/rdh/argentum/impl/render/entity/instancing/EntityInstancing", "package dev.rdh.argentum.impl.render.entity.instancing; " + prefix + "public class EntityInstancing { public boolean isBatchActive(){return Events.batch();} public static EntityInstancing current(){Events.event(\"instancing\");return Events.instance ? new EntityInstancing() : null;} }");
        sources.put("net/minecraft/unmapped/C_37925375", "package net.minecraft.unmapped; " + prefix + "public class C_37925375 { public static void m_55305366(){Events.event(\"matrix.push\");} public static void m_23590488(){Events.event(\"matrix.pop\");} }");
        sources.put("org/polyfrost/overflowanimations/legacy/tint/LegacyDamageTint", "package org.polyfrost.overflowanimations.legacy.tint; " + prefix + "public class LegacyDamageTint { public static boolean beginFlatPass(net.minecraft.unmapped.C_42232651 e,float tick){return Events.beginTint(e,tick);} public static void endFlatPass(){Events.event(\"tint.end\");} }");
        sources.put("com/llamalad7/mixinextras/sugar/ref/LocalFloatRef", "package com.llamalad7.mixinextras.sugar.ref; public interface LocalFloatRef {float get();}");
        sources.put("com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalFloatRefImpl", "package com.llamalad7.mixinextras.sugar.impl.ref.generated; " + prefix + "public class LocalFloatRefImpl implements com.llamalad7.mixinextras.sugar.ref.LocalFloatRef {private float value; public LocalFloatRefImpl(){Events.event(\"ref.new\");} public void init(float f){Events.event(\"ref.init:\"+Integer.toHexString(Float.floatToRawIntBits(f)));value=f;} public float get(){Events.event(\"ref.get\");return value;} public float dispose(){Events.event(\"ref.dispose\");return value;} }");
        Path sourceRoot = output.resolve("stub-source"), classes = output.resolve("stub-classes"); Files.createDirectories(classes);
        List<String> compiler = new ArrayList<>(List.of("--release", "25", "-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) { Path file = sourceRoot.resolve(source.getKey() + ".java"); Files.createDirectories(file.getParent()); Files.writeString(file, source.getValue()); compiler.add(file.toString()); }
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, compiler.toArray(String[]::new)) == 0, "Stub compilation failed");
        Map<String, byte[]> result = new HashMap<>(); try (var files = Files.walk(classes)) { for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) result.put(classes.relativize(file).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", ""), Files.readAllBytes(file)); }
        return result;
    }

    private record Fixture(Class<?> owner, Class<?> entity, Class<?> override) { }
    private static Fixture load(byte[] fixture, Map<String, byte[]> stubs) throws Exception {
        Map<String, byte[]> definitions = new HashMap<>(stubs); definitions.put(OWNER.replace('/', '.'), fixture);
        ClassNode subclass = new ClassNode(ASM9); subclass.version = V25; subclass.access = ACC_PUBLIC; subclass.name = OWNER + "$VirtualOverride"; subclass.superName = OWNER;
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null); constructor.instructions.add(new VarInsnNode(ALOAD, 0)); constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, OWNER, "<init>", "()V", false)); constructor.instructions.add(new InsnNode(RETURN)); subclass.methods.add(constructor); subclass.methods.add(renderMethod(subclass.name, true)); definitions.put(subclass.name.replace('/', '.'), bytes(subclass, true));
        ClassLoader loader = new ClassLoader(CeraLivingOperationFusionProbe.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) { byte[] code = definitions.get(name); loaded = code == null ? super.loadClass(name, false) : defineClass(name, code, 0, code.length); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        return new Fixture(loader.loadClass(OWNER.replace('/', '.')), loader.loadClass(ENTITY.replace('/', '.')), loader.loadClass(subclass.name.replace('/', '.')));
    }

    private record Scenario(int bits, boolean nested, boolean override, float[] floats) { }
    private record Outcome(List<String> events, Throwable thrown) { }
    private static Outcome run(Fixture fixture, Scenario scenario, boolean enabled, int failAt) throws Exception {
        return run(fixture, scenario, enabled, failAt, 0);
    }
    private static Outcome run(Fixture fixture, Scenario scenario, boolean enabled, int failAt, int cleanupFailure) throws Exception {
        Events.reset(scenario.bits, failAt, cleanupFailure); RenderHookAllocationControl.enabled = enabled;
        Object captured = fixture.owner.getConstructor().newInstance(), receiver = (scenario.override ? fixture.override : fixture.owner).getConstructor().newInstance(), entity = fixture.entity.getConstructor().newInstance();
        Events.names.put(captured, "captured"); Events.names.put(receiver, "supplied"); Events.names.put(entity, "entity"); fixture.owner.getField("f_44076704").setBoolean(captured, (scenario.bits & 1) != 0);
        Method method = fixture.owner.getMethod(scenario.nested ? "probeNested" : "probeDirect", fixture.owner, fixture.entity, float.class, float.class, float.class, float.class, float.class, float.class, float.class);
        Object[] arguments = new Object[9]; arguments[0] = receiver; arguments[1] = entity; for (int i = 0; i < 7; i++) arguments[i + 2] = scenario.floats[i];
        Throwable thrown = null; try { method.invoke(captured, arguments); } catch (InvocationTargetException failure) { thrown = failure.getCause(); }
        if (thrown != null) require(thrown == Events.FAILURE || thrown == Events.CLEANUP_FAILURE, "Unexpected JVM failure: " + thrown);
        return new Outcome(List.copyOf(Events.events), thrown);
    }

    private static void same(Outcome expected, Outcome actual, String label) { require(expected.events.equals(actual.events) && expected.thrown == actual.thrown, "Differential mismatch " + label + "\nexpected=" + expected + "\nactual=" + actual); }

    public static final class Events {
        public static final RuntimeException FAILURE = new IllegalStateException("Cera callback probe");
        public static final RuntimeException CLEANUP_FAILURE = new IllegalArgumentException("Cera cleanup probe");
        public static boolean instance;
        private static boolean active, emissive, batch, hidden, tint;
        private static int failAt, cleanupFailure;
        private static final List<String> events = new ArrayList<>();
        private static final IdentityHashMap<Object, String> names = new IdentityHashMap<>();
        private static void reset(int bits, int fail, int cleanup) { events.clear(); names.clear(); active = (bits & 2) != 0; emissive = (bits & 4) != 0; instance = (bits & 8) != 0; batch = (bits & 16) != 0; hidden = (bits & 32) != 0; tint = (bits & 64) != 0; failAt = fail; cleanupFailure = cleanup; }
        public static void event(String event) { events.add(event); if (events.size() == failAt) throw FAILURE; if (events.size() == cleanupFailure) throw CLEANUP_FAILURE; }
        public static boolean active() { event("active"); return active; }
        public static boolean emissive() { event("hasEmissive"); return emissive; }
        public static boolean batch() { event("batch"); return batch; }
        public static boolean hidden() { event("hidden"); return hidden; }
        public static boolean beginTint(Object entity, float tick) { event("tint.begin:" + names.get(entity) + ":" + Integer.toHexString(Float.floatToRawIntBits(tick))); return tint; }
        public static void render(Object self, Object entity, float a, float b, float c, float d, float e, float f) { event("render:" + names.get(self) + ":" + names.get(entity) + ":" + bits(a,b,c,d,e,f)); }
        public static void overrideRender(Object self, Object entity, float a, float b, float c, float d, float e, float f) { event("override:" + names.get(self) + ":" + names.get(entity) + ":" + bits(a,b,c,d,e,f)); }
        public static void flat(Object entity, float a, float b, float c, float d, float e, float f) { event("flat:" + names.get(entity) + ":" + bits(a,b,c,d,e,f)); }
        private static String bits(float... values) { StringJoiner text = new StringJoiner(","); for (float value : values) text.add(Integer.toHexString(Float.floatToRawIntBits(value))); return text.toString(); }
    }

    private static Map<String, byte[]> handlerBytes(ClassNode node) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (MethodNode method : node.methods) if (method == cera(node) || method == tint(node) || method == sugar(node) || method.name.startsWith("mixinextras$bridge$") && (method.name.contains("cera$emissivePass") || method.name.matches("mixinextras\\$bridge\\$m_94468904\\$7[01]"))) {
            ClassNode copy = new ClassNode(ASM9); copy.version = V25; copy.access = ACC_PUBLIC; copy.name = node.name; copy.superName = "java/lang/Object"; copy.methods.add(method); result.put(method.name + method.desc, bytes(copy, false));
        }
        return result;
    }
    private static MethodNode cera(ClassNode node) { return node.methods.stream().filter(method -> method.name.endsWith("$cera$emissivePass")).findFirst().orElseThrow(); }
    private static MethodNode tint(ClassNode node) { return node.methods.stream().filter(method -> method.name.endsWith("$overflowanimations$renderFlatTint")).findFirst().orElseThrow(); }
    private static MethodNode sugar(ClassNode node) { String name = tint(node).name + "$mixinextras$bridge$"; return node.methods.stream().filter(method -> method.name.startsWith(name)).findFirst().orElseThrow(); }
    private static AnnotationNode annotation(MethodNode method) { return method.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/MixinMerged;")).findFirst().orElseThrow(); }
    private static MethodNode caller(ClassNode node) { return node.methods.stream().filter(method -> !method.name.startsWith("mixinextras$bridge$") && Arrays.stream(method.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals(cera(node).name))).findFirst().orElseThrow(); }
    private static int capturedIndex(MethodNode caller) { List<AbstractInsnNode> code = code(caller); for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof InvokeDynamicInsnNode dynamic && dynamic.bsmArgs.length == 3 && dynamic.bsmArgs[1] instanceof Handle handle && handle.getName().contains("cera$emissivePass")) return i; throw new IllegalStateException("Missing captured Cera bridge"); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> code = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction); return code; }
    private static ClassNode read(byte[] code) { ClassNode node = new ClassNode(ASM9); new ClassReader(code).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node, boolean frames) { ClassWriter writer = new ClassWriter(frames ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS) { @Override protected String getCommonSuperClass(String left, String right) { return left.equals(right) ? left : "java/lang/Object"; } }; node.accept(writer); return writer.toByteArray(); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
