plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    // the 1.8.9 Ornithe node has no Fabric API
    if (current.version !in setOf("1.7.10", "1.8.9")) {
        dependencies["fapi"] = node.project.property("deps.fabric_api") as String
    }

    replacements {
        string(current.version == "1.7.10") {
            replace("net.minecraft.client.render.platform.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager")
            replace("Window(mc)", "Window(mc, mc.width, mc.height)")
            replace("font.drawWithShadow(text, (float) x, (float) y, color)", "font.drawWithShadow(text, x, y, color)")
            replace("val scores = scoreboard.getScores(objective)", "val scores = scoreboard.getScores(objective).filterIsInstance<ScoreboardScore>()")
        }

        string(current.parsed >= "1.21.6" || current.version in setOf("1.7.10", "1.8.9")) {
            replace("pushPose", "pushMatrix")
            replace("popPose", "popMatrix")
        }

        string(current.parsed < "1.21.11") {
            replace("net.minecraft.Util", "net.minecraft.util.Util")
        }

        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        string(current.parsed >= "26") {
            replace("net.minecraft.client.gui.GuiGraphics", "net.minecraft.client.gui.GuiGraphicsExtractor")
            replace("GuiGraphics", "GuiGraphicsExtractor")
        }

        string(current.parsed >= "26.2") {
            replace("ContextualBarRenderer", "ContextualBar")
        }

        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
            replace("com/mojang/blaze3d/pipeline/RenderPipeline", "com/mojang/renderpearl/api/pipeline/RenderPipeline")
        }
    }
}

stonecutter tasks {
    order("publishModrinth")
}
