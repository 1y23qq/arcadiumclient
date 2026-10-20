import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import net.ornithemc.ploceus.api.PloceusGradleExtensionApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
//    id("dev.kikugie.loom-back-compat")
    id("net.fabricmc.fabric-loom-remap") version "1.17.21"
    id("ploceus") version "1.17.7"

    alias(libs.plugins.ksp)
    alias(libs.plugins.fletchingtable.fabric)
}

ploceus {
    extensions.getByType<PloceusGradleExtensionApi>().apply {
        setIntermediaryGeneration(2)
    }
}

class ModData {
    val id = property("mod.id").toString()
    val name = property("mod.name")
    val version = property("mod.version")
    val group = property("mod.group").toString()
    val description = property("mod.description")
    val source = property("mod.source")
    val issues = property("mod.issues")
    val license = property("mod.license").toString()
    val modrinth = property("mod.modrinth")
    val kofi = property("mod.kofi")
    val discord = property("mod.discord")
}

class Dependencies {
    //    val fabricApiVersion = property("deps.fabric_api_version")
    val oneconfigVersion = property("deps.oneconfig_version")
    val oneconfigMcVersion = property("deps.oneconfig_mc_version")
    val lenisVersion = property("deps.lenis_version")
    val oslVersion = property("deps.osl_version")
}

class McData {
    val version = stonecutter.current.version
    val dep = property("mod.mc_dep").toString()
}

val mc = McData()
val mod = ModData()
val deps = Dependencies()

version = "${mod.version}+${mc.version}-fabric"
group = mod.group
base { archivesName.set(mod.id) }

loom {
    runConfigs.all {
        ideConfigGenerated(stonecutter.current.isActive)
        runDir = "../../run" // This sets the run folder for all mc versions to the same folder. Remove this line if you want individual run folders.
    }

    runConfigs.remove(runConfigs["server"]) // Removes server run configs
}

loom.runs {
    afterEvaluate {
        val mixinJarFile = configurations.runtimeClasspath.get().incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier && it.group == "net.fabricmc" && it.module == "sponge-mixin"
            }
        }.files.first()

        configureEach {
            vmArg("-javaagent:$mixinJarFile")
            vmArg("-XX:+AllowEnhancedClassRedefinition")

            property("mixin.hotSwap", "true")
            property("mixin.debug.export", "true") // Puts mixin outputs in /run/.mixin.out
        }
    }
}

fletchingTable {
    mixins.create("main") {
        mixin("default", "${mod.id}.mixins.json")
    }

    lang.create("main") {
        patterns.add("assets/${mod.id}/lang/**")
    }
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    maven("https://repo.saadndm.com/releases")
    maven("https://maven.cloverclient.com/releases")
    maven("https://maven.fabricmc.net")

    mavenLocal()
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.bawnorton.com/releases")
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    maven("https://jitpack.io") { content { includeGroupAndSubgroups("com.github") } }
    maven("https://maven.gegy.dev/releases")
    maven("https://nexus.prsm.wtf/repository/maven-public/maven-repo/releases/")
    maven("https://repo.hypixel.net/repository/Hypixel/")
    maven("https://maven.deftu.dev/releases")
    maven("https://central.sonatype.com/repository/maven-snapshots/") { content { includeGroup("net.kyori") } }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://www.cursemaven.com", "Curseforge", "curse.maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${mc.version}")

    modImplementation(libs.fabricloader)

//    exceptions(ploceus.raven("1"))
//    signatures(ploceus.sparrow("1"))
//    nests(ploceus.nests("3"))

    mappings(ploceus.layeredMappings {
        mappings(ploceus.featherMappings(property("feather_build") as String))
        if (mc.version == "1.7.10") mappings(rootProject.file("mappings/feather-1.7.10.tiny"))
    })

    ploceus.dependOsl("${deps.oslVersion}")

    modImplementation("org.polyfrost.oneconfig:${mc.version}-ornithe:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:commands:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:config:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:config-impl:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:events:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:internal:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:ui:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:utils:${deps.oneconfigVersion}")
    implementation("org.polyfrost.oneconfig:hud:${deps.oneconfigVersion}")
}

// mc_dep fields must be in the format 'x', '>=x', '>=x <=y'
val rangeRegex = Regex(""">=\s*([0-9.]+)(?:\s*<=\s*([0-9.]+))?""")
val exactVersionRegex = Regex("""^\d+\.\d+(\.\d+)?$""")

val modrinthId = findProperty("publish.modrinth")?.toString()?.takeIf { it.isNotBlank() }
val curseforgeId = findProperty("publish.curseforge")?.toString()?.takeIf { it.isNotBlank() }

// accessTokens should be placed in the user Gradle gradle.properties file
// for example, on Windows this would be "~/.gradle/gradle.properties"
// then add:
// modrinth.token=
// curseforge.token=


sourceSets.main { java.exclude("**/ElderGuardianParticleRenderState.java") }

java {
    // withSourcesJar() // Uncomment if you want sources
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.processResources {
    val props = buildMap {
        put("id", mod.id)
        put("name", mod.name)
        put("version", mod.version)
        put("mcdep", mc.dep)
        put("description", mod.description)
        put("source", mod.source)
        put("issues", mod.issues)
        put("license", mod.license)
        put("modrinth", mod.modrinth)
        put("kofi", mod.kofi)
        put("discord", mod.discord)
        put("fabric_loader_version", libs.versions.fabricloader.get())
    }

    props.forEach(inputs::property)

    filesMatching("**/lang/en_us.json") { // Defaults description to English translation
        expand(props)
        filteringCharset = "UTF-8"
    }

    filesMatching("fabric.mod.json") { expand(props) }

    if (mc.version == "1.7.10") {
        val legacyMixins = rootProject.file("src/main/resources/overlaytweaks.mixins.json")
        inputs.file(legacyMixins)
        doLast {
            destinationDir.resolve("overlaytweaks.mixins.json").writeText(legacyMixins.readText())
        }
    }
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn(tasks.named("build"))
    }
}

tasks.jar { from(rootProject.file("LICENSE")) }
