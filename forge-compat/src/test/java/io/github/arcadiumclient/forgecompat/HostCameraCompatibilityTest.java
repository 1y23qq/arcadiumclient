package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.type.RemappableAnnotationNode;
import com.bawnorton.mixinsquared.reflection.StateExtension;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HostCameraCompatibilityTest {
    private static final String NATIVE = "org/codeberg/chromatic/freelook/mixin/legacy/MixinGameRenderer";
    private static final String CONTROLLER = "org/codeberg/chromatic/freelook/handler/FreelookHandler";
    private static final String TRACKER = "org/codeberg/chromatic/freelook/util/CameraStateTracker";
    private static final String GUEST = "keystrokesmod/mixin/impl/render/MixinEntityRenderer";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final Set<String> HANDLERS = Set.of("modifyYaw", "modifyLastYaw", "modifyPitch", "modifyLastPitch");
    private static final Set<String> GUEST_HANDLERS = Set.of("freelookRotationYaw", "freelookPrevRotationYaw", "freelookRotationPitch", "freelookPrevRotationPitch");
    @TempDir static Path temporary;
    private ForgeMappings mappings;
    private HostCameraCompatibility adjuster;
    private ClassNode nativeMixin, guestMixin, controller, renderer;
    private String entity, orient, rendererName;
    private Map<String,String> roles;
    private Path runtimeGame, runtimeGuest;

    @BeforeAll void setup() throws Exception {
        raven();
        Path root = Path.of("..").toAbsolutePath().normalize(), loom = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(Files.readString(loom.resolve("calamus-gen2-intermediary-v2.tiny"))), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver) constructor.newInstance(tree, "intermediary"), reader); }
        Path patches = temporary.resolve("patches.jar"); try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) { assertNotNull(input); Files.copy(input, patches); }
        Path patched = new ForgeGamePatcher().patch(loom.resolve("minecraft-client.jar"), patches, temporary.resolve("game"));
        ForgeJarTransformer transformer = new ForgeJarTransformer(); transformer.index(patched, mappings, true); transformer.index(forge(), mappings, true); transformer.index(raven(), mappings, false);
        Path runtime = transformer.transform(patched, temporary.resolve("runtime-game"), mappings, true, null);
        Path guest = transformer.transform(raven(), temporary.resolve("runtime-guest"), mappings, false, null);
        runtimeGame = runtime; runtimeGuest = guest;
        entity = mappings.classFromSrg("net/minecraft/entity/Entity"); rendererName = mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer");
        orient = mappings.methodFromSrg("net/minecraft/client/renderer/EntityRenderer", "func_78467_g", "(F)V");
        roles = Map.of(mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70177_z", "F"), "yaw",
            mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70126_B", "F"), "previousYaw",
            mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70125_A", "F"), "pitch",
            mappings.fieldFromSrg("net/minecraft/entity/Entity", "field_70127_C", "F"), "previousPitch");
        nativeMixin = read(freelook(), NATIVE); controller = read(freelook(), CONTROLLER); guestMixin = read(guest, GUEST); renderer = read(runtime, rendererName);
        adjuster = new HostCameraCompatibility(mappings);
    }

    @Test void cooperatesAtAllFourActualNativeFieldsWithoutRelaxingSelectorsOrdinalsOrBounds() throws Exception {
        int adapted = 0;
        for (MethodNode source : nativeMixin.methods) if (HANDLERS.contains(source.name)) {
            MethodNode handler = copy(source); AdjustableAnnotationNode original = annotation(handler); original.set("require", 2); original.set("allow", 8);
            Map<String,Object> settings = settings(original); List<?> oldPoints = points(original); int locals = handler.maxLocals;
            AdjustableAnnotationNode result = adjuster.adjust(List.of(rendererName), NATIVE, handler, original);
            assertEquals(WRAP, result.desc); assertEquals("(L" + entity + ";L" + OPERATION + ";)F", handler.desc);
            assertEquals(settings, settings(result)); assertEquals(oldPoints.stream().map(p -> ((AnnotationNode) p).values).toList(), points(result).stream().map(p -> ((AnnotationNode) p).values).toList());
            assertEquals(locals + 1, handler.maxLocals); assertEquals(1, calls(handler).stream().filter(call -> call.owner.equals(OPERATION) && call.name.equals("call")).count());
            assertEquals(1, calls(handler).stream().filter(call -> call.owner.equals(CONTROLLER) && (call.name.equals("cameraYaw") || call.name.equals("cameraPitch"))).count());
            assertEquals(0, Arrays.stream(handler.instructions.toArray()).filter(i -> i instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD).count());
            new Analyzer<>(new BasicVerifier()).analyze(nativeMixin.name, handler);
            byte[] once = methodBytes(handler); adjuster.adjust(List.of(rendererName), NATIVE, handler, result); assertArrayEquals(once, methodBytes(handler)); adapted++;
        }
        assertEquals(4, adapted);
        for (MethodNode source : nativeMixin.methods) if (!HANDLERS.contains(source.name) && source.visibleAnnotations != null) for (AnnotationNode annotation : source.visibleAnnotations) {
            byte[] before = methodBytes(source); adjuster.adjust(List.of(rendererName), NATIVE, source, adjustable(annotation)); assertArrayEquals(before, methodBytes(source));
        }
    }

    @Test void registersTheNewHandlerAbiInTheActualCachedMixinMetadataAndStateClone() throws Exception {
        HostClassMetadataTest.initializeMixinProperties();
        ClassNode template = copy(nativeMixin); template.name = NATIVE;
        var fromNode = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class); fromNode.setAccessible(true);
        ClassInfo info = (ClassInfo) fromNode.invoke(null, template);
        MethodNode source = method(template, "modifyYaw"); String oldDescriptor = source.desc;
        Class<?> infoType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo"), stateType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$State");
        var constructor = stateType.getDeclaredConstructor(infoType, ClassNode.class, ClassInfo.class); constructor.setAccessible(true); Object state = constructor.newInstance(null, template, info);
        var create = stateType.getDeclaredMethod("createClassNode", int.class); create.setAccessible(true); ClassNode clone = (ClassNode) create.invoke(state, 0);
        MethodNode handler = method(clone, "modifyYaw"); AdjustableAnnotationNode result = adjuster.adjust(List.of(rendererName), NATIVE, handler, annotation(handler));
        replaceAnnotation(handler, result); StateExtension.tryAs(state, extension -> extension.setClassNode(clone)); ClassNode applied = (ClassNode) create.invoke(state, 0);
        assertEquals(WRAP, annotation(method(applied, "modifyYaw")).desc); assertNotEquals(oldDescriptor, method(applied, "modifyYaw").desc);
        assertNotNull(info.findMethod(method(applied, "modifyYaw"), ClassInfo.INCLUDE_ALL)); assertSame(info, ClassInfo.fromCache(NATIVE));
    }

    @Test void reproducesTheActualRedirectConflictAndExecutesTheCooperativeWrapWithTheRealMixinEngine() throws Exception {
        Path fixture = fixture();
        String raw = run(fixture, "raw"); assertTrue(raw.contains("CAMERA_PROBE EXPECTED_REDIRECT_CONFLICT"), raw);
        String fixed = run(fixture, "fixed"); assertTrue(fixed.contains("CAMERA_PROBE SUCCESS mode=fixed cases=48"), fixed);
        ClassNode result = readClass(temporary.resolve("fixed.class"));
        assertEquals(4, result.methods.stream().filter(method -> method.name.contains("modify") && method.desc.contains(OPERATION)).count());
        assertTrue(result.methods.stream().anyMatch(method -> method.name.contains("freelookRotationYaw")));
    }

    @Test void retainsNativeCameraBehaviorWithNoGuestRedirectsThroughTheSameOperationAdapter() throws Exception {
        String output = run(fixture(), "native"); assertTrue(output.contains("CAMERA_PROBE SUCCESS mode=native cases=48"), output);
    }

    @Test void leavesGuestAndOtherNativeHandlersUntouchedAndRejectsUnknownNativeBodiesBeforeMutation() throws Exception {
        MethodNode untouched = copy(method(nativeMixin, "modifyYaw")); AdjustableAnnotationNode original = annotation(untouched); byte[] before = methodBytes(untouched);
        adjuster.adjust(List.of(rendererName), GUEST, untouched, original); assertArrayEquals(before, methodBytes(untouched));
        adjuster.adjust(List.of("fixture/Other"), NATIVE, untouched, original); assertArrayEquals(before, methodBytes(untouched));
        MethodNode changed = copy(method(nativeMixin, "modifyPitch")); ((FieldInsnNode) Arrays.stream(changed.instructions.toArray()).filter(i -> i instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD).findFirst().orElseThrow()).name = "unknown";
        byte[] invalid = methodBytes(changed); assertThrows(IllegalStateException.class, () -> adjuster.adjust(List.of(rendererName), NATIVE, changed, annotation(changed))); assertArrayEquals(invalid, methodBytes(changed));
        byte[] guest = bytes(guestMixin), nativeController = bytes(controller);
        for (MethodNode method : guestMixin.methods) if (method.visibleAnnotations != null) for (AnnotationNode annotation : method.visibleAnnotations) adjuster.adjust(List.of(rendererName), GUEST, method, adjustable(annotation));
        assertArrayEquals(guest, bytes(guestMixin)); assertArrayEquals(nativeController, bytes(controller));
    }

    @Test void adaptsTheActualStaticPlayerAndWorldFieldsWhileKeepingTheSecondWorldSelectorAndPreviousFields() throws Exception {
        for (String name : List.of(CameraExtraFixtures.NATIVE_CAMERA, CameraExtraFixtures.NATIVE_WORLD)) {
            ClassNode source = CameraExtraFixtures.read(freelook(), name);
            AnnotationNode mixin = source.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            String target = ((Type) ((List<?>) value(mixin,"value")).get(0)).getInternalName();
            int adapted = 0;
            for (MethodNode original : source.methods) if (original.visibleAnnotations != null) for (AnnotationNode item : original.visibleAnnotations) if (item.desc.equals(REDIRECT)) {
                MethodNode handler = copy(original); AdjustableAnnotationNode annotation = annotation(handler); byte[] before = methodBytes(handler);
                if (Set.of("modifyYaw","modifyPitch").contains(handler.name)) {
                    annotation.set("require", 1); annotation.set("allow", 10); Map<String,Object> settings = settings(annotation);
                    AdjustableAnnotationNode result = adjuster.adjust(List.of(target),name,handler,annotation);
                    assertEquals(WRAP,result.desc); assertEquals(settings,settings(result)); assertEquals(original.desc.replace(")F","L"+OPERATION+";)F"),handler.desc);
                    assertEquals((original.access & Opcodes.ACC_STATIC)!=0,(handler.access & Opcodes.ACC_STATIC)!=0);
                    assertEquals(points(annotation).stream().map(at->((AnnotationNode)at).values).toList(),points(result).stream().map(at->((AnnotationNode)at).values).toList());
                    assertEquals(name.equals(CameraExtraFixtures.NATIVE_WORLD)?2:1,((List<?>)value(result,"method")).size());
                    assertEquals(1,calls(handler).stream().filter(call->call.owner.equals(OPERATION)).count());new Analyzer<>(new BasicVerifier()).analyze(name,handler);adapted++;
                } else { adjuster.adjust(List.of(target),name,handler,annotation); assertArrayEquals(before,methodBytes(handler)); }
            }
            assertEquals(2,adapted);
        }
    }

    @Test void wrapsTheActualHurtRotationAndRetainsTheOriginalMathAndCancellableHead() throws Exception {
        ClassNode source=CameraExtraFixtures.read(CameraExtraFixtures.hurtcam(),CameraExtraFixtures.NATIVE_HURT);
        MethodNode original=method(source,"betterhurtcam$modifyHurtCam"),handler=copy(original),head=method(source,"betterhurtcam$disableHurtCam");
        byte[] before=methodBytes(head);List<String> prefix=semantic(original).subList(0,17);AdjustableAnnotationNode annotation=annotation(handler);annotation.set("require",1);annotation.set("allow",8);
        Map<String,Object> settings=settings(annotation);AdjustableAnnotationNode result=adjuster.adjust(List.of(rendererName),CameraExtraFixtures.NATIVE_HURT,handler,annotation);
        assertEquals(WRAP,result.desc);assertEquals("(FFFFL"+OPERATION+";)V",handler.desc);assertEquals(settings,settings(result));assertEquals(prefix,semantic(handler).subList(0,17));
        assertEquals(points(annotation).stream().map(a->((AnnotationNode)a).values).toList(),points(result).stream().map(a->((AnnotationNode)a).values).toList());
        assertEquals(1,calls(handler).stream().filter(call->call.owner.equals(OPERATION)).count());assertEquals(0,calls(handler).stream().filter(call->call.owner.equals(mappings.classFromSrg("net/minecraft/client/renderer/GlStateManager"))).count());
        new Analyzer<>(new BasicVerifier()).analyze(source.name,handler);byte[] once=methodBytes(handler);adjuster.adjust(List.of(rendererName),source.name,handler,result);assertArrayEquals(once,methodBytes(handler));
        for(AnnotationNode item:head.visibleAnnotations)adjuster.adjust(List.of(rendererName),source.name,head,adjustable(item));assertArrayEquals(before,methodBytes(head));
    }

    @Test void executesTheOtherCameraFieldsBothWorldSelectorsAndHurtRotationThroughTheActualMixinEngine() throws Exception {
        Path fixture=CameraExtraFixtures.build(temporary,mappings,runtimeGame,runtimeGuest,roles);
        for(String mode:List.of("fixed","native")) {
            String output=run(fixture,mode,CameraExtendedRuntimeProbe.class,temporary.resolve("extra-"+mode));
            assertTrue(output.contains("CAMERA_EXTRA_PROBE SUCCESS mode="+mode+" player=48 world=96 direction=96 hurt=72"),output);
        }
    }

    private static List<String> semantic(MethodNode method) {
        List<String> result=new ArrayList<>();for(AbstractInsnNode instruction:method.instructions)if(instruction.getOpcode()>=0){String value=Integer.toString(instruction.getOpcode());
            if(instruction instanceof VarInsnNode variable)value+=":"+variable.var;else if(instruction instanceof MethodInsnNode call)value+=":"+call.owner+"."+call.name+call.desc+":"+call.itf;else if(instruction instanceof FieldInsnNode field)value+=":"+field.owner+"."+field.name+field.desc;else if(instruction instanceof LdcInsnNode constant)value+=":"+constant.cst;
            result.add(value);
        }return result;
    }

    private String run(Path fixture, String mode) throws Exception {
        return run(fixture,mode,CameraMixinRuntimeProbe.class,temporary.resolve(mode+".class"));
    }

    private String run(Path fixture, String mode, Class<?> probe, Path result) throws Exception {
        LinkedHashSet<Path> paths = new LinkedHashSet<>(); paths.add(fixture);
        for (Class<?> type : List.of(CameraMixinRuntimeProbe.class, HostCameraCompatibility.class, MixinBootstrap.class, MixinExtrasBootstrap.class, AdjustableAnnotationNode.class, ClassReader.class, ClassNode.class, Remapper.class, Analyzer.class, org.objectweb.asm.util.CheckClassAdapter.class, MappingResolver.class, JsonParser.class, com.google.common.collect.ImmutableList.class)) paths.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        Path output = temporary.resolve(probe.getSimpleName()+"-"+mode + ".log"); List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", String.join(java.io.File.pathSeparator, paths.stream().map(Path::toString).toList()), probe.getName(), fixture.toString(), mode, result.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        for (String name : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(name);
        Process process = builder.start(); boolean finished = process.waitFor(45, TimeUnit.SECONDS); if (!finished) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        String log = Files.readString(output); assertTrue(finished, log); assertEquals(0, process.exitValue(), log); return log;
    }

    private Path fixture() throws Exception {
        Path jar = temporary.resolve("camera-fixture.jar"); if (Files.exists(jar)) return jar;
        Map<String,byte[]> files = new LinkedHashMap<>();
        Remapper binding = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                if (name.equals(entity)) return Type.getInternalName(CameraFixtureSupport.Entity.class);
                if (name.equals(rendererName)) return "fixture/camera/Renderer";
                return switch (name) {
                    case "keystrokesmod/module/ModuleManager" -> Type.getInternalName(CameraFixtureSupport.GuestManager.class);
                    case "keystrokesmod/module/impl/render/Freelook" -> Type.getInternalName(CameraFixtureSupport.GuestFreelook.class);
                    case "keystrokesmod/module/impl/combat/AimAssist" -> Type.getInternalName(CameraFixtureSupport.GuestAim.class);
                    default -> name;
                };
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return owner.equals(entity) ? roles.getOrDefault(name,name) : name; }
        };
        for (boolean nativeSource : List.of(false, true)) {
            ClassNode source = copy(nativeSource ? nativeMixin : guestMixin); source.version = Opcodes.V17; source.fields.clear(); source.innerClasses.clear(); source.interfaces.clear();
            source.methods.removeIf(method -> !(nativeSource ? HANDLERS : GUEST_HANDLERS).contains(method.name));
            filterClassAnnotations(source);
            ClassNode mapped = new ClassNode(); source.accept(new ClassRemapper(mapped, binding));
            for (MethodNode method : mapped.methods) {
                AnnotationNode annotation = method.visibleAnnotations.stream().filter(a -> a.desc.equals(REDIRECT)).findFirst().orElseThrow(); put(annotation, "method", new ArrayList<>(List.of("orient")));
                for (Object point : points(annotation)) {
                    AnnotationNode at = (AnnotationNode) point; String target = (String) value(at,"target");
                    for (var role : roles.entrySet()) if (target.equals("L" + entity + ";" + role.getKey() + ":F")) put(at,"target","L" + Type.getInternalName(CameraFixtureSupport.Entity.class) + ";" + role.getValue() + ":F");
                }
            }
            files.put(mapped.name + ".class", bytes(mapped));
        }
        ClassNode state = copy(controller); state.version = Opcodes.V17; state.fields.removeIf(field -> !Set.of("INSTANCE","freelookToggled").contains(field.name)); state.methods.removeIf(method -> !Set.of("cameraYaw","cameraPitch").contains(method.name)); state.innerClasses.clear();
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null); init.visitVarInsn(Opcodes.ALOAD,0); init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false); init.visitInsn(Opcodes.RETURN); state.methods.add(init);
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null); clinit.visitTypeInsn(Opcodes.NEW,CONTROLLER); clinit.visitInsn(Opcodes.DUP); clinit.visitMethodInsn(Opcodes.INVOKESPECIAL,CONTROLLER,"<init>","()V",false); clinit.visitFieldInsn(Opcodes.PUTSTATIC,CONTROLLER,"INSTANCE","L"+CONTROLLER+";"); clinit.visitInsn(Opcodes.RETURN); state.methods.add(clinit);
        ClassNode bound = new ClassNode(); state.accept(new ClassRemapper(bound,binding)); files.put(bound.name+".class",bytes(bound));
        ClassNode tracker = read(freelook(),TRACKER); tracker.version=Opcodes.V17; files.put(TRACKER+".class",bytes(tracker));
        files.put("fixture/camera/Tracked.class",tracked(tracker));
        List<String> order = new ArrayList<>(); for (AbstractInsnNode instruction : method(renderer,orient).instructions) if (instruction instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD && field.owner.equals(entity) && roles.containsKey(field.name)) order.add(roles.get(field.name));
        assertFalse(order.isEmpty()); assertEquals(4,new HashSet<>(order).size());
        files.put("fixture/camera/Renderer.class",target(order)); files.put("camera-order.txt",String.join(",",order).getBytes(StandardCharsets.UTF_8));
        files.put("camera-guest.json",config("keystrokesmod.mixin.impl.render","MixinEntityRenderer")); files.put("camera-native.json",config("org.codeberg.chromatic.freelook.mixin.legacy","MixinGameRenderer"));
        files.put("META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService",(CameraMixinRuntimeProbe.Properties.class.getName()+"\n").getBytes(StandardCharsets.UTF_8));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) { for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } }
        return jar;
    }

    private static byte[] config(String name,String mixin) { return ("{\"required\":true,\"minVersion\":\"0.8\",\"package\":\""+name+"\",\"compatibilityLevel\":\"JAVA_17\",\"mixins\":[\""+mixin+"\"],\"injectors\":{\"defaultRequire\":1}}").getBytes(StandardCharsets.UTF_8); }
    private static void filterClassAnnotations(ClassNode node) { if(node.visibleAnnotations!=null)node.visibleAnnotations.removeIf(a->!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));if(node.invisibleAnnotations!=null)node.invisibleAnnotations.removeIf(a->!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")); }
    private static byte[] target(List<String> order) {
        String owner="fixture/camera/Renderer",entity=Type.getInternalName(CameraFixtureSupport.Entity.class); ClassNode node=new ClassNode();node.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"view","L"+entity+";",null,null));node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"result","F",null,null));
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","(L"+entity+";)V",null,null);init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitVarInsn(Opcodes.ALOAD,0);init.visitVarInsn(Opcodes.ALOAD,1);init.visitFieldInsn(Opcodes.PUTFIELD,owner,"view","L"+entity+";");init.visitInsn(Opcodes.RETURN);node.methods.add(init);
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,"orient","(F)V",null,null);method.visitVarInsn(Opcodes.ALOAD,0);method.visitInsn(Opcodes.FCONST_0);
        for(String role:order){method.visitVarInsn(Opcodes.ALOAD,0);method.visitFieldInsn(Opcodes.GETFIELD,owner,"view","L"+entity+";");method.visitFieldInsn(Opcodes.GETFIELD,entity,role,"F");method.visitInsn(Opcodes.FADD);}method.visitFieldInsn(Opcodes.PUTFIELD,owner,"result","F");method.visitInsn(Opcodes.RETURN);node.methods.add(method);return bytes(node);
    }
    private static byte[] tracked(ClassNode tracker) {
        String owner="fixture/camera/Tracked",entity=Type.getInternalName(CameraFixtureSupport.Entity.class);ClassNode node=new ClassNode();node.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,owner,null,entity,new String[]{TRACKER});
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,entity,"<init>","()V",false);init.visitInsn(Opcodes.RETURN);node.methods.add(init);
        for(MethodNode signature:tracker.methods){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,signature.name,signature.desc,null,null);boolean yaw=signature.name.toLowerCase(Locale.ROOT).contains("yaw");method.visitVarInsn(Opcodes.ALOAD,0);if(signature.desc.equals("()F")){method.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(CameraFixtureSupport.class),yaw?"nativeYaw":"nativePitch","(L"+entity+";)F",false);method.visitInsn(Opcodes.FRETURN);}else{method.visitVarInsn(Opcodes.FLOAD,1);method.visitFieldInsn(Opcodes.PUTFIELD,entity,yaw?"nativeYaw":"nativePitch","F");method.visitInsn(Opcodes.RETURN);}node.methods.add(method);}return bytes(node);
    }
    private static Path freelook(){return ForgeTestFixtures.nativeJar("freelook-2.3.0+mc1.8.9.jar").toAbsolutePath().normalize();}
    private static Path raven(){return ForgeTestFixtures.optionalGuest("raven-bS-17 (13).jar").toAbsolutePath().normalize();}
    private static Path forge(){return Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toAbsolutePath().normalize();}
    private static ClassNode read(Path jar,String name)throws Exception{try(ZipFile zip=new ZipFile(jar.toFile());var input=zip.getInputStream(zip.getEntry(name+".class"))){ClassNode node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);return node;}}
    private static ClassNode readClass(Path path)throws Exception{ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(node,0);return node;}
    private static ClassNode copy(ClassNode source){ClassNode result=new ClassNode();source.accept(result);return result;}
    private static MethodNode copy(MethodNode source){MethodNode result=new MethodNode(source.access,source.name,source.desc,source.signature,source.exceptions.toArray(String[]::new));source.accept(result);return result;}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(method->method.name.equals(name)).findFirst().orElseThrow();}
    private static List<MethodInsnNode> calls(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode).map(MethodInsnNode.class::cast).toList();}
    private static AdjustableAnnotationNode annotation(MethodNode handler){return adjustable(handler.visibleAnnotations.stream().filter(a->a.desc.equals(REDIRECT)||a.desc.equals(WRAP)).findFirst().orElseThrow());}
    private static AdjustableAnnotationNode adjustable(AnnotationNode source){AdjustableAnnotationNode result=AdjustableAnnotationNode.fromNode(source);if(result instanceof RemappableAnnotationNode remappable)remappable.setRemapper(value->value.applyRefmap(UnaryOperator.identity()));return result;}
    private static void replaceAnnotation(MethodNode method,AnnotationNode value){for(int i=0;i<method.visibleAnnotations.size();i++)if(method.visibleAnnotations.get(i).desc.equals(REDIRECT)||method.visibleAnnotations.get(i).desc.equals(WRAP))method.visibleAnnotations.set(i,value);}
    private static Object value(AnnotationNode node,String key){if(node.values!=null)for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(key))return node.values.get(i+1);return null;}
    private static List<?> points(AnnotationNode annotation){Object value=value(annotation,"at");return value instanceof List<?>list?list:value instanceof AnnotationNode point?List.of(point):List.of();}
    private static Map<String,Object> settings(AnnotationNode node){Map<String,Object> result=new LinkedHashMap<>();if(node.values!=null)for(int i=0;i<node.values.size();i+=2)if(!node.values.get(i).equals("at"))result.put(node.values.get(i).toString(),node.values.get(i+1));return result;}
    private static void put(AnnotationNode node,String key,Object value){if(node.values!=null)for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(key)){node.values.set(i+1,value);return;}if(node.values==null)node.values=new ArrayList<>();node.values.add(key);node.values.add(value);}
    private static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
    private static byte[] methodBytes(MethodNode method){ClassNode node=new ClassNode();node.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"fixture/Method",null,"java/lang/Object",null);node.methods.add(method);return bytes(node);}
}
