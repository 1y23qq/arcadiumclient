plugins { java }

group = "io.github.arcadiumclient"
version = "0.5.7"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.polyfrost.org/releases/")
}

dependencies {
    implementation("net.fabricmc:tiny-remapper:0.14.0")
    implementation("net.fabricmc:mapping-io:0.8.0")
    implementation("net.fabricmc:fabric-loom:1.17.21") { isTransitive = false }
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("com.google.code.gson:gson:2.13.2")
    compileOnly("org.apache.logging.log4j:log4j-api:2.19.0")
    compileOnly("com.moulberry:mixinconstraints:1.1.0")
    compileOnly("net.fabricmc:fabric-loader:0.19.5")
    compileOnly(fileTree("../build/forge1710/cache/libraries") { include("**/*forgePatches.jar", "**/*universal.jar") })
    compileOnly(fileTree("../build/forge1710/cache/mods") { include("*.jar") })
    compileOnly(fileTree("../build/forge1710/cache/libraries/org/lwjgl") { include("**/*.jar"); exclude("**/*natives*.jar") })
    compileOnly("com.google.guava:guava:17.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation(files("../build/forge1710/cache/mods/coretweaks-0.3.4.7-GTNH.jar"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

val versionReference by configurations.creating
dependencies { versionReference("net.fabricmc:fabric-loader:0.19.5") { isTransitive = false } }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

val forgeMixins by sourceSets.creating {
    compileClasspath = files("../build/forge1710/forge-srg-api.jar", "../build/forge1710/minecraft-srg-api.jar", "../build/forge1710/arcadium-srg.jar") +
        sourceSets.main.get().compileClasspath + sourceSets.main.get().output
}
val qa by sourceSets.creating {
    compileClasspath = forgeMixins.compileClasspath + fileTree("../build/forge1710/cache/libraries/com/mojang/authlib") { include("**/*.jar") }
}
tasks.register<Jar>("qaJar") {
    archiveBaseName = "ArcadiumForgeQa"
    from(qa.output)
}

tasks.register("exportClasspath") {
    dependsOn(tasks.classes)
    doLast {
        layout.buildDirectory.file("tool-classpath.txt").get().asFile.writeText(
            sourceSets.main.get().runtimeClasspath.files.joinToString("\n") { it.absolutePath }, Charsets.UTF_8)
        layout.buildDirectory.file("version-reference-classpath.txt").get().asFile.writeText(
            versionReference.files.joinToString("\n") { it.absolutePath }, Charsets.UTF_8)
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
