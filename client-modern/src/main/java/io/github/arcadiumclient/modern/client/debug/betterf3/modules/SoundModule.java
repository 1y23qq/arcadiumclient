// SPDX-License-Identifier: MIT
// Copyright (c) 2020-2026 cominixo / TreyRuffy. Adapted for Arcadium.
package io.github.arcadiumclient.modern.client.debug.betterf3.modules;

import java.util.Arrays;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.DebugLine;
import io.github.arcadiumclient.modern.client.debug.betterf3.utils.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TextColor;

/**
 * The Sound module.
 */
public class SoundModule extends BaseModule {

  /**
   * Total color.
   */
  public TextColor maximumColor;

  /**
   * Default total color.
   */
  public final TextColor defaultMaximumColor = TextColor.fromLegacyFormat(ChatFormatting.DARK_AQUA);

  /**
   * Instantiates a new Sound module.
   */
  public SoundModule() {
    this.defaultNameColor = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
    this.defaultValueColor = TextColor.fromLegacyFormat(ChatFormatting.AQUA);

    this.nameColor = defaultNameColor;
    this.valueColor = defaultValueColor;
    this.maximumColor = this.defaultMaximumColor;

    lines.add(new DebugLine("sounds", "format.betterf3.total", true));
    lines.add(new DebugLine("ambient_sounds", "format.betterf3.total", true));
    lines.add(new DebugLine("mood"));
  }

  /**
   * Updates the Sound module.
   *
   * @param client the Minecraft client
   */
  public void update(final Minecraft client) {

    final String debugString = client.getSoundManager().getDebugString();
    final String[] staticHandlerList =
    debugString.substring(8).substring(0, debugString.indexOf(" ")).replace(" +", "").split("/");
    final String[] streamingHandlerList = debugString.substring(debugString.indexOf("+") + 1).replace(" ", "").split("/");

    final String playing = I18n.get("text.betterf3.line.playing");
    final String maximum = I18n.get("text.betterf3.line.maximum");

    // Sound
    lines.get(0).value(Arrays.asList(Utils.styledText(playing, valueColor), Utils.styledText(maximum,
    this.maximumColor), Utils.styledText(staticHandlerList[0], valueColor),
    Utils.styledText(staticHandlerList[1], this.maximumColor)));

    // Ambient Sound
    lines.get(1).value(Arrays.asList(Utils.styledText(playing, valueColor), Utils.styledText(maximum,
    this.maximumColor), Utils.styledText(streamingHandlerList[0], valueColor),
    Utils.styledText(streamingHandlerList[1], this.maximumColor)));

    // Mood
    if (client.player != null) {
      lines.get(2).value(Math.round(client.player.getCurrentMood() * 100.0F) + "%");
    }
  }

}
