import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import java.util.jar.JarOutputStream
import org.glavo.pack200.Pack200
import org.tukaani.xz.LZMAInputStream

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.tukaani:xz:1.10")
        classpath("org.glavo:pack200:0.3.0")
    }
}

plugins {
    java
}

group = "io.github.arcadiumclient"
version = "0.4.2"
base.archivesName = "ArcadiumForgeCompat"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://libraries.minecraft.net/")
    maven("https://maven.bawnorton.com/releases")
}

val forgeLibraries by configurations.creating { isTransitive = false }
val decodingLibraries by configurations.creating { isTransitive = false }
val modernNettyTest by configurations.creating { isTransitive = false }
val legacyNettyTest by configurations.creating { isTransitive = false }
val externalForgeLibraries = listOf(
    "0c3085cb93c3ef95d731c274b6f2dd195aa0405fe9328d69cab7f5e899d5e124 lzma-0.0.1.jar https://libraries.minecraft.net/lzma/lzma/0.0.1/lzma-0.0.1.jar",
    "09adf7955baa6bddf57a4bdbf9a91cf690261314969b59a18085ece1e05eccf9 vecmath-1.5.2.jar https://libraries.minecraft.net/java3d/vecmath/1.5.2/vecmath-1.5.2.jar",
)

dependencies {
    compileOnly("net.fabricmc:fabric-loader:0.19.5")
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    compileOnly("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.7-beta.3")
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("org.ow2.asm:asm-analysis:9.10.1")
    implementation("org.tukaani:xz:1.10")
    implementation("org.glavo:pack200:0.3.0")
    decodingLibraries("org.tukaani:xz:1.10")
    decodingLibraries("org.glavo:pack200:0.3.0")
    compileOnly("com.google.guava:guava:17.0")
    compileOnly(files("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"))
    compileOnly("com.google.code.gson:gson:2.2.4")
    compileOnly("org.apache.logging.log4j:log4j-core:2.19.0")
    testImplementation("net.fabricmc:fabric-loader:0.19.5")
    testImplementation("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    testImplementation("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.7-beta.3")
    testImplementation("com.google.code.gson:gson:2.2.4")
    testImplementation("com.google.guava:guava:17.0")
    testImplementation("org.apache.commons:commons-lang3:3.3.2")
    testImplementation("io.github.llamalad7:mixinextras-fabric:0.5.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.joml:joml:1.10.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    modernNettyTest("io.netty:netty-common:4.2.18.Final")
    legacyNettyTest("io.netty:netty-all:4.0.23.Final")
    forgeLibraries("net.sf.trove4j:trove4j:3.0.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

val prepareForgeApi by tasks.registering {
    val expectedHash = "596512ad5f12f95d8a3170321543d4455d23b8fe649c68580c5f828fe74f6668"
    val filename = "forge-1.8.9-11.15.1.2318-1.8.9-universal.jar"
    val cached = rootProject.file("../build/forge-compat/dependencies/$filename")
    inputs.property("forgeSha256", expectedHash)
    outputs.file(cached)
    doLast {
        fun digest(file: File) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        if (!cached.isFile || digest(cached) != expectedHash) {
            cached.parentFile.mkdirs()
            val url = URI("https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/$filename").toURL()
            val connection = url.openConnection().apply {
                setRequestProperty("User-Agent", "ArcadiumBuild/$version")
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            val temporary = Files.createTempFile(cached.parentFile.toPath(), "forge-", ".download")
            connection.getInputStream().use { input ->
                Files.newOutputStream(temporary).use { output ->
                    var total = 0L
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 16 * 1024 * 1024) { "Forge download exceeds the size limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(digest(temporary.toFile()) == expectedHash) { "The Forge API download failed verification." }
            Files.move(temporary, cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        check(digest(cached) == expectedHash) { "The cached Forge API failed verification." }
    }
}

tasks.compileJava { dependsOn(prepareForgeApi) }

val testFixturesDirectory = layout.buildDirectory.dir("generated/test-forge-data")
val prepareForgeTestFixtures by tasks.registering {
    dependsOn(prepareForgeApi)
    outputs.dir(testFixturesDirectory)
    outputs.file(rootProject.file("../build/forge-compat/dependencies/forge-1.8.9.srg"))
    doLast {
        val original = rootProject.file("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar")
        val output = testFixturesDirectory.get().asFile.resolve("META-INF/arcadium")
        output.mkdirs()
        ZipFile(original).use { zip ->
            val srg = LZMAInputStream(zip.getInputStream(zip.getEntry("deobfuscation_data-1.8.9.lzma"))).use { it.readBytes() }
            GZIPOutputStream(output.resolve("forge.srg.gz").outputStream()).use { it.write(srg) }
            rootProject.file("../build/forge-compat/dependencies/forge-1.8.9.srg").writeBytes(srg)
            LZMAInputStream(zip.getInputStream(zip.getEntry("binpatches.pack.lzma"))).use { input ->
                JarOutputStream(output.resolve("forge-game-patches.jar").outputStream()).use {
                    Pack200.newUnpacker().unpack(input, it)
                }
            }
        }
    }
}
sourceSets.test { resources.srcDir(testFixturesDirectory) }
tasks.processTestResources { dependsOn(prepareForgeTestFixtures) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("externalForgeLibraries", externalForgeLibraries)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
    from(forgeLibraries) { into("META-INF/arcadium/libraries") }
    doLast {
        val index = destinationDir.resolve("META-INF/arcadium/libraries/index.txt")
        index.parentFile.mkdirs()
        val embedded = forgeLibraries.files.sortedBy { it.name }.map { file ->
            val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
            "$hash ${file.name}"
        }
        index.writeText((embedded + externalForgeLibraries).sortedBy { it.split(' ')[1] }
            .joinToString("\n", postfix = "\n"))
    }
}

val nativeTestDirectory = rootProject.file("../build/forge-compat/native-test-fixtures")
val prepareNativeTestFixtures by tasks.registering {
    val catalog = rootProject.file("../distribution/oneclient-1.8.9.lock.json")
    inputs.file(catalog)
    inputs.files(modernNettyTest, legacyNettyTest)
    outputs.dir(nativeTestDirectory)
    doLast {
        nativeTestDirectory.mkdirs()
        @Suppress("UNCHECKED_CAST")
        val lock = groovy.json.JsonSlurper().parse(catalog) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val entries = (lock["bundles"] as List<Map<String, Any>>).flatMap { it["mods"] as List<Map<String, Any>> }
        val required = setOf("ornithe-standard-libraries-0.22.0.jar", "freelook-2.3.0+mc1.8.9.jar",
            "BetterHurtCam-1.15.1+mc1.8.9-ornithe.jar", "LegacyGuiBlur-6.3.4+mc1.8.9-fabric.jar",
            "argentum-1.0.1.jar", "Sarcio-1.1.0.jar", "Tooltip-Scroll-Ornithe-1.0.0.jar",
            "cera-1.0.1.jar", "overflowanimations-5.0.6+1.8.9-fabric.jar")
        val local = providers.gradleProperty("arcadiumTestNativeMods")
            .orElse(providers.environmentVariable("ARCADIUM_TEST_NATIVE_MODS_DIR")).orNull?.let { file(it) }
        fun digest(file: File) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(file.readBytes()))
        for (name in required) {
            val entry = entries.firstOrNull { (it["path"] as String).substringAfterLast('/') == name }
                ?: error("Pinned native fixture is missing from the distribution catalog: $name")
            val expected = entry["sha1"] as String
            val target = nativeTestDirectory.resolve(name)
            if (target.isFile && digest(target) == expected) continue
            val provided = local?.resolve(name)
            if (provided?.isFile == true && digest(provided) == expected) {
                Files.copy(provided.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val address = (entry["downloads"] as List<String>).first()
            val uri = URI(address)
            check(uri.scheme == "https" && uri.host in setOf("github.com", "cdn.modrinth.com")) { "Unexpected native fixture endpoint: $name" }
            val temporary = Files.createTempFile(nativeTestDirectory.toPath(), "fixture-", ".download")
            val connection = uri.toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 90_000; setRequestProperty("User-Agent", "ArcadiumSourceTests/$version") }
            connection.getInputStream().use { input -> Files.newOutputStream(temporary).use { output ->
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 64 * 1024 * 1024) { "Native fixture exceeds size limit: $name" }
                    output.write(buffer, 0, count)
                }
            } }
            check(digest(temporary.toFile()) == expected) { "Native fixture checksum mismatch: $name" }
            Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        for (library in modernNettyTest.files + legacyNettyTest.files) {
            Files.copy(library.toPath(), nativeTestDirectory.resolve(library.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(prepareNativeTestFixtures)
    systemProperty("arcadium.test.nativeMods", nativeTestDirectory.absolutePath)
    val guests = providers.gradleProperty("arcadiumTestMods").orElse(providers.environmentVariable("ARCADIUM_TEST_FORGE_MODS_DIR"))
    guests.orNull?.let { systemProperty("arcadium.test.guestMods", file(it).absolutePath) }
    dependsOn(tasks.jar)
    systemProperty("arcadium.test.productionJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    exclude("META-INF/arcadium/libraries/lzma-*.jar", "META-INF/arcadium/libraries/vecmath-*.jar")
    for (library in decodingLibraries.files) {
        from(zipTree(library)) {
            include("org/glavo/pack200/**", "org/tukaani/xz/**", "com/sun/java/util/jar/pack/**")
        }
        from(zipTree(library)) {
            include("**/LICENSE*", "**/COPYING*", "**/NOTICE*")
            into("META-INF/licenses/${library.name}")
        }
    }
    from(rootProject.file("../LICENSE"))
    from(rootProject.file("../NOTICE.md"))
    from(rootProject.file("../licenses")) {
        include("FORGE-*", "FML-*")
        into("META-INF/licenses")
    }
    from(rootProject.file("../licenses/forge-compat")) { into("META-INF/licenses/forge-compat") }
    from(rootProject.file("../licenses/native/forge-libraries")) { into("META-INF/licenses/forge-libraries") }
}

tasks.register("verifyDistributionLicenses") {
    dependsOn(tasks.jar)
    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        ZipFile(archive).use { zip ->
            val forbidden = zip.entries().asSequence().filter {
                it.name.endsWith(".srg") || it.name.endsWith(".srg.gz") ||
                    it.name.endsWith("forge-runtime.jar") || it.name.endsWith("forge-game-patches.jar") ||
                    it.name.contains("deobfuscation_data") || it.name.startsWith("net/minecraft/client/") ||
                    it.name.startsWith("LZMA/") || it.name.startsWith("javax/vecmath/") ||
                    it.name.matches(Regex("META-INF/arcadium/libraries/(lzma|vecmath)-.*\\.jar"))
            }.map { it.name }.toList()
            check(forbidden.isEmpty()) { "Forge distribution contains non-redistributable game or MCP data: $forbidden" }
        }
    }
}
tasks.check { dependsOn("verifyDistributionLicenses") }
