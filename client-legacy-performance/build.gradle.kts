plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
}

version = "0.4.1"
group = "io.github.arcadiumclient"
base.archivesName = "ArcadiumLegacyPerformance"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.ornithemc.net/releases/")
}

ploceus { setIntermediaryGeneration(2) }

dependencies {
    minecraft("com.mojang:minecraft:1.7.10")
    mappings(ploceus.featherMappings("2"))
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.3")
    compileOnly("org.lwjgl:lwjgl:3.4.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar {
    archiveClassifier = "1.7.10-dev"
    from(rootProject.file("LICENSE"))
    from(rootProject.file("../NOTICE.md"))
}
tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier = "1.7.10"
}
tasks.test { useJUnitPlatform() }
