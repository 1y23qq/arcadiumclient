import net.ornithemc.ploceus.api.PloceusGradleExtensionApi

plugins {
    id("dev.kikugie.loom-back-compat")
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
    id("ploceus") version "1.17.7" apply false
}

val isOrnithe = sc.current.version in setOf("1.7.10", "1.8.9")
val ploceus = if (isOrnithe) {
    pluginManager.apply("net.fabricmc.fabric-loom-remap")
    pluginManager.apply("ploceus")

    configurations.configureEach {
        exclude(group = "org.lwjgl.lwjgl")
    }

    extensions.getByType<PloceusGradleExtensionApi>().apply {
        setIntermediaryGeneration(2)
    }
} else {
    null
}
val loader = if (isOrnithe) "ornithe" else "fabric"

version = "${property("mod.version")}+mc${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_25
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.ornithemc.net/releases")
    maven("https://redirector.kotlinlang.org/maven/compose-dev") { content { includeGroup("org.jetbrains.skiko") } }

    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")

    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/")

    maven("https://maven.ornithemc.net/releases")
    maven("https://maven.cloverclient.com/releases") {
        content { includeGroup("pl.tomgirl") }
    }
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://maven.fabricmc.net/releases")
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    google()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    if (isOrnithe) {
        mappings(ploceus!!.layeredMappings {
            mappings("net.ornithemc:feather-gen2:${sc.current.version}+build.${sc.properties["deps.feather_build"] as String}:v2") {
                containsUnpick()
            }
            if (sc.current.version == "1.7.10") mappings(rootProject.file("mappings/feather-1.7.10.tiny"))
        })
    } else {
        loomx.applyMojangMappings()
    }

    fun ocfg(vararg modules: String) {
        for (it in modules) modImplementation("org.polyfrost.oneconfig:${it}:${property("deps.oneconfig") as String}")
    }

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    ocfg("${sc.current.version}-$loader", "commands", "config", "config-impl", "events", "internal", "ui", "utils", "hud")

    if (!isOrnithe) {
        modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        jvmArguments.add("-Dmixin.debug.export=true")
        runDirectory.set(File("../../run"))
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        val legacy = isOrnithe
        filesMatching("*.mixins.json") {
            expand("java" to mixinJava)
            if (!legacy) filter { line -> if ("\"legacy." in line) "" else line }
        }
    }

    register<Copy>("buildAndCollect") {
        description = "Build & Collect"
        group = "build"

        from(loomx.modJar.map { it.archiveFile }, loomx.modSourcesJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}
