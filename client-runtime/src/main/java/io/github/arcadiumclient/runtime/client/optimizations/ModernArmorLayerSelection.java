// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class ModernArmorLayerSelection {
    private Object capture, layer, entity, firstModel, secondModel;
    private long frame = -1;

    void select(Object capture, Object layer, Object entity, Object firstModel, Object secondModel, long frame) {
        this.capture = capture; this.layer = layer; this.entity = entity;
        this.firstModel = firstModel; this.secondModel = secondModel; this.frame = frame;
    }
    void clear() { capture = layer = entity = firstModel = secondModel = null; frame = -1; }
    Object capture() { return capture; }
    boolean model(Object model, Object entity, long frame) {
        return capture != null && this.entity == entity && this.frame == frame && model != null && (firstModel == model || secondModel == model);
    }
    boolean layer(Object layer, Object model, Object entity, long frame) {
        return this.layer == layer && model(model, entity, frame);
    }
    boolean captureModel(Object capture, Object model, long frame) {
        return this.capture == capture && model(model, entity, frame);
    }
}
