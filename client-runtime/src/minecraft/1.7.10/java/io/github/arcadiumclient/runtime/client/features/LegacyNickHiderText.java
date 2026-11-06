// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class LegacyNickHiderText {
    private LegacyNickHiderText() {}

    public static Text display(Text text) {
        if (text == null || !NickHiderConfig.getEnabled()) return text;
        return display(text, NickHider.sourceName(), NickHiderConfig.getNickname());
    }

    public static Text display(Text text, String username, String nickname) {
        List<Text> components = new ArrayList<>();
        List<String> runs = new ArrayList<>();
        for (Text component : text) {
            components.add(component);
            runs.add(component.getContent());
        }
        List<String> masked = NickNameMask.INSTANCE.replaceRuns(runs, username, nickname);
        if (masked == runs) return text;
        Text out = new LiteralText("");
        for (int index = 0; index < components.size(); index++) {
            String value = masked.get(index);
            if (!value.isEmpty()) {
                out.append(new LiteralText(value).setStyle(components.get(index).getStyle().deepCopy()));
            }
        }
        return out;
    }
}
