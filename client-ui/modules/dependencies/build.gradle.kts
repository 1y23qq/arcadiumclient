allprojects {
    with(tasks) {
        arrayOf("javadocJar", "sourcesJar", "remapSourcesJar").forEach {
            findByName(it)?.enabled = false
        }
    }
}

repositories {
    mavenLocal()
    maven("https://repo.polyfrost.org/snapshots")
}