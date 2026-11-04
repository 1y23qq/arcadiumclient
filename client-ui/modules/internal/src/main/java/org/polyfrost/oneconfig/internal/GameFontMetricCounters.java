// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

public interface GameFontMetricCounters {
    long arcadium$metricHits();
    long arcadium$metricMisses();
    default void arcadium$flushTextBatches() { }
}
