// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

public interface GlintItemData {
    record Data(boolean potion, int color) { public static final Data EMPTY = new Data(false, -1); }
    Data arcadium$glintData();
    void arcadium$glintData(Data data);
}
