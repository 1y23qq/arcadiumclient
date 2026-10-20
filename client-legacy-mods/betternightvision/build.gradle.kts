plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
}

val minecraftVersion = stonecutter.current.version
version = "1.0.5-arcadium+$minecraftVersion"
group = "dev.microcontrollers"
base.archivesName = "betternightvision"

ploceus { setIntermediaryGeneration(2) }
java { toolchain.languageVersion = JavaLanguageVersion.of(25); withSourcesJar() }
configurations.configureEach { exclude(group = "org.lwjgl.lwjgl") }
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.polyfrost.org/releases")
    maven("https://maven.cloverclient.com/releases")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    google()
}
dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:$minecraftVersion+build.2:v2")
    })
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    ploceus.dependOsl("0.22.0")
    modImplementation("org.polyfrost.oneconfig:$minecraftVersion-ornithe:1.2.23-arcadium")
    for (module in arrayOf("commands", "config", "config-impl", "events", "internal", "ui", "utils", "hud")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
}
tasks.processResources {
    inputs.property("minecraft", minecraftVersion)
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("minecraft" to minecraftVersion, "version" to project.version)
    }
    val native1710 = minecraftVersion == "1.7.10"
    filesMatching("*.mixins.json") {
        filter { line -> if (native1710 && "WorldBorderRendererMixin" in line) "" else line }
    }
}
tasks.jar { from(rootProject.file("LICENSE")) { rename { "LICENSE_betternightvision" } } }
