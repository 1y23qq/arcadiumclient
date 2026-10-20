plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10"

stonecutter parameters {
    replacements.string(current.version == "1.7.10") { replace("net.minecraft.client.render.platform.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager") }
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    if (current.version !in setOf("1.7.10", "1.8.9")) {
        dependencies["fapi"] = node.project.property("deps.fabric_api") as String
    }

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}

stonecutter tasks {
    order("publishModrinth")
}