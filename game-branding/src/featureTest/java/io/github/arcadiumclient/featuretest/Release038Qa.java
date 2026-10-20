// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

final class Release038Qa {
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static int phase = Math.max(0, Math.min(3, Integer.getInteger("arcadium.release038StartPhase", 0))), ticks;

    private Release038Qa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!"ArcadiumTest".equals(mc.getSession().getUsername()) || !"0".equals(mc.getSession().getAccessToken()))
            throw new IllegalStateException("Release QA requires the isolated offline account");
        checks.put("release038", CHECKS);
        CHECKS.put("phase", phase);
        if (++ticks < 20) return false;
        switch (phase) {
            case 0 -> {
                if (!SimpleMode038Qa.tick(mc, checks)) return false;
                return next();
            }
            case 1 -> {
                if (!ResourceSearch038Qa.tick(mc, checks)) return false;
                return next();
            }
            case 2 -> {
                if (!Cape038Qa.tick(mc, checks)) return false;
                return next();
            }
            case 3 -> {
                mc.startGame("arcadium-release038-qa", "Arcadium 0.3.8 QA",
                    new WorldSettings(38, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                return next();
            }
            case 4 -> {
                if (mc.world == null || mc.player == null || ticks < 60) return false;
                HitboxBounds038Qa.verify(mc, checks);
                checks.put("minemen038", Minemen038Qa.run(mc));
                Cape038Qa.verifyWorld(mc, checks);
                CHECKS.put("completed", true);
                return true;
            }
            default -> throw new IllegalStateException("Invalid release QA phase");
        }
    }

    private static boolean next() { phase++; ticks = 0; return false; }
}
