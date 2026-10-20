plugins {
    id("dev.kikugie.stonecutter")

}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter parameters {
    replacements {
        string(current.version == "1.7.10") {
            replace("net.minecraft.client.render.platform.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager")
        }
    }
}
