import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "io.github.arcadiumclient"
version = providers.gradleProperty("modVersion").get()
val coreVersion = providers.gradleProperty("coreVersion").get()
base.archivesName = "ArcadiumNext"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://libraries.minecraft.net")
    google()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://api.modrinth.com/maven")
    maven("https://repo.polyfrost.org/releases/")
    maven("https://repo.polyfrost.org/snapshots/")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    compileOnly("maven.modrinth:sodium:xJZxADzI")
    compileOnly("maven.modrinth:qyu2y7wG:sNOlI9Yl")
    implementation("maven.modrinth:1eAoo2KR:DoR7RYgi")
    implementation((create("maven.modrinth:dynamic-fps:pC2JjFw1") as ExternalModuleDependency).apply { isTransitive = false })
    implementation("org.polyfrost.oneconfig:26.2-fabric:$coreVersion") {
        exclude(group = "net.fabricmc", module = "fabric-language-kotlin")
    }
    for (module in listOf("commands", "config", "config-impl", "hud", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
    runtimeOnly("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")
    implementation(include("com.moulberry:mixinconstraints:1.1.0")!!)
    for (module in listOf("ktor-client-core", "ktor-client-cio", "ktor-client-content-negotiation", "ktor-client-websockets", "ktor-serialization-kotlinx-json")) {
        implementation("io.ktor:$module:3.5.2")
    }
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.ow2.asm:asm-tree:9.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val networkClosure = configurations.detachedConfiguration(
    *listOf("ktor-client-core", "ktor-client-cio", "ktor-client-content-negotiation", "ktor-client-websockets", "ktor-serialization-kotlinx-json")
        .map { dependencies.create("io.ktor:$it-jvm:3.5.2") }.toTypedArray()
)
for (artifact in networkClosure.resolvedConfiguration.resolvedArtifacts) {
    if (artifact.moduleVersion.id.group == "io.ktor") dependencies.add("include", artifact.moduleVersion.id.toString())
}

fun org.gradle.api.file.SourceDirectorySet.inheritSources(kind: String, language: String) {
    val shared = file("../client-modern/src/$kind/$language").canonicalFile.toPath()
    val overrides = file("src/$kind/$language")
    setSrcDirs(listOf(shared.toFile(), overrides))
    exclude { element ->
        val inherited = element.file.toPath().toAbsolutePath().normalize().startsWith(shared)
        inherited && (overrides.resolve(element.path).isFile || kind == "main" && language == "java" && (element.path.startsWith("io/github/arcadiumclient/modern/client/debug/betterf3/") || element.path in setOf(
            "io/github/arcadiumclient/modern/client/glint/mixin/GlintBufferMixin.java",
            "io/github/arcadiumclient/modern/client/glint/mixin/GlintUniformMixin.java",
            "io/github/arcadiumclient/modern/client/glint/mixin/ImmediatelyFastGlintMixin.java",
        )))
    }
}

for (kind in listOf("main", "test")) {
    sourceSets.named(kind) {
        java.inheritSources(kind, "java")
        resources.inheritSources(kind, "resources")
    }
    kotlin.sourceSets.named(kind) { kotlin.inheritSources(kind, "kotlin") }
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") { source(sourceSets.main.get().java) }
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") { source(sourceSets.test.get().java) }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
sourceSets.main { java.srcDir("../client-shared/src/main/java"); resources.srcDir("../client-shared/src/main/resources") }
kotlin { jvmToolchain(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

val yaclTestArchive = configurations.detachedConfiguration(dependencies.create("maven.modrinth:1eAoo2KR:DoR7RYgi")).apply { isTransitive = false }
val yaclTestDirectory = layout.buildDirectory.dir("test-libraries/yacl")
val prepareYaclTestLibraries = tasks.register<Sync>("prepareYaclTestLibraries") {
    from(provider { zipTree(yaclTestArchive.singleFile) }) {
        include("META-INF/jars/gson-*.jar", "META-INF/jars/json-*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(yaclTestDirectory)
}
dependencies.add("testRuntimeOnly", files(provider { fileTree(yaclTestDirectory.get()) { include("*.jar") } }).builtBy(prepareYaclTestLibraries))

val idleCounterpart = configurations.detachedConfiguration(dependencies.create("maven.modrinth:dynamic-fps:pC2JjFw1")).apply { isTransitive = false }
val idleSha512 = "79b13548d7e9de9abfa23e062724a885e5341f371c500d6570056ecea7db00c2f7ace5a7288179ad8baec575b4aa992b91dccbc940c584cb5cddf0bc7aeb892f"
val verifyIdleCounterpart = tasks.register("verifyIdleCounterpart") {
    inputs.files(idleCounterpart)
    doLast {
        val actual = MessageDigest.getInstance("SHA-512").digest(idleCounterpart.singleFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == idleSha512) { "Dynamic FPS counterpart checksum differs from the pinned release." }
    }
}
val idlePrepared = layout.buildDirectory.file("selected-mods/dynamic-fps-3.11.9-arcadium-assets.jar")
val prepareIdleCounterpart = tasks.register<Exec>("prepareIdleCounterpart") {
    dependsOn(verifyIdleCounterpart)
    inputs.files(idleCounterpart, fileTree("src/idleAssets"), fileTree("../client-modern/src/idleAssets/replacements"), fileTree("../client-modern/src/idleAssets/licenses"))
    outputs.file(idlePrepared)
    doFirst {
        val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get()
        commandLine(launcher.executablePath.asFile, "--source", "25", file("src/idleAssets/PrepareCounterpart.java"), idleCounterpart.singleFile,
            idlePrepared.get().asFile, file("../client-modern/src/idleAssets/replacements"), file("../client-modern/src/idleAssets/licenses"), layout.buildDirectory.file("selected-mods/dynamic-fps-preparation.json").get().asFile)
    }
}
dependencies.add("include", dependencies.project(mapOf("path" to ":selected-idle", "configuration" to "selectedIdleElements")))
val auditIdleCounterpart = tasks.register<Exec>("auditIdleCounterpart") {
    dependsOn(prepareIdleCounterpart)
    val audit = layout.buildDirectory.file("selected-mods/dynamic-fps-asset-audit.json")
    inputs.files(idleCounterpart, idlePrepared, fileTree("src/idleAssets"), fileTree("../client-modern/src/idleAssets/replacements"), fileTree("../client-modern/src/idleAssets/licenses"))
    outputs.file(audit)
    doFirst {
        val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get()
        commandLine(launcher.executablePath.asFile, "--source", "25", file("src/idleAssets/PrepareCounterpart.java"), idleCounterpart.singleFile,
            idlePrepared.get().asFile, file("../client-modern/src/idleAssets/replacements"), file("../client-modern/src/idleAssets/licenses"), audit.get().asFile, "--audit-only")
    }
}
tasks.named("processIncludeJars") { dependsOn(auditIdleCounterpart) }
tasks.jar {
    doLast {
        val selected = idlePrepared.get().asFile
        val nestedPath = "META-INF/jars/${selected.name}"
        ZipFile(archiveFile.get().asFile).use { archive ->
            val nested = archive.getEntry(nestedPath) ?: error("Audited idle counterpart is absent from production.")
            check(archive.getInputStream(nested).use { it.readAllBytes() }.contentEquals(selected.readBytes())) { "Nested idle counterpart differs from its audit." }
            check(archive.getInputStream(archive.getEntry("fabric.mod.json")).bufferedReader().use { it.readText() }.contains(nestedPath)) { "Nested idle counterpart is not declared to Fabric." }
        }
    }
}

tasks.processResources {
    from("../client-runtime/src/main/resources") { include("assets/arcadium/**") }
    from("../game-branding/src/main/resources") { include("assets/arcadiumclient/icon.png") }
    from("../game-branding/src/main/resources/assets/arcadiumclient/icon.png") { into("assets/arcadium/textures/brand") }
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar {
    from("../LICENSE")
    from("../NOTICE.md")
    from("../client-legacy-mods/betterhurtcam/LICENSE") { into("META-INF/licenses/betterhurtcam") }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val guiQaSource = sourceSets.create("guiQa") {
    java.setSrcDirs(listOf("src/guiQa/java"))
    resources.setSrcDirs(listOf("src/guiQa/resources"))
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
tasks.named<JavaCompile>(guiQaSource.compileJavaTaskName) { options.isIncremental = false }
tasks.register<Jar>("guiQaJar") {
    dependsOn(tasks.named(guiQaSource.classesTaskName))
    from(guiQaSource.output)
    archiveClassifier.set("gui-qa")
}

loom { accessWidenerPath.set(file("src/main/resources/arcadium-debug.accesswidener")) }
