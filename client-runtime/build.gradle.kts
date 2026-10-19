plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "io.github.arcadiumclient"
version = "0.3.7"
base.archivesName = "ArcadiumRuntime"

repositories {
    mavenLocal()
    mavenCentral()
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
    minecraft("com.mojang:minecraft:1.8.9")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:1.8.9+build.2:v2") { containsUnpick() }
        mappings(file("mappings/feather-overrides.tiny"))
    })
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("org.polyfrost.oneconfig:1.8.9-ornithe:1.2.23-arcadium") {
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
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar {
    from("../LICENSE")
    from("../NOTICE.md")
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
