plugins {
    id("pylon.build")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<Jar>("jar") {
    from("LICENSE.md", "COPYING", "NOTICE.md")
}
tasks.named<Jar>("shadowJar") {
    from("LICENSE.md", "COPYING", "NOTICE.md")
}

val transformTest = sourceSets.create("transformTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
val verifyTransformFilter = tasks.register<JavaExec>("verifyTransformFilter") {
    dependsOn(transformTest.classesTaskName)
    classpath = transformTest.runtimeClasspath
    mainClass.set("pl.tomgirl.pylon.game.PatchSymbolsTest")
}
tasks.check { dependsOn(verifyTransformFilter) }
