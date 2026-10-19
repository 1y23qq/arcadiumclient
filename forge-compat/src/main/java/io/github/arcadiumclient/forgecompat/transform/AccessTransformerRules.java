package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AccessTransformerRules {
    private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;
    private static final int MAX_RULES = 65_536;
    private static final int MAX_LINE_LENGTH = 16 * 1024;

    private enum Kind { CLASS, FIELD, METHOD }
    private record Access(int visibility, int finalChange) {}
    private record Rule(Kind kind, String srgOwner, String name, String descriptor,
                        Access access, ForgeMappings mappings) {}
    private record MethodKey(String name, String descriptor) {}
    private record State(Map<String, List<Rule>> rules, Set<String> targets, int count) {}

    private volatile State state = new State(Map.of(), Set.of(), 0);

    public synchronized void read(Path jar, List<String> configs, ForgeMappings mappings) throws IOException {
        Objects.requireNonNull(mappings, "mappings");
        if (configs.size() > 128) throw new IOException("Too many access transformer configurations in " + jar.getFileName());
        if (configs.isEmpty()) return;
        if (Files.size(jar) > 512L * 1024 * 1024) {
            throw new IOException("Access transformer archive exceeds the 512 MiB limit: " + jar.getFileName());
        }
        Map<String, List<Rule>> collected = new LinkedHashMap<>();
        Set<String> targets = new HashSet<>(state.targets);
        Set<String> seen = new HashSet<>();
        int count = state.count;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.size() > 100_000) throw new IOException("Too many archive entries in " + jar.getFileName());
            for (String config : configs) {
                validateConfig(config, jar);
                String entryName = config.startsWith("META-INF/") ? config : "META-INF/" + config;
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null && !config.startsWith("META-INF/")) {
                    entryName = config;
                    entry = zip.getEntry(entryName);
                }
                if (entry == null || entry.isDirectory()) {
                    throw error(jar, config, 0, "The declared configuration is missing from the archive");
                }
                if (!seen.add(entryName)) continue;
                final String selected = entryName;
                if (zip.stream().filter(candidate -> candidate.getName().equals(selected)).limit(2).count() != 1) {
                    throw error(jar, config, 0, "The archive contains duplicate configuration entries");
                }
                if (entry.getSize() > MAX_CONFIG_BYTES) {
                    throw error(jar, config, 0, "The configuration exceeds the 1 MiB limit");
                }
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) {
                    bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
                }
                if (bytes.length > MAX_CONFIG_BYTES) {
                    throw error(jar, config, 0, "The configuration exceeds the 1 MiB limit");
                }
                CRC32 crc = new CRC32();
                crc.update(bytes);
                if (entry.getSize() != bytes.length || entry.getCrc() != crc.getValue()) {
                    throw error(jar, config, 0, "The configuration is truncated or has an invalid ZIP checksum");
                }
                String text;
                try {
                    text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                } catch (CharacterCodingException cause) {
                    throw error(jar, config, 0, "The configuration is not valid UTF-8");
                }
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                BufferedReader input = new BufferedReader(new StringReader(text));
                String line;
                int number = 0;
                while ((line = input.readLine()) != null) {
                    number++;
                    if (line.length() > MAX_LINE_LENGTH) {
                        throw error(jar, config, number, "The rule exceeds the 16 KiB line limit");
                    }
                    int comment = line.indexOf('#');
                    if (comment >= 0) line = line.substring(0, comment);
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (++count > MAX_RULES) throw error(jar, config, number, "Too many access transformer rules");
                    try {
                        String[] parts = line.split("\\s+");
                        if (parts.length < 2 || parts.length > 3) {
                            throw new IllegalArgumentException("Expected an access modifier, class, and optional member");
                        }
                        Access access = parseAccess(parts[0]);
                        String owner = parts[1].replace('.', '/');
                        if (!className(owner)) throw new IllegalArgumentException("Invalid class name or unsupported class wildcard");
                        String runtimeOwner = mappings.classFromSrg(owner);
                        Rule rule = parseRule(owner, parts.length == 3 ? parts[2] : null, access, mappings);
                        collected.computeIfAbsent(runtimeOwner, ignored -> new ArrayList<>()).add(rule);
                        targets.add(runtimeOwner);
                        if (rule.kind == Kind.CLASS) {
                            int separator = owner.lastIndexOf('$');
                            while (separator > 0) {
                                targets.add(mappings.classFromSrg(owner.substring(0, separator)));
                                separator = owner.lastIndexOf('$', separator - 1);
                            }
                        }
                    } catch (IllegalArgumentException cause) {
                        throw error(jar, config, number, cause.getMessage());
                    }
                }
            }
        }
        Map<String, List<Rule>> combined = new HashMap<>(state.rules);
        collected.forEach((owner, rules) -> {
            List<Rule> merged = new ArrayList<>(combined.getOrDefault(owner, List.of()));
            merged.addAll(rules);
            combined.put(owner, List.copyOf(merged));
        });
        state = new State(Map.copyOf(combined), Set.copyOf(targets), count);
    }

    public boolean targets(String runtimeInternalClass) {
        return state.targets.contains(runtimeInternalClass);
    }

    public void apply(ClassNode node) {
        State current = state;
        Set<MethodKey> widened = new HashSet<>();
        for (Rule rule : current.rules.getOrDefault(node.name, List.of())) {
            switch (rule.kind) {
                case CLASS -> node.access = fixedAccess(node.access, rule.access);
                case FIELD -> {
                    for (var field : node.fields) {
                        if (rule.name.equals("*") || field.name.equals(rule.mappings.fieldFromSrg(
                                rule.srgOwner, rule.name, rule.mappings.runtimeToSrg().mapDesc(field.desc)))) {
                            field.access = fixedAccess(field.access, rule.access);
                        }
                    }
                }
                case METHOD -> {
                    for (MethodNode method : node.methods) {
                        if (!rule.name.equals("*")
                                && (!rule.name.equals(method.name) || !rule.descriptor.equals(method.desc))) continue;
                        int original = method.access;
                        method.access = fixedAccess(original, rule.access);
                        if ((original & Opcodes.ACC_PRIVATE) != 0
                                && (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0
                                && !method.name.startsWith("<")) {
                            widened.add(new MethodKey(method.name, method.desc));
                        }
                    }
                }
            }
        }
        for (var inner : node.innerClasses) {
            for (Rule rule : current.rules.getOrDefault(inner.name, List.of())) {
                if (rule.kind == Kind.CLASS) inner.access = fixedAccess(inner.access, rule.access);
            }
        }
        if (!widened.isEmpty()) {
            boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
            for (MethodNode method : node.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode invocation
                            && invocation.getOpcode() == Opcodes.INVOKESPECIAL
                            && invocation.owner.equals(node.name)
                            && widened.contains(new MethodKey(invocation.name, invocation.desc))) {
                        invocation.setOpcode(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL);
                        invocation.itf = isInterface;
                    }
                }
            }
        }
    }

    private static Rule parseRule(String owner, String member, Access access, ForgeMappings mappings) {
        if (member == null) return new Rule(Kind.CLASS, owner, "", "", access, mappings);
        int parenthesis = member.indexOf('(');
        if (parenthesis < 0) {
            if (!memberName(member, false)) throw new IllegalArgumentException("Invalid field name or unsupported field wildcard");
            return new Rule(Kind.FIELD, owner, member, "", access, mappings);
        }
        String name = member.substring(0, parenthesis);
        String descriptor = member.substring(parenthesis).replace('.', '/');
        if (!memberName(name, true)) throw new IllegalArgumentException("Invalid method name or unsupported method wildcard");
        if (name.equals("*") && descriptor.equals("()")) {
            return new Rule(Kind.METHOD, owner, "*", "", access, mappings);
        }
        validateMethodDescriptor(descriptor);
        if ((name.equals("<init>") && !descriptor.endsWith(")V"))
                || (name.equals("<clinit>") && !descriptor.equals("()V"))) {
            throw new IllegalArgumentException("Invalid constructor or class initializer descriptor");
        }
        return new Rule(Kind.METHOD, owner,
            name.equals("*") ? "*" : mappings.methodFromSrg(owner, name, descriptor),
            mappings.srgToRuntime().mapMethodDesc(descriptor), access, mappings);
    }

    private static Access parseAccess(String value) {
        int finalChange = 0;
        if (value.endsWith("+f") || value.endsWith("-f")) {
            finalChange = value.charAt(value.length() - 2) == '+' ? 1 : -1;
            value = value.substring(0, value.length() - 2);
        }
        int visibility = switch (value) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "default" -> 0;
            case "private" -> Opcodes.ACC_PRIVATE;
            default -> throw new IllegalArgumentException("Unknown access modifier '" + value + "'");
        };
        return new Access(visibility, finalChange);
    }

    private static int fixedAccess(int original, Access requested) {
        int visibility = original & VISIBILITY;
        int result = (original & ~VISIBILITY)
            | (rank(requested.visibility) > rank(visibility) ? requested.visibility : visibility);
        return requested.finalChange > 0 ? result | Opcodes.ACC_FINAL
            : requested.finalChange < 0 ? result & ~Opcodes.ACC_FINAL : result;
    }

    private static int rank(int visibility) {
        return switch (visibility) {
            case Opcodes.ACC_PUBLIC -> 3;
            case Opcodes.ACC_PROTECTED -> 2;
            case 0 -> 1;
            default -> 0;
        };
    }

    private static boolean memberName(String value, boolean method) {
        if (value.equals("*")) return true;
        if (method && (value.equals("<init>") || value.equals("<clinit>"))) return true;
        if (value.isEmpty()) return false;
        return value.chars().noneMatch(character -> Character.isWhitespace(character)
            || Character.isISOControl(character) || ".;[/<>*():".indexOf(character) >= 0);
    }

    private static boolean className(String value) {
        if (value.isEmpty() || value.startsWith("/") || value.endsWith("/") || value.contains("//")) return false;
        return value.chars().noneMatch(character -> Character.isWhitespace(character)
            || Character.isISOControl(character) || ".;[<>*():".indexOf(character) >= 0);
    }

    private static void validateMethodDescriptor(String descriptor) {
        int offset = 1;
        int slots = 0;
        while (offset < descriptor.length() && descriptor.charAt(offset) != ')') {
            char parameter = descriptor.charAt(offset);
            slots += parameter == 'J' || parameter == 'D' ? 2 : 1;
            if (slots > 255) throw new IllegalArgumentException("Method descriptor exceeds 255 parameter slots");
            offset = typeEnd(descriptor, offset, false);
        }
        if (offset >= descriptor.length()) throw new IllegalArgumentException("Invalid method descriptor");
        if (typeEnd(descriptor, offset + 1, true) != descriptor.length()) {
            throw new IllegalArgumentException("Invalid method descriptor suffix");
        }
    }

    private static int typeEnd(String descriptor, int offset, boolean allowVoid) {
        if (offset >= descriptor.length()) throw new IllegalArgumentException("Incomplete method descriptor");
        char type = descriptor.charAt(offset++);
        if ("BCDFIJSZ".indexOf(type) >= 0 || (allowVoid && type == 'V')) return offset;
        if (type == '[') {
            int start = offset - 1;
            while (offset < descriptor.length() && descriptor.charAt(offset) == '[') offset++;
            if (offset - start > 255) throw new IllegalArgumentException("Array descriptor exceeds 255 dimensions");
            return typeEnd(descriptor, offset, false);
        }
        if (type == 'L') {
            int end = descriptor.indexOf(';', offset);
            if (end >= 0 && className(descriptor.substring(offset, end))) return end + 1;
        }
        throw new IllegalArgumentException("Invalid type in method descriptor");
    }

    private static void validateConfig(String config, Path jar) throws IOException {
        if (config == null || config.isBlank() || config.startsWith("/") || config.contains("\\")
                || config.contains(":") || config.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid access transformer configuration path in " + jar.getFileName());
        }
        for (String part : config.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Invalid access transformer configuration path in " + jar.getFileName());
            }
        }
    }

    private static IOException error(Path jar, String config, int line, String reason) {
        return new IOException("Invalid access transformer " + jar.getFileName() + "!" + config
            + (line > 0 ? ":" + line : "") + ": " + reason);
    }
}
