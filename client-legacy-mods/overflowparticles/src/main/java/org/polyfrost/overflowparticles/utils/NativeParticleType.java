package org.polyfrost.overflowparticles.utils;

import java.util.HashMap;
import java.util.Map;

public enum NativeParticleType {
    EXPLOSION_NORMAL("explode"), EXPLOSION_LARGE("largeexplode"), EXPLOSION_HUGE("hugeexplosion"),
    FIREWORKS_SPARK("fireworksSpark"), WATER_BUBBLE("bubble"), WATER_SPLASH("splash"), WATER_WAKE("wake"),
    SUSPENDED("suspended"), SUSPENDED_DEPTH("depthsuspend"), CRIT("crit"), CRIT_MAGIC("magicCrit"),
    SMOKE_NORMAL("smoke"), SMOKE_LARGE("largesmoke"), SPELL("spell"), SPELL_INSTANT("instantSpell"),
    SPELL_MOB("mobSpell"), SPELL_MOB_AMBIENT("mobSpellAmbient"), SPELL_WITCH("witchMagic"),
    DRIP_WATER("dripWater"), DRIP_LAVA("dripLava"), VILLAGER_ANGRY("angryVillager"),
    VILLAGER_HAPPY("happyVillager"), TOWN_AURA("townaura"), NOTE("note"), PORTAL("portal"),
    ENCHANTMENT_TABLE("enchantmenttable"), FLAME("flame"), LAVA("lava"), FOOTSTEP("footstep"),
    CLOUD("cloud"), REDSTONE("reddust"), SNOWBALL("snowballpoof"), SNOW_SHOVEL("snowshovel"),
    SLIME("slime"), HEART("heart"), ITEM_CRACK("iconcrack_"), BLOCK_CRACK("blockcrack_"),
    BLOCK_DUST("blockdust_"), WATER_DROP("rain"), ITEM_TAKE("take");

    private static final Map<String, NativeParticleType> BY_NAME = new HashMap<>();
    private static final ThreadLocal<NativeParticleType> CURRENT = new ThreadLocal<>();

    static {
        for (NativeParticleType type : values()) BY_NAME.put(type.particleName, type);
    }

    private final String particleName;

    NativeParticleType(String particleName) { this.particleName = particleName; }

    public String getParticleName() { return particleName; }

    public static NativeParticleType fromName(String name) {
        if (name == null) return null;
        NativeParticleType type = BY_NAME.get(name);
        if (type != null) return type;
        if (name.startsWith("iconcrack_")) return ITEM_CRACK;
        if (name.startsWith("blockcrack_")) return BLOCK_CRACK;
        if (name.startsWith("blockdust_")) return BLOCK_DUST;
        return null;
    }

    public static NativeParticleType current() { return CURRENT.get(); }
    public static void current(NativeParticleType type) {
        if (type == null) CURRENT.remove(); else CURRENT.set(type);
    }
}
