// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.util.UUID;

public final class BedwarsStats {
    private final UUID uuid;
    private final String name;
    private final int star;
    private final double fkdr;
    private final int finalKills;
    private final int finalDeaths;
    private final boolean nicked;
    private final boolean unavailable;
    private final String packageRank;
    private final String newPackageRank;
    private final String monthlyPackageRank;
    private final String rankPlusColor;
    private final String monthlyRankColor;
    private final String prefix;

    public BedwarsStats(
            UUID uuid,
            String name,
            int star,
            double fkdr,
            int finalKills,
            int finalDeaths,
            boolean nicked,
            boolean unavailable,
            String packageRank,
            String newPackageRank,
            String monthlyPackageRank,
            String rankPlusColor,
            String monthlyRankColor,
            String prefix
    ) {
        this.uuid = uuid;
        this.name = name;
        this.star = star;
        this.fkdr = fkdr;
        this.finalKills = finalKills;
        this.finalDeaths = finalDeaths;
        this.nicked = nicked;
        this.unavailable = unavailable;
        this.packageRank = packageRank;
        this.newPackageRank = newPackageRank;
        this.monthlyPackageRank = monthlyPackageRank;
        this.rankPlusColor = rankPlusColor;
        this.monthlyRankColor = monthlyRankColor;
        this.prefix = prefix;
    }

    public static BedwarsStats nicked(UUID uuid, String name) {
        return new BedwarsStats(uuid, name, 0, 0.0, 0, 0, true, false, null, null, null, null, null, null);
    }

    public static BedwarsStats unavailable(UUID uuid, String name) {
        return new BedwarsStats(uuid, name, 0, 0.0, 0, 0, false, true, null, null, null, null, null, null);
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public String getName() {
        return this.name;
    }

    public int getStar() {
        return this.star;
    }

    public double getFkdr() {
        return this.fkdr;
    }

    public int getFinalKills() {
        return this.finalKills;
    }

    public int getFinalDeaths() {
        return this.finalDeaths;
    }

    public boolean isNicked() {
        return this.nicked;
    }

    public boolean isUnavailable() {
        return this.unavailable;
    }

    public String getPackageRank() {
        return this.packageRank;
    }

    public String getNewPackageRank() {
        return this.newPackageRank;
    }

    public String getMonthlyPackageRank() {
        return this.monthlyPackageRank;
    }

    public String getRankPlusColor() {
        return this.rankPlusColor;
    }

    public String getMonthlyRankColor() {
        return this.monthlyRankColor;
    }

    public String getPrefix() {
        return this.prefix;
    }
}
