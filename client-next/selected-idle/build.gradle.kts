plugins { base }
group = "io.github.arcadiumclient"
version = "3.11.9-arcadium-assets"
configurations.create("selectedIdleElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.capability("io.github.arcadiumclient:dynamic-fps-assets:3.11.9")
    outgoing.artifact(rootProject.layout.buildDirectory.file("selected-mods/dynamic-fps-3.11.9-arcadium-assets.jar")) {
        type = "jar"; extension = "jar"
        builtBy(rootProject.tasks.named("prepareIdleCounterpart"))
    }
}
