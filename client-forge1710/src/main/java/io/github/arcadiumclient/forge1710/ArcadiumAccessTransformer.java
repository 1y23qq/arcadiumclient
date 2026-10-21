// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public final class ArcadiumAccessTransformer implements IClassTransformer {
    private static final JsonObject RULES = load();

    private static JsonObject load() {
        try (var input = ArcadiumAccessTransformer.class.getResourceAsStream("/arcadium-forge-access.json")) {
            if (input == null) throw new IllegalStateException("Arcadium access rules are missing.");
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        JsonObject rules = RULES.getAsJsonObject(transformedName.replace('.', '/'));
        if (rules == null) return bytes;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        if (rules.has("class")) node.access = accessible(node.access);
        if (rules.has("interfaces")) for (var iface : rules.getAsJsonArray("interfaces"))
            if (!node.interfaces.contains(iface.getAsString())) node.interfaces.add(iface.getAsString());
        if (rules.has("fields")) for (var field : node.fields)
            if (contains(rules, "fields", field.name))
                field.access = accessible(field.access);
        if (rules.has("methods")) for (var method : node.methods)
            if (contains(rules, "methods", method.name + method.desc))
                method.access = accessible(method.access);
        ClassWriter output = new ClassWriter(0);
        node.accept(output);
        return output.toByteArray();
    }

    private static boolean contains(JsonObject rules, String key, String wanted) {
        for (var value : rules.getAsJsonArray(key)) if (wanted.equals(value.getAsString())) return true;
        return false;
    }

    private static int accessible(int access) { return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC; }
}
