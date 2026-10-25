plugins { base }
group = "io.github.arcadiumclient"
version = "3.11.6-arcadium-assets"
configurations.create("selectedIdleElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.capability("io.github.arcadiumclient:dynamic-fps-assets:3.11.6")
    outgoing.artifact(rootProject.tasks.named("prepareIdleCounterpart")) { type = "jar"; extension = "jar" }
}
