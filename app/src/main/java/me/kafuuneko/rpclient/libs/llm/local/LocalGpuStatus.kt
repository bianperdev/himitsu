// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local

/** What the performance settings can offer for the GPU on this device. */
sealed class LocalGpuStatus {
    /** No usable GPU: no driver on the device, or the driver offers no device. */
    data object None : LocalGpuStatus()

    /** A driver exists but is not loaded yet; it loads only after the user turns the GPU on. */
    data object NotLoaded : LocalGpuStatus()

    /** The driver loaded and offers [name], for example "QUALCOMM Adreno(TM) 840". */
    data class Ready(val name: String) : LocalGpuStatus()

    /** Loading the driver closed the app last time, so the GPU was turned off. */
    data object DriverFailed : LocalGpuStatus()
}
