plugins {
 id("net.fabricmc.fabric-loom-remap") version "1.17.21"
 id("ploceus") version "1.17.7"
}
version = "0.5.6"
group = "io.github.arcadiumclient"
base.archivesName = "ArcadiumBrand"
val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("1.8.9")
require(minecraftVersion in setOf("1.7.10", "1.8.9")) { "Unsupported Minecraft version: $minecraftVersion" }
if (minecraftVersion == "1.7.10") {
    layout.buildDirectory.set(layout.projectDirectory.dir("build/1.7.10"))
    val generateLegacySources by tasks.registering(Sync::class) {
        from("src/main/java") { exclude("**/branding/MinecraftMixin.java", "**/branding/TitleScreenMixin.java") }
        into(layout.buildDirectory.dir("generated/java"))
    }
    sourceSets.main { java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/java"), "src/minecraft/1.7.10/java")) }
    tasks.withType<JavaCompile>().configureEach { dependsOn(generateLegacySources) }
    tasks.jar { archiveClassifier = "1.7.10-dev" }
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") { archiveClassifier = "1.7.10" }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching(listOf("fabric.mod.json", "arcadium-game.properties")) {
        expand("version" to project.version, "minecraftVersion" to minecraftVersion)
    }
    if (minecraftVersion == "1.7.10") filesMatching("fabric.mod.json") {
        filter { line -> if (line.contains("\"arcadium_forge_compat\"")) "" else line }
    }
}
repositories {
 mavenCentral()
 maven("https://maven.fabricmc.net/")
 maven("https://maven.ornithemc.net/releases/")
}
ploceus { setIntermediaryGeneration(2) }
dependencies {
 minecraft("com.mojang:minecraft:$minecraftVersion")
 mappings(ploceus.featherMappings(if (minecraftVersion == "1.7.10") "2" else "1"))
 modImplementation("net.fabricmc:fabric-loader:0.19.5")
 compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.3")
 compileOnly("org.lwjgl:lwjgl:3.4.3")
 compileOnly("org.lwjgl:lwjgl-sdl:3.4.3")
 compileOnly("org.ow2.asm:asm:9.10.1")
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = 17 }
tasks.withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
tasks.jar { from(rootProject.file("../LICENSE")); from(rootProject.file("../NOTICE.md")) }

val captureProbe by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
tasks.check { dependsOn(captureProbe.compileJavaTaskName) }

val featureTest by sourceSets.creating {
    if (minecraftVersion == "1.7.10") {
        java.setSrcDirs(listOf("src/featureTest1710/java"))
        resources.setSrcDirs(listOf("src/featureTest1710/resources"))
    }
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
val featureTestJar by tasks.registering(Jar::class) {
    archiveBaseName = "ArcadiumFeatureTest"
    archiveClassifier = if (minecraftVersion == "1.7.10") "1.7.10-dev" else "dev"
    from(featureTest.output)
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapFeatureTestJar") {
    inputFile = featureTestJar.flatMap { it.archiveFile }
    archiveBaseName = "ArcadiumFeatureTest"
    archiveClassifier = if (minecraftVersion == "1.7.10") "1.7.10" else ""
    dependsOn(featureTestJar)
}

val performanceTestJar by tasks.registering(Jar::class) {
    archiveBaseName = "ArcadiumPerformanceTest"
    archiveClassifier = "dev"
    from(featureTest.output) {
        include("io/github/arcadiumclient/featuretest/PerformanceQa*.class")
        include("io/github/arcadiumclient/featuretest/QaPlayer.class")
        include("io/github/arcadiumclient/featuretest/mixin/PerformanceTestMixin.class")
    }
    from("src/performanceTest/resources")
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapPerformanceTestJar") {
    inputFile = performanceTestJar.flatMap { it.archiveFile }
    archiveBaseName = "ArcadiumPerformanceTest"
    archiveClassifier = ""
    dependsOn(performanceTestJar)
}
