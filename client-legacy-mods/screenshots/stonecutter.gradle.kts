plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10-ornithe"

stonecutter parameters {
    replacements.string(current.version == "1.7.10") {
        replace("net.minecraft.client.renderer.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager")
        replace("TextureUtil.processPixelValues", "tomeko.screenshotmessageenhancer.compat.PixelRows.flipVertical")
        replace("ClickEvent.Action", "net.minecraft.event.ClickEvent\$Action")
        replace("HoverEvent.Action", "net.minecraft.event.HoverEvent\$Action")
    }
    constants {
        match(node.metadata.project.substringAfterLast('-'), "fabric", "forge", "ornithe")
    }
}
