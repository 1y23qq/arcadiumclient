
allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/releases")
        maven("https://maven.ornithemc.net/releases")
    }
}

subprojects {
    val composeBundleModule = path == ":modules:compose-bundle"
    val forkModule = path.startsWith(":modules:") || parent?.path in setOf(":bootstrap", ":minecraft")
    val includesFontAssets = path == ":modules:ui" || parent?.path in setOf(":bootstrap", ":minecraft")
    if (!forkModule) return@subprojects

    tasks.withType<Jar>().configureEach {
        if (composeBundleModule) {
            from(project.file("LICENSE")) {
                into("META-INF/licenses/compose-bundle")
                rename { "Apache-2.0.txt" }
            }
        } else {
            from(rootProject.file("../LICENSE")) {
                into("META-INF/licenses/arcadium-core")
                rename { "GPL-3.0-only.txt" }
            }
            from(rootProject.file("LICENSE")) {
                into("META-INF/licenses/oneconfig")
                rename { "ORIGINAL-LICENSE.txt" }
            }
            from(rootProject.file("NOTICE.md")) {
                into("META-INF/licenses/oneconfig")
            }
            if (includesFontAssets) {
                from(rootProject.file("../licenses/assets")) {
                    include("*-OFL.txt")
                    into("META-INF/licenses/fonts")
                }
            }
        }
    }

    pluginManager.withPlugin("maven-publish") {
        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    licenses {
                        license {
                            name.set(if (composeBundleModule) "Apache License, Version 2.0" else "GNU General Public License, Version 3")
                            url.set(if (composeBundleModule) "https://www.apache.org/licenses/LICENSE-2.0" else "https://www.gnu.org/licenses/gpl-3.0.html")
                            distribution.set("repo")
                        }
                    }
                    description.set(
                        if (composeBundleModule) "Compose Multiplatform dependency bundle. Included libraries retain their original licenses and notices."
                        else "Independent Arcadium adaptation of OneConfig, conveyed under GPL version 3. Original Polyfrost copyright, license and additional terms are retained."
                    )
                }
            }
        }
    }
}

// each bootstrap node JiJ's its platform jar plus all modules and deps
// compose-bundle ships standalone and is deliberately kept out of that JiJ
val bootstrapNodes = subprojects.filter { it.parent?.path == ":bootstrap" }
val composeBundle = project(":modules:compose-bundle")

// compose-bundle carries its own version so evaluate it to grab the jar task
// rather than guessing the file name
evaluationDependsOn(composeBundle.path)

// Sync rather than Copy because node build/libs keeps jars of previously built versions
tasks.register<Sync>("buildAndCollect") {
    group = "build"
    description = "Builds every OneConfig bootstrap node plus compose-bundle and collects the production jars into build/libs."

    dependsOn(":bootstrap:assembleAllNodes")

    // loom keeps the unmapped jar in build/devlibs so build/libs holds only the shippable one
    // version filter skips stale jars from earlier project versions
    bootstrapNodes.forEach { node ->
        from(node.layout.buildDirectory.dir("libs")) {
            include("*-${node.version}.jar")
        }
    }
    from(composeBundle.tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val noBootstrapNodes = bootstrapNodes.isEmpty()
    doFirst {
        if (noBootstrapNodes) {
            throw GradleException("No bootstrap nodes were registered — check the stonecutter tree in settings.gradle.kts.")
        }
    }

    doLast {
        val target = destinationDir
        logger.lifecycle("Collected ${target.listFiles { f -> f.extension == "jar" }?.size ?: 0} jars into $target")
    }
}
