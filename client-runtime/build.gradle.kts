plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "io.github.arcadiumclient"
version = "0.4.4"
base.archivesName = "ArcadiumRuntime"
val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("1.8.9")
require(minecraftVersion in setOf("1.7.10", "1.8.9")) { "Unsupported Minecraft version: $minecraftVersion" }
if (minecraftVersion == "1.7.10") layout.buildDirectory.set(layout.projectDirectory.dir("build/1.7.10"))

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://libraries.minecraft.net")
    google()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.ornithemc.net/releases/")
    maven("https://repo.polyfrost.org/releases/")
    maven("https://repo.polyfrost.org/snapshots/")
    maven("https://maven.bawnorton.com/releases/")
    maven("https://maven.cloverclient.com/releases/")
    maven("https://maven.taumc.org/releases/")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
}

ploceus { setIntermediaryGeneration(2) }
configurations.configureEach { exclude(group = "org.lwjgl.lwjgl") }
val networkModules = listOf("ktor-client-core", "ktor-client-cio", "ktor-client-content-negotiation", "ktor-client-websockets", "ktor-serialization-kotlinx-json")

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:$minecraftVersion+build.2:v2") { containsUnpick() }
        mappings(file(if (minecraftVersion == "1.7.10") "mappings/feather-1.7.10.tiny" else "mappings/feather-overrides.tiny"))
    })
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("org.polyfrost.oneconfig:$minecraftVersion-ornithe:1.2.23-arcadium") {
        exclude(group = "net.fabricmc", module = "fabric-language-kotlin")
    }
    for (module in listOf("commands", "config", "config-impl", "hud", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
    runtimeOnly("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")
    for (module in networkModules) {
        implementation("io.ktor:$module:3.5.2")
    }
    implementation(include("com.moulberry:mixinconstraints:1.1.0")!!)
    implementation(include("org.joml:joml:1.10.8")!!)
    implementation(include("org.slf4j:slf4j-api:2.0.17")!!)
    implementation(include("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.7-beta.3")!!)
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")
    compileOnly("org.embeddedt.celeritas:celeritas-common:2.5.0-pre.1") { isTransitive = false }
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val networkClosure = configurations.detachedConfiguration(
    *networkModules.map { dependencies.create("io.ktor:$it-jvm:3.5.2") }.toTypedArray()
)
for (artifact in networkClosure.resolvedConfiguration.resolvedArtifacts) {
    if (artifact.moduleVersion.id.group == "io.ktor") {
        dependencies.add("include", artifact.moduleVersion.id.toString())
    }
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
kotlin { jvmToolchain(25) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
tasks.register("exportTestClasspath") {
    dependsOn(tasks.testClasses)
    doLast {
        val output = layout.buildDirectory.file("test-classpath.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(sourceSets.test.get().runtimeClasspath.files.joinToString("\n") { it.absolutePath }, Charsets.UTF_8)
    }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraftVersion" to minecraftVersion,
            "runtimeMixins" to if (minecraftVersion == "1.7.10") "arcadium-runtime-1710.mixins.json" else "arcadium-runtime.mixins.json")
    }
}
tasks.jar {
    from("../LICENSE")
    from("../NOTICE.md")
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

if (minecraftVersion == "1.7.10") {
    val nativeSources = layout.projectDirectory.dir("src/minecraft/1.7.10")
    val generateLegacySources by tasks.registering(Sync::class) {
        val patchFile = layout.projectDirectory.file("legacy-source-patches.json")
        inputs.file(patchFile)
        val replacements = nativeSources.asFile.resolve("java").walkTopDown().filter { it.isFile }
            .map { it.relativeTo(nativeSources.asFile.resolve("java")).invariantSeparatorsPath }.toList() +
            nativeSources.asFile.resolve("kotlin").walkTopDown().filter { it.isFile }
                .map { it.relativeTo(nativeSources.asFile.resolve("kotlin")).invariantSeparatorsPath }.toList()
        from("src/main") {
            exclude("**/client/bedwars/**", "**/features/NoHitDelayConfig.kt", "**/client/MixinNoHitDelay.java",
                "**/client/MixinBedwars*.java", "**/client/MixinArcadiumPrefixTextRenderer.java",
                "**/client/MixinBadlionEntityAnimations.java", "**/client/MixinGuiScaleSpectatorHud.java",
                "**/client/MixinBadlionTitleAnimations.java", "**/client/MixinNickHiderTextWrapping.java",
                "**/client/MixinSkinChangerPlayerInfo.java", "**/compat/argentum/MixinNoRenderFastBlocks.java",
                "**/compat/forge/**")
            replacements.forEach { exclude("java/$it", "kotlin/$it") }
        }
        into(layout.buildDirectory.dir("generated/main"))
        doLast {
            @Suppress("UNCHECKED_CAST")
            val patches = groovy.json.JsonSlurper().parse(patchFile.asFile) as Map<String, List<Map<String, String>>>
            val directory = layout.buildDirectory.dir("generated/main").get().asFile
            directory.walkTopDown().filter { it.isFile && it.extension in setOf("java", "kt") }.forEach { source ->
                var text = file("src/main/" + source.relativeTo(directory).invariantSeparatorsPath).readText(Charsets.UTF_8)
                for (patch in patches["*"] ?: emptyList()) text = text.replace(patch.getValue("from"), patch.getValue("to"))
                for (patch in patches[source.relativeTo(directory).invariantSeparatorsPath] ?: emptyList()) {
                    check(text.contains(patch.getValue("from"))) { "Legacy source patch no longer matches ${source.name}" }
                    text = text.replace(patch.getValue("from"), patch.getValue("to"))
                }
                source.writeText(text, Charsets.UTF_8)
            }
        }
    }
    sourceSets.main {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/main/java"), nativeSources.dir("java")))
    }
    kotlin.sourceSets.main {
        kotlin.setSrcDirs(listOf(layout.buildDirectory.dir("generated/main/kotlin"), nativeSources.dir("kotlin"),
            layout.buildDirectory.dir("generated/main/java"), nativeSources.dir("java")))
    }
    tasks.withType<JavaCompile>().configureEach { dependsOn(generateLegacySources) }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { dependsOn(generateLegacySources) }
    val generateLegacyTests by tasks.registering(Sync::class) {
        val patchFile = layout.projectDirectory.file("legacy-source-patches.json")
        inputs.file(patchFile)
        from("src/test") {
            exclude("**/client/bedwars/**", "**/CeraLightmapColorProbe.java", "**/BundledSkinMeshProbe.java")
        }
        into(layout.buildDirectory.dir("generated/test"))
        doLast {
            @Suppress("UNCHECKED_CAST")
            val patches = groovy.json.JsonSlurper().parse(patchFile.asFile) as Map<String, List<Map<String, String>>>
            val directory = layout.buildDirectory.dir("generated/test").get().asFile
            directory.walkTopDown().filter { it.isFile && it.extension in setOf("java", "kt") }.forEach { source ->
                var text = file("src/test/" + source.relativeTo(directory).invariantSeparatorsPath).readText(Charsets.UTF_8)
                for (patch in patches["*"] ?: emptyList()) text = text.replace(patch.getValue("from"), patch.getValue("to"))
                for (patch in patches["test:" + source.relativeTo(directory).invariantSeparatorsPath] ?: emptyList()) {
                    check(text.contains(patch.getValue("from"))) { "Legacy test patch no longer matches ${source.name}" }
                    text = text.replace(patch.getValue("from"), patch.getValue("to"))
                }
                source.writeText(text, Charsets.UTF_8)
            }
        }
    }
    sourceSets.test {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/test/java"), nativeSources.dir("test/java")))
    }
    kotlin.sourceSets.test {
        kotlin.setSrcDirs(listOf(layout.buildDirectory.dir("generated/test/kotlin"),
            layout.buildDirectory.dir("generated/test/java"), nativeSources.dir("test/java")))
    }
    tasks.named("compileTestKotlin") { dependsOn(generateLegacyTests) }
    tasks.named("compileTestJava") { dependsOn(generateLegacyTests) }
    tasks.jar { archiveClassifier = "1.7.10-dev" }
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") { archiveClassifier = "1.7.10" }
    tasks.processResources {
        from(nativeSources.dir("resources"))
    }
}
