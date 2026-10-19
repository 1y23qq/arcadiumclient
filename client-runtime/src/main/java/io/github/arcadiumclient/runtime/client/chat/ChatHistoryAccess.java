// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import java.util.List;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.text.Text;

public interface ChatHistoryAccess {
    List<ChatMessage> arcadium$history();
    void arcadium$clearHistory();
    void arcadium$restore(Text text, long received, int tick, int id);
    default void arcadium$restore(Text text, long received, int tick) { arcadium$restore(text, received, tick, 0); }
}
