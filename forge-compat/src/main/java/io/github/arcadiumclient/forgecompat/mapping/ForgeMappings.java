package io.github.arcadiumclient.forgecompat.mapping;

import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeMappings {
    private record Member(String officialOwner, String officialName, String officialDescriptor,
                          String srgOwner, String srgName, String srgDescriptor) {}
    private record Key(String owner, String name, String descriptor) {}
    private record Symbol(String name, String descriptor) {}
    private record Lookup(Direction direction, String owner, String name, String descriptor, boolean method) {}
    private record FieldDeclaration(String srgName, String srgDescriptor, String officialName, String runtimeName) {}
    private enum Direction { OFFICIAL, SRG, RUNTIME }

    private final MappingResolver resolver;
    private final String srgIdentity;
    private String cachedIdentity;
    private final Map<String, String> officialToSrg = new HashMap<>();
    private final Map<String, String> srgToOfficial = new HashMap<>();
    private final Map<Key, Member> srgMethods = new HashMap<>();
    private volatile Map<Key, List<Member>> runtimeMethods = Map.of();
    private final Map<Symbol, List<Member>> methodsBySrgSymbol = new HashMap<>();
    private final Map<String, List<Member>> methodsBySrgName = new HashMap<>();
    private volatile Map<Symbol, List<Member>> methodsByRuntimeSymbol = Map.of();
    private final Map<String, List<Member>> methodsByOfficialOwner = new HashMap<>();
    private final Map<String, List<Member>> fieldsBySrgOwner = new HashMap<>();
    private final Map<String, List<Member>> fieldsBySrgName = new HashMap<>();
    private final List<Member> fields = new ArrayList<>();
    private final Map<String, List<String>> hierarchy = new ConcurrentHashMap<>();
    private final Map<String, List<String>> mixinTargets = new ConcurrentHashMap<>();
    private final Map<String, String> officialRuntimeClasses = new ConcurrentHashMap<>();
    private final Map<String, String> runtimeOfficialClasses = new ConcurrentHashMap<>();
    private final Map<Lookup, String> memberCache = new ConcurrentHashMap<>();
    private final Map<Member, String> runtimeMethodNames = new ConcurrentHashMap<>();
    private final Map<Member, String> declaredMethodNames = new ConcurrentHashMap<>();
    private final Map<Member, String> declaredFieldNames = new ConcurrentHashMap<>();
    private final Map<String, Map<Key, FieldDeclaration>> fieldDeclarations = new ConcurrentHashMap<>();
    private volatile boolean reverseMethodsDirty = true;
    private final Map<String, Map<String, List<Member>>> reverseFieldsByDescriptor = new ConcurrentHashMap<>();
    private final Remapper officialRemapper = new NamespaceRemapper(Direction.OFFICIAL);
    private final Remapper srgRemapper = new NamespaceRemapper(Direction.SRG);
    private final Remapper runtimeRemapper = new NamespaceRemapper(Direction.RUNTIME);
    private final Remapper srgOfficialTypes = new Remapper(Opcodes.ASM9) {
        @Override public String map(String name) { return srgToOfficial.getOrDefault(name, name); }
    };
    private final Remapper officialSrgTypes = new Remapper(Opcodes.ASM9) {
        @Override public String map(String name) { return officialToSrg.getOrDefault(name, name); }
    };
    private final Remapper runtimeOfficialTypes = new Remapper(Opcodes.ASM9) {
        @Override public String map(String name) { return officialClassFromRuntime(name); }
    };

    public ForgeMappings(MappingResolver resolver, Reader srg) throws IOException {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        if (!resolver.getNamespaces().contains("official")) {
            throw new IllegalArgumentException("The host MappingResolver has no official namespace");
        }
        List<String[]> pending = new ArrayList<>();
        MessageDigest sourceHash = sha256();
        BufferedReader input = srg instanceof BufferedReader buffered ? buffered : new BufferedReader(srg);
        String line;
        int lineNumber = 0;
        while ((line = input.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            hashText(sourceHash, line);
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "PK:" -> {
                    if (parts.length != 3) throw malformed(lineNumber);
                }
                case "CL:" -> {
                    if (parts.length != 3) throw malformed(lineNumber);
                    putClass(officialToSrg, parts[1], parts[2]);
                    putClass(srgToOfficial, parts[2], parts[1]);
                }
                case "FD:" -> {
                    if (parts.length != 3) throw malformed(lineNumber);
                    pending.add(parts);
                }
                case "MD:" -> {
                    if (parts.length != 5) throw malformed(lineNumber);
                    Type.getMethodType(parts[2]);
                    Type.getMethodType(parts[4]);
                    pending.add(parts);
                }
                default -> throw malformed(lineNumber);
            }
        }
        srgIdentity = HexFormat.of().formatHex(sourceHash.digest());
        if (officialToSrg.isEmpty()) throw new IllegalArgumentException("SRG input contains no classes");
        for (String[] parts : pending) {
            boolean method = parts[0].equals("MD:");
            String[] source = splitMember(parts[1]);
            String[] target = splitMember(parts[method ? 3 : 2]);
            Member member = new Member(source[0], source[1], method ? parts[2] : null,
                target[0], target[1], method ? parts[4] : null);
            if (method) {
                Member previous = srgMethods.putIfAbsent(new Key(member.srgOwner, member.srgName, member.srgDescriptor), member);
                if (previous != null && !previous.equals(member)) throw new IllegalArgumentException("Conflicting SRG method mapping");
                add(methodsBySrgSymbol, new Symbol(member.srgName, member.srgDescriptor), member);
                add(methodsBySrgName, member.srgName, member);
                add(methodsByOfficialOwner, member.officialOwner, member);
            } else {
                fields.add(member);
                add(fieldsBySrgOwner, member.srgOwner, member);
                add(fieldsBySrgName, member.srgName, member);
            }
        }
    }

    public Remapper officialToRuntime() { return officialRemapper; }
    public Remapper srgToRuntime() { return srgRemapper; }
    public Remapper runtimeToSrg() { return runtimeRemapper; }
    public String runtimeNamespace() { return resolver.getCurrentRuntimeNamespace(); }

    public synchronized String identity() {
        if (cachedIdentity != null) return cachedIdentity;
        MessageDigest hash = sha256();
        hashText(hash, "forge-mappings-v1");
        hashText(hash, runtimeNamespace());
        hashText(hash, srgIdentity);
        officialToSrg.keySet().stream().sorted().forEach(name -> hashText(hash, name + "=" + runtimeClassFromOfficial(name)));
        srgMethods.values().stream().sorted(java.util.Comparator.comparing(member -> member.srgOwner + "/" + member.srgName + member.srgDescriptor))
            .forEach(member -> hashText(hash, member.srgOwner + "/" + member.srgName + member.srgDescriptor + "=" + runtimeMethod(member)));
        fields.stream().sorted(java.util.Comparator.comparing(member -> member.srgOwner + "/" + member.srgName))
            .forEach(member -> hashText(hash, member.srgOwner + "/" + member.srgName + "=" + runtimeField(member, null)));
        hierarchy.keySet().stream().sorted().forEach(name -> hashText(hash, "hierarchy:" + name + "=" + hierarchy.get(name)));
        mixinTargets.keySet().stream().sorted().forEach(name -> hashText(hash, "mixin:" + name + "=" + mixinTargets.get(name)));
        fieldDeclarations.keySet().stream().sorted().forEach(owner -> fieldDeclarations.get(owner).entrySet().stream()
            .sorted(java.util.Comparator.comparing(entry -> entry.getKey().name + entry.getKey().descriptor))
            .forEach(entry -> hashText(hash, "field:" + owner + ":" + entry.getValue())));
        cachedIdentity = HexFormat.of().formatHex(hash.digest());
        return cachedIdentity;
    }

    public String classFromSrg(String internalName) {
        return runtimeClassFromOfficial(srgToOfficial.getOrDefault(internalName, internalName));
    }

    public String classToSrg(String internalName) {
        String official = officialClassFromRuntime(internalName);
        return officialToSrg.getOrDefault(official, official);
    }

    public String descriptorFromSrg(String descriptor) {
        return descriptor.startsWith("(") ? srgRemapper.mapMethodDesc(descriptor) : srgRemapper.mapDesc(descriptor);
    }

    public String descriptorToSrg(String descriptor) {
        return descriptor.startsWith("(") ? runtimeRemapper.mapMethodDesc(descriptor) : runtimeRemapper.mapDesc(descriptor);
    }

    public String descriptorToOfficial(String descriptor) {
        return descriptor(runtimeOfficialTypes, descriptor);
    }

    public String fieldFromSrg(String owner, String name, String descriptor) {
        return mapMember(Direction.SRG, owner, name, descriptor, false);
    }

    public String methodFromSrg(String owner, String name, String descriptor) {
        return mapMember(Direction.SRG, owner, name, descriptor, true);
    }

    public String reflectionClassFromSrg(String name) {
        if (name.startsWith("[")) return srgRemapper.mapDesc(name.replace('.', '/')).replace('/', '.');
        boolean dotted = name.indexOf('.') >= 0;
        String mapped = classFromSrg(name.replace('.', '/'));
        return dotted ? mapped.replace('/', '.') : mapped;
    }

    public String mixinSelectorFromSrg(String selector) {
        String owner = null;
        String member = selector;
        if (qualifiedSelector(selector)) {
            int separator = selector.indexOf(';');
            owner = selector.substring(1, separator);
            member = selector.substring(separator + 1);
        } else if (selector.startsWith("L") && selector.indexOf('/') >= 0
            && selector.indexOf('(') < 0 && selector.indexOf(':') < 0) {
            throw new IllegalArgumentException("Malformed Mixin owner selector");
        }
        int method = member.indexOf('(');
        int field = member.indexOf(':');
        if (method >= 0) {
            String name = member.substring(0, method);
            String descriptor = member.substring(method);
            String mapped = owner == null ? globalMethod(Direction.SRG, name, descriptor) : methodFromSrg(owner, name, descriptor);
            return (owner == null ? "" : "L" + classFromSrg(owner) + ";") + mapped + srgRemapper.mapMethodDesc(descriptor);
        }
        if (field >= 0) {
            String name = member.substring(0, field);
            String descriptor = member.substring(field + 1);
            String mapped = owner == null ? globalField(Direction.SRG, name, descriptor) : fieldFromSrg(owner, name, descriptor);
            return (owner == null ? "" : "L" + classFromSrg(owner) + ";") + mapped + ":" + srgRemapper.mapDesc(descriptor);
        }
        if (owner != null && member.isEmpty()) return "L" + classFromSrg(owner) + ";";
        String mapped = bareMethodFromSrg(owner, member);
        return (owner == null ? "" : "L" + classFromSrg(owner) + ";") + mapped;
    }

    public String mixinSelectorFromSrg(String targetOwner, String selector) {
        if (qualifiedSelector(selector)) return mixinSelectorFromSrg(selector);
        String qualified = mixinSelectorFromSrg("L" + targetOwner + ";" + selector);
        return qualified.substring(qualified.indexOf(';') + 1);
    }

    private static boolean qualifiedSelector(String selector) {
        if (!selector.startsWith("L")) return false;
        int separator = selector.indexOf(';');
        int method = selector.indexOf('('), field = selector.indexOf(':');
        return separator >= 0 && (method < 0 || separator < method) && (field < 0 || separator < field);
    }

    private String bareMethodFromSrg(String owner, String name) {
        if (owner != null) {
            for (List<String> level : hierarchyLevels(owner)) {
                Set<String> matches = new LinkedHashSet<>();
                for (Member member : methodsBySrgName.getOrDefault(name, List.of())) {
                    if (member.srgName.equals(name) && level.contains(member.srgOwner)) matches.add(runtimeMethod(member));
                }
                if (!matches.isEmpty()) return unique(matches, name);
            }
        }
        if (!name.startsWith("func_")) return name;
        Set<String> matches = new LinkedHashSet<>();
        for (Member member : methodsBySrgName.getOrDefault(name, List.of())) {
            if (member.srgName.equals(name)) matches.add(runtimeMethod(member));
        }
        return matches.isEmpty() ? name : unique(matches, name);
    }

    public synchronized void indexSrgHierarchy(String name, String superName, Collection<String> interfaces) {
        List<String> parents = new ArrayList<>();
        if (superName != null) parents.add(superName);
        parents.addAll(interfaces);
        if (!List.copyOf(parents).equals(hierarchy.put(name, List.copyOf(parents)))) {
            invalidateMembers();
        }
    }

    public void indexOfficialHierarchy(String name, String superName, Collection<String> interfaces) {
        indexSrgHierarchy(officialToSrg.getOrDefault(name, name),
            superName == null ? null : officialToSrg.getOrDefault(superName, superName),
            interfaces.stream().map(parent -> officialToSrg.getOrDefault(parent, parent)).toList());
    }

    public void indexRuntimeHierarchy(String name, String superName, Collection<String> interfaces) {
        indexSrgHierarchy(classToSrg(name), superName == null ? null : classToSrg(superName),
            interfaces.stream().map(this::classToSrg).toList());
    }

    public synchronized void indexOfficialMethod(String owner, String name, String descriptor) {
        String srgOwner = officialToSrg.getOrDefault(owner, owner);
        Member member = srgMethods.get(new Key(srgOwner, name, officialSrgTypes.mapMethodDesc(descriptor)));
        if (member == null) member = methodsByOfficialOwner.getOrDefault(owner, List.of()).stream()
            .filter(value -> value.officialName.equals(name) && value.officialDescriptor.equals(descriptor)).findFirst().orElse(null);
        if (member != null && !name.equals(declaredMethodNames.put(member, name))) invalidateMembers();
    }

    public synchronized void indexOfficialField(String owner, String name, String descriptor) {
        String srgOwner = officialToSrg.getOrDefault(owner, owner);
        String srgName = name;
        for (Member member : fieldsBySrgOwner.getOrDefault(srgOwner, List.of())) {
            if (member.officialName.equals(name) || member.srgName.equals(name)) {
                srgName = member.srgName;
                if (!name.equals(declaredFieldNames.put(member, name))) invalidateMembers();
                break;
            }
        }
        String srgDescriptor = officialSrgTypes.mapDesc(descriptor);
        Key key = new Key(srgOwner, srgName, srgDescriptor);
        FieldDeclaration declaration = new FieldDeclaration(srgName, srgDescriptor, name,
            resolver.mapFieldName("official", owner.replace('/', '.'), name, descriptor));
        Map<Key, FieldDeclaration> declarations = new HashMap<>(fieldDeclarations.getOrDefault(srgOwner, Map.of()));
        if (!declaration.equals(declarations.put(key, declaration))) {
            fieldDeclarations.put(srgOwner, Map.copyOf(declarations));
            invalidateMembers();
        }
    }

    public synchronized void indexSrgMixinTargets(String mixinClass, Collection<String> targets) {
        List<String> next = List.copyOf(targets);
        if (!next.equals(mixinTargets.put(mixinClass, next))) {
            invalidateMembers();
        }
    }

    private void invalidateMembers() {
        memberCache.clear();
        runtimeMethodNames.clear();
        reverseFieldsByDescriptor.clear();
        reverseMethodsDirty = true;
        cachedIdentity = null;
    }

    private String runtimeClassFromOfficial(String name) {
        if (name == null) return null;
        return officialRuntimeClasses.computeIfAbsent(name,
            key -> resolver.mapClassName("official", key.replace('/', '.')).replace('.', '/'));
    }

    private String officialClassFromRuntime(String name) {
        if (name == null) return null;
        return runtimeOfficialClasses.computeIfAbsent(name,
            key -> resolver.unmapClassName("official", key.replace('/', '.')).replace('.', '/'));
    }

    private String runtimeMethod(Member member) {
        return runtimeMethodNames.computeIfAbsent(member, value -> {
            String source = declaredMethodNames.getOrDefault(value, value.officialName);
            String direct = resolver.mapMethodName("official", value.officialOwner.replace('/', '.'), source, value.officialDescriptor);
            if (!direct.equals(source) || !source.equals(value.officialName)) return direct;
            List<List<String>> levels = hierarchyLevels(value.srgOwner);
            for (int i = 1; i < levels.size(); i++) {
                Set<String> matches = new LinkedHashSet<>();
                for (String parent : levels.get(i)) {
                    Member inherited = srgMethods.get(new Key(parent, value.srgName, value.srgDescriptor));
                    if (inherited == null) continue;
                    String inheritedSource = declaredMethodNames.getOrDefault(inherited, inherited.officialName);
                    String mapped = resolver.mapMethodName("official", inherited.officialOwner.replace('/', '.'), inheritedSource, inherited.officialDescriptor);
                    if (!mapped.equals(inheritedSource) || !inheritedSource.equals(inherited.officialName)) matches.add(mapped);
                }
                if (!matches.isEmpty()) return unique(matches, value.srgOwner + "." + value.srgName + value.srgDescriptor);
            }
            return direct;
        });
    }

    private String runtimeField(Member member, String officialDescriptor) {
        return resolver.mapFieldName("official", member.officialOwner.replace('/', '.'),
            declaredFieldNames.getOrDefault(member, member.officialName), officialDescriptor);
    }

    private synchronized void ensureReverseMethods() {
        if (!reverseMethodsDirty) return;
        Map<Key, List<Member>> byOwner = new HashMap<>();
        Map<Symbol, List<Member>> bySymbol = new HashMap<>();
        for (Member member : srgMethods.values()) {
            String name = runtimeMethod(member);
            String descriptor = officialRemapper.mapMethodDesc(member.officialDescriptor);
            add(byOwner, new Key(runtimeClassFromOfficial(member.officialOwner), name, descriptor), member);
            add(bySymbol, new Symbol(name, descriptor), member);
        }
        byOwner.replaceAll((key, value) -> List.copyOf(value));
        bySymbol.replaceAll((key, value) -> List.copyOf(value));
        runtimeMethods = Map.copyOf(byOwner);
        methodsByRuntimeSymbol = Map.copyOf(bySymbol);
        reverseMethodsDirty = false;
    }

    private String mapMember(Direction direction, String owner, String name, String descriptor, boolean method) {
        if (name.startsWith("<")) return name;
        Lookup lookup = new Lookup(direction, owner, name, descriptor, method);
        return memberCache.computeIfAbsent(lookup, this::resolveMember);
    }

    private String resolveMember(Lookup lookup) {
        Direction direction = lookup.direction;
        if (direction == Direction.RUNTIME && lookup.method) ensureReverseMethods();
        String srgOwner = switch (direction) {
            case SRG -> lookup.owner;
            case OFFICIAL -> officialToSrg.getOrDefault(lookup.owner, lookup.owner);
            case RUNTIME -> classToSrg(lookup.owner);
        };
        String sourceSrgDescriptor = switch (direction) {
            case SRG -> lookup.descriptor;
            case RUNTIME -> descriptorToSrg(lookup.descriptor);
            case OFFICIAL -> descriptor(officialSrgTypes, lookup.descriptor);
        };
        String officialDescriptor = direction == Direction.RUNTIME
            ? descriptor( runtimeOfficialTypes, lookup.descriptor)
            : direction == Direction.SRG ? descriptor(srgOfficialTypes, lookup.descriptor) : lookup.descriptor;
        for (List<String> level : hierarchyLevels(srgOwner)) {
            Set<String> matches = new LinkedHashSet<>();
            for (String owner : level) {
                if (lookup.method) {
                    if (direction == Direction.SRG) {
                        Member member = srgMethods.get(new Key(owner, lookup.name, sourceSrgDescriptor));
                        if (member != null) matches.add(runtimeMethod(member));
                    } else if (direction == Direction.RUNTIME) {
                        for (Member member : runtimeMethods.getOrDefault(new Key(classFromSrg(owner), lookup.name, lookup.descriptor), List.of())) {
                            matches.add(member.srgName);
                        }
                    } else {
                        String officialOwner = srgToOfficial.getOrDefault(owner, owner);
                        for (Member member : methodsByOfficialOwner.getOrDefault(officialOwner, List.of())) {
                            if (member.officialName.equals(lookup.name) && member.officialDescriptor.equals(lookup.descriptor)) {
                                matches.add(runtimeMethod(member));
                            }
                        }
                    }
                } else {
                    boolean declaredMatch = false;
                    for (FieldDeclaration declaration : fieldDeclarations.getOrDefault(owner, Map.of()).values()) {
                        String sourceName = switch (direction) {
                            case SRG -> declaration.srgName;
                            case OFFICIAL -> declaration.officialName;
                            case RUNTIME -> declaration.runtimeName;
                        };
                        if (sourceName.equals(lookup.name) && declaration.srgDescriptor.equals(sourceSrgDescriptor)) {
                            matches.add(direction == Direction.RUNTIME ? declaration.srgName : declaration.runtimeName);
                            declaredMatch = true;
                        }
                    }
                    if (declaredMatch) continue;
                    for (Member member : fieldsBySrgOwner.getOrDefault(owner, List.of())) {
                        String sourceName = switch (direction) {
                            case SRG -> member.srgName;
                            case OFFICIAL -> member.officialName;
                            case RUNTIME -> runtimeField(member, officialDescriptor);
                        };
                        if (sourceName.equals(lookup.name)) matches.add(direction == Direction.RUNTIME
                            ? member.srgName : runtimeField(member, officialDescriptor));
                    }
                }
            }
            if (!matches.isEmpty()) return unique(matches, lookup.owner + "." + lookup.name + lookup.descriptor);
        }
        if (direction == Direction.OFFICIAL) {
            return lookup.method ? resolver.mapMethodName("official", lookup.owner.replace('/', '.'), lookup.name, lookup.descriptor)
                : resolver.mapFieldName("official", lookup.owner.replace('/', '.'), lookup.name, lookup.descriptor);
        }
        if (direction == Direction.RUNTIME && hierarchyLevels(srgOwner).stream()
            .flatMap(Collection::stream).noneMatch(srgToOfficial::containsKey)) return lookup.name;
        return lookup.method ? globalMethod(direction, lookup.name, lookup.descriptor) : globalField(direction, lookup.name, lookup.descriptor);
    }

    private String globalMethod(Direction direction, String name, String descriptor) {
        if (direction == Direction.SRG && !name.startsWith("func_")) return name;
        if (direction == Direction.RUNTIME) ensureReverseMethods();
        List<Member> candidates = direction == Direction.SRG
            ? methodsBySrgSymbol.getOrDefault(new Symbol(name, descriptor), List.of())
            : methodsByRuntimeSymbol.getOrDefault(new Symbol(name, descriptor), List.of());
        Set<String> matches = new LinkedHashSet<>();
        for (Member member : candidates) {
            if (direction == Direction.RUNTIME && runtimeMethod(member).equals(member.officialName)) continue;
            matches.add(direction == Direction.RUNTIME ? member.srgName : runtimeMethod(member));
        }
        return matches.isEmpty() ? name : unique(matches, name + descriptor);
    }

    private String globalField(Direction direction, String name, String descriptor) {
        if (direction == Direction.SRG && !name.startsWith("field_")) return name;
        List<Member> candidates;
        String officialDescriptor = direction == Direction.RUNTIME ? runtimeOfficialTypes.mapDesc(descriptor) : srgOfficialTypes.mapDesc(descriptor);
        if (direction == Direction.SRG) {
            candidates = fieldsBySrgName.getOrDefault(name, List.of());
        } else {
            Map<String, List<Member>> reverse = reverseFieldsByDescriptor.computeIfAbsent(descriptor, key -> {
                Map<String, List<Member>> names = new HashMap<>();
                for (Member member : fields) add(names, runtimeField(member, officialDescriptor), member);
                return names;
            });
            candidates = reverse.getOrDefault(name, List.of());
        }
        Set<String> matches = new LinkedHashSet<>();
        for (Member member : candidates) {
            if (direction == Direction.RUNTIME && runtimeField(member, officialDescriptor).equals(member.officialName)) continue;
            matches.add(direction == Direction.RUNTIME ? member.srgName : runtimeField(member, officialDescriptor));
        }
        return matches.isEmpty() ? name : unique(matches, name + ":" + descriptor);
    }

    private List<List<String>> hierarchyLevels(String owner) {
        List<List<String>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> current = List.of(owner);
        while (!current.isEmpty()) {
            List<String> level = current.stream().filter(seen::add).toList();
            if (level.isEmpty()) break;
            result.add(level);
            List<String> next = new ArrayList<>();
            for (String entry : level) next.addAll(mixinTargets.getOrDefault(entry, hierarchy.getOrDefault(entry, List.of())));
            current = next;
        }
        return result;
    }

    private static String descriptor(Remapper remapper, String value) {
        return value.startsWith("(") ? remapper.mapMethodDesc(value) : remapper.mapDesc(value);
    }

    private static String unique(Set<String> names, String source) {
        if (names.size() != 1) throw new IllegalArgumentException("Ambiguous inherited member mapping: " + source);
        return names.iterator().next();
    }

    private static <K> void add(Map<K, List<Member>> index, K key, Member member) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(member);
    }

    private static void putClass(Map<String, String> map, String source, String target) {
        String previous = map.putIfAbsent(source, target);
        if (previous != null && !previous.equals(target)) throw new IllegalArgumentException("Conflicting SRG class mapping");
    }

    private static String[] splitMember(String value) {
        int separator = value.lastIndexOf('/');
        if (separator <= 0 || separator == value.length() - 1) throw new IllegalArgumentException("Invalid SRG member name");
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }

    private static IllegalArgumentException malformed(int line) {
        return new IllegalArgumentException("Malformed SRG mapping at line " + line);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }

    private static void hashText(MessageDigest hash, String value) {
        hash.update(value.getBytes(StandardCharsets.UTF_8));
        hash.update((byte) 0);
    }

    private final class NamespaceRemapper extends Remapper {
        private final Direction direction;
        private NamespaceRemapper(Direction direction) { super(Opcodes.ASM9); this.direction = direction; }
        @Override public String map(String name) {
            return switch (direction) {
                case OFFICIAL -> runtimeClassFromOfficial(name);
                case SRG -> classFromSrg(name);
                case RUNTIME -> classToSrg(name);
            };
        }
        @Override public String mapFieldName(String owner, String name, String descriptor) {
            return mapMember(direction, owner, name, descriptor, false);
        }
        @Override public String mapMethodName(String owner, String name, String descriptor) {
            return mapMember(direction, owner, name, descriptor, true);
        }
    }
}
