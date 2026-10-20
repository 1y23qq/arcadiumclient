import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByName

plugins {
    id("oneconfig-setup")
}

dependencies {
    "minecraft"("com.mojang:minecraft:${versionedCatalog.versions["minecraft"]}")

    val libsCatalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    // libs.bundles.test-core would pull in log4j-core which collides with the log4j
    // loom already puts on the test classpath
    "testImplementation"(platform(libsCatalog.findLibrary("junit-bom").get()))
    "testImplementation"(libsCatalog.findLibrary("junit").get())
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "testRuntimeOnly"(versionedCatalog["fabric-loader-junit"])
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    workingDir = layout.buildDirectory.dir("test-run").get().asFile
    doFirst { workingDir.mkdirs() }

    maxHeapSize = "2G"
    systemProperty("java.awt.headless", "true")
    systemProperty("mixin.debug.countInjections", "true")
    systemProperty("org.apache.logging.log4j.level", "INFO")

    val skipMixinAudit = project.hasProperty("skipMixinAudit")
    onlyIf { !skipMixinAudit }

    testLogging {
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

val sc = extensions.getByName<StonecutterBuildExtension>("stonecutter")
val loom = extensions.getByName<LoomGradleExtensionAPI>("loom")
loom.apply {
    val minecraftVersion = versionedCatalog.versions["minecraft"].requiredVersion
    accessWidenerPath = if (minecraftVersion in setOf("1.7.10", "1.8.9")) {
        rootProject.file("minecraft/mappings/$minecraftVersion.classtweaker")
    } else {
        sc.process(
            rootProject.file("minecraft/src/main/resources/oneconfigv1.classtweaker"),
            "build/processed.classtweaker",
        )
    }
    runConfigs["client"].apply {
        ideConfigGenerated(true)
        runDir = "../../run"
        // -Pdevauth=false launches offline for runs that do not need a real account
        property("devauth.enabled", (project.findProperty("devauth") ?: "true").toString())
        property("oneconfig.test", "true")
    }
}

if (versionedCatalog.versions["minecraft"].requiredVersion in setOf("1.7.10", "1.8.9")) {
    val legacyAccessFile = rootProject.file("minecraft/mappings/${versionedCatalog.versions["minecraft"].requiredVersion}.classtweaker")
    tasks.named<ProcessResources>("processResources") {
        inputs.file(legacyAccessFile)
        doLast {
            legacyAccessFile.copyTo(destinationDir.resolve("oneconfigv1.classtweaker"), overwrite = true)
        }
    }
}
