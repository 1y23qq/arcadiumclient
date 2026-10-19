package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class GuestMixinVisibility implements MixinAnnotationAdjuster {
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
    private static boolean installed;
    private final FabricLauncher launcher;
    private final Set<String> mixins;

    public static synchronized void install(FabricLauncher launcher, Collection<Path> guestJars) throws IOException {
        if (installed) return;
        MixinAnnotationAdjusterRegistrar.register(new GuestMixinVisibility(launcher, guestMixins(guestJars)));
        installed = true;
    }

    GuestMixinVisibility(FabricLauncher launcher, Set<String> mixins) {
        this.launcher = Objects.requireNonNull(launcher);
        this.mixins = Set.copyOf(mixins.stream().map(name -> name.replace('/', '.')).toList());
    }

    @Override
    public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode method, AdjustableAnnotationNode annotation) {
        if (!mixins.contains(mixin.replace('/', '.')) || !annotation.desc.equals(OVERWRITE)
                || (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE)) != 0) return annotation;
        boolean widen = false;
        for (String target : targets) {
            String internal = target.replace('.', '/');
            if (!internal.startsWith("net/minecraft/") && !internal.startsWith("net/minecraftforge/")) continue;
            byte[] bytes;
            try { bytes = launcher.getClassByteArray(internal.replace('/', '.'), true); }
            catch (IOException exception) { throw unavailable(mixin, internal, exception); }
            if (bytes == null) continue;
            ClassNode node = new ClassNode();
            try { new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
            catch (RuntimeException exception) { throw unavailable(mixin, internal, exception); }
            if (!node.name.equals(internal)) throw unavailable(mixin, internal, new IllegalStateException("Unexpected target class identity: " + node.name));
            for (MethodNode targetMethod : node.methods) {
                if (targetMethod.name.equals(method.name) && targetMethod.desc.equals(method.desc)
                        && (targetMethod.access & Opcodes.ACC_PUBLIC) != 0) widen = true;
            }
        }
        // Private overwrites retain Mixin's invocation-upgrade path; unmatched targets retain its normal validation.
        if (widen) method.access = (method.access & ~VISIBILITY) | Opcodes.ACC_PUBLIC;
        return annotation;
    }

    static Set<String> guestMixins(Collection<Path> jars) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Path jar : jars) try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (var input = zip.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException exception) {
                    throw new IOException("Cannot inspect converted guest Mixin class " + entry.getName() + ".", exception);
                }
                String annotation = "Lorg/spongepowered/asm/mixin/Mixin;";
                if ((node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(value -> value.desc.equals(annotation)))
                        || (node.invisibleAnnotations != null && node.invisibleAnnotations.stream().anyMatch(value -> value.desc.equals(annotation)))) {
                    names.add(node.name.replace('/', '.'));
                }
            }
        }
        return names;
    }

    private static IllegalStateException unavailable(String mixin, String target, Exception cause) {
        return new IllegalStateException("Cannot inspect the pre-Mixin target " + target + " for overwrite visibility in " + mixin + ".", cause);
    }
}
