package io.github.arcadiumclient.forgecompat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;

public final class ForgeEventMetadata {
    private static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DEPTH = 256;

    private ForgeEventMetadata() { }

    public static boolean isEventSuperclass(ClassLoader loader, String name) throws IOException, ClassNotFoundException {
        Set<String> visited = new HashSet<>();
        String internal = name.replace('.', '/');
        while (internal != null) {
            if (!internal.matches("[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*")) throw new IOException("An event superclass has an invalid internal name.");
            if (internal.equals(EVENT)) return true;
            if (internal.equals("java/lang/Object")) return false;
            if (visited.size() >= MAX_DEPTH || !visited.add(internal)) throw new IOException("The event superclass hierarchy is cyclic or exceeds the depth limit.");
            try (var input = loader.getResourceAsStream(internal + ".class")) {
                if (input == null) throw new ClassNotFoundException(internal.replace('/', '.'));
                byte[] bytes = input.readNBytes(MAX_CLASS_BYTES + 1);
                if (bytes.length > MAX_CLASS_BYTES) throw new IOException("An event superclass exceeds the class size limit.");
                try {
                    ClassReader reader = new ClassReader(bytes);
                    if (!reader.getClassName().equals(internal)) throw new IOException("An event superclass resource has a different class name.");
                    internal = reader.getSuperName();
                } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
                    throw new IOException("An event superclass contains invalid class metadata.", failure);
                }
            }
        }
        return false;
    }
}
