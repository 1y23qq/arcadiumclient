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
        long revision = NickHider.renderRevision();
        if (text instanceof RenderedText rendered) {
            if (rendered.revision == revision) return text;
            text = rendered.original;
        }
        List<Text> components = new ArrayList<>();
        List<String> runs = new ArrayList<>();
        for (Text component : text) { components.add(component); runs.add(component.getContent()); }
        List<String> masked = NickHider.displayRuns(runs);
        if (masked == runs) return text;
        return new RenderedText(copyStyled(components, masked), text, revision);
    }

    public static Text display(Text text, String username, String nickname) {
        return display(text, username, nickname, List.of());
    }

    public static Text display(Text text, String username, String nickname, List<NickReplacementRule> rules) {
        List<Text> components = new ArrayList<>();
        List<String> runs = new ArrayList<>();
        for (Text component : text) {
            components.add(component);
            runs.add(component.getContent());
        }
        List<String> masked = new NickRenderPolicy(-1, true, username, nickname, rules).replaceRuns(runs);
        if (masked == runs) return text;
        return copyStyled(components, masked);
    }

    private static Text copyStyled(List<Text> components, List<String> masked) {
        Text out = new LiteralText("");
        for (int index = 0; index < components.size(); index++) {
            String value = masked.get(index);
            if (!value.isEmpty()) {
                out.append(new LiteralText(value).setStyle(components.get(index).getStyle().deepCopy()));
            }
        }
        return out;
    }

    public static List<Text> renderedLines(List<Text> lines) {
        if (!NickHiderConfig.getEnabled()) return lines;
        long revision = NickHider.renderRevision();
        List<Text> result = new ArrayList<>(lines.size());
        for (Text line : lines) result.add(line instanceof RenderedText ? line : new RenderedText(line, line, revision));
        return result;
    }

    private static final class RenderedText implements Text {
        private final Text value, original;
        private final long revision;
        private RenderedText(Text value, Text original, long revision) { this.value = value; this.original = original; this.revision = revision; }
        private String ready(String text) { return NickHider.rememberRendered(text, revision); }
        @Override public String getContent() { return ready(value.getContent()); }
        @Override public String getString() { return ready(value.getString()); }
        @Override public String getFormattedString() { return ready(value.getFormattedString()); }
        @Override public net.minecraft.text.Style getStyle() { return value.getStyle(); }
        @Override public Text setStyle(net.minecraft.text.Style style) { value.setStyle(style); return this; }
        @Override public List<Text> getSiblings() { return value.getSiblings(); }
        @Override public Text append(Text sibling) { value.append(display(sibling)); return this; }
        @Override public Text append(String text) { return append(new LiteralText(text)); }
        @Override public Text copy() { return new RenderedText(value.copy(), original.copy(), revision); }
        @Override public java.util.Iterator<Text> iterator() {
            java.util.Iterator<Text> iterator = value.iterator();
            return new java.util.Iterator<>() {
                @Override public boolean hasNext() { return iterator.hasNext(); }
                @Override public Text next() { Text next = iterator.next(); return next instanceof RenderedText ? next : new RenderedText(next, next, revision); }
            };
        }
    }
}
