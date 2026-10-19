plugins {
 id("net.fabricmc.fabric-loom-remap") version "1.17.21"
 id("ploceus") version "1.17.7"
}
version = "0.3.6"
group = "io.github.arcadiumclient"
base.archivesName = "ArcadiumBrand"
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
repositories {
 mavenCentral()
 maven("https://maven.fabricmc.net/")
 maven("https://maven.ornithemc.net/releases/")
}
ploceus { setIntermediaryGeneration(2) }
dependencies {
 minecraft("com.mojang:minecraft:1.8.9")
 mappings(ploceus.featherMappings("1"))
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
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
val featureTestJar by tasks.registering(Jar::class) {
    archiveBaseName = "ArcadiumFeatureTest"
    archiveClassifier = "dev"
    from(featureTest.output)
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapFeatureTestJar") {
    inputFile = featureTestJar.flatMap { it.archiveFile }
    archiveBaseName = "ArcadiumFeatureTest"
    archiveClassifier = ""
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
