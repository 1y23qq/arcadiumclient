import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"
}

val modid = "confirmdisconnect"
version = "1.1.9-arcadium+1.7.10"
group = "dev.microcontrollers.confirmdisconnect"
base.archivesName = modid
ploceus { setIntermediaryGeneration(2) }
configurations.configureEach { exclude(group = "org.lwjgl.lwjgl") }

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven("https://maven.ornithemc.net/releases")
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
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
    ploceus.dependOsl("0.22.0")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("org.polyfrost.oneconfig:1.7.10-ornithe:1.2.23-arcadium")
    for (module in listOf("config", "config-impl", "commands", "events", "internal", "ui", "utils", "hud")) {
        implementation("org.polyfrost.oneconfig:$module:1.2.23-arcadium")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
tasks.withType<JavaCompile>().configureEach { options.release = 25; options.encoding = "UTF-8" }

loom { runConfigs.remove(runConfigs["server"]) }

tasks.processResources {
    val props = mapOf(
        "name" to "Confirm Disconnect",
        "id" to "confirmdisconnect",
        "group" to "dev.microcontrollers.confirmdisconnect",
        "description" to "Adds a confirmation dialogue before exiting your world/server!",
        "source" to "https://codeberg.org/MicrocontrollersDev/Confirm-Disconnect",
        "issues" to "https://discord.gg/QbNfaCHjUz",
        "license" to "LGPL-3.0",
        "modrinth" to "https://modrinth.com/mod/confirm-disconnect",
        "curseforge" to "https://www.curseforge.com/minecraft/mc-mods/confirm-disconnect",
        "kofi" to "https://ko-fi.com/microcontrollers",
        "discord" to "https://discord.gg/QbNfaCHjUz",
        "version" to "1.1.9-arcadium",
        "fabric_loader_version" to "0.19.5",
        "mcdep" to "1.7.10"
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    doLast {
        val metadata = destinationDir.resolve("fabric.mod.json")
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(metadata) as MutableMap<String, Any>
        json["entrypoints"] = mapOf("main" to listOf("dev.microcontrollers.confirmdisconnect.ConfirmDisconnect"))
        json["mixins"] = listOf("confirmdisconnect.mixins.json")
        metadata.writeText(JsonOutput.toJson(json))
        val config = destinationDir.resolve("confirmdisconnect.mixins.json")
        @Suppress("UNCHECKED_CAST")
        val mixins = JsonSlurper().parse(config) as MutableMap<String, Any>
        mixins["client"] = listOf("GameMenuScreenMixin")
        config.writeText(JsonOutput.toJson(mixins))
        destinationDir.walkTopDown().filter { it.isFile && it.extension == "json" && it.parentFile.name == "lang" }.forEach { file ->
            @Suppress("UNCHECKED_CAST")
            val translations = JsonSlurper().parse(file) as Map<String, String>
            val parts = file.nameWithoutExtension.split('_', limit = 2)
            val locale = parts[0] + if (parts.size == 2) "_" + parts[1].uppercase() else ""
            file.resolveSibling("$locale.lang").writeText(translations.entries.joinToString("\n") { "${it.key}=${it.value.replace("\n", "\\n")}" } + "\n")
        }
    }
}
tasks.jar { from(rootProject.file("LICENSE")) { rename { "${it}_$modid" } } }
