package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class JourneyMapCompatibilityTest {
    private static final String FILE_HANDLER = "journeymap/client/io/FileHandler";
    private static final String JM_LOGGER = "journeymap/client/log/JMLogger";
    private static final String RESOURCE_BRIDGE = Type.getInternalName(JourneyMapResourcesCompatibility.class);
    private static final String LOG_BRIDGE = "io/github/arcadiumclient/forgecompat/LegacyLog4j";
    private static final String LAYOUT = "org/apache/logging/log4j/core/layout/PatternLayout";
    private static final String APPENDER = "org/apache/logging/log4j/core/appender/RandomAccessFileAppender";
    private static final String LAYOUT_DESC = "(Ljava/lang/String;Lorg/apache/logging/log4j/core/config/Configuration;Lorg/apache/logging/log4j/core/pattern/RegexReplacement;Ljava/lang/String;Ljava/lang/String;)Lorg/apache/logging/log4j/core/layout/PatternLayout;";
    private static final String APPENDER_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/apache/logging/log4j/core/Layout;Lorg/apache/logging/log4j/core/Filter;Ljava/lang/String;Ljava/lang/String;Lorg/apache/logging/log4j/core/config/Configuration;)Lorg/apache/logging/log4j/core/appender/RandomAccessFileAppender;";
    private static final String OWN_FILE = "fixture/journeymap/Files";
    private static final String OWN_LOG = "fixture/journeymap/Logging";
    private static final String SUPPORT = "fixture/journeymap/Support";
    private static final String ANCHOR = "fixture/journeymap/ResourceAnchor";
    private static final String ASSETS = "assets/journeymap/icon/theme/";
    @TempDir Path temporary;

    @Test void rewritesTheTwoActualObsoleteFactoriesAndPreservesAllThemeBytesAndOtherFileMethods() throws Exception {
        Path original = journeyMap(); byte[] originalBytes = Files.readAllBytes(original);
        Map<String, byte[]> source = entries(original);
        Path transformed = new ForgeJarTransformer().transform(original, temporary.resolve("full-cache"), mappings(), false, null);
        Map<String, byte[]> result = entries(transformed);
        ClassNode logger = node(result.get(JM_LOGGER + ".class"));
        assertEquals(2, calls(method(logger, "init"), LOG_BRIDGE).size());
        assertEquals(List.of("createLayout" + LAYOUT_DESC, "createAppender" + APPENDER_DESC), calls(method(logger, "init"), LOG_BRIDGE).stream().map(call -> call.name + call.desc).toList());
        ClassNode before = node(source.get(FILE_HANDLER + ".class")), after = node(result.get(FILE_HANDLER + ".class"));
        assertEquals(3, calls(method(after, "copyResources"), RESOURCE_BRIDGE).size());
        assertEquals(method(before, "copyResources").maxLocals, method(after, "copyResources").maxLocals);
        for (String name : List.of("isInJar", "copyFromZip", "copyFromDirectory")) assertArrayEquals(methodBytes(method(before, name)), methodBytes(method(after, name)), name);
        new Analyzer<>(new BasicVerifier()).analyze(after.name, method(after, "copyResources"));
        byte[] once = bytes(after); JourneyMapResourcesCompatibility.normalize(after); assertArrayEquals(once, bytes(after));
        int assets = 0;
        for (var entry : source.entrySet()) if (entry.getKey().startsWith(ASSETS) && !entry.getKey().endsWith("/")) {
            assertArrayEquals(entry.getValue(), result.get(entry.getKey()), entry.getKey()); assets++;
        }
        assertEquals(105, assets); assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test void executesTheActualJourneyMapLoggerInitBodyAndWritesUtf8ThroughTheReal219Appender() throws Exception {
        byte[] raw = entries(journeyMap()).get(JM_LOGGER + ".class");
        Path source = jar(temporary.resolve("logging.jar"), Map.of(JM_LOGGER + ".class", raw));
        byte[] corrected = entries(new ForgeJarTransformer().transform(source, temporary.resolve("logging-cache"), mappings(), false, null)).get(JM_LOGGER + ".class");
        for (boolean fixed : List.of(false, true)) {
            Map<String, byte[]> definitions = new LinkedHashMap<>();
            definitions.put(OWN_LOG.replace('/', '.'), loggingFixture(fixed ? corrected : raw));
            definitions.put(SUPPORT.replace('/', '.'), support());
            try (RuntimeLoader loader = new RuntimeLoader(List.of(), definitions)) {
                Path directory = Files.createDirectories(temporary.resolve(fixed ? "fixed-log 日本語" : "old-log"));
                loader.loadClass(SUPPORT.replace('/', '.')).getField("directory").set(null, directory.toString());
                Object logger = logger(loader); logger.getClass().getMethod("setAdditive", boolean.class).invoke(logger, false);
                logger.getClass().getMethod("setLevel", loader.type("Level")).invoke(logger, loader.type("Level").getField("INFO").get(null));
                Class<?> fixture = loader.loadClass(OWN_LOG.replace('/', '.')); assertSame(logger, fixture.getMethod("init").invoke(null));
                var field = fixture.getDeclaredField("fileAppender"); field.setAccessible(true); Object appender = field.get(null);
                if (!fixed) { assertNull(appender); assertFalse(Files.exists(directory.resolve("journeymap.log"))); continue; }
                assertNotNull(appender); assertEquals(true, appender.getClass().getMethod("isStarted").invoke(appender));
                try {
                    loader.type("Logger").getMethod("info", String.class).invoke(logger, "Theme 日本語 + café 100%");
                    String content = Files.readString(directory.resolve("journeymap.log"), StandardCharsets.UTF_8);
                    assertTrue(content.contains("JourneyMap log initialized."), content);
                    assertTrue(content.contains("Theme 日本語 + café 100%"), content);
                } finally {
                    logger.getClass().getMethod("removeAppender", loader.type("core.Appender")).invoke(logger, appender);
                    appender.getClass().getMethod("stop").invoke(appender);
                }
            }
        }
    }

    @Test void preservesLegacyLayoutCharsetReplacementConfigurationAndDefaultTrueExceptionRules() throws Exception {
        try (RuntimeLoader loader = new RuntimeLoader(List.of(), Map.of())) {
            Object configuration = loader.type("core.config.DefaultConfiguration").getConstructor().newInstance();
            Object replacement = loader.type("core.pattern.RegexReplacement").getMethod("createRegexReplacement", Pattern.class, String.class).invoke(null, Pattern.compile("secret"), "masked");
            Object event = event(loader, "secret 日本語", new IllegalStateException("legacy-marker"));
            for (String always : Arrays.asList(null, "", "true", "TrUe", "invalid", "false", "FaLsE")) {
                Object layout = layout(loader, "%msg%n", configuration, replacement, "UTF-16LE", always);
                assertEquals(StandardCharsets.UTF_16LE, layout.getClass().getMethod("getCharset").invoke(layout));
                assertSame(configuration, layout.getClass().getMethod("getConfiguration").invoke(layout));
                String rendered = (String) layout.getClass().getMethod("toSerializable", loader.type("core.LogEvent")).invoke(layout, event);
                assertTrue(rendered.startsWith("masked 日本語"), rendered);
                assertEquals(!"false".equalsIgnoreCase(always), rendered.contains("legacy-marker"), String.valueOf(always));
                assertArrayEquals(rendered.getBytes(StandardCharsets.UTF_16LE), (byte[]) layout.getClass().getMethod("toByteArray", loader.type("core.LogEvent")).invoke(layout, event));
            }
            for (String charset : Arrays.asList(null, "not-a-real-charset")) {
                Object layout = layout(loader, null, configuration, null, charset, "false");
                assertEquals(Charset.defaultCharset(), layout.getClass().getMethod("getCharset").invoke(layout));
                String rendered = (String) layout.getClass().getMethod("toSerializable", loader.type("core.LogEvent")).invoke(layout, event);
                assertEquals("secret 日本語" + System.lineSeparator(), rendered);
            }
            InvocationTargetException invalid = assertThrows(InvocationTargetException.class, () -> layout(loader, null, null, null, "bad charset name", null));
            assertInstanceOf(java.nio.charset.IllegalCharsetNameException.class, invalid.getCause());
        }
    }

    @Test void preservesLegacyAppenderAppendFlushIgnoreDefaultsAndFileOutput() throws Exception {
        try (RuntimeLoader loader = new RuntimeLoader(List.of(), Map.of())) {
            Object configuration = loader.type("core.config.DefaultConfiguration").getConstructor().newInstance();
            Object layout = layout(loader, "%msg%n", configuration, null, "UTF-8", "false");
            for (String append : Arrays.asList(null, "invalid", "true", "false")) {
                Path output = temporary.resolve("appender-" + append + ".log"); Files.writeString(output, "existing\n");
                Object appender = appender(loader, output, append, null, null, layout, configuration);
                assertNotNull(appender); assertEquals(true, appender.getClass().getMethod("getImmediateFlush").invoke(appender));
                assertEquals(true, appender.getClass().getMethod("ignoreExceptions").invoke(appender));
                appender.getClass().getMethod("start").invoke(appender);
                try {
                    appender.getClass().getMethod("append", loader.type("core.LogEvent")).invoke(appender, event(loader, "entry 日本語", null));
                    String content = Files.readString(output); assertTrue(content.contains("entry 日本語"));
                    assertEquals(!"false".equals(append), content.startsWith("existing\n"));
                } finally { appender.getClass().getMethod("stop").invoke(appender); }
            }
            Object appender = appender(loader, temporary.resolve("explicit.log"), "true", "false", "false", layout, configuration);
            assertEquals(false, appender.getClass().getMethod("getImmediateFlush").invoke(appender));
            assertEquals(false, appender.getClass().getMethod("ignoreExceptions").invoke(appender));
            appender.getClass().getMethod("stop").invoke(appender);
        }
    }

    @Test void limitsGenericFactoryAliasesToTheExactOldStaticDescriptorsForGuestAndOfficialInputs() throws Exception {
        ClassNode fixture = new ClassNode(); fixture.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Factories", null, "java/lang/Object", null);
        for (var factory : List.of(new String[]{"layout", LAYOUT, "createLayout", LAYOUT_DESC}, new String[]{"appender", APPENDER, "createAppender", APPENDER_DESC})) {
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, factory[0], factory[3], null, null);
            int slot = 0; for (Type argument : Type.getArgumentTypes(method.desc)) { method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot); slot += argument.getSize(); }
            method.visitMethodInsn(Opcodes.INVOKESTATIC, factory[1], factory[2], factory[3], false); method.visitInsn(Opcodes.ARETURN); fixture.methods.add(method);
        }
        MethodNode untouched = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "untouched", "()V", null, null);
        untouched.visitMethodInsn(Opcodes.INVOKESTATIC, LAYOUT, "createDefaultLayout", "()L" + LAYOUT + ";", false); untouched.visitInsn(Opcodes.POP);
        untouched.visitLdcInsn(LAYOUT); untouched.visitInsn(Opcodes.POP); untouched.visitInsn(Opcodes.RETURN); fixture.methods.add(untouched);
        Path source = jar(temporary.resolve("factories.jar"), Map.of(fixture.name + ".class", bytes(fixture)));
        try (RuntimeLoader loader = new RuntimeLoader(List.of(), Map.of(fixture.name.replace('/', '.'), bytes(fixture)))) {
            Class<?> caller = loader.loadClass(fixture.name.replace('/', '.'));
            InvocationTargetException layoutFailure = assertThrows(InvocationTargetException.class, () -> caller.getMethod("layout", String.class, loader.type("core.config.Configuration"), loader.type("core.pattern.RegexReplacement"), String.class, String.class).invoke(null, "%msg", null, null, "UTF-8", "false"));
            assertInstanceOf(NoSuchMethodError.class, layoutFailure.getCause());
            InvocationTargetException appenderFailure = assertThrows(InvocationTargetException.class, () -> caller.getMethod("appender", String.class, String.class, String.class, String.class, String.class, loader.type("core.Layout"), loader.type("core.Filter"), String.class, String.class, loader.type("core.config.Configuration")).invoke(null, new Object[10]));
            assertInstanceOf(NoSuchMethodError.class, appenderFailure.getCause());
        }
        for (boolean official : List.of(false, true)) {
            byte[] result = entries(new ForgeJarTransformer().transform(source, temporary.resolve("factory-cache"), mappings(), official, null)).get(fixture.name + ".class");
            ClassNode output = node(result); assertArrayEquals(methodBytes(method(node(entries(source).get(fixture.name + ".class")), "untouched")), methodBytes(method(output, "untouched")));
            assertEquals(LOG_BRIDGE, calls(output.methods.get(0), LOG_BRIDGE).get(0).owner);
            assertEquals(LOG_BRIDGE, calls(output.methods.get(1), LOG_BRIDGE).get(0).owner);
            for (MethodNode method : output.methods) new Analyzer<>(new BasicVerifier()).analyze(output.name, method);
            try (RuntimeLoader loader = new RuntimeLoader(List.of(), Map.of(fixture.name.replace('/', '.'), result))) {
                Class<?> caller = loader.loadClass(fixture.name.replace('/', '.'));
                Object layout = caller.getMethod("layout", String.class, loader.type("core.config.Configuration"), loader.type("core.pattern.RegexReplacement"), String.class, String.class).invoke(null, "%msg", null, null, "UTF-8", "false");
                assertNotNull(layout);
            }
        }
    }

    @Test void copiesAll105ActualThemeAssetsFromAFileCodeSourceJarWithUnicodeSpacesPlusAndPercent() throws Exception {
        Map<String, byte[]> assets = themeAssets(); assertEquals(105, assets.size());
        for (boolean fixed : List.of(false, true)) {
            Map<String, byte[]> files = resourceFixture(fixed); files.putAll(assets); directories(files);
            Path archive = jar(temporary.resolve(fixed ? "テーマ + 100% fixed.jar" : "テーマ + 100% old.jar"), files);
            Path destination = Files.createDirectories(temporary.resolve(fixed ? "jar-fixed" : "jar-old"));
            try (RuntimeLoader loader = new RuntimeLoader(List.of(archive.toUri().toURL()), Map.of())) {
                Class<?> anchor = loader.loadClass(ANCHOR.replace('/', '.'));
                assertEquals("file", anchor.getProtectionDomain().getCodeSource().getLocation().getProtocol());
                URL resource = anchor.getResource("/" + ASSETS); assertEquals("jar", resource.getProtocol());
                assertEquals(archive.toString(), JourneyMapResourcesCompatibility.archivePath(resource));
                Class<?> fixture = loader.loadClass(OWN_FILE.replace('/', '.')); assertEquals(false, fixture.getMethod("isInJar").invoke(null));
                for (String name : List.of("Victorian", "Vault")) fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), name, false);
                if (!fixed) { try (var walk = Files.walk(destination)) { assertEquals(0, walk.filter(Files::isRegularFile).count()); } continue; }
                assertExtracted(assets, destination);
                Path custom = destination.resolve("Vault/Vault.theme.json"); Files.writeString(custom, "user theme");
                fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), "Vault", false);
                assertEquals("user theme", Files.readString(custom));
                fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), "Vault", true);
                assertExtracted(assets, destination);
            } finally { TrackingZipFile.closeAll(); }
        }
    }

    @Test void copiesExplodedThemesAndKeepsOverwriteRulesUsingDecodedFileUris() throws Exception {
        Map<String, byte[]> assets = themeAssets(), files = resourceFixture(true); files.putAll(assets);
        Path directory = Files.createDirectories(temporary.resolve("exploded 日本語 + 100%"));
        for (var entry : files.entrySet()) { Path path = directory.resolve(entry.getKey()); Files.createDirectories(path.getParent()); Files.write(path, entry.getValue()); }
        Path destination = Files.createDirectories(temporary.resolve("exploded-output"));
        try (RuntimeLoader loader = new RuntimeLoader(List.of(directory.toUri().toURL()), Map.of())) {
            Class<?> anchor = loader.loadClass(ANCHOR.replace('/', '.')); URL resource = anchor.getResource("/" + ASSETS);
            assertEquals("file", resource.getProtocol()); assertFalse(JourneyMapResourcesCompatibility.isArchiveResource(resource));
            assertEquals(directory.resolve(ASSETS).toString(), JourneyMapResourcesCompatibility.filePath(resource));
            Class<?> fixture = loader.loadClass(OWN_FILE.replace('/', '.'));
            for (String name : List.of("Victorian", "Vault")) fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), name, false);
            assertExtracted(assets, destination);
            Path custom = destination.resolve("Vault/Vault.theme.json"); Files.writeString(custom, "custom");
            fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), "Vault", false);
            assertEquals("custom", Files.readString(custom));
            fixture.getMethod("copyResources", File.class, String.class, String.class, boolean.class).invoke(null, destination.toFile(), "/" + ASSETS.substring(0, ASSETS.length() - 1), "Vault", true);
            assertExtracted(assets, destination);
        }
    }

    @Test void rejectsUnsupportedResourceExpressionsWithoutPartiallyChangingTheClass() throws Exception {
        byte[] source = entries(journeyMap()).get(FILE_HANDLER + ".class");
        ClassNode other = node(source); other.name = "fixture/Unrelated"; byte[] unchanged = bytes(other);
        JourneyMapResourcesCompatibility.normalize(other); assertArrayEquals(unchanged, bytes(other));
        ClassNode unsupported = node(source); MethodNode method = method(unsupported, "copyResources");
        MethodInsnNode decode = calls(method, "java/net/URLDecoder").get(0); LabelNode entry = new LabelNode();
        method.instructions.insertBefore(decode, entry); method.instructions.insertBefore(method.instructions.getFirst(), new JumpInsnNode(Opcodes.GOTO, entry));
        byte[] before = bytes(unsupported); assertThrows(IllegalStateException.class, () -> JourneyMapResourcesCompatibility.normalize(unsupported)); assertArrayEquals(before, bytes(unsupported));
        assertFalse(JourneyMapResourcesCompatibility.isArchiveResource(null));
        assertThrows(IOException.class, () -> JourneyMapResourcesCompatibility.archivePath(temporary.toUri().toURL()));
        assertThrows(IOException.class, () -> JourneyMapResourcesCompatibility.filePath(new URL("https://example.invalid/theme")));
    }

    public static final class TrackingZipFile extends ZipFile {
        private static final List<ZipFile> OPEN = new ArrayList<>();
        public TrackingZipFile(String name) throws IOException { super(name); OPEN.add(this); }
        static void closeAll() throws IOException { try { for (ZipFile file : OPEN) file.close(); } finally { OPEN.clear(); } }
    }

    private static Object logger(RuntimeLoader loader) throws Exception { return loader.type("LogManager").getMethod("getLogger", String.class).invoke(null, "journeymap"); }
    private static Object layout(RuntimeLoader loader, String pattern, Object configuration, Object replacement, String charset, String always) throws Exception {
        return loader.loadClass(LOG_BRIDGE.replace('/', '.')).getMethod("createLayout", String.class, loader.type("core.config.Configuration"), loader.type("core.pattern.RegexReplacement"), String.class, String.class).invoke(null, pattern, configuration, replacement, charset, always);
    }
    private static Object appender(RuntimeLoader loader, Path output, String append, String flush, String ignore, Object layout, Object configuration) throws Exception {
        return loader.loadClass(LOG_BRIDGE.replace('/', '.')).getMethod("createAppender", String.class, String.class, String.class, String.class, String.class, loader.type("core.Layout"), loader.type("core.Filter"), String.class, String.class, loader.type("core.config.Configuration")).invoke(null, output.toString(), append, "fixture-" + output.getFileName(), flush, ignore, layout, null, "false", null, configuration);
    }
    private static Object event(RuntimeLoader loader, String message, Throwable thrown) throws Exception {
        Object value = loader.type("message.SimpleMessage").getConstructor(String.class).newInstance(message);
        return loader.type("core.impl.Log4jLogEvent").getConstructor(String.class, loader.type("Marker"), String.class, loader.type("Level"), loader.type("message.Message"), Throwable.class).newInstance("fixture", null, "fixture.Logger", loader.type("Level").getField("INFO").get(null), value, thrown);
    }

    private Map<String, byte[]> resourceFixture(boolean fixed) throws Exception {
        Map<String, byte[]> source = entries(journeyMap()); ClassNode files = node(source.get(FILE_HANDLER + ".class"));
        if (fixed) JourneyMapResourcesCompatibility.normalize(files);
        files.fields.clear(); files.methods.removeIf(method -> !Set.of("isInJar", "copyResources", "copyFromZip", "copyFromDirectory").contains(method.name));
        Map<String, byte[]> result = new LinkedHashMap<>(); ClassNode own = translate(files);
        for (MethodNode method : own.methods) new Analyzer<>(new BasicVerifier()).analyze(own.name, method);
        result.put(own.name + ".class", bytes(own));
        ClassNode inner = translate(node(source.get(FILE_HANDLER + "$ZipEntryByteSource.class"))); result.put(inner.name + ".class", bytes(inner));
        ClassNode anchor = new ClassNode(); anchor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, ANCHOR, null, "java/lang/Object", null); result.put(ANCHOR + ".class", bytes(anchor));
        result.put(SUPPORT + ".class", support()); return result;
    }

    private static byte[] loggingFixture(byte[] source) {
        ClassNode node = node(source); node.methods.removeIf(method -> !Set.of("init", "getLogFile").contains(method.name));
        node.fields.removeIf(field -> !field.name.equals("fileAppender"));
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(FILE_HANDLER) && call.name.equals("getJourneyMapDir")) call.owner = SUPPORT;
        return bytes(translate(node));
    }

    private static ClassNode translate(ClassNode source) {
        ClassNode result = new ClassNode(); source.accept(new ClassRemapper(result, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                return switch (name) {
                    case FILE_HANDLER -> OWN_FILE;
                    case FILE_HANDLER + "$ZipEntryByteSource" -> OWN_FILE + "$ZipEntryByteSource";
                    case JM_LOGGER -> OWN_LOG;
                    case "journeymap/client/JourneymapClient" -> ANCHOR;
                    case "journeymap/common/Journeymap", "journeymap/common/log/LogFormatter" -> SUPPORT;
                    case "java/util/zip/ZipFile" -> Type.getInternalName(TrackingZipFile.class);
                    default -> name;
                };
            }
        })); return result;
    }

    private static byte[] support() {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, SUPPORT, null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "directory", "Ljava/lang/String;", null, null));
        MethodNode directory = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getJourneyMapDir", "()Ljava/io/File;", null, null);
        directory.visitTypeInsn(Opcodes.NEW, "java/io/File"); directory.visitInsn(Opcodes.DUP); directory.visitFieldInsn(Opcodes.GETSTATIC, SUPPORT, "directory", "Ljava/lang/String;"); directory.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false); directory.visitInsn(Opcodes.ARETURN); node.methods.add(directory);
        MethodNode logger = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getLogger", "()Lorg/apache/logging/log4j/Logger;", null, null);
        logger.visitLdcInsn("journeymap-fixture"); logger.visitMethodInsn(Opcodes.INVOKESTATIC, "org/apache/logging/log4j/LogManager", "getLogger", "(Ljava/lang/String;)Lorg/apache/logging/log4j/Logger;", false); logger.visitInsn(Opcodes.ARETURN); node.methods.add(logger);
        MethodNode stringify = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "toString", "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null);
        stringify.visitVarInsn(Opcodes.ALOAD, 0); stringify.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "toString", "()Ljava/lang/String;", false); stringify.visitInsn(Opcodes.ARETURN); node.methods.add(stringify); return bytes(node);
    }

    private static final class RuntimeLoader extends URLClassLoader {
        private final Map<String, byte[]> definitions;
        RuntimeLoader(List<URL> fixture, Map<String, byte[]> definitions) throws Exception {
            super(urls(fixture), JourneyMapCompatibilityTest.class.getClassLoader()); this.definitions = new LinkedHashMap<>(definitions);
            try (var input = JourneyMapCompatibilityTest.class.getResourceAsStream("/" + LOG_BRIDGE + ".class")) { this.definitions.put(LOG_BRIDGE.replace('/', '.'), input.readAllBytes()); }
        }
        Class<?> type(String suffix) throws ClassNotFoundException { return loadClass("org.apache.logging.log4j." + suffix); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    byte[] bytes = definitions.get(name);
                    if (bytes != null) type = defineClass(name, bytes, 0, bytes.length);
                    else if (name.startsWith("org.apache.logging.log4j.") || name.startsWith("com.google.common.") || name.startsWith("fixture.journeymap.")) type = findClass(name);
                    else type = super.loadClass(name, false);
                }
                if (resolve) resolveClass(type); return type;
            }
        }
        @Override public void close() throws IOException {
            try {
                Class<?> manager = type("LogManager"); Object context = manager.getMethod("getContext", boolean.class).invoke(null, false);
                context.getClass().getMethod("stop").invoke(context);
            } catch (ReflectiveOperationException exception) { throw new IOException(exception); }
            finally { super.close(); }
        }
    }

    private static URL[] urls(List<URL> fixture) throws Exception {
        List<URL> urls = new ArrayList<>(fixture);
        urls.add(cached("org.apache.logging.log4j/log4j-core/2.19.0/3b6eeb4de4c49c0fe38a4ee27188ff5fee44d0bb/log4j-core-2.19.0.jar").toUri().toURL());
        urls.add(cached("org.apache.logging.log4j/log4j-api/2.19.0/ea1b37f38c327596b216542bc636cfdc0b8036fa/log4j-api-2.19.0.jar").toUri().toURL());
        urls.add(cached("com.google.guava/guava/17.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/guava-17.0.jar").toUri().toURL()); return urls.toArray(URL[]::new);
    }
    private static Path cached(String name) { Path path = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1", name); Assumptions.assumeTrue(Files.isRegularFile(path), path.toString()); return path; }
    private static Path journeyMap() { Path path = ForgeTestFixtures.optionalGuest("journeymap-1.8.9-5.1.5-unlimited.jar").toAbsolutePath().normalize(); Assumptions.assumeTrue(Files.isRegularFile(path)); return path; }
    private static Map<String, byte[]> themeAssets() throws Exception { Map<String, byte[]> result = new LinkedHashMap<>(); entries(journeyMap()).forEach((name, bytes) -> { if (name.startsWith(ASSETS) && !name.endsWith("/")) result.put(name, bytes); }); return result; }
    private static void assertExtracted(Map<String, byte[]> assets, Path output) throws Exception { for (var entry : assets.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(output.resolve(entry.getKey().substring(ASSETS.length()))), entry.getKey()); try (var walk = Files.walk(output)) { assertEquals(assets.size(), walk.filter(Files::isRegularFile).count()); } }
    private static void directories(Map<String, byte[]> entries) { for (String name : List.copyOf(entries.keySet())) for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) entries.putIfAbsent(name.substring(0, slash + 1), new byte[0]); }
    private static ForgeMappings mappings() throws Exception { return new ForgeMappings(new MappingResolver() {
        @Override public List<String> getNamespaces() { return List.of("official", "intermediary"); }
        @Override public String getCurrentRuntimeNamespace() { return "intermediary"; }
        @Override public String mapClassName(String namespace, String name) { return name; }
        @Override public String unmapClassName(String namespace, String name) { return name; }
        @Override public String mapMethodName(String namespace, String owner, String name, String descriptor) { return name; }
        @Override public String mapFieldName(String namespace, String owner, String name, String descriptor) { return name; }
    }, new StringReader("CL: fixture/Placeholder net/minecraft/Placeholder\n")); }
    private static Path jar(Path path, Map<String, byte[]> entries) throws Exception { try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) { for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } } return path; }
    private static Map<String, byte[]> entries(Path path) throws Exception { Map<String, byte[]> entries = new LinkedHashMap<>(); try (ZipFile zip = new ZipFile(path.toFile())) { for (var entry : java.util.Collections.list(zip.entries())) try (var input = zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); } } return entries; }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static List<MethodInsnNode> calls(MethodNode method, String owner) { return Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call && call.owner.equals(owner)).map(MethodInsnNode.class::cast).toList(); }
    private static byte[] methodBytes(MethodNode method) { ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Method", null, "java/lang/Object", null); method.accept(writer); writer.visitEnd(); return writer.toByteArray(); }
}
