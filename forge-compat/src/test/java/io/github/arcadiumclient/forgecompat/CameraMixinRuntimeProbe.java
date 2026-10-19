package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.*;

public final class CameraMixinRuntimeProbe {
    private static final String TARGET = "fixture.camera.Renderer";
    private CameraMixinRuntimeProbe() { }

    public static void main(String[] args) {
        try {
            System.setProperty("mixin.service", Service.class.getName());
            System.setProperty("camera.fixture", args[0]);
            MixinBootstrap.init(); MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            var constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor(); constructor.setAccessible(true);
            IMixinTransformer transformer = (IMixinTransformer) constructor.newInstance();
            MixinExtrasBootstrap.init();
            if (!args[1].equals("raw")) {
                MixinSquaredBootstrap.init(); MixinAnnotationAdjusterRegistrar.register(new HostCameraCompatibility(fixtureMappings())); MixinSquaredBootstrap.reOrderExtensions();
            }
            if (!args[1].equals("native")) Mixins.addConfiguration("camera-guest.json");
            Mixins.addConfiguration("camera-native.json");
            byte[] original;
            try (var input = CameraMixinRuntimeProbe.class.getClassLoader().getResourceAsStream(TARGET.replace('.', '/') + ".class")) { original = input.readAllBytes(); }
            byte[] transformed;
            try { transformed = transformer.transformClassBytes(TARGET, TARGET, original); }
            catch (Throwable failure) {
                boolean conflict = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) if (cause.getMessage() != null && cause.getMessage().contains("Redirector modifyYaw") && cause.getMessage().contains("(0/1)")) conflict = true;
                if (args[1].equals("raw") && conflict) { System.out.println("CAMERA_PROBE EXPECTED_REDIRECT_CONFLICT"); return; }
                throw failure;
            }
            if (args[1].equals("raw")) throw new AssertionError("Original competing redirects unexpectedly succeeded");
            ClassLoader host = new ClassLoader(CameraMixinRuntimeProbe.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.equals(TARGET)) return super.loadClass(name, resolve);
                    synchronized (getClassLoadingLock(name)) { Class<?> result = findLoadedClass(name); if (result == null) result = defineClass(name, transformed, 0, transformed.length); if (resolve) resolveClass(result); return result; }
                }
            };
            Class<?> renderer = host.loadClass(TARGET), controller = Class.forName("org.codeberg.chromatic.freelook.handler.FreelookHandler");
            Object state = controller.getField("INSTANCE").get(null);
            Class<?> tracked = Class.forName("fixture.camera.Tracked");
            List<String> order;
            try (var input = CameraMixinRuntimeProbe.class.getClassLoader().getResourceAsStream("camera-order.txt")) { order = Arrays.asList(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(",")); }
            int cases = 0;
            for (boolean active : List.of(false, true)) for (boolean tracker : List.of(false, true)) for (int guest : new int[]{0,1,2,3}) for (int aim : new int[]{0,1,2}) {
                CameraFixtureSupport.reset(); controller.getField("freelookToggled").setBoolean(state, active);
                CameraFixtureSupport.Entity entity = tracker ? (CameraFixtureSupport.Entity) tracked.getConstructor().newInstance() : new CameraFixtureSupport.Entity();
                if (guest != 0) { CameraFixtureSupport.GuestManager.freelook = new CameraFixtureSupport.GuestFreelook(); CameraFixtureSupport.GuestManager.freelook.enabled = guest != 1; CameraFixtureSupport.GuestFreelook.perspectiveToggled = guest == 3; }
                if (aim != 0) { CameraFixtureSupport.GuestManager.aimAssist = new CameraFixtureSupport.GuestAim(); CameraFixtureSupport.GuestManager.aimAssist.active = aim == 2; }
                Object target = renderer.getConstructor(CameraFixtureSupport.Entity.class).newInstance(entity);
                renderer.getMethod("orient", float.class).invoke(target, .5f);
                float expected = 0;
                boolean guestEnabled = !args[1].equals("native");
                for (String role : order) {
                    float value;
                    if (active && tracker) value = role.contains("Yaw") || role.equals("yaw") ? entity.nativeYaw : entity.nativePitch;
                    else if (guestEnabled && guest == 3) value = role.contains("Yaw") || role.equals("yaw") ? 303 : 404;
                    else if (guestEnabled && aim == 2) value = switch (role) { case "yaw" -> 505; case "previousYaw" -> 506; case "pitch" -> 607; default -> 608; };
                    else value = switch (role) { case "yaw" -> entity.yaw; case "previousYaw" -> entity.previousYaw; case "pitch" -> entity.pitch; default -> entity.previousPitch; };
                    expected += value;
                }
                require(Float.floatToRawIntBits(expected) == Float.floatToRawIntBits(renderer.getField("result").getFloat(target)), "Camera selection changed");
                if (guestEnabled && guest != 0) require(CameraFixtureSupport.GuestManager.freelook.checks == order.size(), "Original guest operation was skipped or duplicated");
                if (!guestEnabled && guest != 0) require(CameraFixtureSupport.GuestManager.freelook.checks == 0, "Native-only path invoked guest code");
                require(CameraFixtureSupport.nativeYawReads + CameraFixtureSupport.nativePitchReads == (active && tracker ? order.size() : 0), "Native tracker reads changed");
                cases++;
            }
            require(cases == 48, "Incomplete camera matrix");
            if (!args[1].equals("native")) {
                CameraFixtureSupport.reset(); CameraFixtureSupport.GuestManager.aimAssist = new CameraFixtureSupport.GuestAim(); RuntimeException failure = new RuntimeException("guest operation"); CameraFixtureSupport.GuestManager.aimAssist.failure = failure;
                controller.getField("freelookToggled").setBoolean(state, true); Object target = renderer.getConstructor(CameraFixtureSupport.Entity.class).newInstance(tracked.getConstructor().newInstance());
                try { renderer.getMethod("orient", float.class).invoke(target, .5f); throw new AssertionError("Guest operation failure was swallowed"); }
                catch (InvocationTargetException invocation) { require(invocation.getCause() == failure, "Guest exception identity changed"); }
            }
            Files.write(Path.of(args[2]), transformed);
            System.out.println("CAMERA_PROBE SUCCESS mode=" + args[1] + " cases=" + cases + " fields=" + order.size());
        } catch (Throwable failure) { while ((failure instanceof InvocationTargetException || failure instanceof ExceptionInInitializerError) && failure.getCause() != null) failure = failure.getCause(); failure.printStackTrace(); System.exit(1); }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    static ForgeMappings fixtureMappings() throws Exception {
        return new ForgeMappings(new MappingResolver() {
            @Override public Collection<String> getNamespaces() { return List.of("official", "intermediary"); }
            @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
            @Override public String mapClassName(String namespace, String name) { return name.equals("e") ? CameraFixtureSupport.Entity.class.getName() : name.equals("r") ? TARGET : name; }
            @Override public String unmapClassName(String namespace, String name) { return name.equals(CameraFixtureSupport.Entity.class.getName()) ? "e" : name.equals(TARGET) ? "r" : name; }
            @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
            @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
        }, new StringReader("CL: e net/minecraft/entity/Entity\nCL: r net/minecraft/client/renderer/EntityRenderer\n"
            + "FD: e/yaw net/minecraft/entity/Entity/field_70177_z\nFD: e/previousYaw net/minecraft/entity/Entity/field_70126_B\n"
            + "FD: e/pitch net/minecraft/entity/Entity/field_70125_A\nFD: e/previousPitch net/minecraft/entity/Entity/field_70127_C\n"
            + "MD: r/orient (F)V net/minecraft/client/renderer/EntityRenderer/func_78467_g (F)V\n"));
    }

    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
        @Override public String getName() { return "Camera owned JVM fixture"; }
        @Override public boolean isValid() { return true; }
        @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
        @Override public IClassProvider getClassProvider() { return this; }
        @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
        @Override public ITransformerProvider getTransformerProvider() { return this; }
        @Override public IClassTracker getClassTracker() { return this; }
        @Override public IMixinAuditTrail getAuditTrail() { return null; }
        @Override public IFeatureValidator getFeatureValidator() { return IFeatureValidator.ALLOW_ALL; }
        @Override public IAdviceProvider getAdviceProvider() { return IAdviceProvider.GENERIC; }
        @Override public Collection<String> getPlatformAgents() { return List.of(); }
        @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleURI(Path.of(System.getProperty("camera.fixture")).toUri()); }
        @Override public Collection<IContainerHandle> getMixinContainers() { return List.of(); }
        @Override public InputStream getResourceAsStream(String name) { return CameraMixinRuntimeProbe.class.getClassLoader().getResourceAsStream(name); }
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, true); }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException { return Class.forName(name, initialize, CameraMixinRuntimeProbe.class.getClassLoader()); }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
        @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true); }
        @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException { return getClassNode(name, runTransformers, 0); }
        @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags) throws ClassNotFoundException, IOException {
            try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) { if (input == null) throw new ClassNotFoundException(name); ClassNode node = new ClassNode(); new ClassReader(input.readAllBytes()).accept(node, flags); return node; }
        }
        @Override public Collection<ITransformer> getTransformers() { return List.of(); }
        @Override public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
        @Override public void addTransformerExclusion(String name) { }
        @Override public void registerInvalidClass(String name) { }
        @Override public boolean isClassLoaded(String name) { return false; }
        @Override public String getClassRestrictions(String name) { return ""; }
    }

    public static final class Properties implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey { }
        private static final Map<IPropertyKey,Object> VALUES = new HashMap<>();
        @Override public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key) { return (T) VALUES.get(key); }
        @Override public void setProperty(IPropertyKey key, Object value) { VALUES.put(key, value); }
        @SuppressWarnings("unchecked") @Override public <T> T getProperty(IPropertyKey key, T defaultValue) { return (T) VALUES.getOrDefault(key, defaultValue); }
        @Override public String getPropertyString(IPropertyKey key, String defaultValue) { Object value = VALUES.get(key); return value == null ? defaultValue : value.toString(); }
    }
}
