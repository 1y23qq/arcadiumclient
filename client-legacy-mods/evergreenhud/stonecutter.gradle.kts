plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    if (current.version !in setOf("1.7.10", "1.8.9")) {
        dependencies["fapi"] = node.project.property("deps.fabric_api") as String
    }

    replacements {
        string(current.version == "1.7.10") {
            replace("net.minecraft.client.render.platform.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager")
            replace("Connection.connect(InetAddress.getByName(address.address), address.port, false)", "Connection.connect(InetAddress.getByName(address.address), address.port)")
            replace("HandshakeC2SPacket(47,", "HandshakeC2SPacket(5,")
            replace("it.handleDisconnection()", "finish(generation, null)")
            replace("menu.container.displayName.string", "menu.container.inventoryName")
            replace("player.getEyePosition(1.0f)", "player.getPosition(1.0f)")
            replace("level.maxBuildHeight", "level.height")
            replace("client.entityRenderDispatcher", "net.minecraft.client.render.entity.EntityRenderDispatcher.INSTANCE")
            replace("return Item.REGISTRY[id]", "return Item.REGISTRY[id] as? Item")
            replace("mc.player?.activeEffects?.filter { StatusEffect.BY_ID", "mc.player?.activeEffects?.filterIsInstance<MobEffectInstance>()?.filter { StatusEffect.BY_ID")
            replace("potion.getPotionEffects(metadata)?.firstOrNull()", "potion.getPotionEffects(metadata)?.filterIsInstance<StatusEffectInstance>()?.firstOrNull()")
            replace("potion.getPotionEffects(v.id.toInt())?.let", "potion.getPotionEffects(v.id.toInt())?.filterIsInstance<StatusEffectInstance>()?.let")
            replace("for (part in this) {", "for (raw in this) { val part = raw as net.minecraft.text.Text")
        }
        string(eval(current.version, "<= 1.8.9")) {
            replace(
                "com.mojang.blaze3d.platform.InputConstants",
                "org.polyfrost.oneconfig.internal.legacy.InputConstants",
            )
            replace(
                "net.minecraft.network.chat.Component",
                "org.polyfrost.oneconfig.internal.legacy.chat.Component",
            )
            replace(
                "com.mojang.blaze3d.platform.NativeImage",
                "org.polyfrost.oneconfig.internal.legacy.NativeImage",
            )
        }

        string(current.parsed < "1.21.11") {
            replace("net.minecraft.Util", "net.minecraft.util.Util")
        }

        string(current.parsed < "26.3") {
            replace("com.mojang.renderpearl.api.buffers", "com.mojang.blaze3d.buffers")
            replace("com.mojang.renderpearl.api.textures", "com.mojang.blaze3d.textures")
            replace("com.mojang.renderpearl.api.GpuFormat", "com.mojang.blaze3d.GpuFormat")
        }
    }
}

stonecutter tasks {
    order("publishModrinth")
}