plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
}
version = "1.2.6-arcadium+1.7.10"
group = "io.github.arcadiumclient.legacy"
base.archivesName = "sciophobia"
ploceus { setIntermediaryGeneration(2) }
configurations.configureEach { exclude(group = "org.lwjgl.lwjgl") }
repositories {
    mavenLocal(); mavenCentral(); google()
    maven("https://maven.ornithemc.net/releases")
    maven("https://repo.polyfrost.org/releases")
    maven("https://maven.cloverclient.com/releases")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    maven("https://central.sonatype.com/repository/maven-snapshots") { content { includeGroup("net.kyori") } }
}
dependencies {
    minecraft("com.mojang:minecraft:1.7.10")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:1.7.10+build.2:v2") { containsUnpick() }
        mappings(rootProject.file("mappings/feather-1.7.10.tiny"))
    })
    ploceus.dependOsl("0.22.0")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("org.polyfrost.oneconfig:1.7.10-ornithe:1.2.23-arcadium")
    for (module in listOf("config", "config-impl", "events", "commands", "internal", "ui", "utils", "hud")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
    
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = 25 }

loom { runConfigs.remove(runConfigs["server"]) }
tasks.jar { from(rootProject.file("LICENSE")) { rename { "LICENSE_sciophobia" } } }
