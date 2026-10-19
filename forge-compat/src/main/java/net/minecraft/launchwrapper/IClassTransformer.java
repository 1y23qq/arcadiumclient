package net.minecraft.launchwrapper;

@FunctionalInterface
public interface IClassTransformer {
    byte[] transform(String name, String transformedName, byte[] basicClass);
}
