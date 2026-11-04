// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.api

object PerformanceCapturePage {
    @JvmStatic var controller: PerformanceCaptureController? = null
}

interface PerformanceCaptureController {
    fun snapshot(): PerformanceCaptureStatus
    fun start(seconds: Int)
    fun stop()
    fun cancel()
    fun openFolder()
}

data class PerformanceCaptureStatus(
    val state: String,
    val detail: String,
    val elapsedSeconds: Int,
    val durationSeconds: Int,
    val archiveName: String?,
    val busy: Boolean,
)
