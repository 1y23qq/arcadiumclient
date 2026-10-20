plugins {
    id("net.fabricmc.fabric-loom-remap") version("1.17.21")
    id("ploceus") version("1.17.7")
}

base.archivesName = "Tooltip-Scroll-Ornithe"
group = "com.provismet"
version = "mod_version"()

ploceus {
    setIntermediaryGeneration(2)
}

dependencies {
    minecraft("com.mojang:minecraft:${"minecraft_version"()}")
    mappings(loom.layered {
        mappings(ploceus.featherMappings("feather_version"()))
        mappings(rootProject.file("mappings/feather-1.7.10.tiny"))
    })

    modImplementation("net.fabricmc:fabric-loader:${"fabric_version"()}")
    ploceus.dependOsl("osl_version"())
}

tasks.processResources {
    val v = project.version
    inputs.property("version", v)

    filesMatching("fabric.mod.json") {
        expand("version" to v)
    }
}

operator fun String.invoke() = rootProject.providers.gradleProperty(this).orNull
    ?: error("Property $this not found")
java { withSourcesJar(); sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
tasks.withType<JavaCompile>().configureEach { options.release = 25; options.encoding = "UTF-8" }
tasks.jar { from(rootProject.file("LICENSE")) { rename { "${it}_tooltipscroll" } } }
