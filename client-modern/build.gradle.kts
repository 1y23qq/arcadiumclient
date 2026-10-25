import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "io.github.arcadiumclient"
version = providers.gradleProperty("modVersion").get()
val coreVersion = providers.gradleProperty("coreVersion").get()
base.archivesName = "ArcadiumModern"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://libraries.minecraft.net")
    google()
    maven("https://maven.fabricmc.net/")
    maven("https://api.modrinth.com/maven")
    maven("https://repo.polyfrost.org/releases/")
    maven("https://repo.polyfrost.org/snapshots/")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11")
    modCompileOnly("maven.modrinth:sodium:rkdTcxoT")
    modCompileOnly("maven.modrinth:qyu2y7wG:nnX0WSow")
    modImplementation("maven.modrinth:1eAoo2KR:pHWDw3Vc")
    modImplementation((create("maven.modrinth:dynamic-fps:Fab7e5Th") as ExternalModuleDependency).apply { isTransitive = false })
    modImplementation("org.polyfrost.oneconfig:1.21.11-fabric:$coreVersion") {
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

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
kotlin { jvmToolchain(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
val yaclTestArchive = configurations.detachedConfiguration(dependencies.create("maven.modrinth:1eAoo2KR:pHWDw3Vc")).apply { isTransitive = false }
val yaclTestDirectory = layout.buildDirectory.dir("test-libraries/yacl")
val prepareYaclTestLibraries = tasks.register<Sync>("prepareYaclTestLibraries") {
    from(provider { zipTree(yaclTestArchive.singleFile) }) {
        include("META-INF/jars/gson-0.2.1.jar", "META-INF/jars/json-0.2.1.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(yaclTestDirectory)
}
dependencies.add("testRuntimeOnly", files(provider { fileTree(yaclTestDirectory.get()) { include("*.jar") } }).builtBy(prepareYaclTestLibraries))
val idleCounterpart = configurations.detachedConfiguration(dependencies.create("maven.modrinth:dynamic-fps:Fab7e5Th")).apply { isTransitive = false }
val verifyIdleCounterpart = tasks.register("verifyIdleCounterpart") {
    inputs.files(idleCounterpart)
    doLast {
        val artifact = idleCounterpart.singleFile
        val actual = MessageDigest.getInstance("SHA-512").digest(artifact.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == "c45652252e2176cb8e2c44dfd9032c617135a100dcf161b9e6e6717803c14d1359d0f940fa10dc1aa6ad80669983f4b9c5636c0c2953d3314e06f893f0cdc736") { "Dynamic FPS counterpart checksum differs from the pinned release." }
    }
}
tasks.named("processIncludeJars") { dependsOn(verifyIdleCounterpart) }
val prepareIdleCounterpart = tasks.register<Zip>("prepareIdleCounterpart") {
    dependsOn(verifyIdleCounterpart)
    archiveFileName.set("dynamic-fps-3.11.6-arcadium-assets.jar")
    archiveExtension.set("jar")
    destinationDirectory.set(layout.buildDirectory.dir("selected-mods"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(provider { zipTree(idleCounterpart.singleFile) }) { exclude("assets/dynamic_fps/textures/**") }
    from("src/idleAssets/replacements")
    from("src/idleAssets/licenses") { into("META-INF/licenses/dynamic-fps") }
}
dependencies.add("include", dependencies.project(mapOf("path" to ":selected-idle", "configuration" to "selectedIdleElements")))
val auditIdleCounterpart = tasks.register<Exec>("auditIdleCounterpart") {
    dependsOn(prepareIdleCounterpart)
    val audit = layout.buildDirectory.file("selected-mods/dynamic-fps-asset-audit.json")
    inputs.files(idleCounterpart, prepareIdleCounterpart.flatMap { it.archiveFile }, fileTree("src/idleAssets"))
    outputs.file(audit)
    doFirst {
        val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get()
        commandLine(launcher.executablePath.asFile, "--source", "25", file("src/idleAssets/AuditCounterpart.java"), idleCounterpart.singleFile,
            prepareIdleCounterpart.get().archiveFile.get().asFile, file("src/idleAssets/replacements"), audit.get().asFile)
    }
}
tasks.named("processIncludeJars") { dependsOn(auditIdleCounterpart) }
tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    doLast {
        val selected = prepareIdleCounterpart.get().archiveFile.get().asFile
        val nestedPath = "META-INF/jars/${selected.name}"
        ZipFile(archiveFile.get().asFile).use { archive ->
            val nested = archive.getEntry(nestedPath) ?: error("Audited idle counterpart is absent from production.")
            check(archive.getInputStream(nested).use { it.readAllBytes() }.contentEquals(selected.readBytes())) { "Nested idle counterpart differs from the audited artifact." }
            val metadata = archive.getInputStream(archive.getEntry("fabric.mod.json")).bufferedReader().use { it.readText() }
            check(metadata.contains(nestedPath)) { "Nested idle counterpart is not declared to Fabric." }
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
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
val guiQaJar = tasks.register<Jar>("guiQaJar") {
    dependsOn(tasks.named(guiQaSource.classesTaskName))
    from(guiQaSource.output)
    archiveClassifier.set("gui-qa-dev")
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapGuiQaJar") {
    dependsOn(guiQaJar)
    inputFile.set(guiQaJar.flatMap { it.archiveFile })
    archiveClassifier.set("gui-qa")
    addNestedDependencies.set(false)
}
