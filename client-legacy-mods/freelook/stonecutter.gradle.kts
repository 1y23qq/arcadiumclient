plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    if (current.version !in setOf("1.7.10", "1.8.9")) {
        dependencies["fapi"] = node.project.property("deps.fabric_api") as String
    }

    replacements {
        if (current.version == "1.7.10") {
            string(true) {
                replace("\"render(FJ)V\"", "\"render(F)V\"")
                replace("Lnet/minecraft/client/render/platform/GlStateManager;translatef(FFF)V", "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V")
            }
        }
        string(current.version in setOf("1.7.10", "1.8.9")) {
            replace("com.mojang.blaze3d.platform.InputConstants", "org.polyfrost.oneconfig.internal.legacy.InputConstants")
        }

        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
            replace("NamespacedIdentifier", "NamespacedIdentifier")
            replace("StringChannelIdentifierParser", "StringChannelIdentifierParser")
        }

        string(current.parsed >= "26") {
            replace("playS2C", "clientboundPlay")
            replace("playC2S", "serverboundPlay")
            replace("displayClientMessage", "sendSystemMessage")
        }
    }
}

stonecutter tasks {
    order("publishModrinth")
}