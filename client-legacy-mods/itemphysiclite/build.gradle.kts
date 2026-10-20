plugins {
    id("net.fabricmc.fabric-loom-remap") version("1.17.21")
    id("ploceus") version("1.17.7")
}

group = "com.example"
version = "mod_version"()

ploceus {
    setIntermediaryGeneration(2)
}

dependencies {
    minecraft("com.mojang:minecraft:${"minecraft_version"()}")
    mappings(loom.layered {
        mappings(ploceus.featherMappings("feather_version"()))
        mappings(rootProject.file("gradle/feather-1.7.10.tiny"))
    })

    modImplementation("net.fabricmc:fabric-loader:${"fabric_version"()}")
    ploceus.dependOsl("0.22.0")
    compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.3")
}

java { sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.jar { from(rootProject.file("LICENSE")) }

tasks.processResources {
    val v = project.version
    inputs.property("version", v)

    filesMatching("fabric.mod.json") {
        expand("version" to v)
    }
}

operator fun String.invoke() = rootProject.providers.gradleProperty(this).orNull
    ?: error("Property $this not found")
