// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import com.electronwill.nightconfig.core.Config;
import java.util.Collections;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TextColor;

/**
 * The FPS module.
 */
public class FpsModule extends BaseModule {

    /**
     * The color for high fps.
     */
    public TextColor colorHigh;

    /**
     * The color for medium fps.
     */
    public TextColor colorMed;

    /**
     * The color for low fps.
     */
    public TextColor colorLow;

    /**
     * The default color for high fps.
     */
    public final TextColor defaultColorHigh = legacyColor(ChatFormatting.GREEN);

    /**
     * The default color for medium fps.
     */
    public final TextColor defaultColorMed = legacyColor(ChatFormatting.YELLOW);

    /**
     * The default color for low fps.
     */
    public final TextColor defaultColorLow = legacyColor(ChatFormatting.RED);

    /**
     * Instantiates a new FPS module.
     */
    public FpsModule() {
        lines.add(new DebugLine("fps", "format.betterf3.no_format", true));
        lines.getFirst().inReducedDebug = true;

        this.colorHigh = this.defaultColorHigh;
        this.colorMed = this.defaultColorMed;
        this.colorLow = this.defaultColorLow;
    }

    /**
     * Updates the FPS module.
     *
     * @param client the Minecraft client
     */
    public void update(final Minecraft client) {
        final int currentFps = client.getFps();

        final String fpsString = I18n.get(
                        "format.betterf3.fps",
                        currentFps,
                        (double) client.options.framerateLimit().get() == Options.UNLIMITED_FRAMERATE_CUTOFF
                                ? I18n.get("text.betterf3.line.fps.unlimited")
                                : client.options.framerateLimit().get(),
                        client.options.enableVsync().get() ? I18n.get("text.betterf3.line.fps.vsync") : "")
                .trim();

        final TextColor color =
                switch (Utils.fpsColor(currentFps)) {
                    case HIGH -> this.colorHigh;
                    case MEDIUM -> this.colorMed;
                    case LOW -> this.colorLow;
                };

        lines.getFirst().value(Collections.singletonList(Utils.styledText(fpsString, color)));
    }

    @Override
    protected void loadModuleConfig(final Config moduleConfig) {
        this.colorHigh = readColor(moduleConfig, "color_high", this.defaultColorHigh);
        this.colorMed = readColor(moduleConfig, "color_med", this.defaultColorMed);
        this.colorLow = readColor(moduleConfig, "color_low", this.defaultColorLow);
    }

    @Override
    protected void saveModuleConfig(final Config moduleConfig) {
        writeColor(moduleConfig, "color_high", this.colorHigh);
        writeColor(moduleConfig, "color_med", this.colorMed);
        writeColor(moduleConfig, "color_low", this.colorLow);
    }
}
