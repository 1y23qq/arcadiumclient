plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter {
    tasks {
        order("publishModrinth")
        order("publishCurseforge")
    }

    parameters {
        replacements {
            if (current.version == "1.7.10") {
                string(true) { replace("\"renderEntities\", \"getLookVector\", \"prepare\"", "\"renderEntities\", \"prepare\"") }
            }
            string(eval(current.version, "<= 1.8.9")) {
                replace(
                    "net.minecraft.server.Bootstrap",
                    "net.minecraft.Bootstrap"
                )
                replace(
                    "net.minecraft.client.CameraType",
                    "org.polyfrost.behindyou.client.compat.CameraType"
                )
            }
        }
    }
}
