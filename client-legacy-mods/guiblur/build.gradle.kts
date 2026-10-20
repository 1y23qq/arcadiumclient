plugins {
    java
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
}

group = "eu.midnightdust.blur"
version = "6.3.4-arcadium+1.7.10"
base.archivesName = "LegacyGuiBlur"
ploceus { setIntermediaryGeneration(2) }
configurations.configureEach { exclude(group = "org.lwjgl.lwjgl") }

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven("https://repo.polyfrost.org/releases")
    maven("https://maven.cloverclient.com/releases") { content { includeGroup("pl.tomgirl") } }
    maven("https://redirector.kotlinlang.org/maven/compose-dev") { content { includeGroup("org.jetbrains.skiko") } }
    maven("https://central.sonatype.com/repository/maven-snapshots") { content { includeGroup("net.kyori") } }
}

dependencies {
    minecraft("com.mojang:minecraft:1.7.10")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:1.7.10+build.2:v2") { containsUnpick() }
        mappings(rootProject.file("mappings/feather-1.7.10.tiny"))
    })
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    ploceus.dependOsl("0.22.0")
    modImplementation("org.polyfrost.oneconfig:1.7.10-ornithe:1.2.23-arcadium")
    for (module in listOf("config", "config-impl", "commands", "events", "internal", "ui", "utils", "hud", "poly-compose")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}
sourceSets.main { java.exclude("**/BlurModMenu.java") }
tasks.withType<JavaCompile>().configureEach { options.release = 25; options.encoding = "UTF-8" }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar { from(rootProject.file("LICENSE")) { rename { "LICENSE_blur" } } }
loom { runConfigs.remove(runConfigs["server"]) }
